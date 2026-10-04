import { FOCUS_FROST_MILLIS, stirLiquid } from './liquid.js';
import { bumpPill } from './lock.js';
import { markUp } from './mods/notification.js';
import { HOLD_BLOCK } from './motion.js';
import { SLING_SHOT_ANGLE, SLING_SHOT_LEAD, SLING_SHOT_MILLIS, SLING_SHOT_OVER, between, slingFrames, slingInto } from './sling.js';
import { SWIPE_AWAY } from './swipeDismiss.js';
import { agoText } from './tabs.js';
import { HOLD_MILLIS, bridge, pill, root, shared } from './state.js';

export const notesList = document.getElementById('lock-notes');
const notesStack = document.getElementById('lock-notes-stack');
const notesClear = document.getElementById('lock-notes-clear');
const notesMore = document.getElementById('lock-notes-more');

const NOTES_LIMIT = 3;
// Mirrors NOTES_PROXIES in BubbleService.kt: a window per group and one for the delete-all bubble.
const NOTES_PROXIES = NOTES_LIMIT + 1;
// Mirrors the "+ 5" in BubbleService.BLUR_PANES: a pane per group's head card, the delete-all bubble and the count.
export const NOTE_PANES = NOTES_LIMIT + 2;
// Mirrors --note-radius in pill.css.
const NOTE_RADIUS = 22;
const NOTES_SWIPE = 24;
const NOTES_TAP_SLOP = 22;
const RELAY_MILLIS = 420;
const RELAY_EASE = 'cubic-bezier(0.22, 1.12, 0.36, 1)';
const POP_MILLIS = 380;
const POP_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';
const POP_STAGGER = 60;
const LEAVE_MILLIS = 180;
const PUSH_CLEAR = 24;
const CLEAR_CORNER = 16;
const FLING_MILLIS = 240;
const CLEAR_STAGGER = 35;
const CLEAR_FLIGHT = 240;
const HOME_PACE = 1.1;
const HOME_STAGGER = 60;
const HOME_CONTENT_GONE = 0.8;
const HOME_FADE_FROM = 0.85;
const HOME_ASPECT = 1.6;

let isShowing = false;
let isLockedNow = false;
let isEnabled = true;
let isHomeAnimated = false;
let homeFlight = 0;
let isClearing = false;
let isRefreshWaiting = false;
let flings = 0;
let focus = null;
let press = null;
let shownKeys = [];
let dismissedKeys = new Set();
let knownKeys = new Set();
let heldKeys = new Set();
let slungKeys = new Set();
let sentProxies = '';

export function isNotesFocused() {
  return focus !== null;
}

function grouped(entries) {
  const groups = new Map();
  [...entries]
    .sort((one, two) => (two.postedAt || 0) - (one.postedAt || 0))
    .forEach(entry => {
      const id = entry.app || entry.appName || entry.key;
      if (!groups.has(id)) groups.set(id, { id, entries: [] });
      groups.get(id).entries.push(entry);
    });
  return [...groups.values()];
}

function liveGroups() {
  return [...notesStack.children].filter(group => !group.classList.contains('leaving') && !group.classList.contains('swiped'));
}

function buildCard(entry, count) {
  const card = document.createElement('div');
  card.className = 'lock-note';
  card.dataset.key = entry.key;
  const inner = document.createElement('div');
  inner.className = 'lock-note-inner';

  const badge = entry.appIconBase64 || entry.iconBase64;
  if (badge) {
    const icon = document.createElement('img');
    icon.className = 'lock-note-icon';
    icon.alt = '';
    icon.src = badge;
    inner.appendChild(icon);
  } else {
    const mark = document.createElement('div');
    mark.className = 'lock-note-icon lock-note-mark';
    mark.style.background = entry.accent || '#ffffff';
    mark.textContent = (entry.appName || entry.app || '').trim().charAt(0).toUpperCase();
    inner.appendChild(mark);
  }

  const copy = document.createElement('div');
  copy.className = 'lock-note-copy';
  const head = document.createElement('div');
  head.className = 'lock-note-head';
  const name = document.createElement('div');
  name.className = 'lock-note-name';
  name.style.color = entry.accent || '#ffffff';
  name.textContent = entry.appName || entry.app || '';
  head.appendChild(name);
  if (count > 1) {
    const tally = document.createElement('div');
    tally.className = 'lock-note-tally';
    tally.textContent = count;
    head.appendChild(tally);
  }
  const age = agoText(entry.postedAt);
  if (age) {
    const when = document.createElement('div');
    when.className = 'lock-note-when';
    when.textContent = age;
    head.appendChild(when);
  }
  copy.appendChild(head);

  if (entry.title) {
    const who = document.createElement('div');
    who.className = 'lock-note-who';
    who.textContent = entry.title;
    copy.appendChild(who);
  }
  const lines = (entry.lines && entry.lines.length)
    ? entry.lines.map(line => line.text)
    : [entry.text || ''];
  const body = document.createElement('div');
  body.className = 'lock-note-body';
  lines.filter(Boolean).forEach(line => {
    const text = document.createElement('div');
    text.className = 'lock-note-text';
    markUp(text, line);
    body.appendChild(text);
  });
  if (body.children.length) copy.appendChild(body);

  inner.appendChild(copy);
  card.appendChild(inner);
  return card;
}

function fillGroup(group, entries) {
  const expandedKey = focus && focus.card && focus.group === group ? focus.card.dataset.key : null;
  group.textContent = '';
  group.keys = entries.map(entry => entry.key);
  group.classList.toggle('stacked', entries.length > 1);
  entries.forEach((entry, index) => {
    const card = buildCard(entry, index === 0 ? entries.length : 0);
    card.style.zIndex = String(entries.length - index);
    group.appendChild(card);
  });
  if (focus && focus.group === group) {
    focus.card = expandedKey ? group.querySelector('[data-key="' + CSS.escape(expandedKey) + '"]') : null;
    if (focus.card) focus.card.classList.add('expanded');
    if (entries.length < 2) group.classList.remove('fanned');
  }
}

function markRunsOut() {
  notesList.querySelectorAll('.lock-note-who, .lock-note-text').forEach(text => {
    text.classList.toggle('runs-out', text.scrollWidth > text.clientWidth + 1);
  });
  notesList.querySelectorAll('.lock-note-body').forEach(body => {
    const tall = [...body.children].reduce((sum, line) => sum + line.offsetHeight, 0);
    body.classList.toggle('runs-out', tall > body.clientHeight + 1);
  });
}

function restingBox(element) {
  let left = 0;
  let top = 0;
  for (let node = element; node && node !== notesList; node = node.offsetParent) {
    left += node.offsetLeft + (node.shiftX || 0);
    top += node.offsetTop + (node.shiftY || 0);
  }
  return { left, top, width: element.offsetWidth, height: element.offsetHeight };
}

function layoutBox(element) {
  const box = restingBox(element);
  return { ...box, left: box.left - (element.shiftX || 0), top: box.top - (element.shiftY || 0) };
}

function setShift(element, x, y) {
  element.shiftX = x;
  element.shiftY = y;
  if (x || y) element.style.translate = x + 'px ' + y + 'px';
  else element.style.removeProperty('translate');
}

function movers() {
  return [...notesList.querySelectorAll('.lock-note, #lock-notes-clear, #lock-notes-more')];
}

function relay(change) {
  const before = new Map(movers().filter(element => element.offsetParent).map(element => [element, restingBox(element)]));
  const heights = new Map();
  notesList.querySelectorAll('.lock-note').forEach(card => {
    heights.set(card.dataset.key, card.offsetHeight);
    if (card.heightMove) card.heightMove.cancel();
  });

  const wasEmpty = notesList.classList.contains('empty');
  change();
  const isEmpty = notesStack.children.length === 0;
  notesList.classList.toggle('empty', isEmpty);
  if ((wasEmpty || notesClear.classList.contains('leaving')) && liveGroups().length) {
    resetBubble(notesClear);
    popIn(notesClear, 0);
  }
  markRunsOut();
  fitNotesProxies();

  notesList.querySelectorAll('.lock-note').forEach(card => {
    const from = heights.get(card.dataset.key);
    const to = card.offsetHeight;
    if (from === undefined || Math.abs(from - to) < 1) return;
    card.heightMove = card.animate(
      [{ height: from + 'px' }, { height: to + 'px' }],
      { duration: RELAY_MILLIS, easing: RELAY_EASE }
    );
  });

  let arrivals = 0;
  movers().forEach(element => {
    const from = before.get(element) || (element.dataset.key && [...before.entries()]
      .find(([old]) => old.dataset.key === element.dataset.key)?.[1]);
    if (!from) {
      if (element.offsetParent && !element.closest('.slung')) popIn(element, arrivals++ * POP_STAGGER);
      return;
    }
    const to = restingBox(element);
    const dx = from.left - to.left;
    const dy = from.top - to.top;
    if (Math.abs(dx) < 0.5 && Math.abs(dy) < 0.5) return;
    element.animate(
      [{ transform: 'translate(' + dx + 'px,' + dy + 'px)' }, { transform: 'translate(0px,0px)' }],
      { duration: RELAY_MILLIS, easing: RELAY_EASE, composite: 'add' }
    );
  });

  stirLiquid(Math.max(RELAY_MILLIS, FOCUS_FROST_MILLIS) + 200);
}

function popIn(element, delay) {
  element.animate(
    [{ transform: 'scale(0.6)' }, { transform: 'scale(1)' }],
    { duration: POP_MILLIS, delay, easing: POP_EASE, composite: 'add', fill: 'backwards' }
  );
  element.animate(
    [{ opacity: 0 }, { opacity: 1 }],
    { duration: POP_MILLIS * 0.5, delay, fill: 'backwards' }
  );
  stirLiquid(POP_MILLIS + delay + 200);
}

function resetBubble(bubble) {
  bubble.classList.remove('leaving');
  bubble.getAnimations().forEach(move => move.cancel());
}

function leave(element, then) {
  element.classList.add('leaving');
  const move = element.animate(
    [{ transform: 'scale(1)', opacity: 1 }, { transform: 'scale(0.8)', opacity: 0 }],
    { duration: LEAVE_MILLIS, easing: 'ease-in', fill: 'forwards' }
  );
  move.addEventListener('finish', then);
  stirLiquid(LEAVE_MILLIS + 200);
}

function read() {
  return JSON.parse(bridge.readNotifications() || '[]');
}

function paint() {
  const entries = read();
  const present = new Set(entries.map(entry => entry.key));
  dismissedKeys = new Set([...dismissedKeys].filter(key => present.has(key)));
  if (shared.state === 'alert') entries.forEach(entry => {
    if (!knownKeys.has(entry.key)) heldKeys.add(entry.key);
  });
  else {
    slungKeys = heldKeys;
    heldKeys = new Set();
  }
  knownKeys = present;
  heldKeys = new Set([...heldKeys].filter(key => present.has(key)));
  const groups = grouped(entries.filter(entry => !dismissedKeys.has(entry.key) && !heldKeys.has(entry.key)));
  shownKeys = groups.flatMap(group => group.entries.map(entry => entry.key));
  const shown = groups.slice(0, NOTES_LIMIT);
  const hidden = groups.length - shown.length;
  const wanted = new Set(shown.map(group => group.id));

  const isFocusGone = focus && !wanted.has(focus.group.dataset.id);

  const leaving = liveGroups().filter(group => !wanted.has(group.dataset.id));
  leaving.forEach(group => leave(group, () => relay(() => group.remove())));
  if (!shown.length && leaving.length) leave(notesClear, () => {});
  if (!hidden && notesMore.classList.contains('present')) {
    leave(notesMore, () => relay(() => {
      notesMore.classList.remove('present');
      resetBubble(notesMore);
    }));
  }

  relay(() => {
    notesStack.querySelectorAll('.flown').forEach(element => element.remove());
    if (isFocusGone) clearFocus();
    shown.forEach((group, index) => {
      let element = liveGroups().find(old => old.dataset.id === group.id);
      if (!element) {
        element = document.createElement('div');
        element.className = 'lock-group';
        element.dataset.id = group.id;
        if (group.entries.some(entry => slungKeys.has(entry.key))) element.classList.add('slung');
      }
      fillGroup(element, group.entries);
      const after = index === 0 ? null : liveGroups().find(old => old.dataset.id === shown[index - 1].id);
      if (after) after.after(element);
      else notesStack.prepend(element);
    });
    if (hidden > 0) {
      notesMore.textContent = '+' + hidden;
      resetBubble(notesMore);
      notesMore.classList.add('present');
    }
    if (focus) {
      pushOthers(focus.group);
      setShift(focus.group, 0, -(focus.group.offsetTop + notesStack.offsetTop));
    }
  });
  slungKeys = new Set();
  splitFromMain();
}

function splitFromMain() {
  const slung = [...notesStack.querySelectorAll('.lock-group.slung')].filter(group => !group.flight);
  if (!slung.length) return;
  const main = pill.getBoundingClientRect();
  const born = parseFloat(getComputedStyle(root).getPropertyValue('--pill-height')) || main.height;
  let longest = 0;
  slung.forEach(group => {
    group.flight = slingInto(group, main, group.getBoundingClientRect(), {
      isWhole: true,
      born,
      onOpened: () => {
        group.flight = null;
        group.classList.remove('slung');
      },
    });
    longest = Math.max(longest, group.flight.total);
  });
  stirLiquid(longest + 200);
}

export function setNotesEnabled(isOn) {
  isEnabled = isOn;
  paintNotes(isLockedNow);
}

export function paintNotes(isLockedNext) {
  isLockedNow = isLockedNext;
  const isLocked = isLockedNext && isEnabled;
  if (isLocked === isShowing) return;
  isShowing = isLocked;
  press = null;
  if (isLocked) {
    homeFlight += 1;
    notesStack.textContent = '';
    resetBubble(notesClear);
    resetBubble(notesMore);
    notesMore.classList.remove('present');
    notesList.classList.add('showing', 'empty');
    paint();
    return;
  }
  dropFocus();
  fitNotesProxies();
  const groups = liveGroups();
  const elements = [...groups];
  if (groups.length) elements.push(notesClear);
  if (notesMore.classList.contains('present')) elements.push(notesMore);
  if (isHomeAnimated && elements.length) flyHome(elements);
  else hideNotes();
}

export function setNotesHomeAnimated(isOn) {
  isHomeAnimated = isOn;
}

function hideNotes() {
  homeFlight += 1;
  notesStack.textContent = '';
  resetBubble(notesClear);
  resetBubble(notesMore);
  notesMore.classList.remove('present');
  notesList.classList.remove('showing');
  stirLiquid(200);
}

function flyHome(elements) {
  const flight = ++homeFlight;
  const main = pill.getBoundingClientRect();
  const mainX = main.left + main.width / 2;
  const mainY = main.top + main.height / 2;
  const measured = elements.map(element => ({
    box: element.getBoundingClientRect(),
    width: element.offsetWidth,
    height: element.offsetHeight,
    cards: [...element.querySelectorAll('.lock-note')].map(card => ({
      card,
      inner: card.firstElementChild,
      innerWidth: card.firstElementChild.offsetWidth,
      radius: card.offsetHeight / 2,
    })),
  }));
  let longest = 0;
  let isBumped = false;
  let left = elements.length;
  elements.forEach((element, index) => {
    const { box, width, height, cards } = measured[index];
    const isGroup = element.classList.contains('lock-group');
    const dx = mainX - (box.left + box.width / 2);
    const dy = mainY - (box.top + box.height / 2);
    const reach = Math.hypot(dx, dy) || 1;
    const angle = ((Math.random() < 0.5 ? -1 : 1) * SLING_SHOT_ANGLE * Math.PI) / 180;
    const millis = Math.round(between(SLING_SHOT_MILLIS) * HOME_PACE);
    const delay = index * HOME_STAGGER;
    longest = Math.max(longest, millis + delay);
    const frames = slingFrames({
      startX: 0,
      startY: 0,
      holdX: (Math.cos(angle) * dx - Math.sin(angle) * dy) * SLING_SHOT_LEAD,
      holdY: (Math.sin(angle) * dx + Math.cos(angle) * dy) * SLING_SHOT_LEAD,
      endX: dx,
      endY: dy,
      scaleStart: 1,
      scaleEnd: Math.min(1, main.height / box.height),
      over: between(SLING_SHOT_OVER),
      reach,
    });
    let furthest = 0;
    const travelled = frames.map(frame => {
      const [x, y] = frame.translate.split(' ').map(parseFloat);
      furthest = Math.max(furthest, Math.min(1, 1 - Math.hypot(dx - x, dy - y) / reach));
      return furthest;
    });
    const narrowest = Math.min(width, height * HOME_ASPECT);
    const timing = { duration: millis, delay, fill: 'both' };

    const move = element.animate(frames.map((frame, step) => {
      if (!isGroup) return frame;
      const [x, y] = frame.translate.split(' ').map(parseFloat);
      const across = width + (narrowest - width) * travelled[step];
      return {
        offset: frame.offset,
        translate: (x + (width - across) / 2).toFixed(1) + 'px ' + y.toFixed(1) + 'px',
        scale: frame.scale,
        width: across.toFixed(1) + 'px',
      };
    }), timing);
    element.animate(frames.map((frame, step) => ({
      offset: frame.offset,
      opacity: travelled[step] < HOME_FADE_FROM ? 1 : 1 - (travelled[step] - HOME_FADE_FROM) / (1 - HOME_FADE_FROM),
    })), timing);

    const contentFade = frames.map((frame, step) => ({
      offset: frame.offset,
      opacity: Math.max(0, 1 - travelled[step] / HOME_CONTENT_GONE),
    }));
    if (isGroup) {
      element.classList.add('homing');
      cards.forEach(({ card, inner, innerWidth, radius }) => {
        inner.style.width = innerWidth + 'px';
        inner.animate(contentFade, timing);
        card.animate(frames.map((frame, step) => ({
          offset: frame.offset,
          borderRadius: (NOTE_RADIUS + (radius - NOTE_RADIUS) * travelled[step]).toFixed(1) + 'px',
        })), timing);
      });
    }
    else if (element.firstElementChild) element.firstElementChild.animate(contentFade, timing);

    move.addEventListener('finish', () => {
      if (flight !== homeFlight) return;
      if (!isBumped) {
        isBumped = true;
        bumpPill();
      }
      left -= 1;
      if (!left && !isShowing) hideNotes();
    });
  });
  stirLiquid(longest + 200);
}

export function refreshNotes() {
  if (!isShowing) return;
  if (press || isClearing || flings) {
    isRefreshWaiting = true;
    return;
  }
  paint();
}

function pushOthers(kept) {
  const origin = notesList.getBoundingClientRect();
  const corner = layoutBox(notesClear);
  setShift(notesClear,
    root.clientWidth - CLEAR_CORNER - corner.width - origin.left - corner.left,
    root.clientHeight - CLEAR_CORNER - corner.height - origin.top - corner.top);
  const keptTop = layoutBox(kept).top;
  [...liveGroups().filter(group => group !== kept), notesMore].forEach(element => {
    const box = layoutBox(element);
    const isAbove = box.top < keptTop;
    setShift(element, 0, isAbove
      ? -(origin.top + box.top + box.height + PUSH_CLEAR)
      : root.clientHeight - origin.top - box.top + PUSH_CLEAR);
  });
}

function gather(group) {
  group.classList.add('gathering');
  const fades = [...group.children].slice(1).map((card, index) => (index === 0 ? card.firstElementChild : card)
    .animate([{ opacity: 1 }, { opacity: 0 }], { duration: RELAY_MILLIS, easing: 'ease-in' }));
  group.gatherFades = fades;
  if (!fades.length) group.classList.remove('gathering');
  else fades[fades.length - 1].addEventListener('finish', () => group.classList.remove('gathering'));
}

function stopGather(group) {
  (group.gatherFades || []).forEach(fade => fade.cancel());
  group.classList.remove('gathering');
}

function focusOn(group, card, isFanned) {
  stopGather(group);
  relay(() => {
    focus = { group, card };
    notesList.classList.add('focused');
    group.classList.add('focused');
    group.classList.toggle('fanned', isFanned && group.children.length > 1);
    if (card) card.classList.add('expanded');
    pushOthers(group);
    setShift(group, 0, -(group.offsetTop + notesStack.offsetTop));
  });
}

function toggleExpanded(card) {
  relay(() => {
    if (focus.card && focus.card !== card) focus.card.classList.remove('expanded');
    const isExpanding = !card.classList.contains('expanded');
    card.classList.toggle('expanded', isExpanding);
    focus.card = isExpanding ? card : null;
  });
}

function clearFocus() {
  const fanned = [...notesStack.querySelectorAll('.lock-group.fanned')];
  notesList.classList.remove('focused');
  notesList.querySelectorAll('.focused, .fanned, .expanded').forEach(element => {
    element.classList.remove('focused', 'fanned', 'expanded');
  });
  [notesClear, notesMore, ...liveGroups()].forEach(element => setShift(element, 0, 0));
  fanned.forEach(gather);
  focus = null;
}

function leaveFocus() {
  if (!focus) return;
  bridge.triggerHaptic('release');
  relay(clearFocus);
}

export function dropFocus() {
  if (!focus) return;
  clearFocus();
  fitNotesProxies();
  stirLiquid(FOCUS_FROST_MILLIS + 200);
}

function clearAll() {
  if (isClearing) return;
  isClearing = true;
  shownKeys.forEach(key => bridge.dismissNotification(key));
  bridge.triggerHaptic('dismiss');
  fitNotesProxies();
  const width = root.clientWidth;
  const groups = liveGroups();
  groups.forEach((group, index) => {
    group.animate(
      [{ transform: 'translateX(0px)', opacity: 1 }, { transform: 'translateX(' + width + 'px)', opacity: 0 }],
      { duration: CLEAR_FLIGHT, delay: index * CLEAR_STAGGER, easing: RELAY_EASE, fill: 'forwards' }
    );
  });
  const flight = groups.length * CLEAR_STAGGER + CLEAR_FLIGHT;
  stirLiquid(flight + 200);
  setTimeout(() => leave(notesClear, () => {}), Math.max(0, flight - LEAVE_MILLIS));
  setTimeout(() => {
    isClearing = false;
    isRefreshWaiting = false;
    groups.forEach(group => group.remove());
    if (isShowing) paint();
  }, flight);
}

function groupAt(x, y) {
  return liveGroups().find(group => {
    const box = group.getBoundingClientRect();
    return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
  });
}

function cardAt(group, x, y) {
  if (!group.classList.contains('fanned')) return group.firstElementChild;
  return [...group.children].find(card => {
    const box = card.getBoundingClientRect();
    return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
  }) || null;
}

function holds(element, x, y) {
  const box = element.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}

function slider() {
  const isCard = focus && focus.group === press.group && focus.group.classList.contains('fanned');
  return isCard ? press.card : press.group;
}

function slide(dx) {
  press.slider.style.transform = 'translateX(' + dx + 'px)';
  const isPast = Math.abs(dx) >= SWIPE_AWAY;
  if (isPast !== press.isPastAway) bridge.triggerHaptic('tap');
  press.isPastAway = isPast;
}

function settleSlide(dx) {
  const element = press.slider;
  element.style.removeProperty('transform');
  if (Math.abs(dx) < SWIPE_AWAY) {
    element.animate(
      [{ transform: 'translateX(' + dx + 'px)' }, { transform: 'translateX(0px)' }],
      { duration: RELAY_MILLIS, easing: POP_EASE, composite: 'add' }
    );
    stirLiquid(RELAY_MILLIS + 200);
    return;
  }
  const isGroup = element === press.group;
  const keys = isGroup ? press.group.keys : [element.dataset.key];
  keys.forEach(key => {
    dismissedKeys.add(key);
    bridge.dismissNotification(key);
  });
  bridge.triggerHaptic('dismiss');
  element.classList.add('swiped');
  if (isGroup && focus && focus.group === element) relay(clearFocus);
  if (isGroup && !liveGroups().length) leave(notesClear, () => {});
  fitNotesProxies();
  const away = Math.sign(dx) * root.clientWidth;
  const fling = element.animate(
    [{ transform: 'translateX(' + dx + 'px)', opacity: 1 }, { transform: 'translateX(' + away + 'px)', opacity: 0 }],
    { duration: FLING_MILLIS, easing: 'ease-in', fill: 'forwards' }
  );
  flings += 1;
  fling.addEventListener('finish', () => {
    flings -= 1;
    element.classList.add('flown');
    if (flings || press) {
      isRefreshWaiting = true;
      return;
    }
    isRefreshWaiting = false;
    if (isShowing) paint();
  });
  stirLiquid(FLING_MILLIS + 200);
}

function releasePress() {
  if (!press) return;
  clearTimeout(press.timer);
  if (press.card) press.card.classList.remove('pressed');
  notesClear.classList.remove('pressed');
  press = null;
  if (isRefreshWaiting) {
    isRefreshWaiting = false;
    refreshNotes();
  }
}

export function notesTouch(action, x, y) {
  if (isClearing) return;
  if (action === 'down') {
    releasePress();
    const isClear = holds(notesClear, x, y);
    const group = isClear ? null : groupAt(x, y);
    const card = group && (!focus || focus.group === group) ? cardAt(group, x, y) : null;
    press = { x, y, group, card, isClear, isHeld: false, isSwiped: false, slider: null, isPastAway: false, timer: 0 };
    if (card) card.classList.add('pressed');
    if (isClear) notesClear.classList.add('pressed');
    if (card) {
      press.timer = setTimeout(() => {
        if (!press || press.card !== card) return;
        press.isHeld = true;
        card.classList.remove('pressed');
        bridge.triggerHaptic('expand');
        if (focus) toggleExpanded(card);
        else focusOn(group, card, true);
      }, HOLD_MILLIS);
    }
    stirLiquid(420);
    return;
  }
  if (!press) return;
  const dx = x - press.x;
  const dy = y - press.y;
  if (action === 'move') {
    if (press.slider) {
      slide(dx);
      return;
    }
    if (press.isHeld || press.isSwiped || Math.hypot(dx, dy) <= HOLD_BLOCK) return;
    clearTimeout(press.timer);
    if (press.card) press.card.classList.remove('pressed');
    if (press.card && Math.abs(dx) > NOTES_SWIPE && Math.abs(dx) > Math.abs(dy)) {
      press.slider = slider();
      slide(dx);
      return;
    }
    if (Math.abs(dy) <= NOTES_SWIPE || Math.abs(dy) <= Math.abs(dx)) return;
    press.isSwiped = true;
    if (dy < 0) {
      leaveFocus();
      return;
    }
    if (focus || !press.group) return;
    bridge.triggerHaptic('expand');
    const isStack = press.group.children.length > 1;
    focusOn(press.group, isStack ? null : press.group.firstElementChild, isStack);
    return;
  }
  if (press.slider) {
    settleSlide(dx);
    releasePress();
    return;
  }
  const isTap = action === 'up' && !press.isHeld && !press.isSwiped && Math.hypot(dx, dy) < NOTES_TAP_SLOP;
  const { card, isClear } = press;
  releasePress();
  if (!isTap) return;
  if (isClear) {
    clearAll();
    return;
  }
  if (!card) {
    leaveFocus();
    return;
  }
  bridge.triggerHaptic('tap');
  bridge.openNotification(card.dataset.key);
}

export function leaveNotesOutside(x, y) {
  if (!focus || holds(focus.group, x, y) || holds(notesClear, x, y)) return;
  leaveFocus();
}

export function fitNotesProxies() {
  const boxes = [];
  if (isShowing && !isClearing) {
    const origin = notesList.getBoundingClientRect();
    const place = element => {
      const box = restingBox(element);
      boxes.push([origin.left + box.left, origin.top + box.top, box.width, box.height]);
    };
    if (focus) {
      place(focus.group);
      place(notesClear);
    } else {
      liveGroups().forEach(place);
      if (boxes.length) place(notesClear);
    }
  }
  const spec = boxes.slice(0, NOTES_PROXIES).map(box => box.map(Math.round).join(',')).join(';');
  if (spec === sentProxies) return;
  sentProxies = spec;
  bridge.setNotesProxies(spec);
}

export function forgetNotesProxies() {
  sentProxies = null;
}

const notesStyle = getComputedStyle(notesList);

export function notePanes() {
  const panes = new Array(NOTE_PANES).fill(null);
  if (!notesList.classList.contains('showing')) return panes;
  const alpha = parseFloat(notesStyle.opacity);
  const sources = [...notesStack.children].filter(group => !group.classList.contains('leaving'))
    .slice(0, NOTES_LIMIT).map(group => group.firstElementChild);
  sources.forEach((card, index) => {
    if (card) panes[index] = { box: card.getBoundingClientRect(), radius: NOTE_RADIUS, alpha };
  });
  if (liveGroups().length) panes[NOTES_LIMIT] = { box: notesClear.getBoundingClientRect(), radius: 999, alpha };
  if (notesMore.classList.contains('present')) {
    panes[NOTES_LIMIT + 1] = { box: notesMore.getBoundingClientRect(), radius: 999, alpha };
  }
  return panes;
}
