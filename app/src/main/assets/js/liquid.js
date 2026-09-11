import { lockPill } from './lock.js';
import { clockPill, isClockLit } from './clock.js';
import { doublePill, isDoubleOut } from './double.js';
import { statusPill } from './status.js';
import { satellites } from './row.js';
import { CLOSED, MELT_MAX, MELT_MIN, bridge, pill, root, shared } from './state.js';








const liquidLayers = {
  edge: document.getElementById('liquid-edge'),
  fill: document.getElementById('liquid-fill'),
};
































const BLUR_PANES = [
  'main', 'left', 'right', 'lock', 'status', 'clock', 'double',
];










const scrim = document.getElementById('quick-scrim');

const blobs = BLUR_PANES.map(name => ({
  name,
  edge: liquidLayers.edge.appendChild(document.createElement('div')),
  fill: liquidLayers.fill.appendChild(document.createElement('div')),
}));
blobs.forEach(blob => { blob.edge.className = 'blob'; blob.fill.className = 'blob'; });


const liquidBlur = document.getElementById('bubble-melt');
let meltNow = null;


function sourceOf(name) {
  if (name === 'main') return pill;
  if (name === 'status') return statusPill;
  if (name === 'clock') return clockPill;
  if (name === 'double') return doublePill;
  if (name === 'lock') return lockPill;
  return satellites[name];
}








function isSkinned(name) {
  if (name === 'main') return true;
  
  
  
  if (name === 'lock') return lockPill.classList.contains('showing');
  
  
  
  
  
  
  
  
  
  if (name === 'status') return statusPill.classList.contains('lit');
  if (name === 'clock') return isClockLit();
  if (name === 'double') return isDoubleOut();


  return CLOSED.has(shared.size) && Boolean(satellites[name].dataset.mod);
}


let mirroredAt = 0;






let mirroring = false;








root.classList.add('liquid');


export function paintLiquidFrame() {
  if (mirroring) return;
  mirroring = true;
  try {
    mirrorFrame();
  } finally {
    mirroring = false;
  }
}

function mirrorFrame() {
  const now = performance.now();
  
  
  
  const elapsed = mirroredAt ? Math.min(0.05, (now - mirroredAt) / 1000) : 0;
  mirroredAt = now;

  const measured = blobs.map(blob => {
    if (!isSkinned(blob.name)) return null;
    const source = sourceOf(blob.name);
    
    
    
    
    
    
    if (blob.source !== source) blob.style = getComputedStyle(source);
    blob.source = source;
    const style = blob.style;
    return {
      source,
      box: source.getBoundingClientRect(),
      radius: style.borderRadius,
      
      
      
      corner: style.cornerShape,
      colour: style.borderTopColor,
      
      
      
      
      fill: style.getPropertyValue('--blob-fill').trim(),
      
      
      
      alpha: parseFloat(style.opacity),
    };
  });

  sendBlurFrame(measured);

  blobs.forEach((blob, index) => {
    const seen = measured[index];
    
    
    
    
    
    
    
    if (!seen || !seen.box.width || (!isNaN(seen.alpha) && seen.alpha < 0.01)) {
      blob.edge.style.display = 'none';
      blob.fill.style.display = 'none';
      return;
    }
    blob.edge.style.display = '';
    blob.fill.style.display = '';
    
    
    
    write(blob.edge, seen.box, 0, seen);
    write(blob.fill, seen.box, -1, seen);
    blob.edge.style.background = seen.colour;
    
    
    blob.fill.style.background = isTinted(seen.fill) ? seen.fill : '';
  });

  paintWalls(measured);
  meltBy(measured);
  
  
  
  renderMerges(measured, elapsed, now);
}












function meltBy(measured) {
  const boxes = measured.filter(seen => seen && seen.box.width).map(seen => seen.box);
  let closest = Infinity;
  
  
  
  
  
  
  
  
  for (let a = 0; a < boxes.length; a += 1) {
    for (let b = a + 1; b < boxes.length; b += 1) {
      const one = boxes[a];
      const two = boxes[b];
      if (one.top >= two.bottom || two.top >= one.bottom) continue;
      closest = Math.min(
        closest, one.left < two.left ? two.left - one.right : one.left - two.right
      );
    }
  }
  
  
  
  
  
  
  const ceiling = shared.goo || MELT_MAX;
  const melt = closest === Infinity
    ? MELT_MIN
    : Math.min(ceiling, Math.max(MELT_MIN, ceiling - closest * 0.5));
  if (melt !== meltNow) {
    meltNow = melt;
    liquidBlur.setAttribute('stdDeviation', melt);
  }
}
















let blurSent = '';
















export function resendBlur() {
  blurSent = '';
  stirLiquid(240);
}
















const FROST_STEPS = 12;

function scrimRegion() {
  const frost = parseFloat(getComputedStyle(scrim).getPropertyValue('--scrim-frost'));
  if (!frost || frost < 0.01) return '';
  const box = scrim.getBoundingClientRect();
  return [
    Math.round(box.left),
    Math.round(box.top),
    Math.round(box.width),
    Math.round(box.height),
    0,
    
    
    
    
    
    
    
    
    
    
    (Math.round(frost * FROST_STEPS) / FROST_STEPS).toFixed(2),
  ].join(',');
}

function sendBlurFrame(measured) {
  const spec = [scrimRegion()].concat(measured.map(seen => {
    if (!seen || !seen.box.width) return '';
    
    
    const alpha = isNaN(seen.alpha) ? 1 : seen.alpha;
    if (alpha < 0.01) return '';
    const width = seen.box.width * alpha;
    const height = seen.box.height * alpha;
    return [
      Math.round(seen.box.left + (seen.box.width - width) / 2),
      Math.round(seen.box.top + (seen.box.height - height) / 2),
      Math.round(width),
      Math.round(height),
      
      
      Math.round(Math.min(parseFloat(seen.radius) || 0, Math.min(width, height) / 2)),
    ].join(',');
  })).join(';');
  if (spec === blurSent) return;
  blurSent = spec;
  bridge.setBlurFrame(spec);
}
















const walls = ['left', 'right'].map(side => ({
  side,
  edge: liquidLayers.edge.appendChild(document.createElement('div')),
  fill: liquidLayers.fill.appendChild(document.createElement('div')),
}));
walls.forEach(wall => { wall.edge.className = 'blob'; wall.fill.className = 'blob'; });


const WALL_REACH = 44;


const WALL_OVER = 12;


const WALL_WAIST = 0.42;

function paintWalls(measured) {
  const grown = root.classList.contains('grown');
  walls.forEach(wall => {
    
    
    
    
    
    const near = !shared.edgeMerge ? null
      : grown ? withinReach(measured[0], wall.side)
      : nearestTo(wall.side, measured);
    if (!near) {
      wall.edge.style.display = 'none';
      wall.fill.style.display = 'none';
      return;
    }
    const waist = near.box.height * WALL_WAIST;
    
    
    const top = grown ? 0 : near.box.top + (near.box.height - waist) / 2;
    const box = {
      left: wall.side === 'left' ? -WALL_OVER : near.box.right - 1,
      top,
      width: (wall.side === 'left' ? near.box.left + 1 : window.innerWidth - near.box.right + 1) + WALL_OVER,
      height: (grown ? near.box.top - top + near.box.height : 0) + waist,
    };
    box.right = box.left + box.width;
    box.bottom = box.top + box.height;
    wall.edge.style.display = '';
    wall.fill.style.display = '';
    const seen = { radius: (waist / 2) + 'px', corner: near.corner, colour: near.colour };
    write(wall.edge, box, 0, seen);
    write(wall.fill, box, -1, seen);
    wall.edge.style.background = near.colour;
  });
}


function gapTo(seen, side) {
  return side === 'left' ? seen.box.left : window.innerWidth - seen.box.right;
}


function withinReach(seen, side) {
  if (!seen || !seen.box.width) return null;
  const gap = gapTo(seen, side);
  return gap >= 0 && gap <= WALL_REACH ? seen : null;
}


function nearestTo(side, measured) {
  let found = null;
  for (const seen of measured) {
    if (!withinReach(seen, side)) continue;
    if (!found || gapTo(seen, side) < gapTo(found, side)) found = seen;
  }
  return found;
}


const isTinted = fill => Boolean(fill) && !fill.startsWith('rgba(0, 0, 0, 0)');

function write(shape, box, grow, seen) {
  shape.style.width = (box.width + grow * 2) + 'px';
  shape.style.height = (box.height + grow * 2) + 'px';
  shape.style.transform =
    'translate(' + (box.left - grow) + 'px,' + (box.top - grow) + 'px)';
  shape.style.borderRadius = seen.radius;
  shape.style.cornerShape = seen.corner;
}













let liquidUntil = 0;
let liquidFrame = null;

export function stirLiquid(milliseconds) {
  /* The mirror is the most expensive loop in the page — a rect off every bubble and a write back to the host, every frame — and behind a hidden stage it was measuring shapes nobody can see: a payload arriving while the screen is off used to buy a second of it. Nothing is mirrored while the stage is hidden, and the reveal stirs it once. */
  if (shared.isStageHidden) return;




  liquidUntil = Math.max(liquidUntil, performance.now() + (milliseconds || 700));
  if (liquidFrame !== null) return;
  
  
  
  
  bridge.wakeFrames();
  wokeAt = performance.now();
  framesSinceWake = 0;
  liquidFrame = requestAnimationFrame(flowLiquid);
}


let wokeAt = 0;
let framesSinceWake = 0;

function flowLiquid() {
  const before = performance.now();
  paintLiquidFrame();
  if (TRACE_MERGES && framesSinceWake < 8) {
    framesSinceWake += 1;
    traceEvent(
      'frame ' + framesSinceWake + ' at +' + Math.round(before - wokeAt) +
      'ms, paint ' + Math.round(performance.now() - before) + 'ms'
    );
  }
  liquidFrame = performance.now() < liquidUntil
    ? requestAnimationFrame(flowLiquid)
    : null;
}




























const CATCH_ENTERED = 0.8;


const CATCH_SWALLOWED = 0.97;


const CATCH_SWELL = 0.13;


const CATCH_STIFFNESS = 260;
const CATCH_DAMPING = 17;








const CATCH_CEILING = 900;


const CATCH_STALL = 260;









const TRACE_MERGES = false;

function trace(what, caught, entry, now) {
  if (!TRACE_MERGES) return;
  console.log(
    '[merge] ' + what + ' ' + nameOf(caught.traveller) + ' into ' +
    nameOf(caught.receiver) + ' at ' + entry.toFixed(2) +
    ' after ' + Math.round(now - caught.since) + 'ms'
  );
}

function nameOf(element) {
  return element.id || element.className || 'unnamed';
}


export function traceEvent(text) {
  if (TRACE_MERGES) console.log('[merge] ' + text);
}


const catches = [];


const swells = new Map();






export function catchInto(traveller, receiver, steps) {
  const running = catches.find(caught => caught.traveller === traveller);
  if (running) {
    Object.assign(running.steps, steps);
    return;
  }
  const caught = {
    traveller,
    receiver,
    steps: Object.assign({}, steps),
    entered: false,
    
    
    best: 0,
    since: performance.now(),
    movedAt: 0,
  };
  caught.movedAt = caught.since;
  catches.push(caught);
  trace('caught', caught, 0, caught.since);
  stirLiquid();
}


export function releaseCatch(traveller) {
  const index = catches.findIndex(caught => caught.traveller === traveller);
  if (index >= 0) catches.splice(index, 1);
}











function enteredShare(traveller, receiver) {
  const wide = Math.min(traveller.right, receiver.right) - Math.max(traveller.left, receiver.left);
  const tall = Math.min(traveller.bottom, receiver.bottom) - Math.max(traveller.top, receiver.top);
  if (wide <= 0 || tall <= 0 || !traveller.width || !traveller.height) return 0;
  return (wide * tall) / (traveller.width * traveller.height);
}

function renderMerges(measured, elapsed, now) {
  const boxOf = new Map();
  measured.forEach(seen => {
    if (seen && seen.box.width) boxOf.set(seen.source, seen.box);
  });

  const driven = new Map();
  for (const caught of catches.slice()) {
    if (!catches.includes(caught)) continue;
    const traveller = boxOf.get(caught.traveller);
    const receiver = boxOf.get(caught.receiver);
    
    
    const entry = traveller && receiver ? enteredShare(traveller, receiver) : 1;

    
    
    
    
    
    
    if (entry > caught.best + 0.01) {
      caught.best = entry;
      caught.movedAt = now;
    }
    const stalled = caught.best > 0.02 && now - caught.movedAt > CATCH_STALL;
    const late = stalled || now - caught.since > CATCH_CEILING;

    if (traveller && receiver) {
      
      
      
      const bulk = Math.min(
        1,
        (traveller.width * traveller.height) / (receiver.width * receiver.height)
      );
      driven.set(
        caught.receiver,
        (driven.get(caught.receiver) || 0) + entry * bulk * CATCH_SWELL
      );
    }

    if (!caught.entered && (entry >= CATCH_ENTERED || late)) {
      caught.entered = true;
      trace('entered', caught, entry, now);
      if (caught.steps.entered) caught.steps.entered();
    }
    if (caught.entered && (entry >= CATCH_SWALLOWED || late)) {
      
      
      trace('swallowed', caught, entry, now);
      releaseCatch(caught.traveller);
      if (caught.steps.swallowed) caught.steps.swallowed();
    }
  }

  driven.forEach((_, receiver) => {
    if (!swells.has(receiver)) swells.set(receiver, { value: 0, velocity: 0 });
  });

  swells.forEach((swell, receiver) => {
    const target = driven.get(receiver);
    if (target !== undefined) {
      
      
      
      
      if (elapsed > 0) swell.velocity = (target - swell.value) / elapsed;
      swell.value = target;
    } else {
      swell.velocity +=
        (-swell.value * CATCH_STIFFNESS - swell.velocity * CATCH_DAMPING) * elapsed;
      swell.value += swell.velocity * elapsed;
      if (Math.abs(swell.value) < 0.0004 && Math.abs(swell.velocity) < 0.004) {
        swells.delete(receiver);
        receiver.style.removeProperty('--absorb');
        return;
      }
    }
    receiver.style.setProperty('--absorb', swell.value.toFixed(4));
  });

  
  
  
  if (catches.length || swells.size) {
    liquidUntil = Math.max(liquidUntil, now + 120);
  }
}
