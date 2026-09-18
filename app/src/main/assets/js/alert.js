import { openCurrent } from './mods/notification.js';
import { HOLD_BLOCK, pillHolds, toy, untoy } from './motion.js';
import { setSize, toClosed } from './row.js';
import { fitLabels } from './labels.js';
import { HALO_MILLIS, startHalo, stirLiquid } from './liquid.js';
import { slingInto } from './sling.js';
import { statsBox, statsIn, statsOut } from './status.js';
import { SIZES, bridge, dragGate, faces, pill, root, shared } from './state.js';


const ALERT_PULL = 22;


const ALERT_STOLEN = 12;


const ALERT_TAP_SLOP = 10;


const CENTRE_TRAVEL = 520;


const CENTRE_CEILING = 0.6;


const CENTRE_LIFT = 0.1;


const HOME_TRAVEL = 460;


const ALERT_FLOOR = 88;


const FIT_TRAVEL = 340;


// Mirrors --grow-ms on #pill.alert-centred in pill.css, and is long enough for the row's own growth as well.
const BOX_SETTLE = 460;

let settleTimer = null;


// Mirrors ALERT_ZONE_HEIGHT_PART in BubbleService.kt.
const ZONE_HEIGHT_PART = 0.30;

let homeTimer = null;

let startX = 0;
let startY = 0;
let pullDrop = 0;
let hasActed = false;
let alertDrag = null;
let isBandWanted = false;


export function isAlertLive() {
  return shared.state === 'alert';
}

export function alertTouch(action, x, y) {
  if (action === 'down') {
    startX = x;
    startY = y;
    pullDrop = 0;
    hasActed = false;
    alertDrag = dragGate();
    return;
  }
  if (!isAlertLive()) return;

  // Slung into the dashboard the Alert answers one gesture and not the row's three: it is standing in a section of the panel rather than on the bar, so a pull downwards has nowhere to go and a sideways throw would be read against the panel underneath it. Up is the way back to the bubble it came from.
  if (isAlertDashed()) {
    if (action !== 'move' || hasActed) return;
    if (y - startY < -ALERT_PULL && startY - y > Math.abs(x - startX)) {
      hasActed = true;
      homeDashAlert();
    }
    return;
  }

  if (action === 'move') {
    const down = y - startY;
    const across = Math.abs(x - startX);
    pullDrop = Math.max(pullDrop, down);
    if (hasActed) return;
    if (Math.hypot(across, down) <= HOLD_BLOCK) return;
    if (!alertDrag(x, y)) return;
    toy(pill, '--drag', 0, down);
    // The pull is made at the screen's right edge as often as on the bubble, so it is as far across as it is down and a fixed sideways cap read every one of those as no gesture at all. Which direction a travel is, is which axis it has covered more of.
    if (down < -ALERT_PULL && -down > across) {
      hasActed = true;
      dismissAlert();
      return;
    }
    if (down > ALERT_PULL && down > across) {
      hasActed = true;
      // The band under the finger is handed over to the travel here rather than at the lift: left standing, `--drag-y` held the bubble off the journey it had just started and then sprang home on a clock of its own once the finger went, which is one movement arriving in two.
      untoy(pill, '--drag');
      if (isAlertCentred()) dismissAlert();
      else centreAlert();
    }
    return;
  }

  untoy(pill, '--drag');
  if (isAlertCentred() && isBandWanted) bridge.setAlertOverlay(1);
  // The shade takes this strip's pointer partway down and the page sees a cancel with the finger still moving, so a pull that was stolen is stronger evidence of the ask than a lift is and commits at a lower bar.
  if (action === 'cancel' && !hasActed && !isAlertCentred() && pullDrop > ALERT_STOLEN) {
    hasActed = true;
    centreAlert();
    return;
  }
  if (action !== 'up' || hasActed) return;
  if (Math.hypot(x - startX, y - startY) > ALERT_TAP_SLOP) return;
  if (pillHolds(x, y)) openCurrent();
  // A window that hears a press is the only window that hears it, so a tap inside the pull zone that was not aimed at the Alert would simply be eaten. The host hands it back to whatever is underneath.
  else bridge.passAlertTap();
}


export function isAlertCentred() {
  return shared.size === 'alertCentre';
}

function dismissAlert() {
  // Letting a focused Alert go is not the same act as throwing an unread one away, and it was answering with the same double click as the pull that focused it.
  bridge.triggerHaptic(isAlertCentred() ? 'release' : 'dismiss');
  toClosed();
}


export function layOutZone() {
  const height = Math.round(root.clientHeight * ZONE_HEIGHT_PART);
  root.style.setProperty('--alert-zone-height', height + 'px');
  root.style.setProperty('--alert-zone-top', Math.round((root.clientHeight - height) / 2) + 'px');
}


export function measureAlert() {
  const face = faces.alert;
  const head = document.getElementById('alert-head');
  const text = document.getElementById('text');
  const picture = document.getElementById('picture');
  const style = getComputedStyle(face);
  const room = SIZES.alert.width - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
  const gap = parseFloat(style.rowGap) || 0;
  // The bubble is still the width it is leaving when an arrival is measured, so the reading is wrapped at the Alert's own width for the read and let go again in the same breath. Measured a frame later instead — after the size had already been applied — the box animated to a default height first and then to the right one, which is the height changing during the arrival.
  text.style.width = room + 'px';
  const hasImage = pill.classList.contains('with-image');
  const wanted = head.offsetHeight + gap + text.scrollHeight +
    (hasImage ? gap + picture.offsetHeight : 0) +
    parseFloat(style.paddingTop) + parseFloat(style.paddingBottom);
  text.style.removeProperty('width');
  const ceiling = Math.round(root.clientHeight * CENTRE_CEILING);
  return Math.max(ALERT_FLOOR, Math.min(ceiling, Math.round(wanted)));
}


export function settleBox() {
  pill.classList.add('box-settling');
  clearTimeout(settleTimer);
  settleTimer = setTimeout(() => {
    pill.classList.remove('box-settling');
    fitLabels();
  }, BOX_SETTLE);
}


export function fitAlert() {
  settleBox();
  const height = measureAlert();
  root.style.setProperty('--alert-height', height + 'px');
  setSize('alert', { width: SIZES.alert.width, height });
  stirLiquid(FIT_TRAVEL);
}







function centreAlert() {
  bridge.triggerHaptic('expand');
  layOutCentre();
  // The halo comes up after the bubble has landed, so the mirror has to keep sending frames past the travel or the ramp stops at whatever strength had arrived.
  startHalo();
  stirLiquid(CENTRE_TRAVEL + HALO_MILLIS);
}




export function recentreAlert() {
  layOutCentre();
  stirLiquid(CENTRE_TRAVEL);
}

function layOutCentre() {
  settleBox();
  root.classList.remove('alert-zone-live');
  // An Alert dwells and closes itself, and one that has been pulled down has been asked for: it stands until the hand says otherwise.
  clearTimeout(shared.dwellTimer);


  const height = measureAlert();
  root.style.setProperty('--alert-height', height + 'px');

  // A notification is read above the middle rather than across it — the thumb and the hand holding the phone are under the lower half, and a box centred on the glass has the hand in front of the end of it.
  const drop = Math.round((root.clientHeight - height) / 2 - root.clientHeight * CENTRE_LIFT);
  root.style.setProperty('--alert-drop', drop + 'px');
  setSize('alertCentre', { width: SIZES.alert.width, height });
  // Resizing the window the finger is standing on pulls the surface out from under it and the system answers with a cancel, so the window is asked for at the lift — the same deferral `setSize` makes for the touch proxy, and for the same reason.
  isBandWanted = true;
  if (!shared.isTouchDown) bridge.setAlertOverlay(1);
}







export function leaveAlertCentre() {
  const wasCentred = isAlertCentred();
  isBandWanted = false;

  untoy(pill, '--drag');
  clearTimeout(settleTimer);
  pill.classList.remove('box-settling');
  root.classList.remove('alert-zone-live');
  root.style.removeProperty('--alert-drop');
  root.style.removeProperty('--alert-height');
  // The goo region is the row's band again the moment the bubble stops being grown, and a bubble drawn outside the region has its skin dropped while `html.liquid` is still blanking its own background — so the bubble travelling home from the middle of the screen was simply not drawn for the whole journey. The region is held at full height until it is back on the bar.
  if (wasCentred) {
    root.classList.add('alert-homing');
    clearTimeout(homeTimer);
    homeTimer = setTimeout(() => root.classList.remove('alert-homing'), HOME_TRAVEL);
    stirLiquid(HOME_TRAVEL + 200);
  }
  bridge.setAlertOverlay(0);
}


// Mirrors --dash-ms in pill.css: the sling home takes this clock. The sling *out* is the slingshot in sling.js and draws its own length per shot, which is the whole point of it — the arrival is the Now bubble's arrival, not a second motion that resembles it.
const DASH_TRAVEL = 300;

const DASH_INSET = 4;

// How much of the older Alert is left on screen once the new one has taken its place: enough for the app's icon and the padding around it, and not a pixel of the reading — what a card pushed aside has to say is which app it was.
const DASH_PEEK = 46;

// Mirrors --dash-peel-ms in pill.css.
const DASH_PEEL = 320;

const dash = document.getElementById('dash');
const dashPeek = document.getElementById('dash-peek');

let dashFlight = null;
let peelTimer = null;

export function isAlertDashed() {
  return shared.size === 'alertDash';
}

export function dashHolds(x, y) {
  if (!dash.classList.contains('live')) return false;
  const box = dash.getBoundingClientRect();
  return x >= box.left && x <= box.right && y >= box.top && y <= box.bottom;
}

// The reading is moved rather than copied: one Alert face is painted by `notification.js` wherever it stands, and a second copy in this bubble would be a second set of ids for the same notification.
function carryAlert() {
  if (faces.alert.parentElement === dash) return;
  dash.appendChild(faces.alert);
  faces.alert.classList.add('showing');
  faces.idle.classList.add('showing');
}

function returnAlert() {
  if (faces.alert.parentElement === pill) return;
  pill.appendChild(faces.alert);
}

// An Alert arriving over the open dashboard does not grow at the cutout: Main stands where it stands and spawns a drop, which is slung along an arc into the stats section and pops out into the room that section's own modules have just left. Grown at the cutout it stood over the dashboard's own bubbles and read as two interfaces in one place; flown as Main itself it moved the one shape the whole row is measured from.
export function dashAlert() {
  clearTimeout(shared.dwellTimer);
  const wasLive = dash.classList.contains('live');
  const rest = layOutDash();
  statsOut();
  // The older card is taken as a copy *before* the face is moved on, because the face is moved rather than duplicated — one Alert face is painted wherever it stands — and a card peeled after the move would be peeling the new notification.
  if (wasLive) peelDash();
  carryAlert();

  if (dashFlight) dashFlight.stop();
  dash.classList.remove('home');
  dash.classList.add('live');
  setSize('alertDash');

  const from = pill.getBoundingClientRect();
  dashFlight = slingInto(dash, from, rest, {
    ball: from.height,
    // The drop is born a little under half the bubble it comes out of, which is what a drop is: the bubble does not travel, it sheds one.
    born: from.height * 0.45,
    restRadius: getComputedStyle(dash).borderTopLeftRadius,
  });
  // The reading appears on the frame the ball starts opening out, and how long the ball was in the air is drawn fresh per shot — so the delay is handed to the stylesheet rather than written into it.
  dash.style.setProperty('--dash-open-delay', dashFlight.millis + 'ms');
  dash.classList.add('opening');
  setTimeout(() => dash.classList.remove('opening'), dashFlight.total);
  stirLiquid(dashFlight.total + 200);
  shared.dwellTimer = setTimeout(homeDashAlert, shared.dwell);
}


// The card that was standing there is pushed out past the right edge and held with its icon showing. It is a copy rather than the bubble itself: the Alert face is one element moved from bubble to bubble, so what stays behind has to be a still of what it looked like.
function peelDash() {
  clearTimeout(peelTimer);
  dashPeek.replaceChildren(...[...dash.children].map(child => child.cloneNode(true)));
  dashPeek.classList.remove('peeling');
  void dashPeek.offsetWidth;
  dashPeek.classList.add('live', 'peeling');
  const left = parseFloat(getComputedStyle(root).getPropertyValue('--dash-left')) || 0;
  dashPeek.style.setProperty('--peel-x', Math.round(root.clientWidth - DASH_PEEK - left) + 'px');
  peelTimer = setTimeout(() => dashPeek.classList.remove('peeling'), DASH_PEEL);
}

function dropPeek() {
  clearTimeout(peelTimer);
  dashPeek.classList.remove('live', 'peeling');
  dashPeek.replaceChildren();
  dashPeek.style.removeProperty('--peel-x');
}

// The box the Alert lands in is the stats section's own, less the inset that keeps it off the section's edges. It is written as a left and a top rather than as a translate because every frame of the flight *is* a translate away from where it rests — the two cannot both own that property.
function layOutDash() {
  const box = statsBox();
  const width = Math.round(box.width - DASH_INSET * 2);
  const height = Math.round(box.height - DASH_INSET * 2);
  const left = Math.round(box.left + DASH_INSET);
  const top = Math.round(box.top + DASH_INSET);
  root.style.setProperty('--dash-width', width + 'px');
  root.style.setProperty('--dash-height', height + 'px');
  root.style.setProperty('--dash-left', left + 'px');
  root.style.setProperty('--dash-top', top + 'px');

  // The home is still the CSS animation's, and it needs the same journey the other way: where Main is standing, as a travel off this resting box.
  const main = pill.getBoundingClientRect();
  root.style.setProperty('--dash-home-x', Math.round(main.left + main.width / 2 - (left + width / 2)) + 'px');
  root.style.setProperty('--dash-home-y', Math.round(main.top + main.height / 2 - (top + height / 2)) + 'px');
  return { left, top, width, height };
}

export function renewDashAlert() {
  carryAlert();
  clearTimeout(shared.dwellTimer);
  shared.dwellTimer = setTimeout(homeDashAlert, shared.dwell);
}

export function homeDashAlert() {
  if (!isAlertDashed() || dash.classList.contains('home')) return;
  clearTimeout(shared.dwellTimer);
  bridge.triggerHaptic('dismiss');
  // The flight is forwards-filled, so a card sent home while it is still landing would fly out of the values that flight had left on it rather than out of the box it is standing in.
  if (dashFlight) dashFlight.stop();
  dashFlight = null;
  dash.classList.remove('opening');
  dash.classList.add('home');
  dropPeek();
  statsIn();
  setTimeout(toClosed, DASH_TRAVEL);
}

export function leaveAlertDash() {
  if (!dash.classList.contains('live')) return;
  if (dashFlight) dashFlight.stop();
  dashFlight = null;
  dash.classList.remove('live', 'home', 'opening');
  dropPeek();
  returnAlert();
  for (const name of ['--dash-width', '--dash-height', '--dash-left', '--dash-top', '--dash-home-x', '--dash-home-y']) {
    root.style.removeProperty(name);
  }
  statsIn();
}
