import { stirLiquid } from './liquid.js';
import { root } from './state.js';

/**
 * The bubble over One UI's own clock, at the far left of the bar.
 *
 * It carries no mod, answers no touch and has no states: it is the time, which is the one
 * thing on this bar that is true without anything having happened. That is also why it costs
 * no window — a bubble nobody can touch needs no proxy, and every pixel a touchable window
 * covers is a pixel the shade swipe cannot start on.
 *
 * Its whole relationship is with the Now bubble beside it: the two must be able to neck when
 * the light comes on, and must never overlap. That is geometry rather than care — its width
 * is capped against `--now-left`, so there is no arrangement of the two in which this bubble
 * reaches the one standing on the flashlight chip.
 */
const clockPill = document.getElementById('clock');
const clockFace = document.getElementById('clock-face');

/** Mirrors --clock-left in pill.css: how far its left edge stands off the screen edge. */
const CLOCK_LEFT = 8;

/** Its flight out of the punch hole, the same one the Status bubble makes to the other end. */
const CLOCK_TRAVEL = 420;
const CLOCK_LAND = 0.42;

let isBorn = false;

/**
 * The phone is German and the bar it is standing on is 24-hour, so this is too — the point of
 * the bubble is that the eye reads it in the same place and the same shape as before.
 */
function paintClock() {
  const now = new Date();
  const minutes = now.getMinutes();
  clockFace.textContent = now.getHours() + ':' + (minutes < 10 ? '0' : '') + minutes;
}

/**
 * On the minute, not every minute: a timer of exactly 60,000ms drifts against the wall clock
 * and eventually turns over a second or two late, which on a clock is the whole of what it had
 * to get right. Each tick works out how long is left of the current minute and waits that.
 */
function tickClock() {
  paintClock();
  const now = new Date();
  const left = 60000 - (now.getSeconds() * 1000 + now.getMilliseconds());
  setTimeout(tickClock, left + 20);
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
  stirLiquid(CLOCK_TRAVEL + 300);
}

/** Whether it is standing on the bar, which is what the skin asks before mirroring it. */
export function isClockLit() {
  return clockPill.classList.contains('lit');
}

export { clockPill };

root.style.setProperty('--clock-left', CLOCK_LEFT + 'px');
tickClock();
// After the first paint, so the drop leaves the hole at the size it will arrive at rather than
// at whatever an empty box measured.
requestAnimationFrame(bornClock);
// The land is felt rather than heard: nothing else on the bar knows the bubble arrived.
setTimeout(() => stirLiquid(200), CLOCK_TRAVEL * CLOCK_LAND);
