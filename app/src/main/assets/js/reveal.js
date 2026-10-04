import { isAlertCentred, isAlertDashed, isAlertLive } from './alert.js';
import { PROXY_TAP_SLOP } from './bridge.js';
import { clockPill, fitClockProxy } from './clock.js';
import { stirLiquid } from './liquid.js';
import { toClosed } from './row.js';
import { slingInto } from './sling.js';
import { closeStatusPanel, fitStatusProxy, openStatusPanel, statusOpen, statusPill } from './status.js';
import { bridge, pill, root, shared } from './state.js';


// Mirrors STAGE_HIDDEN, STAGE_ALERT, STAGE_REVEALED and STAGE_LEAVING in BubbleService.kt.
const STAGE_HIDDEN = 0;
const STAGE_ALERT = 1;
const STAGE_REVEALED = 2;
const STAGE_LEAVING = 3;

const REVEAL_DWELL = 3000;
const REVEAL_PULL = 24;
const REVEAL_OPEN = 300;
const REVEAL_PACE = 0.7;
const REVEAL_BORN = 0.3;
const REVEAL_HOME = 260;
const REVEAL_DROP = 0.15;
// Mirrors CORNER_DOUBLE in BubbleService.kt, which keeps the corner live that long after a reveal.
const CORNER_DOUBLE = 300;
const CORNER_PRESS = 220;
// Mirrors --grow-ms: the Alert is carried back up off the screen before the stage is let go.
const ALERT_HOME = 460;

const marks = ['satellite-left', 'satellite-right', 'dots-left', 'dots-right']
  .map(id => document.getElementById(id));

let stage = STAGE_HIDDEN;
let isEntrancePending = false;
let isAlertHoming = false;
let dwellTimer = null;
let alertTimer = null;
let running = [];
let stripStart = null;
let isStripFired = false;
let cornerStart = null;
let cornerTappedAt = -Infinity;
let pendingOpen = null;


export function isLandscape() {
  return root.classList.contains('landscape');
}

function ask(next) {
  stage = next;
  bridge.setLandscapeStage(next);
}

function token(name) {
  return getComputedStyle(root).getPropertyValue(name).trim();
}

function keep(animation) {
  running.push(() => animation.cancel());
}

function stopRunning() {
  running.forEach(stop => stop());
  running = [];
}

function isPanelOpen() {
  return statusOpen || shared.state === 'extended' || isAlertCentred() || isAlertDashed();
}

function armDwell() {
  clearTimeout(dwellTimer);
  if (stage !== STAGE_REVEALED) return;
  dwellTimer = setTimeout(() => {
    if (isPanelOpen() || isAlertLive()) armDwell();
    else conceal();
  }, REVEAL_DWELL);
}

export function noteLandscapeTouch() {
  if (stage === STAGE_REVEALED) armDwell();
}

function reveal() {
  if (stage === STAGE_REVEALED) {
    armDwell();
    return;
  }
  if (stage === STAGE_LEAVING && !isAlertHoming) return;
  clearTimeout(alertTimer);
  isAlertHoming = false;
  bridge.triggerHaptic('expand');
  const isDrawing = stage !== STAGE_HIDDEN;
  ask(STAGE_REVEALED);
  if (isDrawing) playEntrance();
  else isEntrancePending = true;
}

function playEntrance() {
  isEntrancePending = false;
  stopRunning();
  root.classList.add('revealing');
  root.classList.remove('concealed');
  const from = pill.getBoundingClientRect();
  const bubble = token('--ease-bubble');
  if (shared.state !== 'alert') {
    keep(pill.animate(
      [{ scale: REVEAL_DROP, opacity: 0 }, { scale: 1, opacity: 1 }],
      { duration: REVEAL_OPEN, easing: bubble }
    ));
  }
  let longest = REVEAL_OPEN;
  for (const element of [clockPill, statusPill]) {
    const to = element.getBoundingClientRect();
    if (!to.width) continue;
    const flight = slingInto(element, from, to, {
      isWhole: true,
      pace: REVEAL_PACE,
      born: Math.min(to.width, to.height) * REVEAL_BORN,
    });
    running.push(() => flight.stop());
    longest = Math.max(longest, flight.total);
  }
  marks.forEach(mark => keep(mark.animate(
    [{ opacity: 0, scale: 0.3 }, { opacity: 1, scale: 1 }],
    { duration: REVEAL_OPEN, delay: REVEAL_OPEN * 0.35, easing: bubble, fill: 'backwards' }
  )));
  stirLiquid(longest + 160);
  setTimeout(() => root.classList.remove('revealing'), REVEAL_OPEN);
  clearTimeout(dwellTimer);
  // The host asks for the proxies the moment the stage is shown, which is while the Clock and Status are still slung out of Main, so both windows were measured standing on Main: a tap on Main opened the Dashboard and a tap on Status fell through to the game.
  const landing = setTimeout(() => {
    fitStatusProxy();
    fitClockProxy();
    if (pendingOpen && stage === STAGE_REVEALED) pendingOpen();
    pendingOpen = null;
    armDwell();
  }, longest);
  running.push(() => clearTimeout(landing));
}

function conceal() {
  clearTimeout(dwellTimer);
  if (stage !== STAGE_REVEALED) return;
  if (isPanelOpen()) {
    armDwell();
    return;
  }
  ask(STAGE_LEAVING);
  stopRunning();
  const home = pill.getBoundingClientRect();
  const leave = token('--ease-leave') || 'ease-in';
  for (const element of [clockPill, statusPill]) {
    const box = element.getBoundingClientRect();
    if (!box.width) continue;
    const x = home.left + home.width / 2 - (box.left + box.width / 2);
    const y = home.top + home.height / 2 - (box.top + box.height / 2);
    keep(element.animate(
      [
        { translate: '0px 0px', scale: 1, opacity: 1 },
        { translate: x.toFixed(1) + 'px ' + y.toFixed(1) + 'px', scale: REVEAL_DROP, opacity: 0 },
      ],
      { duration: REVEAL_HOME, easing: leave, fill: 'forwards' }
    ));
  }
  marks.forEach(mark => keep(mark.animate(
    [{ opacity: 1, scale: 1 }, { opacity: 0, scale: 0.3 }],
    { duration: REVEAL_HOME, easing: leave, fill: 'forwards' }
  )));
  const isAlertKept = isAlertLive();
  if (!isAlertKept) {
    keep(pill.animate(
      [{ scale: 1, opacity: 1 }, { scale: REVEAL_DROP, opacity: 0 }],
      { duration: REVEAL_HOME, delay: REVEAL_HOME / 2, easing: leave, fill: 'forwards' }
    ));
  }
  const total = isAlertKept ? REVEAL_HOME : REVEAL_HOME * 1.5;
  stirLiquid(total + 120);
  setTimeout(() => {
    if (stage !== STAGE_LEAVING || isAlertHoming) return;
    root.classList.add('revealing', 'concealed');
    stopRunning();
    setTimeout(() => root.classList.remove('revealing'), 60);
    ask(isAlertLive() ? STAGE_ALERT : STAGE_HIDDEN);
  }, total);
}

// The Dashboard and Notifications launchers only toggled the panel, and with the bar hidden it opened on a stage nobody could see: the bar is brought out first and the panel opens once it has landed.
export function revealThen(open) {
  if (!isLandscape() || stage === STAGE_REVEALED) return false;
  pendingOpen = open;
  reveal();
  return true;
}

export function alertArriving() {
  if (!isLandscape()) return;
  if (stage === STAGE_HIDDEN || isAlertHoming) {
    clearTimeout(alertTimer);
    isAlertHoming = false;
    ask(STAGE_ALERT);
  }
}

export function alertLeaving() {
  if (!isLandscape() || stage !== STAGE_ALERT) return;
  isAlertHoming = true;
  ask(STAGE_LEAVING);
  clearTimeout(alertTimer);
  alertTimer = setTimeout(() => {
    isAlertHoming = false;
    if (stage === STAGE_LEAVING) ask(STAGE_HIDDEN);
  }, ALERT_HOME);
}

export function landscapeSlept() {
  if (!isLandscape()) return;
  clearTimeout(dwellTimer);
  clearTimeout(alertTimer);
  stopRunning();
  cornerTappedAt = -Infinity;
  isEntrancePending = false;
  pendingOpen = null;
  isAlertHoming = false;
  stage = STAGE_HIDDEN;
  root.classList.add('concealed');
}

export function landscapeWoke() {
  if (isEntrancePending) playEntrance();
}

export function setLandscape(isOn) {
  const wasOn = isLandscape();
  root.classList.toggle('landscape', isOn);
  clearTimeout(dwellTimer);
  clearTimeout(alertTimer);
  stopRunning();
  cornerTappedAt = -Infinity;
  isEntrancePending = false;
  pendingOpen = null;
  isAlertHoming = false;
  stage = STAGE_HIDDEN;
  root.classList.toggle('concealed', isOn);
  if (wasOn === isOn) return;
  if (statusOpen) closeStatusPanel();
  if (shared.state !== 'idle') toClosed();
}

export function revealTouch(action, x, y) {
  if (action === 'down') {
    stripStart = { x, y };
    isStripFired = false;
    return;
  }
  if (!stripStart) return;
  const across = x - stripStart.x;
  const down = y - stripStart.y;
  if (action === 'move') {
    if (isStripFired || Math.abs(down) < REVEAL_PULL || Math.abs(down) < Math.abs(across)) return;
    isStripFired = true;
    if (down > 0) reveal();
    else conceal();
    return;
  }
  if (action === 'up' && !isStripFired && Math.hypot(across, down) < PROXY_TAP_SLOP) bridge.passRevealTap();
  stripStart = null;
}

export function cornerTouch(action, x, y) {
  if (action === 'down') {
    cornerStart = { x, y, at: performance.now() };
    return;
  }
  if (action === 'move' || !cornerStart) return;
  const travel = Math.hypot(x - cornerStart.x, y - cornerStart.y);
  const held = performance.now() - cornerStart.at;
  cornerStart = null;
  // A thumb resting on the corner mid-game is a long, sliding press, so only a short, still tap is read as asking for the bar.
  if (action !== 'up' || travel >= PROXY_TAP_SLOP || held > CORNER_PRESS) return;
  // The single tap waited out the double on a timer, and a hidden page's timers are throttled to about a second, so the bar came a second late and a second tap inside that second opened the Dashboard: the bar now comes on the first tap and a second one only adds the Dashboard to its landing.
  const now = performance.now();
  if (now - cornerTappedAt < CORNER_DOUBLE) {
    cornerTappedAt = -Infinity;
    pendingOpen = openStatusPanel;
    return;
  }
  cornerTappedAt = now;
  reveal();
}
