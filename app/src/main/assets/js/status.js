import { stirLiquid } from './liquid.js';
import { bridge, root } from './state.js';

/**
 * The bubble at the right end of the bar, standing on the system's own icons.
 *
 * It is the one bubble that carries no state of its own and never announces anything: what
 * the phone is *attached* to is true all the time, so this bubble is read rather than
 * watched. That is the line between it and the Now bubble at the other end — Now carries
 * what is happening, a torch burning or a file arriving, and it comes and goes with it;
 * Status carries what is connected and stands there for as long as the bar does.
 *
 * A few pixels shorter than the bubbles that can be worked, which is how the eye tells a
 * bubble that reports from one that does something.
 */
export const statusPill = document.getElementById('status');
const statusModus = document.getElementById('status-modus');
const statusLink = document.getElementById('status-link');
const statusCharge = document.getElementById('status-charge');

/** Mirrors --status-right in pill.css: how far its right edge stands off the screen edge. */
const STATUS_RIGHT = 14;

/** Its flight out of the punch hole, matching the Now bubble's at the other end. */
const STATUS_TRAVEL = 420;
const STATUS_LAND = 0.42;

/**
 * The one glyph set. Every icon here is drawn rather than named, because the payload is
 * data — the watcher says what is attached and never what it looks like.
 */
const GLYPHS = {
  wifi: '<svg viewBox="0 0 24 24"><path d="M12 19.5a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6zm0-6.6c1.7 0 3.2.7 4.3 1.8l1.8-1.8A9 9 0 0 0 12 10.3a9 9 0 0 0-6.1 2.6l1.8 1.8a6 6 0 0 1 4.3-1.8zm0-5.4c3 0 5.8 1.2 7.9 3.2l1.8-1.8A14 14 0 0 0 12 5a14 14 0 0 0-9.7 3.9l1.8 1.8A11 11 0 0 1 12 7.5z"/></svg>',
  mobile: '<svg viewBox="0 0 24 24"><path d="M3 20h3v-6H3zm5 0h3V9H8zm5 0h3V5h-3zm5 0h3V2h-3z"/></svg>',
  ethernet: '<svg viewBox="0 0 24 24"><path d="M7 3h10a2 2 0 0 1 2 2v5h-3v3h-2v-3h-4v3H8v-3H5V5a2 2 0 0 1 2-2zm-2 12h14v4a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2z"/></svg>',
  none: '<svg viewBox="0 0 24 24"><path d="M4.4 3 21 19.6 19.6 21 16 17.4A9.8 9.8 0 0 0 12 16.6a1.8 1.8 0 1 0 0 3.6 1.8 1.8 0 0 0 1.3-.5L12 21 3 12a13 13 0 0 1 3.6-2.5L3 5.9zm7.6 4.5c3 0 5.8 1.2 7.9 3.2l1.8-1.8A14 14 0 0 0 12 5c-.6 0-1.2 0-1.8.1l2.2 2.2z"/></svg>',
  bluetooth: '<svg viewBox="0 0 24 24"><path d="M12 2h.6l4.6 4.6L13.4 10l3.8 3.4-4.6 4.6H12v-6.2l-3.4 3.4-1.4-1.4 4.4-4-4.4-4 1.4-1.4L12 8.2zm1.8 4.9L13.4 6v2.7zm0 8.4-1.4-1.5v2.7z"/></svg>',
  usb: '<svg viewBox="0 0 24 24"><path d="M11 2h2l1.5 2.5h-5zM11 5h2v10.2l3-2.4V10h-1.5V7.5h4V10H17v3.4l-4.8 3.8V19a2 2 0 1 1-2 0v-3.6l-3.4-2.6V10.9a2 2 0 1 1 2 0v.9l1.4 1.1z"/></svg>',
  hotspot: '<svg viewBox="0 0 24 24"><path d="M12 9.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5zM7.8 5.8 6.4 4.4a10 10 0 0 0 0 15.2l1.4-1.4a8 8 0 0 1 0-12.4zm9.8-1.4-1.4 1.4a8 8 0 0 1 0 12.4l1.4 1.4a10 10 0 0 0 0-15.2z"/></svg>',
};

/**
 * A Modus is what is riding on top of the link — a pair of headphones, a cable, a hotspot
 * someone is using. It colours the whole bubble and stands at the left, the way a mod's
 * glyph does anywhere else, so the bubble says what it is at a glance rather than by being
 * read. Only one at a time: they are ordered by how much the phone is currently doing for
 * something else, which is what a passenger cares about.
 */
const MODUS_ORDER = ['hotspot', 'usb', 'bluetooth'];
const MODUS_COLOUR = {
  bluetooth: '#3d8bff',
  usb: '#8f7dff',
  hotspot: '#ff9b3d',
};

let attached = null;
let charge = -1;
let isPlugged = false;
let isBorn = false;

/** Which connection a tap on the bubble is about: the Modus if there is one, else the link. */
function subject() {
  if (!attached) return 'none';
  return MODUS_ORDER.find(kind => hasModus(kind)) || attached.link;
}

function hasModus(kind) {
  if (!attached) return false;
  if (kind === 'bluetooth') return Boolean(attached.bluetooth);
  if (kind === 'usb') return Boolean(attached.usb);
  if (kind === 'hotspot') return Boolean(attached.hotspot);
  return false;
}

/**
 * What the bubble is wearing, redrawn whole. There is no arrival and no departure inside
 * this bubble — a Modus starting is not a mod arriving, it is this bubble being true about
 * something else — so the swap is a repaint and the bubble's own width carries the change.
 */
function paintStatus() {
  if (!attached) return;
  const modus = MODUS_ORDER.find(kind => hasModus(kind));
  statusPill.classList.toggle('modal', Boolean(modus));
  statusPill.dataset.modus = modus || '';
  statusModus.innerHTML = modus ? GLYPHS[modus] : '';
  root.style.setProperty('--status-accent', modus ? MODUS_COLOUR[modus] : 'transparent');

  statusLink.innerHTML = GLYPHS[attached.link] || GLYPHS.none;
  // Bars only where the phone can actually count them. Wifi's strength rides on the
  // capabilities the link already carries; a cellular level needs a dangerous permission,
  // so mobile says which link it is and stops there rather than drawing a bar it guessed.
  statusLink.dataset.level = attached.level >= 0 ? attached.level : '';

  // The paired device's charge stands in front of the phone's own, because it is the one
  // that is news: a phone is charged by the person holding it and a pair of headphones runs
  // out in the middle of something. The phone's own is always rightmost either way.
  const paired = attached.bluetooth && attached.bluetooth.charge >= 0
    ? attached.bluetooth.charge
    : -1;
  statusCharge.textContent = (paired >= 0 ? paired + '% · ' : '') +
    (charge >= 0 ? charge + '%' : '');
  statusPill.classList.toggle('plugged', isPlugged);
  fitStatusProxy();
  stirLiquid(420);
}

/** The proxy is exactly the bubble: it stands over the system's icons and nothing more. */
export function fitStatusProxy() {
  if (!isBorn) {
    bridge.setStatusProxy(0, 0, 0);
    return;
  }
  const box = statusPill.getBoundingClientRect();
  if (!box.width) {
    bridge.setStatusProxy(0, 0, 0);
    return;
  }
  bridge.setStatusProxy(Math.round(box.width), Math.round(box.height), Math.round(box.left));
}

/**
 * Born at the punch hole like everything else here, and only once: the bubble does not come
 * and go with the connection, it is the connection's place on the bar. What travels is the
 * bubble itself, from the middle of the screen out to its spot, small and growing on the
 * way — a bubble that appears at its own position is a box being shown.
 */
function bornStatus() {
  if (isBorn) return;
  isBorn = true;
  const box = statusPill.getBoundingClientRect();
  const home = window.innerWidth / 2 - (box.left + box.width / 2);
  root.style.setProperty('--status-fly', Math.round(home) + 'px');
  root.style.setProperty('--status-pop', 0.5);
  requestAnimationFrame(() => {
    statusPill.classList.add('lit');
    root.style.setProperty('--status-fly', '0px');
    root.style.setProperty('--status-pop', 1);
  });
  setTimeout(() => {
    bridge.triggerHaptic('tap');
    fitStatusProxy();
  }, STATUS_TRAVEL * STATUS_LAND);
  stirLiquid(STATUS_TRAVEL + 300);
}

/** Whether a point on the bar belongs to this bubble, asked the same way the light is. */
export function statusHolds(x, y) {
  if (!isBorn) return false;
  const box = statusPill.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}

/**
 * Its Active state, which is the system's own settings screen for whatever it is currently
 * reporting. A panel of our own that toggled radios would be a second settings app; this
 * bubble says what is attached and hands the changing of it back.
 */
export function statusTouch(action, x, y) {
  if (action === 'down') {
    statusPill.classList.add('pressing');
    return;
  }
  if (action === 'move') return;
  statusPill.classList.remove('pressing');
  if (action !== 'up') return;
  bridge.triggerHaptic('tap');
  bridge.openConnectionSettings(subject());
}

window.onConnectivity = state => {
  attached = state;
  bornStatus();
  paintStatus();
};

/** The charge, which arrives on its own clock: the battery mod is an event, this is a level. */
window.onCharge = (level, plugged) => {
  charge = level;
  isPlugged = plugged;
  paintStatus();
};

// The bubble stands where the screen's right edge is, and the page is the side that knows
// what a resize did to that.
window.addEventListener('resize', () => {
  if (isBorn) fitStatusProxy();
});

root.style.setProperty('--status-right', STATUS_RIGHT + 'px');
