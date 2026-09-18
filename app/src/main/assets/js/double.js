import { stirLiquid } from './liquid.js';
import { toClosed } from './row.js';
import { GROWN_PAD, bridge, pill, root } from './state.js';

const doublePill = document.getElementById('double');
const doubleReading = document.getElementById('double-reading');

const DUPLICATE_TRAVEL = 460;

const DUPLICATE_BOTTOM = 40;

let isOut = false;
let homing = null;

export function duplicateReach() {
  return root.clientHeight - DUPLICATE_BOTTOM - Math.round(pill.getBoundingClientRect().top);
}

// The duplicate was born against the bubble's idle top and traded places against it too, but the menu grows the bubble and `html.grown` drops a grown bubble by `--grown-pad` — so the copy stood 26px above the box it was copying and the swap put each bubble 26px past the other's seat instead of into it.
function grownTop() {
  const grab = parseFloat(getComputedStyle(root).getPropertyValue('--grab')) || 0;
  return Math.round(grab + GROWN_PAD);
}

export function duplicateDouble(reading, box) {
  if (isOut) return;
  clearSwap();
  isOut = true;
  doubleReading.textContent = reading;

  const seat = root.clientHeight - DUPLICATE_BOTTOM - box.height;
  swapSpan = Math.round(seat - grownTop());
  root.style.setProperty('--duplicate-width', box.width + 'px');
  root.style.setProperty('--duplicate-height', box.height + 'px');
  root.style.setProperty('--double-bottom', DUPLICATE_BOTTOM + 'px');
  root.style.setProperty('--double-rise', -swapSpan + 'px');

  root.classList.add('double-live');
  doublePill.classList.add('duplicate', 'born', 'showing');
  requestAnimationFrame(() => {
    doublePill.classList.remove('born');
    doublePill.classList.add('out');
  });
  stirLiquid(DUPLICATE_TRAVEL + 300);
}

const SWAP_TRAVEL = 460;
const SWAP_PULL = 22;

let isSwapped = false;
let swapSpan = 0;
let swapping = null;
let swapStartY = 0;
let swapActed = false;

export function isDuplicateLive() {
  return doublePill.classList.contains('duplicate');
}

export function isDuplicateSwapped() {
  return isSwapped;
}

export function duplicateHolds(x, y) {
  if (!isDuplicateLive()) return false;
  const box = doublePill.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}

export function swapDuplicate() {
  if (!isDuplicateLive()) return;
  isSwapped = !isSwapped;
  bridge.triggerHaptic('expand');
  root.style.setProperty('--swap-drop', (isSwapped ? swapSpan : 0) + 'px');
  doublePill.style.setProperty('--double-rise', (isSwapped ? -swapSpan : 0) + 'px');
  root.classList.add('swapping');
  clearTimeout(swapping);
  swapping = setTimeout(() => root.classList.remove('swapping'), SWAP_TRAVEL);
  stirLiquid(SWAP_TRAVEL + 300);
}

function clearSwap() {
  isSwapped = false;
  clearTimeout(swapping);
  root.classList.remove('swapping');
  root.style.removeProperty('--swap-drop');
  doublePill.style.removeProperty('--double-rise');
}

// Which gesture a bubble answers is where it stands, and reading it off the swap flag made that flag a second account of the arrangement: a duplicate born while the flag was still set from a menu that closed some other way answered the seat it was not in, so a pull down on the upper bubble closed the menu instead of trading places.
export function isDuplicateOnTop() {
  return doublePill.getBoundingClientRect().top < pill.getBoundingClientRect().top;
}

export function duplicateTouch(action, x, y) {
  if (action === 'down') {
    swapStartY = y;
    swapActed = false;
    return;
  }
  if (action !== 'move' || swapActed) return;
  const travel = y - swapStartY;
  if (Math.abs(travel) < SWAP_PULL) return;
  swapActed = true;
  if (isDuplicateOnTop() && travel > 0) swapDuplicate();
  else if (!isDuplicateOnTop() && travel < 0) toClosed();
}

export function isDoubleOut() {
  return doublePill.classList.contains('showing');
}

export { doublePill };

export function retireDuplicate() {
  if (!isOut || !doublePill.classList.contains('duplicate')) return;
  isOut = false;
  clearSwap();

  // The duplicate was retired on the Double's own 380ms while its travel runs for 460, so it reached the bubble, had its classes stripped 80ms early and fell back to its seat in full view, reading and all. It goes home on its own clock now, and on the geometry it is going to rather than the one it is leaving.
  const style = getComputedStyle(root);
  const idleHeight = parseFloat(style.getPropertyValue('--pill-height')) || 26;
  const grab = parseFloat(style.getPropertyValue('--grab')) || 0;
  root.style.setProperty(
    '--double-rise',
    Math.round(grab - (root.clientHeight - DUPLICATE_BOTTOM - idleHeight)) + 'px'
  );
  doublePill.classList.remove('out');
  doublePill.classList.add('merging');

  root.classList.add('notifications-homing');
  clearTimeout(homing);
  homing = setTimeout(() => root.classList.remove('notifications-homing'), DUPLICATE_TRAVEL);

  setTimeout(() => {
    if (isOut) return;
    doublePill.classList.remove('showing', 'duplicate', 'merging');
    root.classList.remove('double-live');
  }, DUPLICATE_TRAVEL);
  stirLiquid(DUPLICATE_TRAVEL + 300);
}
