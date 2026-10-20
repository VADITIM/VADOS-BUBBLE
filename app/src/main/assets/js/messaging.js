import { catchInto, releaseCatch, stirLiquid } from './liquid.js';
import { clockPill } from './clock.js';
import { bridge, root } from './state.js';
import { statusOpen } from './status.js';

const messagingPill = document.getElementById('messaging');
const avatar = document.getElementById('messaging-avatar');
const initial = document.getElementById('messaging-initial');

const MESSAGING_GRACE = 8;
const SCREEN_MARGIN = 10;
const BAR_CLEAR = 44;
const DOCK_REACH = 40;
const TOGGLE_DROP = 90;
const EMERGE_MILLIS = 260;
const SETTLE_MILLIS = 420;
const DOCK_MILLIS = 300;
const EMERGE_EASE = 'cubic-bezier(0.2, 0.9, 0.3, 1)';
const SETTLE_EASE = 'cubic-bezier(0.2, 1.24, 0.35, 1)';
const DOCK_EASE = 'cubic-bezier(0.5, 0, 0.3, 1)';

let conversations = [];
let isOut = false;
let isDocking = false;
let isDragging = false;
let dragFrom = null;
let restX = 0;
let restY = 0;
let journey = null;

export function hasMessaging() {
  return conversations.length > 0;
}

export function isMessagingOut() {
  return isOut;
}

function sizeOfBubble() {
  return parseFloat(getComputedStyle(root).getPropertyValue('--messaging-size')) || 46;
}

function screenWidth() {
  return root.clientWidth;
}

function screenHeight() {
  return root.clientHeight;
}

function paintLeader() {
  const leader = conversations[0];
  if (!leader) return;
  messagingPill.style.setProperty('--messaging-accent', leader.accent);
  messagingPill.classList.toggle('has-avatar', Boolean(leader.avatarBase64));
  if (leader.avatarBase64) avatar.src = leader.avatarBase64;
  initial.textContent = Array.from((leader.title || '?').trim())[0] || '?';
}

function placeRest(x, y) {
  restX = x;
  restY = y;
  messagingPill.style.setProperty('--messaging-x', x + 'px');
  messagingPill.style.setProperty('--messaging-y', y + 'px');
}

function follow(across, down) {
  messagingPill.style.translate = 'calc(-50% + ' + across + 'px) calc(-50% + ' + down + 'px)';
}

function clockCentre() {
  const box = clockPill.getBoundingClientRect();
  return { x: box.left + box.width / 2, y: box.top + box.height / 2, box };
}

function clampToScreen(x, y) {
  const half = sizeOfBubble() / 2;
  return {
    x: Math.min(screenWidth() - SCREEN_MARGIN - half, Math.max(SCREEN_MARGIN + half, x)),
    y: Math.min(screenHeight() - SCREEN_MARGIN - half, Math.max(BAR_CLEAR + half, y)),
  };
}

function isNearClock(x, y) {
  const box = clockPill.getBoundingClientRect();
  const across = Math.max(box.left - x, 0, x - box.right);
  const down = Math.max(box.top - y, 0, y - box.bottom);
  return Math.hypot(across, down) < DOCK_REACH;
}

function travel(fromX, fromY, toX, toY, duration, easing, fill) {
  return messagingPill.animate(
    { transform: ['translate(' + fromX + 'px, ' + fromY + 'px)', 'translate(' + toX + 'px, ' + toY + 'px)'] },
    { duration, easing, composite: 'add', fill }
  );
}

export function fitMessagingProxy() {
  if (isDragging) return;
  if (!isOut || isDocking || root.classList.contains('locked') || root.classList.contains('landscape')) {
    bridge.setMessagingProxy(0, 0, 0, 0);
    return;
  }
  const size = sizeOfBubble() + MESSAGING_GRACE * 2;
  bridge.setMessagingProxy(
    Math.round(size),
    Math.round(size),
    Math.round(restX - size / 2),
    Math.round(restY - size / 2)
  );
}

function emerge(x, y) {
  const from = clockCentre();
  releaseCatch(messagingPill);
  isOut = true;
  isDocking = false;
  paintLeader();
  placeRest(x, y);
  messagingPill.style.removeProperty('translate');
  messagingPill.classList.remove('docking');
  messagingPill.classList.add('showing');
  journey = travel(from.x - x, from.y - y, 0, 0, EMERGE_MILLIS, EMERGE_EASE);
  bridge.triggerHaptic('tap');
  stirLiquid(EMERGE_MILLIS + 400);
}

export function pullMessagingOut(x, y) {
  if (!hasMessaging() || isOut || statusOpen || root.classList.contains('locked')) return false;
  emerge(x, y);
  isDragging = true;
  dragFrom = { x, y };
  return true;
}

export function dragMessaging(x, y) {
  if (!isDragging) return;
  follow(x - dragFrom.x, y - dragFrom.y);
  stirLiquid(160);
}

export function releaseMessaging(x, y) {
  if (!isDragging) return;
  isDragging = false;
  messagingPill.classList.remove('pressing');
  const atX = restX + x - dragFrom.x;
  const atY = restY + y - dragFrom.y;
  dragFrom = null;
  placeRest(atX, atY);
  messagingPill.style.removeProperty('translate');
  if (isNearClock(atX, atY)) {
    dockMessaging();
    return;
  }
  const resting = clampToScreen(atX, atY);
  if (resting.x !== atX || resting.y !== atY) {
    placeRest(resting.x, resting.y);
    journey = travel(atX - resting.x, atY - resting.y, 0, 0, SETTLE_MILLIS, SETTLE_EASE);
  }
  stirLiquid(SETTLE_MILLIS + 200);
  fitMessagingProxy();
}

function dockMessaging() {
  if (!isOut || isDocking) return;
  const centre = clockCentre();
  isDocking = true;
  fitMessagingProxy();
  messagingPill.classList.add('docking');
  journey = travel(0, 0, centre.x - restX, centre.y - restY, DOCK_MILLIS, DOCK_EASE, 'forwards');
  catchInto(messagingPill, clockPill, { swallowed: finishDock });
  stirLiquid(DOCK_MILLIS + 400);
}

function finishDock() {
  messagingPill.getAnimations().forEach(animation => animation.cancel());
  journey = null;
  messagingPill.classList.remove('showing', 'docking', 'pressing');
  isOut = false;
  isDocking = false;
  bridge.triggerHaptic('tap');
  fitMessagingProxy();
}

export function messagingTouch(action, x, y) {
  if (action === 'down') {
    if (!isOut || isDocking) return;
    isDragging = true;
    dragFrom = { x, y };
    messagingPill.classList.add('pressing');
    return;
  }
  if (action === 'move') {
    dragMessaging(x, y);
    return;
  }
  releaseMessaging(x, y);
}

window.onToggleMessaging = () => {
  if (isOut) {
    dockMessaging();
    return;
  }
  if (!hasMessaging() || root.classList.contains('locked') || root.classList.contains('landscape')) return;
  const centre = clockCentre();
  const resting = clampToScreen(centre.x, centre.box.bottom + TOGGLE_DROP);
  emerge(resting.x, resting.y);
  stirLiquid(EMERGE_MILLIS + 400);
  fitMessagingProxy();
};

window.onMessaging = payload => {
  conversations = payload || [];
  console.log('[messaging] ' + conversations.length + ' bubbled: ' + conversations.map(conversation =>
    conversation.title + ' (' + conversation.messages.length + ' kept, ' + conversation.unread + ' unread' +
    (conversation.isLive ? '' : ', notification gone') + ')'
  ).join(', '));
  if (!hasMessaging()) {
    if (isOut) dockMessaging();
    return;
  }
  paintLeader();
};
