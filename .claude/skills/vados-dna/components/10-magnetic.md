# Magnetism — the button, the dot field, the click hue

Two pieces that share one idea: **the pointer has a field around it, and things inside that field
lean toward it.** The button is the small, single-target version; the dot field is the ambient,
thousand-target version that backs a whole screen. Origin: PORTFOLIO25
(`src/components/Misc/Magnetic-Button.vue`, `src/components/Misc/Magnetic-Dots.vue`).

**Layers:** both are almost entirely **behaviour**, and both are liftable whole. The button carries
**no styling at all** by design (§1). The dot field's **motion** — the spring integrator in §4 — is
the reusable half and works on any set of points.

**Both are pointer-only.** A touch screen has no hover, so a magnetic field has no input; and worse,
it synthesises one `mousemove` on tap, which flings the target at the finger and *leaves it there*.
Everything here is gated on the device axis (`dna/04-layout-and-sizing.md`), never on width.

---

## 1. The magnetic button

A wrapper that owns a padded hit zone, and a button inside it that travels a fraction of the way
toward the pointer while the pointer is in that zone.

```html
<div ref="wrapperRef" class="mag-wrap" :style="{ padding: `${zone}px` }"
     @mousemove="onMove" @mouseleave="onLeave">
  <button ref="buttonRef" class="mag-btn" :type="type" :disabled="disabled" @click="onClick">
    <slot />
  </button>
</div>
```

**The zone is padding on a wrapper, not a radius check in JS.** The browser already answers "is the
pointer near this element" with hit-testing, for free, at every frame, correctly through transforms
and scroll. A distance test in a global `mousemove` handler re-derives that answer worse and pays for
it on every pointer move on the page.

```ts
const onMove = (event: MouseEvent) => {
  // The magnetism is a hover effect: a touch screen synthesises one mousemove on tap, which would
  // fling the button toward the finger and leave it there.
  if (isMobileDevice.value) return;
  const wrapper = wrapperRef.value, button = buttonRef.value;
  if (!wrapper || !button) return;
  const bounds = wrapper.getBoundingClientRect();
  const dx = event.clientX - (bounds.left + bounds.width  / 2);
  const dy = event.clientY - (bounds.top  + bounds.height / 2);
  gsap.to(button, { x: dx * props.strength, y: dy * props.strength, duration: 0.4, ease: 'power3.out' });
};

const onLeave = () => {
  if (buttonRef.value) gsap.to(buttonRef.value, { x: 0, y: 0, duration: 0.6, ease: 'elastic.out(1,0.3)' });
};
```

**The pull and the release use different curves on purpose.** `power3.out` while attracted — the
button chases, decelerating, and never overshoots the cursor, which would read as it *dodging*.
`elastic.out(1, 0.3)` on release — the snap back is the only place the whole component tells you it
was under tension. Reusing `power3.out` for the release makes the effect read as a lag rather than as
a magnet.

Defaults: `zone: 32` (px of padding — roughly a fingertip either side, wide enough that the pull
starts before the pointer is over the button), `strength: 0.4` (the button never travels more than
40% of the way to the pointer, so the pointer always stays ahead of it and the button never lands
under the cursor and blocks its own hover).

### It ships with no look

```scss
.mag-wrap { display: inline-flex; align-items: center; justify-content: center; cursor: pointer; }

// Bare reset; all visual styling is done by the consumer via :deep(.mag-btn).
.mag-btn {
  -webkit-appearance: none; appearance: none;
  margin: 0; padding: 0;
  background: none; border: none;
  font: inherit; color: inherit;
  display: inline-flex; align-items: center; justify-content: center;
  will-change: transform;
  cursor: pointer;
  &:disabled { cursor: not-allowed; }
}
```

This is the component's whole contract with the DNA's "a component never names a colour" rule taken
to its end: it names no colour, no size, no border and no radius either. It is a **behaviour wrapper**
and the host styles `:deep(.mag-btn)`. That is why it can sit under a link button, a rotary icon
button and a submit control (`components/05-controls.md`) without three variants.

`will-change: transform` is on the button and not the wrapper — the wrapper never moves.

The component exposes its element (`defineExpose({ get element() { return buttonRef.value } })`) so a
screen's enter/leave choreography can tween the real button rather than the padded wrapper, whose box
is bigger than anything visible.

---

## 2. The dot field — what it is

A full-screen canvas grid of dots that, near the pointer, are **dragged toward it**, **scaled up**,
and **brightened** — each with its own radius, so the three effects have different reach and the
field reads as depth rather than as one circular mask. Held mouse buttons strengthen the pull.

Three radii, in `rem` so the field scales with the app's fluid root:

| Radius | Value | What it does | Why that size |
|---|---|---|---|
| `dragRadius` | `6rem` | positional pull | The tightest — the field should deform locally, not slosh. |
| `scaleRadius` | `2rem` | dot enlargement | Tighter still, so only the handful of dots under the cursor grow. |
| `brightnessRadius` | `9rem` | alpha lift | The widest by far. Light carries further than force; this is what makes the pointer read as *lit* rather than as a magnet. |

```ts
const dotSizePx = 3;      // px, not rem: below ~4px a dot IS its antialiasing, and a scaled one blurs
const gapRem = 2;         // rem, so the grid density follows the type scale
const maxScale = 1.1;     // a dot that doubles reads as a bubble, not as a lit dot
const minAlpha = 0.15;    // the field at rest — present, never legible as content
const maxAlpha = 0.9;
const pressPullBoost = 2.2;   // while the button is held, the pull strengthens
```

---

## 3. Canvas, not DOM

A screen-filling grid at a 2rem pitch is a few thousand dots. As elements that is a few thousand
composited layers and a few thousand style recalculations per pointer move; as canvas it is one
element and one `requestAnimationFrame`. **Any field effect with more than a few dozen members is a
canvas.**

```ts
const pixelRatio = window.devicePixelRatio || 1;
canvas.width  = width  * pixelRatio;
canvas.height = height * pixelRatio;
context.scale(pixelRatio, pixelRatio);
```

The backing store is scaled by DPR once at build time and the drawing code then works in CSS pixels
throughout. Skipping this is what makes a canvas look soft next to the crisp DOM beside it.

The grid is rebuilt on resize behind a `requestAnimationFrame` gate, because a drag-resize fires
`resize` far more often than it produces frames:

```ts
const handleResize = () => {
  if (resizeRaf) return;
  resizeRaf = requestAnimationFrame(() => { resizeRaf = 0; buildGrid(); });
};
```

---

## 4. The integrator

Per dot, per frame: a spring toward the target offset, and a plain lerp for scale and alpha.

```ts
dot.vx += (targetX - dot.x) * stiffness;   // stiffness 0.12
dot.vx *= damping;                          // damping   0.82
dot.x  += dot.vx;

dot.vy += (targetY - dot.y) * stiffness;
dot.vy *= damping;
dot.y  += dot.vy;

dot.scale += (targetScale - dot.scale) * scaleLerp;   // 0.18
dot.alpha += (targetAlpha - dot.alpha) * alphaLerp;   // 0.20
```

**Position is a spring and the other two are lerps, and that is the whole feel.** A spring overshoots
and settles, so a dot yanked toward the cursor and released wobbles back — which is what makes the
field feel like a physical surface. Scale and brightness are *not* physical; a dot that overshoots
its brightness flashes. So they get critically-damped lerps and no velocity at all.

`0.12 / 0.82` is the ratio worth carrying: under-damped enough for one visible settle, never a
sustained ring. Raising stiffness without lowering damping gives a field that never comes to rest.

The target is a linear falloff from the radius, not an inverse-square — a physical falloff spikes
hard at the centre and is invisible everywhere else, and this is an interface, not a simulation:

```ts
const strength = (1 - dist / dragRadiusPx) * (mouse.pressed ? pressPullBoost : 1);
targetX = dx * strength;
targetY = dy * strength;
```

**The rAF loop never stops.** No idle short-circuit, no "only run while the pointer moves" gate: a
loop that parks itself has to be correctly restarted from every input path, and the one path that
forgets leaves a dead field with no error. The cost of a settled frame is a few thousand
`arc()` calls into a cleared context.

---

## 5. Following the accent

The field is drawn in the live section colour, read straight from the custom property each frame:

```ts
const cssColor = getComputedStyle(document.documentElement).getPropertyValue('--section-color');
```

The component **names no colour** (`components/00-catalogue.md`, rule 1) — the only literal in the
file is the boot value used before the first screen is active.

**It never cross-fades between two accents.** Fading green to yellow travels through the greys and
the intermediate frames belong to no screen. Instead the whole field fades to zero, swaps colour
while it is invisible, and fades back:

```ts
let fieldAlpha = 1;
let fade: 'none' | 'out' | 'in' = 'none';
let colorSwapPending = false;
const fadeOutSpeed = 0.14;   // fast fade-out before the colour changes
const fadeInSpeed  = 0.05;   // gentle fade-in at the very end

// Swap only once the field is invisible (or immediately when not mid-fade, e.g. the boot handoff).
if (colorSwapPending && (fieldAlpha <= 0.02 || fade === 'none')) {
  currentRgb = { ...targetRgb };
  colorSwapPending = false;
}
```

Out at `0.14`/frame (~7 frames) and in at `0.05` (~20). The asymmetry is law 3 again: the field
*leaves* fast, under the cover of the screen transition, and returns slowly once the incoming screen
has settled. The fade-in is triggered by the transition ending, not by a timer, so it can never
uncover a screen mid-swap:

```ts
onSectionChange((_current, previous) => {
  if (previous === -1) { colorSwapPending = true; return; }  // boot: no curtain, just adopt the colour
  fade = 'out';
  colorSwapPending = true;
});
watch(isTransitioning, (transitioning) => { if (!transitioning) fade = 'in'; });
```

A per-screen dimming factor exists for the one accent that is too hot at the shared alpha range
(`perksBrightness = 0.55` for the bright yellow). **A screen-specific exception like this belongs on
the field, named for the screen, not spread into the palette** — the colour is right, its luminance
in this particular use is not.

---

## 6. The click hue

Clicking anywhere flashes a soft radial wash in the section colour, centred on the pointer, held
while the button is down, following a drag, and fading on release. It is a DOM element, not part of
the canvas: it is one gradient, and CSS transitions it for free.

```scss
.click-hue {
  --hue-x: 50%;
  --hue-y: 50%;
  position: absolute; inset: 0;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.45s ease-out;   // slow fade-out on release
  background: radial-gradient(
    circle 6.8vmax at var(--hue-x) var(--hue-y),
    color-mix(in srgb, var(--section-color, #5bfd5b) 12%, transparent) 0%,
    transparent 62%
  );

  &.held {
    opacity: 1;
    transition: opacity 0.12s ease-out;  // fast flash-in on press
  }
}
```

**The two transitions live on different states, not on the base rule**, which is how one element gets
a 0.12s attack and a 0.45s release. Putting both on the base rule is the usual mistake and gives a
press that arrives as slowly as it leaves.

`6.8vmax` rather than a `rem` or a `px`: this is a screen-scale wash and should cover the same
fraction of any display. `12%` mix — the wash must never be legible as a shape, only as the room
having brightened.

Position is written as two custom properties from JS and read by the gradient. A tween on a custom
property no rule reads is silent dead code (`dna/05-motion.md §7`); this is the working version of
that pattern.

---

## 7. Pointer plumbing

Listeners are on `window`, not the canvas, because the field must keep tracking a pointer that is
over a panel sitting on top of it:

```ts
window.addEventListener('mousemove', handleMouseMove);
window.addEventListener('mouseleave', handleMouseLeave);
window.addEventListener('blur', handleMouseLeave);   // alt-tab away mid-drag leaves the field stuck on
window.addEventListener('mousedown', showHue);
window.addEventListener('mouseup', hideHue);
window.addEventListener('blur', hideHue);
```

**`blur` on both handlers is the fix for the class of bug where an interaction ends outside the
window.** `mouseup` never fires if the button is released over the OS chrome, and `mouseleave` never
fires on an alt-tab.

The root is `pointer-events: none` and only the canvas takes them back, so the field never eats a
click meant for a panel:

```scss
.magnetic-dots-root   { position: absolute; inset: 0; z-index: 0; pointer-events: none; }
.magnetic-dots-canvas { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: auto; }
.magnetic-dots-canvas.disabled { pointer-events: none; }
```

Every listener, both rAF handles and both watchers are torn down in the unmount hook. **A field
component that leaks its loop leaks a permanent frame budget**, and nothing on screen will point at
it.

---

## 8. Traps

**"The button flew at my thumb and stayed there."** The device gate is missing. A touch tap
synthesises a `mousemove` and never a `mouseleave`, so nothing ever calls the reset (§1).

**"The button lands under the cursor and its hover flickers."** `strength` at or near 1. It must stay
well under 1 so the pointer always leads (§1).

**"The snap back feels like lag."** The release reuses the attraction ease instead of an elastic (§1).

**"The dot field is soft/blurry."** The canvas backing store was not scaled by `devicePixelRatio`
(§3).

**"The field never settles — it hums."** Stiffness raised without lowering damping (§4).

**"The dots go grey for half a second at every screen change."** The colour was cross-faded instead of
swapped under cover of a fade-out (§5).

**"The field is stuck lit after I alt-tabbed."** No `blur` handler (§7).

**"Clicks on panels do nothing near the bottom of the screen."** The field root kept
`pointer-events: auto` (§7).

**"The click flash is sluggish."** Both transitions on the base rule instead of the attack living on
`.held` (§6).

---

## Anti-patterns

- A global `mousemove` distance test where a padded wrapper and hit-testing already answer the
  question (§1).
- Giving the magnetic button a look. It is a behaviour wrapper; the host styles it (§1).
- A thousand-element DOM field (§3).
- Springing scale or brightness. Only position is physical (§4).
- An inverse-square falloff in an interface (§4).
- Parking the rAF loop on idle and restarting it from each input path (§4).
- Naming a colour in the field instead of reading the accent (§5).
- Screen-specific luminance corrections pushed back into the palette instead of living on the
  component that needs them (§5).
- Listeners on the canvas rather than the window (§7).
