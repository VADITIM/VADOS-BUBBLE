import { closedTarget, setSize, showFace, toClosed } from '../row.js';
import { bridge, mods, pill, setSweepPhase, shared } from '../state.js';

const timerRemaining = document.getElementById('timer-remaining');
const timerClock = document.getElementById('timer-clock');

/** Wide enough for the glyph and the reading either side of the closed bubble. */
export function timerWindow() {
  return closedTarget();
}

/**
 * One UI hands over a target wall-clock time, not a countdown, so the seconds are
 * counted here — the notification is only re-posted when the timer is touched, and
 * a reading that only moved then would sit frozen on screen.
 */
function remainingText() {
  if (!shared.timer) return '';
  // A pause changes what the reading does, not what it is: the same clock, held
  // still. The summary it leaves behind — "2 h, 13 min, 5 s / Angehalten" — is
  // read as a number rather than shown as a sentence, so nothing in the bubble
  // moves when a timer is paused except the hand.
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
  // A paused timer keeps its target, so it stops counting rather than lying.
  // Phased before the class goes on: adding .running starts the sweep, and an animation
  // started before its delay is written begins at twelve for one frame.
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
    if (!shared.timer) return;
    const reading = remainingText();
    timerRemaining.textContent = reading;
    timerClock.textContent = reading;
  }, 1000);
}

/** The clock app's own buttons — pause, resume, cancel — fired from the bubble. */
export function openTimer() {
  shared.state = 'active';
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
