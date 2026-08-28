import { catchInto, releaseCatch, stirLiquid } from './liquid.js';
import { clock, paintProgress, playPath, shownPosition } from './mods/media.js';
import { DEAD_ZONE, toy, untoy } from './motion.js';
import { fitNowProxy } from './now.js';
import { ensureClosedWindow, isLive, paintSatellites, toClosed } from './row.js';
import { HOLD_MILLIS, bridge, pill, root, shared } from './state.js';
import { leaveForMod } from './tabs.js';

/**
 * Mirrors #lock-now in the CSS above and LOCK_INSET / LOCK_BOTTOM / LOCK_HEIGHT in
 * BubbleService, which places the touch proxy from them. There is no build step
 * joining the three, so a size changed in one of them and not the others is a bubble
 * being aimed at through a hole somewhere else.
 */
const LOCK_INSET = 16;
const LOCK_BOTTOM = 20;
const LOCK_HEIGHT = 124;
/** Open, it is the media tab: the same parts with the room to be worked rather than read. */
const LOCK_OPEN_HEIGHT = 546;

/** A flick up opens the bubble and a flick down closes it, past this much travel. */
const LOCK_SWIPE = 24;

/** Mirrors --lock-fly-ms in the CSS above: the rest of the way home, once it is up to speed. */
const LOCK_FLY = 640;

/** What it pulls itself in to, on the spot, before it goes anywhere at all. */
const LOCK_PINCH_SCALE = 0.55;

/** How long that takes. It is a move of its own, not the first part of the journey. */
const LOCK_PINCH = 260;

/** What share of full speed it crawls at while it is still closing. It is already on its way during the pinch — the pinch is not a pause — but at a quarter of the pace, so the closing is what the eye is given first and the journey only takes over once there is nothing left to close. */
const LOCK_CRAWL = 0.25;

/** Slow to leave, then all at once, and past its mark: gathering in is a thing being wound up, so it has to start reluctantly and end fast enough to throw itself the rest of the way. An ease that leaves at speed reads as the box simply being set to a smaller number, which is the whole complaint about a resize pretending to be a movement. */
const LOCK_PINCH_EASE = 'cubic-bezier(0.62, 0, 0.2, 1.65)';

/** How long the keyguard's own dismiss is left alone before the journey starts. */
const LOCK_FLY_WAIT = 120;

/** Mirrors --ease-split in the CSS above: it lands with the overshoot the row has. */
const LOCK_FLY_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';

/** Mirrors --grow-ms and --ease-split in the CSS above: the box is animated by hand. */
const LOCK_GROW = 340;
const LOCK_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';

export const lockPill = document.getElementById('lock-now');
export const lockArt = document.getElementById('lock-art');
const lockElapsed = document.getElementById('lock-elapsed');

/**
 * Every part that ends up somewhere else when the bubble opens. The cover is the only
 * one that resizes as well as moves; the rest are translated, because scaling text and
 * icons to their new size stretches the glyphs on the way there.
 */
const lockMovers = [
  { element: document.getElementById('lock-art-slot'), resizes: true },
  { element: document.getElementById('lock-meta'), resizes: false },
  { element: document.getElementById('lock-buttons'), resizes: false },
  { element: document.getElementById('lock-timeline'), resizes: false },
];

/** True while the keyguard is up. Told by the host; nothing here works it out. */
let locked = false;

/**
 * What the lock screen's bubble takes off the row while the phone is locked.
 *
 * A stolen mod is not copied down here and it is not drawn in both places at once —
 * it simply belongs to a different bubble for as long as the keyguard is up. The
 * steal is one filter in liveMods() and nothing else, because liveMods() is what the
 * row's width, its satellites, its dots and its window are all worked out from: take
 * the mod out there and every one of them stops carrying it without being told.
 * Putting it back is the same filter going quiet.
 */
const LOCK_STEALS = ['media'];

/**
 * The keyguard has gone but the mod has not been handed back yet — it is still in the
 * air. The steal outlives the lock screen by exactly one flight, because the row
 * opening out is supposed to be *caused* by the drop arriving: let go of at the moment
 * the keyguard went, the bubble at the cutout had already grown and settled before the
 * thing it grew for had even set off, and the two read as unrelated events happening
 * near each other rather than as one bubble being taken into another.
 */
let stealHeld = false;

export function isStolen(mod) {
  return (locked || stealHeld) && LOCK_STEALS.includes(mod);
}

/** Which mod the lock bubble is carrying, in the order the row would have had them. */
function lockMod() {
  if (!locked && !stealHeld) return undefined;
  return shared.modOrder.filter(mod => LOCK_STEALS.includes(mod) && isLive(mod))[0];
}

/** Open, which on this bubble is the media tab and nothing else so far. */
let lockOpen = false;

/** True for the length of the way home, while the bubble is neither shown nor gone. */
let lockFlying = false;

/** The journey itself, held so it can be let go of when the bubble is taken in. */
let lockFlight = null;

export function paintLock() {
  // Mid-flight the bubble is already not the row's answer any more — locked is false
  // and the mod has gone back — so every question this function asks is answered
  // "hide it", which is exactly what must not happen while it is still travelling.
  if (lockFlying) return;
  const mod = lockMod();
  const showing = Boolean(mod);
  if (lockPill.classList.contains('showing') !== showing) stirLiquid(500);
  lockPill.classList.toggle('showing', showing);
  // The goo filter's region is the row's own height until this bubble is standing at
  // the bottom of the screen, and a skin drawn outside the region is not drawn at all.
  root.classList.toggle('lock-live', showing);
  if (!showing && lockOpen) closeLock();
  fitLockProxy();
  if (!showing) return;
  lockPill.style.setProperty('--lock-accent', shared.media.accent || 'var(--section-color)');
  document.getElementById('lock-title').textContent = shared.media.title || '';
  document.getElementById('lock-artist').textContent = shared.media.artist || '';
  document.getElementById('lock-duration').textContent = clock(shared.media.duration || 0);
  document.getElementById('lock-play-path').setAttribute('d', playPath());
  paintLockProgress(shared.media.position || 0);
}

/**
 * Opening does not only make the bubble taller — it re-lays it out. The cover leaves
 * the side of the title and goes above it, which means the row it is in changes
 * direction, and no amount of easing on a width can animate that: a reflow is a jump
 * by definition, and every part of the bubble arrived at its new place in one frame
 * however carefully its own box was transitioned.
 *
 * So the layout is allowed to jump, and then it is put back. Each part is measured
 * before and after, and for one frame carries the transform that returns it to exactly
 * where it was; letting go of that transform is what the eye reads as the move. The
 * cover is the only one that carries a scale with it — an image resamples, but text
 * and icons stretch — so its width and height jump straight to the new size and the
 * scale is what travels, which is a compositor transform rather than a relayout every
 * frame. --ease-split overshoots, so everything lands with the bounce the rest of the
 * interface has.
 */
function settleLock(change) {
  const from = lockPill.getBoundingClientRect().height;
  const before = lockMovers.map(mover => mover.element.getBoundingClientRect());
  change();
  // The box's height is animated by hand rather than by the transition list, and this
  // is the whole reason why: a height still easing towards its new value measures as
  // the old one, and every part inside a bottom-anchored box is placed off that
  // height. Measured against a box that had not grown yet, the transforms below put
  // the contents back to somewhere neither layout ever had them.
  const to = lockOpen ? LOCK_OPEN_HEIGHT : LOCK_HEIGHT;
  lockPill.animate(
    [{ height: from + 'px' }, { height: to + 'px' }],
    { duration: LOCK_GROW, easing: LOCK_EASE }
  );
  lockMovers.forEach((mover, index) => {
    const from = before[index];
    const to = mover.element.getBoundingClientRect();
    if (!from.width || !to.width) return;
    const scale = mover.resizes ? from.width / to.width : 1;
    // Centres, not corners: a box that scales about its middle moves its own edges,
    // and matching corners would leave it half a width out at the far end.
    const dx = (from.left + from.width / 2) - (to.left + to.width / 2);
    const dy = (from.top + from.height / 2) - (to.top + to.height / 2);
    mover.element.style.transition = 'none';
    mover.element.style.transform =
      'translate(' + dx.toFixed(1) + 'px,' + dy.toFixed(1) + 'px) scale(' + scale.toFixed(3) + ')';
  });
  // Next frame, because a transform written and cleared in the same one is never a
  // transition — the browser only ever computes the second value.
  requestAnimationFrame(() => {
    lockMovers.forEach(mover => {
      mover.element.style.transition = 'transform var(--grow-ms) var(--ease-split)';
      mover.element.style.transform = '';
    });
  });
  stirLiquid(900);
}

/**
 * The bubble opening out into the media tab, in place: it is hung off the bottom edge
 * of the screen, so a taller box grows upwards on its own and the end the thumb is
 * resting on never moves. Nothing is animated by hand here and there is no second
 * panel — it is the same bubble at a different height, which is what makes it read as
 * one thing opening rather than as something else appearing over it.
 */
function openLock() {
  if (lockOpen) return;
  lockOpen = true;
  settleLock(() => lockPill.classList.add('open'));
  fitLockProxy();
  // The payload's position is from whenever the session last reported one, which can
  // be minutes ago. Asked for, so the line is right the moment it is big enough to
  // read rather than a tick later — the same reason openPlayer asks.
  const now = Number(bridge.readMediaPosition());
  paintLockProgress(Number.isFinite(now) && now > 0 ? now : (shared.media && shared.media.position) || 0);
  stirLiquid(600);
}

function closeLock() {
  if (!lockOpen) return;
  lockOpen = false;
  settleLock(() => lockPill.classList.remove('open'));
  fitLockProxy();
  stirLiquid(600);
}

/** The line along the bottom edge, the dot at the end of it, and the two times. */
export function paintLockProgress(position) {
  const duration = (shared.media && shared.media.duration) || 0;
  const ratio = duration > 0 ? Math.min(1, position / duration) : 0;
  lockElapsed.style.width = (ratio * 100) + '%';
  document.getElementById('lock-position').textContent = clock(position);
}

/** The proxy is exactly the bubble, and it exists only while the bubble does. */
function fitLockProxy() {
  if (lockFlying || !lockPill.classList.contains('showing')) {
    bridge.setLockProxy(0, 0, 0, 0);
    return;
  }
  const height = lockOpen ? LOCK_OPEN_HEIGHT : LOCK_HEIGHT;
  bridge.setLockProxy(
    Math.round(window.innerWidth - LOCK_INSET * 2), height,
    LOCK_INSET, Math.round(window.innerHeight - LOCK_BOTTOM - height)
  );
}

/**
 * Every proxy put back where it belongs, asked for by the host when the interface
 * comes back on screen. Hiding takes touchability off the windows the page owns, and
 * only the page knows which of them should have it back.
 */
window.refitProxies = () => {
  fitNowProxy();
  fitLockProxy();
};

/**
 * Unlocking, which is a merge and not a disappearance.
 *
 * The row takes its mod back the moment `locked` goes false — that is the steal
 * letting go — so the bubble at the cutout is already opening out to receive
 * something. This is the something: the lock bubble travels to it and is taken in the
 * same way the torch's drop and a satellite's circle are, as a catch the mirror
 * measures rather than a bounce played on a timer. The receiver's stretch grows with
 * however much of the drop is inside it and springs back when it is gone.
 */
function flyLockHome() {
  // Untouchable from the first frame of the flight: it is not a control any more, and
  // a proxy left standing over the bottom of an unlocked screen eats the gesture bar.
  fitLockProxy();
  // A beat before it leaves, because the keyguard is still playing its own dismiss
  // over the top of this: a journey that starts underneath that animation is a journey
  // nobody sees. It goes once there is a home screen to cross.
  lockPill.classList.add('flying');
  setTimeout(startLockFlight, LOCK_FLY_WAIT);
}

function startLockFlight() {
  if (!lockFlying) return;
  const from = lockPill.getBoundingClientRect();
  const to = pill.getBoundingClientRect();
  // Centres, because the shrink is about the middle: matching edges would leave it
  // most of its own width short of the bubble it is aiming at.
  const dx = (to.left + to.width / 2) - (from.left + from.width / 2);
  const dy = (to.top + to.height / 2) - (from.top + from.height / 2);
  // By hand rather than as a class with a transition on it. A transition needs a start
  // value the browser has already drawn, and this journey begins in the same breath as
  // the class that describes it — so the box was simply *at* the bubble by the time
  // anything measured it, the merge read a full overlap on its first frame, and
  // unlocking made the bubble vanish rather than travel. An animation has no such
  // requirement: it holds its own start, and every frame of it is a real box, which is
  // the only reason the merge can be measured off it at all.
  // One journey at two speeds, not two moves: the bubble is already on its way while it
  // is still closing, at a quarter of the pace, and opens up to full speed the moment
  // there is nothing left to close. Held still for the closing it reads as two unrelated
  // things happening in turn — a box being resized, and then a box being slid across the
  // screen — where the slow start reads as one thing gathering itself and going.
  //
  // It closes the rest of the way over the journey, because a merge is only over when
  // the traveller is genuinely inside what is taking it in. Carried at full width it
  // would still be most of the screen wide on arrival, and either it would be seen
  // going out on top of the bubble or the merge would have to be called finished while
  // it plainly had not happened. The drop it closes to is the receiver's own size,
  // measured rather than named — the bubble is a different size under every mod, and
  // both dimensions count, since a drop still standing taller than the bubble is one
  // that never finishes going in. A shade under that, so it is properly inside rather
  // than exactly filling it: a drop that merely fits ends the merge on the stall timer
  // instead of on the shapes.
  const drop = Math.min(to.width / from.width, to.height / from.height) * 0.9;
  const whole = LOCK_PINCH + LOCK_FLY;
  const turn = LOCK_PINCH / whole;
  // Two animations rather than one set of keyframes, because the size and the journey
  // are not on the same clock and a keyframe's easing owns every property in it. The
  // closing winds up and overshoots; the travel crawls at a flat quarter speed and then
  // opens up. Said in one list, whichever ease was written would have been imposed on
  // both, and that is how the first attempt ended up with a bubble that only appeared to
  // shrink where it stood. They are separate CSS properties, so they compose rather than
  // fight, and both are real boxes to the mirror.
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
  // From here, not from where the catch starts. The skin and the glass are mirrored
  // off this box every frame the mirror runs, and the mirror only runs while something
  // has asked it to — so a pinch that stirred nothing left the frosted rectangle and
  // the liquid outline standing at full size while the contents visibly pulled in. The
  // bubble is its skin, so anything that changes its box owes the mirror the frames.
  stirLiquid(LOCK_PINCH + LOCK_FLY + 400);
  // The merge begins where the journey does. The pinch is a move of its own and
  // nothing is being taken in during it.
  setTimeout(beginLockCatch, LOCK_PINCH);
}

function beginLockCatch() {
  if (!lockFlying) return;
  // No steps on it. The catch is here for the goo — the swell that says something is
  // being taken in — and not for deciding when the journey is over: that is answered by
  // where the two centres are, in watchLockCentre, which is a plainer question than an
  // overlap share and gives the same answer at the same instant every time whatever
  // size the row happens to be.
  catchInto(lockPill, pill);
  watchLockCentre();
  stirLiquid(LOCK_FLY + 400);
}

/** How far the bubble is knocked up by what has just gone into it, and for how long. */
const LOCK_BUMP = 12;
const LOCK_BUMP_MS = 420;

/**
 * Watched rather than timed. The flight is eased and its distance depends on where the
 * bubble happens to be, so the moment the drop is level with it is not a number that can
 * be written down beforehand — and it is the moment the whole merge is judged on.
 */
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

/**
 * The row takes its mod back on the spot. Everything the row does about a mod — its
 * width, its glyph, its window — is worked out from liveMods(), so letting the steal go
 * is the whole of it.
 */
function takeLockBack() {
  stealHeld = false;
  if (shared.state === 'idle') toClosed();
  else {
    paintSatellites();
    ensureClosedWindow();
  }
}

/**
 * Knocked up and back down by what has just gone into it. Added rather than set, because
 * transform on the bubble belongs to whatever is dragging it this frame — set outright it
 * would fight the drag and the row's own place.
 */
function bumpPill() {
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
  // Out on the spot, not faded: by now it is inside the bubble and there is
  // nothing left to see it happen against. Two hundred milliseconds of fade here
  // is the split second of a full-size bubble reappearing at the bottom of the
  // screen — the flight is cancelled with it, so the box springs back to where it
  // started and then fades from there, in plain sight.
  lockPill.style.setProperty('--lock-fade-ms', '0ms');
  lockPill.classList.remove('showing', 'open', 'pressed', 'homing');
  // Deep enough inside the bubble now that there is nothing left to see go out,
  // which is also the moment the filter can go back to the row's own height.
  root.classList.remove('lock-live');
  fitLockProxy();
  // And only once that has been drawn is the journey let go of, or the two happen
  // in one frame and the snap back is what the frame shows.
  requestAnimationFrame(() => {
    if (lockFlight) lockFlight.forEach(move => move.cancel());
    lockFlight = null;
    lockPill.classList.remove('flying');
    lockPill.style.removeProperty('--lock-fade-ms');
  });
}

window.onLock = next => {
  if (locked === next) return;
  // Decided and flagged *before* the row is repainted, not after. Taking its mod back
  // is what repaints the row, and repainting the row repaints this bubble — which
  // asks lockMod() again, is told the keyguard has gone, and takes `showing` off the
  // thing that is about to fly. An unskinned shape is not one of the boxes the mirror
  // measures, so the merge saw a traveller with no box at all, read that as "as far in
  // as it will ever get", and swallowed the whole journey on its first frame. That is
  // what made unlocking look like the bubble simply vanishing.
  const flying = !next && Boolean(lockMod());
  locked = next;
  lockFlying = flying;
  // Held for the length of the flight, so the row does not yet know it has its mod
  // back. The catch's entered step is what hands it over.
  stealHeld = flying;
  if (flying) {
    flyLockHome();
    return;
  }
  // The row has just lost or got back a mod, so it is repainted rather than nudged:
  // its width, the circles beside it, the dots and the window it needs are all read
  // off liveMods(), and that answer has changed.
  if (shared.state === 'idle') toClosed();
  else {
    paintSatellites();
    ensureClosedWindow();
  }
  paintLock();
};

/**
 * The lock bubble is an object too, and it is the one most likely to be idly pushed
 * about: it is the bubble a thumb is already resting on. Same band and same dead zone
 * as the bubble at the cutout — the numbers belong to the interface, not to a bubble.
 * Nothing is asked for by it, so there is no threshold and no release to interpret;
 * the transport's own taps still land, because a touch that travelled is not a tap.
 */
let lockStart = null;
let lockHoldTimer = null;
let lockHeld = false;

/** True once a flick has already answered this touch, so the tap after it does not. */
let lockSwiped = false;

lockPill.addEventListener('touchstart', event => {
  lockStart = { x: event.touches[0].clientX, y: event.touches[0].clientY };
  lockHeld = false;
  lockSwiped = false;
  lockPill.classList.add('pressed');
  // Held, it means what a hold means on every bubble here: out to the app the mod
  // stands for. It runs underneath the play drag exactly as the main bubble's does —
  // the dead zone is what keeps the two out of each other's way.
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
  if (Math.hypot(dx, dy) <= DEAD_ZONE) return;
  // Up opens it, down closes it — the direction the box itself moves, so the gesture
  // is the animation asked for by hand. It fires the moment the threshold is passed
  // rather than on release, for the same reason the row's trade does: a threshold
  // that only answers after the finger has gone is one the thumb has to guess at.
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

/**
 * Tapped, it opens and closes — the transport's own buttons stop their clicks before
 * they get here, so a tap on play is a tap on play and a tap on the bubble is a tap
 * on the bubble. A hold has already acted by the time the finger lifts, and the tap
 * that follows would undo it.
 */
lockPill.addEventListener('click', () => {
  if (lockHeld || lockSwiped) return;
  bridge.triggerHaptic('tap');
  if (lockOpen) closeLock();
  else openLock();
});

/**
 * A button that answers being pressed. The class is taken off on the next frame
 * rather than after a timer: the shrink is instant and the way back is the long
 * transition, so the whole of the feedback is one property with one owner, and a
 * second tap landing during the first one restarts it from wherever it had got to.
 */
function knock(element) {
  element.classList.add('knocked');
  requestAnimationFrame(() => requestAnimationFrame(() => {
    element.classList.remove('knocked');
  }));
}

function lockTransport(id, action) {
  document.getElementById(id).addEventListener('click', event => {
    event.stopPropagation();
    knock(event.currentTarget);
    bridge.triggerHaptic('tap');
    bridge.mediaControl(action);
  });
}

lockTransport('lock-previous', 'previous');
lockTransport('lock-next', 'next');
document.getElementById('lock-play').addEventListener('click', event => {
  event.stopPropagation();
  knock(event.currentTarget);
  bridge.triggerHaptic('tap');
  bridge.mediaControl(shared.media && shared.media.isPlaying ? 'pause' : 'play');
});


function transport(id, action) {
  document.getElementById(id).addEventListener('click', event => {
    event.stopPropagation();
    knock(event.currentTarget);
    bridge.triggerHaptic('tap');
    bridge.mediaControl(action);
  });
}

transport('player-previous', 'previous');
transport('player-next', 'next');

document.getElementById('player-play').addEventListener('click', event => {
  event.stopPropagation();
  knock(event.currentTarget);
  bridge.triggerHaptic('tap');
  bridge.mediaControl(shared.media && shared.media.isPlaying ? 'pause' : 'play');
});

/**
 * Scrubbing follows the finger. The seek itself is sent once, on release — asking
 * a player to jump sixty times a second makes it stutter and fight back — but the
 * bar and the clock move the whole way, so the position under the thumb is the
 * position being chosen.
 */
const playerTimeline = document.getElementById('player-timeline');
const playerTrack = document.getElementById('player-track');

/**
 * The drag is relative: it moves the position by however far the finger travels,
 * starting from where the song actually is. Jumping to wherever the thumb first
 * landed would throw away the place you were trying to adjust, and a thumb is
 * wider than the seconds it is aiming at.
 */
let scrubOrigin = 0;
let scrubStart = 0;

function scrubBy(clientX) {
  const box = playerTrack.getBoundingClientRect();
  const travelled = ((clientX - scrubOrigin) / box.width) * shared.media.duration;
  return Math.min(shared.media.duration, Math.max(0, Math.round(scrubStart + travelled)));
}

playerTimeline.addEventListener('touchstart', event => {
  if (!shared.media || !shared.media.duration) return;
  shared.isScrubbing = true;
  playerTimeline.classList.add('scrubbing');
  scrubOrigin = event.touches[0].clientX;
  scrubStart = shownPosition;
  shared.scrubPosition = shownPosition;
}, { passive: true });

playerTimeline.addEventListener('touchmove', event => {
  if (!shared.isScrubbing) return;
  shared.scrubPosition = scrubBy(event.touches[0].clientX);
  paintProgress(shared.scrubPosition);
}, { passive: true });

playerTimeline.addEventListener('touchend', () => {
  if (!shared.isScrubbing) return;
  shared.isScrubbing = false;
  playerTimeline.classList.remove('scrubbing');
  bridge.triggerHaptic('tap');
  bridge.mediaSeek(String(shared.scrubPosition));
}, { passive: true });

// A cancelled touch never gets a touchend, and a bar left twice as tall would
// claim a drag that is over.
playerTimeline.addEventListener('touchcancel', () => {
  shared.isScrubbing = false;
  playerTimeline.classList.remove('scrubbing');
}, { passive: true });

// The tap on the timeline is a seek, not a tap on the player behind it.
playerTimeline.addEventListener('click', event => event.stopPropagation());
