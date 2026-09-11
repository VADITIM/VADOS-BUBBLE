# The module — the universal panel

The one box every piece of content in the app sits in. A bordered, translucent surface with a
micro-label pinned to its top-left corner, an accent hue ring that tracks the pointer around its
border, an optional caption hanging off its bottom edge, an optional info affordance in the corner
opposite the label, and a height that tweens rather than snaps when its content changes shape.
Origin: PORTFOLIO25 (`src/components/Misc/Module.vue`).

**Layers:** all four lift independently. **Styling** alone is the DNA's surface language and is what
`components/03-panel-and-field.md §1` reduces to. **Motion** is the hue ring and the height tween.
**Behaviour** is two pointer listeners and a `ResizeObserver`. The **layout** layer — a column flex
with a padded content well clearing the label — is what makes it drop-in.

`components/03-panel-and-field.md §1` is the **minimum** version, for a project that wants the look
and nothing else. **This file is the canonical component**; where they disagree, this one is the
source.

---

## 1. The anatomy

```html
<div class="module" :style="[accent ? { '--accent': accent } : {}]">
  <div class="module-hue"></div>
  <div class="module-label" :class="{ 'module-label--over': labelOver }">
    <slot name="label">{{ label }}</slot>
  </div>
  <div v-if="$slots.info" class="module-info" tabindex="0" role="note" :aria-label="infoTitle || 'More information'">
    <span class="module-info-mark" aria-hidden="true">i</span>
    <div class="module-info-card">
      <div v-if="infoTitle" class="module-info-title">{{ infoTitle }}</div>
      <slot name="info" />
    </div>
  </div>
  <div class="module-content"><slot /></div>
  <div v-if="caption" class="module-caption">{{ caption }}</div>
</div>
```

Four fixed positions, and every one of them means something:

| Position | Element | What it says |
|---|---|---|
| top-left | the micro-label | **what this box is** — a name, uppercase, never a sentence |
| top-right | the info mark | **what this box means** — opposite the label, so the corners never compete |
| centre | the content well | the box's actual job |
| bottom-centre | the caption | **how to use it** — a full sentence, half-overhanging the border |

The label is `position: absolute`, not the first row of a flex column, **so a consumer can override
the box's own padding without moving it**. A module whose content needs to bleed to the edge (a
canvas, a grid cell, a cube) can zero `.module-content`'s padding and the label stays exactly where
every other module in the app puts it.

---

## 2. The surface

```scss
.module {
  position: relative;
  // Follows the screen it is rendered in unless the consumer names a colour; the app root keeps
  // --section-color pointed at the active screen's accent.
  --accent: var(--section-color, #5bfd5b);
  border: 1px solid #262626;
  border-radius: 0.75rem;
  background: rgba(18, 18, 18, 0.85);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  opacity: 0;                        // §6
  will-change: transform, opacity;
  transition: border-color 0.25s ease;

  &:hover {
    border-color: color-mix(in srgb, var(--accent) 30%, #262626);
  }
}
```

**Depth is a 1px hairline and translucency. Never a shadow** — law 1 (`dna/03-surface.md`).

The `--accent` declaration is the inheritance mechanism in one line: the module re-declares `--accent`
from `--section-color` on itself, so every descendant reads `var(--accent)` and no leaf component
ever names a colour. The `accent` prop overrides it for the rare box that belongs to a thing rather
than to a screen (the cubes in `components/12-cube-3d.md` are the reference case).

**The hover is deliberately *not* wrapped in the `hoverable` guard**, and it is one of only two such
exceptions in the app. Everywhere else an unwrapped `:hover` sticks after a tap and reads as broken —
here the tint reads as *"this is the panel you just touched"*, which is true and useful. An exception
to that rule needs to be argued at the call site, in a comment, every time.

---

## 3. The hue ring

An accent glow masked to the border ring only. The whole ring lights when the pointer is over the
box, and the gradient keeps a bright hot spot at the pointer.

```scss
.module-hue {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  padding: 1.5px;                 // the ring's thickness — the mask below hollows out the rest
  pointer-events: none;
  z-index: 4;
  background: radial-gradient(
    11rem circle at var(--mx, 50%) var(--my, 50%),
    color-mix(in srgb, var(--accent) 95%, transparent),
    color-mix(in srgb, var(--accent) 50%, transparent) 45%,
    color-mix(in srgb, var(--accent) 20%, transparent) 100%
  );
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
          mask-composite: exclude;
  opacity: 0;
  transition: opacity 0.25s ease;
  will-change: opacity;
}
```

**The two-layer mask is the whole technique.** One layer covers the padding box, one covers the
content box, and `exclude` leaves only the difference — a ring exactly `padding` thick, following
`border-radius: inherit` around the corners. There is no second element, no SVG, and no border-image.

The final stop at 20% rather than 0% is what stops the ring going dark on the far side: the whole
border is lit, the pointer just has the hottest point on it. A gradient falling to transparent turns
the ring into a moving arc, which reads as a spotlight rather than as a charged edge.

```ts
// The hue ring is hidden outright in lite mode, so tracking the pointer for it would be per-move
// work for nothing.
const glow = isLiteMode.value ? null : element.querySelector<HTMLElement>('.module-hue');
if (glow) {
  const onMove = (event: MouseEvent) => {
    const bounds = element.getBoundingClientRect();
    glow.style.setProperty('--mx', `${event.clientX - bounds.left}px`);
    glow.style.setProperty('--my', `${event.clientY - bounds.top}px`);
    glow.style.opacity = '1';
  };
  const onLeave = () => { glow.style.opacity = '0'; };
  element.addEventListener('mousemove', onMove);
  element.addEventListener('mouseleave', onLeave);
}
```

Listeners are on the module, not the window — the browser's hit-testing already scopes them. The lite
check happens **once at mount** rather than inside the handler: a mode that cannot change without a
reload is read once, not per pointer move.

---

## 4. The label and the caption

```scss
// Predetermined top-left position; absolute so it never shifts when a consumer overrides padding.
.module-label {
  position: absolute; top: 0; left: 0; z-index: 5;
  font-family: 'Mono';
  font-size: 0.63rem;
  font-weight: 500;
  letter-spacing: 0.19rem;
  color: #8a8a8a;
  padding: 0.81rem 1rem;
  pointer-events: none;

  // Module chrome is shared by every screen, so the vertical layout's type scale belongs HERE
  // rather than being re-declared per consumer.
  @include verticalLayout {
    font-size: clamp(0.688rem, 3vw, 1.063rem);
    letter-spacing: 0.35vw;
    padding: clamp(0.625rem, 2.6vw, 1rem) clamp(0.75rem, 3vw, 1.25rem);
  }

  &--over { z-index: 6; }   // label floats above overlay content
}

.module-caption {
  position: absolute;
  bottom: 0.75rem; left: 0; right: 0;
  transform: translateY(55%);        // half-overhangs the border, so it reads as attached, not inside
  text-align: center;
  font-family: 'Mono';
  font-size: 0.75rem;
  color: #4a4a4a;
  pointer-events: none;
  z-index: 4;

  @include verticalLayout {
    // Captions are full sentences, and the longest ones ran the full width of the box at 3vw.
    font-size: clamp(0.625rem, 2.5vw, 1rem);
    padding: 0 0.75rem;
  }
}
```

`0.19rem` of tracking on a `0.63rem` face is the signature (`dna/02-typography.md`). **The label
names; the caption explains.** Label uppercase and wordless; caption sentence-case and complete.

**Both portrait overrides live here and only here.** Every screen in the app renders modules, so a
consumer re-declaring the portrait label size is one more place for the scale to drift — and the app
has dozens of them.

```scss
.module-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;                       // without it the well refuses to shrink and overflows the box
  padding: 2.75rem 1rem 1rem;          // the top clears the absolutely-positioned label

  @include verticalLayout {
    padding: clamp(2.375rem, 9.5vw, 3.75rem) clamp(0.75rem, 3vw, 1.25rem) clamp(0.75rem, 3vw, 1.25rem);
  }
}
```

The top padding and the label's own size are **two numbers that must agree with no build step joining
them** — catalogue rule 4. Changing the label scale without changing the well's top padding is the
most likely regression in this file.

---

## 5. The info affordance

An `i` in the corner opposite the label, opening a card downward inside the box.

```scss
.module-info {
  position: absolute; top: 0.62rem; right: 0.75rem; z-index: 6;
  width: 1.5rem; height: 1.5rem;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid #3a3a3a;
  border-radius: 50%;
  color: #6a6a6a;
  cursor: help;
  transition: color 0.2s, border-color 0.2s;

  // The mark is a TOUCH TARGET in portrait, not a hover affordance — hence the much larger ramp.
  @include verticalLayout {
    top: clamp(0.5rem, 2.2vw, 0.875rem);
    right: clamp(0.625rem, 2.6vw, 1rem);
    width: clamp(1.5rem, 6.5vw, 2.5rem);
    height: clamp(1.5rem, 6.5vw, 2.5rem);
  }

  &:hover, &:focus-visible { color: var(--accent); border-color: var(--accent); outline: none; }
}

.module-info-card {
  position: absolute;
  top: calc(100% + 0.5rem);
  right: 0;
  width: min(31rem, 900vw);
  padding: 0.75rem 0.88rem;

  // A portrait box is a fraction of the width a landscape one has, and the module clips its
  // overflow — so the card is measured against the SCREEN, not against a desktop maximum it can
  // never reach here.
  @include verticalLayout {
    width: min(22rem, 68vw);
    padding: clamp(0.5rem, 2.4vw, 0.875rem) clamp(0.625rem, 2.8vw, 1rem);
  }

  background: #141414;
  border: 1px solid #2c2c2c;
  border-radius: 0.5rem;
  text-align: left;
  opacity: 0;
  transform: translateY(-6px);
  pointer-events: none;
  transition: opacity 0.2s ease, transform 0.2s ease;
}

// The card is the only way to read the info, so touch needs a real way in rather than the sticky
// :hover it used to get: the trigger is tabindex="0", so a tap focuses it and a tap anywhere else
// blurs it closed.
.module-info:focus .module-info-card { opacity: 1; transform: translateY(0); }

@include hoverable {
  .module-info:hover .module-info-card { opacity: 1; transform: translateY(0); }
}
```

**`:focus` for touch and a guarded `:hover` for pointers is the general answer to "a hover-only
affordance on a touch screen"** (`dna/06-interaction.md`). `tabindex="0"` also makes it keyboard
reachable, which the hover version never was. Note it is `:focus` and not `:focus-visible` — a tap
must open it, and a tap does not produce `:focus-visible`.

The card opens **downward and to the right edge**, inside a box that clips its overflow, so it can
never escape the module. That is a constraint on the copy: an info card is a paragraph or two.

Slotted body copy carries the consumer's style scope, not the module's, so it is reached with
`:deep`:

```scss
.module-info-card :deep(p) {
  margin: 0 0 0.5rem;
  font-family: 'Mono';
  font-size: 0.75rem;
  line-height: 1.6;
  color: #8a8a8a;
  &:last-child { margin-bottom: 0; }
}
```

The card's own title is the one place `Audiowide` appears in the module — it is a heading, and
headings are Audiowide (`dna/02-typography.md`). It is right-aligned to sit under the mark that
opened it.

---

## 6. `opacity: 0` by default

**The module ships hidden.** Screens are never unmounted, so a module is in the DOM long before its
screen is entered; the screen's own enter choreography is what fades it in. A module that defaulted
to visible would be on screen behind every other screen.

The escape hatch is a prop:

```
staticVisible   // skip the default opacity: 0 — for content that reveals itself internally
                // rather than via a container-level enter tween
```

Anything using the bar-sweep reveal (`components/08-text-reveal.md`) for its own contents is in that
category: the box is already there and the text arrives.

---

## 7. The height tween

Content that re-wraps — a description under a carousel, a value that grows a digit — would snap the
box to its new size. A `ResizeObserver` catches that and tweens instead.

```ts
previousHeight = element.offsetHeight;
resizeObserver = new ResizeObserver(() => {
  const newHeight = root.value.offsetHeight;

  // A WINDOW resize changes this box for a reason that has nothing to do with its content, and
  // tweening through it fights the layout: the inline height the tween writes becomes the next
  // measurement, so the box latches onto a mid-tween size and keeps re-chasing it. Re-baseline
  // instead, and drop any inline height a tween left behind.
  if (isViewportResizing.value) {
    gsap.killTweensOf(root.value);
    root.value.style.height = '';
    previousHeight = root.value.offsetHeight;
    return;
  }

  if (previousHeight === null) { previousHeight = newHeight; return; }
  if (Math.abs(newHeight - previousHeight) < 1) return;   // sub-pixel noise is not a change
  const from = previousHeight;
  previousHeight = newHeight;

  // Stop observing for the duration of the tween: every frame it writes is a resize this same
  // observer would otherwise see, and the feedback loop never terminates.
  resizeObserver?.unobserve(root.value);
  gsap.fromTo(root.value, { height: from }, {
    height: newHeight,
    duration: 0.45,
    ease: 'power3.out',
    overwrite: 'auto',
    onComplete: () => {
      root.value.style.height = '';        // hand the height back to the layout
      resizeObserver?.observe(root.value);
    },
  });
});
```

Three separate guards, each answering a real failure:

1. **Unobserve while tweening** — otherwise the observer observes its own animation and re-fires
   every frame.
2. **Re-baseline during a viewport resize** — otherwise the inline height the tween writes becomes
   the next measurement and the box chases a target it is itself creating.
3. **Clear the inline height on completion** — otherwise the box is pinned at a size the layout no
   longer owns, and the next genuine content change starts from a stale baseline.

This is **the one place in the DNA a layout property is animated**, against `dna/05-motion.md §7`. It
is allowed because the alternative — a `scale`, which the rest of the DNA reaches for — would leave
the layout box at its old size and be silently overwritten by any other tween touching `scale`. The
exception is worth stating precisely so it is not generalised: *height is animated only when the
content's natural height changed, only by the box that owns it, and only with the observer detached.*

It is opt-out (`animateHeight`, default on) and a no-op for a module whose height is pinned by a
parent grid track — those never report a size change here at all.

---

## 8. The props

```
label          string    the micro-label text (or use the `label` slot)
accent         string    overrides the live screen colour; leave unset so the box follows the screen
labelOver      boolean   label floats above overlay content
staticVisible  boolean   skip the default opacity: 0 (§6)
caption        string    the hint line pinned to the bottom centre
animateHeight  boolean   tween the box height on content change (default true, §7)
infoTitle      string    heading for the info card, and its aria-label
```

Plus `defineExpose({ get element() { return root.value } })` — a screen's choreography needs the real
element, and a getter rather than a captured ref so it is never stale.

**Every prop here is a switch on chrome, not on style.** There is no `variant`, no `size`, no
`color` beyond the accent override. A module that needs to look different overrides its own class
from the consumer's scope; a module that needs different *chrome* gets a prop.

---

## 9. Traps

**"The label jumped when I changed the box's padding."** It did not — some consumer made the label a
flow child instead of leaving it absolute (§1).

**"The ring is an arc that follows the cursor, not a lit border."** The gradient's final stop falls to
transparent instead of 20% (§3).

**"The ring fills the whole box."** The `mask-composite` pair is missing or unprefixed —
`-webkit-mask-composite: xor` and `mask-composite: exclude` are both needed (§3).

**"The label overlaps the content on a phone."** The portrait label ramp was changed without the
matching change to `.module-content`'s top padding (§4).

**"The info card is unreachable on touch."** Only `:hover` was wired. It needs `tabindex="0"` and a
`:focus` rule; `:focus-visible` is not enough (§5).

**"The box is visible behind every other screen."** `opacity: 0` was removed instead of
`staticVisible` being passed (§6).

**"The box grows a few pixels forever after a window resize."** The `isViewportResizing` re-baseline
is missing, so the tween's own inline height feeds the next measurement (§7).

**"The height tween never finishes."** The observer was not detached for the duration (§7).

**"The box overflows its content on a phone."** `min-height: 0` missing on the content well — a flex
child will not shrink below its content without it (§4).

---

## Anti-patterns

- A `box-shadow` for depth. The hairline and the translucency are the depth (§2, law 1).
- A leaf inside a module naming a colour instead of reading `var(--accent)` (§2).
- An unguarded `:hover` anywhere except the two argued exceptions (§2).
- Re-declaring the portrait label or caption scale in a consumer (§4).
- Animating `height` anywhere the three guards in §7 are not all present.
- Faking a module's size with `transform: scale()` — the layout box keeps its original size and any
  tween touching `scale` overwrites it silently (`components/12-cube-3d.md` §3 is the case that
  taught this).
- Adding a `variant` or `size` prop instead of styling from the consumer's scope (§8).
