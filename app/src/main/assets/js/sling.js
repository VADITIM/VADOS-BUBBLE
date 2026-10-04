// The slingshot is one journey written once. The Now bubble's flight between Main and the lock screen and the Alert's flight into the dashboard's stats section are the same draw-and-release on the same numbers, and a second copy of them would be two motions that look alike only until one of them is tuned.
// The leave is the one journey here written as **two** timelines, and the seam between them is the point of it: a slingshot is a draw and then a release, and those are two events with a discontinuity between them. What made a single timeline read as lame is that it was one curve every time — same draw, same shares, same distance — so the eye learnt it after two goes.
export const SLING_DRAW_MILLIS = [180, 240];
export const SLING_DRAW_BACK = [26, 44];
export const SLING_DRAW_SQUEEZE = 0.94;


// The shot's speed and overshoot are drawn fresh per shot and bounded on purpose: weaker than this and the arc is a drift, stronger and the whole thing is over before it can be seen. A shot nothing threw takes a side at this angle, since straight down the middle is the one shape it may not be; a thrown one is aimed by the throw instead.
export const SLING_SHOT_MILLIS = [300, 420];
export const SLING_SHOT_ANGLE = 10;
export const SLING_SHOT_OVER = [14, 30];
export const SLING_SHOT_PUNCH = 1.16;


// A throw is read off the last stretch of the finger's travel rather than off the whole gesture, because the start of a flick is the finger settling and the end of it is what was thrown. Its speed, in px/ms between the two bounds of SLING_THROW_SPEED, is the one number the shot's clock, bulge and carry are all read off — a gentle flick at the floor, a hard one at the ceiling.
export const SLING_THROW_WINDOW = 64;
export const SLING_THROW_SPEED = [0.3, 2.5];
export const SLING_THROW_MILLIS = [440, 300];
export const SLING_THROW_LEAD = [0.12, 0.7];
export const SLING_THROW_OVER = [6, 40];
export const SLING_THROW_TURN = 60;
export const SLING_THROW_CIRCLE = 0.4;


// How hard the release lets go, as the initial velocity of one damped spring, and how far along the launch line the arc is aimed before it bends onto the target.
export const SLING_SHOT_DECAY = 5.2;
export const SLING_SHOT_LEAD = 0.62;


// The arc is one curve read at this many points. Over a shot of ~350ms that is a sample every 13ms — closer together than the frames that will draw them — so the straight lines between them are what the eye never sees and the curve is what it does.
export const SLING_SAMPLES = 26;


// The punch is keyed to the clock and not to the distance: the release covers a third of the journey in its first sixth, so a stretch keyed to how far it had got would be over before it could be seen.
export const SLING_PUNCH_SHARE = 0.34;


// Where the spring stops being a spring. Past this the overshoot it is still carrying is wound down to nothing on a curve that is flat at both ends, so the shape arrives at a standstill instead of arriving with the velocity a cut-off oscillation still has — that leftover velocity is what a bounce is.
export const SLING_SETTLE_FROM = 0.78;


export const SLING_DRAW_EASE = 'cubic-bezier(0.5, 0, 0.75, 0.2)';
export const SLING_POP_EASE = 'cubic-bezier(0.2, 1.9, 0.4, 1)';
export const SLING_SHOT_EASE = 'cubic-bezier(0.3, 0, 0.2, 1)';


// How far past the mark the spring's first crest lands, as a share of the journey. It has no closed form — the crest is where the spring's own velocity turns, not where its cosine bottoms out, and solving it as though the two were the same put the overshoot at nearly twice what was asked for. The excess climbs with the swing, so a bisection settles it in a handful of steps and the number that comes out is the one the shot was aimed at.
function crestOf(swing, launch) {
  const lag = (SLING_SHOT_DECAY - launch) / swing;
  const at = (Math.atan(lag) + Math.PI - Math.atan(SLING_SHOT_DECAY / swing)) / swing;
  return Math.hypot(1, lag) * (swing / Math.hypot(SLING_SHOT_DECAY, swing)) * Math.exp(-SLING_SHOT_DECAY * at);
}

export function smoothly(step) {
  return step * step * (3 - 2 * step);
}

export function swingFor(share, launch = SLING_SHOT_DECAY) {
  let low = 0.1;
  let high = 40;
  for (let step = 0; step < 24; step += 1) {
    const middle = (low + high) / 2;
    if (crestOf(middle, launch) < share) low = middle;
    else high = middle;
  }
  return (low + high) / 2;
}

export function between([low, high]) {
  return low + Math.random() * (high - low);
}

function blend([from, to], share) {
  return from + (to - from) * share;
}

function wall(value, low, high, keep) {
  return Math.min(Math.max(value, Math.min(low, keep)), Math.max(high, keep));
}

export function throwOf(trail) {
  if (trail.length < 2) return null;
  const last = trail[trail.length - 1];
  let reference = trail[0];
  for (let index = trail.length - 2; index >= 0; index -= 1) {
    reference = trail[index];
    if (last.time - reference.time >= SLING_THROW_WINDOW) break;
  }
  const elapsed = Math.max(1, last.time - reference.time);
  return { x: (last.x - reference.x) / elapsed, y: (last.y - reference.y) / elapsed };
}


// A thrown shot leaves along the throw, turned no further than SLING_THROW_TURN off the line to its target, and the control point is placed down that line at a distance the throw's speed decides — so a slow flick bends the curve barely at all and a hard one swings wide. The spring's launch is then solved so the curve's first velocity, 2 · lead · launch / millis, is the finger's own speed. A lead the canvas cannot hold is shortened along the same line, which keeps the tangent and costs only the bulge.
export function throwShot({ velocity, endX, endY, room, radius }) {
  const reach = Math.hypot(endX, endY) || 1;
  const speed = Math.hypot(velocity.x, velocity.y);
  const power = Math.min(1, Math.max(0, (speed - SLING_THROW_SPEED[0]) / (SLING_THROW_SPEED[1] - SLING_THROW_SPEED[0])));
  const aim = Math.atan2(endY, endX);
  const thrown = speed ? Math.atan2(velocity.y, velocity.x) : aim;
  const turn = Math.atan2(Math.sin(thrown - aim), Math.cos(thrown - aim));
  const limit = (SLING_THROW_TURN * Math.PI) / 180;
  const heading = aim + Math.max(-limit, Math.min(limit, turn));
  const headingX = Math.cos(heading);
  const headingY = Math.sin(heading);

  const fits = [
    headingX > 0 ? (room.right - radius) / headingX : headingX < 0 ? (room.left + radius) / headingX : Infinity,
    headingY > 0 ? (room.bottom - radius) / headingY : headingY < 0 ? (room.top + radius) / headingY : Infinity,
  ];
  const lead = Math.max(1, Math.min(reach * blend(SLING_THROW_LEAD, power), ...fits));
  const millis = Math.round(blend(SLING_THROW_MILLIS, power));

  return {
    holdX: headingX * lead,
    holdY: headingY * lead,
    launch: Math.min(SLING_SHOT_DECAY, (speed * millis) / (2 * lead)),
    millis,
    over: blend(SLING_THROW_OVER, power),
    reach,
  };
}


// One quadratic curve from where the shape let go to where it is going, with its control point set along the launch line: a quadratic's tangent at its start is exactly the line to that control point, so the shape provably leaves at the angle it was aimed at and provably arrives at the target, without either end being a keyframe that has to be tuned into agreement with the other. How far along that curve it has got is one damped spring, so the release is a single continuous velocity — fastest the instant it lets go, easing off, carrying past the target and settling onto it.
// `launch` is the spring's first velocity in journeys per duration; left out it is the decay itself, which is the shot at its fastest the instant it lets go. `room` is the canvas as seen from the shape's resting centre and `extent` the unscaled box at each step: given both, the carry is cut to what the canvas can hold and every sample is kept inside it, except the target, which is always landed on.
export function slingFrames({ startX, startY, holdX, holdY, endX, endY, scaleStart, scaleEnd, over, reach, launch = SLING_SHOT_DECAY, room, extent }) {
  const pointAt = travel => {
    const rest = 1 - travel;
    return [
      rest * rest * startX + 2 * rest * travel * holdX + travel * travel * endX,
      rest * rest * startY + 2 * rest * travel * holdY + travel * travel * endY,
    ];
  };
  let share = Math.min(0.35, Math.max(0.004, over / reach));
  if (room) {
    const landing = extent(1);
    const radiusX = (landing.width * scaleEnd) / 2;
    const radiusY = (landing.height * scaleEnd) / 2;
    for (; share > 0.004; share *= 0.8) {
      const [x, y] = pointAt(1 + share);
      if (x - radiusX >= room.left && x + radiusX <= room.right && y - radiusY >= room.top && y + radiusY <= room.bottom) break;
    }
  }
  const swing = swingFor(Math.max(0.004, share), launch);
  const lag = (SLING_SHOT_DECAY - launch) / swing;
  const frames = [];
  for (let index = 0; index < SLING_SAMPLES; index += 1) {
    const step = index / (SLING_SAMPLES - 1);
    const wound = step <= SLING_SETTLE_FROM
      ? 1
      : 1 - smoothly((step - SLING_SETTLE_FROM) / (1 - SLING_SETTLE_FROM));
    const travel = 1 - Math.exp(-SLING_SHOT_DECAY * step) *
      (Math.cos(swing * step) + lag * Math.sin(swing * step)) * wound;
    const rest = 1 - travel;
    const punch = Math.sin(Math.min(1, step / SLING_PUNCH_SHARE) * Math.PI) ** 2;
    const sizing = scaleStart + (scaleEnd - scaleStart) * (1 - rest * rest);
    let scale = sizing * (1 + (SLING_SHOT_PUNCH - 1) * punch);
    let [x, y] = pointAt(travel);
    if (room) {
      const box = extent(step);
      scale = Math.min(scale, (room.right - room.left) / box.width, (room.bottom - room.top) / box.height);
      x = wall(x, room.left + (box.width * scale) / 2, room.right - (box.width * scale) / 2, endX);
      y = wall(y, room.top + (box.height * scale) / 2, room.bottom - (box.height * scale) / 2, endY);
    }
    frames.push({
      offset: step,
      translate: x.toFixed(1) + 'px ' + y.toFixed(1) + 'px',
      scale,
    });
  }
  return frames;
}


// A box that is not a mod carrying its own reading home takes the whole journey in one call: born out of Main as a circle of the diameter it will fly at, the same damped-spring arc, and then the ball opening out into the box it landed on. The element must already be resting at `to` with its own left and top, because every frame here is a translate away from that resting place and back onto it.
export function slingInto(element, from, to, { ball, restRadius, born, onOpened, isWhole = false, pace = 1 } = {}) {
  // A box flown as a ball of its own height was narrower than the reading it carries, so the landscape bar's Clock and Status spilled their text out of the drop; flown whole, the box and what is in it grow together.
  const diameter = isWhole ? Math.min(to.width, to.height) : ball || Math.min(to.width, to.height);
  const radius = restRadius || getComputedStyle(element).borderTopLeftRadius;
  const drop = (born || Math.min(from.width, from.height)) / diameter;

  // The ball is held at the box's own left and top while it flies, so the circle's middle sits off the box's middle by exactly half of what the box gave up. That offset rides the whole flight and is given back by the opening.
  const offsetX = isWhole ? 0 : (to.width - diameter) / 2;
  const offsetY = isWhole ? 0 : (to.height - diameter) / 2;

  const dx = (from.left + from.width / 2) - (to.left + to.width / 2);
  const dy = (from.top + from.height / 2) - (to.top + to.height / 2);
  const reach = Math.hypot(dx, dy) || 1;

  // Nothing threw this one, so the arc takes a side of its own: straight down the middle is the one shape this journey may not be, whichever way it is travelled.
  const side = Math.random() < 0.5 ? -1 : 1;
  const angle = (side * SLING_SHOT_ANGLE * Math.PI) / 180;
  const launchX = (Math.cos(angle) * -dx - Math.sin(angle) * -dy) / reach;
  const launchY = (Math.sin(angle) * -dx + Math.cos(angle) * -dy) / reach;

  const back = between(SLING_DRAW_BACK);
  const backX = -launchX * back;
  const backY = -launchY * back;

  const frames = slingFrames({
    startX: dx + offsetX,
    startY: dy + offsetY,
    holdX: dx + launchX * reach * SLING_SHOT_LEAD + offsetX,
    holdY: dy + launchY * reach * SLING_SHOT_LEAD + offsetY,
    endX: backX + offsetX,
    endY: backY + offsetY,
    scaleStart: drop,
    scaleEnd: SLING_DRAW_SQUEEZE,
    over: between(SLING_SHOT_OVER),
    reach,
  });

  const millis = Math.round(between(SLING_SHOT_MILLIS) * pace);
  const opening = Math.round(between(SLING_DRAW_MILLIS) * pace);
  const ballSize = {
    width: Math.round(diameter) + 'px',
    height: Math.round(diameter) + 'px',
    borderRadius: Math.round(diameter / 2) + 'px',
  };

  const running = [element.animate(frames, { duration: millis, fill: 'forwards' })];
  if (!isWhole) running.push(element.animate([ballSize, ballSize], { duration: millis, fill: 'forwards' }));

  running[0].addEventListener('finish', () => {
    running.push(
      element.animate(
        [
          {
            translate: (backX + offsetX).toFixed(1) + 'px ' + (backY + offsetY).toFixed(1) + 'px',
            scale: SLING_DRAW_SQUEEZE,
          },
          { translate: '0px 0px', scale: 1 },
        ],
        { duration: opening, easing: SLING_POP_EASE, fill: 'forwards' }
      ),
    );
    if (!isWhole) running.push(
      element.animate(
        [
          ballSize,
          {
            width: Math.round(to.width) + 'px',
            height: Math.round(to.height) + 'px',
            borderRadius: radius,
          },
        ],
        { duration: opening, easing: SLING_SHOT_EASE, fill: 'forwards' }
      )
    );
    // Every animation on this journey is forwards-filled, and the values they end on are the ones the stylesheet already holds — so the last thing that happens is the element getting its own box back. Left filled, a card that had plainly landed was still standing in the flight's values, which is what any later animation on the same properties then had to argue with.
    setTimeout(() => {
      running.forEach(move => move.cancel());
      running.length = 0;
      if (onOpened) onOpened();
    }, opening);
  });

  return {
    millis,
    opening,
    total: millis + opening,
    stop() {
      running.forEach(move => move.cancel());
      running.length = 0;
    },
  };
}
