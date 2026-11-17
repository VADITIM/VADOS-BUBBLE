import { toClosed } from './row.js';
import { closeStatusPanel, openStatusPanel, statusOpen } from './status.js';
import { bridge, root, shared } from './state.js';


// Mirrors STAGE_HIDDEN, STAGE_DASHBOARD and STAGE_LEAVING in BubbleService.kt.
const STAGE_HIDDEN = 0;
const STAGE_DASHBOARD = 1;
const STAGE_LEAVING = 2;

const DASHBOARD_FADE = 300;

const faded = ['pill', 'clock', 'status', 'satellite-left', 'satellite-right', 'dots-left', 'dots-right']
  .map(id => document.getElementById(id));

let stage = STAGE_HIDDEN;
let isDashboardPending = false;
let leaveTimer = null;
let running = [];


export function isLandscape() {
  return root.classList.contains('landscape');
}

function ask(next) {
  stage = next;
  bridge.setLandscapeStage(next);
}

function stopRunning() {
  running.forEach(animation => animation.cancel());
  running = [];
}

function fade(keyframes, easing) {
  stopRunning();
  running = faded.map(element => element.animate(keyframes, { duration: DASHBOARD_FADE, easing, fill: 'forwards' }));
}

export function summonDashboard() {
  if (!isLandscape() || stage === STAGE_DASHBOARD) return;
  if (stage === STAGE_LEAVING) {
    clearTimeout(leaveTimer);
    ask(STAGE_DASHBOARD);
    enterDashboard();
    return;
  }
  bridge.triggerHaptic('expand');
  ask(STAGE_DASHBOARD);
  if (shared.isStageHidden) isDashboardPending = true;
  else enterDashboard();
}

function enterDashboard() {
  isDashboardPending = false;
  root.classList.remove('concealed');
  fade([{ opacity: 0 }, {}], 'ease-in');
  openStatusPanel();
}

export function dashboardLeaving(duration) {
  if (!isLandscape() || stage !== STAGE_DASHBOARD) return;
  ask(STAGE_LEAVING);
  fade([{}, { opacity: 0 }], 'ease-out');
  clearTimeout(leaveTimer);
  leaveTimer = setTimeout(() => {
    if (stage !== STAGE_LEAVING) return;
    root.classList.add('concealed');
    stopRunning();
    ask(STAGE_HIDDEN);
  }, duration);
}

function forget() {
  clearTimeout(leaveTimer);
  stopRunning();
  cornerStart = null;
  cornerTappedAt = -Infinity;
  isDashboardPending = false;
  stage = STAGE_HIDDEN;
}

export function landscapeSlept() {
  if (!isLandscape()) return;
  forget();
  root.classList.add('concealed');
}

export function landscapeWoke() {
  if (isDashboardPending) enterDashboard();
}

export function setLandscape(isOn) {
  const wasOn = isLandscape();
  root.classList.toggle('landscape', isOn);
  forget();
  root.classList.toggle('concealed', isOn);
  if (wasOn === isOn) return;
  if (statusOpen) closeStatusPanel();
  if (shared.state !== 'idle') toClosed();
}
