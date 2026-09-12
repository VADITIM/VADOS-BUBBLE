import { isDuplicateLive, isDuplicateSwapped, swapDuplicate } from './double.js';
import { stirLiquid } from './liquid.js';
import { openPlayer } from './mods/media.js';
import { openCurrent, openPicture } from './mods/notification.js';
import { openTimer } from './mods/timer.js';
import { closeNowPanel, nowOpen } from './now.js';
import { clockPill } from './clock.js';
import { lockPill } from './lock.js';
import { closeStatusPanel, statusOpen, statusPanelTarget, statusPill } from './status.js';
import { abandonSwap, becomeExtended, applyClosedWindow, applyWindow, closedTarget, dragSwap, ensureClosedWindow, liveMods, releaseSwap, toClosed } from './row.js';
import { CLOSED, HOLD_GRACE, HOLD_MILLIS, HOLD_SCALE, bridge, dragGate, pill, root, shared } from './state.js';
import { MOD_FACES, leaveForMod, openNotifications, openMod } from './tabs.js';






















export const HOLD_BLOCK = 18;









export const HOLD_WANDER = 150;











const DRAG_RADIUS = 40;









const NOTIFICATIONS_DRAG_SCALE = 0.15;

function dragRadius() {
  return shared.size === 'notifications' ? DRAG_RADIUS * NOTIFICATIONS_DRAG_SCALE : DRAG_RADIUS;
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
let pillDrag = dragGate();


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
  pillDrag = dragGate();


  heldStep = null;

  // The same latch the touchmove and the bridge's click read: a press that has already collapsed the bubble arrives here against an idle row, where holding arms the mod swap — so the press that closed a panel would have gone on to trade the bubble's mod out from under it.
  if (shared.closedInTouch) return;

  
  
  
  
  
  
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
  // The last of the open-close-open, and the one the withheld click could not reach: a touch that closes the bubble on its way down leaves the rest of its own moves running against a bubble that is now closed and idle, so every gesture the closed row owns is live under a finger that never asked for one. A drift downwards then fed the pull, and the cancel the closing window itself provokes opened the notifications — an expand out of idle, from a touch that had just collapsed the thing. The touch is spent at the close, in this handler as well as in the bridge's.
  if (shared.closedInTouch) return;
  // A grown bubble has nothing to drag — the trade, the pull and the upward dismiss all belong to the closed row — and the band underneath them only leaned a panel that is meant to be read. So a finger that wandered while a mod or the notifications stood open played a gesture instead of the tap it was, and a gesture that has begun waits for a threshold the finger is no longer travelling towards, which is the open-close-open. Grown, the pill answers taps and its own controls answer theirs; its gesture handling is off entirely, and the hold that opens the mod's app keeps its wander.
  if (!CLOSED.has(shared.size) || shared.state === 'extended') {
    grantHoldWander();
    // The Notifications menu is two bubbles, and which one is on top is the gesture rather than a state: a pull down on the upper one trades their places, and a pull up on the lower one closes the menu from wherever it currently stands. A list long enough to scroll owns the drag that starts inside it, or the menu traded places every time it was read.
    if (!hasDismissed && shared.size === 'notifications' && isDuplicateLive() &&
        Math.abs(event.touches[0].clientX - startX) < 40) {
      const travel = event.touches[0].clientY - startY;
      const wants = !isDuplicateSwapped() ? travel > PULL_TRIGGER : travel < -PULL_TRIGGER;
      if (wants && !overScroller(startX, startY)) {
        hasDismissed = true;
        hasMoved = true;
        endHold();
        if (isDuplicateSwapped()) {
          bridge.triggerHaptic('dismiss');
          toClosed();
        } else swapDuplicate();
        return;
      }
    }
    // The one gesture an expanded mod keeps, because tapping it no longer closes it and a finger on the bubble needs a way out that is not the app behind it. It is the closed row's dismiss read on a grown bubble and it dismisses nothing — a mod is not a notification, so the panel closes and the mod stays in the row. An Alert is grown too and its tap opens the app rather than closing it, so it was the one face with no way out at all until it was named here beside the mods.
    if (!hasDismissed && (MOD_FACES[shared.size] || shared.state === 'alert') &&
        startY - event.touches[0].clientY > PULL_TRIGGER &&
        Math.abs(event.touches[0].clientX - startX) < 40) {
      hasDismissed = true;
      hasMoved = true;
      endHold();
      bridge.triggerHaptic('dismiss');
      toClosed();
    }
    return;
  }
  const verticalTravel = event.touches[0].clientY - startY;
  const horizontalTravel = Math.abs(event.touches[0].clientX - startX);
  const sideways = event.touches[0].clientX - startX;
  const isDragging = pillDrag(event.touches[0].clientX, event.touches[0].clientY);




  if (Math.hypot(horizontalTravel, verticalTravel) <= HOLD_BLOCK) {
    grantHoldWander();
    return;
  }
  if (!isDragging) return;
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
    openNotifications();
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
  
  
  
  if (!hasPulled && !shared.hasHeld && !shared.closedInTouch && shared.state !== 'extended' &&
      CLOSED.has(shared.size) && pullDrop > PULL_STOLEN) {
    hasPulled = true;
    endHold();
    bridge.triggerHaptic('expand');
    openNotifications();
    return;
  }
  if (shared.state === 'haptic' && hasGrown) return;
  endHold();
}, { passive: true });

pill.addEventListener('click', () => {
  if (hasMoved || shared.hasHeld) return;

  
  if (shared.state === 'extended') {
    // A tap on an expanded mod is reserved for what is inside it — the transport, the timeline, a timer's buttons — so the bubble itself answers nothing there. It closes by a tap outside it or by the swipe up. The notifications tab keeps the tap, since it has no control the whole box could be mistaken for.
    if (MOD_FACES[shared.size]) return;
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
  openNotifications();
});


// Mirrors `clockHolds` and `statusHolds`: the one reading of whether a point belongs to the main bubble, off its own rect. It used to be asked twice and answered differently — the bridge routed the touch by the pill's box, this by `elementFromPoint` — so any point the bridge called the bubble and the DOM called something else was closed by one half and clicked by the other, which is the open-close-open. A rect also spares it the two things `elementFromPoint` cannot see past: anything drawn over the bubble, and the bubble's own corner radius, which Blink clips hit testing to.
export function pillHolds(x, y) {
  const box = pill.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}


window.onOutsideTap = (x, y) => {
  
  
  
  
  
  
  
  
  const hit = document.elementFromPoint(x, y);
  if (pillHolds(x, y) || (hit && (clockPill.contains(hit) || statusPill.contains(hit)))) return;
  // Every press inside the open panel closed it: a proxy window reports a touch outside itself, so each of the other four called this with a point that was on a quick control, and a control is in none of the three bubbles named above. The panel answers for its own controls now, and a point between them still closes.
  if (statusPanelTarget(x, y)) return;
  if (statusOpen && hit && lockPill.contains(hit)) return;
  if (shared.state === 'extended') {
    toClosed();
    // Windows of one type stack in the order they were added and the row's own proxy went up first, so every proxy above it hears this press as an outside touch before the row's proxy hears the down — `toClosed` armed `closedInTouch` off `isTouchDown`, which is still false here, and the down that arrived a moment later handed the press a click that reopened what the press had just closed. The press is spent at the close. The latch lives one frame, which is long enough for the down belonging to this same press and short enough that a press landing in another app entirely never reaches the next one.
    shared.closedOutside = true;
    requestAnimationFrame(() => { shared.closedOutside = false; });
  }
  
  
  
  if (nowOpen) closeNowPanel();
  if (statusOpen) closeStatusPanel();
};


function overScroller(x, y) {
  const hit = document.elementFromPoint(x, y);
  const list = hit && hit.closest('#notifications-list');
  return Boolean(list) && list.scrollHeight - list.clientHeight > 2;
}
