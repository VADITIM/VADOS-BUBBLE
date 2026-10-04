import { lockArt, lockPill, paintLock, paintLockProgress } from '../lock.js';
import { becomeExtended, closedTarget, setSize, showFace, toClosed } from '../row.js';
import { refreshDock } from '../status.js';
import { bridge, faces, mods, pill, root, shared } from '../state.js';
import { endHold } from '../motion.js';

window.probeAt = 0; // PROBE
window.probeMark = label => { if (window.probeAt && performance.now() - window.probeAt < 1500) console.log('[probe] ' + label + ' @' + Math.round(performance.now() - window.probeAt)); }; // PROBE
let probeWarmAt = 0; // PROBE
function probeFrames() { const deltas = []; const heaps = []; let last = performance.now(); const until = last + 900; const tick = now => { deltas.push(Math.round(now - last)); if (now - last > 20) window.probeMark('LONG FRAME ' + Math.round(now - last) + 'ms'); if (performance.memory) heaps.push(Math.round(performance.memory.usedJSHeapSize / 1024)); last = now; if (now < until) { requestAnimationFrame(tick); return; } console.log('[probe] deltas ' + deltas.join(',')); console.log('[probe] heapKB ' + heaps.filter((value, index) => index === 0 || value < heaps[index - 1] || index === heaps.length - 1).join(',')); }; requestAnimationFrame(tick); } // PROBE
try { new PerformanceObserver(list => list.getEntries().forEach(entry => console.log('[probe] loaf ' + Math.round(entry.duration) + 'ms at ' + Math.round(entry.startTime - window.probeAt) + ' render@' + Math.round(entry.renderStart - entry.startTime) + ' style@' + Math.round(entry.styleAndLayoutStart - entry.startTime) + ' scripts ' + entry.scripts.map(script => script.invoker + ':' + script.sourceFunctionName + ':' + Math.round(script.duration) + ':forced' + Math.round(script.forcedStyleAndLayoutDuration)).join('|')))).observe({ type: 'long-animation-frame' }); } catch (error) { console.log('[probe] no loaf ' + error); } // PROBE
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
  if (art === shownArt) return false;
  shownArt = art;
  if (!art) {
    mediaArt.classList.add('hidden-art');
    playerArt.classList.add('hidden-art');
    lockArt.classList.add('hidden-art');
    songSwapping = false;
    clearTimeout(thrownTimer);
    thrownAt = null;
    typeLabels();
    return false;
  }
  
  
  const covers = [[mediaArt, 110], [playerArt, PLAYER_ART_MS], [lockArt, 110]];
  const wasThrown = thrownAt !== null;
  const throwLeft = wasThrown ? Math.max(0, thrownAt + THROW_MS - performance.now()) : 0;
  clearTimeout(thrownTimer);
  thrownAt = null;




  songSwapping = true;
  for (const [element, milliseconds] of covers) {
    element.style.transitionDuration = milliseconds + 'ms';
    element.classList.add('leaving-art');
  }
  typeLabels('');

  window.probeMark('paintArt NEW ART'); // PROBE
  const decoded = new Image();
  decoded.src = art;
  
  
  
  
  const ready = decoded.decode
    ? decoded.decode().catch(() => {})
    : new Promise(done => { decoded.onload = done; });
  
  
  
  const emptied = new Promise(done => setTimeout(done, wasThrown ? Math.max(throwLeft, 110) : PLAYER_ART_MS + ART_HOLD));
  Promise.all([ready, emptied]).then(() => {
    
    if (shownArt !== art) return;
    
    for (const [element, milliseconds] of covers) enterArt(element, art, milliseconds);
    
    setTimeout(() => {
      if (shownArt !== art) return;
      songSwapping = false;
      playerArt.style.removeProperty('--art-exit');
      typeLabels();
    }, PLAYER_ART_MS);
  });
  return true;
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
  element.style.translate = '';
  element.style.transitionTimingFunction = '';
  if (art && element.getAttribute('src') !== art) element.src = art;
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
  refreshWaveAccents();
  pill.classList.toggle('sounding', Boolean(shared.media.isPlaying));

  const isArtSwapping = paintArt(shared.media.artBase64 || '');
  if (!isArtSwapping && thrownAt !== null && songKey() !== thrownSong) landThrow();
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
  window.probeMark('onMediaUpdate art ' + Boolean(payload && payload.artBase64) + ' same ' + Boolean(payload && shared.media && payload.artBase64 === shared.media.artBase64)); // PROBE
  positionAt = performance.now();
  
  
  
  if (payload && !('artBase64' in payload) && shared.media) {
    payload.artBase64 = shared.media.artBase64;
  }
  shared.media = payload;
  
  
  
  refreshDock();
  if (!shared.media) {
    mods.delete('media');
    shared.clearedMods.delete('media');
    clearInterval(shared.mediaTicker);
    pill.classList.remove('sounding');
    if (shared.state === 'idle') toClosed();
    
    
    
    paintLock();
    return;
  }
  mods.add('media');
  paintMedia();
  runMediaClock();
  joinEqualizer();
  
  
  if (shared.state === 'idle') toClosed();
};












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

function fitWave(canvas, canShrink) {
  const probeStart = performance.now(); // PROBE
  const box = canvas.getBoundingClientRect();
  window.probeMark('fitWave ' + canvas.id + ' rect ' + (performance.now() - probeStart).toFixed(1) + 'ms box ' + Math.round(box.width) + 'x' + Math.round(box.height) + ' has ' + canvas.width + 'x' + canvas.height); // PROBE
  if (!box.width || !box.height) return;
  const ratio = window.devicePixelRatio || 1;
  const width = Math.round(box.width * ratio);
  const height = Math.round(box.height * ratio);
  if (!canShrink && width <= canvas.width && height <= canvas.height) return;
  if (width === canvas.width && height === canvas.height) return;
  canvas.width = width;
  canvas.height = height;
}

const WAVE_SETTLE = 120;

/* Fitted on every observation, the canvas reallocated its backing store on each frame of the player's 460ms growth and of every band change under a scrub — a new GPU surface sixty times over for a box still on its way somewhere. It is fitted once the box has stopped, and stretched by the page in between. */
const waveWatch = new ResizeObserver(entries => entries.forEach(entry => {
  const surface = waveSurfaces.get(entry.target);
  if (!surface) return;
  clearTimeout(surface.fitTimer);
  surface.fitTimer = setTimeout(() => fitWave(entry.target, true), WAVE_SETTLE);
}));






// The beat is the song's, not the surface's, so the two places it is drawn are two surfaces reading one energy rather than two waves that happen to look alike. A surface says what keeps it alive instead of the loop testing for the player's own size, since the same loop now paints a canvas that stands at the foot of a panel the player face is never open behind.
const waveSurfaces = new Map();

export function joinWave(canvas, isWanted, bars = 0) {
  const joined = waveSurfaces.get(canvas);
  if (joined) {
    joined.accent = null;
    return;
  }
  waveSurfaces.set(canvas, { ink: canvas.getContext('2d'), isWanted, bars, accent: null, fitTimer: 0 });
  waveWatch.observe(canvas);
  fitWave(canvas, false);
  runWave();
}

const equalizer = document.getElementById('equalizer');
const EQUALIZER_BARS = 5;

// The idle indicator stays joined past the pause only until the shared energy has settled, so a paused song leaves its bars at rest rather than frozen mid-swing.
export function joinEqualizer() {
  joinWave(equalizer, () => faces.media.classList.contains('showing')
    && Boolean(shared.media && (shared.media.isPlaying || waveEnergy > WAVE_REST + 0.005)), EQUALIZER_BARS);
}

function leaveWave(canvas) {
  clearTimeout(waveSurfaces.get(canvas).fitTimer);
  waveSurfaces.delete(canvas);
  waveWatch.unobserve(canvas);
}

function refreshWaveAccents() {
  for (const surface of waveSurfaces.values()) surface.accent = null;
}







let waveKick = 0;
// A dot turning into a bar jumped to the bar's full height on the frame the song reached it; each bar remembers when it was reached and grows out of its dot.
const WAVE_GROW_MILLIS = 320;
const waveBornAt = new Array(WAVE_BARS).fill(0);
let waveFrameAt = 0;

// The kick jumped to full on one frame and was eased by a per-frame factor, so it hit too hard and stepped with the frame rate. The pulse is a smooth envelope now — a short rise, a longer fall — and the smoothing is scaled by the time the frame actually took.
function beatWave(now) {
  const isPlaying = Boolean(shared.media && shared.media.isPlaying);
  const elapsed = Math.min(64, waveFrameAt ? now - waveFrameAt : 16);
  waveFrameAt = now;
  const since = livePosition() % WAVE_BEAT_MILLIS;
  const pulse = isPlaying
    ? wavePulse(since)
    : 0;
  waveKick += (pulse - waveKick) * (1 - Math.exp(-elapsed / 35));
  const wanted = isPlaying ? 0.36 + waveKick * 0.16 : WAVE_REST;
  waveEnergy += (wanted - waveEnergy) * (1 - Math.exp(-elapsed / 60));
  return isPlaying;
}

function drawWave(canvas, surface, now, isPlaying) {
  const width = canvas.width;
  const height = canvas.height;
  if (!width || !height) return;
  const waveInk = surface.ink;
  /* A computed style read every frame is a style recalculation forced every frame, in the middle of the growth that is already paying for one — for a colour that changes when the song does. */
  if (!surface.accent) {
    surface.accent = getComputedStyle(canvas).getPropertyValue('--app-accent').trim() || '#ffffff';
  }
  const accent = surface.accent;
  const middle = height / 2;
  const count = surface.bars || WAVE_BARS;
  const isWhole = Boolean(surface.bars);
  const step = width / count;
  const thickness = Math.max(2, step * 0.62);
  waveInk.clearRect(0, 0, width, height);
  const dot = Math.max(2, thickness * 0.7);
  const since = livePosition() % WAVE_BEAT_MILLIS;
  // Every bar moved as one swell, the unplayed ones included, so the wave read as a slab breathing. Only what has been played is live now: each bar has its own two rates, the beat ripples out from the bass end a few milliseconds a bar, and what is still to come is a row of dots waiting.
  for (let index = 0; index < count; index += 1) {
    const share = (index + 0.5) / count;
    const x = index * step + (step - thickness) / 2;
    if (!isWhole && share > waveRatio) {
      waveBornAt[index] = 0;
      waveInk.fillStyle = 'rgba(255, 255, 255, 0.32)';
      waveInk.beginPath();
      waveInk.arc(x + thickness / 2, middle, dot / 2, 0, Math.PI * 2);
      waveInk.fill();
      continue;
    }
    const shape = waveShape[Math.floor(share * WAVE_BARS)] || 0.5;
    const ripple = isPlaying ? wavePulse((since - index * 7 + WAVE_BEAT_MILLIS) % WAVE_BEAT_MILLIS) : 0;
    const wobble = isPlaying
      ? 0.5 + 0.28 * Math.sin(now / (170 + shape * 90) + index * 2.1) + 0.22 * Math.sin(now / (95 + shape * 40) + index * 0.6)
      : 0;
    const bass = ripple * (0.35 + 0.65 * Math.pow(1 - share, 1.4));
    const level = Math.min(1, shape * (waveEnergy * 0.55 + wobble * 0.3 + bass * 0.45));
    if (!isWhole && !waveBornAt[index]) waveBornAt[index] = now;
    const grown = isWhole ? 1 : 1 - Math.pow(1 - Math.min(1, (now - waveBornAt[index]) / WAVE_GROW_MILLIS), 3);
    const reach = dot / 2 + Math.max(0, level * (middle - 2) - dot / 2) * grown;
    waveInk.fillStyle = accent;
    waveInk.beginPath();
    waveInk.roundRect(x, middle - reach, thickness, reach * 2, Math.min(thickness / 2, reach) * 0.6);
    waveInk.fill();
  }
}

function wavePulse(since) {
  return since < 45 ? Math.sin((since / 45) * Math.PI / 2) : Math.exp(-(since - 45) / 150);
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
      const probeStart = performance.now(); // PROBE
      for (const [canvas, surface] of waveSurfaces) drawWave(canvas, surface, now, isPlaying);
      if (performance.now() - probeStart > 2 && window.probeMark) window.probeMark('drawWave ' + (performance.now() - probeStart).toFixed(1) + 'ms ' + waveCanvas.width + 'x' + waveCanvas.height); // PROBE
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












// The hold under an expanded mod opens its app with a buzz, and a swipe runs long enough to reach it — so the hold is called off as soon as the finger has clearly started travelling, well before the song changes.
const PLAYER_HOLD_OFF = 17;
const PLAYER_SWIPE = 105;
const PLAYER_DRAG_FOLLOW = 0.85;
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

function releaseArt() {
  if (!playerArt.classList.contains('dragged')) return;
  playerArt.classList.remove('dragged');
  playerArt.style.transitionDuration = PLAYER_ART_MS + 'ms';
  playerArt.style.translate = '';
}

// Mirrors the player's width in pill.css (#pill.player), so a thrown cover is wholly outside the bubble before the next one is let in.
const PLAYER_THROW = 340;
// Long enough for the host to encode the next cover; a song that never sends one gets the old cover back rather than an empty tab.
const THROW_WAIT = 1500;
const THROW_MS = 440;
let thrownAt = null;
let thrownSong = '';
let thrownTimer = 0;

function songKey() {
  return shared.media ? (shared.media.title || '') + '|' + (shared.media.artist || '') : '';
}

/* The thrown cover sprang back to the middle on the release and then left a second time when the next song's art arrived, and the labels retyped on the metadata while the art was still decoding — three things moving on three clocks, over a frame the host was blocking to encode the cover. The cover now leaves once, wholly, and nothing else changes until the next one is decoded and comes in from the other side. */
function throwArt(across) {
  const direction = Math.sign(across);
  playerArt.classList.remove('dragged');
  playerArt.style.transitionDuration = THROW_MS + 'ms';
  playerArt.style.transitionTimingFunction = 'cubic-bezier(0.25, 0.1, 0.25, 1)';
  playerArt.style.setProperty('--art-exit', direction * 76 + 'px');
  playerArt.style.translate = direction * PLAYER_THROW + 'px';
  playerArt.classList.add('leaving-art');
  songSwapping = true;
  typeLabels('');
  thrownAt = performance.now();
  thrownSong = songKey();
  clearTimeout(thrownTimer);
  thrownTimer = setTimeout(landThrow, THROW_WAIT);
}

// A song that never sends new art gets the old cover back rather than an empty bubble.
export function throwLockArt() {
  lockArt.style.transitionDuration = THROW_MS + 'ms';
  lockArt.style.transitionTimingFunction = 'cubic-bezier(0.25, 0.1, 0.25, 1)';
  lockArt.style.translate = -Math.round(lockPill.getBoundingClientRect().width) + 'px';
  lockArt.classList.add('leaving-art');
  const art = shownArt;
  setTimeout(() => {
    if (shownArt === art && lockArt.classList.contains('leaving-art')) enterArt(lockArt, art, PLAYER_ART_MS);
  }, THROW_WAIT);
}

function landThrow() {
  if (thrownAt === null) return;
  const wait = Math.max(0, thrownAt + THROW_MS - performance.now());
  clearTimeout(thrownTimer);
  thrownAt = null;
  const art = shownArt;
  setTimeout(() => {
    if (thrownAt !== null || shownArt !== art) return;
    enterArt(playerArt, shownArt, PLAYER_ART_MS);
    setTimeout(() => {
      if (thrownAt !== null || shownArt !== art) return;
      songSwapping = false;
      playerArt.style.removeProperty('--art-exit');
      typeLabels();
    }, PLAYER_ART_MS);
  }, wait);
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
  if (Math.max(Math.abs(across), Math.abs(down)) >= PLAYER_HOLD_OFF) endHold();
  if (Math.abs(across) > Math.abs(down)) {
    dragArt(across);
    if (Math.abs(across) < PLAYER_SWIPE) return;
    swipeFrom.isSpent = true;
    bridge.triggerHaptic('expand');
    throwArt(across);
    bridge.mediaControl(across > 0 ? 'previous' : 'next');
    return;
  }
  if (down > 0) showHint(down / PLAYER_SWIPE);
  if (down < PLAYER_SWIPE) return;
  swipeFrom.isSpent = true;
  const wanted = shared.media && shared.media.isPlaying ? 'pause' : 'play';
  bridge.triggerHaptic('expand');
  releaseHint(true);
  flipPlaying();
  bridge.mediaControl(wanted);
}, { passive: true });

for (const type of ['touchend', 'touchcancel']) {
  playerFace.addEventListener(type, () => {
    swipeFrom = null;
    releaseArt();
    releaseHint(false);
  }, { passive: true });
}

export function openPlayer() {
  const probeTimes = [performance.now()]; window.probeAt = probeTimes[0]; console.log('[probe] OPEN warmedAgo ' + Math.round(probeTimes[0] - probeWarmAt) + ' canvas ' + waveCanvas.width + 'x' + waveCanvas.height + ' joined ' + waveSurfaces.has(waveCanvas)); // PROBE
  becomeExtended();
  paintMedia();
  probeTimes.push(performance.now()); // PROBE
  showFace('player');
  probeTimes.push(performance.now()); // PROBE
  setSize('player');
  probeTimes.push(performance.now()); // PROBE
  const position = livePosition();
  paintProgress(position);
  runMediaClock(position);
  playEntrance();
  probeTimes.push(performance.now()); // PROBE
  joinWave(waveCanvas, () => shared.size === 'player');
  probeTimes.push(performance.now()); // PROBE
  console.log('[probe] sync paintMedia/showFace/setSize/entrance/joinWave ' + probeTimes.slice(1).map((time, index) => (time - probeTimes[index]).toFixed(1)).join('/')); probeFrames(); // PROBE
}

const WARM_DELAY = 900;
const WARM_HOLD = 160;
let warmTimer = 0;

/* The player is the only face drawn through backdrop blurs and a masked cover, and a process that had never drawn them compiled them on the expand's first frame — 100ms measured with the growth standing still, every first open after a restart. They are drawn once beforehand, too faint to see, while nothing is moving. */
export function warmPlayer() {
  clearTimeout(warmTimer);
  warmTimer = setTimeout(() => {
    if (shared.isStageHidden || shared.size === 'player') return;
    playerFace.classList.add('warming'); probeWarmAt = performance.now(); // PROBE
    warmTimer = setTimeout(() => playerFace.classList.remove('warming'), WARM_HOLD);
  }, WARM_DELAY);
}

warmPlayer();









function playEntrance() {
  
  
  clearTimeout(thrownTimer);
  thrownAt = null;
  playerArt.style.transitionDuration = PLAYER_ART_MS + 'ms';
  playerArt.style.translate = '';
  playerArt.style.removeProperty('--art-exit');
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
