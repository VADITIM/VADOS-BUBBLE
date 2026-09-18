import { stirLiquid } from './liquid.js';
import { POP_IN, popIn, popOut, rubberBandPast, toy, untoy } from './motion.js';
import { HOLD_MILLIS, bridge, root, shared } from './state.js';

















const clockPill = document.getElementById('clock');
const clockFace = document.getElementById('clock-face');
const clockSeconds = document.getElementById('clock-seconds');
const clockDate = document.getElementById('clock-date');


const WEEKDAYS = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];


const CLOCK_LEFT = 14;


const CLOCK_PULL = 34;


const CLOCK_TRAVEL = 420;
const CLOCK_LAND = 0.42;


const CLOCK_TICK = 260;

let isBorn = false;





function paintClock() {
  const now = new Date();
  const minutes = now.getMinutes();
  const seconds = now.getSeconds();
  clockFace.textContent = now.getHours() + ':' + (minutes < 10 ? '0' : '') + minutes;
  
  
  const digits = (seconds < 10 ? '0' : '') + seconds;
  /* The colon is a part of the seconds rather than a pseudo-element on them, so it turns in with the digits it belongs to instead of appearing beside them. Collapsed away on the bar, where the seconds are stacked and have nothing to be separated from. */
  if (clockSeconds.children.length !== 3) {
    const parts = [document.createElement('span'), document.createElement('span'), document.createElement('span')];
    parts[0].id = 'clock-colon';
    parts[0].textContent = ':';
    clockSeconds.replaceChildren(...parts);
  }
  clockSeconds.children[1].textContent = digits[0];
  clockSeconds.children[2].textContent = digits[1];
  
  
  
  clockDate.textContent = WEEKDAYS[now.getDay()] + ', ' + now.getDate() + '.' + (now.getMonth() + 1) + '.';
}










function tickClock() {
  /* The tick is the page's only unconditional heartbeat, and behind a hidden stage it was still writing four text nodes a second and stirring the liquid on every minute — style, layout and a frame's worth of measuring for a surface nobody can see. It sleeps with the stage and repaints on the way back. */
  if (shared.isStageHidden) {
    setTimeout(tickClock, 1000 - new Date().getMilliseconds() + 20);
    return;
  }
  const was = clockFace.textContent;
  paintClock();
  
  if (isBorn && clockFace.textContent !== was) {
    clockPill.classList.add('ticking');
    stirLiquid(CLOCK_TICK * 2);
    setTimeout(() => clockPill.classList.remove('ticking'), CLOCK_TICK);
    
    
    fitClockProxy();
  }
  const now = new Date();
  setTimeout(tickClock, 1000 - now.getMilliseconds() + 20);
}


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


/* The Clock's own handover into the dashboard corner and back, on the interface's one pop. The hours and minutes are deliberately not in it: they are the same reading at both sizes, so the only parts handed over are the seconds — which change form, colon and all — and the date, which is arriving. The seconds shake out, take their grown form while they are standing at nothing, and turn back in at it, because the form change is exactly what the empty frame is for. */

/* Mirrors --ease-split and --grow-ms in pill.css. */
const CORNER_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';
const CORNER_TRAVEL = 340;

let secondsHandover = 0;
let facePutBack = null;
let pinnedSeconds = [];
let heldParts = [];

function carryFace(acrossX, downY) {
  return clockFace.animate(
    { transform: ['translate(' + acrossX + 'px, ' + downY + 'px)', 'translate(0px, 0px)'] },
    { duration: CORNER_TRAVEL, easing: CORNER_EASE, composite: 'add' }
  );
}

function readLength(styles, name, fallback) {
  const value = parseFloat(styles.getPropertyValue(name));
  return Number.isNaN(value) ? fallback : value;
}

/* Mirrors #clock's own placement in pill.css: --clock-left over --grab on the bar, --corner-inset on both axes in the corner, `left` on --clock-width-ms and `top` on --grow-ms. The two axes are read apart because they are not on one clock: status.js pins --clock-width-ms at 0ms for the open, so `left` lands in the frame handOverClock's own layout read flushes while `top` eases the whole way. Read before a class is toggled — the properties are never transitioned, and the box they place is. */
function cornerOriginShift(isGrown) {
  const styles = getComputedStyle(clockPill);
  const inset = readLength(styles, '--corner-inset', 10);
  const acrossX = inset - readLength(styles, '--clock-left', CLOCK_LEFT);
  const downY = inset - readLength(styles, '--grab', 0);
  return {
    acrossX: isGrown ? acrossX : -acrossX,
    downY: isGrown ? downY : -downY,
    acrossMillis: readLength(styles, '--clock-width-ms', 300),
    downMillis: readLength(styles, '--grow-ms', 340)
  };
}

/* The Clock's top-left is what a pinned part hangs off, and it was taken for the one point that does not move — it is the one point that does, since the box is placed from a different pair of properties at either end of the journey. So a part standing perfectly still in the layout was carried across the screen by the whole of that shift: down towards the minute's landing place on the way in, up and left away from the time on the way out. Handing it the face's own travel instead was worse rather than better, because a pinned part is not in flow and so has no jump to be given back — it starts where it was measured, and what it needs is the corner's shift taken back off it on the clock each axis of the box actually moves on. */
function holdStill(part, shift) {
  return [
    ['translate(' + -shift.acrossX + 'px, 0px)', shift.acrossMillis],
    ['translate(0px, ' + -shift.downY + 'px)', shift.downMillis]
  ].map(([to, duration]) => part.animate(
    { transform: ['translate(0px, 0px)', to] },
    { duration, easing: CORNER_EASE, composite: 'add', fill: 'forwards' }
  ));
}

function releaseSeconds() {
  heldParts.forEach((animation) => animation.cancel());
  heldParts = [];
  pinnedSeconds.forEach((part) => {
    part.classList.remove('standing-still');
    part.style.removeProperty('left');
    part.style.removeProperty('top');
  });
  pinnedSeconds = [];
  clockSeconds.style.removeProperty('height');
}

/* Held where it already stood while the box retracts out from under it: in flow it gave its max-width and its margin back at the same time and slid on the way out. What the shrinking box does not cover any more is cut, which is what `overflow: hidden` on the Clock is for and why it is worn only while this is running. */
function pinCornerDate() {
  const box = clockDate.getBoundingClientRect();
  if (!box.width) return;
  const around = clockPill.getBoundingClientRect();
  clockDate.style.left = Math.round(box.left - around.left) + 'px';
  clockDate.style.top = Math.round(box.top - around.top) + 'px';
  clockDate.classList.add('handed-over');
  clockPill.classList.add('corner-leaving');
  setTimeout(() => {
    clockPill.classList.remove('corner-leaving');
    clockDate.classList.remove('handed-over');
    clockDate.style.removeProperty('left');
    clockDate.style.removeProperty('top');
  }, CORNER_TRAVEL);
}

function handOverClock(isGrown) {
  releaseSeconds();
  const was = clockFace.getBoundingClientRect();
  const around = clockPill.getBoundingClientRect();
  const standing = [...clockSeconds.children].map((part) => {
    const box = part.getBoundingClientRect();
    return { part, left: box.left - around.left, top: box.top - around.top };
  });
  const tall = clockSeconds.offsetHeight;
  const shift = cornerOriginShift(isGrown);
  if (!isGrown) pinCornerDate();
  clockPill.classList.toggle('corner-grow', isGrown);
  /* The seconds are the width the layout will need before they are wearing it, so the row reflows once — here — rather than a second time when the form arrives in the middle of the pop. */
  clockSeconds.style.removeProperty('width');
  clockPill.classList.toggle('corner-seconds', isGrown);
  const wide = clockSeconds.offsetWidth;
  clockPill.classList.toggle('corner-seconds', !isGrown);
  clockSeconds.style.width = wide + 'px';
  /* The column the corner stacks, the size the digits take and the room the seconds ask for all land in one frame, and the hours and minutes were wherever that left them — a reading teleporting for a change that is not about it. The difference is measured and given back as one additive travel, so the time is carried to its new place over the same stretch the box takes to grow. Additive because `transform` is the drag's and the pop's as well. */
  const now = clockFace.getBoundingClientRect();
  facePutBack?.cancel();
  facePutBack = carryFace(was.left - now.left, was.top - now.top);

  /* The seconds were shaking out over the same frames the corner's reflow was carrying them across, so they were seen sliding to their grown place before they left it. Each digit is taken out of the flow and pinned where it already stood, and what is left standing in the row is the width and nothing else. */
  clockSeconds.style.height = tall + 'px';
  standing.forEach(({ part, left, top }) => {
    part.style.left = left + 'px';
    part.style.top = top + 'px';
    part.classList.add('standing-still');
  });
  pinnedSeconds = standing.map(({ part }) => part);
  heldParts = standing.flatMap(({ part }) => holdStill(part, shift));
  if (!isGrown) heldParts.push(...holdStill(clockDate, shift));
  popOut([...clockSeconds.children]);
  clearTimeout(secondsHandover);
  secondsHandover = setTimeout(() => {
    releaseSeconds();
    clockPill.classList.toggle('corner-seconds', isGrown);
    popIn([...clockSeconds.children]);
  }, CORNER_TRAVEL);
  return Math.max(CORNER_TRAVEL + POP_IN, isGrown ? popIn([clockDate]) : popOut([clockDate]));
}

export function enterCornerClock() {
  return handOverClock(true);
}

export function leaveCornerClock() {
  return handOverClock(false);
}


export function wakeClock() {
  if (!isBorn) return;
  paintClock();
  fitClockProxy();
}


export function isClockLit() {
  return clockPill.classList.contains('lit');
}











// The clock's touchable window was the clock's own box to the pixel, so a tap on the Now bubble standing there landed beside it as often as on it. Mirrored by `clockHolds`, which decides the same thing on the page's side.
const CLOCK_GRACE = 10;

export function fitClockProxy() {
  const box = clockPill.getBoundingClientRect();
  if (!isBorn || !isClockLit() || !box.width) {
    bridge.setClockProxy(0, 0, 0);
    return;
  }
  bridge.setClockProxy(
    Math.round(box.width + CLOCK_GRACE * 2),
    Math.round(box.bottom),
    Math.round(box.left - CLOCK_GRACE)
  );
}


export function clockHolds(x, y) {
  if (!isClockLit()) return false;
  const box = clockPill.getBoundingClientRect();
  return x >= box.left - CLOCK_GRACE && x <= box.right + CLOCK_GRACE &&
    y >= box.top && y <= box.bottom;
}

let clockHeld = false;
let clockDownAt = null;
let clockHoldTimer = 0;











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
  
  
  
}

export { clockPill };

root.style.setProperty('--clock-left', CLOCK_LEFT + 'px');
tickClock();


requestAnimationFrame(bornClock);

setTimeout(() => stirLiquid(200), CLOCK_TRAVEL * CLOCK_LAND);
