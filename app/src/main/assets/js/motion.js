import { stirLiquid } from './liquid.js';
import { openPlayer } from './mods/media.js';
import { openCurrent, openPicture } from './mods/notification.js';
import { openTimer } from './mods/timer.js';
import { closeNowPanel, nowOpen, nowPill } from './now.js';
import { abandonSwap, applyClosedWindow, applyWindow, closedTarget, dragSwap, ensureClosedWindow, liveMods, releaseSwap, toClosed } from './row.js';
import { CLOSED, HOLD_GRACE, HOLD_MILLIS, HOLD_SCALE, HOLD_SLOP, bridge, pill, root, shared } from './state.js';
import { MOD_FACES, leaveForMod, openHistory, openMod, openQuick } from './tabs.js';

/**
 * The room a resting finger is given before anything at all reads it as movement.
 *
 * A finger pressing for a third of a second wanders, and every pixel of that wander
 * used to reach some gesture or other — which is why the hold felt unreliable even
 * after it was made the senior one: it was not being cancelled any more, but the
 * bubble was still twitching under it and the swell was fighting a lean nobody asked
 * for. Inside this nothing moves and nothing is measured. It is subtracted from the
 * travel rather than compared against it, so the play drag still starts from zero
 * once the finger is genuinely going somewhere.
 */
export const DEAD_ZONE = 12;

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
const DRAG_RADIUS = 20;

/**
 * The band itself: a hyperbola, so the first pixels are nearly free and the last are
 * nearly immovable, and the bubble never leaves the circle however hard it is pulled.
 * Past the dead zone by `travel`, it has come `DRAG_RADIUS * travel / (travel + R)`.
 */
function rubberBand(travel) {
  const past = Math.max(0, travel - DEAD_ZONE);
  return DRAG_RADIUS * past / (past + DRAG_RADIUS);
}

/**
 * One bubble following one finger. The direction is the finger's and the distance is
 * the band's, so a drag straight down and a drag diagonally both end at the edge of
 * the same circle.
 */
export function toy(holder, prefix, dx, dy) {
  const travel = Math.hypot(dx, dy);
  const reach = rubberBand(travel);
  const scale = travel > 0 ? reach / travel : 0;
  holder.classList.remove('homing');
  holder.style.setProperty(prefix + '-x', (dx * scale).toFixed(2) + 'px');
  holder.style.setProperty(prefix + '-y', (dy * scale).toFixed(2) + 'px');
  stirLiquid(160);
}

/** And letting go of it: home on a spring, with the skin and the glass following. */
export function untoy(holder, prefix) {
  if (!holder.classList.contains('homing') && !holder.style.getPropertyValue(prefix + '-x')) return;
  holder.classList.add('homing');
  holder.style.setProperty(prefix + '-x', '0px');
  holder.style.setProperty(prefix + '-y', '0px');
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
// The lean begins where the dead zone ends: one number for "the finger is genuinely
// going somewhere", not two that disagree by eight pixels.
const PULL_START = DEAD_ZONE;
const PULL_TRIGGER = 28;
const PULL_TRAVEL = 24;
/** How much of the way there a finger that *lifted* counts as having asked. */
const PULL_SETTLE = 0.7;
/**
 * The same, for a finger that was taken away rather than lifted. Lower, and it has to
 * be: over an app SystemUI has its own shade gesture on this strip and it pilfers the
 * pointer partway down, which arrives here as a cancel with the finger still moving.
 * That is stronger evidence of an ask than a lift is, not weaker — the gesture did not
 * stop, it was stopped. Discarding it is why the pull worked on the home and lock
 * screens and did nothing at all inside an app, and why it got worse the further the
 * pull had to travel.
 */
const PULL_STOLEN = 0.35;

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

export function endHold() {
  clearTimeout(shared.holdTimer);
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

  if (shared.state === 'idle') {
    shared.state = 'haptic';
    beginHold();
    shared.holdTimer = setTimeout(() => {
      shared.hasHeld = true;
      root.classList.remove('holding');
      // Whatever else the finger had started underneath the hold is let go of here,
      // and let go of without being asked what it wanted: the hold has the touch now,
      // and a trade committing or a list opening behind the panel this is about to
      // put up is two gestures answering one finger.
      abandonSwap();
      endPull();
      bridge.triggerHaptic('expand');
      // The hold on a mod is the way out to its app — the one thing the bubble
      // itself can never be. A bare bubble has no app to leave for, so it holds to
      // the settings it owns; a call taps out to Discord already, so it does too.
      const held = liveMods()[0];
      if (!held || held === 'call') {
        openQuick();
        return;
      }
      leaveForMod(held);
      toClosed();
    }, HOLD_MILLIS);
    return;
  }

  // An open mod holds too, and it means the one thing the panel cannot do: leave
  // for the app itself. No growth under the finger for this one — the window is
  // already exactly the open bubble, so a scale would only be clipped by it.
  if (shared.state === 'active' && MOD_FACES[shared.size]) {
    root.classList.add('pressing');
    shared.holdTimer = setTimeout(() => {
      shared.hasHeld = true;
      root.classList.remove('pressing');
      bridge.triggerHaptic('expand');
      openMod();
      toClosed();
    }, HOLD_MILLIS);
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
  // Inside the dead zone nothing moves and nothing is measured. This is the room the
  // hold needs: a finger pressing for a third of a second wanders, and every pixel of
  // that wander used to reach some gesture or other.
  if (Math.hypot(horizontalTravel, verticalTravel) <= DEAD_ZONE) return;
  // Until something claims the touch the bubble simply follows the finger on its
  // band. Once a real gesture has it — a trade, a lean — the play drag lets go and
  // the row's own motion takes over, or the bubble would be carried twenty pixels off
  // its place for the whole of a swap.
  if (root.classList.contains('dragging') || root.classList.contains('pulling') ||
      hasSwiped) {
    untoy(root, '--drag');
  } else {
    toy(root, '--drag', sideways, verticalTravel);
  }
  // The hold is no longer called off by travel at all. It used to be — one slop for
  // both answers, whatever cancels the hold is the same travel that starts the drag —
  // and that made the drag the senior gesture: a finger that moved was a finger that
  // had given up on holding, so the swell stopped growing and the haptic never came.
  // It is the other way round now. The hold runs underneath whatever else the finger
  // is doing, swelling on `scale` while the drag trades width and the pull leans, and
  // if the finger is still down when its timer comes up the hold takes the touch and
  // the others are let go of without asking for anything. A gesture that has to be
  // held still to work is one the user has to be careful to make.
  // Sideways across a closed bubble trades width with the satellite, live. The
  // vertical limit only decides whether a drag may *start*: once it has, the
  // finger is free to wander down the screen, which it does — the bubble is at the
  // very top and there is nowhere else for a long drag to go.
  const dragging = root.classList.contains('dragging');
  // The pull down comes before the sideways trade, and wins whenever the finger is
  // going down more than it is going across. It is the fallback gesture, so it must
  // be the easy one to make: no aiming, and it starts moving almost at once.
  // Once it has started it keeps the touch until the finger lifts. A pull is now long
  // enough that the finger wanders well past the drag's slop sideways on the way
  // down, and the frame where that wander read as wider than the travel handed the
  // touch to the sideways trade — which sets hasSwiped and kills the pull for good.
  // The direction only decides which gesture this is, and it is decided once.
  const pulling = root.classList.contains('pulling');
  if (shared.state !== 'active' && CLOSED.has(shared.size) && !hasSwiped && !dragging &&
      (pulling || (verticalTravel > PULL_START && verticalTravel > horizontalTravel))) {
    // The hold is not called off while the bubble is merely leaning: the finger that
    // is about to be held still wanders a few pixels, and cancelling on that wander
    // is exactly what broke the hold on the sideways drag. Only a pull that has
    // actually asked for something takes the touch away from it.
    if (verticalTravel >= PULL_TRIGGER) {
      hasMoved = true;
      endHold();
      endPull();
      bridge.triggerHaptic('expand');
      openHistory();
      return;
    }
    // Clamped low because the latch above lets the finger come back up without
    // handing the touch on, and a negative reach would push the bubble upwards.
    const reached = Math.max(0, (verticalTravel - PULL_START) / (PULL_TRIGGER - PULL_START));
    shared.pullReach = reached;
    root.classList.add('pulling');
    // Under the finger nothing eases, so the springback's own transition has to be off
    // again before the lean is written — the same handover toy() makes for the drag.
    root.classList.remove('homing');
    root.style.setProperty('--pull', (PULL_TRAVEL * reached).toFixed(2) + 'px');
    // A touch of shrink with it, so the bubble reads as being drawn out of the
    // cutout under tension rather than simply sliding down the screen.
    root.style.setProperty('--pull-scale', (1 - 0.03 * reached).toFixed(3));
    // The glass and the goo are mirrored off the bubble, and the bubble is now
    // travelling far enough that a mirror standing still is a pane left behind at
    // the cutout. Short, because the next move re-stirs it.
    stirLiquid(160);
    return;
  }
  // The drag starts where the hold gives up and not a pixel sooner. It used to
  // begin at three pixels of travel — a quarter of the wander a resting finger
  // makes in a third of a second — so a hold was regularly cancelled by its own
  // drag before it ever reached the haptic. The slop is taken off the travel, so
  // the swap still starts from nothing once it does begin.
  const committed = Math.abs(sideways) - HOLD_SLOP;
  if (shared.state !== 'active' && CLOSED.has(shared.size) && committed > 0 &&
      (dragging || Math.abs(verticalTravel) < 30)) {
    if (dragSwap(Math.sign(sideways) * committed)) {
      hasSwiped = true;
      hasMoved = true;
      return;
    }
  }
  if (verticalTravel < -24 && horizontalTravel < 40) {
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

/**
 * The bubble lets go of the finger and eases back to where it rests — or, if the
 * finger had all but asked already, finishes the ask itself. A flick can lift
 * between two frames, and a gesture that was visibly most of the way there and did
 * nothing is the same bug as one that needs aiming.
 */
function endPull(settle, threshold) {
  if (!root.classList.contains('pulling')) return;
  const asked = settle && shared.pullReach > (threshold || PULL_SETTLE);
  shared.pullReach = 0;
  root.classList.remove('pulling');
  // The lean lives inside `transform`, which is the per-frame property and has no
  // duration of its own — that is the whole point of the split, and it is why letting
  // go of a pull put the bubble back at the cutout in one frame. The way home is the
  // play drag's, because it is the same journey: `homing` is the one rule that gives
  // `transform` a transition, and it is named here rather than a second curve being
  // written for the pull alone.
  root.classList.add('homing');
  root.style.removeProperty('--pull');
  root.style.removeProperty('--pull-scale');
  // The glass and the goo are mirrored off the bubble, so they have to ride the
  // springback home rather than snapping back the frame the class came off.
  stirLiquid(600);
  if (asked) {
    // The tap that follows a touchend is not this gesture asking twice — it would
    // close what the pull has just opened.
    hasMoved = true;
    bridge.triggerHaptic('expand');
    openHistory();
  }
}

pill.addEventListener('touchend', () => {
  releaseSwap();
  endPull(true);
  endHold();
  untoy(root, '--drag');
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
  endPull(true, PULL_STOLEN);
  untoy(root, '--drag');
  if (shared.state === 'haptic' && hasGrown) return;
  endHold();
}, { passive: true });

pill.addEventListener('click', () => {
  if (hasMoved || shared.hasHeld) return;

  // Open closes on any tap, its own included.
  if (shared.state === 'active') {
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
  shared.state = 'active';
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
  if (hit && (pill.contains(hit) || nowPill.contains(hit))) return;
  if (shared.state === 'active') toClosed();
  // The Now bubble's panel closes on the same tap, and for the same reason: it is
  // one page now, so one message closes whatever on it is open.
  if (nowOpen) closeNowPanel();
};
