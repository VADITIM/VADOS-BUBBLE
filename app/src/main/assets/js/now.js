import { PROXY_TAP_SLOP } from './bridge.js';
import { clockPill, fitClockProxy } from './clock.js';
import { stirLiquid, traceEvent } from './liquid.js';
import { toClosed } from './row.js';
import { closeStatusPanel, setTransfer, statusOpen } from './status.js';
import { HOLD_MILLIS, bridge, root, shared } from './state.js';















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


const TORCH_GLYPH =
  '<svg viewBox="0 0 24 24"><path d="M8.4 2h7.2a1 1 0 0 1 .97 1.24l-.9 3.6a1 1 0 0 1-.97.76H9.3a1 1 0 0 1-.97-.76l-.9-3.6A1 1 0 0 1 8.4 2zm1.1 7.6h5a1 1 0 0 1 1 1V21a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1V10.6a1 1 0 0 1 1-1zm1.5 3.4a1 1 0 0 0 0 2h2a1 1 0 0 0 0-2z"/></svg>';
nowGlyph.innerHTML = TORCH_GLYPH;


const NOW_SLOT = 40;
const NOW_SLOT_GAP = 14;


root.style.setProperty('--now-slot', NOW_SLOT + 'px');

const NOW_PANEL = { width: 300, height: 190, ms: 420 };



root.style.setProperty('--now-panel-width', NOW_PANEL.width + 'px');
root.style.setProperty('--now-panel-height', NOW_PANEL.height + 'px');


const NOW_STAND_ASIDE = 220;
const NOW_EXPAND = 300;

let torch = null;













const nowLive = {};
let nowOrder = [];


const NOW_WIDTHS = {
  recording: 112,
};







const nowDismissed = {};


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


let nowTimers = [];
function nowAfter(milliseconds, step) {
  nowTimers.push(setTimeout(step, milliseconds));
}
function stopNowSteps() {
  nowTimers.forEach(clearTimeout);
  nowTimers = [];
}




function keepTorchOpen() {
  if (nowOwner() === 'torch' && !nowOpen) openNowPanel();
}

function showNowFace(name) {
  for (const [key, face] of Object.entries(nowFaces)) {
    face.classList.toggle('showing', key === name);
  }
}








function nowWidthFor(owner) {
  return NOW_WIDTHS[owner] || 116;
}








function pinClockWidth() {
  root.style.setProperty('--clock-width-ms', '0ms');
  root.style.setProperty('--clock-width', Math.round(clockPill.getBoundingClientRect().width) + 'px');
}

function setClockWidth(next, milliseconds) {
  root.style.setProperty('--clock-width-ms', milliseconds + 'ms');
  root.style.setProperty('--clock-width', next + 'px');
  stirLiquid(milliseconds + 120);
  
  
  nowAfter(milliseconds, fitClockProxy);
}

const nowElapsed = document.getElementById('now-elapsed');


let elapsedTick = null;

function nowClock(milliseconds) {
  const whole = Math.max(0, Math.round(milliseconds / 1000));
  const minutes = Math.floor(whole / 60);
  const seconds = whole % 60;
  return minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
}





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







function setNowMod(name, payload) {
  const had = nowOrder.length;
  if (payload) {
    nowLive[name] = payload;
    
    
    
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
  
  setClockWidth(nowWidthFor(nowOwner()), NOW_EXPAND);
  fitClockProxy();
  keepTorchOpen();
}













function wakeNowMod() {
  traceEvent('now in: ' + nowOwner());
  stopNowSteps();
  paintNowFace();
  pinClockWidth();
  
  
  
  window.onNowStanding(true);
  requestAnimationFrame(() => {
    clockPill.classList.add('now-live');
    stirLiquid(NOW_STAND_ASIDE + 120);
  });
  nowAfter(NOW_STAND_ASIDE, () => {
    setClockWidth(nowWidthFor(nowOwner()), NOW_EXPAND);
    
    
    if (nowOwner() !== 'torch') showNowFace('closed');
    
    bridge.triggerHaptic('tap');
    keepTorchOpen();
  });
}






function restNowMod() {
  traceEvent('now out');
  stopNowSteps();
  if (nowOpen) closeNowPanel();
  showNowFace('');
  
  
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








function nowPanelShift() {
  return window.innerWidth / 2 - NOW_PANEL.width / 2 - clockPill.offsetLeft;
}


function setPanelShift(next, milliseconds) {
  root.style.setProperty('--clock-fly', next + 'px');
  root.style.setProperty('--clock-fly-ms', milliseconds + 'ms');
  stirLiquid(milliseconds + 120);
}

function openNowPanel() {
  
  
  if (shared.state === 'extended') toClosed();
  if (statusOpen) closeStatusPanel();
  nowOpen = true;
  
  
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
  
  
  
  
  stirLiquid(NOW_PANEL.ms + 120);
  
  
  
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
  
  
  setPanelShift(0, NOW_PANEL.ms);
  fitClockProxy();
  stirLiquid(NOW_PANEL.ms + 120);
  
  
  nowAfter(NOW_PANEL.ms + 40, () => root.style.removeProperty('--liquid-tall'));
}







clockPill.addEventListener('click', () => {
  if (!nowOpen) return;
  
  if (nowOwner() === 'torch') return;
  bridge.triggerHaptic('tap');
  closeNowPanel();
});







export function nowOwnsClock() {
  return Boolean(nowOwner()) || nowOpen;
}














let nowHeld = false;
let nowHoldTimer = null;
let nowDownAt = null;









function dismissNowMod() {
  const owner = nowOwner();
  if (!owner) return;
  nowDismissed[owner] = true;
  nowOrder = nowOrder.filter(mod => mod !== owner);
  bridge.triggerHaptic('dismiss');
  if (nowOrder.length) {
    
    
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
      
      
      
      
      if (!nowOpen) openNowPanel();
    }, HOLD_MILLIS);
    return;
  }
  if (action === 'move') {
    
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
  
  
  
  if (nowOwner() === 'recording') {
    pressRecording();
    return;
  }
  openNowPanel();
}






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









window.onNowMods = payload => {
  setNowMod('recording', payload.recording || null);
  setTransfer(payload.transfer || null);
};






document.getElementById('now-stop').addEventListener('click', event => {
  event.stopPropagation();
  const live = nowLive.recording;
  const button = live && live.actions &&
    live.actions.find(action => /stop|beenden|stopp/i.test(action.title));
  if (button) bridge.recordingAction(button.index);
  bridge.triggerHaptic('tap');
  closeNowPanel();
});
