import { catchInto, releaseCatch, resendBlur, stirLiquid } from './liquid.js';
import { blankLockLabels, clock, flipPlaying, paintProgress, playPath, livePosition, shownPosition, songSwapping, typeLabels } from './mods/media.js';
import { cancelSpring, HOLD_BLOCK, rubberBandPast, toy, untoy } from './motion.js';
import { fitClockProxy } from './clock.js';
import { fitStatusProxy, setPanelNowOpen } from './status.js';
import { ensureClosedWindow, isLive, paintSatellites, toClosed } from './row.js';
import { HOLD_MILLIS, bridge, pill, root, shared } from './state.js';
import { leaveForMod } from './tabs.js';


let lockShift = 0;


window.setLockShift = lockX => {
  lockShift = Number(lockX) || 0;
  root.style.setProperty('--lock-x', lockShift + 'px');
  fitLockProxy();
};

const LOCK_HEIGHT = 124;

// Mirrors --lock-compact-height in pill.css: the Now bubble retracted inside the quick-settings panel, with a mod or without one.
const LOCK_COMPACT_HEIGHT = 44;

function compactHeight() {
  return LOCK_COMPACT_HEIGHT;
}

const LOCK_OPEN_HEIGHT = 546;


const LOCK_SWIPE = 24;


// The leave is the one journey here written as **two** timelines, and the seam between them is the point of it rather than the bug the rest of this file spends its comments avoiding: a slingshot is a draw and then a release, and those are two events with a discontinuity between them. What made the single timeline read as lame is that it was one curve every time — same draw, same shares, same distance — so the eye learnt it after two goes.
const LOCK_DRAW_MILLIS = [180, 240];
const LOCK_DRAW_BACK = [26, 44];
const LOCK_DRAW_SQUEEZE = 0.94;


// The shot's speed and overshoot are drawn fresh per shot and bounded on purpose: weaker than this and the arc is a drift, stronger and the whole thing is over before it can be seen. The *angle* is not drawn — it is aimed, off the flick that dismissed the panel.
const LOCK_SHOT_MILLIS = [300, 420];
const LOCK_SHOT_ANGLE = [10, 34];
const LOCK_SHOT_OVER = [14, 30];
const LOCK_SHOT_PUNCH = 1.16;


// How hard the release lets go, as the initial velocity of one damped spring, and how far along the launch line the arc is aimed before it bends onto the target.
const LOCK_SHOT_DECAY = 5.2;
const LOCK_LEAN_REACH = 18;
const LOCK_SHOT_LEAD = 0.62;


// The arc is one curve read at this many points. Over a shot of ~350ms that is a sample every 13ms — closer together than the frames that will draw them — so the straight lines between them are what the eye never sees and the curve is what it does.
const LOCK_SHOT_SAMPLES = 26;


// The punch is keyed to the clock and not to the distance: the release covers a third of the journey in its first sixth, so a stretch keyed to how far it had got would be over before it could be seen.
const LOCK_PUNCH_SHARE = 0.34;


// Where the spring stops being a spring. Past this the overshoot it is still carrying is wound down to nothing on a curve that is flat at both ends, so the shape arrives at a standstill instead of arriving with the velocity a cut-off oscillation still has — that leftover velocity is what a bounce is.
const LOCK_SETTLE_FROM = 0.78;


// How far past the mark the spring's first crest lands, as a share of the journey. It has no closed form — the crest is where the spring's own velocity turns, not where its cosine bottoms out, and solving it as though the two were the same put the overshoot at nearly twice what was asked for. The excess climbs with the swing, so a bisection settles it in a handful of steps and the number that comes out is the one the shot was aimed at.
function crestOf(swing) {
  return (swing / Math.hypot(LOCK_SHOT_DECAY, swing)) *
    Math.exp(-LOCK_SHOT_DECAY * (Math.PI - Math.atan(LOCK_SHOT_DECAY / swing)) / swing);
}

function smoothly(step) {
  return step * step * (3 - 2 * step);
}

function swingFor(share) {
  let low = 0.1;
  let high = 40;
  for (let step = 0; step < 24; step += 1) {
    const middle = (low + high) / 2;
    if (crestOf(middle) < share) low = middle;
    else high = middle;
  }
  return (low + high) / 2;
}


// The reading falls away from under the cover, one block after another, while the cover itself stays where it is: it is the mod, and the mod is the thing being carried home, so it rides the bubble's own close rather than leaving separately.
const LOCK_LEAVE_STAGGER = 0.08;
const LOCK_LEAVE_SPRIG = 0.5;
const LOCK_LEAVE_DROP = 20;


// How long the drop has to leave once the goo has joined it to Main. The mod is not carried the last few pixels and set down: Main puts it on the moment the two touch, and what is left standing there has nothing to say, so it goes.
const LOCK_MERGE_FADE = 200;


const LOCK_DRAW_EASE = 'cubic-bezier(0.5, 0, 0.75, 0.2)';
const LOCK_POP_EASE = 'cubic-bezier(0.2, 1.9, 0.4, 1)';
const LOCK_SHOT_EASE = 'cubic-bezier(0.3, 0, 0.2, 1)';

function between([low, high]) {
  return low + Math.random() * (high - low);
}


const LOCK_FLY_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';


const LOCK_GROW = 340;
const LOCK_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';


const LOCK_SPRIG_MS = 460;
const LOCK_SPRIG_STAGGER = 80;


const LOCK_TYPE_IN = 140;

export const lockPill = document.getElementById('lock-now');


// Where the shot is aimed. Not the middle of Main — the slot the mod's own artwork will stand in once the row has it back, so the cover the ball is carrying lands on the spot it is about to occupy rather than near it. The face it sits in is hidden while the mod is stolen and is still laid out, which is what makes it measurable at all.
const mediaArtSlot = document.getElementById('media-art-slot');
export const lockArt = document.getElementById('lock-art');
const lockElapsed = document.getElementById('lock-elapsed');






const lockMovers = [
  { element: document.getElementById('lock-art-slot'), resizes: true, travels: true },
  { element: document.getElementById('lock-meta'), resizes: false },
  { element: document.getElementById('lock-buttons'), resizes: false },
  { element: document.getElementById('lock-timeline'), resizes: false },
];


let locked = false;











const LOCK_STEALS = ['media'];









let stealHeld = false;

export function isStolen(mod) {
  if (panelHeld) return LOCK_STEALS.includes(mod);
  return (locked || stealHeld) && LOCK_STEALS.includes(mod);
}


function lockMod() {
  if (!locked && !stealHeld && !panelHeld) return undefined;
  return shared.modOrder.filter(mod => LOCK_STEALS.includes(mod) && isLive(mod))[0];
}


let lockOpen = false;


let lockFlying = false;


let lockFlight = null;


let lockArrival = [];
// The three animations the bubble's own shape is made of, kept apart from the reading's: the shape has landed a good half second before the last block of text has finished growing in under it, and handing it back to the stylesheet on the reading's clock is what left it standing in the flight's own values — and untouchable — long after it had visibly arrived.
let lockShape = [];
let lockTypeTimer = 0;

function lockArriving() {
  return lockPill.classList.contains('arriving');
}

function stopLockArrival() {
  clearTimeout(lockTypeTimer);
  lockArrival.forEach(move => move.cancel());
  lockArrival = [];
  lockShape.forEach(move => move.cancel());
  lockShape = [];
  lockPill.classList.remove('arriving', 'circling');
  lockMovers.forEach(mover => mover.element.style.removeProperty('opacity'));
}

export function paintLock() {
  
  
  
  if (lockFlying) return;
  const mod = lockMod();
  const live = Boolean(mod);
  // A bubble about nothing is not a bubble: with no mod live there is nothing to read, so the panel's foot is left empty and the toggles keep the room rather than a blank pill holding it.
  const showing = live;
  const wasShowing = lockPill.classList.contains('showing');
  if (wasShowing !== showing) stirLiquid(500);
  lockPill.classList.toggle('showing', showing);
  // On the keyguard the bubble used to be placed rather than flown, which made it the one bubble that appeared at its own position. It arrives out of Main on the same slingshot the panel's arrival already is — the journey is the same journey, so it is the same code.
  if (showing && !wasShowing && !lockPill.classList.contains('arriving')) {
    lockPill.classList.add('arriving');
    requestAnimationFrame(playNowEnter);
  }
  // `.idle` is what blanks the bubble's children, and it used to come off in the same frame `showing` did — so leaving the panel with nothing playing spent the whole fade out showing the last mod's artwork and labels. It follows there being no mod at all now rather than a blank bubble that is also standing.
  lockPill.classList.toggle('idle', !live);


  root.classList.toggle('lock-live', live);
  if (!live && lockOpen) closeLock();
  if (!live && panelHeld) setPanelNowExpanded(false);
  fitLockProxy();
  if (!live) return;
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
  
  
  
  
  
  const to = panelHeld
    ? (panelExpanded ? LOCK_HEIGHT : compactHeight())
    : (lockOpen ? LOCK_OPEN_HEIGHT : LOCK_HEIGHT);
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
  if (panelHeld) { setPanelNowExpanded(true); return; }
  if (lockOpen) return;
  lockOpen = true;
  settleLock(() => lockPill.classList.add('open'));
  fitLockProxy();
  
  
  paintLockProgress(livePosition());
  stirLiquid(600);
}

function closeLock() {
  if (panelHeld) { setPanelNowExpanded(false); return; }
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

// getBoundingClientRect reads the flight's own translate and scale, so a window measured during an arrival stands on a drop crossing the screen — which is why the proxy was withheld for the whole journey, and why the bubble could be seen for most of a second and not touched. The resting box is known from the frame the flight is worked out, so it is kept and the window is placed where the bubble is going to be.
let lockRest = null;

function fitLockProxy() {
  const hidden = lockFlying || !lockPill.classList.contains('showing') || lockPill.classList.contains('idle');
  const box = hidden ? null : (lockArriving() && lockRest ? lockRest : lockPill.getBoundingClientRect());
  const proxy = hidden
    ? [0, 0, 0, 0]
    : [Math.round(box.width), Math.round(box.height), Math.round(box.left), Math.round(box.top)];
  
  
  
  if (proxy.join() === shownProxy) return;
  shownProxy = proxy.join();
  bridge.setLockProxy(proxy[0], proxy[1], proxy[2], proxy[3]);
}










export function lockHolds(x, y) {
  if (lockFlying || !lockPill.classList.contains('showing') || lockPill.classList.contains('idle')) return false;
  const box = lockArriving() && lockRest ? lockRest : lockPill.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}






window.refitProxies = () => {
  fitLockProxy();
  fitStatusProxy();
  fitClockProxy();
};











function flyLockHome() {

  stopLockArrival();

  fitLockProxy();



  lockPill.classList.add('flying');
  requestAnimationFrame(drawLockBack);
}


let lockShot = null;


// Which way the hand threw it. Every way of closing that has no hand behind it — the unlock, a tap elsewhere, another bubble opening — leaves this at nothing and the shot picks a side of its own.
let lockLean = 0;


// The drop is sized against the bubble it is flying into, so that has to be the bubble which will be standing there and not the one measurable on the way out of the panel. Closing the panel takes `panel-top` off Main and its height eases from `--pill-height` plus `--edge-over` back down to `--pill-height` across the whole of the flight, so the first frame of that transition — the frame the shot is worked out on — reported 46px where the target is 26. The drop was thrown at nearly twice the size of what it was landing in, its overlap topped out around 0.57 against a `CATCH_ENTERED` of 0.8, and the touch could then only fire on the catch's stall fallback: that fallback is what read as the merge lagging out of the panel but not off the lock screen, where Main is already at rest and the measurement happens to agree. Only the height was ever wrong — the width is Main's own and is not mid-flight, and the shot is aimed at the artwork slot rather than at this box, which `panel-top` leaves exactly where it is by paying its extra height back as `padding-top`.
function mainRestHeight() {
  return parseFloat(getComputedStyle(pill).getPropertyValue('--pill-height'));
}


// Timeline one. The shape is dragged back off the line it is about to leave on, and closes to a circle while it goes. The circle is what timeline two needs: a pill scaled down to a drop reads as a box being resized, and a circle scaled down reads as one body of liquid getting smaller, which is the whole of whether the shot is immersive or is a picture of a bubble shrinking.
function drawLockBack() {
  if (!lockFlying) return;
  const from = lockPill.getBoundingClientRect();
  const to = pill.getBoundingClientRect();
  const art = lockMovers[0].element.getBoundingClientRect();

  // Everything the shot needs is worked out here, at the one moment the box is still the box it started as: after the draw it is a circle, and a circle's own rect no longer says where the journey began.
  const ball = Math.min(from.width, from.height);
  const slot = mediaArtSlot.getBoundingClientRect();
  const aim = slot.width ? slot : to;
  const dx = (aim.left + aim.width / 2) - (from.left + from.width / 2);
  const dy = (aim.top + aim.height / 2) - (from.top + from.height / 2);
  const reach = Math.hypot(dx, dy) || 1;

  // The throw is aimed by the hand. The flick that dismissed the panel fires the moment it has gone 24px up, so what it has done sideways by then is a handful of pixels rather than a whole gesture — LOCK_LEAN_REACH is what a full lean is worth, and it is small for exactly that reason. A close with no flick behind it leaves it at nothing and the shot takes a side of its own, since straight up the middle is the one shape this journey may not be.
  const lean = Math.max(-1, Math.min(1, lockLean / LOCK_LEAN_REACH));
  const side = lean === 0 ? (Math.random() < 0.5 ? -1 : 1) : Math.sign(lean);
  const degrees = LOCK_SHOT_ANGLE[0] + Math.abs(lean) * (LOCK_SHOT_ANGLE[1] - LOCK_SHOT_ANGLE[0]);
  const angle = (side * degrees * Math.PI) / 180;

  // The launch line is the direct line turned by the angle. The draw is straight back along that line: the pull and the release are one line, and the bow is what bends off it afterwards.
  const launchX = (Math.cos(angle) * dx - Math.sin(angle) * dy) / reach;
  const launchY = (Math.sin(angle) * dx + Math.cos(angle) * dy) / reach;
  const back = between(LOCK_DRAW_BACK);

  lockShot = {
    dx,
    dy,
    reach,
    launchX,
    launchY,
    backX: -launchX * back,
    backY: -launchY * back,
    drop: (Math.min(to.width, mainRestHeight()) / ball) * 0.9,
    over: between(LOCK_SHOT_OVER),
    millis: Math.round(between(LOCK_SHOT_MILLIS)),
  };

  const millis = Math.round(between(LOCK_DRAW_MILLIS));

  // Nothing may read layout between here and the width animation below: centring takes the bubble off cross-axis stretch, and until the keyframes are in effect the box it would resolve to is its own content's width rather than the one just measured.
  lockPill.classList.add('circling');

  const draw = lockPill.animate(
    [
      { translate: '0px 0px', scale: 1 },
      {
        translate: lockShot.backX.toFixed(1) + 'px ' + lockShot.backY.toFixed(1) + 'px',
        scale: LOCK_DRAW_SQUEEZE,
      },
    ],
    { duration: millis, easing: LOCK_DRAW_EASE, fill: 'forwards' }
  );

  lockFlight = [
    draw,
    lockPill.animate(
      [
        { width: Math.round(from.width) + 'px', borderRadius: '34px' },
        { width: Math.round(ball) + 'px', borderRadius: Math.round(ball / 2) + 'px' },
      ],
      { duration: millis, easing: LOCK_SHOT_EASE, fill: 'forwards' }
    ),
    // The cover is centred in the box by .circling rather than being carried there by hand, so it holds the middle for every width the box passes through instead of chasing a layout that is moving underneath it — an offset worked out once against the starting width is only correct at the starting width. What is animated is the jump that centring it caused: from exactly where the flex row had it, back to nothing. Its size travels on the same clock, from the square it was in the row up to the ball, so the drop that sets off is the artwork at full size rather than the artwork parked in the middle of a blank circle. It is grown as a box rather than scaled because `object-fit` crops against the box it is given: scaled, the cover would carry the pill's wide crop squeezed into a square, and the picture would stretch back out over the draw.
    lockMovers[0].element.animate(
      [
        {
          translate:
            ((art.left + art.width / 2) - (from.left + from.width / 2)).toFixed(1) + 'px ' +
            ((art.top + art.height / 2) - (from.top + from.height / 2)).toFixed(1) + 'px',
          width: Math.round(art.width) + 'px',
          height: Math.round(art.height) + 'px',
        },
        {
          translate: '0px 0px',
          width: Math.round(ball) + 'px',
          height: Math.round(ball) + 'px',
        },
      ],
      { duration: millis, easing: LOCK_SHOT_EASE, fill: 'forwards' }
    ),
    ...lockMovers.filter(mover => !mover.travels).map((mover, index) => {
      const goes = index * LOCK_LEAVE_STAGGER;
      return mover.element.animate(
        [
          { opacity: 1, translate: '0px 0px', offset: 0 },
          { opacity: 1, translate: '0px 0px', offset: goes, easing: LOCK_SHOT_EASE },
          {
            opacity: 0,
            translate: '0px ' + LOCK_LEAVE_DROP + 'px',
            offset: Math.min(1, goes + LOCK_LEAVE_SPRIG),
          },
          { opacity: 0, translate: '0px ' + LOCK_LEAVE_DROP + 'px', offset: 1 },
        ],
        { duration: millis, fill: 'forwards' }
      );
    }),
  ];

  stirLiquid(millis + 200);
  draw.addEventListener('finish', shootLockHome);
}


// Timeline two. The release: it leaves along the launch line, which points off the target by the angle, bows back onto it, carries past Main by `over` and is pulled back onto it. Nothing merges while it is in the air — the goo is the only thing joining it to anything — and Main's own reading goes with the shot, so what lands is a bare bubble rather than a face the arrival has to replace.
function shootLockHome() {
  if (!lockFlying || !lockShot) return;
  const shot = lockShot;

  // One quadratic curve from the draw point to Main, with its control point set along the launch line: a quadratic's tangent at its start is exactly the line to that control point, so the shape provably leaves at the angle it was aimed at and provably arrives at the target, without either end being a keyframe that has to be tuned into agreement with the other.
  const fromX = shot.backX;
  const fromY = shot.backY;
  const holdX = fromX + shot.launchX * shot.reach * LOCK_SHOT_LEAD;
  const holdY = fromY + shot.launchY * shot.reach * LOCK_SHOT_LEAD;

  // How far along that curve the shape has got is one damped spring, so the release is a single continuous velocity: fastest the instant it lets go, easing off, carrying past the target and settling onto it. It was three keyframes with an easing each, and every one of those easings ends at a standstill — which is what read as the shot stopping in the middle and starting again.
  const swing = swingFor(Math.min(0.35, Math.max(0.004, shot.over / shot.reach)));

  const frames = [];
  for (let index = 0; index < LOCK_SHOT_SAMPLES; index += 1) {
    const step = index / (LOCK_SHOT_SAMPLES - 1);
    const wound = step <= LOCK_SETTLE_FROM
      ? 1
      : 1 - smoothly((step - LOCK_SETTLE_FROM) / (1 - LOCK_SETTLE_FROM));
    const travel = 1 - Math.exp(-LOCK_SHOT_DECAY * step) * Math.cos(swing * step) * wound;
    const back = 1 - travel;
    const punch = Math.sin(Math.min(1, step / LOCK_PUNCH_SHARE) * Math.PI) ** 2;
    const closing = LOCK_DRAW_SQUEEZE + (shot.drop - LOCK_DRAW_SQUEEZE) * (1 - back * back);
    frames.push({
      offset: step,
      translate:
        (back * back * fromX + 2 * back * travel * holdX + travel * travel * shot.dx).toFixed(1) +
        'px ' +
        (back * back * fromY + 2 * back * travel * holdY + travel * travel * shot.dy).toFixed(1) +
        'px',
      scale: closing * (1 + (LOCK_SHOT_PUNCH - 1) * punch),
    });
  }

  root.classList.add('lock-incoming');

  // Travel and size are one animation and one keyframe list here, against the rule everywhere else in this file, because both are already sampled off the same clock — there is no easing left for a keyframe to own, so there is nothing for two of them to disagree about.
  lockFlight.push(lockPill.animate(frames, { duration: shot.millis, fill: 'forwards' }));

  // The catch is opened at the throw rather than at the landing, because what it is watching for is the touch. Main's swell then builds off the closing gap the whole way in, and `entered` is the frame the two shapes actually meet on rather than a timer's guess at when they would.
  catchInto(lockPill, pill, { entered: meltLockHome });

  stirLiquid(shot.millis + LOCK_MERGE_FADE + 400);
}


// The two have touched. The cover used to be flown at the slot it was about to stand in and handed over there, which meant the arriving artwork had to match the slot's position and size to the pixel — it never did, so the last frame of the journey was a picture jumping. Nothing is handed over now: Main puts the mod on at the touch and the drop fades out where it stands, so the swap happens inside the neck the goo is already drawing between them.
function meltLockHome() {
  if (!lockFlying || lockPill.classList.contains('melting')) return;
  bumpPill();
  root.classList.remove('lock-incoming');
  takeLockBack();
  lockPill.style.setProperty('--lock-fade-ms', LOCK_MERGE_FADE + 'ms');
  lockPill.classList.add('melting');
  setTimeout(landLock, LOCK_MERGE_FADE);
}


const LOCK_BUMP = 12;
const LOCK_BUMP_MS = 420;


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
  lockShot = null;
  root.classList.remove('lock-incoming');
  lockOpen = false;
  stealHeld = false;
  
  
  
  
  
  releaseCatch(lockPill);
  lockPill.style.setProperty('--lock-fade-ms', '0ms');
  lockPill.classList.remove('showing', 'idle', 'open', 'pressed', 'in-panel', 'compact', 'melting');
  cancelSpring(lockPill);
  
  
  root.classList.remove('lock-live');
  fitLockProxy();
  
  
  requestAnimationFrame(() => {
    if (lockFlight) lockFlight.forEach(move => move.cancel());
    lockFlight = null;
    lockPill.classList.remove('flying', 'circling');
    lockPill.style.removeProperty('--lock-fade-ms');
  });
}

window.onLock = next => {
  
  
  
  
  resendBlur();
  if (locked === next) return;
  
  
  
  
  
  root.classList.toggle('locked', next);

  
  
  
  
  
  
  const flying = !next && Boolean(lockMod());
  lockLean = 0;
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
  // In the panel the bubble's two sizes are `panelExpanded`; `lockOpen` stays false the whole time it is held there, so every tap read as "open" and the expanded bubble could never be tapped shut again.
  if (panelHeld) {
    setPanelNowExpanded(!panelExpanded);
    return;
  }
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





// The Now bubble in the quick-settings panel is this bubble, not a copy of it: it keeps its element, its mod and its steal, and only its box and its arrival differ. The panel outranks the keyguard about which of its two states it is in — whatever it was doing on the lock screen, opening the panel forces it into the state the panel last left it in.
let panelHeld = false;
let panelExpanded = false;


export function openPanelNow(expanded) {
  panelHeld = true;
  // Expanded is the state a mod is read in, so remembering it across a mod that has since ended stood a full-height blank pill at the panel's foot and held the spare row's room shut behind it.
  panelExpanded = expanded && Boolean(lockMod());
  setPanelNowOpen(panelExpanded);
  // The class goes on before the box does: .in-panel and .compact change the width and the height, and the arrival owns both from the frame the bubble is born.
  stopLockArrival();
  lockPill.classList.add('arriving');
  lockPill.classList.add('in-panel');
  lockPill.classList.toggle('compact', !panelExpanded);
  lockOpen = false;
  // The steal is one filter in liveMods(), so the row only lets go of the mod when something repaints it — and nothing did on this path, which left Main carrying a mod the Now bubble was already holding for the whole of the panel's arrival. Same two calls the keyguard's own steal makes.
  paintSatellites();
  ensureClosedWindow();
  paintLock();
  if (!lockPill.classList.contains('showing')) {
    lockPill.classList.remove('arriving');
    return;
  }
  requestAnimationFrame(playNowEnter);
}

export function closePanelNow(lean) {
  if (!panelHeld) return;
  lockLean = Number(lean) || 0;
  panelHeld = false;
  // A blank bubble has no mod to carry home, and flying one anyway sent an empty pill across the screen into a row that was not expecting it.
  if (lockPill.classList.contains('idle')) {
    lockPill.classList.remove('in-panel', 'compact');
    paintLock();
    return;
  }
  if (!lockPill.classList.contains('showing')) {
    lockPill.classList.remove('in-panel', 'compact');
    paintLock();
    return;
  }
  // The panel closing sends the bubble home exactly as the unlock does, on the same timeline, because it is the same journey: the mod goes back to Main.
  lockFlying = true;
  stealHeld = true;
  flyLockHome();
}

function setPanelNowExpanded(expanded) {
  if (panelExpanded === expanded) return;
  panelExpanded = expanded;
  setPanelNowOpen(expanded);
  settleLock(() => lockPill.classList.toggle('compact', !expanded));
  fitLockProxy();
  stirLiquid(900);
}

// The arrival is the leave run the other way, on the leave's own numbers: the drop is born out of Main as a circle of exactly the diameter the shot ends at, flies the same damped-spring arc back to the bar, and only then opens out of the ball into the pill. It used to arrive as a full-width bar sliding in at 0.9 of its size, which read as a box being positioned rather than as the mod being handed back. .arriving takes the CSS off scale, width and opacity for the length of the journey: one owner per property.
function playNowEnter() {
  const to = lockPill.getBoundingClientRect();
  const from = pill.getBoundingClientRect();
  if (!to.width || !from.width) return;
  stopLockArrival();
  lockPill.classList.add('arriving');
  lockRest = to;
  fitLockProxy();

  const art = lockMovers[0].element.getBoundingClientRect();
  // The radius the bubble opens back out to is whatever it wears where it is standing — 16 retracted in the panel, 40 carrying a song, 34 on the keyguard. Landed on a hardcoded 34 the shape had one more corner change left in it after the journey was over, on the border-radius transition's own clock rather than on the arrival's.
  const restRadius = getComputedStyle(lockPill).borderTopLeftRadius;
  const ball = Math.min(to.width, to.height);
  const drop = (Math.min(from.width, mainRestHeight()) / ball) * 0.9;

  const dx = (from.left + from.width / 2) - (to.left + to.width / 2);
  const dy = (from.top + from.height / 2) - (to.top + to.height / 2);
  const reach = Math.hypot(dx, dy) || 1;

  // Nothing threw this one, so the arc takes a side of its own: straight down the middle is the one shape this journey may not be, whichever way it is travelled.
  const side = Math.random() < 0.5 ? -1 : 1;
  const angle = (side * LOCK_SHOT_ANGLE[0] * Math.PI) / 180;
  const launchX = (Math.cos(angle) * -dx - Math.sin(angle) * -dy) / reach;
  const launchY = (Math.sin(angle) * -dx + Math.cos(angle) * -dy) / reach;

  const back = between(LOCK_DRAW_BACK);
  const backX = -launchX * back;
  const backY = -launchY * back;
  const holdX = dx + launchX * reach * LOCK_SHOT_LEAD;
  const holdY = dy + launchY * reach * LOCK_SHOT_LEAD;

  const over = between(LOCK_SHOT_OVER);
  const swing = swingFor(Math.min(0.35, Math.max(0.004, over / reach)));
  const millis = Math.round(between(LOCK_SHOT_MILLIS));
  const opening = Math.round(between(LOCK_DRAW_MILLIS));

  const frames = [];
  for (let index = 0; index < LOCK_SHOT_SAMPLES; index += 1) {
    const step = index / (LOCK_SHOT_SAMPLES - 1);
    const wound = step <= LOCK_SETTLE_FROM
      ? 1
      : 1 - smoothly((step - LOCK_SETTLE_FROM) / (1 - LOCK_SETTLE_FROM));
    const travel = 1 - Math.exp(-LOCK_SHOT_DECAY * step) * Math.cos(swing * step) * wound;
    const rest = 1 - travel;
    const punch = Math.sin(Math.min(1, step / LOCK_PUNCH_SHARE) * Math.PI) ** 2;
    const growing = drop + (LOCK_DRAW_SQUEEZE - drop) * (1 - rest * rest);
    frames.push({
      offset: step,
      translate:
        (rest * rest * dx + 2 * rest * travel * holdX + travel * travel * backX).toFixed(1) +
        'px ' +
        (rest * rest * dy + 2 * rest * travel * holdY + travel * travel * backY).toFixed(1) +
        'px',
      scale: growing * (1 + (LOCK_SHOT_PUNCH - 1) * punch),
    });
  }

  // The class goes on in the same breath as the animation pinning the width, exactly as the leave's does: .circling takes the bubble off cross-axis stretch, so a frame between the two is a frame at its own content's width.
  lockPill.classList.add('circling');
  blankLockLabels();
  lockMovers.forEach(mover => {
    if (!mover.travels) mover.element.style.opacity = '0';
  });

  const held = [
    { width: Math.round(ball) + 'px', borderRadius: Math.round(ball / 2) + 'px' },
    { width: Math.round(ball) + 'px', borderRadius: Math.round(ball / 2) + 'px' },
  ];
  const heldArt = [
    { translate: '0px 0px', width: Math.round(ball) + 'px', height: Math.round(ball) + 'px' },
    { translate: '0px 0px', width: Math.round(ball) + 'px', height: Math.round(ball) + 'px' },
  ];

  lockShape = [
    lockPill.animate(frames, { duration: millis, fill: 'forwards' }),
    lockPill.animate(held, { duration: millis, fill: 'forwards' }),
    lockMovers[0].element.animate(heldArt, { duration: millis, fill: 'forwards' }),
  ];

  lockShape[0].addEventListener('finish', () => openNowBall(to, art, ball, restRadius, backX, backY, opening));
  stirLiquid(millis + opening + 200);
}


// The ball has landed on the point the leave's draw let go from. This is that draw read backwards: the circle opens out into the bar, the cover walks back to its slot at full size rather than being scaled into it, and the reading grows back under it one block after another.
function openNowBall(to, art, ball, restRadius, backX, backY, millis) {
  // The shot is forwards-filled too, so returning here without landing leaves the bubble standing as the ball it flew in as.
  if (!lockArriving()) { landNowEnter(); return; }

  const opening = lockPill.animate(
    [
      { width: Math.round(ball) + 'px', borderRadius: Math.round(ball / 2) + 'px' },
      { width: Math.round(to.width) + 'px', borderRadius: restRadius },
    ],
    { duration: millis, easing: LOCK_SHOT_EASE, fill: 'forwards' }
  );

  lockShape.push(
    lockPill.animate(
      [
        { translate: backX.toFixed(1) + 'px ' + backY.toFixed(1) + 'px', scale: LOCK_DRAW_SQUEEZE },
        { translate: '0px 0px', scale: 1 },
      ],
      { duration: millis, easing: LOCK_POP_EASE, fill: 'forwards' }
    ),
    opening,
    lockMovers[0].element.animate(
      [
        { translate: '0px 0px', width: Math.round(ball) + 'px', height: Math.round(ball) + 'px' },
        {
          translate:
            ((art.left + art.width / 2) - (to.left + to.width / 2)).toFixed(1) + 'px ' +
            ((art.top + art.height / 2) - (to.top + to.height / 2)).toFixed(1) + 'px',
          width: Math.round(art.width) + 'px',
          height: Math.round(art.height) + 'px',
        },
      ],
      { duration: millis, easing: LOCK_SHOT_EASE, fill: 'forwards' }
    )
  );

  const sprigs = lockMovers.filter(mover => !mover.travels);
  sprigs.forEach((mover, index) => {
    mover.element.style.removeProperty('opacity');
    lockArrival.push(mover.element.animate(
      [
        { opacity: 0, translate: '0px ' + LOCK_LEAVE_DROP + 'px', scale: 0.94 },
        { opacity: 1, translate: '0px 0px', scale: 1 },
      ],
      {
        duration: LOCK_SPRIG_MS,
        delay: millis + index * LOCK_SPRIG_STAGGER,
        easing: LOCK_POP_EASE,
        fill: 'backwards',
      }
    ));
  });

  clearTimeout(lockTypeTimer);
  lockTypeTimer = setTimeout(typeLabels, millis + LOCK_SPRIG_STAGGER + LOCK_TYPE_IN);
  // The shape is handed back to the stylesheet when the shape has landed, not when the last line of the reading has finished growing in under it.
  opening.addEventListener('finish', landNowEnter);
  stirLiquid(millis + LOCK_SPRIG_MS + sprigs.length * LOCK_SPRIG_STAGGER + 200);
}

// It bailed here when `arriving` had already been taken off, and every animation in the journey is `fill: forwards` — so bailing left the cover standing at the translate and the size the flight had given it, which is a picture measured against a bubble that was centred in the ball and is now a row. That is the artwork hanging outside the pill: nothing was mid-flight, the flight had simply never been let go of. Landing is idempotent instead, so the last thing that happens on this journey is always the stylesheet getting its properties back.
function landNowEnter() {
  lockShape.forEach(move => move.cancel());
  lockShape = [];
  lockPill.classList.remove('arriving', 'circling');
  lockMovers.forEach(mover => mover.element.style.removeProperty('opacity'));
  lockRest = null;
  fitLockProxy();
}

// The window was placed off a box that was still easing towards its size: the retract and the open both fit the proxy on the spot and again when `settleLock`'s height animation finished, and the width under it is a 340ms CSS transition that finishes on the same frame or a shade later. So the compact bubble's window kept a width from part-way through the change while the bubble itself is centred — the coverage that was left of it sat over the left of the pill, and the whole right half of a bubble that was plainly there answered nothing. The box says when it has stopped.
lockPill.addEventListener('transitionend', event => {
  if (event.target !== lockPill) return;
  if (event.propertyName !== 'width' && event.propertyName !== 'height') return;
  fitLockProxy();
});
