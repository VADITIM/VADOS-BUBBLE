import { setSize, showFace, toClosed } from '../row.js';
import { PICTURE_MAX_HEIGHT, bridge, pill, shared } from '../state.js';

/**
 * The words worth picking out of a message at a glance: what kind of thing it is,
 * rather than what it says. A notification is read in the half-second it is on
 * screen, and "Reel" or "Voice message" tells you whether it is worth opening
 * before any of the words around it do.
 *
 * Deliberately short and deliberately nouns. Every word added here is a word that
 * lights up in someone's actual message the moment they happen to type it, so a
 * common verb belongs nowhere near this list.
 */
const KEYWORDS = [
  'reel', 'reels', 'story', 'stories', 'post', 'live',
  'photo', 'video', 'gif', 'sticker', 'voice message', 'audio',
  'call', 'missed call', 'attachment', 'document', 'location',
  'poll', 'event', 'invite', 'mention', 'reply', 'reaction',
  'foto', 'sprachnachricht', 'anruf', 'umfrage', 'einladung',
];

const LINK_PATTERN = /\bhttps?:\/\/\S+|\bwww\.\S+/gi;

/**
 * When a thing happens, in the formats this phone actually writes them in — it is set to
 * German, so `27.08.2026` and `27.08.` are dates and the comma is a decimal point, which is
 * exactly why this pattern and the money one below have to be told apart carefully rather
 * than both hunting for "a number with punctuation in it".
 *
 * Deliberately only the concrete tokens. "heute" and "morgen" are time words too, but they
 * turn up in ordinary sentences several times a message and a highlighter drawn over every
 * one of them is a highlighter that has stopped meaning anything.
 */
const TIME_PATTERN = new RegExp(
  [
    '\\d{1,2}:\\d{2}(?::\\d{2})?',
    '\\d{1,2}\\.\\d{1,2}\\.(?:\\d{2,4})?',
    '\\d{4}-\\d{2}-\\d{2}',
    '\\d{1,2}\\.?\\s?(?:Jan|Feb|Mär|Mar|Apr|Mai|May|Jun|Jul|Aug|Sep|Okt|Oct|Nov|Dez|Dec)[a-zä]*\\.?',
  ].join('|'),
  'gi'
);

/**
 * An amount of money, symbol on either side because both are written: `7,80€` from a German
 * app and `$5.00` from an American one. The symbol has to be part of the match rather than a
 * lookaround — a bare number is not money and highlighting one would light up every message
 * containing a quantity.
 *
 * The `\b` closes the letter codes only, never the symbols. Written outside the whole
 * alternation it silently killed the commonest case on this phone: a word boundary needs a
 * word character on one side, `€` is not one, and `7,80€ empfangen` has a space after the
 * symbol — so `7,80€` failed to match while `45 CHF` passed.
 */
const MONEY_PATTERN =
  /[€$£¥]\s?\d[\d.,]*|\d[\d.,]*\s?(?:[€$£¥]|(?:EUR|USD|GBP|CHF)\b)/gi;

/**
 * A message, with the parts of it that are not prose marked as such: links wear
 * the blue and the underline every link everywhere wears, and the handful of words
 * that say what kind of message this is take the app's own colour.
 *
 * Written by walking text nodes and replacing them, never by assigning innerHTML:
 * this string came from another app and is entirely under its control. A message
 * containing a tag would otherwise be a message that can draw in this window.
 */
export function markUp(host, message) {
  host.textContent = message;
  if (!message) return;
  paintMatches(host, LINK_PATTERN, 'link');
  // Before the keywords, because these two are the specific ones: a date is a date whatever
  // words are around it, while "Termin" is a guess about what the message is. Each pass only
  // ever sees text no earlier pass has claimed — see paintMatches.
  paintMatches(host, TIME_PATTERN, 'time');
  paintMatches(host, MONEY_PATTERN, 'money');
  // One pass over the whole list rather than one per word, so the earliest match in
  // the message wins rather than the earliest word in the list.
  const words = KEYWORDS
    .map(word => word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    .join('|');
  paintMatches(host, new RegExp('\\b(?:' + words + ')\\b', 'gi'), 'keyword');
}

function paintMatches(host, pattern, className) {
  // Collected first: replacing a node while walking the live list skips whatever
  // came after it.
  const texts = [];
  // Text already claimed by an earlier pass is skipped outright, because each pass walks the
  // tree the previous ones left behind rather than the original string. Without this a link
  // is fair game for every pattern that follows it: `https://x.com/post/12.05.2026` came out
  // of the link pass as one span and then had "post" recoloured and the date highlighted
  // inside it — a URL wearing three different marks, none of which meant anything there.
  const walker = document.createTreeWalker(host, NodeFilter.SHOW_TEXT, {
    acceptNode: (node) => node.parentNode === host
      ? NodeFilter.FILTER_ACCEPT
      : NodeFilter.FILTER_REJECT,
  });
  while (walker.nextNode()) texts.push(walker.currentNode);

  for (const node of texts) {
    const value = node.nodeValue;
    pattern.lastIndex = 0;
    if (!pattern.test(value)) continue;
    pattern.lastIndex = 0;
    const pieces = document.createDocumentFragment();
    let at = 0;
    let found;
    while ((found = pattern.exec(value)) !== null) {
      if (found.index > at) {
        pieces.appendChild(document.createTextNode(value.slice(at, found.index)));
      }
      const mark = document.createElement('span');
      mark.className = className;
      mark.textContent = found[0];
      pieces.appendChild(mark);
      at = found.index + found[0].length;
    }
    if (at < value.length) pieces.appendChild(document.createTextNode(value.slice(at)));
    node.parentNode.replaceChild(pieces, node);
  }
}

const stack = document.getElementById('text');

/** How long an arriving line takes to push the stack up and fade itself in. */
const ARRIVE_MILLIS = 300;

/** Mirrors --ease-grow in pill.css; WAAPI cannot be handed a custom property. */
const EASE_GROW = 'cubic-bezier(0.22, 1.12, 0.36, 1)';

/** Mirrors the padding-top and margin-top on `.message + .message` in pill.css. */
const LINE_GAP = '0.34rem';

/**
 * Every message this notification is currently carrying, oldest first.
 *
 * `lines` is the whole conversation and `text` is only the last of it — a messenger
 * posts one notification per conversation and rewrites it as each message lands,
 * which is the same reading the history list already takes. An app that posts one
 * notification per message has no `lines` and its single `text` is the whole of it.
 */
function linesOf(notification) {
  const lines = (notification.lines || []).map(line => line.text).filter(Boolean);
  return lines.length ? lines : [notification.text || ''];
}

/**
 * Whether this is the alert on screen having another thing said into it, rather than
 * something new arriving.
 *
 * The key is the real test: a messenger rewriting its conversation keeps it, which is
 * exactly why `lines` grows. The sender is the fallback for an app that posts a fresh
 * notification per message, where the key changes every time and the person does not.
 */
function isSameConversation(shown, next) {
  if (!shown) return false;
  if (shown.key === next.key) return true;
  return Boolean(next.title) &&
    shown.title === next.title &&
    shown.package === next.package;
}

/**
 * One line of the conversation, arriving from below.
 *
 * Its own height is what is animated, from nothing to whatever the words wrapped to,
 * and that is deliberately the same animation as the push: the stack is anchored to
 * its bottom edge, so a line growing at the end of it moves everything above it up and
 * off the top. Animated separately they read as a fade happening next to a jump.
 */
function addLine(text, arriving) {
  const line = document.createElement('div');
  line.className = 'message';
  // What is already drawn, so the next payload can be compared against it without a
  // second copy of the conversation being kept in the page.
  line.dataset.line = text;
  markUp(line, text);
  stack.appendChild(line);
  if (!arriving) return;
  // The margin travels with the height because it is the one part of the spacing that
  // sits outside the border box: left alone it lands at full size on the first frame
  // and the push starts with a jump the height animation then has to catch up with.
  line.animate(
    [
      { height: '0px', marginTop: '0px', opacity: 0, translate: '0 0.5rem' },
      { height: line.offsetHeight + 'px', marginTop: LINE_GAP, opacity: 1, translate: '0 0' },
    ],
    { duration: ARRIVE_MILLIS, easing: EASE_GROW }
  );
}

/** How many of the conversation's messages the stack has already been shown. */
let seenCount = 0;

/**
 * What has been said since the stack was last drawn.
 *
 * `lines` grows as the conversation does, so what is new is whatever comes after the
 * count already taken — a count rather than a search through the run, because someone
 * sending "ok" twice has said two things and looking the last one up finds the wrong
 * one of them. An app that posts a fresh notification per message has no `lines` at all
 * and hands over the same one-element run every time, so there the test is simply that
 * it says something other than what is already at the bottom of the stack. The same
 * test covers a messenger that trims its oldest lines away as the run gets long.
 */
function arrivalsIn(next, last) {
  if (next.length > seenCount) return next.slice(seenCount);
  const newest = next[next.length - 1];
  return newest && newest !== last ? [newest] : [];
}

/**
 * The stack, brought up to what the notification now says.
 *
 * A fresh alert is **one message**, whatever the conversation behind it is holding, and
 * is drawn exactly as an alert has always been drawn. The run exists only to tell what
 * has been added while the alert is standing there — drawn in full it would put a
 * backlog on screen and stack an alert nobody watched arrive, which is the one thing
 * stacking is not for.
 */
function paintStack(notification, appending) {
  const drawn = [...stack.children];
  const last = drawn.length ? drawn[drawn.length - 1].dataset.line : undefined;
  const next = linesOf(notification);
  const arrivals = appending ? arrivalsIn(next, last) : [];
  seenCount = next.length;

  if (appending) {
    arrivals.forEach(line => addLine(line, true));
    return;
  }
  stack.replaceChildren();
  addLine(next[next.length - 1] || '', false);
}

/**
 * Who the alert is from, which for a message in a server channel is not what the notification
 * calls itself.
 *
 * A channel message titles itself after the *place* — `#general (Some Server)` — and the person
 * who actually wrote is only in the message's own sender field. The place is worth one word of
 * it and the channel is worth none: what a glance wants is who said something and where, so the
 * head reads `Sender · Server`. Anything without a sender of its own is a notification titled
 * after whoever sent it already, and it is left exactly as it was.
 */
function whoSent(notification) {
  const title = notification.title || '';
  const lines = notification.lines || [];
  const sender = lines.length ? lines[lines.length - 1].sender : '';
  if (!sender || sender === title) return title;
  // Whatever is left of the title once the channel is taken out of it: in brackets when the app
  // puts it there, and otherwise the title with its leading `#channel` removed.
  const bracketed = title.match(/\(([^)]+)\)\s*$/);
  const place = (bracketed ? bracketed[1] : title.replace(/#\S+/, '')).replace(/[\s\-—•·]+$/, '').trim();
  return place && place !== sender ? sender + ' · ' + place : sender;
}

export function show(notification) {
  clearTimeout(shared.dwellTimer);
  // Whatever was taken away is being replaced rather than ended — see onNotificationGone.
  clearTimeout(goneTimer);

  const picture = document.getElementById('picture');
  const hasImage = Boolean(notification.imageBase64);
  // A photo is not another line in a conversation — the image alert is a different
  // face at a different height and there is nowhere in it for the stack to grow. So a
  // picture always arrives as its own alert, and the next words after it do too.
  const appending = shared.state === 'alert' &&
    !hasImage &&
    !pill.classList.contains('with-image') &&
    isSameConversation(shared.current, notification);

  shared.state = 'alert';
  shared.current = notification;

  document.getElementById('app-name').textContent = notification.appName || '';
  document.getElementById('title').textContent = whoSent(notification);
  paintStack(notification, appending);
  document.documentElement.style.setProperty(
    '--app-accent', notification.accent || '#ffffff'
  );

  const icon = document.getElementById('icon');
  icon.classList.toggle('present', Boolean(notification.iconBase64));
  if (notification.iconBase64) icon.src = notification.iconBase64;

  pill.classList.toggle('with-image', hasImage);
  if (hasImage) picture.src = notification.imageBase64;

  showFace('alert');
  pill.classList.add('alert');
  setSize(hasImage ? 'image' : 'alert');
  // Still a thing that arrived, so it is still felt — what an appended line does not do
  // is play the bubble's arrival a second time. The alert is already open and standing
  // where it stands; re-announcing it is what made reading a conversation impossible.
  bridge.triggerHaptic('notification');

  // The dwell starts again, so a conversation being had is a bubble that stays up.
  shared.dwellTimer = setTimeout(toClosed, shared.dwell);
}

/**
 * Taken back. A notification killer works by cancelling what it does not like, and
 * it can be a fraction of a second slower than the announcement is — so whatever
 * the bubble is showing stops being true the moment the shade stops holding it.
 * An open panel is the user's own doing and is left alone.
 */
window.onNotificationGone = key => {
  if (shared.state !== 'alert' || !shared.current || shared.current.key !== key) return;
  // Not on the spot, because an app that posts a fresh notification per message takes the old one away and puts the new one up in the same breath — and the announcement of the new one is held back by KILL_GRACE while the removal of the old one is not. Closed immediately, every real messenger's second message read as the alert being taken away and a brand new one arriving: the bubble shut, played its whole arrival again, and the conversation could not be read. Waited out, the alert is still standing and still holding the old notification when the replacement lands, so show() recognises the conversation and appends to it. It is longer than KILL_GRACE in IslandNotificationListener.kt deliberately — a grace shorter than the delay it is waiting for is no grace at all.
  clearTimeout(goneTimer);
  goneTimer = setTimeout(toClosed, GONE_GRACE);
};

/** Mirrors KILL_GRACE in IslandNotificationListener.kt, which it has to outlast. */
const GONE_GRACE = 900;
let goneTimer = null;

/** The picture opens to its own shape, never to a fixed box that crops it. */
export function openPicture() {
  const picture = document.getElementById('picture');
  const ratio = picture.naturalHeight / (picture.naturalWidth || 1);
  const height = Math.min(PICTURE_MAX_HEIGHT, Math.round(340 * ratio) + 64);
  document.documentElement.style.setProperty('--picture-height', height + 'px');
  setSize('picture', { width: 340, height });
}

export function openCurrent() {
  if (shared.current && shared.current.key) bridge.openNotification(shared.current.key);
  bridge.triggerHaptic('tap');
  toClosed();
}
