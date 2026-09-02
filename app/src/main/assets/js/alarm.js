import { stirLiquid } from './liquid.js';
import { setSize, showFace, toClosed } from './row.js';
import { bridge, pill, root, shared } from './state.js';

/**
 * An alarm ringing, which is the one state on this phone allowed to take the whole screen.
 *
 * Everything else here is information and gets a bubble's worth of room. An alarm is a demand:
 * it goes on until it is answered, it is what the person is being woken by, and said quietly
 * it has failed at the only job it has. So the bubble grows all the way — the same shape at
 * the same corners, arriving out of the row the way every other state does, which is what
 * keeps it from reading as a dialog appearing over the screen.
 *
 * It is a state of the bubble rather than a bubble of its own for exactly that reason. A
 * second element would have to be given the growth, the liquid, the window and the corners all
 * over again, and it would arrive from nowhere.
 */
const alarmClock = document.getElementById('alarm-clock');
const alarmLabel = document.getElementById('alarm-label');
const alarmSnooze = document.getElementById('alarm-snooze');
const alarmDismiss = document.getElementById('alarm-dismiss');

/** The two words the clock app writes on its own buttons, in both languages this phone speaks. */
const SNOOZE = /snooze|schlummer|später/i;
const STOP = /stop|dismiss|beenden|verwerfen|aus/i;

let ringing = null;

function alarmTime() {
  const now = new Date();
  const minutes = now.getMinutes();
  return now.getHours() + ':' + (minutes < 10 ? '0' : '') + minutes;
}

function press(pattern) {
  const button = ringing && ringing.actions &&
    ringing.actions.find(action => pattern.test(action.title));
  if (!button) return false;
  bridge.alarmAction(button.index);
  bridge.triggerHaptic('tap');
  return true;
}

/**
 * The buttons wear the clock app's own words rather than ours, because they fire the clock
 * app's own buttons: a label of ours saying "Snooze" over an action that turns out to be
 * something else is the worst possible thing to press at six in the morning. A button whose
 * action is not offered is not drawn at all.
 */
function paintAlarm() {
  if (!ringing) return;
  alarmClock.textContent = alarmTime();
  alarmLabel.textContent = ringing.label || '';
  const actions = ringing.actions || [];
  const snooze = actions.find(action => SNOOZE.test(action.title));
  const stop = actions.find(action => STOP.test(action.title));
  alarmSnooze.textContent = snooze ? snooze.title : '';
  alarmSnooze.hidden = !snooze;
  alarmDismiss.textContent = stop ? stop.title : 'Stop';
}

window.onAlarm = payload => {
  const wasRinging = Boolean(ringing);
  ringing = payload;
  if (payload) {
    clearTimeout(shared.dwellTimer);
    shared.state = 'active';
    shared.current = null;
    pill.classList.remove('alert', 'with-image');
    root.classList.add('ringing');
    paintAlarm();
    showFace('alarm');
    setSize('alarm');
    // The growth is most of the screen, so the mirror is owed the whole of it: the skin and
    // the glass are both read off this box every frame the loop runs.
    stirLiquid(900);
    bridge.triggerHaptic('notification');
    return;
  }
  if (!wasRinging) return;
  root.classList.remove('ringing');
  toClosed();
  stirLiquid(900);
};

alarmSnooze.addEventListener('click', event => {
  event.stopPropagation();
  press(SNOOZE);
});

alarmDismiss.addEventListener('click', event => {
  event.stopPropagation();
  // Nothing is closed here by hand. The alarm is over when the clock app says it is over, and
  // the clock app says so by taking its notification away — closing on the press instead would
  // put the bubble back on the bar while the phone was still ringing.
  press(STOP);
});
