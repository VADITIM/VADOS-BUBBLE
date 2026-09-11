import { becomeExtended, closedTarget, setSize, showFace, toClosed } from '../row.js';
import { bridge, mods, pill, setSweepPhase, shared } from '../state.js';

const timerRemaining = document.getElementById('timer-remaining');
const timerClock = document.getElementById('timer-clock');


export function timerWindow() {
  return closedTarget();
}






function remainingText() {
  if (!shared.timer) return '';
  
  
  
  
  if (!shared.timer.endsAt && !shared.timer.remaining) {
    return (shared.timer.summary || '').split('/')[0].trim();
  }
  const left = shared.timer.endsAt
    ? Math.max(0, Math.round((shared.timer.endsAt - Date.now()) / 1000))
    : Math.round(shared.timer.remaining / 1000);
  const hours = Math.floor(left / 3600);
  const minutes = Math.floor((left % 3600) / 60);
  const seconds = left % 60;
  const pad = value => String(value).padStart(2, '0');
  return hours > 0
    ? hours + ':' + pad(minutes) + ':' + pad(seconds)
    : minutes + ':' + pad(seconds);
}

export function paintTimer() {
  if (!shared.timer) return;
  document.documentElement.style.setProperty(
    '--app-accent', shared.timer.accent || 'var(--section-color)'
  );
  
  
  
  setSweepPhase(document.getElementById('timer-glyph'));
  pill.classList.toggle('running', Boolean(shared.timer.endsAt) && !shared.timer.isPaused);
  pill.classList.toggle('paused', Boolean(shared.timer.isPaused));
  const reading = remainingText();
  timerRemaining.textContent = reading;
  timerClock.textContent = reading;
  document.getElementById('timer-label').textContent = shared.timer.appName || 'Clock';
}

function runTimerClock() {
  clearInterval(shared.timerTicker);
  if (!shared.timer || !shared.timer.endsAt) return;
  shared.timerTicker = setInterval(() => {
    if (!shared.timer || shared.isStageHidden) return;
    const reading = remainingText();
    timerRemaining.textContent = reading;
    timerClock.textContent = reading;
  }, 1000);
}


export function openTimer() {
  becomeExtended();
  paintTimer();

  const actions = document.getElementById('timer-actions');
  actions.textContent = '';
  for (const action of (shared.timer && shared.timer.actions) || []) {
    const button = document.createElement('div');
    button.className = 'timer-button';
    button.textContent = action.title;
    button.addEventListener('click', event => {
      event.stopPropagation();
      bridge.triggerHaptic('tap');
      bridge.timerAction(action.index);
      toClosed();
    });
    actions.appendChild(button);
  }

  showFace('timerPanel');
  setSize('timer');
}

window.onTimerUpdate = payload => {
  shared.timer = payload;
  if (!shared.timer) {
    mods.delete('timer');
    clearInterval(shared.timerTicker);
    pill.classList.remove('running');
    if (shared.state === 'idle' || shared.size === 'timer') toClosed();
    return;
  }
  mods.add('timer');
  paintTimer();
  runTimerClock();
  if (shared.state === 'idle') toClosed();
};
