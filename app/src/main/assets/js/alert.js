import { openCurrent } from './mods/notification.js';
import { HOLD_BLOCK, pillHolds, toy, untoy } from './motion.js';
import { setSize, toClosed } from './row.js';
import { HALO_MILLIS, HALO_SPREAD_Y, startHalo, stirLiquid } from './liquid.js';
import { SIZES, bridge, dragGate, pill, root, shared } from './state.js';


const ALERT_PULL = 22;


const ALERT_STOLEN = 12;


const ALERT_TAP_SLOP = 10;


const CENTRE_TRAVEL = 520;


const CENTRE_CEILING = 0.6;


const CENTRE_GRAB = 24;


const CENTRE_LIFT = 0.1;


const HOME_TRAVEL = 460;

let homeTimer = null;

let startX = 0;
let startY = 0;
let pullDrop = 0;
let hasActed = false;
let alertDrag = null;
let wantedBand = 0;


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

  if (action === 'move') {
    const down = y - startY;
    const across = Math.abs(x - startX);
    pullDrop = Math.max(pullDrop, down);
    if (hasActed) return;
    if (Math.hypot(across, down) <= HOLD_BLOCK) return;
    if (!alertDrag(x, y)) return;
    toy(pill, '--drag', 0, down);
    if (down < -ALERT_PULL && across < 40) {
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
  if (isAlertCentred() && wantedBand) bridge.setAlertOverlay(wantedBand);
  // The shade takes this strip's pointer partway down and the page sees a cancel with the finger still moving, so a pull that was stolen is stronger evidence of the ask than a lift is and commits at a lower bar.
  if (action === 'cancel' && !hasActed && !isAlertCentred() && pullDrop > ALERT_STOLEN) {
    hasActed = true;
    centreAlert();
    return;
  }
  if (action !== 'up' || hasActed) return;
  if (Math.hypot(x - startX, y - startY) > ALERT_TAP_SLOP) return;
  if (pillHolds(x, y)) openCurrent();
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
  // The overlay is a band across the top of the screen and the centred bubble stands below it, so the room the gesture needs is asked for while the bubble is down there and given straight back at the close.
  // Resizing the window the finger is standing on pulls the surface out from under it and the system answers with a cancel, so the band is asked for at the lift — the same deferral `setSize` makes for the touch proxy, and for the same reason.
  // The halo is part of the shape here, not decoration around it: the hand reaches for the blurred region as readily as for the bubble, so the band covers everything the rings reach rather than stopping at the box.
  wantedBand = drop + height + HALO_SPREAD_Y + CENTRE_GRAB;
  if (!shared.isTouchDown) bridge.setAlertOverlay(wantedBand);
}







export function leaveAlertCentre() {
  const wasCentred = isAlertCentred();
  wantedBand = 0;

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
