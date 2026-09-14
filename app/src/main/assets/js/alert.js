import { openCurrent } from './mods/notification.js';
import { HOLD_BLOCK, pillHolds, toy, untoy } from './motion.js';
import { setSize, toClosed } from './row.js';
import { HALO_MILLIS, startHalo, stirLiquid } from './liquid.js';
import { statsBox, statsIn, statsOut } from './status.js';
import { SIZES, bridge, dragGate, faces, pill, root, shared } from './state.js';


const ALERT_PULL = 22;


const ALERT_STOLEN = 12;


const ALERT_TAP_SLOP = 10;


const CENTRE_TRAVEL = 520;


const CENTRE_CEILING = 0.6;


const CENTRE_LIFT = 0.1;


const HOME_TRAVEL = 460;

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
    // The window is the whole screen now, so a pull begun out at the edge of it is as far across as it is down and a fixed sideways cap read every one of those as no gesture at all. Which direction a travel is, is which axis it has covered more of.
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
  // The window an Alert takes covers the screen so the pull can be made anywhere, and a window that hears a press is the only window that hears it — a tap nowhere near the bubble would simply be eaten by an Alert that has nothing to do with what the finger was aiming at. The host hands it back to whatever is underneath.
  else bridge.passAlertTap();
}


export function isAlertCentred() {
  return shared.size === 'alertCentre';
}

function dismissAlert() {
  bridge.triggerHaptic('dismiss');
  toClosed();
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
  // An Alert dwells and closes itself, and one that has been pulled down has been asked for: it stands until the hand says otherwise.
  clearTimeout(shared.dwellTimer);


  const head = document.getElementById('alert-head');
  const text = document.getElementById('text');
  const picture = document.getElementById('picture');
  const ceiling = Math.round(root.clientHeight * CENTRE_CEILING);
  const wanted = head.offsetHeight + text.scrollHeight +
    (pill.classList.contains('with-image') ? picture.offsetHeight : 0) + 40;
  const height = Math.max(SIZES.alert.height, Math.min(ceiling, wanted));
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


// Mirrors --dash-ms in pill.css: the sling out and the sling home take the same clock.
const DASH_TRAVEL = 300;

const DASH_INSET = 4;

const dash = document.getElementById('dash');

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
  layOutDash();
  statsOut();
  carryAlert();
  // A second Alert landing on one already slung takes the same journey rather than appearing where the first stands, and an animation already running has to be taken off the element before it can be given back or the restart is a no-op.
  dash.classList.remove('slinging', 'home');
  void dash.offsetWidth;
  setSize('alertDash');
  dash.classList.add('live', 'slinging');
  shared.dwellTimer = setTimeout(homeDashAlert, shared.dwell);
}

function layOutDash() {
  const box = statsBox();
  const width = Math.round(box.width - DASH_INSET * 2);
  const height = Math.round(box.height - DASH_INSET * 2);
  const grab = parseFloat(getComputedStyle(root).getPropertyValue('--grab')) || 0;
  root.style.setProperty('--dash-width', width + 'px');
  root.style.setProperty('--dash-height', height + 'px');
  // The bubble is centred on the canvas and stands at the grab inset, so where it has to go is the difference between that resting box and the section's own.
  root.style.setProperty('--dash-x', Math.round(box.left + DASH_INSET - (root.clientWidth - width) / 2) + 'px');
  root.style.setProperty('--dash-y', Math.round(box.top + DASH_INSET - grab) + 'px');
}

export function homeDashAlert() {
  if (!isAlertDashed() || dash.classList.contains('home')) return;
  clearTimeout(shared.dwellTimer);
  bridge.triggerHaptic('dismiss');
  dash.classList.remove('slinging');
  dash.classList.add('home');
  statsIn();
  setTimeout(toClosed, DASH_TRAVEL);
}

export function leaveAlertDash() {
  if (!dash.classList.contains('live')) return;
  dash.classList.remove('live', 'slinging', 'home');
  returnAlert();
  root.style.removeProperty('--dash-width');
  root.style.removeProperty('--dash-height');
  root.style.removeProperty('--dash-x');
  root.style.removeProperty('--dash-y');
  statsIn();
}
