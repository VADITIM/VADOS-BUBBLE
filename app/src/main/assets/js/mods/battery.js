import { setSize, showFace, toClosed } from '../row.js';
import { bridge, pill, root, shared } from '../state.js';

/** Long enough to look at, short enough that it is over before it is in the way. */
const BATTERY_DWELL = 4200;

const BATTERY_COLOURS = {
  low: '#ffb300',
  critical: '#ff3b30',
  charging: '#5bfd5b',
};

/**
 * A battery event, shown once. It borrows the alert's state rather than becoming a
 * mod of its own: it is news, it dwells, and then the bubble goes back to whatever
 * was true before it — a song, a timer, or nothing.
 */
window.onBattery = payload => {
  if (!payload) return;
  clearTimeout(shared.dwellTimer);
  shared.state = 'alert';
  // Nothing to open: the tap on this one only sends it away.
  shared.current = null;
  pill.classList.remove('alert', 'with-image');

  const level = Math.max(0, Math.min(100, Number(payload.level) || 0));
  root.style.setProperty('--app-accent', BATTERY_COLOURS[payload.state] || BATTERY_COLOURS.low);
  // Never below a sliver: a battery drawn as empty says the phone is off.
  root.style.setProperty('--battery-level', Math.max(8, level) + '%');
  document.getElementById('battery-reading').textContent =
    payload.state === 'charging' ? 'Charging · ' + level + ' %' : level + ' %';

  showFace('battery');
  setSize('battery');
  // The class carries the animation, and it is set after the face so the fill
  // starts its run from the level this event actually arrived with.
  pill.classList.toggle('charging', payload.state === 'charging');
  bridge.triggerHaptic('notification');

  shared.dwellTimer = setTimeout(toClosed, BATTERY_DWELL);
};
