import { becomeExtended, setSize, showFace, toClosed } from '../row.js';
import { refreshDock } from '../status.js';
import { bridge, mods, shared } from '../state.js';

const tripRemaining = document.getElementById('trip-remaining');
const tripClock = document.getElementById('trip-clock');

function remainingText() {
  if (!shared.trip) return '';
  if (!shared.trip.endsAt) return shared.trip.arrival || shared.trip.train || '';
  const left = Math.max(0, Math.round((shared.trip.endsAt - Date.now()) / 1000));
  const hours = Math.floor(left / 3600);
  const minutes = Math.floor((left % 3600) / 60);
  const seconds = left % 60;
  const pad = value => String(value).padStart(2, '0');
  return hours > 0
    ? hours + ':' + pad(minutes) + ':' + pad(seconds)
    : minutes + ':' + pad(seconds);
}

function writeReading() {
  const reading = remainingText();
  tripRemaining.textContent = reading;
  tripClock.textContent = reading;
}

function writeField(id, value) {
  const element = document.getElementById(id);
  element.textContent = value || '';
  element.hidden = !value;
}

export function paintTrip() {
  if (!shared.trip) return;
  document.documentElement.style.setProperty('--app-accent', shared.trip.accent || 'var(--section-color)');
  writeReading();
  const trip = shared.trip;
  document.getElementById('trip-label').textContent = trip.appName || 'DB Navigator';
  writeField('trip-train', trip.train);
  writeField('trip-route', trip.origin || trip.destination
    ? [trip.origin, trip.destination].filter(Boolean).join(' → ') : '');
  writeField('trip-exit', trip.exitStop && 'Aussteigen: ' + trip.exitStop);
  writeField('trip-next', trip.nextStop && 'Nächster Halt: ' + trip.nextStop);
  writeField('trip-arrival', trip.arrival && 'an ' + trip.arrival);
  writeField('trip-delay', trip.delay);
  writeField('trip-platform', trip.platform && 'Gl. ' + trip.platform);

  const lines = document.getElementById('trip-lines');
  lines.textContent = '';
  for (const line of trip.lines || []) {
    const row = document.createElement('div');
    row.className = 'trip-line';
    row.textContent = line;
    lines.appendChild(row);
  }
}

function runTripClock() {
  clearInterval(shared.tripTicker);
  if (!shared.trip || !shared.trip.endsAt) return;
  shared.tripTicker = setInterval(() => {
    if (!shared.trip || shared.isStageHidden) return;
    writeReading();
  }, 1000);
}

export function openTrip() {
  becomeExtended();
  paintTrip();

  const actions = document.getElementById('trip-actions');
  actions.textContent = '';
  for (const action of (shared.trip && shared.trip.actions) || []) {
    const button = document.createElement('div');
    button.className = 'timer-button';
    button.textContent = action.title;
    button.addEventListener('click', event => {
      event.stopPropagation();
      bridge.triggerHaptic('tap');
      bridge.tripAction(action.index);
      toClosed();
    });
    actions.appendChild(button);
  }

  showFace('tripPanel');
  setSize('trip');
}

window.onTripUpdate = payload => {
  shared.trip = payload;
  if (!shared.trip) {
    mods.delete('trip');
    shared.clearedMods.delete('trip');
    refreshDock();
    clearInterval(shared.tripTicker);
    if (shared.state === 'idle' || shared.size === 'trip') toClosed();
    return;
  }
  mods.add('trip');
  refreshDock();
  paintTrip();
  runTripClock();
  if (shared.state === 'idle') toClosed();
};
