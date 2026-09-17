import { stirLiquid } from './liquid.js';
import { markUp } from './mods/notification.js';
import { SLING_POP_EASE, SLING_SHOT_EASE, slingInto } from './sling.js';
import { statsBox, statsIn, statsOut } from './status.js';
import { bridge, pill, root } from './state.js';


// The stats section has two channels and the swipe is what turns the dial between them: what the phone *is* — the vitals, the charge, the weather, the day's data — or what is *waiting* on it. One section, two readings of the same corner of the screen, and nothing else in the panel moves for the change.
const CHANNEL_INSET = 4;


// How far the reading travels when the hand swipes between entries. It is the card's contents that move and never the card: the box is the section's and the section has not gone anywhere.
const CHANNEL_SLIDE = 54;
const CHANNEL_STEP_OUT = 130;
const CHANNEL_STEP_IN = 210;


// Mirrors --channel-home-ms in pill.css: the card's flight back to the bubble it came out of.
const CHANNEL_HOME = 300;


// Mirrors --spread-ms in pill.css: a stack opening out over the whole height, and closing back into itself.
const SPREAD_MILLIS = 420;
const SPREAD_INSET = 14;
const SPREAD_GAP = 10;
const SPREAD_TOP = 92;


// The stack shows one layer under the card and never two. A second would be a list drawn badly; one is the only thing a stack has to say, which is that there is more than what is being read.
const UNDER_DROP = 7;
const UNDER_SHRINK = 14;

const channel = document.getElementById('channel');
const channelUnder = document.getElementById('channel-under');
const channelSpread = document.getElementById('channel-spread');
const channelIcon = document.getElementById('channel-icon');
const channelApp = document.getElementById('channel-app');
const channelWhen = document.getElementById('channel-when');
const channelWho = document.getElementById('channel-who');
const channelText = document.getElementById('channel-text');
const channelTally = document.getElementById('channel-tally');

let channelLive = false;
let channelFlight = null;
let groups = [];
let atGroup = 0;
let spreadOpen = false;
let stepTimer = 0;
let homeTimer = 0;

export function isChannelLive() {
  return channelLive;
}

export function isSpreadOpen() {
  return spreadOpen;
}


// One group is one app. The Alert's own list groups by app *and* conversation, because a list is read line by line and two chats in one app are two things; a stack is glanced at, and what it is standing for is the app.
function groupsOf(entries) {
  const byApp = new Map();
  for (const entry of entries) {
    const id = entry.app || entry.appName || '';
    const group = byApp.get(id);
    if (group) group.entries.push(entry);
    else byApp.set(id, { id, entries: [entry] });
  }
  return [...byApp.values()];
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

function firstLine(entry) {
  const lines = (entry.lines || []).map(line => line.text).filter(Boolean);
  return lines.length ? lines[lines.length - 1] : (entry.text || '');
}

function readGroups() {
  groups = groupsOf(JSON.parse(bridge.readNotifications() || '[]'));
  if (atGroup >= groups.length) atGroup = 0;
}

function paintChannel() {
  const group = groups[atGroup];
  channel.classList.toggle('empty', !group);
  channelUnder.classList.toggle('showing', Boolean(group) && group.entries.length > 1);
  if (!group) {
    channelIcon.classList.remove('present');
    channelApp.textContent = 'Nothing waiting';
    channelWhen.textContent = '';
    channelWho.textContent = '';
    channelText.textContent = '';
    channelTally.textContent = '';
    return;
  }

  const entry = group.entries.reduce(
    (latest, one) => (one.postedAt || 0) > (latest.postedAt || 0) ? one : latest, group.entries[0]
  );
  const badge = entry.appIconBase64 || entry.iconBase64;
  channelIcon.classList.toggle('present', Boolean(badge));
  if (badge) channelIcon.src = badge;
  channel.style.setProperty('--channel-accent', entry.accent || '#ffffff');
  channelApp.textContent = entry.appName || entry.app || '';
  channelWhen.textContent = agoText(entry.postedAt);
  channelWho.textContent = entry.title || '';
  markUp(channelText, firstLine(entry));
  channelTally.textContent = group.entries.length > 1 ? String(group.entries.length) : '';
}

function layOutChannel() {
  const box = statsBox();
  const width = Math.round(box.width - CHANNEL_INSET * 2);
  const height = Math.round(box.height - CHANNEL_INSET * 2);
  const left = Math.round(box.left + CHANNEL_INSET);
  const top = Math.round(box.top + CHANNEL_INSET);
  root.style.setProperty('--channel-width', width + 'px');
  root.style.setProperty('--channel-height', height + 'px');
  root.style.setProperty('--channel-left', left + 'px');
  root.style.setProperty('--channel-top', top + 'px');
  root.style.setProperty('--channel-under-drop', UNDER_DROP + 'px');
  root.style.setProperty('--channel-under-inset', UNDER_SHRINK + 'px');

  const main = pill.getBoundingClientRect();
  root.style.setProperty('--channel-home-x', Math.round(main.left + main.width / 2 - (left + width / 2)) + 'px');
  root.style.setProperty('--channel-home-y', Math.round(main.top + main.height / 2 - (top + height / 2)) + 'px');
  return { left, top, width, height };
}


// The channel turns in the order the section empties: the two bubbles collapse and the two readings are swept away by `statsOut`, and only then is the bubble slung into the room they left. It is the Main bubble's own arrival into this panel — the same slingshot the Now bubble takes to the panel's foot — because it is the same bubble arriving in the same place.
export function openChannel() {
  if (channelLive) return;
  channelLive = true;
  clearTimeout(homeTimer);
  readGroups();
  atGroup = 0;
  statsOut();

  const rest = layOutChannel();
  paintChannel();
  channel.classList.remove('home');
  channel.classList.add('live');

  const from = pill.getBoundingClientRect();
  if (channelFlight) channelFlight.stop();
  channelFlight = slingInto(channel, from, rest, {
    ball: from.height,
    born: from.height * 0.45,
    restRadius: getComputedStyle(channel).borderTopLeftRadius,
    onOpened: () => channelUnder.classList.add('live'),
  });
  channel.style.setProperty('--channel-open-delay', channelFlight.millis + 'ms');
  channel.classList.add('opening');
  setTimeout(() => channel.classList.remove('opening'), channelFlight.total);
  bridge.triggerHaptic('expand');
  stirLiquid(channelFlight.total + 200);
}

export function closeChannel() {
  if (!channelLive) return;
  if (spreadOpen) collapseSpread();
  channelLive = false;
  if (channelFlight) channelFlight.stop();
  channelFlight = null;
  channel.classList.remove('opening');
  channelUnder.classList.remove('live', 'showing');
  channel.classList.add('home');
  bridge.triggerHaptic('dismiss');
  statsIn();
  clearTimeout(homeTimer);
  homeTimer = setTimeout(() => channel.classList.remove('live', 'home'), CHANNEL_HOME);
  stirLiquid(CHANNEL_HOME + 200);
}


// The card does not move between entries — the section is where it is — so what crosses is the reading inside it, out the way the hand pushed and in from the other side.
export function stepChannel(direction) {
  if (!channelLive || spreadOpen || groups.length < 2) return;
  clearTimeout(stepTimer);
  const away = -direction * CHANNEL_SLIDE;
  const contents = [...channel.children].filter(child => child !== channelTally);
  contents.forEach(child => child.animate(
    [{ translate: '0px 0px', opacity: 1 }, { translate: away + 'px 0px', opacity: 0 }],
    { duration: CHANNEL_STEP_OUT, easing: SLING_SHOT_EASE, fill: 'forwards' }
  ));

  stepTimer = setTimeout(() => {
    atGroup = (atGroup + direction + groups.length) % groups.length;
    paintChannel();
    [...channel.children].filter(child => child !== channelTally).forEach(child => child.animate(
      [{ translate: -away + 'px 0px', opacity: 0 }, { translate: '0px 0px', opacity: 1 }],
      { duration: CHANNEL_STEP_IN, easing: SLING_POP_EASE }
    ));
  }, CHANNEL_STEP_OUT);

  bridge.triggerHaptic('tap');
  stirLiquid(CHANNEL_STEP_OUT + CHANNEL_STEP_IN);
}


// A stack is a count until it is asked about. Tapped, the dashboard underneath goes and the entries it was standing for take the whole height, one card each, opening out of the one box they were stacked in.
export function expandSpread() {
  if (!channelLive || spreadOpen) return;
  const group = groups[atGroup];
  if (!group) return;
  spreadOpen = true;
  bridge.triggerHaptic('expand');

  const from = channel.getBoundingClientRect();
  const room = root.clientHeight - SPREAD_TOP - SPREAD_INSET;
  const count = group.entries.length;
  const height = Math.round((room - SPREAD_GAP * (count - 1)) / count);

  channelSpread.replaceChildren();
  group.entries.forEach((entry, index) => {
    const card = document.createElement('div');
    card.className = 'spread-card';
    card.style.setProperty('--spread-accent', entry.accent || '#ffffff');
    card.style.left = SPREAD_INSET + 'px';
    card.style.width = (root.clientWidth - SPREAD_INSET * 2) + 'px';
    card.style.top = (SPREAD_TOP + index * (height + SPREAD_GAP)) + 'px';
    card.style.height = height + 'px';

    const head = document.createElement('div');
    head.className = 'spread-head';
    const badge = entry.appIconBase64 || entry.iconBase64;
    if (badge) {
      const icon = document.createElement('img');
      icon.className = 'spread-icon';
      icon.alt = '';
      icon.src = badge;
      head.appendChild(icon);
    }
    const name = document.createElement('span');
    name.className = 'spread-app';
    name.textContent = entry.appName || entry.app || '';
    head.appendChild(name);
    const when = document.createElement('span');
    when.className = 'spread-when';
    when.textContent = agoText(entry.postedAt);
    head.appendChild(when);
    card.appendChild(head);

    if (entry.title) {
      const who = document.createElement('div');
      who.className = 'spread-who';
      who.textContent = entry.title;
      card.appendChild(who);
    }
    const text = document.createElement('div');
    text.className = 'spread-text';
    markUp(text, firstLine(entry));
    card.appendChild(text);

    card.addEventListener('click', event => {
      event.stopPropagation();
      if (entry.key) bridge.openNotification(entry.key);
      bridge.triggerHaptic('tap');
    });
    channelSpread.appendChild(card);

    // Every card opens out of the one box the stack was standing in, so the separation is the motion rather than a set of cards appearing at their own places.
    const to = card.getBoundingClientRect();
    card.animate(
      [
        {
          translate:
            (from.left - to.left).toFixed(1) + 'px ' + (from.top - to.top).toFixed(1) + 'px',
          width: Math.round(from.width) + 'px',
          height: Math.round(from.height) + 'px',
          opacity: index ? 0.6 : 1,
        },
        { translate: '0px 0px', width: to.width + 'px', height: to.height + 'px', opacity: 1 },
      ],
      { duration: SPREAD_MILLIS, easing: SLING_POP_EASE, delay: index * 40, fill: 'backwards' }
    );
  });

  channelSpread.classList.add('live');
  root.classList.add('spread-open');
  stirLiquid(SPREAD_MILLIS + count * 40 + 200);
}

export function collapseSpread() {
  if (!spreadOpen) return;
  spreadOpen = false;
  const to = channel.getBoundingClientRect();
  const cards = [...channelSpread.children];
  cards.forEach((card, index) => {
    const from = card.getBoundingClientRect();
    card.animate(
      [
        { translate: '0px 0px', width: from.width + 'px', height: from.height + 'px', opacity: 1 },
        {
          translate: (to.left - from.left).toFixed(1) + 'px ' + (to.top - from.top).toFixed(1) + 'px',
          width: Math.round(to.width) + 'px',
          height: Math.round(to.height) + 'px',
          opacity: index ? 0 : 1,
        },
      ],
      { duration: SPREAD_MILLIS, easing: SLING_SHOT_EASE, fill: 'forwards' }
    );
  });
  root.classList.remove('spread-open');
  root.classList.add('spread-landing');
  setTimeout(() => {
    channelSpread.classList.remove('live');
    channelSpread.replaceChildren();
    root.classList.remove('spread-landing');
  }, SPREAD_MILLIS);
  bridge.triggerHaptic('dismiss');
  stirLiquid(SPREAD_MILLIS + 200);
}


// The panel closing under it takes the channel with it: there is no section left for the card to be standing in.
export function dropChannel() {
  if (!channelLive) return;
  channelLive = false;
  spreadOpen = false;
  if (channelFlight) channelFlight.stop();
  channelFlight = null;
  clearTimeout(stepTimer);
  clearTimeout(homeTimer);
  channel.classList.remove('live', 'home', 'opening');
  channelUnder.classList.remove('live', 'showing');
  channelSpread.classList.remove('live');
  channelSpread.replaceChildren();
  root.classList.remove('spread-open', 'spread-landing');
}


export function channelHolds(x, y) {
  if (!channelLive) return false;
  const box = channel.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}


// A stack is asked about by being tapped and a single entry is opened by it — the count on the card is what says which of the two a tap is going to do.
channel.addEventListener('click', event => {
  event.stopPropagation();
  if (!channelLive || spreadOpen) return;
  const group = groups[atGroup];
  if (!group) return;
  if (group.entries.length > 1) {
    expandSpread();
    return;
  }
  if (group.entries[0].key) bridge.openNotification(group.entries[0].key);
  bridge.triggerHaptic('tap');
});


// A notification landing or leaving while the channel stands is a repaint of what it is showing, not a second arrival: the box is the section's whatever is in it.
export function refreshChannel() {
  if (!channelLive || spreadOpen) return;
  readGroups();
  paintChannel();
}
