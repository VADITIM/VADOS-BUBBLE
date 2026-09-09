import { lockArt, lockPill, paintLock, paintLockProgress } from '../lock.js';
import { becomeExtended, closedTarget, setSize, showFace, toClosed } from '../row.js';
import { paintQuickMedia } from '../status.js';
import { bridge, mods, pill, shared } from '../state.js';

const playerElapsed = document.getElementById('player-elapsed');
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

let shownAccent = '';

export function paintMedia() {
  if (!shared.media || shared.isScrubbing) return;
  
  
  
  const accent = shared.media.accent || 'var(--section-color)';
  if (accent !== shownAccent) {
    shownAccent = accent;
    document.documentElement.style.setProperty('--app-accent', accent);
  }
  pill.classList.toggle('sounding', Boolean(shared.media.isPlaying));

  paintArt(shared.media.artBase64 || '');
  
  
  
  if (!songSwapping) typeLabels();
  document.getElementById('player-duration').textContent = clock(shared.media.duration || 0);
  document.getElementById('player-play-path').setAttribute('d', playPath());
  
  
  const talks = TALKERS.includes(shared.media.app);
  if (!talks && speedAt !== 0) {
    speedAt = 0;
    playerSpeed.textContent = '1×';
  }
  playerSpeed.hidden = !talks;
  paintProgress(shared.media.position || 0);
  paintLock();
}


export function playPath() {
  return shared.media && shared.media.isPlaying ? 'M7 5h3v14H7zm7 0h3v14h-3z' : 'M8 5v14l11-7z';
}


export let shownPosition = 0;

export function paintProgress(position) {
  shownPosition = position;
  const duration = (shared.media && shared.media.duration) || 0;
  const ratio = duration > 0 ? Math.min(1, position / duration) : 0;
  playerElapsed.style.width = (ratio * 100) + '%';
  playerPosition.textContent = clock(position);
}





function runMediaClock(from) {
  clearInterval(shared.mediaTicker);
  if (!shared.media || !shared.media.isPlaying) return;
  let position = from === undefined ? (shared.media.position || 0) : from;
  shared.mediaTicker = setInterval(() => {
    if (shared.isScrubbing) return;
    position += 500;
    
    
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
  
  
  
  paintQuickMedia();
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

export function openPlayer() {
  becomeExtended();
  paintMedia();
  showFace('player');
  setSize('player');
  const position = livePosition();
  paintProgress(position);
  runMediaClock(position);
  playEntrance();
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
  const path = playPath();
  document.getElementById('player-play-path').setAttribute('d', path);
  document.getElementById('lock-play-path').setAttribute('d', path);
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
