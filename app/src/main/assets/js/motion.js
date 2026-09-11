import { stirLiquid } from './liquid.js';
import { openPlayer } from './mods/media.js';
import { openCurrent, openPicture } from './mods/notification.js';
import { openTimer } from './mods/timer.js';
import { closeNowPanel, nowOpen } from './now.js';
import { clockPill } from './clock.js';
import { lockPill } from './lock.js';
import { closeStatusPanel, statusOpen, statusPanelTarget, statusPill } from './status.js';
import { abandonSwap, becomeExtended, applyClosedWindow, applyWindow, closedTarget, dragSwap, ensureClosedWindow, liveMods, releaseSwap, toClosed } from './row.js';
import { CLOSED, HOLD_GRACE, HOLD_MILLIS, HOLD_SCALE, bridge, pill, root, shared } from './state.js';
import { MOD_FACES, leaveForMod, openHistory, openMod } from './tabs.js';






















export const HOLD_BLOCK = 18;









export const HOLD_WANDER = 150;











const DRAG_RADIUS = 40;









const HISTORY_DRAG_SCALE = 0.15;

function dragRadius() {
  return shared.size === 'history' ? DRAG_RADIUS * HISTORY_DRAG_SCALE : DRAG_RADIUS;
}






function rubberBand(travel) {
  const radius = dragRadius();
  const past = Math.max(0, travel - HOLD_BLOCK);
  return radius * past / (past + radius);
}


const HOME_MILLISECONDS = 520;
const HOME_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';


const springs = new WeakMap();












function springHome(element, from) {
  cancelSpring(element);
  const spring = element.animate(
    { transform: [from, 'translate(0px, 0px) scale(1)'] },
    { duration: HOME_MILLISECONDS, easing: HOME_EASE, composite: 'add' },
  );
  springs.set(element, spring);
}


export function cancelSpring(element) {
  const spring = springs.get(element);
  if (spring) spring.cancel();
}













export function rubberBandPast(dx, dy) {
  return Math.hypot(dx, dy) > HOLD_BLOCK;
}

export function toy(element, prefix, dx, dy) {
  const travel = Math.hypot(dx, dy);
  const reach = rubberBand(travel);
  const scale = travel > 0 ? reach / travel : 0;
  
  
  
  if (travel > HOLD_BLOCK) endHold();
  cancelSpring(element);
  element.style.setProperty(prefix + '-x', (dx * scale).toFixed(2) + 'px');
  element.style.setProperty(prefix + '-y', (dy * scale).toFixed(2) + 'px');
  stirLiquid(160);
}








export function untoy(element, prefix) {
  const x = element.style.getPropertyValue(prefix + '-x');
  const y = element.style.getPropertyValue(prefix + '-y');
  if (!x && !y) return;
  element.style.removeProperty(prefix + '-x');
  element.style.removeProperty(prefix + '-y');
  springHome(element, `translate(${x || '0px'}, ${y || '0px'})`);
  stirLiquid(600);
}















const PULL_TRIGGER = 22;







const PULL_STOLEN = 12;



pill.addEventListener('transitionend', event => {
  if (event.propertyName !== 'height' && event.propertyName !== 'width') return;
  if (CLOSED.has(shared.size)) applyClosedWindow();
});

let startY = 0;
let startX = 0;
let hasMoved = false;
let hasGrown = false;
let hasSwiped = false;


let hasPulled = false;


let pullDrop = 0;


let hasWandered = false;


let heldStep = null;


let holdArmedAt = 0;







function grantHoldWander() {
  if (hasWandered || !holdPending || !heldStep) return;
  hasWandered = true;
  
  
  const served = Date.now() - holdArmedAt;
  clearTimeout(shared.holdTimer);
  shared.holdTimer = setTimeout(heldStep, Math.max(0, HOLD_MILLIS - served) + HOLD_WANDER);
}





let hasDismissed = false;








function closedWindow() {
  const live = liveMods();
  if (!live.length) return { width: shared.compact.width, height: shared.compact.height };
  return { width: closedTarget().width, height: shared.compact.height };
}

function beginHold() {
  root.classList.add('holding');
  
  
  
  
  clearTimeout(shared.growTimer);
  shared.growTimer = setTimeout(() => {
    
    
    
    
    
    
    if (root.classList.contains('dragging')) return;
    
    
    const base = closedWindow();
    
    
    
    const main = closedTarget().main || shared.compact.width;
    hasGrown = true;
    applyWindow({
      width: Math.ceil(base.width + main * (HOLD_SCALE - 1)) + 2,
      height: Math.ceil(base.height * HOLD_SCALE) + 2,
    });
  }, HOLD_GRACE);
}


let holdPending = false;
export function endHold() {
  clearTimeout(shared.holdTimer);
  holdPending = false;
  root.classList.remove('pressing');
  clearTimeout(shared.growTimer);
  hasGrown = false;
  
  if (shared.state === 'haptic' && !shared.hasHeld) shared.state = 'idle';
  if (!root.classList.contains('holding')) return;
  root.classList.remove('holding');
  ensureClosedWindow();
}

pill.addEventListener('touchstart', event => {
  startY = event.touches[0].clientY;
  startX = event.touches[0].clientX;
  hasMoved = false;
  shared.hasHeld = false;
  hasGrown = false;
  hasSwiped = false;
  hasDismissed = false;
  hasPulled = false;
  pullDrop = 0;
  hasWandered = false;
  
  
  heldStep = null;

  
  
  
  
  
  
  if (shared.state === 'idle' && liveMods().length) {
    shared.state = 'haptic';
    holdPending = true;
    beginHold();
    
    
    heldStep = () => {
      holdPending = false;
      
      
      const held = liveMods()[0];
      if (!held) {
        endHold();
        return;
      }
      shared.hasHeld = true;
      root.classList.remove('holding');
      
      
      
      abandonSwap();
      bridge.triggerHaptic('expand');
      leaveForMod(held);
      toClosed();
    };
    holdArmedAt = Date.now();
    shared.holdTimer = setTimeout(heldStep, HOLD_MILLIS);
    return;
  }

  
  
  
  if (shared.state === 'extended' && MOD_FACES[shared.size]) {
    root.classList.add('pressing');
    holdPending = true;
    heldStep = () => {
      holdPending = false;
      shared.hasHeld = true;
      root.classList.remove('pressing');
      bridge.triggerHaptic('expand');
      openMod();
      toClosed();
    };
    holdArmedAt = Date.now();
    shared.holdTimer = setTimeout(heldStep, HOLD_MILLIS);
  }
}, { passive: true });

pill.addEventListener('touchmove', event => {
  
  
  
  
  
  if (shared.hasHeld) return;
  const verticalTravel = event.touches[0].clientY - startY;
  const horizontalTravel = Math.abs(event.touches[0].clientX - startX);
  const sideways = event.touches[0].clientX - startX;
  
  
  
  
  if (Math.hypot(horizontalTravel, verticalTravel) <= HOLD_BLOCK) {
    grantHoldWander();
    return;
  }
  pullDrop = Math.max(pullDrop, verticalTravel);
  
  
  
  
  
  const dragging = root.classList.contains('dragging');
  if (dragging || hasSwiped) untoy(pill, '--drag');
  else toy(pill, '--drag', sideways, verticalTravel);
  
  
  
  if (!hasPulled && shared.state !== 'extended' && CLOSED.has(shared.size) && !hasSwiped &&
      !dragging && verticalTravel > PULL_TRIGGER && verticalTravel > horizontalTravel) {
    hasPulled = true;
    hasMoved = true;
    endHold();
    untoy(pill, '--drag');
    bridge.triggerHaptic('expand');
    openHistory();
    return;
  }
  
  
  const committed = Math.abs(sideways) - HOLD_BLOCK;
  if (!hasPulled && shared.state !== 'extended' && CLOSED.has(shared.size) && committed > 0 &&
      horizontalTravel > Math.abs(verticalTravel)) {
    if (dragSwap(Math.sign(sideways) * committed)) {
      hasSwiped = true;
      hasMoved = true;
      return;
    }
  }
  if (!hasDismissed && verticalTravel < -24 && horizontalTravel < 40) {
    hasDismissed = true;
    hasMoved = true;
    
    
    endHold();
    bridge.triggerHaptic('dismiss');
    bridge.onSwipeDismiss();
    toClosed();
    return;
  }
}, { passive: true });

pill.addEventListener('touchend', () => {
  releaseSwap();
  endHold();
  untoy(pill, '--drag');
}, { passive: true });







pill.addEventListener('touchcancel', () => {
  releaseSwap();
  untoy(pill, '--drag');
  
  
  
  if (!hasPulled && !shared.hasHeld && shared.state !== 'extended' && CLOSED.has(shared.size) &&
      pullDrop > PULL_STOLEN) {
    hasPulled = true;
    endHold();
    bridge.triggerHaptic('expand');
    openHistory();
    return;
  }
  if (shared.state === 'haptic' && hasGrown) return;
  endHold();
}, { passive: true });

pill.addEventListener('click', () => {
  if (hasMoved || shared.hasHeld) return;

  
  if (shared.state === 'extended') {
    bridge.triggerHaptic('tap');
    toClosed();
    return;
  }

  if (shared.state === 'alert') {
    clearTimeout(shared.dwellTimer);
    
    
    if (pill.classList.contains('with-image') && shared.size !== 'picture') {
      bridge.triggerHaptic('expand');
      openPicture();
      return;
    }
    openCurrent();
    return;
  }

  
  
  
  becomeExtended();
  bridge.triggerHaptic('expand');
  
  
  
  const owner = liveMods()[0];
  if (owner === 'timer') {
    openTimer();
    return;
  }
  if (owner === 'media') {
    openPlayer();
    return;
  }
  
  
  
  if (owner === 'call' && shared.call && shared.call.key) {
    bridge.openNotification(shared.call.key);
    toClosed();
    return;
  }
  openHistory();
});






window.onOutsideTap = (x, y) => {
  
  
  
  
  
  
  
  
  const hit = document.elementFromPoint(x, y);
  if (hit && (pill.contains(hit) || clockPill.contains(hit) || statusPill.contains(hit))) return;
  // Every press inside the open panel closed it: a proxy window reports a touch outside itself, so each of the other four called this with a point that was on a quick control, and a control is in none of the three bubbles named above. The panel answers for its own controls now, and a point between them still closes.
  if (statusPanelTarget(x, y)) return;
  if (statusOpen && hit && lockPill.contains(hit)) return;
  if (shared.state === 'extended') toClosed();
  
  
  
  if (nowOpen) closeNowPanel();
  if (statusOpen) closeStatusPanel();
};
