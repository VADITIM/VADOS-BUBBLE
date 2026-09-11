# The 3D cube — CSS faces, quaternion spin, raining build

A wireframe cube built from six CSS-transformed faces, spinning idly, draggable with the pointer in
two axes, and assembled on screen face by face as if it were raining into place. Origin: PORTFOLIO25
(`src/components/Main/Logs Section/Cubes.vue` + `Logs-Cube.vue`).

**Layers:** the **motion** (the build, the shadow bloom, the idle bob) and the **behaviour** (the
quaternion orientation model and the drag) are the valuable halves and lift independently of each
other. The **styling** — the blueprint-grid face, the glow floor — is a look and is easy to replace.
The **layout** layer is two arrangements of the same object: a row on a desktop and an upright
carousel circle on a phone (§8).

**No 3D library.** Six divs, `transform-style: preserve-3d`, one `perspective`, and a `matrix3d`
string written per frame. The whole object is under 40 lines of geometry.

---

## 1. Geometry from one custom property

```ts
// Each face is pushed out half the cube's edge. The edge itself is the CSS custom property
// `--cube-size` on the scene, so the geometry follows the layout size at every viewport instead of
// being pinned to a px constant.
const FACE_HALF = 'calc(var(--cube-size) / 2)';
const FACE_TRANSFORMS = [
  `rotateX(-90deg) translateZ(${FACE_HALF})`,   // bottom
  `rotateY(180deg) translateZ(${FACE_HALF})`,   // back
  `rotateY(-90deg) translateZ(${FACE_HALF})`,   // left
  `rotateY(90deg)  translateZ(${FACE_HALF})`,   // right
  `rotateY(0deg)   translateZ(${FACE_HALF})`,   // front
  `rotateX(90deg)  translateZ(${FACE_HALF})`,   // top
];
```

**Every dimension in the component is a fraction of `--cube-size`** — the scene box, the face
`translateZ`, the blueprint grid pitch, the token type size. Change the one property and the cube is
a different size and *the same object*; pin any of them to px and a small cube becomes the same
object with a denser grid and oversized text.

```scss
.cube-scene {
  --cube-size: 13.1rem;
  width: var(--cube-size);
  height: var(--cube-size);
  perspective: 69rem;
  cursor: grab;
  touch-action: none;      // §5
}

.cube {
  position: relative;
  width: 100%; height: 100%;
  transform-style: preserve-3d;
  transform: rotateX(-20deg) rotateY(-28deg);   // the resting three-quarter view
}

.cube-face-wrapper { position: absolute; inset: 0; transform-style: preserve-3d; opacity: 0; }
```

**The wrapper/face split is what makes the build possible.** The face carries the *static* transform
that puts it on its side of the cube; the wrapper carries the *animated* transform that flies it in.
One element cannot hold both — a tween on `y` would blow away the `rotateY(90deg) translateZ(...)`
that makes it a face.

`perspective` sits on the scene, not on the cube, so it survives the `matrix3d` written to the cube
every frame.

---

## 2. Sizing by box, never by scale

```scss
// `--cube-size` drives the scene box, the face translateZ (composed in the script) and the face
// type. Sizing the box rather than transform-scaling it keeps the layout honest — a scale left the
// box at its unscaled size (overflowing the cell) and was silently overwritten by the hover tween's
// `scale: 1`, which is why the cubes only grew once hovered.
```

That comment is the bug report that produced one of the DNA's standing rules
(`dna/04-layout-and-sizing.md`): **never fake a size change with `transform: scale()`.** Two failures
at once — a layout box still at its old size, and a value any other tween touching `scale` will
silently overwrite. The symptom was "the cubes are the wrong size until you hover them", which points
nowhere near either cause.

---

## 3. Orientation is a quaternion, not two angles

```ts
// World-space (extrinsic) rotations: pre-multiply so each drag increment rotates around the SCREEN
// axes, not the cube's local axes. This means "drag right always spins right" regardless of
// current orientation.
interface Q { w: number; x: number; y: number; z: number }

const qMul = (left: Q, right: Q): Q => ({
  w: left.w*right.w - left.x*right.x - left.y*right.y - left.z*right.z,
  x: left.w*right.x + left.x*right.w + left.y*right.z - left.z*right.y,
  y: left.w*right.y - left.x*right.z + left.y*right.w + left.z*right.x,
  z: left.w*right.z + left.x*right.y - left.y*right.x + left.z*right.w,
});

const qNorm = (q: Q): Q => {
  const length = Math.sqrt(q.w**2 + q.x**2 + q.y**2 + q.z**2) || 1;
  return { w: q.w/length, x: q.x/length, y: q.y/length, z: q.z/length };
};

const qFromAxis = (axisX: number, axisY: number, axisZ: number, degrees: number): Q => {
  const halfAngle = degrees * Math.PI / 360;
  const sine = Math.sin(halfAngle);
  return { w: Math.cos(halfAngle), x: axisX*sine, y: axisY*sine, z: axisZ*sine };
};

// Column-major matrix3d for CSS; perspective on the scene still applies.
const qToCSS = ({ w, x, y, z }: Q) =>
  `matrix3d(${1-2*(y*y+z*z)},${2*(x*y+z*w)},${2*(x*z-y*w)},0,` +
  `${2*(x*y-z*w)},${1-2*(x*x+z*z)},${2*(y*z+x*w)},0,` +
  `${2*(x*z+y*w)},${2*(y*z-x*w)},${1-2*(x*x+y*y)},0,0,0,0,1)`;

// The resting view as a quaternion: CSS rotateX(-20deg) rotateY(-28deg) is the extrinsic product
// qY(-28) * qX(-20) — right-to-left world order.
const Q0 = () => qNorm(qMul(qFromAxis(0,1,0,-28), qFromAxis(1,0,0,-20)));
```

**Accumulating `rotateX(a) rotateY(b)` as two numbers is the wrong model and it fails visibly.** Once
the cube has been dragged past a quarter turn, its local Y axis is no longer the screen's, so
"drag right" starts spinning it around some diagonal, and near the poles the two angles gimbal-lock
into a single degree of freedom. A quaternion **pre-multiplied** by each increment applies the
rotation in world space, so drag-right is drag-right forever.

`qNorm` on every accumulation is not optional: floating-point drift in a quaternion multiplied
sixty times a second turns into a visible shear inside a minute.

---

## 4. The idle spin

```ts
function spinnerStep() {
  for (const cube of states) {
    if (!cube.dragging && !cube.building) {
      cube.q = qNorm(qMul(qFromAxis(0,1,0, cube.vY), qMul(qFromAxis(1,0,0, cube.vX), cube.q)));
      cube.vX += (cube.idleVX - cube.vX) * 0.02;   // ease back to the resting drift
      cube.vY += (cube.idleVY - cube.vY) * 0.02;
    }
    cube.element.style.transform = qToCSS(cube.q);
  }
}
```

The velocity is a **lerp back toward an idle drift**, not a clamp to zero. Releasing a flick therefore
spins on, decays, and settles into the ambient rotation instead of stopping — the cube never has a
moment where it is obviously "not being animated".

`0.02` is a very slow return: a hard flick coasts for several seconds. That is the intended read — a
heavy object with real angular momentum.

Each cube's idle velocity is randomised per axis and per sign:

```ts
const idleVX = gsap.utils.random([-1, 1]) * gsap.utils.random(0.02, 0.07);
const idleVY = gsap.utils.random([-1, 1]) * gsap.utils.random(0.08, 0.2);
```

Y is roughly 3× X, because a cube tumbling equally on both axes reads as debris; a cube turning
mostly about its vertical with a slight tilt reads as an object on display.

**Reduced motion parks the idle velocity at zero rather than stopping the loop.** Dragging still
works, it just comes to rest. Killing the loop instead would make the cube undraggable, and the drag
is content, not decoration — the same distinction as the typewriter's carve-out
(`components/09-typewriter.md §4`).

The transform is written **every frame regardless of the branch**, so a dragging cube and a settled
one go through exactly one code path to the DOM.

---

## 5. The drag

```ts
const move = (event: PointerEvent) => {
  const dx = event.clientX - lastX, dy = event.clientY - lastY;
  lastX = event.clientX; lastY = event.clientY;
  cube.q = qNorm(qMul(qFromAxis(0,1,0, dx * 0.5), qMul(qFromAxis(1,0,0, -dy * 0.5), cube.q)));
  cube.vX = -dy * 0.5; cube.vY = dx * 0.5;   // the throw: last frame's delta becomes the velocity
};
```

Half a degree per pixel, and `dy` negated so dragging **down** tips the top of the cube **toward** you.
Setting the velocity from the same delta on every move means release requires no special case — the
cube is already carrying the speed of the last frame.

`pointerdown` binds `pointermove`/`pointerup` **on `window`**, not on the element, so a drag that
leaves the cube keeps working. Both are removed in `up`, and again in the component's teardown.

```scss
// Rotation is a two-axis drag, so the cube has to claim the whole gesture: touch-action stops the
// browser panning, and the data-scroll-region attribute marks the claim for the app's scroll input
// so a vertical drag rotates instead of stepping the screen.
.cube-scene { touch-action: none; }
```

**Any element that takes a two-axis drag has to say so twice** — once to the browser (`touch-action`)
and once to whatever else owns gestures on that screen. Doing only one leaves a gesture that half
works.

On a phone the drag is not bound at all:

```ts
// On a phone the horizontal stroke belongs to the circle carousel, and a touch screen fires
// mouseenter on tap but never the matching mouseleave — so there the cube keeps only its idle spin.
if (!isMobileDevice.value) {
  scene.addEventListener('pointerdown', down);
  scene.addEventListener('mouseenter', enter);
  scene.addEventListener('mouseleave', leave);
}
```

Hover, meanwhile, grows the scene and stretches the shadow floor — the shadow with `overwrite: 'auto'`
because hover in and out can race:

```ts
gsap.to(scene,  { scale: 1.1, duration: 0.35, ease: 'power2.out' });
gsap.to(shadow, { scaleX: 1.18, filter: 'brightness(1.5)', duration: 0.35, ease: 'power2.out', overwrite: 'auto' });
```

*(This `scale` is legitimate — it is a hover effect on a settled box, not a way of sizing the cube.
§2 is about sizing.)*

---

## 6. The build — the piece worth stealing

The cube does not fade in. **Its six faces rain down from above the viewport in a random order,
land, and become a cube.**

```ts
const BUILD_STAGGER = 0.22;  // gap between successive cubes starting to build
const FACE_STAGGER  = 0.06;  // gap between faces of the same cube

function buildCube(cube: HTMLElement, delay: number) {
  const faces = cube.querySelectorAll<HTMLElement>('.cube-face-wrapper');

  // Give the cube a random orientation to build INTO, and a fresh idle drift, so no two builds
  // resolve to the same pose.
  state.building = true;
  state.q = qNorm(qMul(qFromAxis(0,1,0, gsap.utils.random(-360, 360)),
                       qFromAxis(1,0,0, gsap.utils.random(-40, 20))));

  gsap.killTweensOf(faces);
  // Measured, not guessed: far enough above the cube's own screen position that the faces start
  // off-viewport at any layout, rather than at a px constant that is off-screen on one size only.
  const rect = cube.getBoundingClientRect();
  const fromTop = -(rect.top + rect.height + 80);

  const order = gsap.utils.shuffle([...faces].map((_, index) => index));
  let last = 0;
  order.forEach((faceIndex, step) => {
    const face = faces[faceIndex];
    // A fixed stagger plus a random jitter of most of one step: ordered enough to read as a
    // sequence, irregular enough not to read as a metronome.
    const start = step * FACE_STAGGER + gsap.utils.random(0, FACE_STAGGER * 0.9);
    const duration = gsap.utils.random(0.26, 0.42);
    last = Math.max(last, delay + start + duration);

    gsap.set(face, { opacity: 0, y: fromTop, x: gsap.utils.random(-40, 40), rotationZ: gsap.utils.random(-18, 18) });
    const timeline = gsap.timeline({ delay: delay + start });
    // Opacity snaps on in 0.1s while the fall is already running: a face that fades over its whole
    // travel reads as a ghost, one that is simply there reads as a pop-in.
    timeline.to(face, { opacity: 1, duration: 0.1, ease: 'none' }, 0)
            .to(face, { y: 0, x: 0, rotationZ: 0, duration, ease: 'power4.out' }, 0);
  });

  gsap.delayedCall(last + 0.05, () => { state.building = false; });
  return last;   // the time the last face lands — callers chain off this
}
```

`power4.out` is the fall: nearly all the distance is covered in the first third, and the last few
pixels take the rest. It is the only ease that reads as *arriving* rather than as *descending*.

**`building` is a flag the spin loop honours** (§4), because the build owns the cube's orientation for
its duration and a concurrent idle spin would fight it for the same `transform`.

**Every stage returns the time it finishes**, and the caller composes:

```ts
function buildAll(base: number) {
  const order = gsap.utils.shuffle(cubeElements.map((_, i) => i));
  let done = 0;
  order.forEach((cubeIndex, slot) => {
    done = Math.max(done, buildCube(cubeElements[cubeIndex], base + slot * BUILD_STAGGER));
  });
  return done;
}

const boxesDone = SECTION_ENTER_DELAY + BOX_REVEAL_DURATION + Math.max(0, boxes.length - 1) * BOX_REVEAL_STAGGER;
const done = buildAll(boxesDone);
// Names sweep in once the builds are mostly settled; the positional stagger handles the cascade.
playLabelReveals(nameElements, done - 0.35);
```

**Returned completion times rather than hard-coded delays.** A choreography with four stages and four
authored delay constants is four numbers that drift apart the moment any duration changes. The
`- 0.35` overlap is the one deliberate authored number, and it is an overlap on a *computed* time.

### The shadow sells it

```ts
// Blooms while the faces rain down, then pulses once the last face lands; sells the impact of the
// completed cube.
shadowTimeline
  .to(shadow, { opacity: 0.85, scaleX: 1,    duration: last - delay, ease: 'power2.out' }, 0)
  .to(shadow, { scaleX: 1.25,  opacity: 1,   duration: 0.09, ease: 'power2.out' }, last - delay)
  .to(shadow, { scaleX: 1,     opacity: 0.85, duration: 0.4,  ease: 'power2.out' }, last - delay + 0.09);
```

A 90ms squash and a 400ms recovery, keyed to the *computed* landing time. This is the whole trick for
making an assembly feel like it has mass: the impact is on the ground, not on the object.

The floor itself is one gradient, tinted per cube:

```html
<div class="cube-shadow" :style="{ background: `radial-gradient(ellipse at center, ${cube.background} 0%, transparent 70%)` }"></div>
```

### The idle bob

```ts
// Gentle perpetual float; each scene bobs on its own rhythm so the row never reads as mechanically
// synced. Runs for the component's whole life.
gsap.to(scene, {
  y: gsap.utils.random(6, 11),
  duration: gsap.utils.random(1.8, 2.8),
  ease: 'sine.inOut',
  yoyo: true, repeat: -1,
  delay: gsap.utils.random(0, 1.5),
});
```

Randomised amplitude, period **and** start delay — three sources of desynchronisation, because two
are not enough to stop a row of three reading as one object. Skipped entirely under reduced motion
and in lite mode: an endless bob is pure ambience.

### The leave

```ts
// The whole box slides down off screen and fades, carrying the cube with it. No per-face fly-out on
// leave — that build is an enter-only flourish.
gsap.to(boxes, { y: '60vh', opacity: 0, duration: 0.22, stagger: 0.035, ease: 'power3.in', overwrite: 'auto' });
```

0.22s against a build measured in seconds. **The construction is not run backwards** — law 3.

---

## 7. The face

A blueprint grid and an edge vignette, with no extra DOM:

```scss
.cube-face {
  // Bled 1px past the cube's logical edge on every side (translateZ is still pegged to the true
  // half-size in the script) so adjoining faces overlap by a hairline instead of leaving a seam gap
  // at the shared corner.
  position: absolute;
  inset: -1px;
  box-sizing: border-box;
  display: flex; align-items: center; justify-content: center;

  background:
    radial-gradient(ellipse at center, transparent 55%, rgba(0, 0, 0, 0.45) 100%),
    linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px) 0 0 / calc(var(--cube-size) / 7.6) calc(var(--cube-size) / 7.6),
    linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px) 0 0 / calc(var(--cube-size) / 7.6) calc(var(--cube-size) / 7.6),
    rgba(0, 0, 0, 0.3);

  span { font-weight: 700; font-size: calc(var(--cube-size) / 10.5); letter-spacing: 1px; }
}
```

Border, inner glow and token colour are injected per cube, because the cube's colour belongs to the
cube rather than to the screen:

```ts
{ border: `2px solid ${cube.color}`, transform, boxShadow: `inset 0 0 30px ${cube.background}, 0 0 12px ${cube.background}` }
```

**The `inset: -1px` bleed is the fix for hairline seams at the cube's corners.** Faces meeting exactly
at the edge leave a sub-pixel gap that the background shows through, and it moves as the cube turns —
which reads as the cube being made of loose panels. `translateZ` stays pegged to the true half-size,
so the geometry is unchanged; only the paint overlaps.

Colour and background come in pairs (`#3664fc` / `rgba(54,100,252,0.18)`) — a solid for lines and text
and a heavily transparent version of the same hue for fills and glows. That pairing is the general
recipe for a "wireframe/HUD" object anywhere in the DNA.

---

## 8. Two arrangements of the same object

**Landscape** is a flex row of modules, one cube per module
(`components/11-module.md`), each cube carrying its own accent through the module's `accent` prop.

**On a phone** the three cubes ride an **upright circle** parked off to the right: the active one
faces front, the other two hang back above and below it at ±120°. Native scroll-snap cannot do this —
the cells are not in a line, they are placed in 3D — so the seat is tweened.

```ts
const CIRCLE_STEP_DEGREES = 120;

function layoutCircle(immediate = false) {
  // The circle stands upright, so its size is bounded by the HEIGHT it is drawn in.
  const radius = (cellElements[0].parentElement?.clientHeight ?? 0) * 0.34;
  cellElements.forEach((cell, index) => {
    let offset = index - activeCube;
    if (offset >  1) offset -= cellElements.length;   // wrap, so the far cube takes the short way
    if (offset < -1) offset += cellElements.length;
    const angle = (offset * CIRCLE_STEP_DEGREES * Math.PI) / 180;

    // The seat is written as yPercent, NOT y: the enter and leave tweens already own `y` on this
    // element, and GSAP composes the two into one transform — sharing the property would make the
    // reveal drop every cube onto centre.
    gsap.killTweensOf(cell, 'yPercent,z,zIndex');
    gsap.to(cell, {
      yPercent: (Math.sin(angle) * radius) / (cell.offsetHeight || 1) * 100,
      z: Math.cos(angle) * radius - radius,
      zIndex: offset === 0 ? 2 : 1,
      duration: immediate ? 0 : 0.55,
      ease: 'power3.out',
    });
  });
}
```

**`yPercent` versus `y` is the transferable rule**: when two independent choreographies animate the
same element, they must own *different* transform properties. GSAP composes `x/y/xPercent/yPercent/
rotation/scale` into one matrix, so two systems writing `y` do not layer — the last one wins and the
other silently stops working.

A back cube is brought forward by **tapping it**, not by swiping:

```ts
const activate = () => { if (index === activeCube) return; activeCube = index; layoutCircle(); };
cell.addEventListener('click', activate);
```

```scss
// `pan-y`: the circle answers to taps only, so every stroke here still belongs to the screen step.
@include mobileDevice {
  perspective: 62rem;
  perspective-origin: 68% 50%;   // what parks the circle to the right, leaving the left edge free
  transform-style: preserve-3d;
  touch-action: pan-y;
  > * { position: absolute; top: 50%; left: 68%; margin: 0; translate: -50% -50%; }
}
```

**A component inside a screen does not take a gesture the screen needs.** The carousel could have had
a swipe; it would have eaten the vertical stroke that steps between screens. Taps cost nothing and
the navigation stays whole.

On the circle the module chrome is dropped entirely — the cube *is* the object there, and a frame
would draw two more boxes floating behind the front one:

```scss
@include mobileDevice {
  border: none;
  background: transparent;
  // The frame is gone, so its clipping goes with it: a corner swinging past the old box edge should
  // read as the cube turning, not as the cube being cut off by a window that is not drawn.
  overflow: visible;
  :deep(.module-hue), :deep(.module-label) { display: none; }
  :deep(.module-content) { padding: 0; }
}
```

---

## 9. Lite mode

```ts
// Lite: the boxes still arrive, but the cubes are simply there rather than raining in face by face —
// that build is dozens of 3D layers in flight.
if (isLiteMode.value) {
  playLiteEnter(boxes);
  cubeElements.forEach(cube => gsap.set(cube.querySelectorAll('.cube-face-wrapper'), { opacity: 1, x: 0, y: 0, rotationZ: 0 }));
  gsap.set(shadowElements, { opacity: 0.85, scaleX: 1 });
  playLabelReveals(nameElements, SECTION_ENTER_DELAY);
  return;
}
```

**The degraded path sets the same final state the full path animates to** — it does not skip it. The
faces are visible, the shadow is at its resting opacity, the names still sweep. A lite path that
simply returns early leaves the screen holding whatever the initial hidden state was.

---

## 10. Traps

**"Drag right spins it sideways once it has been turned around."** Two accumulated Euler angles
instead of a pre-multiplied quaternion (§3).

**"The cube shears / squashes after a minute of spinning."** Missing `qNorm` on the accumulation (§3).

**"The cubes are the wrong size until you hover one."** Sized with `transform: scale()`, then
overwritten by the hover tween's `scale: 1` (§2).

**"There are hairline gaps at the cube's corners, and they move."** Faces meeting exactly at the edge
instead of bleeding `-1px` (§7).

**"A face flies in and takes its whole side of the cube with it."** The animated transform was put on
the face rather than on the wrapper around it (§1).

**"Dragging the cube also scrolls the page / steps the screen."** Only one of `touch-action: none` and
the app-level gesture claim was set (§5).

**"On the phone carousel, entering the screen drops every cube onto centre."** The seat tween and the
enter tween both write `y` (§8).

**"The build fires but the cube also drifts, and it looks like it is fighting itself."** The
`building` flag is not honoured by the spin loop (§4, §6).

**"On a big screen the faces start already visible."** `fromTop` was a px constant instead of being
measured from the cube's own rect (§6).

**"The three cubes bob in unison."** Only the amplitude or only the delay was randomised (§6).

---

## Anti-patterns

- Reaching for a 3D library to draw a cube (§ intro).
- Accumulating Euler angles for a free-rotation drag (§3).
- Any dimension in the object that is not a fraction of `--cube-size` (§1).
- `transform: scale()` as a sizing mechanism (§2).
- Two choreographies writing the same transform property on one element (§8).
- A per-face fly-*out* on leave. The construction is enter-only; the exit is a cut (§6).
- Authored delay constants chaining a multi-stage choreography instead of returned completion times
  (§6).
- A component inside a screen claiming a gesture that screen's navigation needs (§8).
- A lite-mode path that returns early instead of setting the final state (§9).
