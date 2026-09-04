import { lockArt, lockPill, paintLock, paintLockProgress } from '../lock.js';
import { becomeExtended, closedTarget, setSize, showFace, toClosed } from '../row.js';
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
    songSwapping = false;
    typeLabels();
    return;
  }
  // The open player's cover is ten times the area of the closed one, so the small ones are quicker
  // still: the same travel over a third of the distance reads as sluggish at that size.
  const covers = [[mediaArt, 110], [playerArt, PLAYER_ART_MS], [lockArt, 110]];

  // Beat one: the cover leaves and the lines are taken away with it, at the same time. A song
  // changing is one event, so what is leaving leaves together — the picture out to the left and
  // the two labels untyped behind their carets.
  songSwapping = true;
  for (const [element, milliseconds] of covers) {
    element.style.transitionDuration = milliseconds + 'ms';
    element.classList.add('leaving-art');
  }
  typeLabels('');

  const decoded = new Image();
  decoded.src = art;
  // decode() rather than onload: onload fires when the bytes are in, and the actual
  // decode then happens synchronously on the main thread at the first paint of the
  // new src — in the middle of the slide, which is exactly where the stutter was.
  // This does it off-thread and hands back a picture that is ready to be drawn.
  const ready = decoded.decode
    ? decoded.decode().catch(() => {})
    : new Promise(done => { decoded.onload = done; });
  // Beat two: the picture is decoded while the slot empties, and the two are waited on together —
  // whichever is slower is what the next beat starts after. Decoding under the slide is exactly
  // what the beat of nothing exists to move the work into.
  const emptied = new Promise(done => setTimeout(done, PLAYER_ART_MS + ART_HOLD));
  Promise.all([ready, emptied]).then(() => {
    // Still the art we were asked for? A fast skip can land two of these.
    if (shownArt !== art) return;
    // Beat three: the new cover comes in from the right.
    for (const [element, milliseconds] of covers) enterArt(element, art, milliseconds);
    // Beat four: the song is read only once its cover is standing there.
    setTimeout(() => {
      if (shownArt !== art) return;
      songSwapping = false;
      typeLabels();
    }, PLAYER_ART_MS);
  });
}

/** The player's cover is the slowest of the three, so it is the one the timeline is paced by. */
const PLAYER_ART_MS = 210;

/**
 * Whether a changeover is mid-flight, and so whether the labels belong to it rather than to
 * whatever repaint is asking. Read by the lock bubble too, which draws the same song.
 */
export let songSwapping = false;

/** Both bubbles' lines, typed to the song — or to nothing, which is the same run backwards. */
export function typeLabels(force) {
  const title = force === undefined ? ((shared.media && shared.media.title) || '') : force;
  const artist = force === undefined ? ((shared.media && shared.media.artist) || '') : force;
  typeInto(document.getElementById('player-title'), title);
  typeInto(document.getElementById('player-artist'), artist);
  typeInto(document.getElementById('lock-title'), title);
  typeInto(document.getElementById('lock-artist'), artist);
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

/**
 * The beat between the two covers. The old one leaving and the new one arriving used to be the
 * same instant, which reads as one picture being shoved sideways by another; held apart, the
 * slot is empty for long enough to say the song ended before the next one starts.
 */
const ART_HOLD = 300;

/** The arriving half: in from the right, the picture already decoded before it is put in. */
function enterArt(element, art, milliseconds) {
  element.style.transitionDuration = milliseconds + 'ms';
  element.src = art;
  element.classList.remove('hidden-art', 'leaving-art');
  // Placed at the far side with no transition, then let go of on the next frame: a value
  // written and cleared in one frame is never a transition, the browser only computes the
  // second one.
  element.classList.add('entering-art');
  requestAnimationFrame(() => requestAnimationFrame(() => {
    element.classList.remove('entering-art');
  }));
}

/**
 * The players whose "songs" are people talking. A voice note is the one kind of playback where
 * the rate is worth a control of its own, and the two messengers are where they arrive — a
 * speed button on an album would be an option nobody asked for on every song they play.
 */
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
  // Written only when it changes: a custom property on the root invalidates the computed style of
  // every element on the page, and a media session reports several times a second — that recalc
  // was landing in the middle of the open and the art slide, which is where the stutter was.
  const accent = shared.media.accent || 'var(--section-color)';
  if (accent !== shownAccent) {
    shownAccent = accent;
    document.documentElement.style.setProperty('--app-accent', accent);
  }
  pill.classList.toggle('sounding', Boolean(shared.media.isPlaying));

  paintArt(shared.media.artBase64 || '');
  // The lines belong to the changeover while one is running: typed here as well they would be
  // written into a bubble whose cover has not arrived yet, which is the whole thing this
  // sequence exists to put in order.
  if (!songSwapping) typeLabels();
  document.getElementById('player-duration').textContent = clock(shared.media.duration || 0);
  document.getElementById('player-play-path').setAttribute('d', playPath());
  // A session that has been swapped underneath us is a different thing playing, so the rate
  // goes back to normal with it rather than being inherited by whatever arrives next.
  const talks = TALKERS.includes(shared.media.app);
  if (!talks && speedAt !== 0) {
    speedAt = 0;
    playerSpeed.textContent = '1×';
  }
  playerSpeed.hidden = !talks;
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

/** When the position in the payload was true, so the page can say where the song is without asking. */
let positionAt = 0;

/**
 * Where the song is now, worked out here rather than asked for.
 *
 * It used to be `bridge.readMediaPosition()`, a synchronous call across the bridge that ends in a
 * binder round-trip to the player's own process — on the WebView's JavaScript thread, in the same
 * frame the tap starts the bubble opening. That is the stall on the first press: everything else
 * about the open is cheap, and this one line waited on another app. The payload's own position is
 * a fixed point in time and the page already counts the seconds off it; the host corrects a drift
 * of more than two seconds on its own, which is what a seek from somewhere else is.
 */
export function livePosition() {
  if (!shared.media) return 0;
  const since = shared.media.isPlaying ? performance.now() - positionAt : 0;
  return Math.round((shared.media.position || 0) + since);
}

window.onMediaUpdate = payload => {
  positionAt = performance.now();
  // The cover is only in the payload when it has changed — it is a few hundred kilobytes and
  // parsing it again on every playback tick was most of what made the player stutter. Absent, the
  // picture is still whatever the page already has.
  if (payload && !('artBase64' in payload) && shared.media) {
    payload.artBase64 = shared.media.artBase64;
  }
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
  becomeExtended();
  paintMedia();
  showFace('player');
  setSize('player');
  const position = livePosition();
  paintProgress(position);
  runMediaClock(position);
  playEntrance();
}

/**
 * The cover coming in and the song being typed, replayed at the moment the player is opened.
 *
 * Both belong to paintMedia(), which runs on every payload — so they had already played, against
 * a face nobody could see, and opening the player showed the finished result. That is why the
 * typewriter was "rarely visible": it only ever ran when a song changed while the player already
 * stood open. The entrance belongs to the face becoming visible, not to the song changing.
 */
function playEntrance() {
  // The same order the changeover runs in: the cover arrives, and the song is read once it is
  // standing there.
  playerArt.style.transitionDuration = PLAYER_ART_MS + 'ms';
  playerArt.classList.remove('hidden-art', 'leaving-art');
  playerArt.classList.add('entering-art');
  requestAnimationFrame(() => requestAnimationFrame(() => {
    playerArt.classList.remove('entering-art');
  }));
  // Emptied first, or typeInto finds the lines already correct and does nothing.
  document.getElementById('player-title').textContent = '';
  document.getElementById('player-artist').textContent = '';
  songSwapping = true;
  setTimeout(() => {
    songSwapping = false;
    typeLabels();
  }, PLAYER_ART_MS);
}

/**
 * The play glyph turning over on the spot, before the session has said anything.
 *
 * A media session answers a transport command in its own time — tens of milliseconds for a local
 * player, longer for one that has to reach a server — and until it does, the only thing on
 * screen is the glyph the user has just pressed still showing the old state. That reads as the
 * tap having been missed, so it gets pressed again. What is drawn here is what the button did;
 * the next payload is the correction, and if the player refuses the command it puts the glyph
 * straight back.
 */
export function flipPlaying() {
  if (!shared.media) return;
  shared.media.isPlaying = !shared.media.isPlaying;
  pill.classList.toggle('sounding', Boolean(shared.media.isPlaying));
  const path = playPath();
  document.getElementById('player-play-path').setAttribute('d', path);
  document.getElementById('lock-play-path').setAttribute('d', path);
}

/**
 * A label changing over as a typewriter: the old line is cleared and the new one is typed in
 * behind a caret that blinks while it types and goes out when the line is finished.
 *
 * The DNA's numbers, and its reason for them: 0.06s a character typing, 0.03s deleting, because
 * a line being taken away should always read faster than one being written. The caret is a
 * pseudo-element on the label rather than a character in it, or the text would reflow by a
 * character's width every time it blinked.
 *
 * Only on a real change. Every payload from a media session repaints these, several a second
 * while a song plays, and a typewriter that ran on every repaint would be a label that never
 * settles.
 */
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
  // Backwards through whatever is standing there, then forwards through the new line, on a
  // timeout that reschedules itself rather than an interval: the two directions run at
  // different speeds, and an interval's period is fixed at the moment it is created. One run
  // rather than two chained, so a song changed mid-sentence simply retargets the line already
  // being typed.
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
