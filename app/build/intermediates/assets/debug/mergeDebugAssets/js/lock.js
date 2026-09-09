import { catchInto, releaseCatch, resendBlur, stirLiquid } from './liquid.js';
import { clock, flipPlaying, paintProgress, playPath, livePosition, shownPosition, songSwapping, typeLabels } from './mods/media.js';
import { cancelSpring, HOLD_BLOCK, rubberBandPast, toy, untoy } from './motion.js';
import { fitClockProxy } from './clock.js';
import { fitNotesProxy, paintNotes } from './notes.js';
import { paintPadlock } from './padlock.js';
import { fitStatusProxy } from './status.js';
import { ensureClosedWindow, isLive, paintSatellites, toClosed } from './row.js';
import { HOLD_MILLIS, bridge, pill, root, shared } from './state.js';
import { leaveForMod } from './tabs.js';


let lockShift = 0;


window.setLockShift = lockX => {
  lockShift = Number(lockX) || 0;
  root.style.setProperty('--lock-x', lockShift + 'px');
  fitLockProxy();
  fitNotesProxy();
};

const LOCK_HEIGHT = 124;

const LOCK_OPEN_HEIGHT = 546;


const LOCK_SWIPE = 24;


const LOCK_FLY = 640;


const LOCK_PINCH_SCALE = 0.55;


const LOCK_PINCH = 260;


const LOCK_CRAWL = 0.25;


const LOCK_PINCH_EASE = 'cubic-bezier(0.62, 0, 0.2, 1.65)';


const LOCK_FLY_WAIT = 120;


const LOCK_FLY_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';


const LOCK_GROW = 340;
const LOCK_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';

export const lockPill = document.getElementById('lock-now');
export const lockArt = document.getElementById('lock-art');
const lockElapsed = document.getElementById('lock-elapsed');






const lockMovers = [
  { element: document.getElementById('lock-art-slot'), resizes: true },
  { element: document.getElementById('lock-meta'), resizes: false },
  { element: document.getElementById('lock-buttons'), resizes: false },
  { element: document.getElementById('lock-timeline'), resizes: false },
];


let locked = false;











const LOCK_STEALS = ['media'];









let stealHeld = false;

export function isStolen(mod) {
  return (locked || stealHeld) && LOCK_STEALS.includes(mod);
}


function lockMod() {
  if (!locked && !stealHeld) return undefined;
  return shared.modOrder.filter(mod => LOCK_STEALS.includes(mod) && isLive(mod))[0];
}


let lockOpen = false;


let lockFlying = false;


let lockFlight = null;

export function paintLock() {
  
  
  
  if (lockFlying) return;
  const mod = lockMod();
  const showing = Boolean(mod);
  if (lockPill.classList.contains('showing') !== showing) stirLiquid(500);
  lockPill.classList.toggle('showing', showing);
  
  
  root.classList.toggle('lock-live', showing);
  if (!showing && lockOpen) closeLock();
  fitLockProxy();
  if (!showing) return;
  lockPill.style.setProperty('--lock-accent', shared.media.accent || 'var(--section-color)');
  
  
  
  
  if (!songSwapping) typeLabels();
  document.getElementById('lock-duration').textContent = clock(shared.media.duration || 0);
  document.getElementById('lock-play-path').setAttribute('d', playPath());
  paintLockProgress(livePosition());
}

















function settleLock(change) {
  const from = lockPill.getBoundingClientRect().height;
  const before = lockMovers.map(mover => mover.element.getBoundingClientRect());
  change();
  
  
  
  
  
  const to = lockOpen ? LOCK_OPEN_HEIGHT : LOCK_HEIGHT;
  const growth = lockPill.animate(
    [{ height: from + 'px' }, { height: to + 'px' }],
    { duration: LOCK_GROW, easing: LOCK_EASE }
  );
  
  
  
  
  growth.addEventListener('finish', fitLockProxy);
  lockMovers.forEach((mover, index) => {
    const from = before[index];
    const to = mover.element.getBoundingClientRect();
    if (!from.width || !to.width) return;
    const scale = mover.resizes ? from.width / to.width : 1;
    
    
    const dx = (from.left + from.width / 2) - (to.left + to.width / 2);
    const dy = (from.top + from.height / 2) - (to.top + to.height / 2);
    mover.element.style.transition = 'none';
    mover.element.style.transform =
      'translate(' + dx.toFixed(1) + 'px,' + dy.toFixed(1) + 'px) scale(' + scale.toFixed(3) + ')';
  });
  
  
  requestAnimationFrame(() => {
    lockMovers.forEach(mover => {
      mover.element.style.transition = 'transform var(--grow-ms) var(--ease-split)';
      mover.element.style.transform = '';
    });
  });
  stirLiquid(900);
}








function openLock() {
  if (lockOpen) return;
  lockOpen = true;
  settleLock(() => lockPill.classList.add('open'));
  fitLockProxy();
  
  
  paintLockProgress(livePosition());
  stirLiquid(600);
}

function closeLock() {
  if (!lockOpen) return;
  lockOpen = false;
  settleLock(() => lockPill.classList.remove('open'));
  fitLockProxy();
  stirLiquid(600);
}


export function paintLockProgress(position) {
  const duration = (shared.media && shared.media.duration) || 0;
  const ratio = duration > 0 ? Math.min(1, position / duration) : 0;
  lockElapsed.style.width = (ratio * 100) + '%';
  document.getElementById('lock-position').textContent = clock(position);
}










let shownProxy = '';

function fitLockProxy() {
  const hidden = lockFlying || !lockPill.classList.contains('showing');
  const box = hidden ? null : lockPill.getBoundingClientRect();
  const proxy = hidden
    ? [0, 0, 0, 0]
    : [Math.round(box.width), Math.round(box.height), Math.round(box.left), Math.round(box.top)];
  
  
  
  if (proxy.join() === shownProxy) return;
  shownProxy = proxy.join();
  bridge.setLockProxy(proxy[0], proxy[1], proxy[2], proxy[3]);
}










export function lockHolds(x, y) {
  if (lockFlying || !lockPill.classList.contains('showing')) return false;
  const box = lockPill.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}






window.refitProxies = () => {
  fitLockProxy();
  fitStatusProxy();
  fitClockProxy();
  fitNotesProxy();
};











function flyLockHome() {
  
  
  fitLockProxy();
  
  
  
  lockPill.classList.add('flying');
  setTimeout(startLockFlight, LOCK_FLY_WAIT);
}

function startLockFlight() {
  if (!lockFlying) return;
  const from = lockPill.getBoundingClientRect();
  const to = pill.getBoundingClientRect();
  
  
  const dx = (to.left + to.width / 2) - (from.left + from.width / 2);
  const dy = (to.top + to.height / 2) - (from.top + from.height / 2);
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  
  const drop = Math.min(to.width / from.width, to.height / from.height) * 0.9;
  const whole = LOCK_PINCH + LOCK_FLY;
  const turn = LOCK_PINCH / whole;
  
  
  
  
  
  
  
  const crawled = LOCK_CRAWL * turn;
  lockFlight = [
    lockPill.animate(
      [
        { scale: 1, offset: 0, easing: LOCK_PINCH_EASE },
        { scale: LOCK_PINCH_SCALE, offset: turn, easing: LOCK_FLY_EASE },
        { scale: drop, offset: 1 },
      ],
      { duration: whole, fill: 'forwards' }
    ),
    lockPill.animate(
      [
        { translate: '0px 0px', offset: 0, easing: 'linear' },
        {
          translate: (dx * crawled).toFixed(1) + 'px ' + (dy * crawled).toFixed(1) + 'px',
          offset: turn,
          easing: LOCK_FLY_EASE,
        },
        { translate: dx.toFixed(1) + 'px ' + dy.toFixed(1) + 'px', offset: 1 },
      ],
      { duration: whole, fill: 'forwards' }
    ),
  ];
  
  
  
  
  
  stirLiquid(LOCK_PINCH + LOCK_FLY + 400);
  
  
  setTimeout(beginLockCatch, LOCK_PINCH);
}

function beginLockCatch() {
  if (!lockFlying) return;
  
  
  
  
  
  catchInto(lockPill, pill);
  watchLockCentre();
  stirLiquid(LOCK_FLY + 400);
}


const LOCK_BUMP = 12;
const LOCK_BUMP_MS = 420;






function watchLockCentre() {
  if (!lockFlying) return;
  const traveller = lockPill.getBoundingClientRect();
  const receiver = pill.getBoundingClientRect();
  if (traveller.top + traveller.height / 2 > receiver.top + receiver.height / 2) {
    requestAnimationFrame(watchLockCentre);
    return;
  }
  releaseCatch(lockPill);
  takeLockBack();
  bumpPill();
  landLock();
}






function takeLockBack() {
  stealHeld = false;
  if (shared.state === 'idle') toClosed();
  else {
    paintSatellites();
    ensureClosedWindow();
  }
}






export function bumpPill() {
  pill.animate(
    [
      { transform: 'translateY(0px)' },
      { transform: 'translateY(-' + LOCK_BUMP + 'px)', offset: 0.35 },
      { transform: 'translateY(0px)' },
    ],
    { duration: LOCK_BUMP_MS, easing: 'cubic-bezier(0.2, 1.6, 0.35, 1)', composite: 'add' }
  );
  stirLiquid(LOCK_BUMP_MS + 200);
}

function landLock() {
  if (!lockFlying) return;
  lockFlying = false;
  lockOpen = false;
  stealHeld = false;
  
  
  
  
  
  lockPill.style.setProperty('--lock-fade-ms', '0ms');
  lockPill.classList.remove('showing', 'open', 'pressed');
  cancelSpring(lockPill);
  
  
  root.classList.remove('lock-live');
  fitLockProxy();
  
  
  requestAnimationFrame(() => {
    if (lockFlight) lockFlight.forEach(move => move.cancel());
    lockFlight = null;
    lockPill.classList.remove('flying');
    lockPill.style.removeProperty('--lock-fade-ms');
  });
}

window.onLock = next => {
  
  
  
  
  resendBlur();
  if (locked === next) return;
  
  
  
  
  
  root.classList.toggle('locked', next);
  paintPadlock(next);
  paintNotes(next);
  
  
  
  
  
  
  
  const flying = !next && Boolean(lockMod());
  locked = next;
  lockFlying = flying;
  
  
  stealHeld = flying;
  if (flying) {
    flyLockHome();
    return;
  }
  
  
  
  if (shared.state === 'idle') toClosed();
  else {
    paintSatellites();
    ensureClosedWindow();
  }
  paintLock();
};








let lockStart = null;
let lockHoldTimer = null;
let lockHeld = false;


let lockSwiped = false;

lockPill.addEventListener('touchstart', event => {
  lockStart = { x: event.touches[0].clientX, y: event.touches[0].clientY };
  lockHeld = false;
  lockSwiped = false;
  lockPill.classList.add('pressed');
  
  
  
  
  
  stirLiquid(420);
  
  
  
  clearTimeout(lockHoldTimer);
  lockHoldTimer = setTimeout(() => {
    lockHeld = true;
    lockPill.classList.remove('pressed');
    untoy(lockPill, '--lock-drag');
    bridge.triggerHaptic('expand');
    leaveForMod('media');
  }, HOLD_MILLIS);
}, { passive: true });

lockPill.addEventListener('touchmove', event => {
  if (!lockStart || lockHeld) return;
  const dx = event.touches[0].clientX - lockStart.x;
  const dy = event.touches[0].clientY - lockStart.y;
  if (Math.hypot(dx, dy) <= HOLD_BLOCK) return;
  
  
  
  
  if (!lockSwiped && Math.abs(dy) > LOCK_SWIPE && Math.abs(dy) > Math.abs(dx)) {
    lockSwiped = true;
    clearTimeout(lockHoldTimer);
    lockPill.classList.remove('pressed');
    bridge.triggerHaptic('tap');
    untoy(lockPill, '--lock-drag');
    if (dy < 0) openLock();
    else closeLock();
    return;
  }
  if (lockSwiped) return;
  
  
  
  if (rubberBandPast(dx, dy)) {
    clearTimeout(lockHoldTimer);
    lockPill.classList.remove('pressed');
  }
  toy(lockPill, '--lock-drag', dx, dy);
}, { passive: true });

function releaseLock() {
  clearTimeout(lockHoldTimer);
  lockPill.classList.remove('pressed');
  lockStart = null;
  untoy(lockPill, '--lock-drag');
}

lockPill.addEventListener('touchend', releaseLock, { passive: true });
lockPill.addEventListener('touchcancel', releaseLock, { passive: true });







lockPill.addEventListener('click', () => {
  if (lockHeld || lockSwiped) return;
  bridge.triggerHaptic('tap');
  if (lockOpen) closeLock();
  else openLock();
});







function knock(element) {
  element.classList.add('knocked');
  
  
  
  clearTimeout(knocking.get(element));
  knocking.set(element, setTimeout(() => element.classList.remove('knocked'), KNOCK_MILLIS));
}

const KNOCK_MILLIS = 90;
const knocking = new WeakMap();











function ownsTouch(element) {
  for (const type of ['touchstart', 'touchmove', 'touchend', 'touchcancel', 'click']) {
    element.addEventListener(type, event => event.stopPropagation(), { passive: true });
  }
}





document.querySelectorAll('#lock-buttons .transport').forEach(ownsTouch);
ownsTouch(document.getElementById('lock-timeline'));



document.querySelectorAll('#player-buttons .transport').forEach(ownsTouch);











const lockTrack = document.getElementById('lock-track');
let lockScrubOrigin = 0;
let lockScrubStart = 0;

document.getElementById('lock-timeline').addEventListener('touchstart', event => {
  if (!shared.media || !shared.media.duration) return;
  shared.isScrubbing = true;
  lockScrubOrigin = event.touches[0].clientX;
  lockScrubStart = shownPosition;
  shared.scrubPosition = shownPosition;
}, { passive: true });

document.getElementById('lock-timeline').addEventListener('touchmove', event => {
  if (!shared.isScrubbing) return;
  const box = lockTrack.getBoundingClientRect();
  const travelled =
    ((event.touches[0].clientX - lockScrubOrigin) / box.width) * shared.media.duration;
  shared.scrubPosition = Math.min(
    shared.media.duration, Math.max(0, Math.round(lockScrubStart + travelled))
  );
  paintLockProgress(shared.scrubPosition);
}, { passive: true });

function releaseLockScrub() {
  if (!shared.isScrubbing) return;
  shared.isScrubbing = false;
  bridge.triggerHaptic('tap');
  bridge.mediaSeek(String(shared.scrubPosition));
}
document.getElementById('lock-timeline')
  .addEventListener('touchend', releaseLockScrub, { passive: true });
document.getElementById('lock-timeline')
  .addEventListener('touchcancel', () => { shared.isScrubbing = false; }, { passive: true });















function transport(id, action) {
  const button = document.getElementById(id);
  button.addEventListener('touchstart', () => {
    knock(button);
    bridge.triggerHaptic('tap');
  }, { passive: true });
  button.addEventListener('click', event => {
    event.stopPropagation();
    bridge.mediaControl(typeof action === 'function' ? action() : action);
  });
}


function pressPlay() {
  const wanted = shared.media && shared.media.isPlaying ? 'pause' : 'play';
  flipPlaying();
  return wanted;
}

transport('lock-previous', 'previous');
transport('lock-next', 'next');
transport('lock-play', pressPlay);
transport('player-previous', 'previous');
transport('player-next', 'next');
transport('player-play', pressPlay);







const playerTimeline = document.getElementById('player-timeline');
const playerTrack = document.getElementById('player-track');







let scrubOrigin = 0;
let scrubStart = 0;

function scrubBy(clientX) {
  const box = playerTrack.getBoundingClientRect();
  const travelled = ((clientX - scrubOrigin) / box.width) * shared.media.duration;
  return Math.min(shared.media.duration, Math.max(0, Math.round(scrubStart + travelled)));
}















playerTimeline.addEventListener('touchstart', event => {
  if (!shared.media || !shared.media.duration) return;
  event.stopPropagation();
  shared.isScrubbing = true;
  playerTimeline.classList.add('scrubbing');
  scrubOrigin = event.touches[0].clientX;
  scrubStart = shownPosition;
  shared.scrubPosition = shownPosition;
}, { passive: true });

playerTimeline.addEventListener('touchmove', event => {
  if (!shared.isScrubbing) return;
  event.stopPropagation();
  shared.scrubPosition = scrubBy(event.touches[0].clientX);
  paintProgress(shared.scrubPosition);
}, { passive: true });

playerTimeline.addEventListener('touchend', event => {
  if (!shared.isScrubbing) return;
  event.stopPropagation();
  shared.isScrubbing = false;
  playerTimeline.classList.remove('scrubbing');
  bridge.triggerHaptic('tap');
  bridge.mediaSeek(String(shared.scrubPosition));
}, { passive: true });



playerTimeline.addEventListener('click', event => event.stopPropagation());



playerTimeline.addEventListener('touchcancel', () => {
  shared.isScrubbing = false;
  playerTimeline.classList.remove('scrubbing');
}, { passive: true });


playerTimeline.addEventListener('click', event => event.stopPropagation());
