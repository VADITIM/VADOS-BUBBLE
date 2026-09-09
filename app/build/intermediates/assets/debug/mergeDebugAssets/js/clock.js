import { stirLiquid } from './liquid.js';
import { rubberBandPast, toy, untoy } from './motion.js';
import { HOLD_MILLIS, bridge, root } from './state.js';

















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
  if (clockSeconds.children.length !== 2) {
    clockSeconds.replaceChildren(document.createElement('span'), document.createElement('span'));
  }
  clockSeconds.children[0].textContent = digits[0];
  clockSeconds.children[1].textContent = digits[1];
  
  
  
  clockDate.textContent = WEEKDAYS[now.getDay()] + ', ' + now.getDate() + '.' + (now.getMonth() + 1) + '.';
}










function tickClock() {
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


export function isClockLit() {
  return clockPill.classList.contains('lit');
}











export function fitClockProxy() {
  const box = clockPill.getBoundingClientRect();
  if (!isBorn || !isClockLit() || !box.width) {
    bridge.setClockProxy(0, 0, 0);
    return;
  }
  bridge.setClockProxy(Math.round(box.width), Math.round(box.bottom), Math.round(box.left));
}


export function clockHolds(x, y) {
  if (!isClockLit()) return false;
  const box = clockPill.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
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
