import { lockArt, lockPill, paintLock, paintLockProgress } from '../lock.js';
import { closedTarget, setSize, showFace, toClosed } from '../row.js';
import { bridge, mods, pill, shared } from '../state.js';

const playerElapsed = document.getElementById('player-elapsed');
const playerPosition = document.getElementById('player-position');

/** Wide enough for the art and the indicator either side of the closed bubble. */
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

/**
 * The cover changes over rather than being replaced. Assigning a new src paints
 * the browser's own broken-image glyph for a frame or two while the data URI is
 * decoded — that is the "img" box — and a player that has not published its art
 * yet hands over its app icon, which then flicks to the real cover a moment later.
 * So the new art is decoded off-screen first, and only a picture that is actually
 * ready gets to slide in: out to the left with a fade, in from the left with one.
 */
function paintArt(art) {
  if (art === shownArt) return;
  shownArt = art;
  if (!art) {
    mediaArt.classList.add('hidden-art');
    playerArt.classList.add('hidden-art');
    lockArt.classList.add('hidden-art');
    return;
  }
  const decoded = new Image();
  decoded.src = art;
  // decode() rather than onload: onload fires when the bytes are in, and the actual
  // decode then happens synchronously on the main thread at the first paint of the
  // new src — in the middle of the slide, which is exactly where the stutter was.
  // This does it off-thread and hands back a picture that is ready to be drawn.
  const ready = decoded.decode
    ? decoded.decode().catch(() => {})
    : new Promise(done => { decoded.onload = done; });
  ready.then(() => {
    // Still the art we were asked for? A fast skip can land two of these.
    if (shownArt !== art) return;
    // The open player's cover is ten times the area of the closed one, and the
    // same 220ms that reads as a change over there reads as a flicker here.
    swapArt(mediaArt, art, 220);
    swapArt(playerArt, art, 420);
    swapArt(lockArt, art, 220);
  });
}

/**
 * The same picture, moved into the bubble's own copy without a slide.
 *
 * A swap hands the bubble to whatever the satellite was carrying, and for media
 * that picture is already on screen — in the circle. It has to be in the bubble's
 * own <img> before that face is shown, because the two are different elements and
 * only one of them is drawing at a time. It was not: `shownArt` records what the
 * page last *asked* for, and a satellite's art is set straight onto its own element
 * without going through here, so the bubble's copy could be stale or still hidden
 * while shownArt claimed it was current. paintArt() then skipped, the face was
 * shown, and the picture had to be fetched and painted after the fact — which is
 * the flicker that read as the image re-rendering at the moment of the swap.
 *
 * No slide, deliberately: a swap is the same song arriving in a different box, and
 * a picture that slides out and back would say the song had changed.
 */
export function carryArt() {
  const art = (shared.media && shared.media.artBase64) || '';
  shownArt = art;
  if (!art) return;
  for (const element of [mediaArt, playerArt]) {
    element.classList.remove('leaving-art', 'entering-art', 'hidden-art');
    if (element.getAttribute('src') !== art) element.src = art;
  }
}

/** Out to the left, back in from the left, at whatever pace suits the size. */
function swapArt(element, art, milliseconds) {
  element.style.transitionDuration = milliseconds + 'ms';
  element.classList.add('leaving-art');
  setTimeout(() => {
    element.src = art;
    element.classList.remove('hidden-art', 'leaving-art');
    element.classList.add('entering-art');
    requestAnimationFrame(() => element.classList.remove('entering-art'));
  }, milliseconds);
}

export function paintMedia() {
  if (!shared.media || shared.isScrubbing) return;
  document.documentElement.style.setProperty(
    '--app-accent', shared.media.accent || 'var(--section-color)'
  );
  pill.classList.toggle('sounding', Boolean(shared.media.isPlaying));

  paintArt(shared.media.artBase64 || '');
  document.getElementById('player-title').textContent = shared.media.title || '';
  document.getElementById('player-artist').textContent = shared.media.artist || '';
  document.getElementById('player-duration').textContent = clock(shared.media.duration || 0);
  document.getElementById('player-play-path').setAttribute('d', playPath());
  paintProgress(shared.media.position || 0);
  paintLock();
}

/** The one shape two bubbles draw for the same fact, so it is written once. */
export function playPath() {
  return shared.media && shared.media.isPlaying ? 'M7 5h3v14H7zm7 0h3v14h-3z' : 'M8 5v14l11-7z';
}

// What the bar is currently showing, which is where a relative drag starts from.
export let shownPosition = 0;

export function paintProgress(position) {
  shownPosition = position;
  const duration = (shared.media && shared.media.duration) || 0;
  const ratio = duration > 0 ? Math.min(1, position / duration) : 0;
  playerElapsed.style.width = (ratio * 100) + '%';
  playerPosition.textContent = clock(position);
}

/**
 * The session only reports a position when something changes, so the seconds in
 * between are counted here rather than asked for sixty times a minute.
 */
function runMediaClock(from) {
  clearInterval(shared.mediaTicker);
  if (!shared.media || !shared.media.isPlaying) return;
  let position = from === undefined ? (shared.media.position || 0) : from;
  shared.mediaTicker = setInterval(() => {
    if (shared.isScrubbing) return;
    position += 500;
    // Two bubbles can be showing this song at once now — the open player up at the
    // cutout and the lock screen's own — and the second was counting on nothing.
    if (shared.size === 'player') paintProgress(position);
    if (lockPill.classList.contains('showing')) paintLockProgress(position);
  }, 500);
}

window.onMediaUpdate = payload => {
  shared.media = payload;
  if (!shared.media) {
    mods.delete('media');
    clearInterval(shared.mediaTicker);
    pill.classList.remove('sounding');
    if (shared.state === 'idle') toClosed();
    // The song ending is the lock bubble's business too, and it is the only path that
    // does not go through paintMedia — so without this the bubble at the bottom stands
    // there holding a song that has stopped.
    paintLock();
    return;
  }
  mods.add('media');
  paintMedia();
  runMediaClock();
  runBars();
  // A song that starts while an alert is showing waits its turn; the alert
  // returns to it on its own.
  if (shared.state === 'idle') toClosed();
};

/**
 * The three bars, one swing at a time. Music is not periodic, so an indicator that
 * is says "animation" rather than "sound" — every swing gets its own reach and its
 * own pace.
 *
 * Driven from script rather than by a CSS animation over custom properties: those
 * keyframes re-read the properties on every frame, so a fresh pair of heights
 * landed in the middle of a swing and the bar jumped to it. Here a swing is an
 * animation of its own, from the height the last one ended on to a new top and
 * down to a new bottom, and the next one is only rolled when it has finished.
 */
const BARS = '#equalizer span, .sat-bars span';

/** Bumped to stop the bars: every swing checks it before scheduling the next. */
let barRun = 0;

function pulse(bar, from, run) {
  const high = 62 + Math.random() * 38;
  // A band that drops out for one swing is what a real meter does; it stays in the
  // cycle, so it is back on the next one.
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

/**
 * Restarted rather than resumed: the satellite's bars are written into the page
 * long after the bubble's, so every caller that can have changed which bars exist
 * calls this and the whole set is put back in motion.
 */
export function runBars() {
  barRun += 1;
  const run = barRun;
  // Silence is a state of its own and it should look like one: the bars stop where
  // the sound did otherwise, at whatever height the last swing happened to reach.
  // Pulled down to their own width, so three bars become three dots.
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
  shared.state = 'active';
  paintMedia();
  showFace('player');
  setSize('player');
  // Asked for rather than remembered: the payload's position is from whenever the
  // session last reported one, which can be minutes ago, and the timeline drew that
  // stale value until the next tick corrected it — right as the bubble finished
  // opening, which is what made it look like the animation was doing it.
  const now = Number(bridge.readMediaPosition());
  const position = Number.isFinite(now) && now > 0 ? now : (shared.media.position || 0);
  paintProgress(position);
  runMediaClock(position);
}
