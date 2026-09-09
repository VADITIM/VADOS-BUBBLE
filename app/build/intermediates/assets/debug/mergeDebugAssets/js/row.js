import { catchInto, paintLiquidFrame, releaseCatch, stirLiquid, traceEvent } from './liquid.js';
import { isStolen } from './lock.js';
import { paintAvatar, paintCall } from './mods/call.js';
import { carryArt, mediaWindow, paintMedia, runBars } from './mods/media.js';
import { paintTimer, timerWindow } from './mods/timer.js';
import { endHold } from './motion.js';
import { closeNowPanel, nowOpen } from './now.js';
import { closeStatusPanel, statusOpen } from './status.js';
import { BUMP_MAX, BUMP_MIN, CHIP_ROOM, CLOSED, GROWN_PAD, SAT_GAP, SAT_GAP_ASIDE, SIZES, SPLIT_SHRINK, SWAP_DISTANCE, SWAP_FLICK, SWAP_STILL, bridge, faces, mods, pill, root, setSweepPhase, shared } from './state.js';






const MORPH_MS = 420;

const MORPH_EASE = 'cubic-bezier(0.22, 1.12, 0.36, 1)';

const MORPH_STEPS = 14;


function measureMorphs(face) {
  const places = new Map();
  face.querySelectorAll('[data-morph]').forEach(element => {
    const box = element.getBoundingClientRect();
    if (box.width) places.set(element.dataset.morph, box);
  });
  return places;
}
















function morphInto(face, before) {
  if (!before || !before.size) return;
  const hole = pill.getBoundingClientRect();
  const holeX = hole.left + hole.width / 2;
  const holeY = hole.top + hole.height / 2;
  let crossed = false;
  face.querySelectorAll('[data-morph]').forEach(element => {
    const from = before.get(element.dataset.morph);
    const to = element.getBoundingClientRect();
    if (!from || !to.width) return;
    const startX = from.left + from.width / 2;
    const startY = from.top + from.height / 2;
    const endX = to.left + to.width / 2;
    const endY = to.top + to.height / 2;
    const scale = element.hasAttribute('data-morph-scale') ? from.width / to.width : 1;
    const frames = [];
    for (let step = 0; step <= MORPH_STEPS; step += 1) {
      const at = step / MORPH_STEPS;
      const rest = 1 - at;
      const x = rest * rest * startX + 2 * rest * at * holeX + at * at * endX;
      const y = rest * rest * startY + 2 * rest * at * holeY + at * at * endY;
      const size = 1 + (scale - 1) * rest;
      frames.push({
        transform: 'translate(' + (x - endX).toFixed(1) + 'px,' + (y - endY).toFixed(1) + 'px)' +
          ' scale(' + size.toFixed(3) + ')',
      });
    }
    element.animate(frames, { duration: MORPH_MS, easing: MORPH_EASE, composite: 'add' });
    crossed = true;
  });
  if (crossed) stirLiquid(MORPH_MS + 120);
}

export function showFace(name) {
  const arriving = faces[name];
  const leaving = Object.values(faces).find(element => element.classList.contains('showing'));
  const before = arriving && leaving && leaving !== arriving ? measureMorphs(leaving) : null;
  for (const [key, element] of Object.entries(faces)) {
    element.classList.toggle('showing', key === name);
  }
  if (arriving) morphInto(arriving, before);
}






function afterRelayout(work) {
  
  
  
  
  
  requestAnimationFrame(work);
}

function paintSize() {
  pill.classList.toggle('alerting', shared.size === 'alert' || shared.size === 'image');
  pill.classList.toggle('expanded', shared.size === 'haptic');
  pill.classList.toggle('picture', shared.size === 'picture');
  pill.classList.toggle('history', shared.size === 'history');
  pill.classList.toggle('playing', shared.size === 'playing');
  pill.classList.toggle('player', shared.size === 'player');
  pill.classList.toggle('timing', shared.size === 'timing');
  pill.classList.toggle('calling', shared.size === 'calling');

  pill.classList.toggle('timer', shared.size === 'timer');

  
  
  
  
  root.classList.toggle('grown', !CLOSED.has(shared.size));
  if (!CLOSED.has(shared.size)) {
    
    
    root.style.setProperty('--width-ease', 'var(--ease-grow)');
    root.style.setProperty('--width-ms', 'var(--grow-ms)');
    root.style.setProperty('--width-delay', '0ms');
    root.style.setProperty('--face-delay', '0ms');
    root.style.setProperty('--face-fade', '0ms');
  }
  stirLiquid();
  paintShift();
}







export function paintShift() {
  root.style.setProperty(
    '--shift', (CLOSED.has(shared.size) ? layout().mainX : 0) + 'px'
  );
  stirLiquid();
}



export const satellites = {
  right: document.getElementById('satellite-right'),
  left: document.getElementById('satellite-left'),
};
const dotGroups = {
  right: document.getElementById('dots-right'),
  left: document.getElementById('dots-left'),
};











const DOT_SIZE = 6;
const DOT_GAP = 5;


function dotted() {
  const spread = { right: [], left: [] };
  
  
  
  
  const limit = satelliteLimit();
  liveMods().slice(1 + limit).forEach((mod, index) => {
    spread[limit > 1 && index % 2 !== 0 ? otherSide(satSide) : satSide].push(mod);
  });
  return spread;
}


function dotRoom(count) {
  return count ? DOT_GAP + count * DOT_SIZE + (count - 1) * DOT_GAP : 0;
}


const SATELLITE_FACES = {
  
  
  media: '<img class="sat-art glyph" alt="">',
  timer: '<div class="sat-clock"></div>',
  call: '<div class="sat-avatar avatar"></div>',

};


let satSide = 'right';


let swap = 0;


let swapDir = 1;


let settling = false;






const FAR_PUSH = 17;










const SWAP_PRESS = 5;


let swapPassed = false;


let swapSpeed = 0;
let swapTravelled = 0;
let swapMovedAt = 0;


export function isLive(mod) {
  if (mod === 'timer') return mods.has('timer') && Boolean(shared.timer);
  if (mod === 'media') return mods.has('media') && Boolean(shared.media);
  if (mod === 'call') return mods.has('call') && Boolean(shared.call);

  return false;
}


function payloadOf(mod) {
  if (mod === 'media') return shared.media;
  if (mod === 'timer') return shared.timer;

  return shared.call;
}


export function liveMods() {
  return shared.modOrder.filter(mod => isLive(mod) && !isStolen(mod));
}


function accentOf(mod) {
  const payload = payloadOf(mod);
  return (payload && payload.accent) || 'var(--section-color)';
}


function satSize() {
  return shared.compact.height;
}








function glyphAligned(box) {
  return box.mainX - box.main / 2 + satSize() / 2;
}









let chipOut = false;


const SHIFT_HOME = 100;

let shiftingHome = false;
let shiftTimer = null;

window.onNowStanding = out => {
  if (chipOut === out) return;
  chipOut = out;
  
  
  
  
  
  shiftingHome = true;
  clearTimeout(shiftTimer);
  shiftTimer = setTimeout(() => { shiftingHome = false; }, SHIFT_HOME + 40);
  
  
  
  
  if (shared.state === 'idle') toClosed();
  else {
    paintSatellites();
    ensureClosedWindow();
  }
  paintShift();
};












const HOLE_WIDTH = 34;

function rowShift(resting) {
  
  
  if (!chipOut || !liveMods().length || !shared.nowPushes) return 0;
  
  
  const glyph = shared.compact.height - 10;
  const inset = (shared.compact.height - glyph) / 2 - 1;
  
  
  const clear = window.innerWidth / 2 - HOLE_WIDTH / 2 - glyph - inset + CHIP_ROOM;
  return Math.max(0, clear - (window.innerWidth / 2 - resting / 2));
}


export function rowLeftEdge() {
  const resting = restingMain();
  return window.innerWidth / 2 - resting / 2 + rowShift(resting);
}






function satelliteLimit() {
  
  
  
  return chipOut && shared.nowPushes ? 1 : 2;
}


function satGap() {
  return chipOut ? SAT_GAP_ASIDE : SAT_GAP;
}

function restingMain() {
  
  
  
  if (mergeHold) return shared.compact.width;
  const live = liveMods();
  if (!live.length) return shared.compact.width;
  return shared.compact.width + shared.modWidth + 4 - (live.length > 1 ? SPLIT_SHRINK : 0);
}










function layout() {
  const live = liveMods();
  const resting = restingMain();
  const push = rowShift(resting);
  if (live.length < 2) {
    return {
      main: resting, sat: satSize(), mainX: push, satX: push,
      inSide: satSide, farX: push, farFade: 1, edge: 0, reach: 0, push,
    };
  }
  const traded = (resting - satSize()) * swap;
  
  
  
  
  
  
  const inSide = live.length > 2 && satelliteLimit() > 1 && swapDir < 0 &&
    root.classList.contains('dragging')
    ? otherSide(satSide)
    : satSide;
  const edge = inSide === 'right' ? 1 : -1;
  
  
  const reach = (resting + satSize()) / 2 + satGap();
  
  
  
  
  
  const pressed = reach - (satGap() + SWAP_PRESS) * Math.sin(swap * Math.PI);
  
  const crossed = swap > 0.5;
  const shoved = crossed ? (1 - swap) * 2 : swap * 2;
  return {
    main: resting - traded,
    sat: satSize() + traded,
    mainX: push - edge * swap * pressed,
    satX: push + edge * (1 - swap) * pressed,
    inSide,
    push,
    
    
    
    
    
    farX: push + (crossed ? edge : -edge) * (reach + FAR_PUSH * shoved),
    farFade: Math.min(1, Math.abs(swap - 0.5) * 2),
    edge,
    reach,
  };
}


function otherSide(side) {
  return side === 'right' ? 'left' : 'right';
}







export function closedTarget() {
  const box = layout();
  const live = liveMods();
  const spread = dotted();
  
  
  
  const counted = Math.max(dotRoom(spread.right.length), dotRoom(spread.left.length));
  const room = live.length > 1 ? 2 * (satSize() + satGap() + counted) : 0;
  return {
    width: restingMain() + room,
    height: -1,
    
    
    
    shift: box.push,
    
    main: box.main,
  };
}






export function paintSatellites() {
  
  
  
  if (chipOut && satSide === 'left') satSide = 'right';
  layoutSatellites();
  stirLiquid();
  
  
  
  if (root.classList.contains('dragging')) paintLiquidFrame();
}

function layoutSatellites() {
  const live = liveMods();
  const box = layout();
  stageSplit(live.length);
  
  
  
  
  
  if (!settling) {
    root.style.setProperty('--main-width', box.main + 'px');
    root.style.setProperty('--shift', box.mainX + 'px');
    
    
    
    
    
    
    
    root.style.setProperty(
      '--shift-delay',
      root.classList.contains('dragging') || shiftingHome ? '0ms' : '120ms'
    );
    root.style.setProperty(
      '--shift-ms', shiftingHome ? SHIFT_HOME + 'ms' : 'var(--grow-ms)'
    );
    
    
    
    
    
    
    
    
    Object.values(satellites).forEach(element => {
      if (element.dataset.leaving) return;
      element.style.setProperty('--split-ms', shiftingHome ? SHIFT_HOME + 'ms' : '');
      element.style.setProperty('--split-delay', shiftingHome ? '0ms' : '');
    });
    
    
    stirLiquid();
  }
  paintDots(box);

  
  
  
  
  
  if (live.length < 2) {
    clearSatellite('right');
    clearSatellite('left');
    return;
  }
  
  
  
  
  if (live.length === 2 || satelliteLimit() === 1) {
    clearSatellite(otherSide(satSide));
    paintTrader(satellites[satSide], live[1], box);
    return;
  }
  const inSide = box.inSide;
  paintTrader(satellites[inSide], live[inSide === satSide ? 1 : 2], box);
  paintWrapper(satellites[otherSide(inSide)], live[inSide === satSide ? 2 : 1], box);
}















const MERGE_TRAVEL = 260;






const MERGE_EXPAND = 300;







const MOD_ENTER = 300;












const LAND = 0.38;







let mergeHold = false;
let mergeTimer = null;


let takingIn = false;


let lastLive = 0;








function stageSplit(count) {
  const changed = count !== lastLive;
  const leaving = count < lastLive;
  lastLive = count;
  
  
  const arriving = takingIn;
  takingIn = false;
  const moving = changed || arriving;
  root.style.setProperty('--width-ease', moving ? 'var(--ease-split)' : 'var(--ease-grow)');
  root.style.setProperty(
    '--width-ms',
    arriving ? MERGE_EXPAND + 'ms' : leaving ? '240ms' : changed ? '380ms' : 'var(--grow-ms)'
  );
  
  
  root.style.setProperty('--width-delay', '0ms');
  
  
  root.style.setProperty('--face-fade', arriving ? '260ms' : leaving ? '140ms' : '0ms');
}












function clearSatellite(side) {
  const element = satellites[side];
  if (!element.dataset.mod || element.dataset.leaving) return;
  
  
  
  
  
  if (settling) {
    dropSatellite(element);
    return;
  }
  element.dataset.leaving = '1';
  traceEvent(
    'satellite ' + side + ' sent home, mods=' + liveMods().length +
    ', limit=' + satelliteLimit() + ', mergeHold=' + mergeHold
  );
  
  
  element.classList.remove('live');
  element.style.setProperty('--split-ms', MERGE_TRAVEL + 'ms');
  element.style.setProperty('--split-delay', '0ms');
  aimHome(element);
  stirLiquid(MERGE_TRAVEL + MERGE_EXPAND + 200);
  
  
  
  
  catchInto(element, pill, { swallowed: retireLeaving });
}


function aimHome(element) {
  const target = glyphAligned(layout()) + 'px';
  element.style.setProperty('--sat-x', target);
  element.style.setProperty('--sat-birth', target);
}






function trackExpansion() {
  for (const side of ['right', 'left']) {
    const element = satellites[side];
    if (!element.dataset.leaving) continue;
    element.style.setProperty('--split-ms', MERGE_EXPAND + 'ms');
    aimHome(element);
  }
}


function dropSatellite(element) {
  releaseCatch(element);
  element.classList.remove('showing', 'live');
  delete element.dataset.leaving;
  element.removeAttribute('style');
  delete element.dataset.mod;
  element.innerHTML = '';
}


function retireLeaving() {
  root.classList.remove('handing-over');
  for (const side of ['right', 'left']) {
    const element = satellites[side];
    if (!element.dataset.leaving) continue;
    dropSatellite(element);
  }
  stirLiquid(300);
}


function dressSatellite(element, mod) {
  
  
  
  releaseCatch(element);
  delete element.dataset.leaving;
  const fresh = element.dataset.mod !== mod;
  if (fresh) {
    element.dataset.mod = mod;
    element.innerHTML = SATELLITE_FACES[mod];
    
    runBars();
    
    
    
    if (mod === 'timer') setSweepPhase(element);
  }
  
  
  if (mod === 'call') {
    const avatar = element.querySelector('.sat-avatar');
    if (avatar) paintAvatar(avatar);
  }
  if (mod === 'media') {
    const art = element.querySelector('.sat-art');
    if (art && shared.media) art.src = shared.media.artBase64 || '';
  }
  
  
  element.style.setProperty('--app-accent', accentOf(mod));
  
  
  element.classList.toggle(
    'live',
    mod === 'media' ? Boolean(shared.media.isPlaying)
      : mod === 'timer' ? Boolean(shared.timer.endsAt)
      : true
  );

  return fresh;
}





function paintTrader(element, mod, box) {
  const fresh = dressSatellite(element, mod);
  element.style.opacity = '';
  element.style.setProperty('--sat-width', box.sat + 'px');
  element.style.setProperty('--sat-x', box.satX + 'px');
  element.style.setProperty('--sat-birth', glyphAligned(box) + 'px');
  reveal(element, fresh);
}





function paintWrapper(element, mod, box) {
  const fresh = dressSatellite(element, mod);
  element.style.opacity = root.classList.contains('dragging') ? String(box.farFade) : '';
  element.style.setProperty('--sat-width', satSize() + 'px');
  element.style.setProperty('--sat-x', box.farX + 'px');
  element.style.setProperty(
    '--sat-birth', (box.mainX - (box.edge * box.main) / 2) + 'px'
  );
  reveal(element, fresh);
}







function reveal(element, fresh) {
  if (fresh) void element.offsetWidth;
  element.classList.add('showing');
}








const dotsSeen = { right: 0, left: 0 };

function paintDots(box) {
  const spread = dotted();
  for (const side of ['right', 'left']) {
    const group = dotGroups[side];
    const mods = spread[side];
    group.textContent = '';
    if (!mods.length) {
      group.classList.remove('showing');
      dotsSeen[side] = 0;
      continue;
    }
    for (const mod of mods) {
      const dot = document.createElement('span');
      dot.style.background = accentOf(mod);
      
      
      if (mods.length > dotsSeen[side]) dot.classList.add('arriving');
      group.appendChild(dot);
    }
    dotsSeen[side] = mods.length;
    const edge = side === 'right' ? 1 : -1;
    const width = mods.length * DOT_SIZE + (mods.length - 1) * DOT_GAP;
    const start = box.reach + satSize() / 2 + DOT_GAP;
    group.style.setProperty(
      '--dots-x', (box.push + edge * (start + width / 2)) + 'px'
    );
    group.classList.add('showing');
  }
}







export function dragSwap(travelled) {
  const live = liveMods();
  if (live.length < 2) return false;
  
  
  
  
  
  const now = performance.now();
  if (!root.classList.contains('dragging')) {
    swapSpeed = 0;
    swapMovedAt = 0;
  }
  if (swapMovedAt && now > swapMovedAt) {
    const rate = Math.abs(travelled - swapTravelled) / (now - swapMovedAt);
    swapSpeed = swapMovedAt ? swapSpeed * 0.6 + rate * 0.4 : rate;
  }
  swapMovedAt = now;
  swapTravelled = travelled;
  
  
  
  const towards = satSide === 'right' ? -travelled : travelled;
  let progress = swapDir > 0 ? towards : -towards;
  if (progress < 0 && live.length > 2) {
    swapDir = -swapDir;
    progress = -progress;
  }
  swap = Math.min(1, Math.max(0, progress / SWAP_DISTANCE));
  
  
  
  
  
  const past = swap > 0.5;
  if (past !== swapPassed) {
    swapPassed = past;
    bridge.triggerHaptic('tap');
    
    
    
    
    
    if (past) endHold();
  }
  root.classList.add('dragging');
  paintSatellites();
  return swap > 0.04;
}

















export function abandonSwap() {
  if (!root.classList.contains('dragging')) return;
  root.classList.remove('dragging');
  swap = 0;
  swapDir = 1;
  swapPassed = false;
  paintSatellites();
}

export function releaseSwap() {
  if (!root.classList.contains('dragging')) return;
  const committed = swap > 0.5;
  swapPassed = false;
  
  
  if (performance.now() - swapMovedAt > SWAP_STILL) swapSpeed = 0;
  if (!committed) {
    root.classList.remove('dragging');
    swap = 0;
    swapDir = 1;
    paintSatellites();
    return;
  }

  const box = layout();
  const flanked = liveMods().length > 2;
  const dir = swapDir;
  
  
  
  enterFrom(flanked ? box.inSide : satSide);
  swap = 0;
  swapDir = 1;
  
  
  
  
  
  
  
  
  settling = true;
  if (flanked) commitRotation(box, dir);
  else commitTrade(box);

  
  
  requestAnimationFrame(() => {
    root.classList.remove('dragging');
    
    
    
    
    
    
    
    
    settling = false;
    paintSatellites();
  });
}








function rotate(dir) {
  const live = liveMods();
  if (live.length < 2) return;
  const spun = dir < 0
    ? [live[live.length - 1], ...live.slice(0, -1)]
    : [...live.slice(1), live[0]];
  shared.modOrder = [...spun, ...shared.modOrder.filter((mod) => !live.includes(mod))];
}










function commitTrade(box) {
  rotate(1);
  satSide = otherSide(satSide);

  
  
  carryArt();
  
  toClosed();

  root.style.setProperty('--main-width', box.sat + 'px');
  root.style.setProperty('--shift', box.satX + 'px');
  const arriving = satellites[satSide];
  arriving.style.setProperty('--sat-x', box.mainX + 'px');
  arriving.style.setProperty('--sat-width', box.main + 'px');

  
  
  
  
  
  stirLiquid();
}







function commitRotation(box, dir) {
  rotate(dir);

  carryArt();
  toClosed();

  root.style.setProperty('--main-width', box.sat + 'px');
  root.style.setProperty('--shift', box.satX + 'px');
  
  
  
  const stepped = satellites[otherSide(box.inSide)];
  stepped.style.setProperty('--sat-x', box.mainX + 'px');
  stepped.style.setProperty('--sat-width', box.main + 'px');
  
  satellites[box.inSide].style.setProperty('--sat-x', (box.edge * box.reach) + 'px');

  
  stirLiquid();
}



let windowWidth = shared.compact.width;
let deferredTarget = null;

export function applyWindow(target) {
  windowWidth = target.width < 0 ? shared.compact.width : target.width;
  
  
  bridge.setWindowBounds(
    target.width,
    target.height < 0 || CLOSED.has(shared.size) ? target.height : target.height + GROWN_PAD,
    target.rise || 0, Math.round(target.shift || 0)
  );
}


function windowFor(next, override) {
  if (override) return override;
  if (next === 'idle') return { width: -1, height: -1 };
  if (next === 'playing') return mediaWindow();
  if (next === 'timing') return timerWindow();
  if (next === 'calling') return closedTarget();
  return SIZES[next];
}








export function setSize(next, override) {
  if (shared.size === next && !override) return;
  shared.size = next;
  const target = windowFor(next, override);
  const width = target.width < 0 ? shared.compact.width : target.width;

  if (width > windowWidth) {
    deferredTarget = null;
    applyWindow(target);
    afterRelayout(paintSize);
    return;
  }
  deferredTarget = target;
  paintSize();
  ensureClosedWindow();
  
  
  
  
  
  
  
  
  
  
  
  
  
  if (!shared.isTouchDown) applyClosedWindow();
}








export function applyClosedWindow() {
  if (root.classList.contains('holding')) return;
  applyWindow(deferredTarget || windowFor(shared.size));
}






export function ensureClosedWindow() {
  clearTimeout(shared.restoreTimer);
  
  
  shared.restoreTimer = setTimeout(applyClosedWindow, MERGE_TRAVEL + MERGE_EXPAND + 320);
}


let lastOwner = null;


const MOD_CLOSED = {

  timer: { paint: () => paintTimer(), face: 'timer', size: 'timing', room: () => timerWindow() },
  media: { paint: () => paintMedia(), face: 'media', size: 'playing', room: () => mediaWindow() },
  call: { paint: () => paintCall(), face: 'call', size: 'calling', room: closedTarget },
};










let enterSide = 'hole';
function enterFrom(side) {
  enterSide = side;
}






const MOD_LEAVE = 260;
const MOD_LEAVE_WAIT = 340 * LAND;
let leaveTimer = null;







function flyGlyphOut(name) {
  const glyph = faces[name] && faces[name].querySelector('.glyph');
  if (!glyph) {
    showFace('idle');
    return;
  }
  root.classList.add('leaving');
  stirLiquid(MOD_LEAVE_WAIT + MOD_LEAVE + 200);
  leaveTimer = setTimeout(() => {
    glyph.classList.add('leaving');
    leaveTimer = setTimeout(() => {
      glyph.classList.remove('leaving');
      root.classList.remove('leaving');
      leaveTimer = null;
      showFace('idle');
      root.style.removeProperty('--app-accent');
    }, MOD_LEAVE);
  }, MOD_LEAVE_WAIT);
}






const COLLAPSE = 340;
let returnTimer = null;









function faceAfterCollapse(name) {
  clearTimeout(returnTimer);
  stirLiquid(COLLAPSE + MOD_ENTER + 200);
  returnTimer = setTimeout(() => {
    returnTimer = null;
    
    
    if (shared.state !== 'idle') return;
    const owner = liveMods()[0];
    
    
    
    
    if (name === 'idle') {
      if (owner) return;
      showFace('idle');
      root.style.removeProperty('--app-accent');
      return;
    }
    
    if (!owner || MOD_CLOSED[owner].face !== name) return;
    showFace(name);
    enterFrom('hole');
    flyGlyphIn(name);
  }, COLLAPSE * LAND);
}


function flyGlyphIn(name) {
  const glyph = faces[name] && faces[name].querySelector('.glyph');
  if (!glyph) return;
  
  clearTimeout(leaveTimer);
  root.classList.remove('leaving');
  glyph.classList.remove('entering', 'bumping', 'leaving');
  
  
  void glyph.offsetWidth;
  
  
  
  const side = enterSide;
  enterSide = 'hole';
  if (side === 'hole') {
    glyph.classList.add('entering');
    return;
  }
  const thrown = BUMP_MIN + (BUMP_MAX - BUMP_MIN) * Math.min(1, swapSpeed / SWAP_FLICK);
  glyph.style.setProperty(
    '--bump', (side === 'left' ? thrown : -thrown).toFixed(1) + 'px'
  );
  glyph.classList.add('bumping');
}












function claimRoom(owner) {
  const held = mergeHold;
  mergeHold = false;
  const room = MOD_CLOSED[owner].room();
  mergeHold = held;
  if ((room.width < 0 ? shared.compact.width : room.width) > windowWidth) applyWindow(room);
}









function handOver() {
  mergeHold = false;
  root.classList.remove('entering');
  
  
  if (shared.state !== 'idle') return;
  
  
  const carried = Boolean(leavingSatellite());
  takingIn = true;
  if (carried) root.classList.add('handing-over');
  toClosed();
  if (carried) trackExpansion();
  takingIn = false;
}







function handOverAfter(milliseconds) {
  clearTimeout(mergeTimer);
  mergeTimer = setTimeout(handOver, milliseconds);
}


function leavingSatellite() {
  return ['right', 'left']
    .map(side => satellites[side])
    .find(element => element.dataset.leaving) || null;
}





















export function becomeExtended() {
  if (nowOpen) closeNowPanel();
  if (statusOpen) closeStatusPanel();
  shared.state = 'extended';
}

export function toClosed() {
  clearTimeout(shared.dwellTimer);
  
  
  if (mergeHold && !takingIn) return;
  
  
  const wasOpen = !CLOSED.has(shared.size);
  shared.state = 'idle';
  shared.current = null;
  pill.classList.remove('alert', 'with-image', 'charging');

  const live = liveMods();
  const owner = live[0] || null;
  const settling = takingIn;

  
  
  
  if (!settling && owner && live.length < lastLive) {
    mergeHold = true;
    paintSatellites();
    showFace('idle');
    root.style.removeProperty('--app-accent');
    setSize('idle');
    claimRoom(owner);
    
    
    
    
    const carrier = leavingSatellite();
    if (carrier) catchInto(carrier, pill, { entered: handOver });
    else handOverAfter(MERGE_TRAVEL * LAND);
    return;
  }

  
  
  
  if (!settling && owner && owner !== lastOwner && live.length === 1) {
    mergeHold = true;
    paintSatellites();
    MOD_CLOSED[owner].paint();
    showFace(MOD_CLOSED[owner].face);
    root.classList.add('entering');
    
    
    enterFrom('hole');
    flyGlyphIn(MOD_CLOSED[owner].face);
    setSize(MOD_CLOSED[owner].size, { width: -1, height: -1, shift: 0 }, true);
    claimRoom(owner);
    handOverAfter(MOD_ENTER * LAND);
    return;
  }

  mergeHold = false;

  
  
  
  
  paintSatellites();

  if (owner) {
    const mod = MOD_CLOSED[owner];
    mod.paint();
    
    
    
    
    if (returnTimer !== null && !settling) {
      setSize(mod.size, mod.room(), true);
      lastOwner = owner;
      return;
    }
    if (wasOpen && !settling) {
      showFace(null);
      setSize(mod.size, mod.room(), true);
      faceAfterCollapse(mod.face);
      lastOwner = owner;
      return;
    }
    showFace(mod.face);
    
    
    if (!settling && owner !== lastOwner) flyGlyphIn(mod.face);
    setSize(mod.size, mod.room(), true);
    lastOwner = owner;
    return;
  }

  
  
  const leaving = lastOwner;
  lastOwner = null;
  ensureClosedWindow();
  setSize('idle');
  if (!leaving) {
    
    
    
    if (leaveTimer !== null) return;
    
    
    
    
    
    if (wasOpen) {
      showFace(null);
      faceAfterCollapse('idle');
      return;
    }
    showFace('idle');
    root.style.removeProperty('--app-accent');
    return;
  }
  flyGlyphOut(MOD_CLOSED[leaving].face);
}
