import { lockArt, lockPill, paintLock, paintLockProgress } from '../lock.js';
import { becomeExtended, closedTarget, setSize, showFace, toClosed } from '../row.js';
import { refreshDock } from '../status.js';
import { bridge, mods, pill, root, shared } from '../state.js';

const playerPosition = document.getElementById('player-position');


export function mediaWindow() {
  return closedTarget();
}

export function clock(milliseconds) {
  const total = Math.max(0, Math.round(milliseconds / 1000));
  return Math.floor(total / 60) + ':' + String(total % 60).padStart(2, '0');
}

const mediaArt = document.getElementById('media-art');
const playerArt = document.getElementById('player-art');
let shownArt = null;









function paintArt(art) {
  if (art === shownArt) return;
  shownArt = art;
  if (!art) {
    mediaArt.classList.add('hidden-art');
    playerArt.classList.add('hidden-art');
    lockArt.classList.add('hidden-art');
    songSwapping = false;
    typeLabels();
    return;
  }
  
  
  const covers = [[mediaArt, 110], [playerArt, PLAYER_ART_MS], [lockArt, 110]];

  
  
  
  songSwapping = true;
  for (const [element, milliseconds] of covers) {
    element.style.transitionDuration = milliseconds + 'ms';
    element.classList.add('leaving-art');
  }
  typeLabels('');

  const decoded = new Image();
  decoded.src = art;
  
  
  
  
  const ready = decoded.decode
    ? decoded.decode().catch(() => {})
    : new Promise(done => { decoded.onload = done; });
  
  
  
  const emptied = new Promise(done => setTimeout(done, PLAYER_ART_MS + ART_HOLD));
  Promise.all([ready, emptied]).then(() => {
    
    if (shownArt !== art) return;
    
    for (const [element, milliseconds] of covers) enterArt(element, art, milliseconds);
    
    setTimeout(() => {
      if (shownArt !== art) return;
      songSwapping = false;
      typeLabels();
    }, PLAYER_ART_MS);
  });
}


const PLAYER_ART_MS = 210;





export let songSwapping = false;


export function typeLabels(force) {
  const title = force === undefined ? ((shared.media && shared.media.title) || '') : force;
  const artist = force === undefined ? ((shared.media && shared.media.artist) || '') : force;
  typeInto(document.getElementById('player-title'), title);
  typeInto(document.getElementById('player-artist'), artist);
  typeInto(document.getElementById('lock-title'), title);
  typeInto(document.getElementById('lock-artist'), artist);
}

















export function carryArt() {
  const art = (shared.media && shared.media.artBase64) || '';
  shownArt = art;
  if (!art) return;
  for (const element of [mediaArt, playerArt]) {
    element.classList.remove('leaving-art', 'entering-art', 'hidden-art');
    if (element.getAttribute('src') !== art) element.src = art;
  }
}






const ART_HOLD = 300;


function enterArt(element, art, milliseconds) {
  element.style.transitionDuration = milliseconds + 'ms';
  element.src = art;
  element.classList.remove('hidden-art', 'leaving-art');
  
  
  
  element.classList.add('entering-art');
  requestAnimationFrame(() => requestAnimationFrame(() => {
    element.classList.remove('entering-art');
  }));
}






const TALKERS = ['telegram', 'whatsapp'];
const SPEEDS = [1, 1.5, 2];
const playerSpeed = document.getElementById('player-speed');
let speedAt = 0;

playerSpeed.addEventListener('click', event => {
  event.stopPropagation();
  speedAt = (speedAt + 1) % SPEEDS.length;
  playerSpeed.textContent = SPEEDS[speedAt] + '×';
  bridge.mediaSpeed(String(SPEEDS[speedAt]));
  bridge.triggerHaptic('tap');
});

export function paintMedia() {
  if (!shared.media || shared.isScrubbing) return;
  document.documentElement.style.setProperty(
    '--app-accent', shared.media.accent || 'var(--section-color)'
  );
  pill.classList.toggle('sounding', Boolean(shared.media.isPlaying));

  paintArt(shared.media.artBase64 || '');
  
  
  
  if (!songSwapping) typeLabels();
  document.getElementById('player-duration').textContent = clock(shared.media.duration || 0);
  buildWaveShape();

  
  const talks = TALKERS.includes(shared.media.app);
  if (!talks && speedAt !== 0) {
    speedAt = 0;
    playerSpeed.textContent = '1×';
  }
  playerSpeed.hidden = !talks;
  paintProgress(shared.media.position || 0);
  paintLock();
}


export let shownPosition = 0;

export function paintProgress(position) {
  shownPosition = position;
  setWaveRatio(position);
  playerPosition.textContent = clock(position);
}

// The wave is one drawing read by two surfaces, so how far through the song it is has one owner rather than a copy per place it is painted.
export function setWaveRatio(position) {
  const duration = (shared.media && shared.media.duration) || 0;
  waveRatio = duration > 0 ? Math.min(1, position / duration) : 0;
}





function runMediaClock(from) {
  clearInterval(shared.mediaTicker);
  if (!shared.media || !shared.media.isPlaying) return;
  let position = from === undefined ? (shared.media.position || 0) : from;
  shared.mediaTicker = setInterval(() => {
    if (shared.isScrubbing) return;
    position += 500;
    /* A song outlasts a screen, so this is the one ticker that runs for hours with nothing on screen — twice a second, a width and a reading written into a lock bubble nobody can see. The count goes on, so the bar is right the moment the screen comes back; only the painting sleeps. */
    if (shared.isStageHidden) return;
    
    
    if (shared.size === 'player') paintProgress(position);
    if (lockPill.classList.contains('showing')) paintLockProgress(position);
  }, 500);
}


let positionAt = 0;











export function livePosition() {
  if (!shared.media) return 0;
  const since = shared.media.isPlaying ? performance.now() - positionAt : 0;
  return Math.round((shared.media.position || 0) + since);
}

window.onMediaUpdate = payload => {
  positionAt = performance.now();
  
  
  
  if (payload && !('artBase64' in payload) && shared.media) {
    payload.artBase64 = shared.media.artBase64;
  }
  shared.media = payload;
  
  
  
  refreshDock();
  if (!shared.media) {
    mods.delete('media');
    clearInterval(shared.mediaTicker);
    pill.classList.remove('sounding');
    if (shared.state === 'idle') toClosed();
    
    
    
    paintLock();
    return;
  }
  mods.add('media');
  paintMedia();
  runMediaClock();
  runBars();
  
  
  if (shared.state === 'idle') toClosed();
};












const BARS = '#equalizer span, .sat-bars span';


let barRun = 0;

function pulse(bar, from, run) {
  const high = 62 + Math.random() * 38;
  
  
  const low = 18 + Math.random() * 26;
  const top = Math.random() < 0.2 ? low + 8 : high;
  const swing = bar.animate(
    [{ height: from + '%' }, { height: top + '%' }, { height: low + '%' }],
    { duration: 500 + Math.random() * 750, easing: 'ease-in-out' }
  );
  swing.onfinish = () => {
    if (run !== barRun || !bar.isConnected) return;
    bar.style.height = low + '%';
    pulse(bar, low, run);
  };
}






export function runBars() {
  barRun += 1;
  const run = barRun;
  
  
  
  if (!shared.media || !shared.media.isPlaying) {
    document.querySelectorAll(BARS).forEach(bar => {
      bar.getAnimations().forEach(existing => existing.cancel());
      bar.animate(
        [{ height: bar.getBoundingClientRect().height + 'px' }, { height: '3px' }],
        { duration: 260, easing: 'ease-out' }
      );
      bar.style.height = '3px';
    });
    return;
  }
  document.querySelectorAll(BARS).forEach(bar => {
    bar.getAnimations().forEach(existing => existing.cancel());
    pulse(bar, parseFloat(bar.style.height) || 40, run);
  });
}






const waveBox = document.getElementById('player-wave');
const waveCanvas = document.getElementById('player-wave-canvas');

const WAVE_BARS = 54;
const WAVE_BEAT_MILLIS = 480;
const WAVE_REST = 0.12;

let waveRatio = 0;
let waveFrame = 0;
let waveEnergy = WAVE_REST;
let waveShape = [];
let waveShapeFor = null;







function buildWaveShape() {
  const song = shared.media
    ? (shared.media.title || '') + '|' + (shared.media.artist || '')
    : '';
  if (song === waveShapeFor && waveShape.length) return;
  waveShapeFor = song;
  let seed = 2166136261;
  for (let index = 0; index < song.length; index += 1) {
    seed = Math.imul(seed ^ song.charCodeAt(index), 16777619);
  }
  waveShape = [];
  let level = 0.5;
  for (let index = 0; index < WAVE_BARS; index += 1) {
    seed = Math.imul(seed ^ (seed >>> 15), 2246822519);
    level = level * 0.5 + (((seed >>> 8) & 0xffff) / 0xffff) * 0.5;
    waveShape.push(0.24 + level * 0.76);
  }
}

function fitWave(canvas) {
  const box = canvas.getBoundingClientRect();
  if (!box.width || !box.height) return;
  const ratio = window.devicePixelRatio || 1;
  canvas.width = Math.round(box.width * ratio);
  canvas.height = Math.round(box.height * ratio);
}

const waveWatch = new ResizeObserver(entries => entries.forEach(entry => fitWave(entry.target)));






// The beat is the song's, not the surface's, so the two places it is drawn are two surfaces reading one energy rather than two waves that happen to look alike. A surface says what keeps it alive instead of the loop testing for the player's own size, since the same loop now paints a canvas that stands at the foot of a panel the player face is never open behind.
const waveSurfaces = new Map();

export function joinWave(canvas, isWanted) {
  if (waveSurfaces.has(canvas)) return;
  waveSurfaces.set(canvas, { ink: canvas.getContext('2d'), isWanted });
  waveWatch.observe(canvas);
  fitWave(canvas);
  runWave();
}

function leaveWave(canvas) {
  waveSurfaces.delete(canvas);
  waveWatch.unobserve(canvas);
}







function beatWave(now) {
  const isPlaying = Boolean(shared.media && shared.media.isPlaying);
  const beat = isPlaying
    ? Math.pow(1 - ((now % WAVE_BEAT_MILLIS) / WAVE_BEAT_MILLIS), 2.4)
    : 0;
  const wanted = isPlaying ? 0.42 + beat * 0.58 : WAVE_REST;
  waveEnergy += (wanted - waveEnergy) * 0.24;
  return isPlaying;
}

function drawWave(canvas, waveInk, now, isPlaying) {
  const width = canvas.width;
  const height = canvas.height;
  if (!width || !height) return;
  const accent = getComputedStyle(canvas).getPropertyValue('--app-accent').trim() || '#ffffff';
  const middle = height / 2;
  const step = width / WAVE_BARS;
  const thickness = Math.max(2, step * 0.44);
  waveInk.clearRect(0, 0, width, height);
  for (let index = 0; index < WAVE_BARS; index += 1) {
    const share = (index + 0.5) / WAVE_BARS;
    const shimmer = isPlaying ? 0.76 + 0.24 * Math.sin(now / 180 + index * 0.9) : 1;
    const reach = Math.max(1, waveShape[index] * waveEnergy * shimmer * (middle - 2));
    waveInk.fillStyle = share <= waveRatio ? accent : 'rgba(255, 255, 255, 0.32)';
    waveInk.fillRect(index * step + (step - thickness) / 2, middle - reach, thickness, reach * 2);
  }
  const head = waveRatio * width;
  waveInk.fillStyle = accent;
  waveInk.fillRect(head - 1, 0, shared.isScrubbing ? 3 : 2, height);
}

function runWave() {
  cancelAnimationFrame(waveFrame);
  const step = now => {
    for (const [canvas, surface] of waveSurfaces) {
      if (!surface.isWanted()) leaveWave(canvas);
    }
    if (!waveSurfaces.size) return;
    if (!shared.isStageHidden) {
      const isPlaying = beatWave(now);
      for (const [canvas, surface] of waveSurfaces) drawWave(canvas, surface.ink, now, isPlaying);
    }
    waveFrame = requestAnimationFrame(step);
  };
  waveFrame = requestAnimationFrame(step);
}







function waveSeek(clientX) {
  const box = waveCanvas.getBoundingClientRect();
  const share = Math.min(1, Math.max(0, (clientX - box.left) / box.width));
  return Math.round(share * (shared.media.duration || 0));
}

waveBox.addEventListener('touchstart', event => {
  if (!shared.media || !shared.media.duration) return;
  event.stopPropagation();
  shared.isScrubbing = true;
  waveBox.classList.add('scrubbing');
  shared.scrubPosition = waveSeek(event.touches[0].clientX);
  paintProgress(shared.scrubPosition);
}, { passive: true });

waveBox.addEventListener('touchmove', event => {
  if (!shared.isScrubbing) return;
  event.stopPropagation();
  shared.scrubPosition = waveSeek(event.touches[0].clientX);
  paintProgress(shared.scrubPosition);
}, { passive: true });

function releaseWave(seeks) {
  if (!shared.isScrubbing) return;
  shared.isScrubbing = false;
  waveBox.classList.remove('scrubbing');
  if (!seeks) return;
  bridge.triggerHaptic('tap');
  bridge.mediaSeek(String(shared.scrubPosition));
}

waveBox.addEventListener('touchend', event => {
  event.stopPropagation();
  releaseWave(true);
}, { passive: true });

waveBox.addEventListener('touchcancel', () => releaseWave(false), { passive: true });
waveBox.addEventListener('click', event => event.stopPropagation());












const PLAYER_SWIPE = 52;
const PLAYER_DRAG_FOLLOW = 0.55;
const PLAY_GLYPH = 'M8 5l11 7-11 7z';
const PAUSE_GLYPH = 'M7 5h4v14H7Zm6 0h4v14h-4Z';
const playerFace = document.getElementById('player-face');
const playerHint = document.getElementById('player-hint');
const playerHintPath = document.getElementById('player-hint-path');
let swipeFrom = null;

function dragArt(across) {
  playerArt.classList.add('dragged');
  playerArt.style.translate = Math.round(across * PLAYER_DRAG_FOLLOW) + 'px';
}

function releaseArt(thrown) {
  playerArt.classList.remove('dragged');
  playerArt.style.transitionDuration = PLAYER_ART_MS + 'ms';
  playerArt.style.translate = thrown ? Math.sign(thrown) * 140 + 'px' : '';
  if (thrown) setTimeout(() => { playerArt.style.translate = ''; }, PLAYER_ART_MS);
}

function showHint(share) {
  playerHintPath.setAttribute('d', shared.media && shared.media.isPlaying ? PAUSE_GLYPH : PLAY_GLYPH);
  playerHint.classList.remove('settling');
  playerHint.style.opacity = Math.min(1, share);
  playerHint.style.scale = 0.55 + 0.45 * Math.min(1, share);
}

function releaseHint(struck) {
  if (!playerHint.style.opacity) return;
  playerHint.classList.add('settling');
  playerHint.style.opacity = '0';
  playerHint.style.scale = struck ? 1.35 : 0.55;
}

playerFace.addEventListener('touchstart', event => {
  swipeFrom = shared.isScrubbing
    ? null
    : { x: event.touches[0].clientX, y: event.touches[0].clientY, isSpent: false };
}, { passive: true });

playerFace.addEventListener('touchmove', event => {
  if (!swipeFrom || swipeFrom.isSpent || shared.isScrubbing) return;
  const across = event.touches[0].clientX - swipeFrom.x;
  const down = event.touches[0].clientY - swipeFrom.y;
  if (Math.abs(across) > Math.abs(down)) {
    dragArt(across);
    if (Math.abs(across) < PLAYER_SWIPE) return;
    swipeFrom.isSpent = true;
    bridge.triggerHaptic('expand');
    bridge.mediaControl(across > 0 ? 'previous' : 'next');
    releaseArt(across);
    return;
  }
  if (down > 0) showHint(down / PLAYER_SWIPE);
  if (down < PLAYER_SWIPE) return;
  swipeFrom.isSpent = true;
  bridge.triggerHaptic('expand');
  const wanted = shared.media && shared.media.isPlaying ? 'pause' : 'play';
  releaseHint(true);
  flipPlaying();
  bridge.mediaControl(wanted);
}, { passive: true });

for (const type of ['touchend', 'touchcancel']) {
  playerFace.addEventListener(type, () => {
    swipeFrom = null;
    releaseArt(0);
    releaseHint(false);
  }, { passive: true });
}

export function openPlayer() {
  becomeExtended();
  paintMedia();
  showFace('player');
  setSize('player');
  const position = livePosition();
  paintProgress(position);
  runMediaClock(position);
  playEntrance();
  joinWave(waveCanvas, () => shared.size === 'player');
}









function playEntrance() {
  
  
  playerArt.style.transitionDuration = PLAYER_ART_MS + 'ms';
  playerArt.classList.remove('hidden-art', 'leaving-art');
  playerArt.classList.add('entering-art');
  requestAnimationFrame(() => requestAnimationFrame(() => {
    playerArt.classList.remove('entering-art');
  }));
  
  document.getElementById('player-title').textContent = '';
  document.getElementById('player-artist').textContent = '';
  songSwapping = true;
  setTimeout(() => {
    songSwapping = false;
    typeLabels();
  }, PLAYER_ART_MS);
}











export function flipPlaying() {
  if (!shared.media) return;
  shared.media.isPlaying = !shared.media.isPlaying;
  pill.classList.toggle('sounding', Boolean(shared.media.isPlaying));
}














const TYPE_MILLIS = 60;
const UNTYPE_MILLIS = 30;
const typing = new WeakMap();

export function typeInto(element, next) {
  const wanted = next || '';
  const running = typing.get(element);
  if (running && running.wanted === wanted) return;
  if (!running && element.textContent === wanted) return;
  if (running) clearTimeout(running.timer);
  const state = { wanted, timer: 0 };
  typing.set(element, state);
  element.classList.add('typing');
  
  
  
  
  
  const step = () => {
    const shown = element.textContent;
    if (shown.length && !wanted.startsWith(shown)) {
      element.textContent = shown.slice(0, -1);
      state.timer = setTimeout(step, UNTYPE_MILLIS);
      return;
    }
    if (shown === wanted) {
      typing.delete(element);
      element.classList.remove('typing');
      return;
    }
    element.textContent = wanted.slice(0, shown.length + 1);
    state.timer = setTimeout(step, TYPE_MILLIS);
  };
  step();
}

// The arriving Now bubble types its labels in, so they have to be genuinely empty at the moment it is born. Asking typeInto for '' instead untyped them over the flight, and typeInto reads a half-untyped label as a prefix of the real one and types it forward again — the labels never cleared and the arrival raced its own leftovers.
export function blankLockLabels() {
  for (const id of ['lock-title', 'lock-artist']) {
    const element = document.getElementById(id);
    const running = typing.get(element);
    if (running) clearTimeout(running.timer);
    typing.delete(element);
    element.classList.remove('typing');
    element.textContent = '';
  }
}
