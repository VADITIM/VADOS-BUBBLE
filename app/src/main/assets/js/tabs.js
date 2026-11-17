import { stirLiquid } from './liquid.js';
import { isLandscape } from './reveal.js';
import { markUp } from './mods/notification.js';
import { becomeExtended, setSize, showFace, toClosed } from './row.js';
import { swipeToDismiss } from './swipeDismiss.js';
import { GROWN_PAD, SIZES, bridge, pill, root, shared } from './state.js';


function afterRowRemoved(below, gap) {
  closeGap(below, gap);
  
  
  const left = [...document.querySelectorAll('.notification-row')]
    .reduce((sum, node) => sum + Number(node.dataset.count || 1), 0);
  document.getElementById('notifications-count').textContent = left ? String(left) : '';
  
  setTimeout(() => {
    if (shared.size === 'notifications' && !document.querySelector('.notification-row')) toClosed();
  }, 220);
}









function closeGap(below, gap) {
  if (!below.length) return;
  for (const other of below) {
    other.style.transition = 'none';
    other.style.transform = 'translateY(' + gap + 'px)';
  }
  
  
  requestAnimationFrame(() => {
    for (const other of below) {
      other.style.transition = 'transform 260ms var(--ease-grow)';
      other.style.transform = '';
    }
  });
  
  
  if (shared.size === 'notifications') {
    setSize('notifications', notificationsWindow(), true);
    stirLiquid(600);
  }
}


export const MOD_FACES = { player: 'media', timer: 'timer', trip: 'trip' };






export function openMod() {
  leaveForMod(MOD_FACES[shared.size]);
}







export function leaveForMod(mod) {
  if (mod === 'media') {
    if (shared.media && shared.media.package) bridge.openApp(shared.media.package);
    return;
  }
  if (mod === 'timer' && shared.timer && shared.timer.key) bridge.openNotification(shared.timer.key);
  if (mod === 'call' && shared.call && shared.call.key) bridge.openNotification(shared.call.key);
  if (mod === 'trip' && shared.trip && shared.trip.key) bridge.openNotification(shared.trip.key);
}









function grouped(entries) {
  const groups = [];
  const byKey = new Map();
  for (const entry of entries) {
    const id = (entry.app || '') + '  ' + (entry.title || '');
    const group = byKey.get(id);
    if (group) {
      group.entries.push(entry);
      continue;
    }
    const fresh = { id, head: entry, entries: [entry] };
    byKey.set(id, fresh);
    groups.push(fresh);
  }
  return groups;
}









export function agoText(postedAt) {
  if (!postedAt) return '';
  const minutes = Math.floor((Date.now() - postedAt) / 60000);
  if (minutes < 1) return 'now';
  if (minutes < 60) return minutes + 'm';
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + 'h';
  return Math.floor(hours / 24) + 'd';
}

export function isNotificationsSettling() {
  return performance.now() - shared.notificationsClosedAt < NOTIFICATIONS_HOMING;
}

// Where the open menu comes to rest, read off the same geometry its window was asked for, so a tap while it is still growing is answered against the menu it is becoming.
export function notificationsRest() {
  if (shared.state !== 'extended' || shared.size !== 'notifications') return null;
  const style = getComputedStyle(root);
  const grab = parseFloat(style.getPropertyValue('--grab')) || 0;
  const drop = parseFloat(root.style.getPropertyValue('--notifications-drop')) || 0;
  const height = parseFloat(root.style.getPropertyValue('--notifications-height')) || SIZES.notifications.height;
  const left = (root.clientWidth - SIZES.notifications.width) / 2;
  const top = grab + GROWN_PAD + drop;
  return { left, top, right: left + SIZES.notifications.width, bottom: top + height };
}

export function restingRowAt(x, y) {
  const rest = notificationsRest();
  if (!rest) return null;
  const box = pill.getBoundingClientRect();
  const atX = x - rest.left + box.left;
  const atY = y - rest.top + box.top;
  const list = document.getElementById('notifications-list').getBoundingClientRect();
  const listBottom = list.top + (rest.bottom - rest.top) - (list.top - box.top);
  if (atY < list.top || atY > listBottom) return null;
  return [...document.querySelectorAll('.notification-row')].find(row => {
    const rowBox = row.getBoundingClientRect();
    return atX >= rowBox.left && atX <= rowBox.right && atY >= rowBox.top && atY <= rowBox.bottom;
  }) || null;
}

export function openNotifications() {
  if (isNotificationsSettling()) return;
  const list = document.getElementById('notifications-list');
  list.textContent = '';

  const entries = JSON.parse(bridge.readNotifications() || '[]');
  document.getElementById('notifications-count').textContent =
    entries.length ? String(entries.length) : '';
  if (entries.length === 0) {
    const empty = document.createElement('div');
    empty.id = 'notifications-empty';
    empty.textContent = 'Nothing waiting';
    list.appendChild(empty);
  }

  const bodies = [];
  grouped(entries).forEach((group, index) => {
    const entry = group.head;
    const row = document.createElement('div');
    row.className = 'notification-row';
    const rowDelay = ROW_ENTER_DELAY + Math.min(index, ROW_STAGGER_LIMIT) * ROW_STAGGER;
    row.style.setProperty('--row-delay', rowDelay + 'ms');
    row.style.setProperty('--accent', entry.accent || '#ffffff');
    
    
    row.dataset.count = String(group.entries.length);
    row.dataset.keys = JSON.stringify(group.entries.map(one => one.key));
    
    
    swipeToDismiss(row, group.entries.map(one => one.key), {
      rowSelector: '.notification-row',
      afterRemove: afterRowRemoved,
    });

    const mark = document.createElement('div');
    mark.className = 'notification-mark';
    mark.style.background = entry.accent || '#ffffff';
    
    
    
    if (entry.gradient) {
      row.dataset.gradient = '';
      row.style.setProperty('--row-gradient', entry.gradient);
    }

    const copy = document.createElement('div');
    copy.className = 'notification-copy';

    const head = document.createElement('div');
    head.className = 'notification-head-row';
    const name = document.createElement('div');
    name.className = 'notification-name';
    name.style.color = entry.accent || '#ffffff';
    const nameText = document.createElement('span');
    nameText.className = 'reveal-text';
    nameText.textContent = entry.appName || entry.app || '';
    const nameBar = document.createElement('span');
    nameBar.className = 'reveal-bar';
    name.append(nameText, nameBar);
    head.appendChild(name);
    if (group.entries.length > 1) {
      const tally = document.createElement('div');
      tally.className = 'notification-tally';
      tally.textContent = group.entries.length;
      head.appendChild(tally);
    }
    
    
    
    const newest = group.entries.reduce(
      (latest, one) => (one.postedAt || 0) > (latest.postedAt || 0) ? one : latest, entry
    );
    const age = agoText(newest.postedAt);
    if (age) {
      const when = document.createElement('div');
      when.className = 'notification-when';
      when.textContent = age;
      head.appendChild(when);
    }
    copy.appendChild(head);

    
    
    if (entry.title) {
      const who = document.createElement('div');
      who.className = 'notification-who';
      who.textContent = entry.title;
      copy.appendChild(who);
    }
    
    
    
    
    
    const lines = group.entries.flatMap(one =>
      (one.lines && one.lines.length)
        ? one.lines.map(line => line.text)
        : [one.text || (entry.title ? '' : one.title || '')]
    ).filter(Boolean);
    if (lines.length) {
      const body = document.createElement('div');
      body.className = 'notification-body';
      for (const line of lines) {
        const text = document.createElement('div');
        text.className = 'notification-text';
        markUp(text, line);
        body.appendChild(text);
      }
      copy.appendChild(body);
      bodies.push({ body, delay: rowDelay + BODY_TYPE_AFTER });
    }

    // The large icon a messenger posts is whoever wrote the message, not the app, so a list read at a glance says which app it came from and falls back to the notification's own icon where an app has none.
    const badge = entry.appIconBase64 || entry.iconBase64;
    if (badge) {
      const icon = document.createElement('img');
      icon.className = 'notification-icon';
      icon.alt = '';
      icon.src = badge;
      row.append(icon, copy);
    } else {
      mark.textContent = (entry.appName || entry.app || '').trim().charAt(0).toUpperCase();
      row.append(mark, copy);
    }
    row.addEventListener('click', event => {
      event.stopPropagation();
      
      
      if (row.dataset.swiped === 'true' || row.dataset.scrolled === 'true') return;
      bridge.openNotification(entry.key);
      bridge.triggerHaptic('tap');
      toClosed();
    });
    list.appendChild(row);
  });

  becomeExtended();
  showFace('notifications');
  setSize('notifications', notificationsWindow());
  stirLiquid(760);
  bodies.forEach(({ body, delay }) => typeBody(body, delay));
}

// The menu's height is measured off its rows the moment it opens, so a body typed in by writing its text would grow the row under a menu already sized for it; every character is laid out from the start and the untyped rest is only uncoloured.
function typeBody(body, delay) {
  const walker = document.createTreeWalker(body, NodeFilter.SHOW_TEXT);
  const parts = [];
  while (walker.nextNode()) parts.push({ node: walker.currentNode, full: walker.currentNode.nodeValue });
  const total = parts.reduce((sum, part) => sum + part.full.length, 0);
  if (!total) return;
  for (const part of parts) {
    part.rest = document.createElement('span');
    part.rest.className = 'typing-rest';
    part.rest.textContent = part.full;
    part.node.nodeValue = '';
    part.node.after(part.rest);
  }
  const interval = Math.min(TYPE_CHARACTER, TYPE_BUDGET / total);
  const start = performance.now() + delay;
  const step = now => {
    if (!body.isConnected) return;
    let shown = Math.max(0, Math.floor((now - start) / interval));
    for (const part of parts) {
      const typed = Math.min(part.full.length, shown);
      shown -= typed;
      if (part.node.nodeValue.length === typed) continue;
      part.node.nodeValue = part.full.slice(0, typed);
      part.rest.textContent = part.full.slice(typed);
    }
    if (shown <= 0 && parts[parts.length - 1].rest.textContent) {
      requestAnimationFrame(step);
      return;
    }
    for (const part of parts) part.rest.remove();
  };
  requestAnimationFrame(step);
}

// Mirrors SHELL's Motion.notifications: rows 34ms apart, ten at most, characters 12ms apart; the budget keeps a long message from typing for seconds.
const ROW_ENTER_DELAY = 120;
const ROW_STAGGER = 34;
const ROW_STAGGER_LIMIT = 10;
const BODY_TYPE_AFTER = 200;
const TYPE_CHARACTER = 12;
const TYPE_BUDGET = 700;

export function retireNotifications() {
  if (!root.style.getPropertyValue('--notifications-drop')) return;
  root.style.removeProperty('--notifications-drop');
  root.classList.add('notifications-homing');
  setTimeout(() => root.classList.remove('notifications-homing'), NOTIFICATIONS_HOMING);
}

// Mirrors html.notifications-homing's 460ms in pill.css.
const NOTIFICATIONS_HOMING = 460;

// How far the lowest notifications menu stands off the bottom edge of the screen.
const NOTIFICATIONS_FOOT = 16;
// Mirrors --clear-size, --clear-gap and --clear-foot on #notifications-clear in pill.css.
const CLEAR_SIZE = 44;
const CLEAR_GAP = 12;
const CLEAR_STAGGER = 35;
const CLEAR_FLIGHT = 240;

const clearButton = document.getElementById('notifications-clear');

export function clearHolds(x, y) {
  if (shared.size !== 'notifications') return false;
  const box = clearButton.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}

export { clearButton };

clearButton.addEventListener('touchstart', () => clearButton.classList.add('pressing'), { passive: true });
clearButton.addEventListener('touchend', () => clearButton.classList.remove('pressing'), { passive: true });
clearButton.addEventListener('touchcancel', () => clearButton.classList.remove('pressing'), { passive: true });

clearButton.addEventListener('click', event => {
  event.stopPropagation();
  if (shared.size !== 'notifications') return;
  const rows = [...document.querySelectorAll('.notification-row')];
  for (const row of rows) JSON.parse(row.dataset.keys || '[]').forEach(key => bridge.dismissNotification(key));
  bridge.triggerHaptic('dismiss');
  rows.forEach((row, index) => {
    const delay = Math.min(index, 8) * CLEAR_STAGGER;
    row.style.transition = 'transform ' + CLEAR_FLIGHT + 'ms var(--ease-grow) ' + delay + 'ms, opacity ' + CLEAR_FLIGHT + 'ms ease ' + delay + 'ms';
    row.style.transform = 'translateX(400px)';
    row.style.opacity = '0';
  });
  document.getElementById('notifications-count').textContent = '';
  setTimeout(() => {
    if (shared.size === 'notifications') toClosed();
  }, Math.min(rows.length, 8) * CLEAR_STAGGER + CLEAR_FLIGHT);
});

function notificationsWindow() {
  // In landscape the delete-all bubble stands beside the menu rather than under it, so the menu may take every pixel under the bar and the proxy is widened for the bubble instead of lengthened.
  const isWide = isLandscape();
  const grab = parseFloat(getComputedStyle(root).getPropertyValue('--grab')) || 0;
  const highest = grab + GROWN_PAD;
  const tall = isWide ? root.clientHeight : screen.height;
  const below = isWide ? 0 : CLEAR_GAP + CLEAR_SIZE;
  const floor = Math.round(tall * 0.35);
  const ceiling = isWide ? tall - highest - NOTIFICATIONS_FOOT : Math.round(tall * 0.85) - below;
  const head = document.getElementById('notifications-head');
  const list = document.getElementById('notifications-list');
  const frame = 20;
  const wanted = head.offsetHeight + list.scrollHeight + frame;
  const height = Math.max(floor, Math.min(ceiling, wanted));
  const lowest = Math.max(highest, root.clientHeight - height - below - NOTIFICATIONS_FOOT);
  const top = Math.round(lowest + (highest - lowest) * shared.notificationsRise);
  const clearBottom = isWide ? top + height
    : shared.isClearCornered ? root.clientHeight - NOTIFICATIONS_FOOT : top + height + CLEAR_GAP + CLEAR_SIZE;
  // The proxy is measured from the top of the screen, so the room asked for has to reach the bottom of a bubble that now stands in the middle of it rather than just under the bar.
  return {
    width: SIZES.notifications.width + (isWide ? (CLEAR_GAP + CLEAR_SIZE) * 2 : 0),
    height: clearBottom - grab,
    geometry: {
      '--notifications-height': height + 'px',
      '--notifications-drop': (top - grab - GROWN_PAD) + 'px',
    },
  };
}
