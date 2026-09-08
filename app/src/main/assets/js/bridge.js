import { stirLiquid } from './liquid.js';
import { mediaWindow } from './mods/media.js';
import { show } from './mods/notification.js';
import { timerWindow } from './mods/timer.js';
import { nowOpen, nowOwnsClock, nowTouch } from './now.js';
import { statusHolds, statusOpen, statusPanelPush, statusPill, statusTouch } from './status.js';
import { clockHolds, clockPill, clockTouch } from './clock.js';
import { lockHolds, lockPill } from './lock.js';
import { ensureClosedWindow, paintSatellites, paintShift, setSize } from './row.js';
import { bridge, CLOSED, MELT_MAX, pill, root, shared } from './state.js';

/**
 * The window this page is drawn in takes no touches at all — it spans the whole
 * status bar so that every bubble is on one surface and can merge with the others,
 * and a touchable window that wide would leave nowhere for the shade swipe to start.
 * The finger lands on a small proxy window instead, and the host forwards where it
 * was touched.
 *
 * Nothing below reimplements a gesture. It rebuilds the events the DOM would have
 * produced and dispatches them at whatever is under the point, so every listener on
 * this page — the pill's own drag, a history row's swipe, a button's click — keeps
 * working without knowing the touch came the long way round.
 */
let proxyTarget = null;
let proxyDown = null;
let proxySource = 'main';
/** True for the whole of a touch the Now bubble answered on its own. */
let nowOwnsTouch = false;
/** The same, for the bubble at the right end of the bar. */
let statusOwnsTouch = false;
let clockOwnsTouch = false;
/** Far enough that the release is a drag's end rather than a tap. Its own number: this is about
 *  a tap, not about the block, and a touch that ended inside the block is still a tap. */
export const PROXY_TAP_SLOP = 22;

/** An element, short enough to read off a debug line. */
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

/**
 * What a touch from this proxy is allowed to land on. A proxy is placed over one bubble and
 * nothing else, so a touch it heard belongs to that bubble however the page happens to be
 * stacked at that moment — a grown panel is drawn across most of the bar and sits above the
 * others in the DOM, so without this every tap and every hold aimed at one of them was answered
 * by whatever mod owned the main bubble.
 *
 * The lock screen's bubble is here for the same reason and it is not a hypothetical one: the
 * notification list stands above it in the markup and its proxy is added to the window manager
 * after the lock bubble's, so a tap on the player was landing on whichever of the two the
 * hit-test happened to answer with — which is exactly what "I cannot tap it, it goes straight
 * through" is. Where the finger fell decides, not what is stacked over it.
 */
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
  // And the main bubble's own proxy, by geometry rather than by what the hit-test answers: that window is the bubble plus the grab margin and the bleed either side, so a finger landing in the margin — which is most of the finger, on a 30dp pill — hit the canvas behind it, and the bubble's own listeners never heard a touch it had plainly been given. That is the whole of "the clock and the Status bubble are easy to get a grip on and the main one is not": those two are routed by where the finger fell and this one was routed by what happened to be under it. Inside the bubble the element still wins, because an open panel is full of controls that have to be clicked.
  if (source === 'main') return hit && pill.contains(hit) ? hit : pill;
  return hit;
}

window.onProxyTouch = (action, x, y, source) => {
  // Before any routing, because it is not a question about which bubble was touched: it is
  // whether *a* finger is down anywhere on this interface, which is what tells the row whether
  // it may shrink a window right now or has to wait for the lift.
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
  // The bubble at the right end first, and by geometry alone: a touch this window heard is that
  // bubble's, and one the row's window heard is decided by where it landed. Only while it is
  // closed — an open panel is full of switches that have to be clicked, so once it is open the
  // touches go the ordinary way and reach the elements that know what to do with them.
  if (action === 'down') {
    statusOwnsTouch = !statusOpen && (source === 'status' || statusHolds(x, y));
  }
  // Alongside the ordinary routing rather than instead of it: the flick that puts an open panel
  // away has to be heard while every switch under the finger goes on being clickable, and the
  // two are told apart by how far the finger travelled — which both readings can agree on
  // without either taking the gesture off the other.
  if (statusOpen && (source === 'status' || statusHolds(x, y))) statusPanelPush(action, x, y);
  if (statusOwnsTouch) {
    statusTouch(action, x, y);
    if (action === 'up' || action === 'cancel') statusOwnsTouch = false;
    return;
  }
  // And the bubble at the left end, which is two bubbles' worth of meaning in one box: the time,
  // and whichever Now mod has taken it over. Which of the two answers is not a question about
  // where the finger fell — both are the same box — so it is asked of the mods, and the clock
  // gets the touch only when nothing is standing in it. The open panel is the exception both of
  // them share: the intensity rail in there is dragged, so the touches go the ordinary way.
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
    // Everything after a down goes to the element the down landed on, exactly as a
    // real touch stream does: a finger that slides off a row still belongs to it.
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
  // A real touch that did not travel is followed by a click, and half this page is
  // listening for one. It is sent to what is under the finger *now* rather than to
  // the down's target, because a panel that opened under the release did not get
  // the down and would otherwise never be clickable.
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
/** The one lit colour, repointed at the token every rule on the bar already reads it through. */
window.setAccent = hex => root.style.setProperty('--section-color', hex);
window.setPillBackground = rgba => {
  root.style.setProperty('--pill-background', rgba);
  // Split for the skin: solid colour on the shapes, transparency on the layer.
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
  // A closed mod sizes its window from the bubble's own width, so a bubble that
  // changes size under one has to hand it the new room.
  if (shared.size === 'timing') setSize('timing', timerWindow(), true);
  else if (shared.size === 'playing') setSize('playing', mediaWindow(), true);
  else { paintSatellites(); paintShift(); }
};
window.setGrab = value =>
  document.documentElement.style.setProperty('--grab', value + 'px');
window.setNotificationIdentity = value => { pill.dataset.identity = String(value); };

/**
 * How far the liquid reaches, as a percentage of the deviation two touching shapes melt at.
 * A percentage rather than a raw deviation because the number that matters to the eye is
 * "how far apart can two bubbles be and still neck", and that is twice the deviation — so
 * the slider is scaled against the value the interface was tuned at rather than set blind.
 */
window.setGoo = percent => {
  shared.goo = MELT_MAX * (Number(percent) || 100) / 100;
};

/** The alert's dwell, in tenths of a second because the store holds ints. */
window.setAlertDwell = tenths => {
  shared.dwell = Math.max(500, (Number(tenths) || 50) * 100);
};

/** Whether the liquid wets the sides of the screen. The mirror reads it every frame. */
window.setEdgeMerge = value => {
  shared.edgeMerge = Number(value) !== 0;
  stirLiquid(240);
};

window.setNowPushes = value => {
  shared.nowPushes = Number(value) !== 0;
  paintShift();
  stirLiquid(240);
};

/** How much wider than the bare bubble a mod makes it. The row reads it every layout. */
window.setModWidth = value => {
  shared.modWidth = Number(value) || 56;
  paintSatellites();
  ensureClosedWindow();
};
window.setMicrophoneActive = isActive =>
  document.getElementById('microphone-dot').classList.toggle('active', isActive);
window.setCameraActive = isActive =>
  document.getElementById('camera-dot').classList.toggle('active', isActive);
