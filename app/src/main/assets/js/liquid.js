import { lockPill } from './lock.js';
import { nowPill } from './now.js';
import { satellites } from './row.js';
import { CLOSED, MELT_MAX, MELT_MIN, bridge, pill, root, shared } from './state.js';

/**
 * The row's skin, drawn as solid shapes under one metaball filter so the bubble
 * and whatever has split off it can actually run together. The shapes are not
 * positioned by rules of their own: they are *mirrored* off the boxes they stand
 * for, frame by frame, which is the only way a skin can follow a transition, a
 * drag and a hold at once without every one of those rules being written twice.
 */
const liquidLayers = {
  edge: document.getElementById('liquid-edge'),
  fill: document.getElementById('liquid-fill'),
};

/**
 * One blob a layer for each box the row is made of, made once and kept. The Now
 * bubble is one of them: that is the whole point of it being drawn in this page
 * rather than in a window of its own, and it is why the drop can be seen leaving
 * the bubble at the punch hole instead of simply appearing out by the clock.
 */
/**
 * Mirrored in BubbleService's BLUR_PANES: the host keeps one pane of glass per
 * bubble in exactly this order, and setBlurFrame() sends the rectangles down in it.
 * A bubble added here and not there draws with no glass behind it.
 */
const BLUR_PANES = ['main', 'left', 'right', 'now', 'lock'];

const blobs = BLUR_PANES.map(name => ({
  name,
  edge: liquidLayers.edge.appendChild(document.createElement('div')),
  fill: liquidLayers.fill.appendChild(document.createElement('div')),
}));
blobs.forEach(blob => { blob.edge.className = 'blob'; blob.fill.className = 'blob'; });

/** The one primitive whose strength changes with how close the shapes are. */
const liquidBlur = document.getElementById('bubble-melt');
let meltNow = null;

/** Which box each blob is the skin of. */
function sourceOf(name) {
  if (name === 'main') return pill;
  if (name === 'now') return nowPill;
  if (name === 'lock') return lockPill;
  return satellites[name];
}

/**
 * A satellite that is not dressed has no skin: it is not merely invisible, it is
 * not part of the row. A dressed one always has skin even while it is tucked
 * inside the bubble at a fraction of its size — that is exactly where the merge
 * comes from, and fading it instead would put a half-transparent shape under the
 * filter, which erases it rather than dimming it.
 */
function isSkinned(name) {
  if (name === 'main') return true;
  // The Now bubble has a skin only while the light is on. Dark it is still in the
  // page, a zero-opacity box standing at its spot, and a blob mirrored off it would
  // be a permanent lump of liquid out by the clock.
  if (name === 'now') return nowPill.classList.contains('lit');
  // Only while it is standing there. Off the lock screen it is a zero-opacity box at
  // the bottom of the canvas, and a blob mirrored off it would be a permanent lump of
  // liquid over the wallpaper.
  if (name === 'lock') return lockPill.classList.contains('showing');
  // Only while the row is a row. A grown bubble is one shape with nothing beside it,
  // and a satellite still holding a mod behind it is not standing on the bar — it
  // would be a frosted circle out at the side of an open panel.
  return CLOSED.has(shared.size) && Boolean(satellites[name].dataset.mod);
}

/** When the mirror last ran, so anything integrated over time has a real step. */
let mirroredAt = 0;

/**
 * True while the mirror is painting. A merge threshold crossing at the end of a
 * frame changes state, and a state change repaints — which, under a finger, paints
 * the skin synchronously and would re-enter this on top of itself.
 */
let mirroring = false;

/**
 * The skin takes the bubble over again when the shape has come home, which is measured
 * rather than announced: a bubble bigger than the filter's own region has its skin
 * silently dropped, so blanking its background the frame a close *begins* leaves the
 * whole close painting nothing at all. paintSize takes the skin off when a growth
 * starts; this is the only thing that puts it back.
 */
function settleSkin(seen) {
  if (root.classList.contains('liquid') || !CLOSED.has(shared.size)) return;
  // Its own resting height and a little over for the border, since the box is measured
  // and the number it is measured against is the one Kotlin last pushed down.
  if (!seen || !seen.box.height || seen.box.height > shared.compact.height + 3) return;
  // Temporary: the bubble was reported going opaque for a frame exactly as it lands, and this is the one place the
  // skin and the bubble's own background trade over. Pull back out once diagnosed.
  console.log(
    'liquid on: size=' + shared.size + ' box=' + Math.round(seen.box.width) + 'x' +
    Math.round(seen.box.height) + ' compact=' + shared.compact.height + ' alpha=' + seen.alpha
  );
  root.classList.add('liquid');
}

/** Every rect first, every write after: one layout per frame instead of three. */
export function paintLiquidFrame() {
  if (mirroring) return;
  mirroring = true;
  try {
    mirrorFrame();
  } finally {
    mirroring = false;
  }
}

function mirrorFrame() {
  const now = performance.now();
  // Capped: a loop that stopped and started again, or a frame the system dropped,
  // would otherwise integrate a spring through half a second in one step and throw
  // the bubble across the screen.
  const elapsed = mirroredAt ? Math.min(0.05, (now - mirroredAt) / 1000) : 0;
  mirroredAt = now;

  const measured = blobs.map(blob => {
    if (!isSkinned(blob.name)) return null;
    const source = sourceOf(blob.name);
    // Resolved once per element and kept: the declaration getComputedStyle hands back is *live*, so re-reading it
    // next frame gives this frame's values — asking for a fresh one per blob per frame was allocating five
    // declarations a frame for numbers the same five objects already carry.
    const style = blob.style || (blob.style = getComputedStyle(source));
    return {
      source,
      box: source.getBoundingClientRect(),
      radius: style.borderRadius,
      // The skin is the shape, so it owes the shape's corners as well as their size:
      // a squircle mirrored as a plain round rectangle is a bubble whose liquid does
      // not fit inside its own outline.
      corner: style.cornerShape,
      colour: style.borderTopColor,
      // Glass cannot fade, so it shrinks instead: a circle on its way out is a
      // smaller pane every frame, and by the time it is invisible there is no pane
      // left hanging over the wallpaper.
      alpha: parseFloat(style.opacity),
    };
  });

  settleSkin(measured[0]);
  sendBlurFrame(measured);

  blobs.forEach((blob, index) => {
    const seen = measured[index];
    if (!seen || !seen.box.width) {
      blob.edge.style.display = 'none';
      blob.fill.style.display = 'none';
      return;
    }
    blob.edge.style.display = '';
    blob.fill.style.display = '';
    // The border was inside the box it came from, so the skin keeps it there: the
    // edge is the box itself and the fill is a pixel in from it. Drawing the edge
    // *outside* instead would leave the bubble two pixels wider than it was.
    write(blob.edge, seen.box, 0, seen);
    write(blob.fill, seen.box, -1, seen);
    blob.edge.style.background = seen.colour;
  });

  meltBy(measured);
  // Last, because a threshold crossing here can change state — and a state change
  // repaints, which can land back in this function. Everything this frame is meant
  // to draw has been drawn by now, and the guard above turns the nested call away.
  renderMerges(measured, elapsed, now);
}

/**
 * How strongly the skin runs together, from how far apart the shapes actually are.
 *
 * A fixed deviation cannot do this job: the shapes bridge at about twice it, so one
 * strong enough to fuse a satellite leaving the bubble welds the whole row into a
 * single bar for as long as it stands there, and one weak enough to leave the row
 * alone never merges anything at all. There is no number that is both. So it is not
 * a number — it is read off the gap every frame, which is what liquid does anyway:
 * two drops pull together as they come near and let go as they part, and nothing
 * about that is a constant.
 */
function meltBy(measured) {
  const boxes = measured.filter(seen => seen && seen.box.width).map(seen => seen.box);
  let closest = Infinity;
  // Every pair, and only the pairs that actually stand beside each other. Sorting the boxes
  // by their left edge and walking the list treats the canvas as one line, which it stopped
  // being the moment a bubble was drawn at the bottom of the screen: the lock bubble is
  // nearly the full width down there, so against anything on the row it reported a gap of
  // most of the screen *negative*, the deviation pinned at its maximum, and the whole row
  // was welded into one bar for as long as the keyguard was up. Two shapes on different
  // rows are not near each other in any sense the skin cares about — five boxes make ten
  // pairs, so there is nothing to save by being clever about it.
  for (let a = 0; a < boxes.length; a += 1) {
    for (let b = a + 1; b < boxes.length; b += 1) {
      const one = boxes[a];
      const two = boxes[b];
      if (one.top >= two.bottom || two.top >= one.bottom) continue;
      closest = Math.min(
        closest, one.left < two.left ? two.left - one.right : one.left - two.right
      );
    }
  }
  // Touching or overlapping is full strength; a resting row is far enough out to be
  // left as separate shapes. Between the two the neck forms and thins on its own.
  // shared.goo rather than MELT_MAX: the ceiling is the settings panel's to move, and the
  // floor stays where it is — a slider that could also raise the resting deviation would
  // weld a row that is standing still, which is the one thing the measured melt exists to
  // avoid.
  const ceiling = shared.goo || MELT_MAX;
  const melt = closest === Infinity
    ? MELT_MIN
    : Math.min(ceiling, Math.max(MELT_MIN, ceiling - closest * 0.5));
  if (melt !== meltNow) {
    meltNow = melt;
    liquidBlur.setAttribute('stdDeviation', melt);
  }
}

/**
 * The glass, sent as the rectangles it is standing on rather than as a journey.
 *
 * The blur is the host's — nothing a WebView draws reaches pixels it does not own —
 * but it no longer has a life of its own over there: the host used to be told the
 * shape each bubble was heading for and how long it would take, and it ran that
 * curve on its own clock, which meant every curve in this file had a twin in Kotlin
 * and anything the CSS did without being described (a hold's scale, a drag, a swap
 * that renames two boxes) left the frosted rectangle behind. The skin is already
 * mirrored off the real boxes every frame, so the glass is read off the same
 * measurement: it is an extension of the bubble, not a second element chasing it.
 *
 * The string is compared before it is sent because a bridge call is a JNI hop and a
 * resting row measures the same numbers frame after frame.
 */
let blurSent = '';

/**
 * Forget what the host was last told, so the next frame sends the regions again even if
 * nothing about them has changed.
 *
 * The dedupe below is right almost always — an unchanged spec is a bridge call and a layout
 * pass for nothing — but it assumes the host still has what it was given. At screen-off the
 * host clears every pane by hand, because the blur belongs to the compositor and a
 * transparent view goes on blurring. So the two disagree exactly once: the panes are gone
 * and the page believes they are still there, and on wake it says nothing because the
 * geometry is identical to what it was before the screen went off.
 *
 * That is the whole of "the blur on the locked bar sometimes does not render". Sometimes,
 * because anything that moved while the phone slept — a song changing, a timer ticking —
 * changed the spec and hid the bug.
 */
export function resendBlur() {
  blurSent = '';
  stirLiquid(240);
}

function sendBlurFrame(measured) {
  const spec = measured.map(seen => {
    if (!seen || !seen.box.width) return '';
    // A shape mid-fade keeps its box, so the pane is shrunk on the fade instead and
    // stays centred on the shape while it closes to nothing.
    const alpha = isNaN(seen.alpha) ? 1 : seen.alpha;
    if (alpha < 0.01) return '';
    const width = seen.box.width * alpha;
    const height = seen.box.height * alpha;
    return [
      Math.round(seen.box.left + (seen.box.width - width) / 2),
      Math.round(seen.box.top + (seen.box.height - height) / 2),
      Math.round(width),
      Math.round(height),
      // One radius, because a blurred region has one: the largest corner the shape
      // is wearing, capped at half its shortest side the way a border-radius is.
      Math.round(Math.min(parseFloat(seen.radius) || 0, Math.min(width, height) / 2)),
    ].join(',');
  }).join(';');
  if (spec === blurSent) return;
  blurSent = spec;
  bridge.setBlurFrame(spec);
}

function write(shape, box, grow, seen) {
  shape.style.width = (box.width + grow * 2) + 'px';
  shape.style.height = (box.height + grow * 2) + 'px';
  shape.style.transform =
    'translate(' + (box.left - grow) + 'px,' + (box.top - grow) + 'px)';
  shape.style.borderRadius = seen.radius;
  shape.style.cornerShape = seen.corner;
}

/**
 * The mirror runs only while something can still be moving. A permanent frame loop
 * under a window that is on screen all day is a battery bill for nothing, so every
 * paint that could start a transition says so and the loop outlives it by one
 * animation, then stops on its own with the shapes left where they landed.
 *
 * It runs whatever state the bubble is in, not only the closed row it draws skin
 * for: the glass is mirrored in the same pass, and a grown panel has glass exactly
 * as a closed bubble does. Gated on the skin, an alert opened with the filter off
 * left its frosted rectangle at the closed bubble's size for as long as it stood
 * there.
 */
let liquidUntil = 0;
let liquidFrame = null;

export function stirLiquid(milliseconds) {
  // Past the longest transition an argument-less caller can have started, which is a growth (--grow-ms, 460 at its
  // longest) or a split (380 after 150 of delay). It used to be 1,100 — past the longest *choreography* there is, a
  // mod being taken back in — but a choreography says how long it needs when it asks, and paying its length on every
  // resting repaint was up to a second of frame loop after everything had stopped moving.
  liquidUntil = Math.max(liquidUntil, performance.now() + (milliseconds || 700));
  if (liquidFrame !== null) return;
  // Starting cold. The display idles down when nothing has drawn for a while, so the
  // first frames of the first animation after a quiet stretch arrive late and unevenly
  // — the panel is still coming up to speed while the motion is already running. The
  // host is asked for the frames on the same breath as the motion is asked for.
  bridge.wakeFrames();
  wokeAt = performance.now();
  framesSinceWake = 0;
  liquidFrame = requestAnimationFrame(flowLiquid);
}

/** Temporary, alongside the merge trace: how the first frames after a cold start land. */
let wokeAt = 0;
let framesSinceWake = 0;

function flowLiquid() {
  const before = performance.now();
  paintLiquidFrame();
  if (TRACE_MERGES && framesSinceWake < 8) {
    framesSinceWake += 1;
    traceEvent(
      'frame ' + framesSinceWake + ' at +' + Math.round(before - wokeAt) +
      'ms, paint ' + Math.round(performance.now() - before) + 'ms'
    );
  }
  liquidFrame = performance.now() < liquidUntil
    ? requestAnimationFrame(flowLiquid)
    : null;
}



/**
 * A merge is measured, not scheduled.
 *
 * One bubble running into another used to be a set of timers: the circle was given
 * a travel length, the bubble was told to open out at a share of it, and the circle
 * was retired a fixed while after that. Every one of those numbers was a guess at
 * where the shape would be, and the shape is the only thing that actually knows —
 * so an ease that overshoots, a drag that reverses, a bubble that is a different
 * width today all put the reaction somewhere other than the moment it belongs to.
 * That gap between two animations, each correct on its own clock, is what reads as
 * lag.
 *
 * So a merge is a *catch*: the traveller and the bubble taking it in are registered
 * as a pair, and every frame the mirror already runs measures how much of the
 * traveller is inside the receiver. That one number drives everything — the
 * receiver's stretch is written from it continuously, and the steps of the
 * choreography fire as it crosses named shares rather than as timers expire. Run it
 * backwards and it reads backwards, because there is no timeline to run backwards.
 *
 * Rectangles, not shapes: these are pills and circles, and their bounding boxes
 * overlap slightly sooner than their outlines do. The thresholds are named against
 * this measure, so it is consistent with itself, and the goo is what sells the
 * contact anyway.
 */
/** The share of the traveller inside the receiver at which the receiver may change state. */
const CATCH_ENTERED = 0.8;

/** ...and at which the traveller can be taken off screen without it being seen going. */
const CATCH_SWALLOWED = 0.97;

/** How far a receiver stretches at full absorption of a shape as big as itself. */
const CATCH_SWELL = 0.13;

/** The release: liquid taken in jumps its tension and settles, so it is a spring. */
const CATCH_STIFFNESS = 260;
const CATCH_DAMPING = 17;

/**
 * A catch that cannot finish still has to finish. A traveller can be caught out of
 * the air — dressed again, sent back out, opened over — and its steps would then
 * never fire, leaving a circle marked as leaving for the rest of the session. The
 * ceiling is not a timeline: nothing is timed against it, it only says that after
 * this long the merge is over whatever the boxes say.
 */
const CATCH_CEILING = 900;

/** How long a merge may make no progress at all before it counts as arrived. */
const CATCH_STALL = 260;

/**
 * Every merge says what it did and how far in the traveller was when it did it, because the only way to tell a
 * threshold that is set wrong from a shape that never got where it was going is to read the shares off the phone.
 *
 * Off in normal running: it is a console line and a bridge hop per merge and, worse, one on eight frames of every
 * cold start of the mirror — which is a string built inside the frame loop the tracing exists to measure. Switch it
 * back on by hand while working on a merge.
 */
const TRACE_MERGES = false;

function trace(what, caught, entry, now) {
  if (!TRACE_MERGES) return;
  console.log(
    '[merge] ' + what + ' ' + nameOf(caught.traveller) + ' into ' +
    nameOf(caught.receiver) + ' at ' + entry.toFixed(2) +
    ' after ' + Math.round(now - caught.since) + 'ms'
  );
}

function nameOf(element) {
  return element.id || element.className || 'unnamed';
}

/** Temporary, and the same trace: what the page decided, so the log reads in order. */
export function traceEvent(text) {
  if (TRACE_MERGES) console.log('[merge] ' + text);
}

/** The travellers being taken in right now, and what they are being taken into. */
const catches = [];

/** Each receiver's stretch, kept as a spring so it can be released rather than reset. */
const swells = new Map();

/**
 * Register a merge, or add steps to one already running. A second call for the same
 * traveller does not restart it: the circle is where it is, and the share of it that
 * is already inside is the truth whoever asked.
 */
export function catchInto(traveller, receiver, steps) {
  const running = catches.find(caught => caught.traveller === traveller);
  if (running) {
    Object.assign(running.steps, steps);
    return;
  }
  const caught = {
    traveller,
    receiver,
    steps: Object.assign({}, steps),
    entered: false,
    // The deepest it has been so far, and when that last changed: a merge is judged
    // by whether it is still getting anywhere, not only by where it has got to.
    best: 0,
    since: performance.now(),
    movedAt: 0,
  };
  caught.movedAt = caught.since;
  catches.push(caught);
  trace('caught', caught, 0, caught.since);
  stirLiquid();
}

/** Caught out of the air: it is not being taken in any more, so nothing is owed. */
export function releaseCatch(traveller) {
  const index = catches.findIndex(caught => caught.traveller === traveller);
  if (index >= 0) catches.splice(index, 1);
}

/**
 * How much of the first box is inside the second, as a share of the first.
 *
 * Of the *traveller*, always, and never of whichever happens to be smaller. Measured
 * the other way it reads 1 the moment a big traveller covers a small receiver, which
 * on the lock bubble's flight home was a third of the way in — so a shape still most
 * of the screen wide counted as swallowed and went out in front of the user. A
 * traveller that is bigger than what is taking it in has not arrived yet; it is still
 * something that has to become a drop first.
 */
function enteredShare(traveller, receiver) {
  const wide = Math.min(traveller.right, receiver.right) - Math.max(traveller.left, receiver.left);
  const tall = Math.min(traveller.bottom, receiver.bottom) - Math.max(traveller.top, receiver.top);
  if (wide <= 0 || tall <= 0 || !traveller.width || !traveller.height) return 0;
  return (wide * tall) / (traveller.width * traveller.height);
}

function renderMerges(measured, elapsed, now) {
  const boxOf = new Map();
  measured.forEach(seen => {
    if (seen && seen.box.width) boxOf.set(seen.source, seen.box);
  });

  const driven = new Map();
  for (const caught of catches.slice()) {
    if (!catches.includes(caught)) continue;
    const traveller = boxOf.get(caught.traveller);
    const receiver = boxOf.get(caught.receiver);
    // A traveller that has stopped being one of the shapes on the bar is as far in
    // as it is ever going: there is nothing left of it standing outside.
    const entry = traveller && receiver ? enteredShare(traveller, receiver) : 1;

    // A merge that has stopped getting anywhere is over, whatever the boxes say. A
    // circle that set off and was then left standing half inside the bubble — its
    // travel interrupted, its target moved out from under it — used to hang there
    // until the ceiling, which is most of a second of a shape merging with nothing.
    // Only counted once it has actually started arriving: one that has not touched
    // the receiver at all is not stalled, it is still on its way.
    if (entry > caught.best + 0.01) {
      caught.best = entry;
      caught.movedAt = now;
    }
    const stalled = caught.best > 0.02 && now - caught.movedAt > CATCH_STALL;
    const late = stalled || now - caught.since > CATCH_CEILING;

    if (traveller && receiver) {
      // How much a bubble is stretched by what has entered it is how much of it
      // there is, not merely that something arrived: a dot barely disturbs the
      // bubble and a satellite of the same size disturbs it fully.
      const bulk = Math.min(
        1,
        (traveller.width * traveller.height) / (receiver.width * receiver.height)
      );
      driven.set(
        caught.receiver,
        (driven.get(caught.receiver) || 0) + entry * bulk * CATCH_SWELL
      );
    }

    if (!caught.entered && (entry >= CATCH_ENTERED || late)) {
      caught.entered = true;
      trace('entered', caught, entry, now);
      if (caught.steps.entered) caught.steps.entered();
    }
    if (caught.entered && (entry >= CATCH_SWALLOWED || late)) {
      // Off the list before the step runs: the step is what takes the traveller off
      // screen, and a repaint inside it would otherwise find the catch still live.
      trace('swallowed', caught, entry, now);
      releaseCatch(caught.traveller);
      if (caught.steps.swallowed) caught.steps.swallowed();
    }
  }

  driven.forEach((_, receiver) => {
    if (!swells.has(receiver)) swells.set(receiver, { value: 0, velocity: 0 });
  });

  swells.forEach((swell, receiver) => {
    const target = driven.get(receiver);
    if (target !== undefined) {
      // Driven: the bubble is exactly as stretched as the shape inside it is deep.
      // The speed it is being stretched at is kept, because that is what the release
      // inherits — a circle that ran in hard springs back harder than one that
      // drifted in, and neither is a number written down anywhere.
      if (elapsed > 0) swell.velocity = (target - swell.value) / elapsed;
      swell.value = target;
    } else {
      swell.velocity +=
        (-swell.value * CATCH_STIFFNESS - swell.velocity * CATCH_DAMPING) * elapsed;
      swell.value += swell.velocity * elapsed;
      if (Math.abs(swell.value) < 0.0004 && Math.abs(swell.velocity) < 0.004) {
        swells.delete(receiver);
        receiver.style.removeProperty('--absorb');
        return;
      }
    }
    receiver.style.setProperty('--absorb', swell.value.toFixed(4));
  });

  // Nothing here is on a timer, so nothing here can say in advance how long it needs
  // the mirror for. It asks for the next frame instead, for as long as it has
  // something left to do.
  if (catches.length || swells.size) {
    liquidUntil = Math.max(liquidUntil, now + 120);
  }
}
