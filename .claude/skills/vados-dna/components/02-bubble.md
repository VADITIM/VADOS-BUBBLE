# The bubble

A rounded box that behaves like a physical object rather than a control: it can be pressed, held,
dragged in any direction on a rubber band, pulled, flicked away, and grown open. Every one of those
is a separate rule, and they compose without contradicting each other because each owns a different
CSS property.

Origin: Dynamic Bubble. Pairs with [01 — goo](01-goo-liquid.md), which is what makes several of
them read as one material.

**Layers:** layout is trivial (a box); the value is in **motion** and **behaviour**. Take the
three-property split (§1) and the rubber band (§3) even if you take nothing else.

---

## 1. Three properties, three owners — the rule that makes everything else possible

The moment more than one thing can move a box, they fight over `transform`. The fix is not to
serialise them; it is to give each a property of its own.

```css
#pill {
  /* Where it stands: flights, births, anchor changes. Transitioned. */
  translate: var(--fly, 0px) 0;
  /* The finger: written per frame, and therefore NEVER given a transition. */
  transform: translate(var(--drag-x, 0px), var(--drag-y, 0px));
  /* The one thing with a duration of its own: the press, the hold swell, the pop. */
  scale: var(--pop, 1);

  transition:
    translate var(--fly-ms, 420ms) var(--ease-split),
    scale 320ms var(--ease-split),
    width var(--grow-ms) var(--ease-grow),
    height var(--grow-ms) var(--ease-grow);
}
```

The individual `translate` / `rotate` / `scale` properties compose in that fixed order and then
`transform` is applied innermost. So:

- A value written in `transform` is measured **in the element's own rotated/scaled frame**.
- A value written in `translate` is measured in the **parent's** frame.

That distinction bites the moment anything is rotated. A glyph counter-rotated inside a turned
control must put its travel in `translate` and its counter-rotation in `transform`, or it walks
sideways out of the thing it is riding in.

**Anything written per frame gets no transition.** A per-frame value under an overshooting easing is
the element bouncing to somewhere it was never sent.

---

## 2. The press, and only one press

```css
#pill.pressing { scale: 1.03; }
```

**1.03 is the press response everywhere.** A press is one gesture and must not change meaning with
where on the screen it lands — an emphatic press in one corner and a subtle one in another is two
languages. The *hold* is a different thing and goes further (1.14), because a press is an
acknowledgement and a hold is a state on its way to opening something.

---

## 3. The rubber band — being an object

This is not a gesture and nothing is ever asked for by it. The finger is followed in every
direction on a band that gives less the further it goes, and the box springs home when let go.

```js
const DRAG_RADIUS = 40;   // how far it may ever be pulled, however hard
const HOLD_BLOCK  = 18;   // the radius every gesture must leave before it is a gesture at all

/**
 * A hyperbola: the first pixels are nearly free and the last are nearly immovable, and the
 * box never leaves the circle however hard it is pulled.
 */
function rubberBand(travel) {
  const past = Math.max(0, travel - HOLD_BLOCK);
  return DRAG_RADIUS * past / (past + DRAG_RADIUS);
}

/** The direction is the finger's, the distance is the band's. */
export function toy(element, prefix, dx, dy) {
  const travel = Math.hypot(dx, dy);
  const reach = rubberBand(travel);
  const scale = travel > 0 ? reach / travel : 0;
  if (travel > HOLD_BLOCK) endHold();
  cancelSpring(element);
  element.style.setProperty(prefix + '-x', (dx * scale).toFixed(2) + 'px');
  element.style.setProperty(prefix + '-y', (dy * scale).toFixed(2) + 'px');
}
```

**One block radius, not four.** A finger pressing for a third of a second wanders, and if every
pixel of that wander reaches a different gesture with a different threshold, the press fails
*differently depending on which way the hand happened to drift*. One number, subtracted from the
travel rather than compared against it, so the band still starts from zero once the finger is
genuinely going somewhere. Inside the block the hold has priority over everything.

**The band scales down, it does not switch off.** Inside a scrollable panel the radius drops to 15%
of resting — an 85% cut, not zero, so the box still answers a touch rather than reading as dead. The
hyperbola takes its whole shape from that one number, so a smaller radius already gives both less
reach and less give per pixel; no second "intensity" constant is needed.

---

## 4. The way home is an *added* animation, never a class

```js
const HOME_MILLISECONDS = 520;
const HOME_EASE = 'cubic-bezier(0.2, 1.7, 0.35, 1)';

function springHome(element, from) {
  cancelSpring(element);
  const spring = element.animate(
    { transform: [from, 'translate(0px, 0px) scale(1)'] },
    { duration: HOME_MILLISECONDS, easing: HOME_EASE, composite: 'add' },
  );
  springs.set(element, spring);
}

/** The offsets are REMOVED, not written back as 0px. */
export function untoy(element, prefix) {
  const x = element.style.getPropertyValue(prefix + '-x');
  const y = element.style.getPropertyValue(prefix + '-y');
  if (!x && !y) return;
  element.style.removeProperty(prefix + '-x');
  element.style.removeProperty(prefix + '-y');
  springHome(element, `translate(${x || '0px'}, ${y || '0px'})`);
}
```

Two traps, both of which produced a bug that only appeared after the *first* gesture of a session:

- **A `homing` class that transitions `transform` outlives the gesture that added it**, and every
  later per-frame write then runs through a 1.7-overshoot easing. `composite: 'add'` composes onto
  whatever `transform` already says, ends by itself, and takes nothing over.
- **A custom property left standing at `0px` is still an answer** to "is this being dragged".
  Remove it.

---

## 5. Hold to open

The hold grows the box under the finger so the pressure is visible before the haptic confirms it.

```css
#pill.holding { scale: var(--hold-now, 1.14); }
```

- **Grown from its own top edge**, not its middle, wherever a screen edge is close — scaling from
  the centre pushes it under whatever clips that edge.
- It is `scale` **and nothing else** — no `transform`, no `translate` — so the swell composes with a
  drag rather than replacing it.
- It runs *underneath* whatever else the finger has started, and if the finger is still down when
  the timer comes up, the hold **takes** the touch and the others are let go of without being asked
  what they wanted. A trade committing behind a panel that is opening over it is two answers to one
  finger.
- It outranks the others up to the moment one of them is *earned* — the block radius — and not one
  pixel past it. A finger that crosses the block has said what it wanted and has felt the box say
  so, so the hold ends at the crossing rather than counting on underneath a gesture that visibly
  happened.
- **A bare box with nothing behind it has no hold at all.** Arming one that then quietly does
  nothing spends the swell, the window growth and a third of a second of the touch on a gesture
  that was never going to happen. Not armed, it answers a press with the band and nothing else.

---

## 6. Push — the flick-away

Dismiss on an upward flick. **It fires once per touch, latched.**

```js
if (!dismissed && dy < -PUSH_DISTANCE) { dismissed = true; dismiss(); }
```

A `touchmove` is a stream. Unlatched, every frame the finger stayed above the line fired the dismiss
again — which is the whole of "it triggers multiple times until it breaks".

---

## 7. Growing open

**Never resize the drawing surface to animate a size.** Resizing a canvas/WebView/window
reallocates its surface, which reads as the interface blinking out for a frame. The surface is a
fixed stage; something else clips it.

**Ask for the room before the animation, never during it.** A container that grows mid-flight clips
the flight it was meant to make room for. And a bouncy curve overshoots, so the room asked for is
the overshoot's, not the target's — `overflow: visible` on anything that is a bubble, and a face
that genuinely must clip does it on an inner element.

**Closing is the same rule read backwards.** Hold the content off entirely while the box gives its
width back, and only then let what belongs there arrive. Painted on the tap instead, the content is
simply *there*, at full open width, before anything has begun to shrink: it reads as having been
deleted and the box as merely resized afterwards. That is the whole of "tapping an open thing does
not animate back".

---

## Anti-patterns

- **More than one thing writing `transform`.** §1.
- **A transition on a per-frame property.** §1.
- **A different press scale in different places.** §2.
- **A separate threshold per gesture.** §3.
- **A class-based springback.** §4.
- **A custom property reset to `0px` instead of removed.** §4.
- **Arming a hold that resolves to nothing.** §5.
- **An unlatched flick.** §6.
- **Resizing the surface to animate a size.** §7.
- **Asking for room during the animation that needs it.** §7.
- **Painting the closed content before the box has shrunk.** §7.
