import { fitLabels } from './labels.js';
import { stirLiquid } from './liquid.js';
import { mediaWindow } from './mods/media.js';
import { show } from './mods/notification.js';
import { timerWindow } from './mods/timer.js';
import { nowOpen, nowOwnsClock, nowTouch } from './now.js';
import { closeStatusPanel, openStatusPanel, statusHolds, statusOpen, statusPanelPush, statusPanelTarget, statusPill, statusTouch } from './status.js';
import { clockHolds, clockPill, clockTouch, wakeClock } from './clock.js';
import { lockHolds, lockPill } from './lock.js';
import { pillHolds } from './motion.js';
import { ensureClosedWindow, paintSatellites, paintShift, setSize, toClosed } from './row.js';
import { bridge, CLOSED, MELT_MAX, pill, root, shared } from './state.js';
import { openHistory } from './tabs.js';













let proxyTarget = null;
let proxyDown = null;
let proxySource = 'main';

let nowOwnsTouch = false;

let statusOwnsTouch = false;
let clockOwnsTouch = false;
let mainOwnsTouch = false;


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
  // A grown Main is drawn over the panel, and the Status proxy spans the panel from the screen's top edge down — so a touch on an Alert that arrived while the panel stood open was handed to the panel's own routing, which answered with the nearest quick toggle inside its grace radius or with the scrim. The bubble could be seen and not touched. Whatever the grown bubble covers is the grown bubble's, ahead of every panel that is open underneath it.
  if (mainHolds(x, y)) return pillTarget(x, y, hit);
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


function mainHolds(x, y) {
  return root.classList.contains('grown') && pillHolds(x, y);
}


// A near miss on a control inside the expanded bubble answered with the bubble itself, and the bubble's own click closes it, so a button missed by two pixels read as a tap to dismiss rather than as a button that was hard to hit.
function pillTarget(x, y, hit) {
  const inside = pillHolds(x, y);
  const within = inside && !!hit && pill.contains(hit) ? hit : null;
  const under = within ? within.closest(PILL_CONTROLS) : null;
  // A grown bubble's window is wider than the bubble, and a point in that margin was answered with the pill itself — while every other proxy heard the same touch as an outside tap and closed the bubble on the way down. The click that followed then arrived at a closed bubble and opened it again, which is the open-close-open a tap near an extended bubble's edge played. Outside the grown bubble the main proxy now answers nothing and the outside tap is the only reading of it; the grace margin stays for the compact bubble, which is smaller than a fingertip.
  const fallback = inside ? (within || pill) : (root.classList.contains('grown') ? null : pill);
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
  
  
  
  if (action === 'down') {
    shared.isTouchDown = true;
    // An outside report belonging to this same press reaches the page before this down does, so a bubble already closed by it hands this press a spent latch rather than a fresh one.
    shared.closedInTouch = shared.closedOutside;
  }
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
    // The panel's push is read off every touch its proxy hears, so a swipe on an Alert standing over the panel leaned and closed the panel underneath it as well as dismissing the Alert. The bubble owns the whole touch it started, not only the point it began at.
    mainOwnsTouch = mainHolds(x, y);
    statusOwnsTouch = !statusOpen && !statusPill.classList.contains('panel-closing') && (source === 'status' || statusHolds(x, y));
  }
  
  
  
  
  if (statusOpen && !mainOwnsTouch && (source === 'status' || statusHolds(x, y))) statusPanelPush(action, x, y);
  if (mainOwnsTouch && (action === 'up' || action === 'cancel')) mainOwnsTouch = false;
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
  // One finger may change the bubble's state once. The bubble is closed on the way down — by an outside tap another proxy heard, or by a gesture firing at its threshold — and this click then landed on a bubble that was already closed, which opens what is closed: the open-close-open. Guarding the pill's own click was not enough, because a click that lands on a mod's control or its satellite opens the bubble through that control's handler instead. The close was this touch's answer, so the touch has no click left to give.
  if (action === 'up' && travelled < PROXY_TAP_SLOP && !shared.closedInTouch) {
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

/* Landscape, a fullscreen app and a dark screen all hide the stage, and the page was never told — so every ticker, every mirror frame and every measurement went on running against a surface nobody can see, which is most of a day of CPU for a phone that spends most of it in a pocket. The page sleeps with the stage and repaints what had gone stale on the way back. */
window.setStageHidden = hidden => {
  const isHidden = Boolean(hidden);
  if (isHidden === shared.isStageHidden) return;
  shared.isStageHidden = isHidden;
  if (isHidden) return;
  wakeClock();
  fitLabels();
  stirLiquid(240);
};

// Mirrors the FONT_STACKS array in panel.html — the index a preference holds has to mean the same face in both places.
const FONT_STACKS = [
  'var(--font-face-mono)',
  'var(--font-face-system)',
  'var(--font-face-audiowide)',
  'var(--font-face-wosker)',
  'var(--font-face-striker)',
  'var(--font-face-clash-extralight)',
  'var(--font-face-clash-light)',
  'var(--font-face-clash-regular)',
  'var(--font-face-clash-medium)',
  'var(--font-face-clash-semibold)',
  'var(--font-face-clash-bold)',
  'var(--font-face-general-extralight)',
  'var(--font-face-general-extralight-italic)',
  'var(--font-face-general-light)',
  'var(--font-face-general-light-italic)',
  'var(--font-face-general-regular)',
  'var(--font-face-general-italic)',
  'var(--font-face-general-medium)',
  'var(--font-face-general-medium-italic)',
  'var(--font-face-general-semibold)',
  'var(--font-face-general-semibold-italic)',
  'var(--font-face-general-bold)',
  'var(--font-face-general-bold-italic)',
  'var(--font-face-tanker-regular)',
];
window.setFonts = (clock, main, satellite, status, overlay, battery, stats, connectors, notificationHeading, notificationContent) => {
  root.style.setProperty('--font-clock', FONT_STACKS[clock] || FONT_STACKS[0]);
  root.style.setProperty('--font-main', FONT_STACKS[main] || FONT_STACKS[0]);
  root.style.setProperty('--font-satellite', FONT_STACKS[satellite] || FONT_STACKS[0]);
  root.style.setProperty('--font-status', FONT_STACKS[status] || FONT_STACKS[0]);
  root.style.setProperty('--font-overlay', FONT_STACKS[overlay] || FONT_STACKS[0]);
  root.style.setProperty('--font-battery', FONT_STACKS[battery] || FONT_STACKS[0]);
  root.style.setProperty('--font-stats', FONT_STACKS[stats] || FONT_STACKS[0]);
  root.style.setProperty('--font-connectors', FONT_STACKS[connectors] || FONT_STACKS[0]);
  root.style.setProperty('--font-notification-heading', FONT_STACKS[notificationHeading] || FONT_STACKS[0]);
  root.style.setProperty('--font-notification-content', FONT_STACKS[notificationContent] || FONT_STACKS[0]);
};

// Mirrors the size preferences the panel writes — a percentage there, a bare multiplier for the font-size calc() in pill.css here.
window.setFontSizes = (clock, main, satellite, status, overlay, battery, stats, connectors, notificationHeading, notificationContent) => {
  root.style.setProperty('--font-size-clock', (clock || 100) / 100);
  root.style.setProperty('--font-size-main', (main || 100) / 100);
  root.style.setProperty('--font-size-satellite', (satellite || 100) / 100);
  root.style.setProperty('--font-size-status', (status || 100) / 100);
  root.style.setProperty('--font-size-overlay', (overlay || 100) / 100);
  root.style.setProperty('--font-size-battery', (battery || 100) / 100);
  root.style.setProperty('--font-size-stats', (stats || 100) / 100);
  root.style.setProperty('--font-size-connectors', (connectors || 100) / 100);
  root.style.setProperty('--font-size-notification-heading', (notificationHeading || 100) / 100);
  root.style.setProperty('--font-size-notification-content', (notificationContent || 100) / 100);
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


window.setQuickDividers = value => {
  root.classList.toggle('no-dividers', Number(value) === 0);
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
