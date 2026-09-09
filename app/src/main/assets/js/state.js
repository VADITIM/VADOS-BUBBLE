
export const pill = document.getElementById('pill');
export const faces = {
  idle: document.getElementById('idle-face'),
  media: document.getElementById('media-face'),
  player: document.getElementById('player-face'),
  timer: document.getElementById('timer-face'),
  timerPanel: document.getElementById('timer-panel'),
  call: document.getElementById('call-face'),


  alert: document.getElementById('alert-face'),
  history: document.getElementById('history-face'),
};
export const bridge = window.Android || {
  ready() {}, setWindowSize() {}, setWindowBounds() {}, setBlurFrame() {}, triggerHaptic() {}, onSwipeDismiss() {},
  mediaControl() {}, mediaSeek() {}, readMicrophoneAccess() { return 'unavailable'; }, readTorchLit() { return false; }, setTorchLit() {},
  setMicrophoneAccess() {}, readHistory() { return '[]'; }, readUnreadCount() { return 0; },
  openNotification() {}, dismissNotification() {}, timerAction() {},
  setTorch() {}, setLockProxy() {}, setStatusProxy() {}, setClockProxy() {},
  openConnectionSettings() {}, openClock() {}, requestToggles() {}, setToggle() {}, setLevel() {},
  recordingAction() {}, setNotesProxy() {}, mediaSpeed() {},
  note() {}, wakeFrames() {},
};






export const GROWN_PAD = 26;

export const SIZES = {
  idle:     { width: -1,  height: -1  },
  
  
  
  alert:    { width: 300, height: 162 },
  image:    { width: 300, height: 197 },
  haptic:   { width: 340, height: 150 },
  picture:  { width: 340, height: 320 },
  player:   { width: 340, height: 190 },
  timer:    { width: 340, height: 132 },
  

  history:  { width: 340, height: 320 },
};


export const DWELL = 5000;
export const HOLD_MILLIS = 350;
export const HOLD_SCALE = 1.14;


export const MOD_WIDTH = 56;






export const SAT_GAP = 8;





export const SAT_GAP_ASIDE = 4;





export const CHIP_ROOM = 12;







export const MELT_MIN = 1.6;





export const MELT_MAX = 6;

export const SPLIT_SHRINK = 24;

export const SWAP_DISTANCE = 80;







export const BUMP_MIN = 3;
export const BUMP_MAX = 14;

export const SWAP_FLICK = 1.4;

export const SWAP_STILL = 90;

export const HOLD_GRACE = 140;
document.documentElement.style.setProperty('--hold-scale', HOLD_SCALE);
export const PICTURE_MAX_HEIGHT = 460;
export const CLOSED = new Set(['idle', 'playing', 'timing', 'calling']);


export const root = document.documentElement;










export const shared = {
  














  state: 'idle',
  
  isTouchDown: false,
  




  modOrder: ['timer', 'media', 'call'],
  size: 'idle',            
  current: null,           
  
  
  compact: { width: 130, height: 34 },
  dwellTimer: null,
  holdTimer: null,
  growTimer: null,
  restoreTimer: null,
  mediaTicker: null,
  timerTicker: null,
  
  isScrubbing: false,
  scrubPosition: 0,
  
  media: null,
  
  timer: null,
  
  call: null,
  hasHeld: false,
  
  goo: MELT_MAX,
  
  
  dwell: DWELL,
  
  edgeMerge: true,
  
  nowPushes: true,
  
  modWidth: 56,
};


export const mods = new Set();















export const SWEEP_CYCLE = 12000;
export function setSweepPhase(element) {
  element.style.setProperty('--sweep-phase', -(Date.now() % SWEEP_CYCLE) + 'ms');
}
