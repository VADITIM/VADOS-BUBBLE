import { isAlertCentred, recentreAlert } from '../alert.js';
import { setSize, showFace, toClosed } from '../row.js';
import { PICTURE_MAX_HEIGHT, bridge, pill, shared } from '../state.js';











const KEYWORDS = [
  'reel', 'reels', 'story', 'stories', 'post', 'live',
  'photo', 'video', 'gif', 'sticker', 'voice message', 'audio',
  'call', 'missed call', 'attachment', 'document', 'location',
  'poll', 'event', 'invite', 'mention', 'reply', 'reaction',
  'foto', 'sprachnachricht', 'anruf', 'umfrage', 'einladung',
];

const LINK_PATTERN = /\bhttps?:\/\/\S+|\bwww\.\S+/gi;











const TIME_PATTERN = new RegExp(
  [
    '\\d{1,2}:\\d{2}(?::\\d{2})?',
    '\\d{1,2}\\.\\d{1,2}\\.(?:\\d{2,4})?',
    '\\d{4}-\\d{2}-\\d{2}',
    '\\d{1,2}\\.?\\s?(?:Jan|Feb|Mär|Mar|Apr|Mai|May|Jun|Jul|Aug|Sep|Okt|Oct|Nov|Dez|Dec)[a-zä]*\\.?',
  ].join('|'),
  'gi'
);












const MONEY_PATTERN =
  /[€$£¥]\s?\d[\d.,]*|\d[\d.,]*\s?(?:[€$£¥]|(?:EUR|USD|GBP|CHF)\b)/gi;










export function markUp(host, message) {
  host.textContent = message;
  if (!message) return;
  paintMatches(host, LINK_PATTERN, 'link');
  
  
  
  paintMatches(host, TIME_PATTERN, 'time');
  paintMatches(host, MONEY_PATTERN, 'money');
  
  
  const words = KEYWORDS
    .map(word => word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
    .join('|');
  paintMatches(host, new RegExp('\\b(?:' + words + ')\\b', 'gi'), 'keyword');
}

function paintMatches(host, pattern, className) {
  
  
  const texts = [];
  
  
  
  
  
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


const ARRIVE_MILLIS = 300;


const EASE_GROW = 'cubic-bezier(0.22, 1.12, 0.36, 1)';


const LINE_GAP = '0.34rem';









function linesOf(notification) {
  const lines = (notification.lines || []).map(line => line.text).filter(Boolean);
  return lines.length ? lines : [notification.text || ''];
}









function isSameConversation(shown, next) {
  if (!shown) return false;
  if (shown.key === next.key) return true;
  return Boolean(next.title) &&
    shown.title === next.title &&
    shown.package === next.package;
}









function addLine(text, arriving) {
  const line = document.createElement('div');
  line.className = 'message';
  
  
  line.dataset.line = text;
  markUp(line, text);
  stack.appendChild(line);
  if (!arriving) return;
  
  
  
  line.animate(
    [
      { height: '0px', marginTop: '0px', opacity: 0, translate: '0 0.5rem' },
      { height: line.offsetHeight + 'px', marginTop: LINE_GAP, opacity: 1, translate: '0 0' },
    ],
    { duration: ARRIVE_MILLIS, easing: EASE_GROW }
  );
}


let seenCount = 0;












function arrivalsIn(next, last) {
  if (next.length > seenCount) return next.slice(seenCount);
  const newest = next[next.length - 1];
  return newest && newest !== last ? [newest] : [];
}










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











function whoSent(notification) {
  const title = notification.title || '';
  const lines = notification.lines || [];
  const sender = lines.length ? lines[lines.length - 1].sender : '';
  if (!sender || sender === title) return title;
  
  
  const bracketed = title.match(/\(([^)]+)\)\s*$/);
  const place = (bracketed ? bracketed[1] : title.replace(/#\S+/, '')).replace(/[\s\-—•·]+$/, '').trim();
  return place && place !== sender ? sender + ' · ' + place : sender;
}

export function show(notification) {
  clearTimeout(shared.dwellTimer);
  
  clearTimeout(goneTimer);

  const picture = document.getElementById('picture');
  const hasImage = Boolean(notification.imageBase64);
  
  
  
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
  // A second message arriving while the Alert is being read at the centre of the screen must not put it back on the bar: it grows where it stands and it keeps standing there.
  if (isAlertCentred()) recentreAlert();
  else setSize(hasImage ? 'image' : 'alert');
  
  
  
  bridge.triggerHaptic('notification');
  // The gesture that answers an Alert is made anywhere across the top band rather than on the bubble alone, so the band is asked for while the Alert stands and given straight back at the close.
  bridge.setAlertOverlay(-1);

  
  if (!isAlertCentred()) shared.dwellTimer = setTimeout(toClosed, shared.dwell);
}







window.onNotificationGone = key => {
  if (shared.state !== 'alert' || !shared.current || shared.current.key !== key) return;
  
  clearTimeout(goneTimer);
  if (isAlertCentred()) return;
  goneTimer = setTimeout(toClosed, GONE_GRACE);
};


const GONE_GRACE = 900;
let goneTimer = null;


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
