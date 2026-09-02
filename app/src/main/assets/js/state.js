
export const pill = document.getElementById('pill');
export const faces = {
  idle: document.getElementById('idle-face'),
  media: document.getElementById('media-face'),
  player: document.getElementById('player-face'),
  timer: document.getElementById('timer-face'),
  timerPanel: document.getElementById('timer-panel'),
  call: document.getElementById('call-face'),
  alarm: document.getElementById('alarm-face'),


  alert: document.getElementById('alert-face'),
  history: document.getElementById('history-face'),
  quick: document.getElementById('quick-face'),
};
export const bridge = window.Android || {
  ready() {}, setWindowSize() {}, setWindowBounds() {}, setBlurFrame() {}, triggerHaptic() {}, onSwipeDismiss() {},
  mediaControl() {}, mediaSeek() {}, readMicrophoneAccess() { return 'unavailable'; }, readTorchLit() { return false; }, setTorchLit() {},
  setMicrophoneAccess() {}, readHistory() { return '[]'; }, readUnreadCount() { return 0; },
  openNotification() {}, dismissNotification() {}, timerAction() {},
  setTorch() {}, setNowProxy() {}, setLockProxy() {}, setStatusProxy() {}, openConnectionSettings() {},
  recordingAction() {}, alarmAction() {},
  note() {}, wakeFrames() {},
};
// The host window tracks the bubble in both directions. It sits above the status
// bar, so anything it covers is a pixel the shade swipe cannot start on — at rest
// that must be the bubble and nothing more. Negative means "the user's own size".
export const SIZES = {
  idle:     { width: -1,  height: -1  },
  // The alert begins where the closed bubble does and grows downwards, so the
  // window only needs the height — a few pixels over the bubble's own, since the
  // bubble cannot be drawn outside the window holding it.
  alert:    { width: 300, height: 115 },
  image:    { width: 300, height: 159 },
  haptic:   { width: 340, height: 150 },
  picture:  { width: 340, height: 320 },
  player:   { width: 340, height: 190 },
  timer:    { width: 340, height: 132 },
  // Five steps in a row and their label above them; nothing else belongs in it.

  history:  { width: 340, height: 320 },
  // The whole screen less the margin the bubble is already standing in. An alarm is the one
  // state allowed to cost the shade swipe: while it rings there is nothing else to be doing.
  alarm:    { width: 356, height: 720 },
};
export const DWELL = 5000;
export const HOLD_MILLIS = 350;
export const HOLD_SCALE = 1.14;
// Kept in step with --mod-width in the CSS: the window has to be as wide as the
// bubble it clips, and no wider — every extra pixel is dead space over the shade.
export const MOD_WIDTH = 56;
/**
 * The gap a split leaves between the bubble and the satellite it let go of. Wide
 * enough that the skin reads them as two shapes standing apart: the melt is strong
 * at nothing and gone by about this far, so the neck of liquid between them thins
 * out exactly as the satellite finishes leaving.
 */
export const SAT_GAP = 8;
/**
 * The same gap while the Now bubble is out. The row has the right-hand half of the bar
 * to fit into and the pair reads as one group standing aside, so the circle sits in
 * closer — which is also what leaves the light the room it needs at the other end.
 */
export const SAT_GAP_ASIDE = 4;
/**
 * How much further right than the bare minimum the row stands while the light is out.
 * The minimum is the glyph clearing the punch hole and nothing more, and a row parked
 * exactly on that minimum leaves the light a hair short of the clock it has to cover.
 */
export const CHIP_ROOM = 12;

/**
 * How far the skin runs together, weakest and strongest. The weak end has to leave
 * a resting row as separate shapes — it bridges at roughly twice itself, so it must
 * stay well under SAT_GAP — and the strong end has to hold a neck across shapes
 * that are touching. See meltBy().
 */
export const MELT_MIN = 1.6;
/**
 * The deviation the skin melts at when two shapes are touching, and so — since shapes bridge
 * at roughly twice it — how far apart two bubbles can be and still neck. The default the
 * strength slider is a percentage of; `shared.goo` is what meltBy actually reads.
 */
export const MELT_MAX = 6;
/** How much the main bubble pulls in when it has let a satellite go. */
export const SPLIT_SHRINK = 24;
/** How far the finger travels to hand the bubble over to the satellite. */
export const SWAP_DISTANCE = 80;
/**
 * How hard the arriving glyph is knocked on, and what decides it. The knock is the
 * row's momentum carrying on through the thing that arrived, so it is the *finger*
 * that sets it: a flick throws the glyph, a slow drag barely disturbs it. A fixed
 * distance made every swap read the same however it was made, which is the one
 * thing a hand-driven gesture must not do.
 */
export const BUMP_MIN = 3;
export const BUMP_MAX = 14;
/** The finger speed, in pixels per millisecond, that earns the whole knock. */
export const SWAP_FLICK = 1.4;
/** A finger this still before it lifted has let the row go at rest, not thrown it. */
export const SWAP_STILL = 90;
// How long a press has to last before the window is allowed to grow under it.
export const HOLD_GRACE = 140;
/**
 * How far a resting finger may wander before it counts as a drag rather than a
 * hold. A finger pressing for a third of a second travels further than it feels
 * like it does, and this is one threshold for both answers: under it nothing moves
 * and the hold survives, over it the drag owns the touch. The swipe that dismisses
 * starts at 24, so this stays under it.
 */
export const HOLD_SLOP = 22;
document.documentElement.style.setProperty('--hold-scale', HOLD_SCALE);
export const PICTURE_MAX_HEIGHT = 460;
export const CLOSED = new Set(['idle', 'playing', 'timing', 'calling']);

/** The page's own root, where every custom property the CSS reads is written. */
export const root = document.documentElement;

/**
 * What is true right now, in one object rather than as eighteen module-scope values.
 *
 * These are the values several modules both read and write — the state the row, the mods,
 * the lock bubble and the gestures all answer to. An imported binding can be read from
 * anywhere and assigned only where it was declared, so a plain `export let` would have
 * meant a setter for each of them; one object costs a dot and keeps the writes where the
 * decision is made. Anything only one module writes belongs to that module instead.
 */
export const shared = {
  /**
   * The bubble has named states, because every gesture means something different in
   * each one and inferring that from whatever size happens to be showing does not
   * survive a second mod.
   *
   *   idle     the bubble as configured, nothing added
   *   alert    a notification has taken it over for its dwell
   *   active   it was tapped open
   *   haptic   it is being held down
   *
   * Closed mods are not a state but a set: things that are simply true for as long
   * as they are true — a song playing, a sensor live — and they stack, so a closed
   * bubble can carry the song and the microphone dot at once. An idle state with a
   * non-empty `mods` is the closed mod state.
   */
  state: 'idle',
  /**
   * Which mod owns the bubble when several are true at once. It is an order, not a
   * ranking, because a swipe rotates it: the timer leads by default — one of them is
   * running out — but the song can be pulled to the front by hand.
   */
  modOrder: ['timer', 'media', 'call'],
  size: 'idle',            // a key of SIZES
  current: null,           // the notification the alert face is showing
  // Mirrors what Kotlin last pushed through setCompactSize, so the hold knows how
  // much room the scaled bubble needs.
  compact: { width: 130, height: 34 },
  dwellTimer: null,
  holdTimer: null,
  growTimer: null,
  restoreTimer: null,
  mediaTicker: null,
  timerTicker: null,
  // While a finger is on the timeline, nothing else may repaint it.
  isScrubbing: false,
  scrubPosition: 0,
  // The song that is playing, or null. It owns the resting bubble while it exists.
  media: null,
  // The timer the clock app is running, or null. It outranks the song.
  timer: null,
  // The call that is connected, or null.
  call: null,
  hasHeld: false,
  pullReach: 0,
  /** The melt ceiling in force, which the settings panel scales. */
  goo: MELT_MAX,
  /** Whether the row stands aside for the Now bubble. The panel owns it; the row reads it. */
  nowPushes: true,
  /** How much wider than the bare bubble a mod makes it, from the settings panel. */
  modWidth: 56,
};

/** The mods that are true right now: 'media', and the sensor indicators later. */
export const mods = new Set();

/**
 * Start a clock hand partway through its sweep, so it reads as the hand that was already
 * running rather than a new one starting at twelve.
 *
 * A negative `animation-delay` is the whole mechanism: it begins an animation that far into
 * its cycle. The offset comes off the wall clock rather than any state of ours, which is what
 * keeps every hand in the interface in phase with every other — the bubble's glyph and the
 * satellite's circle are meant to be one picture handed between two places, and two
 * independently started twelve-second loops are not.
 *
 * Lives here because it is reached from both the row and a mod, and state.js is the module
 * that imports nothing: anything both sides call belongs where neither has to import the
 * other. Mirrors the 12s in sweep's two rules in pill.css; nothing joins them at build time.
 */
export const SWEEP_CYCLE = 12000;
export function setSweepPhase(element) {
  element.style.setProperty('--sweep-phase', -(Date.now() % SWEEP_CYCLE) + 'ms');
}
