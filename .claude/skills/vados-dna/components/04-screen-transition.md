# Screen transition — slices, positional stagger, bar-sweep reveal

How one screen becomes another. Every screen change is a **menu transition**: it arrives slowly and
diagonally, and it leaves fast and all at once. Origin: PORTFOLIO25, ported unchanged into Dynamic
Bubble's control panel — the same implementation, not a second one that drifts.

**Layers:** almost entirely **motion**, with a small behaviour layer for the stagger and the change
gate. Liftable whole into anything with more than one screen.

---

## 1. Screens are never unmounted, only entered and left

```css
.screen {
  position: absolute;
  inset: 0;
  visibility: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
  touch-action: pan-y;
}
.screen.active, .screen.leaving { visibility: visible; }
```

An animation needs a live target, and a state change can arrive mid-flight. `visibility` rather than
`display` keeps the box measurable, which the stagger below depends on.

---

## 2. The diagonal slice

Every screen has one, parked off-canvas and animated in with the screen. **Nothing else in the app
uses a diagonal** — that is what makes it read as the transition rather than as decoration.

```css
.slice {
  position: fixed;
  top: 0; bottom: 0;
  width: 60%;
  left: -10%;
  background: var(--section-color);
  opacity: 0.06;
  clip-path: polygon(0 0, 100% 0, 62% 100%, 0 100%);
  pointer-events: none;
  /* Parked completely off its own edge, so the travel is a real slide across the glass
     rather than a nudge of an already-visible layer. */
  --slice-hidden: -115%;
}
.slice.front { opacity: 0.04; clip-path: polygon(0 0, 78% 0, 40% 100%, 0 100%); }

/* A screen that belongs to the right-hand side gets its slices from there — direction
   carries which way you stepped. */
.screen[data-side="right"] .slice {
  left: auto; right: -10%;
  clip-path: polygon(0 0, 100% 0, 100% 100%, 38% 100%);
  --slice-hidden: 115%;
}

.screen.active  .slice       { animation: slice-enter 0.5s var(--ease-enter) 0.06s both; }
.screen.active  .slice.front { animation-delay: 0.12s; }
.screen.leaving .slice       { animation: slice-leave 0.22s var(--ease-leave) both; }

@keyframes slice-enter { from { transform: translateX(var(--slice-hidden)); } to { transform: translateX(0); } }
@keyframes slice-leave { from { transform: translateX(0); } to { transform: translateX(var(--slice-hidden)); } }
```

Two slices at two opacities and two delays. One is a wipe; two at different rakes and speeds is
parallax, and parallax is what makes a flat colour read as depth.

---

## 3. The positional stagger

One scalar per element, derived from its distance to the top-left corner, so the screen **assembles
diagonally like a display powering on** rather than in DOM order.

```js
function applyPositionalStagger(screen) {
  for (const element of screen.querySelectorAll('.stagger')) {
    const box = element.getBoundingClientRect();
    const delay = 0.06
      + (box.top  / window.innerHeight) * 0.28
      + (box.left / window.innerWidth)  * 0.1;
    element.style.setProperty('--enter-at', delay.toFixed(2) + 's');
    element.style.setProperty('--reveal-delay', (delay + 0.1).toFixed(2) + 's');
  }
}
```

```css
.screen.active .stagger {
  animation: panel-enter 0.34s var(--ease-enter) var(--enter-at, 0.06s) both;
  transform-origin: center center;
}
.screen.leaving .stagger { animation: panel-leave 0.16s var(--ease-leave) both; }

@keyframes panel-enter { from { opacity: 0; transform: scale(0.92); } to { opacity: 1; transform: scale(1); } }
@keyframes panel-leave { from { opacity: 1; transform: scale(1); } to { opacity: 0; transform: scale(0.97); } }
```

**Vertical is weighted nearly 3× horizontal** (0.28 vs 0.1). A stagger weighted evenly reads as a
sweep from a corner; weighted down-heavy it reads as the screen filling.

**Enter overshoots out of its own centre; leave is immediate, uniform and faster** — 0.34s against
0.16s, and the leave carries **no stagger at all**. This is law 3 of the DNA: the end is never the
start reversed. A reversed enter reads as an undo; a fast uniform leave reads as a decision.

---

## 4. The bar-sweep reveal

Text is **wiped into existence by an accent bar that was already there**, never faded in.

```html
<h1 class="stagger"><span class="reveal">
  <span class="reveal-text">System</span><span class="reveal-bar"></span>
</span></h1>
```

```css
.reveal { position: relative; display: inline-block; }
.reveal-text {
  clip-path: inset(0 100% 0 0);
  animation: reveal-text 0s linear forwards;
  animation-delay: calc(var(--reveal-delay, 0s) + 0.283s);
}
.reveal-bar {
  position: absolute; inset: 0;
  background: var(--accent, var(--section-color));
  transform: scaleX(0);
  transform-origin: left center;
  animation: reveal-bar 0.62s var(--ease-sweep) forwards;
  animation-delay: var(--reveal-delay, 0s);
}
@keyframes reveal-text { to { clip-path: inset(0 0 0 0); } }
@keyframes reveal-bar {
  0%    { transform: scaleX(0); transform-origin: left  center; opacity: 1; }
  45.6% { transform: scaleX(1); transform-origin: left  center; opacity: 1; }
  45.7% { transform: scaleX(1); transform-origin: right center; opacity: 1; }
  99%   { transform: scaleX(0); transform-origin: right center; opacity: 1; }
  100%  { transform: scaleX(0); transform-origin: right center; opacity: 0; }
}
```

The origin flips at the exact midpoint, which is what turns two scales into one bar travelling
across and off. The text's `clip-path` snaps open (`0s` duration) at 0.283s — behind the bar, so the
bar is what appears to deposit it.

The two delays come from the same stagger scalar, `+0.1s`, so the reveal is part of the assembly
rather than a second animation with its own idea of time.

---

## 5. The change gate

```js
const LEAVE_DURATION = 170;   // mirrors the leave animation above
let activeIndex = 0;
let isChanging = false;

/** Every input path funnels through here, so nothing can bypass a running change. */
function changeScreen(nextIndex) {
  if (isChanging || nextIndex === activeIndex) return;
  if (nextIndex < 0 || nextIndex >= screens.length) return;
  isChanging = true;

  const leaving = screens[activeIndex];
  const entering = screens[nextIndex];
  leaving.classList.remove('active');
  leaving.classList.add('leaving');

  setTimeout(() => {
    leaving.classList.remove('leaving');
    activeIndex = nextIndex;
    // The accent is repointed while nothing of either screen is on the glass, so the room
    // changes colour between shots rather than during one.
    document.documentElement.style.setProperty('--section-color', entering.dataset.accent);
    entering.classList.add('active');
    entering.scrollTop = 0;
    requestAnimationFrame(() => { entering.scrollTop = 0; });
    applyPositionalStagger(entering);
    isChanging = false;
  }, LEAVE_DURATION);
}
```

Three things worth keeping:

- **One funnel.** Taps, keys and gestures all call this, so no input can start a change on top of a
  running one.
- **The accent changes in the gap**, while neither screen is visible. A colour that crossfades under
  a moving screen reads as a bug.
- **Scroll is reset twice** — once on the class flip and once on the next frame — because the engine
  restores a remembered scroll position for a box that becomes visible.

**One accent per screen, declared on the screen** (`data-accent`), inherited by everything in it. A
leaf never names a colour.

---

## 6. Reduced motion

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation: none !important; transition: none !important; }
  .reveal-text { clip-path: inset(0 0 0 0); }
  .reveal-bar  { display: none; }
}
```

Killing the animations is not enough on its own: anything whose *resting* state is the hidden one —
a clipped reveal, a parked slice — has to be given its finished state explicitly, or reduced motion
means an empty screen.

---

## Anti-patterns

- **A leave that is the enter reversed.** Law 3.
- **A staggered leave.** The stagger is assembly; leaving is a decision.
- **Fading text in.** Wipe it with the bar.
- **DOM-order stagger.** It reads as a list; positional reads as a screen.
- **An accent that crossfades during the transition.** Change it in the gap.
- **`display: none` on an inactive screen.** Unmeasurable, so the stagger cannot size it.
- **A second input path that bypasses the change gate.**
- **Reduced motion that only removes animations** without restoring hidden resting states.
