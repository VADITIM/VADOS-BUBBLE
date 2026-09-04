import { stirLiquid } from './liquid.js';
import { rubberBandPast, toy, untoy } from './motion.js';
import { HOLD_MILLIS, bridge, root } from './state.js';

/**
 * The bubble over One UI's own clock, at the far left of the bar, and the bubble the Now mods
 * are shown in.
 *
 * It used to carry nothing and open into nothing — it was the time, which is the one thing on
 * this bar that is true without anything having happened. It carries the Now mods now, because
 * a second bubble for them at the same end of the bar meant this one had to leave whenever a
 * torch or a recording came out, and a bar with a bubble that keeps departing is a bar with a
 * hole in it. What belongs to the mods is in now.js; what is here is the time, the box and the
 * hand: it is pressed, it is pulled, and it is held, and the hold is the way out to the clock
 * app the same way every other bubble's hold is the way out to the app behind it.
 *
 * It costs a window for that. The proxy is exactly the box — the digits when it is only the
 * time, the mod's width when it is carrying one, the panel when one is open — because every
 * pixel a touchable window covers is a pixel the shade swipe cannot start on.
 */
const clockPill = document.getElementById('clock');
const clockFace = document.getElementById('clock-face');
const clockSeconds = document.getElementById('clock-seconds');

/** Mirrors --clock-left in pill.css: how far its left edge stands off the screen edge. */
const CLOCK_LEFT = 14;

/** How far down the finger has to pull before the pull counts, matching the Status bubble's. */
const CLOCK_PULL = 34;

/** Its flight out of the punch hole, the same one the Status bubble makes to the other end. */
const CLOCK_TRAVEL = 420;
const CLOCK_LAND = 0.42;

/** How long the swell on the minute is held before it is let go, in milliseconds. */
const CLOCK_TICK = 260;

let isBorn = false;

/**
 * The phone is German and the bar it is standing on is 24-hour, so this is too — the point of
 * the bubble is that the eye reads it in the same place and the same shape as before.
 */
function paintClock() {
  const now = new Date();
  const minutes = now.getMinutes();
  const seconds = now.getSeconds();
  clockFace.textContent = now.getHours() + ':' + (minutes < 10 ? '0' : '') + minutes;
  // One element per digit, because the two are stacked: a text node cannot be broken into lines
  // at a place of our choosing, and a `<br>` between them is the same two boxes with worse names.
  const digits = (seconds < 10 ? '0' : '') + seconds;
  if (clockSeconds.children.length !== 2) {
    clockSeconds.replaceChildren(document.createElement('span'), document.createElement('span'));
  }
  clockSeconds.children[0].textContent = digits[0];
  clockSeconds.children[1].textContent = digits[1];
}

/**
 * On the second, not every second: a repeating interval drifts against the wall clock and
 * eventually turns the digits over late, which on a clock is the whole of what it had to get
 * right. Each tick works out how long is left of the current second and waits exactly that.
 *
 * The seconds are redrawn sixty times a minute and the *minute* is still the only thing this
 * bubble moves for — a swell every second would be a bubble that never stops twitching, and the
 * glyph-as-cause rule is about a reading changing, not about a repaint happening.
 */
function tickClock() {
  const was = clockFace.textContent;
  paintClock();
  // Only on a real change, so the first paint does not swell: this is the digits' own doing.
  if (isBorn && clockFace.textContent !== was) {
    clockPill.classList.add('ticking');
    stirLiquid(CLOCK_TICK * 2);
    setTimeout(() => clockPill.classList.remove('ticking'), CLOCK_TICK);
    // The minute is the only turn that can change the bubble's width — the seconds are tabular
    // and two digits wide for the whole of it — so the proxy is refitted there and not per second.
    fitClockProxy();
  }
  const now = new Date();
  setTimeout(tickClock, 1000 - now.getMilliseconds() + 20);
}

/** Born at the punch hole like everything else here, and only once. */
function bornClock() {
  if (isBorn) return;
  isBorn = true;
  paintClock();
  const box = clockPill.getBoundingClientRect();
  const home = window.innerWidth / 2 - (box.left + box.width / 2);
  root.style.setProperty('--clock-fly', Math.round(home) + 'px');
  root.style.setProperty('--clock-pop', 0.5);
  requestAnimationFrame(() => {
    clockPill.classList.add('lit');
    root.style.setProperty('--clock-fly', '0px');
    root.style.setProperty('--clock-pop', 1);
  });
  setTimeout(fitClockProxy, CLOCK_TRAVEL * CLOCK_LAND);
  stirLiquid(CLOCK_TRAVEL + 300);
}

/** Whether it is standing on the bar, which is what the skin asks before mirroring it. */
export function isClockLit() {
  return clockPill.classList.contains('lit');
}

/**
 * The proxy is exactly the box, whatever the box currently is — the digits, a mod's width, or
 * the panel a mod opens into. Measured rather than told, so a mod that changes the size owes
 * this a call and nothing else.
 *
 * The height is taken to the box's *bottom* rather than as the box's own height: the host hangs
 * this window at the top of the screen and adds the grab margin to whatever it is given, which
 * is right for a bubble sitting on the bar and short by the bar's own inset for a panel hanging
 * off it — the last few pixels of an open panel could be seen and not touched.
 */
export function fitClockProxy() {
  const box = clockPill.getBoundingClientRect();
  if (!isBorn || !isClockLit() || !box.width) {
    bridge.setClockProxy(0, 0, 0);
    return;
  }
  bridge.setClockProxy(Math.round(box.width), Math.round(box.bottom), Math.round(box.left));
}

/** Whether a point on the bar belongs to this bubble, asked the same way the others are. */
export function clockHolds(x, y) {
  if (!isClockLit()) return false;
  const box = clockPill.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}

let clockHeld = false;
let clockDownAt = null;
let clockHoldTimer = 0;

/**
 * Held, it opens the clock app — the one thing behind this bubble worth leaving for, and the
 * same meaning the hold has on every other bubble here.
 *
 * Tapped or pulled, it does its animation and nothing else. That is deliberate rather than
 * unfinished: there is no Active state for the time, because there is nothing about it the
 * bubble could open out to say that the two digits are not already saying. What the gesture
 * buys is the press and the band — the bubble answers the hand, which is what tells the user
 * it is a bubble at all and not a picture of one.
 */
export function clockTouch(action, x, y) {
  if (action === 'down') {
    clockHeld = false;
    clockDownAt = { x, y };
    clockPill.classList.add('pressing');
    clearTimeout(clockHoldTimer);
    clockHoldTimer = setTimeout(() => {
      clockHeld = true;
      clockPill.classList.remove('pressing');
      bridge.triggerHaptic('expand');
      bridge.openClock();
    }, HOLD_MILLIS);
    return;
  }
  if (action === 'move') {
    if (!clockDownAt) return;
    const across = x - clockDownAt.x;
    const down = y - clockDownAt.y;
    toy(clockPill, '--clock-drag', across, down);
    if (rubberBandPast(across, down) || down > CLOCK_PULL) {
      clearTimeout(clockHoldTimer);
      clockPill.classList.remove('pressing');
    }
    return;
  }
  clearTimeout(clockHoldTimer);
  clockPill.classList.remove('pressing');
  untoy(clockPill, '--clock-drag');
  clockDownAt = null;
  // A tap does nothing and says nothing: there is nothing behind this bubble to open — the hold
  // is the way to the clock app — and a buzz that answers with nothing is worse than a bubble
  // that simply did not move under the finger.
}

export { clockPill };

root.style.setProperty('--clock-left', CLOCK_LEFT + 'px');
tickClock();
// After the first paint, so the drop leaves the hole at the size it will arrive at rather than
// at whatever an empty box measured.
requestAnimationFrame(bornClock);
// The land is felt rather than heard: nothing else on the bar knows the bubble arrived.
setTimeout(() => stirLiquid(200), CLOCK_TRAVEL * CLOCK_LAND);
