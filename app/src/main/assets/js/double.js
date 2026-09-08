import { announceCharge } from './status.js';
import { stirLiquid } from './liquid.js';
import { bridge, root } from './state.js';

/**
 * The bubble that carries the system's own short-lived announcements — a battery running out,
 * a recording that ended — standing at the bottom of the screen, centred, where the thumb
 * already is.
 *
 * It is a second Main rather than anything owned by the first: it is not a mod and not a
 * satellite, and it runs alongside whatever the bubble at the cutout is doing rather than
 * taking its slot. That is what makes it Double, and it is truer down here than it was up
 * there. It used to stand beside the punch hole and ride the main bubble's right edge every
 * frame — because the bubble beside it is a different width under every mod and three times
 * that wide with an alert open, so a Double parked at the closed row's edge is a Double drawn
 * *inside* an alert the moment one arrives. All of that machinery existed to keep two shapes
 * out of one corner, and the corner is not one it needed: what this bubble says has nothing to
 * do with what the row is carrying.
 *
 * The battery lives here rather than in the row because a battery is not a mod. A mod is true
 * for as long as it is true and the bubble carries it; a charge level is always true, and a
 * bubble carrying it permanently would be a status bar. What is news is the moment it changed
 * — past a mark, or plugged in — and news that does not take the bubble away from a song is
 * exactly what this bubble is for.
 *
 * Nothing about it is interactive, so it costs no window.
 */
const doublePill = document.getElementById('double');
const doubleReading = document.getElementById('double-reading');

/** Long enough to look at, short enough that it is over before it is in the way. */
const DOUBLE_DWELL = 4200;

/**
 * How far off the bottom edge it stands, in pixels. High enough to clear One UI's gesture bar,
 * low enough to still read as belonging to the bottom of the screen rather than floating in it.
 * Mirrors --double-bottom in pill.css, which is where the layout reads it.
 */
const DOUBLE_BOTTOM = 120;

/** How far below its place it waits, and the distance it rises through. */
const DOUBLE_RISE = 40;

/** Out of the edge, and back down the same way. */
const DOUBLE_TRAVEL = 380;

const DOUBLE_COLOURS = {
  low: '#ffb300',
  critical: '#ff3b30',
};

let dwell = null;
let isOut = false;

/** The announcement itself: one thing, said once, at the bottom of the screen. */
export function announceDouble(payload) {
  const level = Math.max(0, Math.min(100, Number(payload.level) || 0));
  root.style.setProperty('--double-accent', DOUBLE_COLOURS[payload.state] || DOUBLE_COLOURS.low);
  // Never below a sliver: a battery drawn as empty says the phone is off. A property rather than
  // a width written on the element, because the heartbeat keyframes own that one thing.
  root.style.setProperty('--double-level', Math.max(8, level) + '%');
  doubleReading.textContent = level + ' %';

  clearTimeout(dwell);
  if (!isOut) {
    isOut = true;
    // The filter's region is the row's band until something says otherwise, and this bubble is
    // at the far end of the screen from it — see html.double-live in pill.css.
    root.classList.add('double-live');
    doublePill.classList.add('showing');
    requestAnimationFrame(() => doublePill.classList.add('out'));
    bridge.triggerHaptic('notification');
  }
  stirLiquid(DOUBLE_TRAVEL + 300);
  dwell = setTimeout(retireDouble, DOUBLE_DWELL);
}

/**
 * Home again, which is the arrival backwards: it sinks back through the edge it rose out of.
 * It used to be a merge into the main bubble, which is what a bubble standing at the cutout
 * owes the bubble it is standing beside — down here there is nothing at the bottom of the
 * screen for it to be taken back into, and a merge into a shape most of the screen away is a
 * journey, not a merge. `showing` comes off only once the sink has finished, or the shape
 * blinks out where it stands instead of leaving.
 */
function retireDouble() {
  if (!isOut) return;
  isOut = false;
  doublePill.classList.remove('out');
  setTimeout(() => {
    if (isOut) return;
    doublePill.classList.remove('showing');
    root.classList.remove('double-live');
  }, DOUBLE_TRAVEL);
  stirLiquid(DOUBLE_TRAVEL + 400);
}

/** Whether it is standing on the screen, which is what the skin asks before mirroring it. */
export function isDoubleOut() {
  return doublePill.classList.contains('showing');
}

export { doublePill };

root.style.setProperty('--double-bottom', DOUBLE_BOTTOM + 'px');
root.style.setProperty('--double-rise', DOUBLE_RISE + 'px');

/**
 * A battery event, and the two halves of it no longer land in the same place.
 *
 * **Every one of them is the Status bubble's now** — plugged in, running low and nearly out alike:
 * the news and the reading it is news about belong to one shape, and a bubble opening at the
 * bottom of the screen to say something about a number standing at the top of it is two places for
 * one fact. All three are one announcement in three moods, told apart by the mark in the cell and
 * by the colour. Nothing about a battery reaches the Double any more; what is left for it is the
 * announcement that has no reading anywhere on the bar — a recording that ended — and until that
 * is built `announceDouble` is the bubble waiting rather than the bubble working.
 */
window.onBattery = payload => {
  if (!payload) return;
  announceCharge(payload.state);
};
