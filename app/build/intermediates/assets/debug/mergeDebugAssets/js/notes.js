import { stirLiquid } from './liquid.js';
import { HOLD_BLOCK } from './motion.js';
import { swipeToDismiss } from './swipeDismiss.js';
import { bridge, HOLD_MILLIS } from './state.js';












const notesPill = document.getElementById('lock-notes');
const notesList = document.getElementById('lock-notes-list');






const NOTES_LIMIT = 5;

let showing = false;


let locked = false;


let allowed = true;

window.setNotesShown = wanted => {
  const next = Boolean(Number(wanted));
  if (next === allowed) return;
  allowed = next;
  paintNotes(locked);
};

export function isNotesShowing() {
  return showing;
}


export function noteAt(index) {
  return (showing && notesList.children[index]) || null;
}





function grouped(entries) {
  const byWho = new Map();
  for (const entry of entries) {
    const who = (entry.package || entry.app || '') + ' ' + (entry.title || '');
    if (!byWho.has(who)) byWho.set(who, []);
    byWho.get(who).push(entry);
  }
  return [...byWho.values()];
}


function linesOf(entry) {
  return (entry.lines && entry.lines.length) ? entry.lines.map(line => line.text) : [entry.text || ''];
}


function initialOf(entry) {
  const name = entry.appName || entry.app || '?';
  return name.trim().charAt(0).toUpperCase() || '?';
}


function armHold(note, entry) {
  let start = null;
  let holdTimer = null;
  note.addEventListener('touchstart', event => {
    start = { x: event.touches[0].clientX, y: event.touches[0].clientY };
    note.dataset.held = 'false';
    clearTimeout(holdTimer);
    holdTimer = setTimeout(() => {
      note.dataset.held = 'true';
      bridge.triggerHaptic('expand');
      if (entry.key) bridge.openNotification(entry.key);
    }, HOLD_MILLIS);
  }, { passive: true });
  note.addEventListener('touchmove', event => {
    if (!start || note.dataset.held === 'true') return;
    const dx = event.touches[0].clientX - start.x;
    const dy = event.touches[0].clientY - start.y;
    if (Math.hypot(dx, dy) > HOLD_BLOCK) clearTimeout(holdTimer);
  }, { passive: true });
  const release = () => clearTimeout(holdTimer);
  note.addEventListener('touchend', release, { passive: true });
  note.addEventListener('touchcancel', release, { passive: true });
}








function armExpand(note) {
  note.addEventListener('click', () => {
    if (note.dataset.swiped === 'true' || note.dataset.scrolled === 'true') return;
    if (note.dataset.held === 'true') return;
    const already = note.classList.contains('expanded');
    
    notesList.querySelectorAll('.lock-note.expanded').forEach(other => {
      if (other !== note) other.classList.remove('expanded');
    });
    note.classList.toggle('expanded', !already);
    bridge.triggerHaptic('tap');
    
    
    stirLiquid(420);
  });
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
  fitNotesProxy();
}






export function paintNotes(isLocked) {
  locked = isLocked;
  showing = isLocked && allowed;
  notesPill.classList.toggle('showing', showing);
  if (!showing) {
    notesList.textContent = '';
    fitNotesProxy();
    return;
  }

  const entries = JSON.parse(bridge.readHistory() || '[]');
  const groups = grouped(entries);
  notesList.textContent = '';

  groups.slice(0, NOTES_LIMIT).forEach(group => {
    const entry = group[group.length - 1];
    const note = document.createElement('div');
    note.className = 'lock-note';
    note.style.setProperty('--note-accent', entry.accent || '#ffffff');

    const iconWrap = document.createElement('div');
    iconWrap.className = 'lock-note-icon-wrap';
    if (entry.iconBase64) {
      const icon = document.createElement('img');
      icon.className = 'lock-note-icon';
      icon.alt = '';
      icon.src = entry.iconBase64;
      iconWrap.appendChild(icon);
    } else {
      iconWrap.classList.add('fallback');
      iconWrap.textContent = initialOf(entry);
    }

    const copy = document.createElement('div');
    copy.className = 'lock-note-copy';

    const head = document.createElement('div');
    head.className = 'lock-note-head';
    const who = document.createElement('span');
    who.className = 'lock-note-who';
    who.textContent = entry.title || entry.appName || entry.app || '';
    head.appendChild(who);
    const app = document.createElement('span');
    app.className = 'lock-note-app';
    app.textContent = entry.appName || entry.app || '';
    head.appendChild(app);
    copy.appendChild(head);

    const lines = linesOf(entry);
    const said = document.createElement('div');
    said.className = 'lock-note-said';
    said.textContent = lines[lines.length - 1];
    copy.appendChild(said);

    
    
    if (lines.length > 1) {
      const more = document.createElement('div');
      more.className = 'lock-note-more';
      const inner = document.createElement('div');
      inner.className = 'lock-note-more-inner';
      lines.slice(0, -1).forEach(text => {
        const line = document.createElement('div');
        line.className = 'lock-note-line';
        line.textContent = text;
        inner.appendChild(line);
      });
      more.appendChild(inner);
      copy.appendChild(more);
    }

    note.append(iconWrap, copy);
    if (group.length > 1) {
      const tally = document.createElement('div');
      tally.className = 'lock-note-tally';
      tally.textContent = group.length;
      note.appendChild(tally);
    }

    const keys = group.map(one => one.key).filter(Boolean);
    swipeToDismiss(note, keys, {
      rowSelector: '.lock-note',
      afterRemove: closeGap,
    });
    armHold(note, entry);
    armExpand(note);
    notesList.appendChild(note);
  });

  const hidden = groups.length - NOTES_LIMIT;
  notesList.dataset.more = hidden > 0 ? '+' + hidden : '';
  fitNotesProxy();
  stirLiquid(420);
}






export function fitNotesProxy() {
  if (!showing || !notesList.children.length) {
    bridge.setNotesProxy(0, 0, 0, 0);
    return;
  }
  const box = notesPill.getBoundingClientRect();
  bridge.setNotesProxy(
    Math.round(box.width), Math.round(box.height), Math.round(box.left), Math.round(box.top)
  );
}


window.onNotesChanged = () => {
  if (showing) paintNotes(true);
};
