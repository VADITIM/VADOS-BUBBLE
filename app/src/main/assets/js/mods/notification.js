import { setSize, showFace, toClosed } from '../row.js';
import { DWELL, PICTURE_MAX_HEIGHT, bridge, pill, shared } from '../state.js';

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

export function show(notification) {
  clearTimeout(shared.dwellTimer);
  shared.state = 'alert';
  shared.current = notification;

  document.getElementById('app-name').textContent = notification.appName || '';
  document.getElementById('title').textContent = notification.title || '';
  markUp(document.getElementById('text'), notification.text || '');
  document.documentElement.style.setProperty(
    '--app-accent', notification.accent || '#ffffff'
  );

  const icon = document.getElementById('icon');
  icon.classList.toggle('present', Boolean(notification.iconBase64));
  if (notification.iconBase64) icon.src = notification.iconBase64;

  const picture = document.getElementById('picture');
  const hasImage = Boolean(notification.imageBase64);
  pill.classList.toggle('with-image', hasImage);
  if (hasImage) picture.src = notification.imageBase64;

  showFace('alert');
  pill.classList.add('alert');
  setSize(hasImage ? 'image' : 'alert');
  bridge.triggerHaptic('notification');

  shared.dwellTimer = setTimeout(toClosed, DWELL);
}

/**
 * Taken back. A notification killer works by cancelling what it does not like, and
 * it can be a fraction of a second slower than the announcement is — so whatever
 * the bubble is showing stops being true the moment the shade stops holding it.
 * An open panel is the user's own doing and is left alone.
 */
window.onNotificationGone = key => {
  if (shared.state === 'alert' && shared.current && shared.current.key === key) toClosed();
};

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
