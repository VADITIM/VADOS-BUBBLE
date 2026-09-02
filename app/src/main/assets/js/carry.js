import { stirLiquid } from './liquid.js';
import { bridge, CLOSED, pill, root, shared } from './state.js';

/**
 * The bubble picked up and put somewhere else.
 *
 * Until this, the bubble was a fixture of the status bar: a drag rubber-banded and let go, and
 * the punch hole was the only place it could ever be. Carried, it is an object on the screen —
 * the same object, doing everything it did before, standing where it was put.
 *
 * Four things collide with that, and each is answered here rather than left to be discovered:
 *
 * 1. **The goo layer's region is bar-sized on purpose** — a filter costs its area — and a
 *    bubble outside it silently loses its skin and paints its own background, which reads as a
 *    colour bug. So the region travels with the bubble: a band around wherever it is standing,
 *    not the whole screen, because the whole screen is exactly the cost the small region exists
 *    to avoid.
 * 2. **The proxy has to follow**, and it is the only touchable window on the bar. Carried, it
 *    is the same size it always was and simply somewhere else — which is why free placement was
 *    chosen over the edge-snap Google's and Samsung's bubbles use: a bubble parked half
 *    off-screen needs a proxy hanging over the edge, and a dock needs one the size of the dock.
 * 3. **The states that mean something at the cutout are not carried with it.** An alert is a
 *    thing arriving at the hole; an open panel is centred on the screen. So anything past a mod
 *    brings the bubble home first, and it goes back to where it was put when it closes. One
 *    rule, and it is the honest one — the alternative is re-deriving every geometry in the row
 *    against a moving origin.
 * 4. **Where it was put survives a restart**, because a bubble that goes home whenever the
 *    phone sleeps was never really put anywhere.
 */

/** How far a drag has to travel before the bubble comes off its spot rather than springing back. */
const CARRY_GRAB = 72;

/** How near the punch hole it has to be let go for it to be home again rather than parked. */
const CARRY_HOME = 56;

/** Its way home when a state needs it at the cutout, and its way back out afterwards. */
const CARRY_TRAVEL = 420;

let carried = { x: 0, y: 0 };

/** Where it was parked, kept while a state borrows the cutout so it can be given back. */
let parked = null;
let carrying = false;

export function isCarried() {
  return carried.x !== 0 || carried.y !== 0;
}

export function isCarrying() {
  return carrying;
}

function writeCarry(easeMilliseconds) {
  root.style.setProperty('--carry-ms', (easeMilliseconds || 0) + 'ms');
  root.style.setProperty('--carry-x', carried.x.toFixed(1) + 'px');
  root.style.setProperty('--carry-y', carried.y.toFixed(1) + 'px');
  // The region rides with it. A band rather than the whole screen: the whole screen is the
  // cost the bar-sized region exists to avoid, and the skin only ever needs the room the
  // shapes themselves stand in.
  root.classList.toggle('carried', isCarried());
  root.style.setProperty('--liquid-rise', Math.min(0, carried.y).toFixed(1) + 'px');
  root.style.setProperty('--liquid-drop', Math.max(0, carried.y).toFixed(1) + 'px');
  stirLiquid((easeMilliseconds || 0) + 240);
}

/** Picked up: from here the bubble goes exactly where the finger goes, and stays there. */
export function carryTo(x, y) {
  carrying = true;
  root.classList.add('carrying');
  carried = { x, y };
  writeCarry(0);
}

/** Let go. Near enough to the hole is home; anywhere else is where it now lives. */
export function dropCarry() {
  if (!carrying) return;
  carrying = false;
  root.classList.remove('carrying');
  if (Math.hypot(carried.x, carried.y) < CARRY_HOME) carried = { x: 0, y: 0 };
  writeCarry(CARRY_TRAVEL);
  bridge.triggerHaptic('tap');
  bridge.setCarried(Math.round(carried.x), Math.round(carried.y));
}

/**
 * Home for a moment, because something needs the bubble to be the bubble at the hole: an alert
 * is a thing *arriving* there, and every open panel is centred on the screen. It is given back
 * its place the moment that is over.
 */
export function borrowCarryHome() {
  if (!isCarried() || parked) return;
  parked = carried;
  carried = { x: 0, y: 0 };
  writeCarry(CARRY_TRAVEL);
}

export function returnCarry() {
  if (!parked) return;
  carried = parked;
  parked = null;
  writeCarry(CARRY_TRAVEL);
}

/**
 * The one rule, applied wherever the row repaints: anything past a mod happens at the cutout.
 * Called from the row rather than guessed at here, because the row is the side that knows what
 * it has just become.
 */
export function fitCarry() {
  if (shared.state === 'idle' && CLOSED.has(shared.size)) returnCarry();
  else borrowCarryHome();
}

/** Where the proxy has to be, which is wherever the bubble is. */
export function carryOffset() {
  return { x: carried.x, y: carried.y };
}

/** Restored from the store: a bubble that went home every time the phone slept was never put anywhere. */
window.setCarried = (x, y) => {
  carried = { x: Number(x) || 0, y: Number(y) || 0 };
  parked = null;
  writeCarry(0);
  // The window is the host's and it has just been told where the bubble is, so the page says
  // what it is drawing rather than waiting for something else to move.
  bridge.setCarryProxy(Math.round(carried.x), Math.round(carried.y));
};

export { CARRY_GRAB };

/** Kept in step with the proxy on every write, since the two are one answer. */
export function reportCarry() {
  bridge.setCarryProxy(Math.round(carried.x), Math.round(carried.y));
}

// The bubble is what the mirror measures, so nothing here tells the skin anything: it is read
// off the box like every other frame. The only thing owed is the frames themselves.
pill.addEventListener('transitionend', event => {
  if (event.propertyName === 'translate') reportCarry();
});
