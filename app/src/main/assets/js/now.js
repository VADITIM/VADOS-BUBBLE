import { PROXY_TAP_SLOP } from './bridge.js';
import { clockPill, fitClockProxy } from './clock.js';
import { stirLiquid, traceEvent } from './liquid.js';
import { toClosed } from './row.js';
import { closeStatusPanel, setTransfer, statusOpen } from './status.js';
import { HOLD_MILLIS, bridge, root, shared } from './state.js';

/**
 * The Now mods — a torch burning, a recording running, a file in flight — and the bubble they
 * are shown in, which is the clock's.
 *
 * There is no Now bubble on the bar any more. It stood on One UI's flashlight chip out by the
 * clock, which meant two bubbles wanting the same corner: the clock left whenever a mod came
 * out, the row stood aside for both of them, and most of the motion on that end of the bar was
 * the two of them getting out of each other's way. One bubble carries both instead — the time
 * when nothing is happening, the thing that is happening when something is — and the Now bubble
 * survives only on the lock screen, where it has the room to be a bubble.
 *
 * The time is not covered up: it stands aside first (the digits fade and walk left) and the box
 * grows to the mod afterwards, which is the glyph-as-cause rule applied to a whole reading.
 */
const nowGlyph = document.getElementById('now-glyph');
const nowReading = document.getElementById('now-reading');
const nowSlider = document.getElementById('now-slider');
const nowRail = document.getElementById('now-rail');
const nowFlow = document.getElementById('now-flow');
const clockTime = document.getElementById('clock-time');
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
// The media player's own box, mirroring SIZES.player in state.js: the torch is only ever seen open now, so it stands beside the other panels rather than as a widened chip, and two open bubbles at two widths read as two different kinds of thing.
const NOW_PANEL = { width: 300, height: 190, ms: 420 };
// The CSS is told the panel's size rather than repeating it: this is the number the
// host is given for the glass and for the proxy, and three copies of it is three
// chances to move one and not the others.
root.style.setProperty('--now-panel-width', NOW_PANEL.width + 'px');
root.style.setProperty('--now-panel-height', NOW_PANEL.height + 'px');

/** How long the time takes to stand aside, and how long the box then takes to grow. */
const NOW_STAND_ASIDE = 220;
const NOW_EXPAND = 300;

let torch = null;

/**
 * What is happening, in the order it started happening.
 *
 * The Now bubble carries one thing at a time and the row's rule decides which: first come,
 * first served. A recording that started before a torch keeps the bubble until it stops,
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
  recording: 112,
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

const NOW_GLYPHS = {
  recording: '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="7"/></svg>',
};

function nowOwner() {
  return nowOrder[0];
}

export let nowOpen = false;
let nowDot = null;
let nowDotAt = 0;
let nowSliding = false;

/** One arrival is a run of steps, and a second mod cancels the first outright. */
let nowTimers = [];
function nowAfter(milliseconds, step) {
  nowTimers.push(setTimeout(step, milliseconds));
}
function stopNowSteps() {
  nowTimers.forEach(clearTimeout);
  nowTimers = [];
}

/**
 * The torch has no resting face any more: a light that is on is shown as its panel and nothing else. It is still a Now mod — it owns the bubble first-come like the other three — it simply arrives open, which is why this is called wherever the owner can change and not only on the arrival.
 */
function keepTorchOpen() {
  if (nowOwner() === 'torch' && !nowOpen) openNowPanel();
}

function showNowFace(name) {
  for (const [key, face] of Object.entries(nowFaces)) {
    face.classList.toggle('showing', key === name);
  }
}

/**
 * How wide the bubble stands for the mod it is carrying. The mod's own number and nothing
 * measured: this bubble starts at the far left of the bar and grows right into empty space —
 * the row stands aside for it — so there is no edge for it to negotiate with any more, and the
 * width that used to be worked out against the row's left edge is now simply what the content
 * is worth.
 */
function nowWidthFor(owner) {
  return NOW_WIDTHS[owner] || 116;
}

/**
 * What the bubble measures as right now, written back into the property that sizes it.
 *
 * A transition needs a start the browser has already computed, and this bubble's resting width
 * is `auto` — the digits' own. So the measured width is written first, in the same breath as
 * whatever is about to change it, and the growth eases off a real number instead of jumping.
 */
function pinClockWidth() {
  root.style.setProperty('--clock-width-ms', '0ms');
  root.style.setProperty('--clock-width', Math.round(clockPill.getBoundingClientRect().width) + 'px');
}

function setClockWidth(next, milliseconds) {
  root.style.setProperty('--clock-width-ms', milliseconds + 'ms');
  root.style.setProperty('--clock-width', next + 'px');
  stirLiquid(milliseconds + 120);
  // The proxy is the box, and the box has just changed: a window left at the old width is a
  // bubble that can be seen and not touched at one end of itself.
  nowAfter(milliseconds, fitClockProxy);
}

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
 * The face the owner wears. One face rather than one per mod: every Now mod is a glyph and a
 * short reading, which is the same two-run shape every resting face on this bar has.
 */
function paintNowFace() {
  const owner = nowOwner();
  const live = nowLive[owner];
  clockPill.dataset.now = owner || '';
  clockPill.classList.toggle('recording', owner === 'recording');
  clockPill.classList.toggle('paused', Boolean(live && live.isPaused));

  if (owner === 'torch') {
    nowGlyph.innerHTML = TORCH_GLYPH;
    nowReading.textContent = torch && torch.dimmable ? torch.step + '/' + torch.steps : '';
    return;
  }
  if (owner === 'recording') {
    nowGlyph.innerHTML = NOW_GLYPHS.recording;
    nowReading.textContent = nowClock(Date.now() - (live ? live.since : Date.now()));
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
    wakeNowMod();
    return;
  }
  if (had && !nowOrder.length) {
    restNowMod();
    return;
  }
  if (!nowOrder.length) return;
  paintNowFace();
  // The width belongs to the mod, so a swap between two of them is a growth like any other.
  setClockWidth(nowWidthFor(nowOwner()), NOW_EXPAND);
  fitClockProxy();
  keepTorchOpen();
}

/**
 * A mod taking the clock bubble over.
 *
 * Two beats, never one, and in this order: the time stands aside — the digits fade and walk
 * left, which is the reading leaving rather than being covered up — and only once it has gone
 * does the box grow to the mod. That is the glyph-as-cause rule with a whole reading in the
 * cause's place: the width is what the arrival did, not what announced it.
 *
 * There is no flight, and that is the point of the change. The mods used to be a second bubble
 * thrown out of the punch hole to a spot of its own; they are shown in a bubble that is already
 * standing there, so what used to be a journey is now a hand-over inside one shape.
 */
function wakeNowMod() {
  traceEvent('now in: ' + nowOwner());
  stopNowSteps();
  paintNowFace();
  pinClockWidth();
  // The row gives up the bar this bubble is about to grow into, and it does it now rather than
  // when the width arrives: the bar is claimed the moment the time starts leaving, or the two
  // bubbles reach for the same pixels for the length of the growth.
  window.onNowStanding(true);
  requestAnimationFrame(() => {
    clockPill.classList.add('now-live');
    stirLiquid(NOW_STAND_ASIDE + 120);
  });
  nowAfter(NOW_STAND_ASIDE, () => {
    setClockWidth(nowWidthFor(nowOwner()), NOW_EXPAND);
    // The torch has no closed face to show on the way in — it arrives open — and showing one
    // for a frame first would be the bubble changing its mind in public.
    if (nowOwner() !== 'torch') showNowFace('closed');
    // The knock of the mod landing, which is also what opens the bubble out.
    bridge.triggerHaptic('tap');
    keepTorchOpen();
  });
}

/**
 * The last mod ending, the same hand-over backwards: the box gives the width back first and the
 * time comes home into it afterwards. A mod that ends because its app was killed leaves this
 * way too — a face swapped for the time on the spot reads as the bubble forgetting.
 */
function restNowMod() {
  traceEvent('now out');
  stopNowSteps();
  if (nowOpen) closeNowPanel();
  showNowFace('');
  // Back to what the digits are worth, in pixels so the way back eases as the way out did, and
  // released to `auto` once it is there — an offset at rest is removed, never written as zero.
  clockPill.classList.remove('now-live', 'recording', 'paused');
  const time = clockTime.getBoundingClientRect().width;
  setClockWidth(Math.round(time + 22), NOW_EXPAND);
  window.onNowStanding(false);
  nowAfter(NOW_EXPAND + 40, () => {
    root.style.removeProperty('--clock-width');
    root.style.removeProperty('--clock-width-ms');
    clockPill.dataset.now = '';
    fitClockProxy();
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
 * How far this bubble has to travel to stand in the middle of the screen. An open bubble is
 * centred wherever its closed one stood — the media tab does it and so does every other Active
 * state — and this one stands at the far left of the bar, so being centred is a move rather
 * than a width. It is written as the same offset the clock's own birth flight uses: one
 * property, one owner, and the way home is that offset going back to nothing.
 */
function nowPanelShift() {
  return window.innerWidth / 2 - NOW_PANEL.width / 2 - clockPill.offsetLeft;
}

/** Where the panel stands, on the property the clock's arrival already owns. */
function setPanelShift(next, milliseconds) {
  root.style.setProperty('--clock-fly', next + 'px');
  root.style.setProperty('--clock-fly-ms', milliseconds + 'ms');
  stirLiquid(milliseconds + 120);
}

function openNowPanel() {
  // One bubble is extended at a time — see becomeExtended() in row.js. Whichever is arriving
  // puts the others back to idle, rather than two panels standing open on one screen.
  if (shared.state === 'extended') toClosed();
  if (statusOpen) closeStatusPanel();
  nowOpen = true;
  // Which half of the panel is showing belongs to the mod, and the panel is one box either
  // way: it is the same bubble held down, not a second thing opening over it.
  const owner = nowOwner();
  clockPill.classList.toggle('recording-panel', owner === 'recording');
  document.getElementById('now-label').textContent =
    owner === 'recording' ? 'Recording' : 'Flashlight';
  if (owner === 'recording' && nowLive.recording) {
    nowElapsed.textContent = nowClock(Date.now() - nowLive.recording.since);
  }
  buildNowSlider();
  if (!nowSliding) nowDotAt = (torch ? torch.step : 1) - 1;
  paintNowSlider();
  setPanelShift(nowPanelShift(), NOW_PANEL.ms);
  fitClockProxy();
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
    clockPill.classList.add('open');
    showNowFace('panel');
  });
}

export function closeNowPanel() {
  nowOpen = false;
  clockPill.classList.remove('open', 'recording-panel');
  showNowFace('closed');
  // Back to its spot at the left end of the bar, which paints the glass and stirs the skin on
  // the same curve — the panel was centred by this same offset on the way out.
  setPanelShift(0, NOW_PANEL.ms);
  fitClockProxy();
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
clockPill.addEventListener('click', () => {
  if (!nowOpen) return;
  // Except the torch's, which was never opened and so is not one that can be shut: while the light burns this is the only face it has, and it goes away by the light going out.
  if (nowOwner() === 'torch') return;
  bridge.triggerHaptic('tap');
  closeNowPanel();
});

/**
/**
 * Whether the mods currently own the clock bubble, which is what decides whose gesture a touch
 * on it is: a bubble showing the time is held out to the clock app, and one showing a recording
 * is tapped to pause it. The box is the same box either way — only the meaning changes.
 */
export function nowOwnsClock() {
  return Boolean(nowOwner()) || nowOpen;
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
 * Swiped up: the mod comes off the bubble and the time comes back.
 *
 * It is a Push in the sense states.md means — the mod comes off the bubble, not out of
 * existence — so the recording goes on recording and the file goes on arriving. There is no
 * flight home any more: the bubble the mod was standing in is the clock, and the clock is not
 * going anywhere. What leaves is the mod, and what arrives in its place is the time.
 */
function dismissNowMod() {
  const owner = nowOwner();
  if (!owner) return;
  nowDismissed[owner] = true;
  nowOrder = nowOrder.filter(mod => mod !== owner);
  bridge.triggerHaptic('dismiss');
  if (nowOrder.length) {
    // Handed straight over: there is still something happening, and a bubble that went back to
    // the time and was taken over again a frame later would be one move saying two things.
    paintNowFace();
    setClockWidth(nowWidthFor(nowOwner()), NOW_EXPAND);
    keepTorchOpen();
    return;
  }
  restNowMod();
}
export function nowTouch(action, x, y) {
  if (action === 'down') {
    nowHeld = false;
    nowDownAt = { x, y };
    clockPill.classList.add('pressing');
    clearTimeout(nowHoldTimer);
    nowHoldTimer = setTimeout(() => {
      nowHeld = true;
      clockPill.classList.remove('pressing');
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
      clockPill.classList.remove('pressing');
    }
    return;
  }
  clearTimeout(nowHoldTimer);
  clockPill.classList.remove('pressing');
  const travelled = nowDownAt ? Math.hypot(x - nowDownAt.x, y - nowDownAt.y) : Infinity;
  const lifted = nowDownAt ? nowDownAt.y - y : 0;
  nowDownAt = null;
  // Upwards past the threshold is a dismissal whatever else the finger did on the way: it is
  // the one gesture on this bubble that means the same thing it means on every other one.
  if (action === 'up' && !nowOpen && lifted > NOW_SWIPE) {
    dismissNowMod();
    return;
  }
  if (action !== 'up' || nowHeld || travelled >= PROXY_TAP_SLOP) return;
  bridge.triggerHaptic('expand');
  if (nowOpen) {
    if (nowOwner() !== 'torch') closeNowPanel();
    return;
  }
  // What a tap means belongs to the mod. The torch has a panel behind it and nothing else to
  // do; a recording has one obvious act and it is the one you want without looking — pause,
  // and again to carry on.
  if (nowOwner() === 'recording') {
    pressRecording();
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
 * A recording and a transfer, pushed together because the host reads them off the same shade in
 * one pass — and shown in two bubbles, because they are two different kinds of true. A recording
 * is something the person started and it stands in the clock with the torch; a file in flight is
 * something the phone is doing for whatever it is attached to, and that is the Status bubble's
 * subject. So the payload arrives here and its transfer half is handed straight over, rather than
 * the host learning that one shade reading now feeds two places.
 */
window.onNowMods = payload => {
  setNowMod('recording', payload.recording || null);
  setTransfer(payload.transfer || null);
};

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
