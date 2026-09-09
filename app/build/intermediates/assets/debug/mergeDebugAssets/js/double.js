import { announceCharge } from './status.js';
import { stirLiquid } from './liquid.js';
import { bridge, root } from './state.js';
























const doublePill = document.getElementById('double');
const doubleReading = document.getElementById('double-reading');


const DOUBLE_DWELL = 4200;






const DOUBLE_BOTTOM = 120;


const DOUBLE_RISE = 40;


const DOUBLE_TRAVEL = 380;

const DOUBLE_COLOURS = {
  low: '#ffb300',
  critical: '#ff3b30',
};

let dwell = null;
let isOut = false;


export function announceDouble(payload) {
  const level = Math.max(0, Math.min(100, Number(payload.level) || 0));
  root.style.setProperty('--double-accent', DOUBLE_COLOURS[payload.state] || DOUBLE_COLOURS.low);
  
  
  root.style.setProperty('--double-level', Math.max(8, level) + '%');
  doubleReading.textContent = level + ' %';

  clearTimeout(dwell);
  if (!isOut) {
    isOut = true;
    
    
    root.classList.add('double-live');
    doublePill.classList.add('showing');
    requestAnimationFrame(() => doublePill.classList.add('out'));
    bridge.triggerHaptic('notification');
  }
  stirLiquid(DOUBLE_TRAVEL + 300);
  dwell = setTimeout(retireDouble, DOUBLE_DWELL);
}









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


export function isDoubleOut() {
  return doublePill.classList.contains('showing');
}

export { doublePill };

root.style.setProperty('--double-bottom', DOUBLE_BOTTOM + 'px');
root.style.setProperty('--double-rise', DOUBLE_RISE + 'px');












window.onBattery = payload => {
  if (!payload) return;
  announceCharge(payload.state);
};
