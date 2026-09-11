# Module 06 — Interaction and input

The interface is a machine you operate, not a document you browse. Input is the largest part of that
feeling, and most of it is decided by the *pointer*, not the screen shape.

## Navigation is stateful, not scrolled

There is no visual scrollbar and no document scroll. The wheel, the keyboard and the swipe all express
*intent* to change screen; a state machine decides what happens. Screens stay mounted; they are
entered and left.

- **Wheel:** one notch = one step, on a pointer device.
- **Swipe:** a drag past a threshold (~30% of viewport height) = one step, on a handheld.
- **Keyboard:** `W`/`S` step one screen; the number row jumps directly (`1` = the first registry
  entry). The digit range is derived from the registry length, so adding a screen extends it without
  a code change. Every key routes through the same change function as the wheel, so locks, skip rules
  and transitions apply for free — a locked screen simply ignores its digit.
- A backtick / slash / zero opens the terminal; a dedicated digit is the dev cache-wipe key.

**Every input path must funnel into one change function.** A key handler that manipulates state
directly is how a lock gets bypassed.

## The pointer decides, not the width

| Follows the **pointer** (`coarse` vs `fine`) | Follows the **orientation** |
|---|---|
| Nav shape (bottom row vs side column) | Which layout is rendered |
| Wheel-step vs drag-navigation | Arrangement within a screen |
| Hover affordances, cursor fields, magnetism | Transition axis (`scaleX` vs `scaleY`) |
| Touch drag initialisation | Slice geometry |
| Control hit sizes | |

Anything that is a *touch affordance* (bottom nav, swipe paging) or a *pointer affordance* (hover
lists, cursor fields) keys off the pointer. Anything about the shape of the screen keys off
orientation.

## Hover on touch is a bug waiting to happen

A touch screen fires `:hover` — and a synthetic `mouseenter`/`mousemove` — on tap, then leaves it
applied until something else is tapped. Every hover effect becomes a stuck state.

- **CSS:** wrap hover rules in `@include hoverable`. Keep the non-hover part of the rule (`&--open`,
  `.selected`) *outside* the mixin, or it disappears on touch too.
- **JS:** gate `mouseenter`/`mousemove` handlers on `!isMobileDevice`.
- **Deliberate exceptions** are allowed and should be listed explicitly in the project's rules, so
  they read as decisions rather than oversights. Here: the panel border glow, and one title's
  character interaction.
- **A hover-only affordance that is the only way to reach some content needs a real touch path, not a
  bigger tap target.** Give the trigger `tabindex="0"` and open on `:focus`, with the hover rule
  guarded by `hoverable` — a tap focuses it, a tap anywhere else blurs it closed, and it becomes
  keyboard-reachable for free. It must be `:focus` and not `:focus-visible`: a tap does not produce
  `:focus-visible`. The module's info card is the reference case (`components/11-module.md §5`).
- **A magnetic / pointer-following field is a hover effect and gets the same gate.** A touch screen
  synthesises one `mousemove` on tap and never the matching leave, so the field engages at the
  finger's position and stays there — the failure is louder than a stuck tint because the element
  has physically moved (`components/10-magnetic.md §1`). Every pointer-position listener also needs a
  `blur` handler: `mouseup` and `mouseleave` both fail to fire when the interaction ends outside the
  window.

## Touch equivalents are different interactions, not bigger hit areas

- **Hover becomes nothing, not tap.** A tap that only previews costs the visitor the one gesture they
  have.
- **Hold and drag become tap.** A drag-to-spin control becomes tap-to-activate seats on a phone,
  because a vertical stroke there already belongs to the screen step and a competing handler loses
  either way.
- **Only one gesture may own an axis.** The axis is locked once per gesture, centrally. Anything
  animating off a stroke *declares its region* (`data-scroll-region` vertical, `data-carousel-region`
  horizontal) rather than binding its own listener. A component that wants a stroke on the screen axis
  must give up that stroke instead.
- **`touch-action` states the intent to the browser too.** `pan-y` on a surface that only reacts to
  taps keeps native scrolling responsive; `none` is for pointer devices, where nothing else wants the
  stroke.

## The mobile address-bar problem (worth knowing anywhere with dynamic viewports)

A mobile browser only collapses its address bar for a genuine document scroll, and this app never
gives it one. So the handheld gets a short runway of real scrollable height that the first upward
swipe of the session is handed, before gesture-claiming begins:

```scss
html.is-mobile-device body { min-height: calc(100svh + 12vh); }
html.is-mobile-device.runway-spent body { min-height: 100svh; }
```

`svh`, not `dvh` — the small viewport is the one with the bar still showing, so the runway is a
constant; a `dvh` term would shrink as the bar collapses and re-trigger the layout it is trying to
settle. And the runway must be *removed* once spent, not merely ignored: left in place, the browser
scrolls the document back to 0 whenever the bar returns.

Scrollable regions set `overscroll-behavior: contain` so a scroll never chains out into that runway.

## Step sliders are dots, and the dot is liquid

A control with a handful of discrete settings — brightness, intensity, a 1-to-5 anything — is never
a continuous track with a knob on it, and never a row of buttons. It is a rail of squircle slots
with one round dot travelling along it, and the dot **merges into the slot it reaches** rather than
lighting it up: the two run together like water, hold as one shape, and pull apart as the finger
moves on. The value is legible from across the room as *where the liquid is*, and dragging feels
like pushing a drop along a channel rather than scrubbing a number.

Two layers, one filter:

- **Rail** — every slot at full size, low opacity. The shape of what is possible.
- **Flow** — the dot, plus the same slots scaled by how near the dot is (`1 - |dot - index|`,
  floored at zero). This layer alone carries the goo filter.

```html
<filter id="goo" x="-30%" y="-30%" width="160%" height="160%">
  <feGaussianBlur in="SourceGraphic" stdDeviation="5" result="blurred"/>
  <feColorMatrix in="blurred" type="matrix"
    values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 22 -10"/>
</filter>
```

That is the whole metaball: one blur, then one alpha contrast steep enough to snap the blurred edges
back to hard ones. Shapes bridge at roughly **twice the deviation**, so the deviation and the gap
between slots are one decision and not two — five bridges at ten pixels, so the slots stand further
apart than that, or the rail fuses into a single bar and never separates again.

Rules that come with it:

- **Everything inside the flowing layer is solid.** A half-transparent shape falls below the
  contrast's threshold and is erased rather than merged. The transparency is worn by the layer,
  never by what is in it.
- **Size is the only variable.** A slot the dot has reached is full size, one a whole step away is
  nothing at all, and in between both are part-grown and the blur bridges them. Never fade a slot in
  and out — a fading shape dissolves under the contrast instead of flowing.
- **The dot follows the finger continuously and settles on release**, snapping to the nearest step.
  It is a slider, so it is dragged; stepping it by tapping is a different control.
- **The value is committed once, on release.** Sent every frame it makes hardware stutter — a light
  re-strikes, an audio scrub judders — and nobody can read a number changing sixty times a second.
- **Haptic on the settle, not on every step crossed.** One piece of feedback for one decision.
- The same material is used for every other merge in the interface: if two shapes on one surface are
  made of the same stuff and approach each other, they run together, with the same deviation and the
  same contrast. The product then reads as one body of water rather than as one clever widget.
  Merging happens within a single goo layer on a single surface — nothing bridges across two windows
  or two canvases.

## Feedback

- Every pressable thing owns its press feedback, because the OS tap highlight is removed globally.
- Completion of a screen transition fires a `10ms` vibration where available.
- Focus is always visible. `outline: none` is only ever acceptable with a replacement in the same rule.
- Destructive controls are always the danger colour, never the screen accent, and always require a
  second, differently-worded confirmation.

## Anti-patterns

Scroll-driven interfaces. Continuous tracks and knobs for a handful of discrete steps, and step
controls built as rows of buttons. Committing a slider's value on every frame of the drag rather
than on release. Half-transparent shapes inside a goo layer. Fading a step in and out instead of
growing it. Hover as the only route to information. A tap that merely previews. Two
handlers competing for one axis. Removing a platform affordance without replacing it. Key handlers
that bypass the central state change. Width-based decisions about touch.
