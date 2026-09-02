import { PROXY_TAP_SLOP } from './bridge.js';
import { catchInto, releaseCatch, stirLiquid, traceEvent } from './liquid.js';
import { rowLeftEdge } from './row.js';
import { HOLD_MILLIS, bridge, pill, root, shared } from './state.js';

/**
 * The torch's bubble, out by the clock. It was a page in a window of its own until
 * the canvas made one surface of the whole bar; everything here is that page, moved
 * across so that this bubble is in the same goo layer as the rest of the row and
 * can therefore actually merge with it. Nothing that used to be a message to the
 * host is one any more — the width the bubble gives up for it, and which of the two
 * stands in front, are now this page talking to itself.
 */
export const nowPill = document.getElementById('now');
const nowGlyph = document.getElementById('now-glyph');
const nowReading = document.getElementById('now-reading');
const nowSlider = document.getElementById('now-slider');
const nowRail = document.getElementById('now-rail');
const nowFlow = document.getElementById('now-flow');
const nowFaces = {
  closed: document.getElementById('now-closed'),
  panel: document.getElementById('now-panel'),
};

/** A beam rather than a bulb: a cone says the light is pointed somewhere. */
const TORCH_GLYPH =
  '<svg viewBox="0 0 24 24"><path d="M8.4 2h7.2a1 1 0 0 1 .97 1.24l-.9 3.6a1 1 0 0 1-.97.76H9.3a1 1 0 0 1-.97-.76l-.9-3.6A1 1 0 0 1 8.4 2zm1.1 7.6h5a1 1 0 0 1 1 1V21a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V10.6a1 1 0 0 1 1-1zm1.5 3.4a1 1 0 0 0 0 2h2a1 1 0 0 0 0-2z"/></svg>';
nowGlyph.innerHTML = TORCH_GLYPH;

/** Kept in step with the CSS above: five slots and four gaps. */
const NOW_SLOT = 40;
const NOW_SLOT_GAP = 14;
// The slot's size is a layout the script owns — it places every slot along the rail
// from this number — so the CSS is given it rather than holding a second copy.
root.style.setProperty('--now-slot', NOW_SLOT + 'px');
const NOW_PANEL = { width: 300, height: 132, ms: 420 };
// The CSS is told the panel's size rather than repeating it: this is the number the
// host is given for the glass and for the proxy, and three copies of it is three
// chances to move one and not the others.
root.style.setProperty('--now-panel-width', NOW_PANEL.width + 'px');
root.style.setProperty('--now-panel-height', NOW_PANEL.height + 'px');

/**
 * How wide the pill rests, mirroring RESTING_WIDTH in BubbleService. Not the closed
 * bubble's width: this pill's whole job is to stand on top of One UI's own
 * flashlight chip, and that chip is wider than the bubble. Matched to the bubble
 * instead, Samsung's blue pill was left sticking out past its right edge.
 */
/**
 * The narrowest the light is ever drawn: enough to cover One UI's own flashlight chip,
 * which is the thing it is standing on. Measured off that chip and nothing else — if a
 * One UI update makes theirs wider, theirs shows past the end of ours and this is the
 * number to move. It is a floor and not a width: with a mod on the row the light
 * reaches further, up to the row's own edge.
 */
const NOW_RESTING = 116;
/**
 * The gap the Now bubble leaves between itself and the bubble at the hole. Read it
 * against meltBy(): the melt is MELT_MAX less half the gap, and two shapes run together
 * at about twice the melt, so four pixels apart is a bridge of eight — the pair stands
 * there permanently necked, which is the merge being visible at rest without the two
 * of them reading as one welded pill.
 */
const NOW_REACH = 4;
/**
 * The narrowest it may stand at. One UI's clock is under its left half and its chip
 * under the right, and a pill that closes to less than this hands back a piece of the
 * bar it is there to be instead of. Only the flight home goes below it, and by then it
 * is a drop and on its way out.
 */
let NOW_COVER = 128;

/**
 * Where its left edge rests and how much bar it must cover are one measurement, taken on the
 * phone off the chip One UI draws there — so both come from the host and neither is decided
 * here. This one is a fallback for the frames before the host has said anything.
 */
window.setNowCover = value => {
  NOW_COVER = Number(value) || NOW_COVER;
  fitNowWidth();
};

/** Where its left edge rests, told by the host, which is the side that measures. */
let nowLeft = 53;

/**
 * The flight out and the flight home, the share of the way at which the pill is
 * actually against its stop — the split curve overshoots and settles back, so that
 * is under halfway and not the end — and the opening out that hitting it causes.
 */
const NOW_TRAVEL = 420;
/* Longer than the flight out, on purpose. Going out, the drop separating from the
   bubble is the whole of what there is to see and it is over the moment the pill
   hits its stop; coming home it is the last stretch that matters — the two shapes
   necking and running together — and at the outward speed that stretch was three
   frames and the light was simply gone. */
const NOW_RETURN = 560;
const NOW_LAND = 0.42;
const NOW_EXPAND = 300;
/**
 * The share of the flight after which the pill is clear of the bubble at the cutout.
 * That is when the bubble pulls its width in, not when the light came on: one drop
 * leaves, the other closes behind it.
 */
const NOW_CLEAR = 0.2;
/** The width given back before the pill is allowed to leave its spot. */
const NOW_COLLAPSE = 260;

let torch = null;

/**
 * What is happening, in the order it started happening.
 *
 * The Now bubble carries one thing at a time and the row's rule decides which: first come,
 * first served. A recording that started before a download keeps the bubble until it stops,
 * and nothing here ranks one kind of event above another — the phone has no way of knowing
 * which of two true things the person cares about, and guessing is how a bubble ends up
 * flickering between two states that are both correct.
 *
 * Torch is one of these rather than the special case it used to be: it is a Now mod like the
 * others, and everything below reads the owner instead of reading `torch`.
 */
const nowLive = {};
let nowOrder = [];

/** How wide each mod asks the bubble to stand, before the floors below are applied. */
const NOW_WIDTHS = {
  torch: 116,
  recording: 138,
  download: 178,
  upload: 178,
};

/**
 * Which mods have been swiped away. A dismissed mod is still live — the recording is still
 * recording — it is simply not being shown any more, and it stays dismissed until it actually
 * ends. Anything else and a transfer swiped away is back a fraction of a second later, because
 * a progress notification re-posts on every tick.
 */
const nowDismissed = {};

/** How far up the finger has to travel for the swipe to be a dismissal. Mirrors LOCK_SWIPE. */
const NOW_SWIPE = 24;

/**
 * The dismissal's own flight, which is the lock bubble's shape rather than the torch's: it
 * gathers itself in on the spot while already crawling home, then opens up to full speed. The
 * torch going *out* keeps its own choreography — that one is a light being switched off and it
 * has been walked on the phone — so this is a second flight rather than a rewrite of the first.
 * When the dismissal has been walked too, the two should become one helper.
 */
const NOW_FLING_PINCH_SCALE = 0.55;
const NOW_FLING_PINCH = 260;
const NOW_FLING = 520;
const NOW_FLING_CRAWL = 0.25;
const NOW_FLING_PINCH_EASE = 'cubic-bezier(0.62, 0, 0.2, 1.65)';
const NOW_FLING_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';

/** A finished transfer stands still for this long wearing a tick, then goes home. */
const NOW_DONE_DWELL = 1100;

const NOW_GLYPHS = {
  recording: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="7"/></svg>',
  download: '<svg viewBox="0 0 24 24"><path d="M11 3h2v9.2l3.3-3.3 1.4 1.4L12 16l-5.7-5.7 1.4-1.4L11 12.2zM5 18h14v2H5z"/></svg>',
  upload: '<svg viewBox="0 0 24 24"><path d="M12 3l5.7 5.7-1.4 1.4L13 6.8V16h-2V6.8L7.7 10.1 6.3 8.7zM5 18h14v2H5z"/></svg>',
  done: '<svg viewBox="0 0 24 24"><path d="M9.8 16.2 5.6 12l-1.4 1.4 5.6 5.6L20.4 7.9 19 6.5z"/></svg>',
};

function nowOwner() {
  return nowOrder[0];
}

export let nowOpen = false;
let nowDot = null;
let nowDotAt = 0;
let nowSliding = false;
let nowWidth = 46;

/** One flight is a run of steps, and a second light cancels the first outright. */
let nowTimers = [];
function nowAfter(milliseconds, step) {
  nowTimers.push(setTimeout(step, milliseconds));
}
function stopNowFlight() {
  nowTimers.forEach(clearTimeout);
  nowTimers = [];
  // A light switched back on while its drop was falling home is not being taken in
  // any more, and the bubble must not go on being told it is.
  releaseCatch(nowPill);
}

function showNowFace(name) {
  for (const [key, face] of Object.entries(nowFaces)) {
    face.classList.toggle('showing', key === name);
  }
}

/**
 * How wide the Now bubble stands once it has arrived: as far as the row's own left
 * edge, less the gap the skin needs to neck across. The row moves aside for it
 * (rowShift) and it takes what the row gave up, so the two grow and shrink against
 * each other and the neck between them holds.
 */
function nowResting() {
  // The mod's own width is a *floor* like the other two, never a ceiling: this bubble grows
  // into whatever the row leaves it either way, and a download asking for 178 is asking not to
  // be squeezed below that when the row is wide. NOW_COVER is still the hard one — below it the
  // bubble hands back the piece of bar it exists to stand on — which is also why "Torch
  // narrowed" is as narrow as the torch gets: its own ask is under the cover it owes.
  const asked = NOW_WIDTHS[nowOwner()] || NOW_RESTING;
  return Math.max(asked, NOW_COVER, rowLeftEdge() - nowLeft - NOW_REACH);
}

/**
 * The Now bubble taking whatever the row has left it. Called from the row's own paint
 * rather than worked out once on arrival, because the edge it grows up to moves: a
 * mod arriving pushes the row further right and this fills the gap that opens behind
 * it. Nothing here while it is flying or open — the flight owns the width on the way
 * out, and the panel is a size of its own.
 */
export function fitNowWidth() {
  if (nowPill.classList.contains('lit') && !nowOpen &&
      !root.classList.contains('now-flying')) {
    const next = nowResting();
    if (Math.abs(next - nowWidth) >= 1) {
      setNowWidth(next, NOW_EXPAND, 'var(--ease-split)');
      fitNowProxy();
    }
  }
  // Always, even when the width did not move — the width not moving is exactly the
  // case this is for.
  fitNowCrowd();
}

/** The least bar left between the light's glyph and its reading once it is crowded. */
const NOW_READ_GAP = 8;
/** How far the reading currently stands off its own edge, so the room can be read back. */
let nowCrowd = 0;

/**
 * The reading giving way to the row. Once the light has shrunk to NOW_COVER it cannot
 * give up any more width — below that it hands back the piece of bar it exists to
 * stand on — so the row goes on coming and the two bubbles' contents are what meet.
 * The reading is pushed left by exactly as much of it as the row has taken, which is
 * what one body of liquid does when another arrives beside it: it does not stay put
 * and get sat on, and it does not vanish.
 *
 * Bounded by the glyph, which does not move — the glyph is the left inset in every
 * bubble here and the one thing on this bar that is always in the same place.
 */
function fitNowCrowd() {
  const idle = !nowPill.classList.contains('lit') || nowOpen ||
    root.classList.contains('now-flying');
  const taken = idle ? 0 : (nowLeft + nowWidth + NOW_REACH) - rowLeftEdge();
  // Read back through the push already applied, or the room shrinks every time this
  // runs and the reading walks itself into the glyph one paint at a time.
  const room = idle ? 0 : nowReading.getBoundingClientRect().left + nowCrowd -
    nowGlyph.getBoundingClientRect().right - NOW_READ_GAP;
  const next = Math.min(Math.max(0, taken), Math.max(0, room));
  if (Math.abs(next - nowCrowd) < 0.5) return;
  nowCrowd = next;
  root.style.setProperty('--now-crowd', next.toFixed(1) + 'px');
  stirLiquid(NOW_EXPAND + 120);
}

/** Flying, the pill is a drop: as wide as it is tall, a circle. */
function nowDrop() { return shared.compact.height; }

/**
 * How far the punch hole is from this spot. The page can answer this itself now
 * that it is drawn across the whole screen — it used to be told, because a window
 * the size of the pill had no idea how wide the screen was.
 */
function nowFlight() {
  return window.innerWidth / 2 - nowDrop() / 2 - nowLeft;
}

function setNowFly(next, milliseconds, ease) {
  root.style.setProperty('--now-fly', next + 'px');
  root.style.setProperty('--now-fly-ms', milliseconds + 'ms');
  root.style.setProperty('--now-fly-ease', ease);
  // Nothing about the glass is said here any more. This bubble's pane is mirrored
  // off the pill itself every frame like every other one, so the flight only has to
  // keep the mirror running for as long as it lasts.
  stirLiquid(milliseconds + 120);
}

function setNowWidth(next, milliseconds, ease) {
  nowWidth = next;
  root.style.setProperty('--now-width', next + 'px');
  root.style.setProperty('--now-width-ms', milliseconds + 'ms');
  root.style.setProperty('--now-width-ease', ease);
  stirLiquid(milliseconds + 120);
}

/** The proxy is exactly what can be touched, and nothing else on this bar can. */
export function fitNowProxy() {
  if (!nowPill.classList.contains('lit')) {
    bridge.setNowProxy(0, 0, 0);
    return;
  }
  if (nowOpen) {
    bridge.setNowProxy(
      NOW_PANEL.width, NOW_PANEL.height, Math.round(nowLeft + nowPanelShift())
    );
    return;
  }
  bridge.setNowProxy(Math.round(nowWidth), shared.compact.height, Math.round(nowLeft));
}

const nowLine = document.getElementById('now-line');
const nowLineDone = document.getElementById('now-line-done');
const nowElapsed = document.getElementById('now-elapsed');

/** Seconds since a recording started, counted here because the shade counts its own. */
let elapsedTick = null;

function nowClock(milliseconds) {
  const whole = Math.max(0, Math.round(milliseconds / 1000));
  const minutes = Math.floor(whole / 60);
  const seconds = whole % 60;
  return minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
}

/**
 * The face the owner wears. One face rather than one per mod: every Now mod is a glyph, a
 * short reading and — for a transfer — a line along the bottom, which is the same two-run
 * shape every resting face on this bar has.
 */
function paintNowFace() {
  const owner = nowOwner();
  const live = nowLive[owner];
  // Written as data rather than as a class per mod: the CSS needs to know *which* transfer it
  // is drawing to point the glyph the right way, and two classes for one answer is two things
  // to keep in step.
  nowPill.dataset.now = owner || '';
  nowPill.classList.toggle('recording', owner === 'recording');
  nowPill.classList.toggle('transferring', owner === 'download' || owner === 'upload');
  nowPill.classList.toggle('paused', Boolean(live && live.isPaused));

  if (owner === 'torch') {
    nowGlyph.innerHTML = TORCH_GLYPH;
    nowReading.textContent = torch && torch.dimmable ? torch.step + '/' + torch.steps : '';
    nowLine.classList.remove('showing');
    return;
  }
  if (owner === 'recording') {
    nowGlyph.innerHTML = NOW_GLYPHS.recording;
    nowReading.textContent = nowClock(Date.now() - (live ? live.since : Date.now()));
    nowLine.classList.remove('showing');
    return;
  }
  if (owner === 'download' || owner === 'upload') {
    const isDone = Boolean(live && live.isDone);
    nowGlyph.innerHTML = isDone ? NOW_GLYPHS.done : NOW_GLYPHS[owner];
    nowReading.textContent = live ? live.detail : '';
    nowLine.classList.add('showing');
    const share = live && live.total ? Math.min(1, live.done / live.total) : 0;
    nowLineDone.style.width = (isDone ? 100 : Math.round(share * 100)) + '%';
  }
}

function paintNowReading() {
  paintNowFace();
}

/**
 * The elapsed reading, ticking only while a recording is the thing being shown. A second is
 * the resolution the number is written at, so a second is how often it is asked for — and the
 * mirror is not stirred for it: the text changes inside a bubble whose box does not.
 */
function fitElapsedTick() {
  const isRecording = nowOwner() === 'recording' && !nowLive.recording?.isPaused;
  if (isRecording && elapsedTick === null) {
    elapsedTick = setInterval(() => {
      const live = nowLive.recording;
      if (!live) return;
      const reading = nowClock(Date.now() - live.since);
      nowReading.textContent = reading;
      if (nowOpen) nowElapsed.textContent = reading;
    }, 1000);
    return;
  }
  if (!isRecording && elapsedTick !== null) {
    clearInterval(elapsedTick);
    elapsedTick = null;
  }
}

/**
 * A Now mod arriving, changing or ending. Everything about which bubble is out, how wide it
 * stands and what it says is worked out from the order here, so this is the one place a mod's
 * life is written down — the flight out happens when the first one starts and the flight home
 * when the last one ends, whichever mods those happen to be.
 */
function setNowMod(name, payload) {
  const had = nowOrder.length;
  if (payload) {
    nowLive[name] = payload;
    // A dismissed mod keeps being tracked and keeps being out of the order: the swipe said
    // "not on my bar", not "stop happening", and the difference is what stops a transfer's
    // next tick from bringing the bubble straight back.
    if (!nowOrder.includes(name) && !nowDismissed[name]) nowOrder.push(name);
  } else {
    delete nowLive[name];
    delete nowDismissed[name];
    nowOrder = nowOrder.filter(mod => mod !== name);
  }
  fitElapsedTick();
  if (!had && nowOrder.length) {
    lightNow();
    return;
  }
  if (had && !nowOrder.length) {
    douseNow();
    return;
  }
  if (!nowOrder.length) return;
  paintNowFace();
  // The width belongs to the mod, so a swap between two of them is a growth like any other.
  fitNowWidth();
  fitNowProxy();
}

/**
 * The light coming on. The pill is born a small drop in the true middle of the
 * screen — inside the punch hole, inside the bubble drawn around it — and thrown out
 * along the bar to its spot. It hits that spot, the bubble at the cutout closes up
 * behind it, and the pill opens out to the right off the stop it ran into. The width
 * is the consequence; the arrival is the cause.
 *
 * Both shapes are in one goo layer now, so the drop genuinely separates from the
 * bubble on the way out and is genuinely taken back into it on the way home. That is
 * the thing two windows could never do, and the reason this page is the way it is.
 */
function lightNow() {
  traceEvent('now out: ' + nowOwner());
  stopNowFlight();
  paintNowReading();
  root.classList.add('now-flying');
  setNowWidth(nowDrop(), 0, 'linear');
  setNowFly(nowFlight(), 0, 'linear');
  // Born at the hole and no bigger than it, then let go on the next frame — the
  // starting point has to be a frame the browser has actually drawn, or there is
  // nothing to move away from.
  root.style.setProperty('--now-pop', 0.5);
  void nowPill.offsetWidth;
  requestAnimationFrame(() => {
    nowPill.classList.add('lit');
    root.style.setProperty('--now-pop', 1);
    setNowFly(0, NOW_TRAVEL, 'var(--ease-split)');
    fitNowProxy();
  });
  nowAfter(NOW_TRAVEL * NOW_CLEAR, () => window.onTorchChip(true));
  nowAfter(NOW_TRAVEL * NOW_LAND, () => {
    setNowWidth(nowResting(), NOW_EXPAND, 'var(--ease-split)');
    // The knock of hitting the stop, which is also what opens the pill out.
    bridge.triggerHaptic('tap');
  });
  // The reading is the last thing in: it belongs to width, and until the pill has
  // finished opening out there is no width for it to belong to.
  nowAfter(NOW_TRAVEL * NOW_LAND + NOW_EXPAND, () => {
    root.classList.remove('now-flying');
    fitNowProxy();
  });
}

/**
 * The light going out, the same journey backwards. The pill gives its width back
 * first, then leaves its spot and falls home into the punch hole, and the bubble
 * there opens up again as it arrives — because it is the thing arriving.
 */
function douseNow() {
  traceEvent('now home');
  stopNowFlight();
  if (nowOpen) closeNowPanel();
  // First, before the width goes: the reading belongs to width the pill is about to
  // give back, and left on it was squeezed into the drop and carried home in place of
  // the glyph — which is the one thing that has to survive the journey, because it is
  // what the whole flight is about.
  root.classList.add('now-flying');
  setNowWidth(nowDrop(), NOW_COLLAPSE, 'var(--ease-split)');
  nowAfter(NOW_COLLAPSE * 0.35, () => {
    root.style.setProperty('--now-pop', 0.5);
    // The glyph leaves first and the pill is pulled after it, the same way the glyph
    // is what arrives and the width only follows. It is the thing with the meaning
    // in it, so it is the thing with the momentum.
    nowGlyph.classList.remove('dragging');
    void nowGlyph.offsetWidth;
    root.style.setProperty('--now-drag-ms', NOW_RETURN + 'ms');
    nowGlyph.classList.add('dragging');
    setNowFly(nowFlight(), NOW_RETURN, 'var(--ease-grow)');
    // `lit` stays on for the whole way home, and that is the point: it is what the
    // skin reads to decide this bubble is one of the shapes in the row, so taking it
    // off at the start of the return — which is what it used to do — meant the drop
    // spent the entire journey outside the liquid and arrived at the bubble as a
    // separate object that simply faded out on top of it. It goes out on arrival
    // instead, and it does not fade on the way: a drop coming home is absorbed, and
    // a half-transparent shape is erased by the alpha contrast rather than merged.
    fitNowProxy();
    // The bubble at the cutout opens up as the drop falls back into it.
    // At the start of the return and not part-way through it. The row is not waiting
    // for the drop to arrive — the bar is free the moment the light leaves its spot —
    // and anything else reads as the bubbles hanging back before remembering to move.
    window.onTorchChip(false);
    // And the bubble takes the drop the same way it takes a circle: as a merge it
    // measures rather than a bounce it plays. The stretch grows with however much of
    // the drop is inside it — liquid added to a drop of water jumps its tension and
    // contains itself again — and the light only goes out once the drop is deep
    // enough inside that nothing is seen going out.
    catchInto(nowPill, pill, {
      swallowed: () => {
        // Home, dark, and put back on its spot ready to be thrown out again. It goes
        // out on the spot rather than fading, because by now it is inside the bubble
        // and there is nothing left to see it happen against.
        root.style.setProperty('--now-fade-ms', '0ms');
        nowPill.classList.remove('lit');
        nowGlyph.classList.remove('dragging');
        setNowFly(0, 0, 'linear');
        fitNowProxy();
        // Taking `lit` off is what takes the glass off with it: an unlit Now bubble is
        // not one of the shapes the mirror measures, so its pane is sent as nothing and
        // the host clears it — a transparent view otherwise goes on blurring.
        requestAnimationFrame(() => root.style.removeProperty('--now-fade-ms'));
      },
    });
  });
}

function nowSlotX(index) {
  return index * (NOW_SLOT + NOW_SLOT_GAP) + NOW_SLOT / 2;
}

function buildNowSlider() {
  const count = torch && torch.dimmable ? torch.steps : 1;
  if (nowFlow.children.length === count + 1) return;
  nowRail.textContent = '';
  nowFlow.textContent = '';
  for (let index = 0; index < count; index += 1) {
    for (const layer of [nowRail, nowFlow]) {
      const slot = document.createElement('div');
      slot.className = 'now-slot';
      slot.style.left = nowSlotX(index) + 'px';
      layer.appendChild(slot);
    }
  }
  nowDot = document.createElement('div');
  nowDot.className = 'now-dot';
  nowFlow.appendChild(nowDot);
  nowSlider.style.width =
    (count * NOW_SLOT + (count - 1) * NOW_SLOT_GAP) + 'px';
}

/**
 * The dot where it is, and every step swollen by how near the dot is to it. A step
 * the dot has reached is full size; one a whole step away is nothing at all; in
 * between, both are part-grown and the blur bridges them — which is the liquid.
 */
function paintNowSlider() {
  if (!nowDot) return;
  nowDot.style.left = nowSlotX(nowDotAt) + 'px';
  Array.from(nowFlow.querySelectorAll('.now-slot')).forEach((slot, index) => {
    slot.style.setProperty('--near', Math.max(0, 1 - Math.abs(nowDotAt - index)));
  });
}

function nowSlideTo(clientX) {
  const count = nowFlow.querySelectorAll('.now-slot').length;
  const box = nowSlider.getBoundingClientRect();
  const at = (clientX - box.left - NOW_SLOT / 2) / (NOW_SLOT + NOW_SLOT_GAP);
  return Math.min(count - 1, Math.max(0, at));
}

nowSlider.addEventListener('touchstart', event => {
  if (!torch || !torch.dimmable) return;
  nowSliding = true;
  nowSlider.classList.add('dragging');
  nowDotAt = nowSlideTo(event.touches[0].clientX);
  paintNowSlider();
}, { passive: true });

nowSlider.addEventListener('touchmove', event => {
  if (!nowSliding) return;
  nowDotAt = nowSlideTo(event.touches[0].clientX);
  paintNowSlider();
}, { passive: true });

nowSlider.addEventListener('touchend', () => {
  if (!nowSliding) return;
  nowSliding = false;
  nowSlider.classList.remove('dragging');
  nowDotAt = Math.round(nowDotAt);
  paintNowSlider();
  bridge.setTorch(nowDotAt + 1);
  bridge.triggerHaptic('tap');
});

nowSlider.addEventListener('click', event => event.stopPropagation());

/**
 * How far this bubble has to travel to stand in the middle of the screen. An open
 * bubble is centred wherever its closed one stood — the media tab does it and so
 * does every other Active state — and this one starts out by the clock, so being
 * centred is a move rather than a width. It is written as the flight offset the
 * pill already owns rather than as a second transform: one property, one owner,
 * and the way home is the same offset going back to nothing.
 */
function nowPanelShift() {
  return window.innerWidth / 2 - NOW_PANEL.width / 2 - nowLeft;
}

function openNowPanel() {
  nowOpen = true;
  // Which half of the panel is showing belongs to the mod, and the panel is one box either
  // way: it is the same bubble held down, not a second thing opening over it.
  const owner = nowOwner();
  nowPill.classList.toggle('recording-panel', owner === 'recording');
  document.getElementById('now-label').textContent =
    owner === 'recording' ? 'Recording' : 'Flashlight';
  if (owner === 'recording' && nowLive.recording) {
    nowElapsed.textContent = nowClock(Date.now() - nowLive.recording.since);
  }
  buildNowSlider();
  if (!nowSliding) nowDotAt = (torch ? torch.step : 1) - 1;
  paintNowSlider();
  const shift = nowPanelShift();
  root.style.setProperty('--now-fly', shift + 'px');
  root.style.setProperty('--now-fly-ms', NOW_PANEL.ms + 'ms');
  root.style.setProperty('--now-fly-ease', 'var(--ease-grow)');
  fitNowProxy();
  // The skin is mirrored off this box every frame and the loop only runs while
  // something says it is moving. A class that changes the size says nothing, so
  // without this the blob stayed at the size it had when the panel opened and the
  // shape left standing there afterwards was the panel's, not the pill's.
  stirLiquid(NOW_PANEL.ms + 120);
  // The filter's region is the layer's own box, so a shape taller than the layer is
  // cut off at its edge rather than drawn: the panel is twice the height of the row
  // it is standing in and the layer has to make room for it before it opens.
  root.style.setProperty('--liquid-tall', (NOW_PANEL.height + 24) + 'px');
  requestAnimationFrame(() => {
    nowPill.classList.add('open');
    showNowFace('panel');
  });
}

export function closeNowPanel() {
  nowOpen = false;
  nowPill.classList.remove('open', 'recording-panel');
  showNowFace('closed');
  // Back to its spot by the clock, which paints the glass and stirs the skin on the
  // same curve — the panel was centred by this same offset on the way out.
  setNowFly(0, NOW_PANEL.ms, 'var(--ease-grow)');
  fitNowProxy();
  stirLiquid(NOW_PANEL.ms + 120);
  // Back to the row's own height once the panel has finished coming in, and not
  // before: a filter region that shrinks mid-collapse clips what is still moving.
  nowAfter(NOW_PANEL.ms + 40, () => root.style.removeProperty('--liquid-tall'));
}

/**
 * A tap on the open panel closes it, exactly as a tap on an open bubble does. The panel
 * is the one part of this bubble reached the ordinary way — the rail inside it is
 * dragged — so this is a listener rather than a branch in nowTouch, and the rail's own
 * handler stops the click before it gets here.
 */
nowPill.addEventListener('click', () => {
  if (!nowOpen) return;
  bridge.triggerHaptic('tap');
  closeNowPanel();
});

/**
 * Whether a point on the bar belongs to this bubble.
 *
 * Its own box and nothing else — not the proxy that heard the touch, not what the DOM
 * has stacked over it. Both proxies carry a grab margin, the two bubbles rest six
 * pixels apart, and the light now reaches most of the way to the punch hole, so the
 * margins overlap and the window that hears a finger is a race. Where the finger
 * landed is not.
 */
export function nowHolds(x, y) {
  if (!nowPill.classList.contains('lit')) return false;
  const box = nowPill.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}

/**
 * The light's own Tap and Haptic, driven straight from the forwarded stream rather
 * than from listeners on the element.
 *
 * Everything else on this page is reached by rebuilding the DOM events the browser
 * would have made and dispatching them at whatever is under the point, which is right
 * for the row: it is a dozen elements deep and every one of them has its own reason to
 * hear a finger. This bubble is one shape with two gestures, and going the long way
 * round for it meant a tap could be answered by the row instead — hit-testing, event
 * bubbling and a retargeted click are three places for that to go wrong and there was
 * nothing to gain from any of them. The panel is the exception and still gets real
 * events: the intensity rail is dragged, and that is the DOM's own to handle.
 */
let nowHeld = false;
let nowHoldTimer = null;
let nowDownAt = null;

/**
 * Swiped up: the bubble goes home to the punch hole and what it was showing is let go of.
 *
 * It is a Push in the sense states.md means — the mod comes off the bubble, not out of
 * existence — so the recording goes on recording and the file goes on arriving. If something
 * else is live it takes the bubble over on the spot; if nothing is, the shape flies home and
 * is taken back into the bubble at the cutout, measured rather than timed.
 */
function flingNowHome() {
  const owner = nowOwner();
  if (!owner) return;
  nowDismissed[owner] = true;
  nowOrder = nowOrder.filter(mod => mod !== owner);
  bridge.triggerHaptic('tap');
  if (nowOrder.length) {
    // Handed straight over: there is still something happening, and a bubble that flew home
    // and came back out for it would be one journey saying two things.
    paintNowFace();
    fitNowWidth();
    fitNowProxy();
    return;
  }
  stopNowFlight();
  if (nowOpen) closeNowPanel();
  root.classList.add('now-flying');
  fitNowProxy();
  const from = nowPill.getBoundingClientRect();
  const to = pill.getBoundingClientRect();
  const dx = (to.left + to.width / 2) - (from.left + from.width / 2);
  const dy = (to.top + to.height / 2) - (from.top + from.height / 2);
  const whole = NOW_FLING_PINCH + NOW_FLING;
  const turn = NOW_FLING_PINCH / whole;
  const drop = Math.min(to.width / from.width, to.height / from.height) * 0.9;
  // Two animations on two properties rather than one keyframe list, because a keyframe's
  // easing owns every property in it and these two want opposite ones: the closing winds up
  // and overshoots, the travel crawls flat and then opens out.
  const flight = [
    nowPill.animate(
      [
        { scale: 1, offset: 0, easing: NOW_FLING_PINCH_EASE },
        { scale: NOW_FLING_PINCH_SCALE, offset: turn, easing: NOW_FLING_EASE },
        { scale: drop, offset: 1 },
      ],
      { duration: whole, fill: 'forwards' }
    ),
    nowPill.animate(
      [
        { translate: '0px 0px', offset: 0, easing: 'linear' },
        {
          translate: (dx * NOW_FLING_CRAWL * turn).toFixed(1) + 'px ' +
            (dy * NOW_FLING_CRAWL * turn).toFixed(1) + 'px',
          offset: turn,
          easing: NOW_FLING_EASE,
        },
        { translate: dx.toFixed(1) + 'px ' + dy.toFixed(1) + 'px', offset: 1 },
      ],
      { duration: whole, fill: 'forwards' }
    ),
  ];
  // The mirror only runs while something asks for frames, and the skin and the glass are both
  // read off this box: a flight that stirred nothing would leave a frosted rectangle standing
  // at the clock while the shape crossed the screen.
  stirLiquid(whole + 400);
  // The bar is free the moment the shape leaves its spot, not when it arrives.
  window.onTorchChip(false);
  catchInto(nowPill, pill);
  nowAfter(NOW_FLING_PINCH, () => watchNowCentre(flight));
}

/**
 * The merge ends the frame the drop's centre reaches the bubble's. Watched rather than timed:
 * the flight is eased and its distance depends on where the bubble is standing, so that
 * instant is not a number that can be written down beforehand.
 */
function watchNowCentre(flight) {
  const mine = nowPill.getBoundingClientRect();
  const bubble = pill.getBoundingClientRect();
  if (mine.left + mine.width / 2 > bubble.left + bubble.width / 2) {
    nowAfter(0, () => requestAnimationFrame(() => watchNowCentre(flight)));
    return;
  }
  releaseCatch(nowPill);
  root.style.setProperty('--now-fade-ms', '0ms');
  nowPill.classList.remove('lit');
  fitNowProxy();
  requestAnimationFrame(() => {
    flight.forEach(move => move.cancel());
    root.classList.remove('now-flying');
    setNowFly(0, 0, 'linear');
    setNowWidth(nowDrop(), 0, 'linear');
    root.style.removeProperty('--now-fade-ms');
  });
}

export function nowTouch(action, x, y) {
  if (action === 'down') {
    nowHeld = false;
    nowDownAt = { x, y };
    nowPill.classList.add('pressing');
    clearTimeout(nowHoldTimer);
    nowHoldTimer = setTimeout(() => {
      nowHeld = true;
      nowPill.classList.remove('pressing');
      bridge.triggerHaptic('expand');
      // There is no app behind this one to leave for — the torch is the camera
      // service and has no face of its own — so the hold opens what the bubble owns,
      // and what it adds over the tap is the growth and the buzz that say the finger
      // was heard before anything happened.
      if (!nowOpen) openNowPanel();
    }, HOLD_MILLIS);
    return;
  }
  if (action === 'move') {
    // The same wander that calls off the row's hold calls off this one.
    if (nowDownAt && Math.hypot(x - nowDownAt.x, y - nowDownAt.y) > PROXY_TAP_SLOP) {
      clearTimeout(nowHoldTimer);
      nowPill.classList.remove('pressing');
    }
    return;
  }
  clearTimeout(nowHoldTimer);
  nowPill.classList.remove('pressing');
  const travelled = nowDownAt ? Math.hypot(x - nowDownAt.x, y - nowDownAt.y) : Infinity;
  const lifted = nowDownAt ? nowDownAt.y - y : 0;
  nowDownAt = null;
  // Upwards past the threshold is a dismissal whatever else the finger did on the way: it is
  // the one gesture on this bubble that means the same thing it means on every other one.
  if (action === 'up' && !nowOpen && lifted > NOW_SWIPE) {
    flingNowHome();
    return;
  }
  if (action !== 'up' || nowHeld || travelled >= PROXY_TAP_SLOP) return;
  bridge.triggerHaptic('expand');
  if (nowOpen) {
    closeNowPanel();
    return;
  }
  // What a tap means belongs to the mod. The torch has a panel behind it and nothing else to
  // do; a recording has one obvious act and it is the one you want without looking — pause,
  // and again to carry on. A transfer has none: there is nothing to do to a file that is on
  // its way, so the tap opens the app that is moving it.
  const owner = nowOwner();
  if (owner === 'recording') {
    pressRecording();
    return;
  }
  if (owner === 'download' || owner === 'upload') {
    const live = nowLive[owner];
    if (live && live.key) bridge.openNotification(live.key);
    return;
  }
  openNowPanel();
}

/**
 * Pause or resume, by pressing the recorder's own button rather than by telling the recorder
 * anything of ours. Which button that is is read off its title, because the notification is
 * the only place either word is ever written and it is written in the phone's language.
 */
function pressRecording() {
  const live = nowLive.recording;
  if (!live || !live.actions) return;
  const wanted = live.isPaused ? /resume|fortsetzen|weiter/i : /pause|anhalten/i;
  const button = live.actions.find(action => wanted.test(action.title));
  if (button) bridge.recordingAction(button.index);
}

/** Where its left edge rests: the host measures it off One UI's own chip. */
window.setNowLeft = value => {
  nowLeft = value;
  root.style.setProperty('--now-left', value + 'px');
};

window.onTorchUpdate = payload => {
  const wasLit = Boolean(torch);
  torch = payload;
  setNowMod('torch', payload);
  if (torch && wasLit && nowOpen && !nowSliding) {
    nowDotAt = torch.step - 1;
    buildNowSlider();
    paintNowSlider();
  }
};

/**
 * A recording and a transfer, pushed together because the host reads them off the same shade
 * in one pass. A transfer that reaches its total is not dropped on the spot: it stands there
 * wearing a tick for a moment first, or the only thing the person ever sees of a finished
 * download is the bubble disappearing.
 */
window.onNowMods = payload => {
  setNowMod('recording', payload.recording || null);

  const transfer = payload.transfer || null;
  const kind = transfer ? transfer.mod : (nowLive.download ? 'download' : 'upload');
  const standing = nowLive[kind];
  if (transfer) {
    const isDone = transfer.total > 0 && transfer.done >= transfer.total;
    setNowMod(transfer.mod, Object.assign({ isDone }, transfer));
    if (isDone) holdTheTick(transfer.mod);
    return;
  }
  // Gone from the shade. If it was nearly there when it went, it finished — apps take a
  // progress notification away the moment it completes — so the tick is owed either way.
  if (standing && !standing.isDone) {
    const share = standing.total ? standing.done / standing.total : 0;
    if (share >= 0.95) {
      setNowMod(kind, Object.assign({}, standing, { isDone: true }));
      holdTheTick(kind);
      return;
    }
  }
  if (standing && standing.isDone) return;
  setNowMod('download', null);
  setNowMod('upload', null);
};

let tickTimer = null;
function holdTheTick(kind) {
  clearTimeout(tickTimer);
  tickTimer = setTimeout(() => setNowMod(kind, null), NOW_DONE_DWELL);
}

/**
 * Stop, which is the recorder's own button again. The panel closes on it rather than waiting
 * to be told the recording ended: the shade will say so a moment later, and a panel still
 * standing open over a recording that has stopped is the interface disagreeing with itself.
 */
document.getElementById('now-stop').addEventListener('click', event => {
  event.stopPropagation();
  const live = nowLive.recording;
  const button = live && live.actions &&
    live.actions.find(action => /stop|beenden|stopp/i.test(action.title));
  if (button) bridge.recordingAction(button.index);
  bridge.triggerHaptic('tap');
  closeNowPanel();
});
