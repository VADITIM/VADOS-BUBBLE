import { markUp } from './mods/notification.js';
import { becomeExtended, setSize, showFace, toClosed } from './row.js';
import { swipeToDismiss } from './swipeDismiss.js';
import { SIZES, bridge, root, shared } from './state.js';

/** What the panel does once a swipe has actually taken a row out — see closeGap() below. */
function afterRowRemoved(below, gap) {
  closeGap(below, gap);
  // The number in the header is the notifications, not the rows they were folded into, so a
  // row takes its whole group out of the count with it.
  const left = [...document.querySelectorAll('.history-row')]
    .reduce((sum, node) => sum + Number(node.dataset.count || 1), 0);
  document.getElementById('history-count').textContent = left ? String(left) : '';
  // A row that took the last notification with it leaves nothing to hold open.
  setTimeout(() => {
    if (shared.size === 'history' && !document.querySelector('.history-row')) toClosed();
  }, 220);
}

/**
 * The hole a dismissed row leaves, closed by moving rather than by disappearing.
 * A row removed from the flow takes its height out of the layout in one frame and
 * everything under it is simply drawn somewhere else — which is the teleport. So
 * the rows below are first offset by exactly the height that is about to vanish,
 * putting them back where they already were, and then let go: they travel up into
 * the gap while the panel shrinks around them, and the two moves are one.
 */
function closeGap(below, gap) {
  if (!below.length) return;
  for (const other of below) {
    other.style.transition = 'none';
    other.style.transform = 'translateY(' + gap + 'px)';
  }
  // The offset has to be a frame the browser has actually drawn, or there is
  // nothing for the rows to travel back from.
  requestAnimationFrame(() => {
    for (const other of below) {
      other.style.transition = 'transform 260ms var(--ease-grow)';
      other.style.transform = '';
    }
  });
  // The panel gives its height back on the same clock, so the list closing up and
  // the box closing around it are one move rather than two.
  if (shared.size === 'history') setSize('history', historyWindow(), true);
}

/** Which open size belongs to which mod — the hold needs to know whose app to open. */
export const MOD_FACES = { player: 'media', timer: 'timer' };

/**
 * Leaves for the app the open mod stands for. The timer goes through its own
 * notification rather than the launcher, because the clock app's intent lands on
 * the running timer instead of on whatever tab was last open.
 */
export function openMod() {
  leaveForMod(MOD_FACES[shared.size]);
}

/**
 * Out to the app a mod stands for, whichever way in that app has. The timer and a
 * call go through their own notification rather than the launcher: those intents
 * land on the running timer and inside the call, where a launcher intent lands on
 * whatever tab the app was last left on.
 */
export function leaveForMod(mod) {
  if (mod === 'media') {
    if (shared.media && shared.media.package) bridge.openApp(shared.media.package);
    return;
  }
  if (mod === 'timer' && shared.timer && shared.timer.key) bridge.openNotification(shared.timer.key);
  if (mod === 'call' && shared.call && shared.call.key) bridge.openNotification(shared.call.key);
}


/**
 * The list, as conversations rather than as notifications. Three messages from the
 * same person are one thing that happened, and drawn as three rows they push
 * everything else off the panel — which is the shade's own worst habit. So an app
 * and a title together make a group: the name is written once, the sender once, and
 * every line they sent underneath, in the order the shade is holding them.
 */
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

/**
 * How long ago something arrived, in the shortest true form. Minutes are the unit the list is
 * read in — "how stale is this" is the only question being asked of it — so anything under an
 * hour is minutes, and the first minute is "now" rather than "0m", which reads as broken.
 *
 * Empty for anything without a time, so a payload from before postedAt existed simply has no
 * age rather than an age of fifty-six years.
 */
function agoText(postedAt) {
  if (!postedAt) return '';
  const minutes = Math.floor((Date.now() - postedAt) / 60000);
  if (minutes < 1) return 'now';
  if (minutes < 60) return minutes + 'm';
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + 'h';
  return Math.floor(hours / 24) + 'd';
}

export function openHistory() {
  const list = document.getElementById('history-list');
  list.textContent = '';

  const entries = JSON.parse(bridge.readHistory() || '[]');
  document.getElementById('history-count').textContent =
    entries.length ? String(entries.length) : '';
  if (entries.length === 0) {
    const empty = document.createElement('div');
    empty.id = 'history-empty';
    empty.textContent = 'Nothing waiting';
    list.appendChild(empty);
  }

  for (const group of grouped(entries)) {
    const entry = group.head;
    const row = document.createElement('div');
    row.className = 'history-row';
    // What the row is worth to the count in the header, which counts notifications
    // and not the rows they were folded into.
    row.dataset.count = String(group.entries.length);
    // A swipe throws away the conversation, not the newest line of it: the row is
    // the whole group, and leaving the rest behind would redraw it a line shorter.
    swipeToDismiss(row, group.entries.map(one => one.key), {
      rowSelector: '.history-row',
      afterRemove: afterRowRemoved,
    });

    const mark = document.createElement('div');
    mark.className = 'history-mark';
    mark.style.background = entry.accent || '#ffffff';
    // An identity that is genuinely several colours — Google's four — carries them as a
    // gradient beside its flat accent. Only the two places that can take one use it; the
    // accent above stays the fallback and is what everything else reads.
    if (entry.gradient) {
      row.dataset.gradient = '';
      row.style.setProperty('--row-gradient', entry.gradient);
    }

    const copy = document.createElement('div');
    copy.className = 'history-copy';

    const head = document.createElement('div');
    head.className = 'history-head-row';
    const name = document.createElement('div');
    name.className = 'history-name';
    name.style.color = entry.accent || '#ffffff';
    name.textContent = entry.appName || entry.app || '';
    head.appendChild(name);
    if (group.entries.length > 1) {
      const tally = document.createElement('div');
      tally.className = 'history-tally';
      tally.textContent = group.entries.length;
      head.appendChild(tally);
    }
    // How long ago, at the far end of the head row. Read off the newest entry in the group
    // rather than the head: a conversation is grouped oldest-first for reading, but its age
    // is the age of the last thing said in it, not the first.
    const newest = group.entries.reduce(
      (latest, one) => (one.postedAt || 0) > (latest.postedAt || 0) ? one : latest, entry
    );
    const age = agoText(newest.postedAt);
    if (age) {
      const when = document.createElement('div');
      when.className = 'history-when';
      when.textContent = age;
      head.appendChild(when);
    }
    copy.appendChild(head);

    // The sender, written once for the whole group rather than in front of every
    // line they sent.
    if (entry.title) {
      const who = document.createElement('div');
      who.className = 'history-who';
      who.textContent = entry.title;
      copy.appendChild(who);
    }
    // A messenger keeps its whole conversation in one notification and rewrites it
    // as each line arrives, so the lines are the messages and `text` is only the
    // last of them. Every line is drawn, oldest first, and a divider is set between
    // them: they are one conversation and stay grouped as one, but three things
    // someone said are three things, not a paragraph.
    const lines = group.entries.flatMap(one =>
      (one.lines && one.lines.length)
        ? one.lines.map(line => line.text)
        : [one.text || (entry.title ? '' : one.title || '')]
    ).filter(Boolean);
    lines.forEach((line, index) => {
      const text = document.createElement('div');
      text.className = index ? 'history-text divided' : 'history-text';
      markUp(text, line);
      copy.appendChild(text);
    });

    row.append(mark, copy);
    row.addEventListener('click', event => {
      event.stopPropagation();
      // A swipe ends in a click too, and so does a scroll; neither the row that was
      // thrown away nor the one the finger merely rested on is a row that was chosen.
      if (row.dataset.swiped === 'true' || row.dataset.scrolled === 'true') return;
      bridge.openNotification(entry.key);
      bridge.triggerHaptic('tap');
      toClosed();
    });
    list.appendChild(row);
  }

  becomeExtended();
  showFace('history');
  setSize('history', historyWindow());
}

/**
 * As tall as the list actually is, and no taller — up to three quarters of the
 * screen, past which a panel hanging off the cutout stops reading as a bubble and
 * starts reading as a second shade. A short list gets a short panel: a fixed height
 * left two notifications sitting at the top of a box mostly full of nothing.
 *
 * Measured off the face while it is still the old size, which works because the
 * face is laid out at its final width from the first frame — see #history-face.
 */
function historyWindow() {
  const ceiling = Math.round(screen.height * 0.75);
  const head = document.getElementById('history-head');
  const list = document.getElementById('history-list');
  // Padding of the face, top and bottom, in the same units the rest of this is in.
  const frame = 20;
  const wanted = head.offsetHeight + list.scrollHeight + frame;
  const height = Math.max(SIZES.history.height, Math.min(ceiling, wanted));
  root.style.setProperty('--history-height', height + 'px');
  return { width: SIZES.history.width, height };
}
