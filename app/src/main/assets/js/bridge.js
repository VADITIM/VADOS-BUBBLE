import { stirLiquid } from './liquid.js';
import { mediaWindow } from './mods/media.js';
import { show } from './mods/notification.js';
import { timerWindow } from './mods/timer.js';
import { nowOpen, nowOwnsClock, nowTouch } from './now.js';
import { closeStatusPanel, openStatusPanel, statusHolds, statusOpen, statusPanelPush, statusPill, statusTouch } from './status.js';
import { clockHolds, clockPill, clockTouch } from './clock.js';
import { lockHolds, lockPill } from './lock.js';
import { ensureClosedWindow, paintSatellites, paintShift, setSize } from './row.js';
import { bridge, CLOSED, MELT_MAX, pill, root, shared } from './state.js';













let proxyTarget = null;
let proxyDown = null;
let proxySource = 'main';

let nowOwnsTouch = false;

let statusOwnsTouch = false;
let clockOwnsTouch = false;


export const PROXY_TAP_SLOP = 22;


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
  if (lockHolds(x, y)) {
    return hit && lockPill.contains(hit) ? hit : lockPill;
  }
  if (nowOpen && clockHolds(x, y)) {
    return hit && clockPill.contains(hit) ? hit : clockPill;
  }
  
  if (source === 'main') return hit && pill.contains(hit) ? hit : pill;
  return hit;
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
