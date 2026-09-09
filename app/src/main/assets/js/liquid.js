import { lockPill } from './lock.js';
import { clockPill, isClockLit } from './clock.js';
import { doublePill, isDoubleOut } from './double.js';
import { noteAt } from './notes.js';
import { isPadlockShowing, padlockPill } from './padlock.js';
import { statusPill } from './status.js';
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
 *
 * 'note0'…'note4' are five reserved panes rather than one per notification actually
 * showing, because the host's panes are a fixed `View` list stood up once at start — the
 * same shape the two satellites already are. Mirrors NOTES_LIMIT in notes.js.
 */
/**
 * The quick settings' modules are in here because they are bubbles: they are in the same body of
 * liquid the row is, they neck into each other and into the charge standing beside them, and they
 * are slung home into the Status bubble rather than being switched off. A module left out of this
 * list would be the one shape in the panel painting its own background — see bubbles.md.
 *
 * There are fifteen of them rather than six because the four groups were broken up: every connector
 * and every state is its own bubble now, which is what lets one of them be thrown in and slung home
 * on its own. The names are the control's own `data-toggle`, so a switch added to the panel is a
 * name here and a `quick-` line in QUICK_MODULES and nothing else.
 *
 * The cable is the one control in the panel that is **not** here: it stands inside the foot module
 * rather than beside it, and a pane over pixels another pane already covers frosts them twice.
 */
const BLUR_PANES = [
  'main', 'left', 'right', 'lock', 'status', 'clock', 'double', 'padlock',
  'note0', 'note1', 'note2', 'note3', 'note4',
  'quickHotspot', 'quickPlane', 'quickGps', 'quickMobile', 'quickWifi', 'quickBluetooth',
  'quickModus', 'quickDim', 'quickRotate', 'quickSaver', 'quickRecording', 'quickMic',
  'quickBright', 'quickVolume', 'quickMedia',
];

/**
 * The frosted screen behind the open quick settings, and it is a pane without being a blob: it is
 * flat glass rather than liquid, and a full-screen shape in the goo layer would weld every bubble
 * on the canvas into it. So it rides the blur bridge alone — prepended to the spec `sendBlurFrame`
 * sends, which makes it **pane 0** and every blob's pane its own index plus one.
 *
 * Mirrored in BubbleService: SCRIM_PANE is that 0, BLUR_PANES over there is this list plus it, and
 * the host gives that one pane its own radius so the screen can be frosted harder than a bubble is.
 */
const scrim = document.getElementById('quick-scrim');

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
  if (name === 'status') return statusPill;
  if (name === 'clock') return clockPill;
  if (name === 'double') return doublePill;
  if (name === 'padlock') return padlockPill;
  if (name === 'lock') return lockPill;
  if (name.startsWith('note')) return noteAt(Number(name.slice(4)));
  if (name.startsWith('quick')) return quickModule(name);
  return satellites[name];
}

/** Which box each quick-settings module is. Resolved once — the markup is static, and a query per module per frame is fifteen of them for elements that cannot change. The twelve switches are looked up off the same `data-toggle` their pane is named after, so the two cannot drift apart by a typo the way a hand-written list of ids could. The cable is excluded by id: it is a `.quick-mode` like the rest and would be picked up by the same query, but it has no pane of its own — see BLUR_PANES. */
const QUICK_MODULES = Object.assign(
  {
    quickBright: document.querySelector('.level[data-level="brightness"]'),
    quickVolume: document.querySelector('.level[data-level="volume"]'),
    quickMedia: document.getElementById('quick-media'),
  },
  Object.fromEntries([...document.querySelectorAll('.quick-mode:not(#quick-media-usb), .quick-knob')].map(control => [
    'quick' + control.dataset.toggle[0].toUpperCase() + control.dataset.toggle.slice(1),
    control,
  ])),
);
const quickModule = name => QUICK_MODULES[name];

/** The frame the modules stand in, which is also what says whether any of them are standing. */
const quickPanel = document.getElementById('quick-panel');

/**
 * A satellite that is not dressed has no skin: it is not merely invisible, it is
 * not part of the row. A dressed one always has skin even while it is tucked
 * inside the bubble at a fraction of its size — that is exactly where the merge
 * comes from, and fading it instead would put a half-transparent shape under the
 * filter, which erases it rather than dimming it.
 */
function isSkinned(name) {
  if (name === 'main') return true;
  // Only while it is standing there. Off the lock screen it is a zero-opacity box at
  // the bottom of the canvas, and a blob mirrored off it would be a permanent lump of
  // liquid over the wallpaper.
  if (name === 'lock') return lockPill.classList.contains('showing');
  // Only once it has been born. Before the first connectivity payload it is a zero-opacity
  // box at the right end of the bar, and a blob mirrored off it would be a lump of liquid
  // standing over the system's icons from the moment the service starts.
  // Open, this bubble is not a bubble: the panel it grows into is the battery and only the battery,
  // so the glass that would stand behind the cell is dropped for as long as it is open. A pane there
  // was a rounded slab of liquid the exact size of the cell, drawn a pixel behind it and reading as a
  // frame round a shape that is already its own outline.
  if (name === 'status') {
    return statusPill.classList.contains('lit') && !statusPill.classList.contains('open');
  }
  if (name === 'clock') return isClockLit();
  if (name === 'double') return isDoubleOut();
  if (name === 'padlock') return isPadlockShowing();
  // Only as many as are actually standing there. A repaint can shrink the list from five notes
  // to two without this frame knowing in advance, which is exactly what noteAt() answers.
  if (name.startsWith('note')) return Boolean(noteAt(Number(name.slice(4))));
  // Only while the panel is up, and through the sling as well as the stand: `leaving` is what holds
  // the modules on screen while they fly home, and a skin dropped at the start of that flight is
  // six bubbles that stop being liquid exactly as they run into the one taking them in.
  if (name.startsWith('quick')) {
    return quickPanel.classList.contains('showing') || quickPanel.classList.contains('leaving');
  }
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
 * The skin is on from the first frame and never comes off. It used to be dropped the moment the bubble grew, because a
 * grown bubble stood outside the filter's own region and would have painted nothing at all — the region is the whole
 * screen while the bubble is grown now, so there is nothing left to drop it for, and an extended state is in the same
 * body of liquid the closed row is. That is exactly what "the alert overlapped the row instead of merging with it" was:
 * a bubble painting its own background is not in the goo layer, so it can only ever stack on top of what it meets.
 */
root.classList.add('liquid');

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
    // Resolved once per *element* and kept: the declaration getComputedStyle hands back is *live*, so re-reading it
    // next frame gives this frame's values — asking for a fresh one per blob per frame was allocating five
    // declarations a frame for numbers the same five objects already carry. Kept per element rather than per blob
    // forever, because a note pane's element is not the same one twice: notes.js redraws its list whole rather than
    // reconciling it, so blob.source is compared here and the declaration is re-resolved whenever it disagrees —
    // otherwise a note pane went on reading the computed style of whatever card used to be at that position.
    if (blob.source !== source) blob.style = getComputedStyle(source);
    blob.source = source;
    const style = blob.style;
    return {
      source,
      box: source.getBoundingClientRect(),
      radius: style.borderRadius,
      // The skin is the shape, so it owes the shape's corners as well as their size:
      // a squircle mirrored as a plain round rectangle is a bubble whose liquid does
      // not fit inside its own outline.
      corner: style.cornerShape,
      colour: style.borderTopColor,
      // A tint the bubble asked for, or nothing. Read from the element rather than from its
      // background, because html.liquid blanks every bubble's own background the moment the skin
      // takes over painting it — so a bubble that wants its own colour has to say so in a property
      // that survives that. Transparent is the initial value and means the layer's own fill.
      fill: style.getPropertyValue('--blob-fill').trim(),
      // Glass cannot fade, so it shrinks instead: a circle on its way out is a
      // smaller pane every frame, and by the time it is invisible there is no pane
      // left hanging over the wallpaper.
      alpha: parseFloat(style.opacity),
    };
  });

  sendBlurFrame(measured);

  blobs.forEach((blob, index) => {
    const seen = measured[index];
    // A shape at zero opacity has no skin, and until now only the *pane* knew that: `sendBlurFrame`
    // has always dropped a region under `alpha < 0.01` and the blob went on being drawn from the box
    // alone. Everything that fades here used to fade while its class was still on and be dropped a
    // moment later when the class went, so the two frames nobody saw hid it — and a module leaving
    // the quick panel is the case where that stops being true, because the class it is dropped by
    // comes off on a timer and the mirror can stop before it. A blob left drawn is a lump of liquid
    // standing over the wallpaper for as long as nothing else stirs the mirror.
    if (!seen || !seen.box.width || (!isNaN(seen.alpha) && seen.alpha < 0.01)) {
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
    // Cleared rather than written black when there is no tint, so the stylesheet's own fill is
    // what paints and there is one place the resting colour lives.
    blob.fill.style.background = isTinted(seen.fill) ? seen.fill : '';
  });

  paintWalls(measured);
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

/**
 * The frosted screen, as the one region that is not a bubble's — and the one region that fades by
 * changing its *strength* rather than its size. Every other pane here shrinks on its own opacity,
 * because a bubble's glass is the bubble and a shape closing to its middle is what a bubble leaving
 * looks like. A screen has no middle to close to: shrunk, the frost read as a rectangle of blur
 * collapsing towards the centre of the wallpaper, which is the one thing it must not look like. So
 * this pane keeps the whole screen for as long as it is there at all and sends `--scrim-frost` as a
 * sixth number — the share of SCRIM_BLUR the host is to actually apply — and the ramp up over 0.6s
 * and home over 1.1s is that number transitioning in the stylesheet.
 *
 * Read off the custom property rather than off `opacity`, because the two are no longer the same
 * fade: the dark ground over the frost still leaves in 140ms and the frost behind it takes eight
 * times as long, which is the whole point of the change.
 */
/** How many steps the frost ramp is allowed — see the comment on the value it rounds. */
const FROST_STEPS = 12;

function scrimRegion() {
  const frost = parseFloat(getComputedStyle(scrim).getPropertyValue('--scrim-frost'));
  if (!frost || frost < 0.01) return '';
  const box = scrim.getBoundingClientRect();
  return [
    Math.round(box.left),
    Math.round(box.top),
    Math.round(box.width),
    Math.round(box.height),
    0,
    // **Quantised hard, and this is the single most expensive number in the project.** It is compared
    // against the last frame's string as text on both sides of the bridge, and the host re-applies a
    // pane's blur whenever its region changes — so every distinct value here is one more full-screen
    // Samsung blur of a 1080×2340 surface. Two decimals were nearly free-running: a 450ms ramp at
    // 120Hz is fifty-odd frames and a hundred available values, so essentially every frame of every
    // open and every close re-blurred the whole screen. Measured on the phone, the scrim's ramp was
    // about 4ms of the 90th-percentile frame and most of the GPU tail (15ms down to 9ms with the
    // panes off entirely). At a twelfth the ramp is twelve re-blurs instead of fifty and the step is
    // not visible, because the blur is not what the eye is reading during the ramp — the scrim's dark
    // ground is, and that is a plain CSS opacity transition that costs nothing and stays smooth.
    (Math.round(frost * FROST_STEPS) / FROST_STEPS).toFixed(2),
  ].join(',');
}

/**
 * Whether a shape gets glass of its own, which is not the same question as whether it is liquid.
 * The quick settings' modules are the one set that answers no: they stand on the frosted screen the
 * panel already puts up, so a pane under each of them is the same wallpaper blurred twice — darker
 * and duller under every module than between them, which reads as six grey cards rather than as
 * glass. They keep their blob, so they go on necking into each other and into the charge; what they
 * give up is only the second frost. Their panes stay in the list and are simply sent empty, because
 * the host's panes are a fixed `View` list and a pane's index is its position in this one.
 */
function isGlazed(name) {
  return !name.startsWith('quick');
}

function sendBlurFrame(measured) {
  const spec = [scrimRegion()].concat(measured.map((seen, index) => {
    if (!seen || !seen.box.width || !isGlazed(blobs[index].name)) return '';
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
  })).join(';');
  if (spec === blurSent) return;
  blurSent = spec;
  bridge.setBlurFrame(spec);
}

/**
 * The screen's own edges, as something the liquid can touch.
 *
 * A bubble standing a few pixels off the side of the screen is a drop next to a wall, and a drop
 * next to a wall does not stand apart from it — it reaches out and wets it. So a bubble close
 * enough to an edge grows a neck into it: a shape from off-screen up to the bubble's own edge,
 * narrower than the bubble is tall, which the goo then fillets into the bubble exactly as it
 * fillets a satellite. It is drawn rather than blurred into place because the melt is one number
 * for the whole layer — raising it far enough to bridge fourteen pixels of gap would weld the
 * row into a bar, which is the one thing meltBy() exists to prevent.
 *
 * A grown bubble takes its neck all the way up to the top of the screen instead, so the two meet
 * in the corner and the goo rounds it: the panel is held in the corner of the screen rather than
 * floating a margin away from it.
 */
const walls = ['left', 'right'].map(side => ({
  side,
  edge: liquidLayers.edge.appendChild(document.createElement('div')),
  fill: liquidLayers.fill.appendChild(document.createElement('div')),
}));
walls.forEach(wall => { wall.edge.className = 'blob'; wall.fill.className = 'blob'; });

/** How near an edge a bubble has to be before the liquid reaches it. */
const WALL_REACH = 44;

/** How far the neck hangs off the screen, so its own outer end is never seen. */
const WALL_OVER = 12;

/** What the neck keeps of the bubble's height. Under half, or it is a bar rather than a neck. */
const WALL_WAIST = 0.42;

function paintWalls(measured) {
  const grown = root.classList.contains('grown');
  walls.forEach(wall => {
    // Whichever skinned bubble is nearest this edge, and only if the setting is on and it is
    // near enough to be reaching. The main bubble grown is the one that takes the corners.
    // Grown, the main bubble takes both edges whatever else is standing near them: the corner is
    // the panel's to close, and a neck drawn to the clock instead would leave the panel floating
    // a margin off the very edge it is supposed to be held by.
    const near = !shared.edgeMerge ? null
      : grown ? withinReach(measured[0], wall.side)
      : nearestTo(wall.side, measured);
    if (!near) {
      wall.edge.style.display = 'none';
      wall.fill.style.display = 'none';
      return;
    }
    const waist = near.box.height * WALL_WAIST;
    // Up to the top of the screen while the bubble is grown, so the neck and the panel close the
    // corner between them; on the row it is a neck at the bubble's own middle and nothing more.
    const top = grown ? 0 : near.box.top + (near.box.height - waist) / 2;
    const box = {
      left: wall.side === 'left' ? -WALL_OVER : near.box.right - 1,
      top,
      width: (wall.side === 'left' ? near.box.left + 1 : window.innerWidth - near.box.right + 1) + WALL_OVER,
      height: (grown ? near.box.top - top + near.box.height : 0) + waist,
    };
    box.right = box.left + box.width;
    box.bottom = box.top + box.height;
    wall.edge.style.display = '';
    wall.fill.style.display = '';
    const seen = { radius: (waist / 2) + 'px', corner: near.corner, colour: near.colour };
    write(wall.edge, box, 0, seen);
    write(wall.fill, box, -1, seen);
    wall.edge.style.background = near.colour;
  });
}

/** How far this bubble's own edge stands off that side of the screen. */
function gapTo(seen, side) {
  return side === 'left' ? seen.box.left : window.innerWidth - seen.box.right;
}

/** The same box back, or nothing when it is too far from the edge to be reaching for it. */
function withinReach(seen, side) {
  if (!seen || !seen.box.width) return null;
  const gap = gapTo(seen, side);
  return gap >= 0 && gap <= WALL_REACH ? seen : null;
}

/** The bubble this edge would reach for: the nearest one to it, if any is near enough at all. */
function nearestTo(side, measured) {
  let found = null;
  for (const seen of measured) {
    if (!withinReach(seen, side)) continue;
    if (!found || gapTo(seen, side) < gapTo(found, side)) found = seen;
  }
  return found;
}

/** Whether a bubble asked for a colour of its own. Nothing at all and fully transparent are the same answer, and a computed `<color>` says the second of the two. */
const isTinted = fill => Boolean(fill) && !fill.startsWith('rgba(0, 0, 0, 0)');

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
