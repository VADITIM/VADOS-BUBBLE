# The typewriter

A line of text typed out one character at a time behind a blinking caret bar, and backspaced away
again on exit. Origin: PORTFOLIO25 (`src/modules/miscTypewriter.ts` + `src/components/Misc/Typewriter.vue`).

**Layers:** **behaviour** and **motion** are one thing and lift together. The **layout** layer is
small but load-bearing — the one-cell grid in §2 is what stops the line reflowing every frame — and
the **styling** layer is the caret alone.

`dna/05-motion.md §6.5` states the timings; this file is the implementation.

**The pattern mirrors the bar-sweep reveal** (`components/08-text-reveal.md`) deliberately: all
motion lives in a standalone module with a plain-DOM API, and the framework component is a thin
wrapper. Any screen can then drive a typewriter from its own animation handler without going through
the component, which is what makes it sequenceable inside a larger timeline.

---

## 1. The structure

```
.tw                one-cell grid
  ├ .tw-sizer      the FULL target text, visibility: hidden — reserves the box
  └ .tw-line       flex, baseline-aligned
      ├ .tw-text   the characters revealed so far
      └ .tw-caret  a bar, not a glyph
```

```html
<span class="tw" :class="{ 'tw--reverse': reverse }" :style="{ color }">
  <span class="tw-sizer" aria-hidden="true">{{ sizerText }}</span>
  <span class="tw-line">
    <span class="tw-text"></span><span
      class="tw-caret"
      :style="{ background: caretColor, boxShadow: `0 0 8px ${caretColor}` }"
    ></span>
  </span>
</span>
```

The sizer is `aria-hidden`, and the live line is what a screen reader reads. There is no whitespace
between `.tw-text` and `.tw-caret` in the markup, on purpose: `white-space: pre` on the host would
otherwise render that newline as a space and park the caret a character away from the text.

---

## 2. The one-cell grid

```scss
// The sizer and the live line share one grid cell, so the element is always exactly the finished
// line's size and never reflows mid-animation.
.tw { display: inline-grid; white-space: pre; }
.tw-sizer, .tw-line { grid-area: 1 / 1; }
.tw-sizer {
  visibility: hidden;
  padding-right: 0.12em;   // the caret lives in the overlaid line and so measures nothing of its own
}

// Flex, not inline: playClear's `clearFromStart` pins .tw-text's width to hold the line's right
// edge, and an inline box ignores a width.
.tw-line { display: flex; align-items: baseline; }
```

**This is the whole reason the component exists rather than a `textContent` loop.** A line that sizes
itself to its typed-so-far content grows the box on every character, and anything centred,
right-aligned or laid out beside it jitters for the length of the animation. The sizer books the
final width up front.

`white-space: pre` matters twice over: a typed run ending in a space collapses without it, and the
line's leading indent disappears.

---

## 3. The caret

```scss
// The classic text-editor caret: a thin vertical bar the height of the text.
.tw-caret {
  display: inline-block;
  width: 0.08em;
  height: 1em;
  margin-left: 0.04em;
  border-radius: 0;               // a rounded caret is the blanket radius leaking onto text
  transform: translateY(0.08em);  // the baseline sits above the glyph box's bottom
}
```

Every dimension is in `em`, so the caret is welded to the type size at any viewport and needs no
portrait override. Glow is injected inline in the caret colour (defaulting to the text colour), the
same way the reveal bar carries its accent.

```ts
function startBlink(handle: Handle, period: number) {
  handle.blink?.kill();
  gsap.set(handle.caretElement, { opacity: 1 });
  // A blink at the collapsed time scale is a strobe; hold the caret solid instead.
  if (prefersReducedMotion.value) { handle.blink = null; return; }
  handle.blink = gsap.to(handle.caretElement, {
    opacity: 0, duration: period, repeat: -1, yoyo: true, ease: 'steps(1)',
  });
}
```

`ease: 'steps(1)'` — a caret that fades is a pulsing light, not a caret.

---

## 4. Typing

```ts
export function playType(handle: Handle, options?: TypewriterOptions): Promise<void> {
  const resolvedOptions = resolve(options);
  handle.tween?.kill();
  startBlink(handle, resolvedOptions.caretBlink);
  const state = { visibleCount: 0 };
  return new Promise(resolve => {
    // Carved out of reduced motion: the typing IS the content here, and a line that appears fully
    // formed says nothing.
    handle.tween = keepFullMotion(gsap.to(state, {
      visibleCount: handle.full.length,
      duration: handle.full.length * resolvedOptions.speed,
      delay: resolvedOptions.delay,
      ease: `steps(${Math.max(1, handle.full.length)})`,
      onUpdate: () => reveal(handle, Math.round(state.visibleCount)),
      onComplete: () => {
        handle.textElement.textContent = handle.full;
        if (!resolvedOptions.holdCaret) stopBlink(handle);
        resolve();
      },
    }));
  });
}
```

One tween on **one number**, with `steps(n)` doing the quantising, rather than n scheduled callbacks.
The engine owns the timeline, so the line can be killed, seeked, nested in a parent timeline and
time-scaled like anything else. The promise is what lets a caller `await` a line and start the next.

`keepFullMotion` is the **reduced-motion carve-out**: this is one of the few animations that is not
decoration. Collapsing it produces a finished sentence and no information about how it got there.
The rule generalises — *reduced motion removes movement that carries no content; it does not remove
the content*.

```ts
// `count` visible characters, taken from the start (normal) or the end (reverse, so growth reads
// right-to-left).
function reveal(handle: Handle, count: number) {
  handle.textElement.textContent = handle.reverse
    ? handle.full.slice(handle.full.length - count)
    : handle.full.slice(0, count);
}
```

---

## 5. Clearing, and the front-delete

```ts
export function playClear(handle: Handle, options?: TypewriterOptions): Promise<void> {
  const resolvedOptions = resolve(options);
  handle.tween?.kill();
  startBlink(handle, resolvedOptions.caretBlink);
  const state = { visibleCount: handle.textElement.textContent?.length ?? handle.full.length };

  // Freeze the box at the width it currently has and right-align what is left inside it: the
  // characters then vanish off the front while the line's right edge — and with it the caret —
  // stays exactly where it was.
  const deleteFromStart = resolvedOptions.clearFromStart && !handle.reverse;
  if (deleteFromStart) {
    handle.textElement.style.width = `${handle.textElement.getBoundingClientRect().width}px`;
    handle.textElement.style.textAlign = 'right';
  }
  return new Promise(resolve => {
    // NOT carved out of reduced motion the way typing is: the backspace is an exit, and letting it
    // run in real time would leave text standing on a screen that is already gone.
    handle.tween = gsap.to(state, {
      visibleCount: 0,
      duration: state.visibleCount * resolvedOptions.clearSpeed,
      ease: `steps(${Math.max(1, state.visibleCount)})`,
      onUpdate: () => {
        const count = Math.round(state.visibleCount);
        if (deleteFromStart) handle.textElement.textContent = handle.full.slice(handle.full.length - count);
        else reveal(handle, count);
      },
      onComplete: () => {
        handle.textElement.textContent = '';
        // The caret is the one thing left standing, so it keeps blinking; only a reset (the next
        // enter) puts it away.
        if (!deleteFromStart) stopBlink(handle);
        resolve();
      },
    });
  });
}
```

**The enter and the exit take opposite reduced-motion decisions in the same file**, and that asymmetry
is the point: an entrance that carries content is kept, an exit never is.

**`clearFromStart` is the terminal-line delete.** Normal backspacing walks the caret leftward across
the line. Front-deleting holds the right edge — so the caret sits still and blinks while the sentence
is eaten out from under it, which is what makes a line about to be replaced read as *being replaced*
rather than as being undone.

---

## 6. The API

```ts
export interface TypewriterOptions {
  speed?: number;           // seconds per character while typing (default 0.06 → ~16 chars/s)
  delay?: number;           // seconds before the first character
  clearSpeed?: number;      // seconds per character while clearing (default 0.03)
  caretBlink?: number;      // caret blink period, seconds (default 0.5)
  holdCaret?: boolean;      // leave the caret blinking after the line finishes (default true)
  clearFromStart?: boolean; // delete from the front, holding the right edge (see §5)
}

createTypewriter(element, full, reverse?)  // wire an element; starts empty, caret hidden
playType(handle, options?) : Promise<void>
playClear(handle, options?): Promise<void>
setTypewriterText(handle, text)            // swap the target with no animation
resetTypewriter(handle)                    // snap to empty; drops what clearFromStart pinned
killTypewriter(handle)                     // unmount
```

`resetTypewriter` clearing `style.width` and `style.textAlign` is not tidiness — those are the two
inline properties `clearFromStart` writes, and a handle reused without dropping them types the next
line into a box frozen at the previous line's width.

### Retype

```ts
// Backspace the current line and retype it as `newText`. A generation guard discards a stale
// sequence if retype is called again before this one settles.
let retypeGeneration = 0;
async function retype(newText: string) {
  const generation = ++retypeGeneration;
  await clear();
  if (generation !== retypeGeneration) return;
  setText(newText);
  if (generation !== retypeGeneration) return;
  await type();
}
```

The guard is mandatory for anything driven by user input — a rotating tagline, a status line, a
search echo. Two overlapping retypes without it interleave their `onUpdate` writes into one element
and produce a line that is neither string.

The sizer always renders the **target** text, not the current one, so a retype re-books the box for
the new line *before* that line starts typing:

```ts
const sizerText = ref(props.text);
function setText(newText: string) { setTypewriterText(handle, newText); sizerText.value = newText; }
```

---

## 7. Reverse

Growth right-to-left, for text anchored to a right edge.

```scss
// The caret sits at the growing text's LEADING (left) edge instead of trailing it.
.tw--reverse .tw-line {
  justify-content: flex-end;
  .tw-caret { order: -1; margin-left: 0; margin-right: 0.04em; }
}
```

The sizer is what gives reverse a stable right edge to grow from — without the booked width there is
no fixed edge and the line crawls leftward as it types.

`reverse` and `clearFromStart` are mutually exclusive by construction (`clearFromStart && !reverse`):
both hold the right edge, and asking for both is asking a line to delete from the end it is growing
from.

---

## 8. The numbers

| Number | Why |
|---|---|
| `0.06s`/char typing | ~16 chars/s — fast enough not to be a wait, slow enough to be read as it lands. |
| `0.03s`/char clearing | **Deletion always reads faster than typing.** Exactly half; a shared ratio, not a tuned pair. |
| `0.5s` caret blink | The platform convention. Deviating from it makes the caret read as a decoration. |
| `steps(n)` easing | A character is discrete. Any smooth ease produces fractional counts and duplicated frames. |
| `0.08em` caret width | Under a tenth of the type size at every scale, so it stays a caret and never a block cursor. |
| `0.12em` sizer padding | Books the caret's own width, which measures nothing from inside the overlaid line. |

---

## 9. Traps

**"Everything next to the line jitters while it types."** The sizer is missing, or the host set an
explicit width that overrides it.

**"`clearFromStart` moves the caret anyway."** `.tw-line` was left as an inline box, so the pinned
width on `.tw-text` did nothing.

**"The caret strobes for users with reduced motion on."** The blink was not guarded — at a collapsed
time scale a 0.5s yoyo becomes a flicker.

**"The typed line disappears instantly for reduced-motion users."** The `keepFullMotion` carve-out
was dropped along with the rest of the motion. Typing is content (§4).

**"Two lines fight over one element."** Overlapping `retype` calls with no generation guard (§6).

**"The second line types into a box the width of the first."** A handle reused without
`resetTypewriter` after a `clearFromStart` run (§6).

**"The trailing space vanishes."** `white-space: pre` missing on `.tw`.

---

## Anti-patterns

- A `setInterval` / per-character `setTimeout` loop instead of one stepped tween (§4). It cannot be
  killed cleanly, cannot be nested in a timeline, and drifts against everything else on screen.
- Letting the box size itself to the typed-so-far text (§2).
- A caret that fades instead of stepping (§3).
- Deleting at the typing speed (§8).
- Collapsing the typing animation under reduced motion (§4) — or *keeping* the deletion (§5).
- A retype with no generation guard (§6).
- Reaching for the component when a screen needs to sequence the line inside its own timeline — the
  module's plain-DOM API is there for exactly that (§ intro).
