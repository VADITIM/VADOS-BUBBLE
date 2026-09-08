import { stirLiquid } from './liquid.js';
import { openPlayer } from './mods/media.js';
import { openCurrent, openPicture } from './mods/notification.js';
import { openTimer } from './mods/timer.js';
import { closeNowPanel, nowOpen } from './now.js';
import { clockPill } from './clock.js';
import { closeStatusPanel, statusOpen, statusPill } from './status.js';
import { abandonSwap, becomeExtended, applyClosedWindow, applyWindow, closedTarget, dragSwap, ensureClosedWindow, liveMods, releaseSwap, toClosed } from './row.js';
import { CLOSED, HOLD_GRACE, HOLD_MILLIS, HOLD_SCALE, bridge, pill, root, shared } from './state.js';
import { MOD_FACES, leaveForMod, openHistory, openMod } from './tabs.js';

/**
 * The block: the one radius every gesture on a bubble has to leave before it is a gesture at
 * all, and the whole of what the hold is protected by.
 *
 * A finger pressing for a third of a second wanders, and every pixel of that wander used to
 * reach something: the play drag started at 12, the sideways trade at 22, the lean at 12, and
 * the hold was called off somewhere along a hyperbola at 80% of the band's radius. Four numbers
 * for one question — has the finger stopped resting and started asking — and a hold that failed
 * differently depending on which direction the wander happened to go.
 *
 * One number now, and it is the same for the drag and for the swipe down, because they are the
 * same question asked in two directions. Inside it **the hold has priority over everything**:
 * nothing leans, nothing trades, nothing is picked up, and the swell goes on growing. Outside
 * it the touch belongs to whichever gesture the direction names, and the hold is over — its
 * precondition has failed, and the swell springs back from wherever it had got to rather than
 * from where it would have ended up.
 *
 * Small enough that a real swipe is never swallowed, wide enough that a press is never mistaken
 * for one. It is subtracted from the travel rather than compared against it, so the band still
 * starts from zero once the finger is genuinely going somewhere.
 */
export const HOLD_BLOCK = 18;

/**
 * The extra time a hold is given when the finger wanders inside the block.
 *
 * A finger that moves and then settles is a finger that is still deciding, and the third of a
 * second it had been counting for was measured from before it started. Granted once per touch,
 * not per frame — the point is a little more patience, not a hold that can be kept alive
 * indefinitely by never quite standing still.
 */
export const HOLD_WANDER = 150;

/**
 * How far a bubble may be pulled from where it rests, however hard it is dragged.
 *
 * This is not a gesture and nothing is ever asked for by it: it is the bubble being
 * an object rather than a control with three approved directions. The finger is
 * followed in every direction, on a rubber band that gives less the further it goes,
 * and the bubble springs home when it is let go. The gestures that do mean something
 * run on top of it, because they are measured from the finger and not from where the
 * bubble ended up.
 */
const DRAG_RADIUS = 40;

/**
 * The notifications tab is a scrollable list, not an object to be pushed around, so the
 * same press-and-drag the bare bubble gets reads as far too loose once it has grown into
 * that tab — a swipe meant for the list underneath was shoving the whole panel instead.
 * Cut to 15% of the resting radius (an 85% reduction) rather than turned off outright,
 * so the bubble still answers a touch instead of feeling dead while `shared.size` is
 * `'history'`.
 */
const HISTORY_DRAG_SCALE = 0.15;

function dragRadius() {
  return shared.size === 'history' ? DRAG_RADIUS * HISTORY_DRAG_SCALE : DRAG_RADIUS;
}

/**
 * The band itself: a hyperbola, so the first pixels are nearly free and the last are
 * nearly immovable, and the bubble never leaves the circle however hard it is pulled.
 * Past the block by `travel`, it has come `DRAG_RADIUS * travel / (travel + R)`.
 */
function rubberBand(travel) {
  const radius = dragRadius();
  const past = Math.max(0, travel - HOLD_BLOCK);
  return radius * past / (past + radius);
}

/** How long the way home takes, and the curve it lands on. Mirrors --ease-split in pill.css. */
const HOME_MILLISECONDS = 520;
const HOME_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';

/** The springback each bubble is running, so a finger coming back can take it off again. */
const springs = new WeakMap();

/**
 * The way home, added to the bubble rather than transitioned onto it.
 *
 * It used to be a class — `homing` — that gave `transform` a 520ms overshooting
 * transition, and `transform` is the property everything written per frame shares: the
 * merge's `--absorb` squash, the pull's lean. A per-frame value driven through a spring
 * that overshoots by 1.7 is the bubble bouncing and landing somewhere it was never sent,
 * and the class outlived the gesture that added it, so after the first drag of a session
 * every later one left it on. An added animation cannot do that: it composes onto whatever
 * `transform` is already saying, it ends by itself, and it takes nothing over on the way.
 */
function springHome(element, from) {
  cancelSpring(element);
  const spring = element.animate(
    { transform: [from, 'translate(0px, 0px) scale(1)'] },
    { duration: HOME_MILLISECONDS, easing: HOME_EASE, composite: 'add' },
  );
  springs.set(element, spring);
}

/** The finger is back on the bubble, so the journey home is over whatever it had left. */
export function cancelSpring(element) {
  const spring = springs.get(element);
  if (spring) spring.cancel();
}

/**
 * One bubble following one finger. The direction is the finger's and the distance is
 * the band's, so a drag straight down and a drag diagonally both end at the edge of
 * the same circle.
 */
/**
 * Whether a drag has gone far enough that a hold running underneath it should be called off.
 *
 * Exported because every bubble owes the same answer and only this file knows the band. The
 * main bubble's hold is ended inside toy(); the lock bubble keeps its own timer and asks
 * here, which is the difference between one rule and two that drift.
 */
export function rubberBandPast(dx, dy) {
  return Math.hypot(dx, dy) > HOLD_BLOCK;
}

export function toy(element, prefix, dx, dy) {
  const travel = Math.hypot(dx, dy);
  const reach = rubberBand(travel);
  const scale = travel > 0 ? reach / travel : 0;
  // Measured on the finger rather than on the band: the block is a distance the *hand* has
  // moved, and the same distance has to mean the same thing whichever bubble is under it — the
  // band's own hyperbola says different things at different points along itself.
  if (travel > HOLD_BLOCK) endHold();
  cancelSpring(element);
  element.style.setProperty(prefix + '-x', (dx * scale).toFixed(2) + 'px');
  element.style.setProperty(prefix + '-y', (dy * scale).toFixed(2) + 'px');
  stirLiquid(160);
}

/**
 * And letting go of it: home on a spring, with the skin and the glass following.
 *
 * The offsets are *removed* rather than written back as `0px`. Left standing at zero they
 * are still an answer to "is this bubble being dragged", and the guard here read exactly
 * that — which is how one drag per session used to be enough to leave the bubble sprung.
 */
export function untoy(element, prefix) {
  const x = element.style.getPropertyValue(prefix + '-x');
  const y = element.style.getPropertyValue(prefix + '-y');
  if (!x && !y) return;
  element.style.removeProperty(prefix + '-x');
  element.style.removeProperty(prefix + '-y');
  springHome(element, `translate(${x || '0px'}, ${y || '0px'})`);
  stirLiquid(600);
}

/**
 * The pull down, which is the same gesture wherever it starts: the bubble follows
 * the finger a little and opens the list once it has been asked properly. It begins
 * early — a gesture that has to be aimed is one that reads as broken — and then it
 * has a proper stretch to it, so the bubble is visibly being drawn down rather than
 * twitching once and opening.
 *
 * The travel used to stop at the grab margin because the bubble's own window was the
 * touchable one and anything below that margin was clipped. It is not any more: the
 * canvas is STAGE_HEIGHT tall and never resizes, and the proxy that heard the finger
 * keeps the whole gesture until the finger lifts however far outside itself it goes.
 * So nothing here is bounded by a window — the only limit left is what reads well.
 */
/** How far down the finger goes before the pull is an ask rather than a wander. Mirrors STATUS_PULL: it is one gesture and it must not need a different hand at two ends of the same bar. It is also mirrored by PULL_GRAB in BubbleService.kt, which is the room under the bubble the proxy window has to hold for the finger to still be on it when this threshold is crossed — raise one and the other owes it the same. */
const PULL_TRIGGER = 22;

/**
 * The same ask, in pixels, when the gesture was taken away rather than finished. Lower, and it
 * has to be: over an app SystemUI has its own shade gesture on this strip and it pilfers the
 * pointer partway down, which arrives here as a cancel with the finger still moving. That is
 * stronger evidence of an ask than a lift is, not weaker.
 */
const PULL_STOLEN = 12;

// The window shrinks back only once the collapse has finished, so the resize
// never lands mid-frame.
pill.addEventListener('transitionend', event => {
  if (event.propertyName !== 'height' && event.propertyName !== 'width') return;
  if (CLOSED.has(shared.size)) applyClosedWindow();
});

let startY = 0;
let startX = 0;
let hasMoved = false;
let hasGrown = false;
let hasSwiped = false;

/** True once the pull has opened the list, so a stream of touchmoves cannot open it again. */
let hasPulled = false;

/** The furthest down the finger has got, kept so a gesture the shade confiscates can still be read. */
let pullDrop = 0;

/** The wander's extra time, granted once per touch. */
let hasWandered = false;

/** What the hold does when its timer comes up, held so the wander can restart the same step. */
let heldStep = null;

/** When the current hold was armed, so a restart can carry the time it had already served. */
let holdArmedAt = 0;

/**
 * The hold's timer, restarted with HOLD_WANDER added, when the finger moves inside the block.
 *
 * Restarted rather than lengthened, because a timer that has been counting cannot be asked how
 * long it has left. It is granted once, so the finger cannot keep a hold alive by twitching.
 */
function grantHoldWander() {
  if (hasWandered || !holdPending || !heldStep) return;
  hasWandered = true;
  // What it had left plus the grant, never the grant alone: a wander at fifty milliseconds
  // would otherwise make the hold *shorter* than a finger that never moved at all.
  const served = Date.now() - holdArmedAt;
  clearTimeout(shared.holdTimer);
  shared.holdTimer = setTimeout(heldStep, Math.max(0, HOLD_MILLIS - served) + HOLD_WANDER);
}

/** True once the upward dismiss has fired, and it may fire once per touch. A touchmove is a
 *  stream, not an event: every frame the finger stayed above the line met the same condition and
 *  fired the dismiss again — the haptic, the host call and the close, over and over until the
 *  bubble was left in a state nothing had asked for. A gesture that has already acted is over. */
let hasDismissed = false;

/**
 * The window is exactly the resting bubble, so a bubble that scales past it is
 * simply clipped at the sides. It is widened by the scale factor for the duration
 * of the hold — and by no more than that, because every pixel this window covers
 * is a pixel the notification shade cannot be dragged from.
 */
/** The window the closed bubble occupies right now — bare, or under a mod. */
function closedWindow() {
  const live = liveMods();
  if (!live.length) return { width: shared.compact.width, height: shared.compact.height };
  return { width: closedTarget().width, height: shared.compact.height };
}

function beginHold() {
  root.classList.add('holding');
  // The room for the scale is asked for late on purpose. Resizing the window while
  // a finger is down interrupts the gesture, and the tap that follows is lost —
  // that is what made a closed mod need several taps to open. A tap is over long
  // before this fires; only a real hold ever gets here.
  clearTimeout(shared.growTimer);
  shared.growTimer = setTimeout(() => {
    // Not while the row is being dragged. Resizing the proxy pulls the surface out
    // from under the finger and the system answers with a cancel, which would end the
    // drag the hold is deliberately running underneath. The window a closed row with
    // a satellite already has is symmetric and holds a satellite's width either side,
    // which is more than the swell needs — so the growth is simply skipped and the
    // scale is drawn in room that was already there.
    if (root.classList.contains('dragging')) return;
    // Scaled from whatever the bubble currently is, never from the bare size: a
    // mod makes the closed bubble wider.
    const base = closedWindow();
    // Only the bubble scales, so only the bubble's share of the window needs the
    // extra room. Scaling the whole width would pad it by the satellites' width
    // as well — dead pixels over the shade, for a growth that never reaches them.
    const main = closedTarget().main || shared.compact.width;
    hasGrown = true;
    applyWindow({
      width: Math.ceil(base.width + main * (HOLD_SCALE - 1)) + 2,
      height: Math.ceil(base.height * HOLD_SCALE) + 2,
    });
  }, HOLD_GRACE);
}

/** True while a hold is counting and has neither fired nor been called off: inside the block the hold outranks everything, and this is what the gestures below read to know it has not resolved yet. */
let holdPending = false;
export function endHold() {
  clearTimeout(shared.holdTimer);
  holdPending = false;
  root.classList.remove('pressing');
  clearTimeout(shared.growTimer);
  hasGrown = false;
  // A hold released before it opened anything leaves the bubble where it began.
  if (shared.state === 'haptic' && !shared.hasHeld) shared.state = 'idle';
  if (!root.classList.contains('holding')) return;
  root.classList.remove('holding');
  ensureClosedWindow();
}

pill.addEventListener('touchstart', event => {
  startY = event.touches[0].clientY;
  startX = event.touches[0].clientX;
  hasMoved = false;
  shared.hasHeld = false;
  hasGrown = false;
  hasSwiped = false;
  hasDismissed = false;
  hasPulled = false;
  pullDrop = 0;
  hasWandered = false;
  // Cleared, not left standing: a bare bubble arms no hold, and a step left over from the last
  // touch is a hold the wander could restart for a bubble that never had one.
  heldStep = null;

  // A bare bubble has no hold at all. It is the way out to the app behind whatever the bubble is
  // wearing, and an idle one is wearing nothing — there is no app and no panel to fall back on,
  // since the quick settings belong to the Status bubble. It used to be armed anyway and then
  // quietly do nothing when its timer came up, which bought the swell, the window growth and a
  // third of a second of the touch for a gesture that was never going to happen. Not armed, the
  // press is answered by the band and by nothing else, and the finger is free the whole time.
  if (shared.state === 'idle' && liveMods().length) {
    shared.state = 'haptic';
    holdPending = true;
    beginHold();
    // Held rather than written inline, so the wander can restart the same step with more time
    // on it: a timer that is already counting cannot be asked how long it has left.
    heldStep = () => {
      holdPending = false;
      // Read again rather than closed over: the mod can end between the press and the timer, and
      // leaving for an app that has stopped playing is worse than doing nothing.
      const held = liveMods()[0];
      if (!held) {
        endHold();
        return;
      }
      shared.hasHeld = true;
      root.classList.remove('holding');
      // Whatever else the finger had started underneath the hold is let go of here,
      // and let go of without being asked what it wanted: the hold has the touch now, and a
      // trade committing behind what this is about to do is two gestures answering one finger.
      abandonSwap();
      bridge.triggerHaptic('expand');
      leaveForMod(held);
      toClosed();
    };
    holdArmedAt = Date.now();
    shared.holdTimer = setTimeout(heldStep, HOLD_MILLIS);
    return;
  }

  // An open mod holds too, and it means the one thing the panel cannot do: leave
  // for the app itself. No growth under the finger for this one — the window is
  // already exactly the open bubble, so a scale would only be clipped by it.
  if (shared.state === 'extended' && MOD_FACES[shared.size]) {
    root.classList.add('pressing');
    holdPending = true;
    heldStep = () => {
      holdPending = false;
      shared.hasHeld = true;
      root.classList.remove('pressing');
      bridge.triggerHaptic('expand');
      openMod();
      toClosed();
    };
    holdArmedAt = Date.now();
    shared.holdTimer = setTimeout(heldStep, HOLD_MILLIS);
  }
}, { passive: true });

pill.addEventListener('touchmove', event => {
  // The haptic has fired: it has already opened a panel or left for an app, and it
  // owns the rest of this touch outright. Nothing below may read the same finger —
  // a pull or a sideways trade running on top of what the hold just did is two
  // gestures answering one touch, and the second one undoes the first. The finger
  // has to be lifted before anything else may be asked for.
  if (shared.hasHeld) return;
  const verticalTravel = event.touches[0].clientY - startY;
  const horizontalTravel = Math.abs(event.touches[0].clientX - startX);
  const sideways = event.touches[0].clientX - startX;
  // Inside the block nothing moves, nothing is measured and nothing may be claimed: the hold
  // has priority there, full stop. What the finger buys by wandering is a little more patience
  // — the hold's timer is given HOLD_WANDER once, because a finger that moved and then settled
  // is still deciding and the third of a second it was counting began before it started.
  if (Math.hypot(horizontalTravel, verticalTravel) <= HOLD_BLOCK) {
    grantHoldWander();
    return;
  }
  pullDrop = Math.max(pullDrop, verticalTravel);
  // Until something claims the touch the bubble simply follows the finger on its band, which is
  // the whole of what a pull looks like here now: the bubble is an object being drawn down, and
  // the list opens when the finger has gone far enough. There is no second lean written on top
  // of the band and no share of a threshold to settle from — that machinery is what made the
  // same gesture open the list at one distance and do nothing at another.
  const dragging = root.classList.contains('dragging');
  if (dragging || hasSwiped) untoy(pill, '--drag');
  else toy(pill, '--drag', sideways, verticalTravel);
  // The pull, exactly as the Status bubble reads it: far enough down, and more down than
  // across. Felt at the crossing rather than on the lift, because the crossing is the moment
  // there was still a decision to make.
  if (!hasPulled && shared.state !== 'extended' && CLOSED.has(shared.size) && !hasSwiped &&
      !dragging && verticalTravel > PULL_TRIGGER && verticalTravel > horizontalTravel) {
    hasPulled = true;
    hasMoved = true;
    endHold();
    untoy(pill, '--drag');
    bridge.triggerHaptic('expand');
    openHistory();
    return;
  }
  // Sideways across a closed bubble trades width with the satellite, live. The block is taken
  // off the travel, so the swap still starts from nothing once it begins.
  const committed = Math.abs(sideways) - HOLD_BLOCK;
  if (!hasPulled && shared.state !== 'extended' && CLOSED.has(shared.size) && committed > 0 &&
      horizontalTravel > Math.abs(verticalTravel)) {
    if (dragSwap(Math.sign(sideways) * committed)) {
      hasSwiped = true;
      hasMoved = true;
      return;
    }
  }
  if (!hasDismissed && verticalTravel < -24 && horizontalTravel < 40) {
    hasDismissed = true;
    hasMoved = true;
    // The one gesture the hold does not get to run underneath: this one has already
    // acted, and there is nothing left to hold.
    endHold();
    bridge.triggerHaptic('dismiss');
    bridge.onSwipeDismiss();
    toClosed();
    return;
  }
}, { passive: true });

pill.addEventListener('touchend', () => {
  releaseSwap();
  endHold();
  untoy(pill, '--drag');
}, { passive: true });
/**
 * Growing the window to make room for the scaled bubble pulls the surface out from
 * under the finger, and the system answers that with a cancel — the hold was being
 * broken by its own animation, which is why it had to be retried. A cancel that
 * arrives once that growth has happened is ours, not the user's, and the hold runs
 * on: it still has a touchend or the 350ms timer to finish it.
 */
pill.addEventListener('touchcancel', () => {
  releaseSwap();
  untoy(pill, '--drag');
  // Taken away rather than finished, with the finger still on its way down: over an app the
  // shade has the pointer now and there will be no lift to wait for, so the ask is answered
  // here and at a lower bar than a lift would need.
  if (!hasPulled && !shared.hasHeld && shared.state !== 'extended' && CLOSED.has(shared.size) &&
      pullDrop > PULL_STOLEN) {
    hasPulled = true;
    endHold();
    bridge.triggerHaptic('expand');
    openHistory();
    return;
  }
  if (shared.state === 'haptic' && hasGrown) return;
  endHold();
}, { passive: true });

pill.addEventListener('click', () => {
  if (hasMoved || shared.hasHeld) return;

  // Open closes on any tap, its own included.
  if (shared.state === 'extended') {
    bridge.triggerHaptic('tap');
    toClosed();
    return;
  }

  if (shared.state === 'alert') {
    clearTimeout(shared.dwellTimer);
    // A photo opens to its full shape first; anything else has already said
    // everything it has to say, so the tap goes straight to the app.
    if (pill.classList.contains('with-image') && shared.size !== 'picture') {
      bridge.triggerHaptic('expand');
      openPicture();
      return;
    }
    openCurrent();
    return;
  }

  // Closed. What a tap opens depends on which mod owns the closed bubble: the song
  // if one is playing, and on the bare bubble the list — the thing a bubble with
  // nothing to say is most often being asked for.
  becomeExtended();
  bridge.triggerHaptic('expand');
  // Whichever mod owns the closed bubble owns the tap on it — the head of the
  // order, not whichever mod would rank first: a swipe can have handed the bubble
  // to the other one, and it used to open the neighbour's content instead.
  const owner = liveMods()[0];
  if (owner === 'timer') {
    openTimer();
    return;
  }
  if (owner === 'media') {
    openPlayer();
    return;
  }
  // A call has no panel of its own — everything worth doing to one is in Discord.
  // So the tap leaves for it, through the notification's own intent, which lands in
  // the call rather than on whatever tab Discord was last left on.
  if (owner === 'call' && shared.call && shared.call.key) {
    bridge.openNotification(shared.call.key);
    toClosed();
    return;
  }
  openHistory();
});

/**
 * A tap anywhere else on the screen closes the bubble. It cannot come from a DOM
 * listener — the host window is only as big as the bubble — so it is the window
 * manager's ACTION_OUTSIDE, which reports the touch without swallowing it.
 */
window.onOutsideTap = (x, y) => {
  // Every proxy reports a touch outside *its own* bounds, and there is more than one
  // proxy — so a tap on an open panel is an outside tap as far as the Now bubble's
  // proxy is concerned. Closing on it shut the panel on the way down, and the tap's
  // own click then landed on the bubble that had taken its place and opened it again:
  // a tap on an open bubble replayed the opening instead of closing it, and a hold on
  // one never got the chance to start because the panel was gone before the finger
  // had been down a tenth of a second. The point is hit-tested here rather than in
  // Kotlin, because the page is the only side that knows where anything is.
  const hit = document.elementFromPoint(x, y);
  if (hit && (pill.contains(hit) || clockPill.contains(hit) || statusPill.contains(hit))) return;
  if (shared.state === 'extended') toClosed();
  // The Now bubble's panel closes on the same tap, and for the same reason: it is
  // one page now, so one message closes whatever on it is open. The Status bubble's
  // quick settings are the third of them.
  if (nowOpen) closeNowPanel();
  if (statusOpen) closeStatusPanel();
};
