import { stirLiquid } from './liquid.js';
import { mediaWindow } from './mods/media.js';
import { show } from './mods/notification.js';
import { timerWindow } from './mods/timer.js';
import { nowHolds, nowOpen, nowPill, nowTouch } from './now.js';
import { statusHolds, statusTouch } from './status.js';
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
/** Far enough that the release is a drag's end rather than a tap. Mirrors HOLD_SLOP. */
export const PROXY_TAP_SLOP = 22;

/** An element, short enough to read off a debug line. */
function describe(element) {
  if (!element) return 'nothing';
  return (element.id || element.className || element.tagName) +
    (pill.contains(element) ? ' (in the bubble)' : '') +
    (nowPill.contains(element) ? ' (in the light)' : '');
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
 * What a touch from this proxy is allowed to land on. A proxy is placed over one
 * bubble and nothing else, so a touch it heard belongs to that bubble however the
 * page happens to be stacked at that moment — a grown panel is drawn across most of
 * the bar and sits above the Now bubble in the DOM, so without this every tap and
 * every hold aimed at the light was answered by whatever mod owned the main bubble.
 */
function proxyPoint(x, y, source) {
  const hit = document.elementFromPoint(x, y);
  // Where it landed decides, not which window heard it and not what is stacked over
  // it. The two proxies each carry a grab margin and the two bubbles now stand a few
  // pixels apart, so their margins lie over each other: a touch on the light was
  // being answered by the main bubble whenever the main proxy heard it first, and a
  // touch on the main bubble's left edge was being eaten by the light whenever the
  // Now proxy did. The one thing neither window knows is where the shapes are.
  // The closed light never gets here — it answers its own touches, see nowTouch. This
  // is the open panel, which is drawn across the bar and above everything on it: a
  // touch inside it is the panel's whatever the DOM has stacked where the finger fell.
  if (!nowOpen || !nowHolds(x, y)) return hit;
  return hit && nowPill.contains(hit) ? hit : nowPill;
}

window.onProxyTouch = (action, x, y, source) => {
  if (action !== 'move') {
    const box = nowPill.getBoundingClientRect();
    bridge.note(
      'page: ' + action + ' at ' + Math.round(x) + ', ' + Math.round(y) +
      ' — the light is ' + (nowPill.classList.contains('lit') ? 'lit' : 'dark') +
      ' at ' + Math.round(box.left) + '–' + Math.round(box.right) +
      ', ' + Math.round(box.top) + '–' + Math.round(box.bottom) +
      ' — owner ' + (nowHolds(x, y) && !nowOpen ? 'the light' : 'the row') +
      ' — under it ' + describe(document.elementFromPoint(x, y))
    );
  }
  // The light first, and by geometry alone. Its panel is the exception: the rail in
  // there is dragged, so once it is open the touches go the ordinary way and reach the
  // elements that know what to do with them.
  // A touch this window heard is this bubble's, whatever the coordinates work out to:
  // the window is placed over the light and over nothing else, which is the same thing
  // that made this simple when the light was a page in a window of its own. The row's
  // window can hear one too — the two stand six pixels apart — and that one is decided
  // by where it landed, which is all the row's window can tell us.
  if (action === 'down') {
    nowOwnsTouch = !nowOpen &&
      (source === 'now' || (nowHolds(x, y) && CLOSED.has(shared.size)));
  }
  if (nowOwnsTouch) {
    nowTouch(action, x, y);
    if (action === 'up' || action === 'cancel') nowOwnsTouch = false;
    return;
  }
  // The same question as the light's, at the other end of the bar: a touch this window heard
  // is this bubble's, and a touch the row's window heard is decided by where it landed.
  if (action === 'down') {
    statusOwnsTouch = source === 'status' || statusHolds(x, y);
  }
  if (statusOwnsTouch) {
    statusTouch(action, x, y);
    if (action === 'up' || action === 'cancel') statusOwnsTouch = false;
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
