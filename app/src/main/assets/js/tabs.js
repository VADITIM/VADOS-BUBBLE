import { duplicateDouble, duplicateReach } from './double.js';
import { markUp } from './mods/notification.js';
import { becomeExtended, setSize, showFace, toClosed } from './row.js';
import { swipeToDismiss } from './swipeDismiss.js';
import { SIZES, bridge, root, shared } from './state.js';


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
  
  
  if (shared.size === 'notifications') setSize('notifications', notificationsWindow(), true);
}


export const MOD_FACES = { player: 'media', timer: 'timer' };






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









function agoText(postedAt) {
  if (!postedAt) return '';
  const minutes = Math.floor((Date.now() - postedAt) / 60000);
  if (minutes < 1) return 'now';
  if (minutes < 60) return minutes + 'm';
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + 'h';
  return Math.floor(hours / 24) + 'd';
}

export function openNotifications() {
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

  for (const group of grouped(entries)) {
    const entry = group.head;
    const row = document.createElement('div');
    row.className = 'notification-row';
    
    
    row.dataset.count = String(group.entries.length);
    
    
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
    name.textContent = entry.appName || entry.app || '';
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
    lines.forEach((line, index) => {
      const text = document.createElement('div');
      text.className = index ? 'notification-text divided' : 'notification-text';
      markUp(text, line);
      copy.appendChild(text);
    });

    // The large icon a messenger posts is whoever wrote the message, not the app, so a list read at a glance says which app it came from and falls back to the notification's own icon where an app has none.
    const badge = entry.appIconBase64 || entry.iconBase64;
    if (badge) {
      const icon = document.createElement('img');
      icon.className = 'notification-icon';
      icon.alt = '';
      icon.src = badge;
      row.append(mark, icon, copy);
    } else {
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
  }

  const box = notificationsWindow();
  duplicateDouble(entries.length ? String(entries.length) + ' waiting' : 'Nothing waiting', box);
  becomeExtended();
  showFace('notifications');
  // The menu is two bubbles and the lower one has to answer a pull, so the room asked for reaches its bottom edge rather than the menu's — this is the modal's own area for as long as it stands, and it is handed back at the close.
  setSize('notifications', { width: box.width, height: duplicateReach() });
}










function notificationsWindow() {
  const ceiling = Math.round(screen.height * 0.75);
  const head = document.getElementById('notifications-head');
  const list = document.getElementById('notifications-list');
  
  const frame = 20;
  const wanted = head.offsetHeight + list.scrollHeight + frame;
  const height = Math.max(SIZES.notifications.height, Math.min(ceiling, wanted));
  root.style.setProperty('--notifications-height', height + 'px');
  return { width: SIZES.notifications.width, height };
}
