# Module 05 — Motion

The longest module, because motion *is* the identity. Palette and type make it recognisable in a
screenshot; motion is what makes it recognisable in use. Everything here is platform-independent —
the CSS/GSAP examples are the fastest way to demonstrate it, not the point of it.

---

## 1. Intent — what motion is for

Motion in this system carries meaning. Every animation answers one of exactly four questions, and an
animation that answers none of them is deleted rather than tuned.

| Intent | The question it answers | Character |
|---|---|---|
| **Arrival** | "What is this, and where did it come from?" | Confident, overshooting, staggered. Takes its time. |
| **Departure** | "That is gone, stop looking at it." | Fast, decisive, no delay, no stagger. |
| **Life** | "This thing is running / is alive / is waiting for you." | Endless, small-amplitude, unnoticeable until you look. |
| **Consequence** | "Something you did just happened." | Immediate, single-shot, tied to the input. |

Ambience without one of these is decoration, and decoration is the first thing dropped in reduced
modes (§9). If you cannot name the intent of a tween, it does not ship.

### The governing metaphor

**A screen change is a menu transition, not a page load.** Screens do not fade into each other, do
not scroll past each other, and are never unmounted. They are *swapped behind a curtain*, like a
console game moving between menu pages. The visitor is operating a machine, and the machine responds
mechanically: it snaps, it sweeps, it wipes, it never dissolves.

---

## 2. The three-phase law

**Every animated thing has a start, may have a middle, and must have an end.** This is the single
most important rule in the system, and the one most often broken by an addition.

```
        ENTER                    IDLE                     LEAVE
   (required, always)     (optional, endless)      (required, always)
   arrival choreography   life / ambience          departure choreography
   delayed, slow, staggered   low amplitude, loops     immediate, fast, uniform
```

**Enter (required).** How the element arrives. Delayed by the enter gate, eased out, staggered
against its siblings, entering from a defined off-screen origin.

**Idle (optional).** What it does while it is on screen and no one is touching it. Drift, bob, pulse,
caret blink, rotation. Endless, `yoyo: true, repeat: -1`, `sine.inOut`, amplitude small enough that
you notice it only in peripheral vision. **The idle loop is paused when its screen is not the active
one** — an off-screen loop is a composited layer held forever for nothing.

**Leave (required).** How it goes. The exact counterpart of the enter, but never its reverse (§4).

### The pairing rule, stated three ways

1. Every element animated on enter **must** be animated on leave, and vice versa.
2. Registering a new animated element means registering it in **every** motion path the app has —
   full, reduced, and any future one. A missing branch strands content on screen exactly like a
   missing leave animation.
3. A reveal with no exit is a bug report, not a style choice. It leaves content sitting on the screen
   during the swap and breaks the machine feel instantly.

The cheapest way to satisfy this by construction is a *pair* — one function that hides, one that
reveals, defined next to each other, with the leave literally derived from the enter's target state.

---

## 3. Timing — the numbers

These constants are the house tempo. Copy them; do not re-derive them per project.

| Constant | Value | Meaning |
|---|---|---|
| `ENTER_DELAY` | `0.5s` | How long an incoming screen waits before anything of it moves. |
| Enter duration — UI | `0.35–0.6s` | Panels, labels, controls. |
| Enter duration — backgrounds | `0.45–0.95s` | Slices, large layers. |
| **Leave duration** | `0.21–0.5s` | Always noticeably shorter than the matching enter. |
| Multi-layer offset | `0.1s` | Between back and front layers of one element group. |
| Sibling stagger | `0.03–0.12s` | Between UI elements in a row/grid. |
| Ambient stagger | `each: 0.15–0.2, from: 'random'` | Three or more decorative elements. |
| Idle drift period | `~3s`, amplitude `~20px` | With a random per-element direction and a `0–2s` random delay. |
| Curtain close | `0.42s` | Bars sweep shut. |
| Curtain hold | closed until `~0.92s` | The window in which the swap happens. |
| Curtain open | `0.5s` | Bars sweep apart, revealing a settled screen. |
| Full cut | `~1.72s` | Close + hold + open. |
| Bar stagger | random in `[0, 0.3s]`, min gap `0.045s` | See §6. |
| Typewriter | `0.06s`/char typing, `0.03s`/char deleting, `0.5s` caret blink | Deletion always reads faster than typing. |
| Label bar grow | `0.42s` | |
| Label bar retract | `0.5s` | |
| Label leave | `0.3s` | |
| Hover / state transitions | `0.2–0.25s ease` | CSS, not the animation engine. |

**Why enter is slower than leave:** arrival is information the visitor has to read; departure is
information they are done with. Making them symmetric makes the app feel slow in exactly the place
where it should feel responsive.

---

## 4. Easing — and the asymmetry law

| Phase | Element | Ease |
|---|---|---|
| Enter | prominent / large | `back.out` — the overshoot is what makes it land with a snap |
| Enter | most UI | `power2.out` |
| Leave | backgrounds / large layers | `back.in` |
| Leave | UI content | `power2.in` / `power2.inOut` |
| Drift / float | position | `power4.inOut` |
| Drift / float | opacity | `power2.in` |
| Idle loop | anything | `sine.inOut` |
| Wipe / sweep | bars, clip-paths | `power3.inOut` |
| Slow dramatic entrance | one per app, at most | `1.6s power4.inOut` |

**Never `linear`** for an enter or leave. Linear is for a progress bar or a constant rotation, and
nothing else.

### The asymmetry law

> **Leave fires immediately at time `0`. Enter fires at `ENTER_DELAY`.**
> **A leave is never a reversed enter.**

Concretely:

- No `delay` on a leave animation, ever. Exits are instant.
- No positional stagger on a leave — the screen clears **as one**, even when it arrived in sequence.
- The leave ease is the `in` counterpart of the enter's `out` ease, but the *duration and
  choreography differ*. `timeline.reverse()` is not the leave animation.

The reason: a staggered exit means the visitor waits for content they have already dismissed. The
enter's stagger tells them how a screen is built; nothing needs to tell them how it is torn down.

---

## 5. Choreography — how a screen assembles

### Absolute positions, never chaining

```js
timeline.to(background, { ... }, 0.50)
timeline.to(panel,      { ... }, 0.55)
timeline.to(label,      { ... }, 0.60)
```

Absolute time positions (`tl.to(el, {...}, 0.55)`), **not** `>` sequential chaining. Two reasons:
precise overlap control, and predictable behaviour when a transition is interrupted mid-flight —
which, in a menu you can navigate fast, happens constantly. Stagger by writing the start times out
(`0.55`, `0.60`, `0.65`) rather than leaning on a `stagger:` shorthand for anything structural.

### Direction has meaning

- Backgrounds enter from **the side of the screen the screen belongs to** (left or right) in
  landscape, and from the **bottom** (`top: 100%`) in portrait, exiting upward.
- Content follows its screen's slice: if the background comes from the left, the content does too.
- Floating/ambient elements enter from **below**, with a larger offset than interactive elements —
  they are further away, so they travel further.
- **Stagger direction matches travel direction.** Elements further from the viewport edge the motion
  comes from arrive later. Never the reverse; a reversed stagger reads as elements racing past each
  other.

### The positional stagger (the diagonal assembly)

The house signature for text. Each element's delay is a single scalar derived from its distance from
the top-left corner:

```js
const yFraction = rect.top  / viewportHeight   // 0 at top    → ~1 at bottom
const xFraction = rect.left / viewportWidth    // 0 at left   → ~1 at right
const delay = startAt + yFraction * 1.1 + xFraction * 0.4
```

Vertical position dominates (bottom elements clearly start later); horizontal position breaks the tie
between two elements on the same row, so the more left-aligned one always fires first. The screen
assembles diagonally, top-left to bottom-right, like a display powering on.

**Two elements at the same height but different `x` are never simultaneous.** Perfectly synced
reveals across different positions are the thing this exists to prevent.

On leave, the positional stagger is **not** re-run (asymmetry law).

---

## 6. The named patterns

These are reusable choreographies, each with a fixed identity. Reach for one before inventing motion.

### 6.1 The bar-sweep reveal (default for all text)

Every text element — headings, labels, captions, static copy — uses this unless explicitly told
otherwise. A plain fade or slide on text is the *exception*, not the default.

**Structure:** `.pc-label > .pc-label-inner > (.pc-label-text, .pc-label-bar)`
- the text starts hidden: `clip-path: inset(0 100% 0 0)`
- the bar is a solid glowing rectangle the size of the text, at `scaleX(0)`, origin `left center`

**Sequence (one timeline per element):**

1. Bar grows left→right, `scaleX 0→1`, `0.42s`, `power3.inOut`.
2. The instant it is full, the text's clip-path is set to fully revealed **underneath** the opaque bar.
3. `transform-origin` flips to `right center`.
4. Bar retracts to `scaleX(0)`, `0.5s`, `power3.inOut` — uncovering the text as it goes.
5. Bar opacity → `0` once collapsed.

The text is never seen fading in. It is *wiped into existence* by a bar that was already there. Start
delays come from the positional stagger (§5).

**Leave:** text re-clips to `inset(0 100% 0 0)` in `0.3s` `power2.in`, bar opacity to `0`. No stagger,
no bar sweep — the reveal is a construction, the exit is a cut.

**Without an animation engine** (a small surface, an embedded WebView, anywhere GSAP is more weight
than the whole feature) the sweep is one keyframe track on the bar plus a zero-duration clip flip on
the text. The `transform-origin` flip happens between two keyframes a hundredth apart — `45.6%` and
`45.7%` of a `0.92s` track — because an origin change *inside* a running transform is what makes the
bar retract from the far edge instead of jumping. The text's clip-path is switched at
`delay + 0.42s`, under the opaque bar, with a `0s` animation rather than a transition, so it can never
be caught mid-reveal. The positional stagger becomes a custom property (`--reveal-delay`) written by
one script pass over `getBoundingClientRect()`; CSS reads it as `animation-delay`.

### 6.2 The curtain (screen cut)

A stack of 6 bars sweeps closed to mask the moment one screen is swapped for the next, holds while
the incoming screen's name flashes on it, then sweeps open onto a screen that has already settled.

- Bars close `scaleX 0→1` with **alternating origins**, `0.42s`.
- Per-bar start offsets are **random** in `[0, 0.3s]`, each at least `0.045s` from every other. Random
  because a fixed cadence reads mechanical in a bad way; the minimum gap because two offsets landing
  within a few milliseconds makes the bars read as lockstep and defeats the randomisation. The offsets
  are used in element order, not sorted, so the *sweep direction* is randomised too.
- While closed (`~0.46–0.86s`), the incoming screen's kicker + name are shown on the curtain.
- Bars open at `0.92s` over `0.5s`.
- The curtain is **purely an overlay** — it does not drive state. The per-screen enter/leave
  animations still run underneath; the cut is inserted *between* them. Leave fires at `0`, the curtain
  is shut by `0.42s`, and the enter waits out `ENTER_DELAY` so the incoming screen settles behind it.
- The axis follows the layout: `scaleX` in landscape, `scaleY` in portrait (columns closing from top
  and bottom). Timing and staggers are **shared, never forked**, between layouts.

The accent text on the curtain picks black or white by luminance
(`r*0.299 + g*0.587 + b*0.114 > 0.5`) and desaturates the accent toward its own grey when it needs to
recede — computed, never hand-picked.

### 6.3 The slice background

Per-screen slanted `clip-path` layers, entering with the screen and leaving with it. Back layer at
`ENTER_DELAY`, front layer at `ENTER_DELAY + 0.1s`, `0.45s`, `back.out` in, `back.in` out. Hidden and
revealed positions are the fixed pairs from Module 03. Every screen must have one.

### 6.4 The drift (idle)

```js
gsap.fromTo(element, { y: 0 }, {
  y: amplitude * randomDirection,   // amplitude ~20, direction ±1 per element
  duration: 3,
  delay: delay + Math.random() * 2, // desynchronised, so the group never pulses together
  ease: 'sine.inOut',
  yoyo: true, repeat: -1,
})
```

Randomised direction and delay per element are the whole trick — a synchronised group of drifting
elements reads as a single moving object. **Paused whenever its screen is inactive.**

### 6.5 The typewriter

Characters appear one at a time behind a blinking caret bar. `0.06s`/char typing (~16 chars/s),
`0.03s`/char deleting — deletion always reads faster than typing. Caret blinks at `0.5s`, and by
default keeps blinking after the line finishes. Optionally deletes **from the front** of the line,
holding the text's right edge still, so the caret stays put and blinks rather than travelling
backwards with the characters.

### 6.6 Haptic punctuation

A `10ms` vibration on the completion of a screen transition, where the platform offers it. Motion
that ends on a physical device should be felt ending.

---

## 7. Engine discipline

Platform-specific in expression, universal in substance.

- **Transform and opacity only.** Layout properties are never animated.
- **Persistent targets.** Screens are never unmounted — the animation engine needs stable objects to
  address. Never animate something that can be conditionally removed from the tree.
- **`overwrite: 'auto'`** on anything whose enter and leave can race. In a menu you can navigate
  faster than the animation runs, they always can.
- **Kill before you build.** Every choreography starts by killing existing tweens on its targets.
- **Initial state is set inside the responsive callback**, so it re-runs on a layout change. Setting
  it once at module scope leaves a stale hidden state when the layout flips.
- **Return the cleanup function** from every responsive/scoped animation context.
- **Animating a variable requires a consumer.** Tweening a custom property that no rule reads is
  silent dead code: the tween runs, the value updates, nothing moves, and there is no error. This
  project has a CI check for exactly that (`npm run check:css-vars`) — worth reproducing anywhere a
  design token can be animated.
- **Never leave a declarative transition on a property the engine also animates.** The transition sits
  in front of every frame the tween writes and smears it. Move the transition onto the state that
  needs it (`:hover`, `.is-open`), never the base rule.
- **`immediateRender: false` as a global default**, so a `from` tween does not flash its start state.
- Interruptible-first: a transition class that toggles off transitions during a drag, restoring them
  on complete *and on interrupt*, because a cancelled transition that never restores is a dead panel.

---

## 8. Interaction motion

- Hover: `0.2–0.25s ease` on `border-color`, `color`, `opacity` only. Declarative transitions, not
  timelines — hover is a state, not a choreography.
- Press: the OS highlight is removed globally, so **every pressable thing owns its own press
  feedback**. If you removed the platform's affordance, you owe it a replacement.
- Pointer-tracked effects (magnetism, the border glow) are **gated on pointer type**, never on
  layout — a rotated monitor still has a mouse.
- Drag/scroll motion: one gesture owns one axis, locked once per gesture. A component that wants a
  stroke on an axis another gesture owns must **give up the stroke**, not compete for it.

---

## 9. Degradation — three independent settings, one precedence function

Not one "reduce motion" switch. Three, deliberately separate, because they answer different
complaints:

| Setting | What it targets | Why it exists |
|---|---|---|
| **Lite mode** | Site-wide motion and paint cost | The visitor's hardware cannot afford the full thing. |
| **Instant cuts** | The screen-swap curtain *only* | The visitor wants the full experience but not the wait between screens. |
| `prefers-reduced-motion` | Endless animation, all transitions | The visitor's OS says so. |

**The precedence rule lives in exactly one function**, and everything reads it:

```
transitionsAreInstant = userChoseInstant OR liteMode OR prefersReducedMotion
```

Never let two settings compute this independently.

### The lite substitute pair

Lite mode is not "faster animations". It is **one substitute pair that every generic element falls
back to**, defined in a single module:

```js
HIDDEN  = { opacity: 0, y: 18 }
VISIBLE = { opacity: 1, y: 0 }
enter: 0.42s, stagger 0.05, power2.out, delayed by the enter gate, force3D: false
leave: 0.24s, no stagger, no delay, power2.in, force3D: false
```

Opacity plus a short 2D offset, nothing else. No scale (it redraws the element at a new size every
frame) and no 3D promotion — **without hardware acceleration a composited layer is a cost, not a
saving.** Components choose *which* elements fall back; the module decides *how* they move. Never
write a lite-only duration, ease or delay anywhere else.

`LITE_ENTER_GATE = leaveDuration + 0.12` — the outgoing screen must be fully gone before the incoming
one starts, because lite mode has no curtain to hide an overlap, and two screens animating against
each other is both the ugliest and the most expensive frame on the page.

**The correctness trap:** if you skip the curtain but keep the full `ENTER_DELAY`, the visitor watches
the incoming screen assemble in the open. Skipping the curtain and shortening the gate are one change,
not two.

### What lite drops, and what survives

- **Dropped:** the curtain, cursor ambience, canvas decorations, floating drift, idle bobs, per-face
  builds, physics, parallax, per-word glitches, the pointer glow. CSS-side, a root class kills
  `backdrop-filter`, `box-shadow`, `text-shadow` and `will-change` wholesale.
- **Kept, simplified:** every *unique* animation that carries meaning — the one that makes a screen
  that screen. What goes is the ambience *around* it.
- **Kept in full:** the screen slices (every screen still needs its background) and the loading
  sequence (it runs before any choice has been made).
- **Labels have no lite branch to write** — the shared reveal functions route to the lite pair
  themselves, dropping the bar sweep and the positional stagger. The stagger trailed ~2.4s behind the
  swap and the clip-path was the priciest paint on the page.

### Reduced motion

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation: none !important; transition: none !important; scroll-behavior: auto !important;
  }
}
```

Declared once globally, never in a shared module a component imports. Idle drifts additionally
early-return under reduced motion rather than being time-scaled — under a time scale a drift *shakes*
instead of drifting, which is worse than not moving at all.

---

## 10. Writing a new animation — the checklist

1. **Name the intent.** Arrival, departure, life, or consequence. No name, no animation.
2. **Write the enter and the leave in the same commit**, next to each other.
3. **Decide whether it has an idle**, and if so, pause it when the screen is inactive.
4. **Pick a named pattern first** (§6). Only choreograph from scratch if none fits.
5. **Take timings from the table** (§3). Leave shorter than enter, always.
6. **Ease `out` in, `in` out.** Never `linear`. `back.*` for anything big.
7. **Absolute time positions.** No `>` chaining, no `delay` on the leave.
8. **Define the off-screen origin** from the direction rules (§5), and match the units the layout uses
   — a `px` offset where the layout is in `%` puts the hidden position somewhere different on every
   viewport.
9. **Kill existing tweens on the targets**, set `overwrite: 'auto'`.
10. **Register the lite branch and any other motion path.**
11. **Confirm every animated variable is actually read somewhere.**
12. **Check nothing declarative is transitioning the same property.**

## Anti-patterns

Symmetric enter/leave. `timeline.reverse()` as a leave. A delayed exit. A staggered exit. `linear`
easing on a transition. `>` chaining in a screen transition. Fade-only screen changes. Scroll-driven
motion. Endless loops running off-screen. Animating layout properties. An animation with no named
intent. A new animated element registered in the full path but not the reduced one.
