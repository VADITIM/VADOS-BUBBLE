import { catchInto, paintLiquidFrame, releaseCatch, stirLiquid, traceEvent } from './liquid.js';
import { isStolen } from './lock.js';
import { paintAvatar, paintCall } from './mods/call.js';
import { carryArt, mediaWindow, paintMedia, runBars } from './mods/media.js';
import { paintTimer, timerWindow } from './mods/timer.js';
import { endHold } from './motion.js';
import { fitNowWidth } from './now.js';
import { BUMP_MAX, BUMP_MIN, CHIP_ROOM, CLOSED, MOD_WIDTH, SAT_GAP, SAT_GAP_ASIDE, SIZES, SPLIT_SHRINK, SWAP_DISTANCE, SWAP_FLICK, SWAP_STILL, bridge, faces, mods, pill, root, shared } from './state.js';

export function showFace(name) {
  for (const [key, element] of Object.entries(faces)) {
    element.classList.toggle('showing', key === name);
  }
}

/**
 * The viewport never changes — the WebView is a fixed-size stage the host window
 * only clips — so this waits for nothing but the window's own next frame, which
 * is all that is needed for the growth not to be clipped on its first frame.
 */
function afterRelayout(work) {
  // One frame, not two. The viewport genuinely never changes — the stage is a
  // fixed size the host window only clips — so nothing here is waiting for a
  // re-layout, only for the window to have been given its new clip. The second
  // frame was insurance, and it was insurance paid at the exact moment a growth
  // is meant to start, which is where it was read as a stall.
  requestAnimationFrame(work);
}

function paintSize() {
  pill.classList.toggle('alerting', shared.size === 'alert' || shared.size === 'image');
  pill.classList.toggle('expanded', shared.size === 'haptic');
  pill.classList.toggle('picture', shared.size === 'picture');
  pill.classList.toggle('history', shared.size === 'history');
  pill.classList.toggle('playing', shared.size === 'playing');
  pill.classList.toggle('player', shared.size === 'player');
  pill.classList.toggle('timing', shared.size === 'timing');
  pill.classList.toggle('calling', shared.size === 'calling');

  pill.classList.toggle('battery', shared.size === 'battery');
  pill.classList.toggle('timer', shared.size === 'timer');

  // The skin only exists for the closed row: a grown panel is one shape with
  // nothing to merge into, so it paints its own background and the filter stops.
  root.classList.toggle('liquid', CLOSED.has(shared.size));
  if (!CLOSED.has(shared.size)) {
    // Opening is a growth, not a split, and must not inherit the split's curve or
    // the wait a departing satellite left behind.
    root.style.setProperty('--width-ease', 'var(--ease-grow)');
    root.style.setProperty('--width-ms', 'var(--grow-ms)');
    root.style.setProperty('--width-delay', '0ms');
    root.style.setProperty('--face-delay', '0ms');
    root.style.setProperty('--face-fade', '0ms');
  }
  stirLiquid();
  paintShift();
}

/**
 * How far right the closed row has been pushed, which is a layout rule and nothing
 * to do with the glass any more — the glass is mirrored off what this ends up
 * drawing. An open bubble owns the middle of the window on its own, so whatever
 * offset the closed pair left behind has to go or it opens off the cutout.
 */
export function paintShift() {
  root.style.setProperty(
    '--shift', (CLOSED.has(shared.size) ? layout().mainX : 0) + 'px'
  );
  stirLiquid();
}

/** The states the bubble rests in: bare, or under a mod. */

export const satellites = {
  right: document.getElementById('satellite-right'),
  left: document.getElementById('satellite-left'),
};
const dotGroups = {
  right: document.getElementById('dots-right'),
  left: document.getElementById('dots-left'),
};

/**
 * The closed row, as places rather than as cases. Whatever is true takes the next
 * place outwards from the middle:
 *
 *   … dot dot [satellite] (bubble) [satellite] dot dot …
 *
 * The bubble is whatever leads the order, the next two get a circle a side, and
 * everything after that is counted with a dot on alternating sides. Adding a fifth
 * kind of mod needs nothing here — it is the length of the list that decides.
 */
const DOT_SIZE = 6;
const DOT_GAP = 5;

/** The mods that get a dot rather than a circle, nearest the bubble first. */
function dotted() {
  const spread = { right: [], left: [] };
  // Whatever is past the circles the row is allowed. With the Now bubble out that is
  // one circle rather than two, so the mod that had been the far satellite becomes the
  // first dot — counted onto the satellite's own side, because with one circle there
  // is no other side to alternate onto.
  const limit = satelliteLimit();
  liveMods().slice(1 + limit).forEach((mod, index) => {
    spread[limit > 1 && index % 2 !== 0 ? otherSide(satSide) : satSide].push(mod);
  });
  return spread;
}

/** The room one side needs for its dots, the gap to the circle included. */
function dotRoom(count) {
  return count ? DOT_GAP + count * DOT_SIZE + (count - 1) * DOT_GAP : 0;
}

/** What each mod looks like once it is a circle of its own. */
const SATELLITE_FACES = {
  // The album art, not the bars: what left the bubble has to be what turns up in
  // the circle, or the eye has no reason to believe they are the same thing.
  media: '<img class="sat-art glyph" alt="">',
  timer: '<div class="sat-clock"></div>',
  call: '<div class="sat-avatar avatar"></div>',

};

/** Which side the satellite hangs off. A committed swap puts it on the other. */
let satSide = 'right';

/** How far a drag has traded the bubble's width for the satellite's, 0 to 1. */
let swap = 0;

/** Which of the two satellites the drag is pulling in: 1 the near one, -1 the far. */
let swapDir = 1;

/** True for the one frame between a swap being committed and the pair settling. */
let settling = false;

/**
 * How far the wrapping satellite is shoved past its slot before it changes sides.
 * Half its own width, which is exactly the room the window has left over — and it
 * is fully faded by the time it gets there, so the edge never clips anything seen.
 */
const FAR_PUSH = 17;

/**
 * How far the two shapes press *into* each other at the middle of a trade, past
 * touching. The row's geometry gives the gap no reason to change on its own — the
 * bubble gives up exactly the width the satellite takes, so the distance between
 * their centres is constant and the skin has nothing to melt — and the whole liquid
 * hand-over only ever appeared on the settle, after the finger had already gone.
 * This is what makes the merge happen under the finger instead: they close, fuse
 * through the middle, and let go again as the trade finishes.
 */
const SWAP_PRESS = 5;

/** True once the drag is past the point where letting go would commit it. */
let swapPassed = false;

/** How fast the finger is dragging the row, in pixels per millisecond, smoothed. */
let swapSpeed = 0;
let swapTravelled = 0;
let swapMovedAt = 0;

/** Whether a mod is actually true right now, not merely remembered. */
export function isLive(mod) {
  if (mod === 'timer') return mods.has('timer') && Boolean(shared.timer);
  if (mod === 'media') return mods.has('media') && Boolean(shared.media);
  if (mod === 'call') return mods.has('call') && Boolean(shared.call);

  return false;
}

/** Whatever payload a mod is carrying, so the satellites can read them alike. */
function payloadOf(mod) {
  if (mod === 'media') return shared.media;
  if (mod === 'timer') return shared.timer;

  return shared.call;
}

/** The mods that are true, in the order a swipe has left them in. */
export function liveMods() {
  return shared.modOrder.filter(mod => isLive(mod) && !isStolen(mod));
}

/** The mod's own colour, so a satellite keeps the app's identity beside the bubble. */
function accentOf(mod) {
  const payload = payloadOf(mod);
  return (payload && payload.accent) || 'var(--section-color)';
}

/** A satellite at rest is a circle: exactly as tall as the bubble, and as wide. */
function satSize() {
  return shared.compact.height;
}

/**
 * Where a circle stands when the glyph it holds is exactly the bubble's glyph.
 * Both boxes carry the same inset at the same end, so that is simply their left
 * edges flush — and from there the picture can be handed from one to the other
 * without moving, which is the whole trick behind a split reading as one thing
 * leaving rather than two things being drawn.
 */
function glyphAligned(box) {
  return box.mainX - box.main / 2 + satSize() / 2;
}

/**
 * True while the Now bubble is standing out on the bar.
 *
 * It used to mean one small thing — the closed bubble gave up a little width so that
 * two pills fitted on one status bar — and it means something larger now: the bar is
 * the Now bubble's as well, so the row stands aside for it rather than shaving
 * itself. Everything below reads this: where the row stands, how many circles it is
 * allowed, and how wide the Now bubble may grow.
 */
let chipOut = false;

/** How long the row takes to stand aside for the light, or to come back. */
const SHIFT_HOME = 100;
/** True while that move is the one being painted, so the ordinary curve stays out of it. */
let shiftingHome = false;
let shiftTimer = null;

window.onTorchChip = out => {
  if (chipOut === out) return;
  chipOut = out;
  // Standing aside is not a growth and does not take a growth's time. The row is not
  // changing shape here — it is the same row in a different place — and at the growth's
  // length it read as the bubbles wandering back rather than snapping into the space
  // the light had just left. No delay either: the delay exists to hold a step aside
  // until a width has come in, and there is no width moving here.
  shiftingHome = true;
  clearTimeout(shiftTimer);
  shiftTimer = setTimeout(() => { shiftingHome = false; }, SHIFT_HOME + 40);
  // The row's whole shape depends on this — its place, its satellites, its dots — so
  // it is repainted rather than nudged, and the glass and the window go with it.
  // Nothing here is worked out by the CSS: the glass is a region of the host's window
  // and the proxy is a window of its own, and both keep whatever they were last told.
  if (shared.state === 'idle') toClosed();
  else {
    paintSatellites();
    ensureClosedWindow();
  }
  paintShift();
};

/**
 * How far right the closed row stands. Nothing while the bar is the row's own; with
 * the Now bubble out, exactly enough to bring the bubble's left edge to the punch
 * hole — which is the bare bubble's left edge, since the bare bubble is the one drawn
 * around the hole. A mod then grows to the right from the hole instead of out either
 * side of it, and everything hanging off the bubble travels with it.
 */
/**
 * The camera's own hole, plus the margin nothing is allowed inside. Measured off the
 * phone rather than mirrored from anywhere: it is a hole in the glass.
 */
const HOLE_WIDTH = 34;

function rowShift(resting) {
  // A bare bubble has no glyph to protect and is the bubble drawn around the hole in
  // the first place, so it does not move at all — it is the mods that are pushed.
  if (!chipOut || !liveMods().length) return 0;
  // Mirrors --mod-glyph and --glyph-inset in the CSS above, which are the same two
  // numbers off the same bubble height.
  const glyph = shared.compact.height - 10;
  const inset = (shared.compact.height - glyph) / 2 - 1;
  // The left edge that stands the glyph's far side exactly on the hole's near one:
  // the mod's icon is the first thing in the bubble, and it is what the hole eats.
  const clear = window.innerWidth / 2 - HOLE_WIDTH / 2 - glyph - inset + CHIP_ROOM;
  return Math.max(0, clear - (window.innerWidth / 2 - resting / 2));
}

/** The closed row's left edge, which is what the Now bubble grows up to. */
export function rowLeftEdge() {
  const resting = restingMain();
  return window.innerWidth / 2 - resting / 2 + rowShift(resting);
}

/**
 * How many circles may stand beside the bubble. One while the Now bubble is out: the
 * bar it has left is not wide enough for two, and a row that overflows into the punch
 * hole is worse than a row that counts.
 */
function satelliteLimit() {
  return chipOut ? 1 : 2;
}

/** How far the circle stands off the bubble, which depends on how much bar there is. */
function satGap() {
  return chipOut ? SAT_GAP_ASIDE : SAT_GAP;
}

function restingMain() {
  // Mid-merge the bubble is the bare closed bubble and nothing else, whatever is
  // still true: the mod that is coming has not arrived yet, and the bubble must
  // not already be the width of something it is not yet carrying.
  if (mergeHold) return shared.compact.width;
  const live = liveMods();
  if (!live.length) return shared.compact.width;
  return shared.compact.width + MOD_WIDTH + 4 - (live.length > 1 ? SPLIT_SHRINK : 0);
}

/**
 * Where everything sits right now, drag included.
 *
 * The two widths trade — whatever the bubble gives up the satellite takes — and
 * the centre travels with them: whichever of the two is currently the big one sits
 * on the cutout, so a swap is one thing sliding into the middle while the other
 * retreats to the side it shrank towards. The distance between their centres works
 * out constant, so the gap never breathes.
 */
function layout() {
  const live = liveMods();
  const resting = restingMain();
  const push = rowShift(resting);
  if (live.length < 2) {
    return {
      main: resting, sat: satSize(), mainX: push, satX: push,
      inSide: satSide, farX: push, farFade: 1, edge: 0, reach: 0, push,
    };
  }
  const traded = (resting - satSize()) * swap;
  // Which satellite the drag is pulling into the middle. With one satellite it can
  // only ever be that one; with both sides taken the finger picks, and the side it
  // picked is the side everything else is measured from.
  // Only a drag in progress can move it off the resting side. A direction left over
  // from a drag that was abandoned used to survive into the next paint, and the two
  // circles were drawn on each other's sides while nothing was being dragged at all.
  const inSide = live.length > 2 && satelliteLimit() > 1 && swapDir < 0 &&
    root.classList.contains('dragging')
    ? otherSide(satSide)
    : satSide;
  const edge = inSide === 'right' ? 1 : -1;
  // Half of both widths plus the gap: the same whichever of them is the big one,
  // which is why this is a constant and not a function of the drag.
  const reach = (resting + satSize()) / 2 + satGap();
  // Which is why the pair is pressed together on top of it: a peak at halfway, zero
  // at both ends, so the gap closes to an overlap as the trade goes through and is
  // back to the resting gap by the time it is done. This is the only thing in the
  // row that moves the two shapes relative to each other, and it is what the skin
  // melts on. Positions only — reach itself stays the constant the dots are hung off.
  const pressed = reach - (satGap() + SWAP_PRESS) * Math.sin(swap * Math.PI);
  // Past halfway the swap is decided, so the far one is drawn where it will land.
  const crossed = swap > 0.5;
  const shoved = crossed ? (1 - swap) * 2 : swap * 2;
  return {
    main: resting - traded,
    sat: satSize() + traded,
    mainX: push - edge * swap * pressed,
    satX: push + edge * (1 - swap) * pressed,
    inSide,
    push,
    // The one on the far side, when a third mod is true. It is the only piece that
    // has to end up at the other end of the row, and a circle flying the whole way
    // across the bubble to get there reads as a glitch. So it is shoved further out
    // by the bubble arriving over it, fading as it goes, and is already gone by the
    // halfway mark — where it changes sides and comes back in from the other edge.
    farX: push + (crossed ? edge : -edge) * (reach + FAR_PUSH * shoved),
    farFade: Math.min(1, Math.abs(swap - 0.5) * 2),
    edge,
    reach,
  };
}

/** The side a satellite is not on. */
function otherSide(side) {
  return side === 'right' ? 'left' : 'right';
}

/**
 * The window a closed bubble needs. With a satellite it is symmetric and never
 * moves: it holds the bubble centred plus a satellite's worth of room on *both*
 * sides, because the pair slides across it during a drag and a window that had to
 * follow that would be resized under the finger — which cancels the touch.
 */
export function closedTarget() {
  const box = layout();
  const live = liveMods();
  const spread = dotted();
  // Symmetric, and sized by whichever side is carrying more: the row slides across
  // this window during a drag, and a window that had to be resized under the finger
  // cancels the touch.
  const counted = Math.max(dotRoom(spread.right.length), dotRoom(spread.left.length));
  const room = live.length > 1 ? 2 * (satSize() + satGap() + counted) : 0;
  return {
    width: restingMain() + room,
    height: -1,
    // The row stands aside for the Now bubble, and every pixel of it that can be
    // touched is in this window — so the window stands aside too, or the bubble is
    // being aimed at through a hole in the wrong place.
    shift: box.push,
    // What the bubble itself is drawing, which the blur follows.
    main: box.main,
  };
}

/**
 * The mods that do not own the bubble, as circles beside it. Only one can be
 * beside it so far; a third mod is the moment to draw dots rather than to invent
 * a third side.
 */
export function paintSatellites() {
  // The bar left of the bubble belongs to the Now bubble while it is out, so a
  // satellite that a swap has left standing there moves over. It is not hidden and it
  // is not forbidden — it simply has one side to be on.
  if (chipOut && satSide === 'left') satSide = 'right';
  layoutSatellites();
  fitNowWidth();
  stirLiquid();
  // Under a finger the skin cannot be a frame behind what it is the skin of: the
  // content would slide out of its own fill. Everything else has a transition to
  // follow and is mirrored by the loop.
  if (root.classList.contains('dragging')) paintLiquidFrame();
}

function layoutSatellites() {
  const live = liveMods();
  const box = layout();
  stageSplit(live.length);
  // Mid hand-off this box is not the one to paint: swap is already back at 0 and
  // satSide already flipped, so it is the row's full-width, dead-centre resting
  // shape — correct for where things are going, wrong for where the hand-off has
  // actually stood them. commitTrade/commitRotation paint the real one right
  // after toClosed() returns; painting this one first is the split-second flash.
  if (!settling) {
    root.style.setProperty('--main-width', box.main + 'px');
    root.style.setProperty('--shift', box.mainX + 'px');
    // Under a finger there is no sequence to stage: everything tracks the drag. Nor
    // after a swap, and that one cost a visible bug — the delay exists for a split,
    // where the bubble narrows first and only then steps aside, but a swap has
    // already put both boxes where they belong. Holding the step aside for another
    // 120ms left the new bubble standing at the width it had just taken, off its own
    // centre, for exactly that long before it slid over: read as the bubble being
    // wider on one side for a moment and then correcting itself.
    root.style.setProperty(
      '--shift-delay',
      root.classList.contains('dragging') || shiftingHome ? '0ms' : '120ms'
    );
    root.style.setProperty(
      '--shift-ms', shiftingHome ? SHIFT_HOME + 'ms' : 'var(--grow-ms)'
    );
    // The circle travels on the row's clock too, and this is the whole of that bug:
    // standing aside for the light is one move of one row, but the bubble took it as
    // a shift (100ms, no delay) while the circle took it as a split (150ms of delay
    // and then 380ms of travel) — so the bubble had arrived and stopped before its
    // own satellite had started. Nothing is splitting here: the two are in the same
    // place relative to each other before and after, so they move on the same
    // numbers. A circle already flying home keeps its own, which clearSatellite gave
    // it and which are the merge's, not the row's.
    Object.values(satellites).forEach(element => {
      if (element.dataset.leaving) return;
      element.style.setProperty('--split-ms', shiftingHome ? SHIFT_HOME + 'ms' : '');
      element.style.setProperty('--split-delay', shiftingHome ? '0ms' : '');
    });
    // The skin and the glass are both mirrored off these boxes, so the only thing
    // left to say is that they are about to move.
    stirLiquid();
  }
  paintDots(box);

  // One satellite and two are different things, not the same thing with a count.
  // With one it is a trade between two bubbles and the sides change hands; with two
  // the sides are fixed and the row rotates through them, which is a third motion —
  // the wrap — that has no meaning at all in the pair. They were one loop with a
  // flag in it, and the flag was read wrong in the case that happens most.
  if (live.length < 2) {
    clearSatellite('right');
    clearSatellite('left');
    return;
  }
  // One circle, whether that is because one mod is beside the bubble or because the
  // Now bubble is out and the row is allowed only one. The far one is not hidden: it
  // is aimed at the bubble and run into it, which is what clearSatellite does, and the
  // bubble takes the knock. What it turns into is the dot paintDots has already drawn.
  if (live.length === 2 || satelliteLimit() === 1) {
    clearSatellite(otherSide(satSide));
    paintTrader(satellites[satSide], live[1], box);
    return;
  }
  const inSide = box.inSide;
  paintTrader(satellites[inSide], live[inSide === satSide ? 1 : 2], box);
  paintWrapper(satellites[otherSide(inSide)], live[inSide === satSide ? 2 : 1], box);
}

/**
 * A mod leaving is not the split played backwards at the same speed. Going out,
 * the bubble lets go and the piece it dropped shoots away — quick, and read at a
 * glance. Coming back, three things have to be seen in order, and each of them is
 * the cause of the next: the circle travels into the bubble, the bubble takes it
 * in, and only then does it open out around what it has just swallowed. Played at
 * the speed of the split all three land on top of each other and it reads as one
 * box resizing for no reason.
 */
/**
 * The circle's run into the middle of the bubble. Long enough to be the visible
 * cause of what follows, and no longer: the bare closed bubble is a moment the
 * merge passes through, not a state it stops in.
 */
const MERGE_TRAVEL = 260;

/**
 * The bubble opening out around what has arrived. The circle is still holding the
 * glyph through all of it and travels with the edge, so this is one number for
 * both of them.
 */
const MERGE_EXPAND = 300;

/**
 * A mod taking the bubble arrives as its glyph: up out of the middle, small
 * enough to sit inside the camera hole, landing on the left with the split's
 * overshoot. Only then is there something at the edge for the bubble to open out
 * around.
 */
const MOD_ENTER = 300;

/**
 * Where in the arrival the glyph is actually at the left edge, as a share of the
 * whole thing. Not the end of it: the split curve overshoots by a good half of
 * its travel and then settles back, so the glyph is furthest left about a third
 * of the way in and spends the rest of the time coming to rest.
 *
 * Waiting for the animation to *finish* is what read as a pause between two
 * movements — the icon shot out, stopped, and only then did the bubble notice.
 * The bubble opens out at the moment the glyph reaches the wall, which is also
 * the moment it is pushing hardest on it, so the two are one movement.
 */
const LAND = 0.38;

/**
 * True while the bubble is the bare closed bubble waiting for a circle to reach
 * it. A mod stopping is not the bubble changing shape: the mod closes, the bubble
 * is left closed and bare, and it becomes a mod bubble again only because
 * something arrived and made it one.
 */
let mergeHold = false;
let mergeTimer = null;

/** Set for the one paint that is the bubble opening out around what it took in. */
let takingIn = false;

/** How many mods were true the last time the row was drawn. */
let lastLive = 0;

/**
 * A width change in the closed row is a split or a merge, never a growth, so it
 * takes the split's own curve — the same overshoot the satellite leaves on, which
 * is what makes the pair read as one thing letting go rather than two boxes
 * resizing. A width change with the same mods still true is neither, and keeps
 * the plain curve: an alert collapsing back should not bounce.
 */
function stageSplit(count) {
  const changed = count !== lastLive;
  const leaving = count < lastLive;
  lastLive = count;
  // The second half of a merge: the bubble is not changing its mind about how many
  // mods are true, it is opening out around the one that has just arrived.
  const arriving = takingIn;
  takingIn = false;
  const moving = changed || arriving;
  root.style.setProperty('--width-ease', moving ? 'var(--ease-split)' : 'var(--ease-grow)');
  root.style.setProperty(
    '--width-ms',
    arriving ? MERGE_EXPAND + 'ms' : leaving ? '240ms' : changed ? '380ms' : 'var(--grow-ms)'
  );
  // The order is sequenced in JS now, one visible cause at a time, so no part of
  // it waits on a delay of its own.
  root.style.setProperty('--width-delay', '0ms');
  // The mod that has stopped being true takes its face off the bubble at once —
  // it is the first thing that happens and the reason for everything after it.
  root.style.setProperty('--face-fade', arriving ? '260ms' : leaving ? '140ms' : '0ms');
}

/**
 * A mod that has stopped being true does not blink out where it stands, and it
 * does not fly back to an edge either. It goes to the one place its glyph has a
 * right to be — the bubble's own glyph slot, left edges flush — because from
 * there the picture can simply be handed over: the circle stops drawing it and
 * the bubble starts, in the same place, at the same size, on the same frame.
 *
 * The inline geometry is what carries it there, so it is kept for the whole
 * journey and cleared at the end rather than at the start: wiping it first left
 * the circle to vanish on the spot while the bubble grew for no visible reason.
 */
function clearSatellite(side) {
  const element = satellites[side];
  if (!element.dataset.mod || element.dataset.leaving) return;
  // A swap is not a mod ending. The circle is vacated because the mod it was
  // holding has just become the bubble — which is already drawing that same glyph,
  // in its own slot, this very frame. Flown home the way a departing mod is, it
  // spends the whole merge as a second copy of the icon crawling into the first:
  // two timers, two avatars. Nothing is leaving here, so nothing travels.
  if (settling) {
    dropSatellite(element);
    return;
  }
  element.dataset.leaving = '1';
  traceEvent(
    'satellite ' + side + ' sent home, mods=' + liveMods().length +
    ', limit=' + satelliteLimit() + ', mergeHold=' + mergeHold
  );
  // Whatever it was doing, it has stopped being true — the bars and the sweeping
  // hand are claims about something that is no longer happening.
  element.classList.remove('live');
  element.style.setProperty('--split-ms', MERGE_TRAVEL + 'ms');
  element.style.setProperty('--split-delay', '0ms');
  aimHome(element);
  stirLiquid(MERGE_TRAVEL + MERGE_EXPAND + 200);
  // The travel is a transition; everything the bubble does about it is measured off
  // where the circle has actually got to. It is retired when it is inside and cannot
  // be seen going — whether the bubble is opening out around it or simply going bare,
  // which is the difference that used to need two different timers.
  catchInto(element, pill, { swallowed: retireLeaving });
}

/** Onto the bubble's glyph slot, wherever the bubble currently has it. */
function aimHome(element) {
  const target = glyphAligned(layout()) + 'px';
  element.style.setProperty('--sat-x', target);
  element.style.setProperty('--sat-birth', target);
}

/**
 * The second leg: the bubble is opening out and its glyph slot travels left with
 * its edge, so the circle still carrying that glyph travels with it, on the same
 * clock and the same curve. They arrive together because they are the same move.
 */
function trackExpansion() {
  for (const side of ['right', 'left']) {
    const element = satellites[side];
    if (!element.dataset.leaving) continue;
    element.style.setProperty('--split-ms', MERGE_EXPAND + 'ms');
    aimHome(element);
  }
}

/** Off, on the spot, holding nothing — no journey, no retirement to wait for. */
function dropSatellite(element) {
  releaseCatch(element);
  element.classList.remove('showing', 'live');
  delete element.dataset.leaving;
  element.removeAttribute('style');
  delete element.dataset.mod;
  element.innerHTML = '';
}

/** The hand-over itself: the circle stops holding the glyph and the bubble has it. */
function retireLeaving() {
  root.classList.remove('handing-over');
  for (const side of ['right', 'left']) {
    const element = satellites[side];
    if (!element.dataset.leaving) continue;
    dropSatellite(element);
  }
  stirLiquid(300);
}

/** Whatever the circle is holding: the face, its colour, and whether it is moving. */
function dressSatellite(element, mod) {
  // Caught on its way home and sent back out: it is not being taken in any more, so
  // the merge watching it is called off and the sweep that would have retired it
  // skips whatever is no longer marked as leaving.
  releaseCatch(element);
  delete element.dataset.leaving;
  const fresh = element.dataset.mod !== mod;
  if (fresh) {
    element.dataset.mod = mod;
    element.innerHTML = SATELLITE_FACES[mod];
    // Brand new bars, which no swing is driving yet.
    runBars();
  }
  // The avatar is data, not markup, so it is set every paint rather than baked into
  // the face: the same circle carries whoever the call is with now.
  if (mod === 'call') {
    const avatar = element.querySelector('.sat-avatar');
    if (avatar) paintAvatar(avatar);
  }
  if (mod === 'media') {
    const art = element.querySelector('.sat-art');
    if (art && shared.media) art.src = shared.media.artBase64 || '';
  }
  // Its own app's colour, not the bubble's: the accent variable follows whichever
  // mod owns the bubble, and a Spotify circle beside a timer must stay green.
  element.style.setProperty('--app-accent', accentOf(mod));
  // The animation is the truth about the thing, exactly as it is in the bubble:
  // bars move while sound plays, the hand sweeps while the timer runs.
  element.classList.toggle(
    'live',
    mod === 'media' ? Boolean(shared.media.isPlaying)
      : mod === 'timer' ? Boolean(shared.timer.endsAt)
      : true
  );

  return fresh;
}

/**
 * The circle the bubble is trading width with. It is the only one that changes size
 * — it is taking what the bubble gives up — and it tracks the finger the whole way.
 */
function paintTrader(element, mod, box) {
  const fresh = dressSatellite(element, mod);
  element.style.opacity = '';
  element.style.setProperty('--sat-width', box.sat + 'px');
  element.style.setProperty('--sat-x', box.satX + 'px');
  element.style.setProperty('--sat-birth', glyphAligned(box) + 'px');
  reveal(element, fresh);
}

/**
 * The circle that is not in the trade: it keeps its size, gets shoved further out as
 * the bubble arrives over it, and changes sides while it is invisible.
 */
function paintWrapper(element, mod, box) {
  const fresh = dressSatellite(element, mod);
  element.style.opacity = root.classList.contains('dragging') ? String(box.farFade) : '';
  element.style.setProperty('--sat-width', satSize() + 'px');
  element.style.setProperty('--sat-x', box.farX + 'px');
  element.style.setProperty(
    '--sat-birth', (box.mainX - (box.edge * box.main) / 2) + 'px'
  );
  reveal(element, fresh);
}

/**
 * A circle that has just been given a place has to be *put* there before it is told
 * to show, or it transitions in from wherever the last one it held left it — which
 * is the width and the side of a different mod, and reads as the wrong bubble
 * sliding out of the wrong edge.
 */
function reveal(element, fresh) {
  if (fresh) void element.offsetWidth;
  element.classList.add('showing');
}

/**
 * The counted rest of the row: one dot per mod, outside the circle on its side, in
 * that mod's own colour. They sit at a fixed remove from the satellite and stay
 * there through a drag — a dot is a count, and the count does not change while the
 * row rotates.
 */
/** How many dots each side was last drawn with, so an arrival can be told from a repaint. */
const dotsSeen = { right: 0, left: 0 };

function paintDots(box) {
  const spread = dotted();
  for (const side of ['right', 'left']) {
    const group = dotGroups[side];
    const mods = spread[side];
    group.textContent = '';
    if (!mods.length) {
      group.classList.remove('showing');
      dotsSeen[side] = 0;
      continue;
    }
    for (const mod of mods) {
      const dot = document.createElement('span');
      dot.style.background = accentOf(mod);
      // The group is rebuilt on every paint, so a plain CSS animation on the element
      // would replay on every drag frame. Only a count that has grown is an arrival.
      if (mods.length > dotsSeen[side]) dot.classList.add('arriving');
      group.appendChild(dot);
    }
    dotsSeen[side] = mods.length;
    const edge = side === 'right' ? 1 : -1;
    const width = mods.length * DOT_SIZE + (mods.length - 1) * DOT_GAP;
    const start = box.reach + satSize() / 2 + DOT_GAP;
    group.style.setProperty(
      '--dots-x', (box.push + edge * (start + width / 2)) + 'px'
    );
    group.classList.add('showing');
  }
}

/**
 * The drag itself: dragging away from the satellite pulls it into the middle, the
 * way a carousel moves — the finger drags the row, not the thing it wants. The
 * bubble gives up width as the satellite takes it, live under the finger, and
 * nothing is committed until the finger lifts.
 */
export function dragSwap(travelled) {
  const live = liveMods();
  if (live.length < 2) return false;
  // The finger's speed, kept while it is down so the release has something to throw
  // the glyph with. Smoothed, because a single touchmove pair is mostly noise — one
  // slow frame in the middle of a flick would otherwise decide the whole knock. The
  // travel is measured raw rather than off swap, so a drag that changes its mind and
  // comes back through the middle still reads as fast.
  const now = performance.now();
  if (!root.classList.contains('dragging')) {
    swapSpeed = 0;
    swapMovedAt = 0;
  }
  if (swapMovedAt && now > swapMovedAt) {
    const rate = Math.abs(travelled - swapTravelled) / (now - swapMovedAt);
    swapSpeed = swapMovedAt ? swapSpeed * 0.6 + rate * 0.4 : rate;
  }
  swapMovedAt = now;
  swapTravelled = travelled;
  // Away from a satellite pulls it in. With both sides taken either one can be
  // pulled, so the finger's direction picks which — and it may change its mind, as
  // long as it comes back through the middle first.
  const towards = satSide === 'right' ? -travelled : travelled;
  let progress = swapDir > 0 ? towards : -towards;
  if (progress < 0 && live.length > 2) {
    swapDir = -swapDir;
    progress = -progress;
  }
  swap = Math.min(1, Math.max(0, progress / SWAP_DISTANCE));
  // The buzz belongs to the moment the trade is earned, not to the moment the finger
  // happens to leave: a threshold the user cannot feel until afterwards is a
  // threshold they have to guess at. It fires in both directions — crossing back is
  // just as much news — and once per crossing, so a finger resting on the line does
  // not rattle.
  const past = swap > 0.5;
  if (past !== swapPassed) {
    swapPassed = past;
    bridge.triggerHaptic('tap');
    // Past the line the trade has been earned, and a hold must not still be counting
    // underneath it: a finger that keeps the row a moment too long after the swap has
    // visibly happened used to be answered with a panel opening on top of it. The
    // hold outranks the drag right up to the threshold and not one pixel past it —
    // after that this finger has already said what it wanted.
    if (past) endHold();
  }
  root.classList.add('dragging');
  paintSatellites();
  return swap > 0.04;
}

/**
 * Past halfway the satellite has earned the bubble.
 *
 * The two are drawn by different elements — one bubble, one satellite — so the
 * swap is a hand-off: each takes over exactly where the other one is, with the
 * transitions still off, so not a pixel moves at the moment they change roles. The
 * satellite ends up on the side the old bubble retreated to, because that is where
 * it visibly went; sending it back across would be the jump this replaces. Only
 * then are the transitions let go, and the pair settles into rest.
 */
/**
 * The drag lets go of the row without trading, because the hold has taken the touch
 * off it. Not releaseSwap: that one asks whether the finger got far enough and hands
 * the bubble over if it did, and a swap committing behind a panel that is opening
 * over it is two answers to one finger.
 */
export function abandonSwap() {
  if (!root.classList.contains('dragging')) return;
  root.classList.remove('dragging');
  swap = 0;
  swapDir = 1;
  swapPassed = false;
  paintSatellites();
}

export function releaseSwap() {
  if (!root.classList.contains('dragging')) return;
  const committed = swap > 0.5;
  swapPassed = false;
  // A finger that came to a stop and then lifted let the row go; whatever it was
  // doing a moment before that is not what it handed over with.
  if (performance.now() - swapMovedAt > SWAP_STILL) swapSpeed = 0;
  if (!committed) {
    root.classList.remove('dragging');
    swap = 0;
    swapDir = 1;
    paintSatellites();
    return;
  }

  const box = layout();
  const flanked = liveMods().length > 2;
  const dir = swapDir;
  // The side the incoming satellite is standing on, read before the commit swaps
  // the names over. With two satellites the finger picked one; with one there is
  // only the one it can be.
  enterFrom(flanked ? box.inSide : satSide);
  swap = 0;
  swapDir = 1;
  // Set before the commit, not after: toClosed() runs inside it and repaints the
  // row on its own account, with swap already back at 0 and satSide already
  // flipped — full width, dead centre, the state the row is headed for but is
  // not at yet. That write reached the glass a beat before commitTrade or
  // commitRotation handed it the real one, and the two native layout passes
  // landed as separate frames: a flash to full width before the correct, smaller,
  // offset one took over. Flagged settling, layoutSatellites leaves the row alone
  // and only the hand-off's own numbers are ever painted.
  settling = true;
  if (flanked) commitRotation(box, dir);
  else commitTrade(box);

  // The settle is one move, not a staged pair: the hand-off already stood both
  // boxes where the finger left them, so from here they only travel to rest.
  requestAnimationFrame(() => {
    root.classList.remove('dragging');
    // Cleared *before* the paint, never after. settling exists to stop this row
    // being painted at its destination while the hand-off is still standing it
    // where the finger left it — and the hand-off is over by the time this frame
    // runs, so leaving the flag up here suppressed the one paint that was supposed
    // to carry it home. The bubble then kept the part-way width and the sideways
    // offset the release froze it at, off the cutout, until some unrelated repaint
    // — a media tick, a timer second — happened along and fixed it by accident.
    // Which is exactly what "it sometimes rearranges itself" was.
    settling = false;
    paintSatellites();
  });
}

/**
 * The row moves on by one, and only the mods that are actually true take part: a
 * mod that is not live still holds a place in the order, and rotating the whole
 * list handed the bubble to something invisible — which looked like every second
 * swipe doing nothing at all. The dead ones keep their places behind the live row,
 * so the order they come back in is the order they were in.
 */
function rotate(dir) {
  const live = liveMods();
  if (live.length < 2) return;
  const spun = dir < 0
    ? [live[live.length - 1], ...live.slice(0, -1)]
    : [...live.slice(1), live[0]];
  shared.modOrder = [...spun, ...shared.modOrder.filter((mod) => !live.includes(mod))];
}

/**
 * One satellite: the two of them simply trade. The satellite becomes the bubble and
 * the bubble becomes the satellite, on the side it retreated to — that is visibly
 * where it went, and sending it back across would be the jump this replaces.
 *
 * They are drawn by different elements, so this is a hand-off: each takes over
 * exactly where the other one is, with the transitions still off, so not a pixel
 * moves at the moment they change roles.
 */
function commitTrade(box) {
  rotate(1);
  satSide = otherSide(satSide);

  // Whatever picture the circle was holding has to be in the bubble's own copy of
  // it before the bubble is asked to show it. See carryArt().
  carryArt();
  // Faces and window first, while nothing can animate.
  toClosed();

  root.style.setProperty('--main-width', box.sat + 'px');
  root.style.setProperty('--shift', box.satX + 'px');
  const arriving = satellites[satSide];
  arriving.style.setProperty('--sat-x', box.mainX + 'px');
  arriving.style.setProperty('--sat-width', box.main + 'px');

  // Nothing is said about the glass here. A swap is two boxes trading names without
  // moving a pixel, and it was exactly the case the old contract could not describe:
  // the host had to be told the settled geometry outright or it spent the settle as a
  // full-width pane around a bubble that was neither that wide nor in that place. The
  // mirror measures what is actually standing there, so a trade is nothing to it.
  stirLiquid();
}

/**
 * Two satellites: the sides are fixed and the row rotates through them. Which way it
 * rotates is which circle the finger pulled — the one being pulled in takes the
 * middle, the bubble takes the slot opposite it, and the third has already crossed
 * over while it was invisible.
 */
function commitRotation(box, dir) {
  rotate(dir);

  carryArt();
  toClosed();

  root.style.setProperty('--main-width', box.sat + 'px');
  root.style.setProperty('--shift', box.satX + 'px');
  // The bubble became the circle opposite the one it handed over to, and it is
  // already standing there — still at the width it shrank to, which is what it goes
  // on settling from.
  const stepped = satellites[otherSide(box.inSide)];
  stepped.style.setProperty('--sat-x', box.mainX + 'px');
  stepped.style.setProperty('--sat-width', box.main + 'px');
  // The one that crossed is already drawn on this side, mid fade-in.
  satellites[box.inSide].style.setProperty('--sat-x', (box.edge * box.reach) + 'px');

  // And the glass needs no telling, for the same reason it does not in a trade.
  stirLiquid();
}


/** What the window is showing right now, in the same units the sizes are given in. */
let windowWidth = shared.compact.width;
let deferredTarget = null;

export function applyWindow(target) {
  windowWidth = target.width < 0 ? shared.compact.width : target.width;
  bridge.setWindowBounds(
    target.width, target.height, target.rise || 0, Math.round(target.shift || 0)
  );
}

/** The window every size wants, including the closed ones the table has no row for. */
function windowFor(next, override) {
  if (override) return override;
  if (next === 'idle') return { width: -1, height: -1 };
  if (next === 'playing') return mediaWindow();
  if (next === 'timing') return timerWindow();
  if (next === 'calling') return closedTarget();
  return SIZES[next];
}

/**
 * Which comes first depends on the direction, and only on that. Growing, the
 * window has to be bigger before the bubble animates into it, or the first frames
 * are clipped — that was a mod arriving into a window still the bare width, drawn
 * cut off until the resize caught up. Shrinking, the bubble has to finish first,
 * or the collapse plays against a viewport it has already outgrown.
 */
export function setSize(next, override) {
  if (shared.size === next && !override) return;
  shared.size = next;
  const target = windowFor(next, override);
  const width = target.width < 0 ? shared.compact.width : target.width;

  if (width > windowWidth) {
    deferredTarget = null;
    applyWindow(target);
    afterRelayout(paintSize);
    return;
  }
  deferredTarget = target;
  paintSize();
  ensureClosedWindow();
}

/**
 * The window follows the bubble down at transitionend, but a state that ends
 * without a size transition — a released hold, a list closed at the same size —
 * would leave the window grown, and a grown window is dead space over the shade.
 */
/** Back to whatever closed is right now, which under a mod is not the bare size. */
/** The window the finished shrink was headed for, once the bubble is done moving. */
export function applyClosedWindow() {
  if (root.classList.contains('holding')) return;
  applyWindow(deferredTarget || windowFor(shared.size));
}

/**
 * The transition end is the real signal, but a state can end at the size it began
 * at — a released hold, a list closed at the same size — and then no transition
 * ever fires. This is the floor under that.
 */
export function ensureClosedWindow() {
  clearTimeout(shared.restoreTimer);
  // Past the longest thing that can still be moving: a satellite running home
  // takes its own split, and the bubble only starts opening out after it.
  shared.restoreTimer = setTimeout(applyClosedWindow, MERGE_TRAVEL + MERGE_EXPAND + 320);
}

/** Which mod the closed bubble was carrying the last time it settled. */
let lastOwner = null;

/** What closed looks like under each mod: what to paint, wear and grow to. */
const MOD_CLOSED = {

  timer: { paint: () => paintTimer(), face: 'timer', size: 'timing', room: () => timerWindow() },
  media: { paint: () => paintMedia(), face: 'media', size: 'playing', room: () => mediaWindow() },
  call: { paint: () => paintCall(), face: 'call', size: 'calling', room: closedTarget },
};

/**
 * Which side the mod is arriving from, which decides how its glyph gets there.
 *
 * 'hole' is a mod taking a bare bubble: it comes up out of the middle, because that
 * is the punch hole and everything here is born there. A side is a mod arriving off a
 * swipe, which did not come from the middle at all — it came from the satellite the
 * finger pulled in, so it is knocked on from where it already stands and the side
 * says which way.
 */
let enterSide = 'hole';
function enterFrom(side) {
  enterSide = side;
}

/** Plays the glyph's arrival: up out of the middle and onto the left. */
function flyGlyphIn(name) {
  const glyph = faces[name] && faces[name].querySelector('.glyph');
  if (!glyph) return;
  glyph.classList.remove('entering', 'bumping');
  // Without this the class is added in the same frame it was removed and the
  // animation is never restarted — the glyph simply appears where it lands.
  void glyph.offsetWidth;
  // Consumed here and not left standing: a side is a fact about the swipe that caused
  // this arrival, and a mod handed over for any other reason afterwards was reading the
  // last swipe's side and being knocked on by a finger that had long since gone.
  const side = enterSide;
  enterSide = 'hole';
  if (side === 'hole') {
    glyph.classList.add('entering');
    return;
  }
  const thrown = BUMP_MIN + (BUMP_MAX - BUMP_MIN) * Math.min(1, swapSpeed / SWAP_FLICK);
  glyph.style.setProperty(
    '--bump', (side === 'left' ? thrown : -thrown).toFixed(1) + 'px'
  );
  glyph.classList.add('bumping');
}

/**
 * The room the bubble is going to need, asked for while it is still bare. The
 * window is the one thing here that cannot be animated: it is resized by the host
 * a frame or two after being asked, and a resize landing in the middle of the
 * growth clips it — the bubble is drawn cut off, then catches up, which is
 * exactly what the last of the lag was. Asked for early it is simply already
 * there, and the growth is nothing but CSS from then on.
 *
 * A window wider than the bubble costs shade pixels at the top of the screen, so
 * it is only ever early by the length of one hand-over.
 */
function claimRoom(owner) {
  const held = mergeHold;
  mergeHold = false;
  const room = MOD_CLOSED[owner].room();
  mergeHold = held;
  if ((room.width < 0 ? shared.compact.width : room.width) > windowWidth) applyWindow(room);
}

/**
 * The bubble opening out around what has reached it. Nothing happens between the
 * arrival and this: it *is* the reason the bubble changes width, and a reason has to
 * be seen before the thing it causes.
 *
 * The circle is retired by its own catch when it is deep enough inside not to be
 * seen going, so nothing is scheduled here either.
 */
function handOver() {
  mergeHold = false;
  root.classList.remove('entering');
  // Opened or taken over while the glyph was on its way: whatever is showing now
  // is the user's own doing, and it will ask for closed again itself.
  if (shared.state !== 'idle') return;
  // A circle still holding the glyph has to keep holding it until the bubble has
  // finished arriving around it, or the picture is drawn twice in the meantime.
  const carried = Boolean(leavingSatellite());
  takingIn = true;
  if (carried) root.classList.add('handing-over');
  toClosed();
  if (carried) trackExpansion();
  takingIn = false;
}

/**
 * The same thing on a timer, for a glyph rather than a bubble. A mod taking a bare
 * bubble brings its own glyph up out of the punch hole, and a glyph is *inside* the
 * bubble the whole way — there is no overlap to measure, so this one leg is still
 * timed against its own arrival curve.
 */
function handOverAfter(milliseconds) {
  clearTimeout(mergeTimer);
  mergeTimer = setTimeout(handOver, milliseconds);
}

/** The circle on its way home, if one is. */
function leavingSatellite() {
  return ['right', 'left']
    .map(side => satellites[side])
    .find(element => element.dataset.leaving) || null;
}

/**
 * Resting is whatever is true when nothing is happening: the bare bubble, or —
 * while something is playing — the song. An alert is a visitor; this is what it
 * returns to.
 *
 * A mod arriving or leaving is two beats, never one. The glyph moves first — in
 * from the middle, or in from the circle it had split off into — and only once it
 * is standing at the bubble's left edge does the bubble open out around it. The
 * width is the consequence; the glyph is the cause. Played together, as it was,
 * there is nothing to see but a box changing size for no stated reason.
 */
export function toClosed() {
  clearTimeout(shared.dwellTimer);
  // Already mid-hand-over, and bare on purpose. Whatever has changed underneath
  // will be read again when the glyph lands.
  if (mergeHold && !takingIn) return;
  shared.state = 'idle';
  shared.current = null;
  pill.classList.remove('alert', 'with-image', 'charging');

  const live = liveMods();
  const owner = live[0] || null;
  const settling = takingIn;

  // A mod stopped being true and another is still standing. The one that ended
  // closes — face off, back to the bare closed bubble — and the one left over is
  // a circle out at the side, which then runs in and makes it a mod bubble again.
  if (!settling && owner && live.length < lastLive) {
    mergeHold = true;
    paintSatellites();
    showFace('idle');
    root.style.removeProperty('--app-accent');
    setSize('idle');
    claimRoom(owner);
    // The circle that ran in is what opens the bubble out, so the bubble waits for
    // it rather than for a share of how long it was expected to take. paintSatellites
    // above is what put it on its way and registered the catch; this adds the step
    // the arrival is the cause of.
    const carrier = leavingSatellite();
    if (carrier) catchInto(carrier, pill, { entered: handOver });
    else handOverAfter(MERGE_TRAVEL * LAND);
    return;
  }

  // A mod taking a bare bubble. There is no circle to bring the glyph, so the
  // glyph brings itself: out of the middle, where it clears the camera hole, and
  // over to the left where the bubble will grow from.
  if (!settling && owner && owner !== lastOwner && live.length === 1) {
    mergeHold = true;
    paintSatellites();
    MOD_CLOSED[owner].paint();
    showFace(MOD_CLOSED[owner].face);
    root.classList.add('entering');
    // No satellite brought this one, so it comes up out of the middle: the punch
    // hole, which is where everything on this screen is born.
    enterFrom('hole');
    flyGlyphIn(MOD_CLOSED[owner].face);
    setSize(MOD_CLOSED[owner].size, { width: -1, height: -1, shift: 0 }, true);
    claimRoom(owner);
    handOverAfter(MOD_ENTER * LAND);
    return;
  }

  mergeHold = false;

  // Closed mods are additive, so this is where they are asked, in order, what
  // closed looks like right now. Whichever mod leads the order owns the bubble;
  // the rest split off it as satellites, which have to be laid out before the
  // window is asked for, since they are what makes it wider.
  paintSatellites();

  if (owner) {
    const mod = MOD_CLOSED[owner];
    mod.paint();
    showFace(mod.face);
    // The bubble changing hands while it is already a mod bubble: no room is being
    // asked for, so there is only the glyph to swap, and it arrives the same way.
    if (!settling && owner !== lastOwner) flyGlyphIn(mod.face);
    setSize(mod.size, mod.room(), true);
    lastOwner = owner;
    return;
  }

  lastOwner = null;
  ensureClosedWindow();
  setSize('idle');
  showFace('idle');
  root.style.removeProperty('--app-accent');
}
