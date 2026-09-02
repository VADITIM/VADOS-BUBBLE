import { catchInto, stirLiquid } from './liquid.js';
import { bridge, pill, root } from './state.js';

/**
 * The bubble beside the punch hole, which is a second Main rather than anything owned by the
 * first: it stands where the bubble stands, it is not a mod and not a satellite, and it runs
 * alongside whatever the bubble is doing rather than taking its slot. That is what makes it
 * Double.
 *
 * It holds the system's own short-lived announcements — charging, low battery, a recording
 * that ended. Nothing about it is interactive, so like the Clock bubble it costs no window: it
 * appears, says one thing for a few seconds, and goes.
 *
 * The battery lives here rather than in the row because a battery is not a mod. A mod is true
 * for as long as it is true and the bubble carries it; a charge level is always true, and a
 * bubble carrying it permanently would be a status bar. What is news is the moment it changed
 * — past a mark, or plugged in — and news beside the bubble is exactly what this bubble is.
 */
const doublePill = document.getElementById('double');
const doubleReading = document.getElementById('double-reading');

/** Long enough to look at, short enough that it is over before it is in the way. */
const DOUBLE_DWELL = 4200;

/** The gap it keeps from the bubble's edge, the same reach the Now bubble rests at. */
const DOUBLE_REACH = 6;

/** Out of the hole, and home again the same way. */
const DOUBLE_TRAVEL = 380;

const DOUBLE_COLOURS = {
  low: '#ffb300',
  critical: '#ff3b30',
  charging: '#5bfd5b',
};

let dwell = null;
let magnet = null;
let isOut = false;

/**
 * Magnetic against the bubble's edge, measured rather than placed.
 *
 * It cannot be given a fixed spot: the bubble beside it is a different width under every mod
 * and three times that wide with an alert open, so a Double parked at the closed row's edge is
 * a Double drawn *inside* an alert the moment one arrives — two shapes overlapping instead of
 * two bodies of liquid touching. So it rides the bubble's own right edge every frame it is
 * out, which also means it is pushed along by a growth rather than jumped over by one.
 */
function clingToPill() {
  if (!isOut) return;
  const bubble = pill.getBoundingClientRect();
  const mine = doublePill.getBoundingClientRect();
  const middle = window.innerWidth / 2;
  // Where its own left edge would fall if nothing moved it, so the offset is a difference
  // rather than an absolute — the element is laid out from the middle of the screen like the
  // bubble is, and the transform is what carries it out to the edge.
  const resting = middle - mine.width / 2;
  const wanted = bubble.right + DOUBLE_REACH;
  root.style.setProperty('--double-out', Math.round(wanted - resting) + 'px');
  magnet = requestAnimationFrame(clingToPill);
}

/** The announcement itself: one thing, said once, beside whatever the bubble is doing. */
export function announceDouble(payload) {
  const level = Math.max(0, Math.min(100, Number(payload.level) || 0));
  root.style.setProperty('--double-accent', DOUBLE_COLOURS[payload.state] || DOUBLE_COLOURS.low);
  // Never below a sliver: a battery drawn as empty says the phone is off.
  // The property rather than the width, because the charging run animates *from* it: a width
  // written straight onto the element is a second author on the one thing the keyframes own.
  root.style.setProperty('--double-level', Math.max(8, level) + '%');
  doubleReading.textContent =
    payload.state === 'charging' ? 'Charging · ' + level + ' %' : level + ' %';
  doublePill.classList.toggle('charging', payload.state === 'charging');

  clearTimeout(dwell);
  if (!isOut) {
    isOut = true;
    doublePill.classList.add('showing');
    // Its spot is worked out before it is asked to travel, or the first frame of the flight
    // is a jump to wherever the last one left the offset.
    clingToPill();
    requestAnimationFrame(() => doublePill.classList.add('out'));
    bridge.triggerHaptic('notification');
  }
  stirLiquid(DOUBLE_TRAVEL + 300);
  dwell = setTimeout(retireDouble, DOUBLE_DWELL);
}

/**
 * Home again, which is a merge and not a disappearance: it goes back into the bubble it came
 * out of, and the bubble takes the knock. The offset is given up first and the shape follows
 * it in, so what is seen is one drop being taken back rather than a box fading beside another.
 */
function retireDouble() {
  if (!isOut) return;
  isOut = false;
  cancelAnimationFrame(magnet);
  magnet = null;
  root.style.setProperty('--double-out', '0px');
  doublePill.classList.remove('out');
  catchInto(doublePill, pill, {
    swallowed: () => {
      doublePill.classList.remove('showing', 'charging');
    },
  });
  stirLiquid(DOUBLE_TRAVEL + 400);
}

/** Whether it is standing beside the bubble, which is what the skin asks before mirroring it. */
export function isDoubleOut() {
  return doublePill.classList.contains('showing');
}

export { doublePill };

/**
 * A battery event, which is the first thing this bubble carries. It used to borrow the main
 * bubble's alert state, which meant a charge level took the bubble away from a song for four
 * seconds — the one thing a Double exists not to do.
 */
window.onBattery = payload => {
  if (!payload) return;
  announceDouble(payload);
};
