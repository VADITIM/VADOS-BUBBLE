# Skeleton loader, and the slow toast

What is on screen while something is being fetched, and what is said when it takes too long.

Written for this skill rather than extracted from a project — it is the DNA's answer to a problem
every project has, authored to the same rules as everything else here. **Not yet consumed by a
project**, so the first repo to use it should report back anything that did not survive contact.

**Layers:** all four, and they are genuinely separable — the skeleton styling works without the
toast, and the toast works without the skeleton.

---

## 1. The rule: a skeleton is the real layout, wearing a skeleton

**A skeleton is not a silhouette.** It is not three grey rectangles that gesture at "content". It is
**the component's own markup**, with its own boxes at their own sizes, painted flat. Anything else
guarantees a jump when the content lands, and a jump at the end of a wait is worse than a spinner at
the start of one.

Practically, this means the loading state is a **class on the container**, not a different subtree:

```html
<section class="module field-list" data-loading>
  <span class="module-label">Pill</span>

  <!-- The real cards. They render empty and the container paints them as skeleton. -->
  <div class="field">
    <div class="field-head"><span>&nbsp;</span><span class="value">&nbsp;</span></div>
    <div class="slider"><div class="slider-track"></div></div>
    <div class="field-buttons"><button>&nbsp;</button><button>&nbsp;</button><button>&nbsp;</button></div>
  </div>
  <!-- …as many as the real list is expected to have… -->
</section>
```

Where the real count is unknown, render the **median** count, not the maximum — a skeleton that
collapses from ten rows to three reads as an error.

---

## 2. Styling

```css
/*
 * The skeleton, as a state of the real layout rather than a shape standing in for it. Every
 * descendant is flattened to one sunken fill and one radius, so what is left on screen is the
 * arrangement — which is the only part of the content that is actually known yet.
 */
[data-loading] * {
  /* Colour, border and text all go at once: a skeleton that keeps a border is a box that
     looks finished and empty, which reads as a bug rather than as a wait. */
  color: transparent !important;
  border-color: transparent !important;
  background: var(--sunken) !important;
  fill: transparent !important;
  /* Nothing inside a skeleton answers a finger. */
  pointer-events: none;
}
[data-loading] {
  background: var(--panel) !important;
  border-color: var(--border) !important;
  position: relative;
  overflow: hidden;
}
/* Anything that is genuinely empty in the real layout stays empty here — a skeleton that
   paints the gaps is a solid grey block. */
[data-loading] .module-label { background: transparent !important; }

/*
 * The sweep. One highlight crossing the whole container, not one per box: several sweeps at
 * several phases read as several things loading, and the panel is one thing loading.
 */
[data-loading]::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(
    100deg,
    transparent 25%,
    color-mix(in srgb, var(--section-color) 7%, rgba(255, 255, 255, 0.05)) 45%,
    transparent 65%
  );
  /* Twice the width, so a full traverse is one clean pass with no edge ever visible. */
  width: 200%;
  left: -50%;
  transform: translateX(-50%);
  animation: skeleton-sweep 1.6s var(--ease-sweep) infinite;
  pointer-events: none;
}
@keyframes skeleton-sweep {
  from { transform: translateX(-50%); }
  to   { transform: translateX(50%); }
}

@media (prefers-reduced-motion: reduce) {
  /* No sweep, but the skeleton must still say "not finished" — so it holds a static tint
     rather than disappearing, which would leave an empty panel that looks broken. */
  [data-loading]::after {
    animation: none;
    background: color-mix(in srgb, var(--section-color) 4%, transparent);
    width: 100%;
    left: 0;
    transform: none;
  }
}
```

Three decisions worth keeping:

- **One sweep per container, not per box.** Several sweeps at several phases read as several
  independent things loading. A panel is one thing loading.
- **The sweep carries a trace of the accent**, so even the waiting state belongs to the screen it is
  on. It is 7% — present, not decorative.
- **`transform` only.** A sweep animated on `background-position` re-paints the whole gradient every
  frame; a translated pseudo-element is composited.

---

## 3. The slow toast

```css
/*
 * Centred, because it is not a notification — it is an answer to a question the person has
 * already started asking ("is this broken?"), and the answer belongs where they are looking.
 */
.slow-toast {
  position: fixed;
  top: 50%;
  left: 50%;
  z-index: 999;
  translate: -50% -50%;
  padding: 0.9rem 1.15rem;
  border: 1px solid var(--border);
  border-radius: var(--squircle);
  background: var(--panel-solid);
  color: var(--text-body);
  font-family: "Mono", monospace;
  font-size: 0.72rem;
  letter-spacing: 0.08rem;
  text-align: center;
  pointer-events: none;
  animation: slow-toast-in 0.34s var(--ease-enter) both;
}
.slow-toast.leaving { animation: slow-toast-out 0.16s var(--ease-leave) both; }

@keyframes slow-toast-in  { from { opacity: 0; scale: 0.92; } to { opacity: 1; scale: 1; } }
@keyframes slow-toast-out { from { opacity: 1; scale: 1; } to { opacity: 0; scale: 0.97; } }
```

Enter 0.34s on the overshooting curve, leave 0.16s uniform — the same numbers as a panel entering
and leaving, because it is the same law: **the end is faster, immediate, and never the start
reversed.**

---

## 4. Behaviour

```js
/**
 * The loading state, and the one thing said when it outlasts patience.
 *
 * Two exported calls and no state of its own beyond the timer, so a caller cannot get the
 * skeleton and the toast out of step: whatever ends the load ends both.
 */

/**
 * How long a wait may run before it is worth saying something about it.
 *
 * Seven seconds is past every fast path and past most slow ones, so it fires for genuine
 * trouble rather than for a slow network — a reassurance that appears routinely is noise, and
 * noise is what people learn to ignore before the one time it mattered.
 */
const SLOW_AFTER = 7000;
const SLOW_TEXT = 'Taking longer than expected. Please wait';

/** Mirrors the leave animation on .slow-toast in the stylesheet. */
const TOAST_LEAVE = 160;

const waits = new WeakMap();

/**
 * Puts a container into its skeleton and starts the clock. Idempotent: a second call on a
 * container already loading is ignored rather than restarting the clock, or a view that
 * refreshes on a timer would never reach seven seconds however long it was actually stuck.
 */
export function beginLoading(container) {
  if (waits.has(container)) return;
  container.setAttribute('data-loading', '');
  waits.set(container, setTimeout(() => showSlowToast(), SLOW_AFTER));
}

/**
 * Takes it out again. Safe to call for a container that was never loading, and safe to call
 * twice — an error path and a success path both end here and neither has to know about the
 * other.
 */
export function endLoading(container) {
  const timer = waits.get(container);
  if (timer === undefined) return;
  clearTimeout(timer);
  waits.delete(container);
  container.removeAttribute('data-loading');
  // Only when nothing else is still waiting: two panels loading and one finishing is not the
  // moment to tell someone the wait is over.
  if (!document.querySelector('[data-loading]')) hideSlowToast();
}

let toast = null;

function showSlowToast() {
  if (toast) return;
  toast = document.createElement('div');
  toast.className = 'slow-toast';
  toast.setAttribute('role', 'status');
  toast.setAttribute('aria-live', 'polite');
  toast.textContent = SLOW_TEXT;
  document.body.appendChild(toast);
}

function hideSlowToast() {
  if (!toast) return;
  const leaving = toast;
  toast = null;
  leaving.classList.add('leaving');
  setTimeout(() => leaving.remove(), TOAST_LEAVE);
}
```

Usage:

```js
beginLoading(panel);
try {
  const data = await fetchSettings();
  render(panel, data);
} finally {
  endLoading(panel);           // the finally is the point: an error must not leave a skeleton
}
```

---

## 5. The decisions behind it

- **Skeleton over spinner.** A spinner says "something is happening"; a skeleton says "*this* is
  happening, and here is the shape of it". The second is worth the extra markup, and there is no
  extra markup here because the skeleton *is* the layout.
- **No skeleton under ~200ms.** A skeleton that flashes is worse than nothing. If the source is
  usually fast, delay `beginLoading` by 200ms and cancel it if the data beats the timer.
- **The timer is per wait, not per container-paint.** Idempotence in `beginLoading` is load-bearing:
  a view that re-renders while loading would otherwise restart the clock forever and the toast would
  never appear on exactly the stuck case it exists for.
- **One toast for any number of waits**, and it leaves only when the last one finishes. Two toasts
  for two slow panels is the interface complaining about itself.
- **`role="status"` / `aria-live="polite"`** — the toast is the only thing on screen that announces
  a change no visual state carries.
- **The toast never becomes an error.** It says *wait*, not *failed*. Whatever decides the request
  has actually failed is a different concern with a different message, and it should replace the
  skeleton rather than sit on top of it.

---

## Anti-patterns

- **A generic grey-blocks skeleton that is not the real layout.** Guarantees a jump. §1.
- **The maximum row count instead of the median.** Collapses on arrival. §1.
- **One sweep per box.** Reads as many things loading. §2.
- **Animating the sweep on `background-position`.** Full repaint per frame. §2.
- **A skeleton that keeps its borders.** Looks finished and empty. §2.
- **Reduced motion that removes the sweep and leaves an empty panel.** §2.
- **A toast in the corner.** The question is being asked at the centre of attention. §3.
- **Restarting the slow timer on every re-render.** The toast never fires. §4.
- **Ending the load only on the success path.** An error leaves a permanent skeleton. §4.
- **A toast per pending request.** §5.
- **Turning the wait message into an error message.** §5.
