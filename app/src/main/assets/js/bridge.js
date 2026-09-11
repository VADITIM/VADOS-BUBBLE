import { stirLiquid } from './liquid.js';
import { mediaWindow } from './mods/media.js';
import { show } from './mods/notification.js';
import { timerWindow } from './mods/timer.js';
import { nowOpen, nowOwnsClock, nowTouch } from './now.js';
import { closeStatusPanel, openStatusPanel, statusHolds, statusOpen, statusPanelPush, statusPanelTarget, statusPill, statusTouch } from './status.js';
import { clockHolds, clockPill, clockTouch } from './clock.js';
import { lockHolds, lockPill } from './lock.js';
import { ensureClosedWindow, paintSatellites, paintShift, setSize, toClosed } from './row.js';
import { bridge, CLOSED, MELT_MAX, pill, root, shared } from './state.js';
import { openHistory } from './tabs.js';













let proxyTarget = null;
let proxyDown = null;
let proxySource = 'main';

let nowOwnsTouch = false;

let statusOwnsTouch = false;
let clockOwnsTouch = false;


export const PROXY_TAP_SLOP = 22;


const PILL_GRACE = 22;
const PILL_CONTROLS = '.transport, #player-timeline, .timer-button, .history-row';


function describe(element) {
  if (!element) return 'nothing';
  return (element.id || element.className || element.tagName) +
    (pill.contains(element) ? ' (in the bubble)' : '') +
    (clockPill.contains(element) ? ' (in the clock)' : '');
}

function proxyEvent(type, target, x, y, isEnd) {
  const touch = new Touch({ identifier: 1, target, clientX: x, clientY: y, pageX: x, pageY: y });
  target.dispatchEvent(new TouchEvent(type, {
    touches: isEnd ? [] : [touch],
    targetTouches: isEnd ? [] : [touch],
    changedTouches: [touch],
    bubbles: true,
    cancelable: true,
  }));
}














function proxyPoint(x, y, source) {
  const hit = document.elementFromPoint(x, y);
  if (statusOpen && statusHolds(x, y)) {
    return hit && statusPill.contains(hit) ? hit : statusPill;
  }
  // The Now bubble stands at the panel's foot but is not in the panel, so a touch on it arrived through the Status proxy and was handed to `statusPanelTarget`, which knows only the panel's own controls — it answered with the nearest toggle inside its grace radius, or with the scrim. It owns its own box wherever it stands, which is why this outranks the panel's own routing.
  if (lockHolds(x, y)) {
    return hit && lockPill.contains(hit) ? hit : lockPill;
  }
  if (statusOpen && source === 'status') return statusPanelTarget(x, y) || hit;
  if (nowOpen && clockHolds(x, y)) {
    return hit && clockPill.contains(hit) ? hit : clockPill;
  }
  
  if (source === 'main') return pillTarget(x, y, hit);
  return hit;
}


// A near miss on a control inside the expanded bubble answered with the bubble itself, and the bubble's own click closes it, so a button missed by two pixels read as a tap to dismiss rather than as a button that was hard to hit.
function pillTarget(x, y, hit) {
  const inside = !!hit && pill.contains(hit);
  const under = inside ? hit.closest(PILL_CONTROLS) : null;
  // A grown bubble's window is wider than the bubble, and a point in that margin was answered with the pill itself — while every other proxy heard the same touch as an outside tap and closed the bubble on the way down. The click that followed then arrived at a closed bubble and opened it again, which is the open-close-open a tap near an extended bubble's edge played. Outside the grown bubble the main proxy now answers nothing and the outside tap is the only reading of it; the grace margin stays for the compact bubble, which is smaller than a fingertip.
  const fallback = inside ? hit : (root.classList.contains('grown') ? null : pill);
  const face = pill.querySelector('.face.showing');
  if (under || !face) return under || fallback;
  const room = face.getBoundingClientRect();
  let nearest = null;
  let closest = PILL_GRACE;
  for (const control of face.querySelectorAll(PILL_CONTROLS)) {
    const box = control.getBoundingClientRect();
    // A history row scrolled past the list's edge is still in the document, so without this a tap near the bubble's rim opened a notification nobody can see.
    if (!box.width || box.bottom < room.top || box.top > room.bottom) continue;
    const across = Math.max(box.left - x, 0, x - box.right);
    const down = Math.max(box.top - y, 0, y - box.bottom);
    const gap = Math.hypot(across, down);
    if (gap >= closest) continue;
    closest = gap;
    nearest = control;
  }
  return nearest || fallback;
}

window.onProxyTouch = (action, x, y, source) => {
  
  
  
  if (action === 'down') shared.isTouchDown = true;
  else if (action === 'up' || action === 'cancel') shared.isTouchDown = false;
  if (action !== 'move') {
    const box = clockPill.getBoundingClientRect();
    bridge.note(
      'page: ' + action + ' at ' + Math.round(x) + ', ' + Math.round(y) +
      ' — the clock is at ' + Math.round(box.left) + '–' + Math.round(box.right) +
      ', ' + Math.round(box.top) + '–' + Math.round(box.bottom) +
      ' — owner ' + (clockHolds(x, y) ? (nowOwnsClock() ? 'a Now mod' : 'the clock') : 'the row') +
      ' — under it ' + describe(document.elementFromPoint(x, y))
    );
  }
  
  
  
  
  if (action === 'down') {
    statusOwnsTouch = !statusOpen && !statusPill.classList.contains('panel-closing') && (source === 'status' || statusHolds(x, y));
  }
  
  
  
  
  if (statusOpen && (source === 'status' || statusHolds(x, y))) statusPanelPush(action, x, y);
  if (statusOwnsTouch) {
    statusTouch(action, x, y);
    if (action === 'up' || action === 'cancel') statusOwnsTouch = false;
    return;
  }
  
  
  
  
  
  if (action === 'down') {
    clockOwnsTouch = !nowOpen && (source === 'clock' || clockHolds(x, y));
    nowOwnsTouch = clockOwnsTouch && nowOwnsClock();
  }
  if (nowOwnsTouch) {
    nowTouch(action, x, y);
    if (action === 'up' || action === 'cancel') nowOwnsTouch = false;
    return;
  }
  if (clockOwnsTouch) {
    clockTouch(action, x, y);
    if (action === 'up' || action === 'cancel') clockOwnsTouch = false;
    return;
  }
  if (action === 'down') {
    
    
    proxySource = source;
    proxyTarget = proxyPoint(x, y, source);
    proxyDown = { x, y };
    if (proxyTarget) proxyEvent('touchstart', proxyTarget, x, y, false);
    return;
  }
  if (!proxyTarget) return;
  if (action === 'move') {
    proxyEvent('touchmove', proxyTarget, x, y, false);
    return;
  }
  proxyEvent(action === 'up' ? 'touchend' : 'touchcancel', proxyTarget, x, y, true);
  
  
  
  
  const travelled = proxyDown
    ? Math.hypot(x - proxyDown.x, y - proxyDown.y)
    : Infinity;
  if (action === 'up' && travelled < PROXY_TAP_SLOP) {
    const released = proxyPoint(x, y, proxySource) || proxyTarget;
    released.dispatchEvent(new MouseEvent('click', {
      clientX: x, clientY: y, bubbles: true, cancelable: true,
    }));
  }
  proxyTarget = null;
  proxyDown = null;
};


window.onNotificationUpdate = notification => show(notification);
window.setUnreadCount = count => {
  const badge = document.getElementById('unread');
  badge.textContent = count > 9 ? '9+' : String(count);
  badge.classList.toggle('present', count > 0);
};

window.setAccent = hex => root.style.setProperty('--section-color', hex);
window.setPillBackground = rgba => {
  root.style.setProperty('--pill-background', rgba);
  
  const parts = (String(rgba).match(/[\d.]+/g) || []).map(Number);
  if (parts.length < 3) return;
  root.style.setProperty('--pill-solid', 'rgb(' + parts.slice(0, 3).join(',') + ')');
  root.style.setProperty('--pill-alpha', parts.length > 3 ? String(parts[3]) : '1');
  stirLiquid(120);
};
window.setCompactSize = (width, height) => {
  shared.compact = { width, height };
  document.documentElement.style.setProperty('--pill-width', width + 'px');
  document.documentElement.style.setProperty('--pill-height', height + 'px');
  
  
  if (shared.size === 'timing') setSize('timing', timerWindow(), true);
  else if (shared.size === 'playing') setSize('playing', mediaWindow(), true);
  else { paintSatellites(); paintShift(); }
};
window.setGrab = value =>
  document.documentElement.style.setProperty('--grab', value + 'px');
window.setNotificationIdentity = value => { pill.dataset.identity = String(value); };







window.setGoo = percent => {
  shared.goo = MELT_MAX * (Number(percent) || 100) / 100;
};


window.setAlertDwell = tenths => {
  shared.dwell = Math.max(500, (Number(tenths) || 50) * 100);
};


window.setEdgeMerge = value => {
  shared.edgeMerge = Number(value) !== 0;
  stirLiquid(240);
};

window.setNowPushes = value => {
  shared.nowPushes = Number(value) !== 0;
  paintShift();
  stirLiquid(240);
};


window.setModWidth = value => {
  shared.modWidth = Number(value) || 56;
  paintSatellites();
  ensureClosedWindow();
};
window.setMicrophoneActive = isActive =>
  document.getElementById('microphone-dot').classList.toggle('active', isActive);
window.setCameraActive = isActive =>
  document.getElementById('camera-dot').classList.toggle('active', isActive);







window.onToggleStatusPanel = () => {
  if (statusOpen) closeStatusPanel();
  else openStatusPanel();
};

window.onToggleNotifications = () => {
  if (shared.state === 'extended' && shared.size === 'history') toClosed();
  else openHistory();
};
