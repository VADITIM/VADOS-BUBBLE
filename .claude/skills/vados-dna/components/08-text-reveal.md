# The bar-sweep text reveal

The default way text arrives anywhere in the DNA. A solid accent bar grows across the text's box,
the text's clip-path snaps open *underneath* the opaque bar, and the bar then retracts off the far
edge — leaving text that was never seen fading in. Origin: PORTFOLIO25 (`src/modules/miscLabelReveal.ts`
+ `src/components/Misc/Label-Set.vue`).

**Layers:** **motion** and **behaviour** are the whole component and lift together; the **styling**
layer is four rules and exists only to give the bar something to cover. The **layout** layer
(absolutely-positioned labels driven by a `position` object per label) is the least interesting part
and is usually replaced by the host.

`dna/05-motion.md §6.1` states the rule; `components/04-screen-transition.md §4` holds the
**engine-free** version (one CSS keyframe track, for surfaces where GSAP is more weight than the
feature). **This file is the engine-driven canonical implementation.** Where the two disagree, this
one is the source and the other is the port.

---

## 1. The naming contract

```
.pc-label            the positioned anchor
  └ .pc-label-inner  one reveal unit — position: relative; overflow: hidden
      ├ .pc-label-text   clip-path: inset(0 100% 0 0)
      └ .pc-label-bar    scaleX(0), transform-origin: left center
```

**`.pc-label*` is the one deliberate exception to the "no section prefixes on class names" rule**
(`dna/09-code-style.md`). Every section in the app renders these class names and one shared module
queries them by string, so they are a cross-file contract: rename them everywhere or nowhere. A
component that invents `.my-label-bar` gets no reveal and no error.

The reveal unit is `.pc-label-inner`, **not** `.pc-label`. That is what makes a multi-word label able
to sweep word by word: each word gets its own inner, so each gets its own bar and its own positional
delay, and the anchor above them stays one positioned box.

---

## 2. The motion

```ts
import { gsap } from 'gsap';

// Initial hidden state; bar collapsed, text clipped away.
export function hideLabels(labelElements: HTMLElement[]) {
  labelElements.forEach(label => {
    gsap.set(label.querySelector('.pc-label-text'), { clipPath: 'inset(0 100% 0 0)' });
    gsap.set(label.querySelector('.pc-label-bar'), { scaleX: 0, x: '0%', opacity: 1, transformOrigin: 'left center' });
  });
}

// The 4-step sweep for a single unit. Returns a timeline so a one-off choreography (a landing page)
// can sequence labels inside a master timeline instead of via the positional stagger below.
export function buildLabelReveal(label: HTMLElement): gsap.core.Timeline {
  const timeline = gsap.timeline();
  const text = label.querySelector('.pc-label-text');
  const bar = label.querySelector('.pc-label-bar');
  if (!text || !bar) return timeline;
  gsap.killTweensOf([text, bar]);
  gsap.set(text, { clipPath: 'inset(0 100% 0 0)' });
  gsap.set(bar, { scaleX: 0, x: '0%', opacity: 1, transformOrigin: 'left center' });
  timeline.to(bar, { scaleX: 1, duration: 0.42, ease: 'power3.inOut' })
    .set(text, { clipPath: 'inset(0 0% 0 0)' })
    .set(bar, { transformOrigin: 'right center' })
    .to(bar, { scaleX: 0, duration: 0.5, ease: 'power3.inOut' })
    .set(bar, { opacity: 0 });
  return timeline;
}
```

The `.set()` calls between the two tweens are the entire trick, and they are why this is a timeline
rather than two tweens with delays: the clip flip and the origin flip must land in the *same frame*
that the bar is full. A transition or a duration on either one lets the reveal be caught mid-state,
which reads as the text tearing.

---

## 3. The positional stagger

```ts
export function playLabelReveals(labelElements: HTMLElement[], startAt: number) {
  const vh = window.innerHeight;
  const vw = window.innerWidth;
  labelElements.forEach(label => {
    const rect = label.getBoundingClientRect();
    const yFrac = rect.top / vh;    // 0 (top) → ~1 (bottom)
    const xFrac = rect.left / vw;   // 0 (left) → ~1 (right)
    // Vertical dominates so bottom labels clearly start later; horizontal only breaks the tie
    // between two labels on the same row, so the more left-aligned one always goes first.
    const delay = startAt + yFrac * 1.1 + xFrac * 0.4;
    buildLabelReveal(label).delay(delay);
  });
}
```

**The delay is measured, not authored.** Nothing lists the labels in order; a label added anywhere in
the markup takes its place in the cascade because its own screen position is what schedules it. This
is the same scalar family as the screen transition's stagger (`components/04 §3`) — vertical weighted
roughly 3× horizontal — so a screen's labels and its content read as one assembly rather than two
animations with separate ideas of time.

The stagger reads `getBoundingClientRect()`, so **it must run after layout**, and it must re-run per
layout rather than being cached: the same label is at a different fraction of the screen in portrait.

---

## 4. The leave

```ts
export function playLabelLeave(labelElements: HTMLElement[]) {
  labelElements.forEach(label => {
    const text = label.querySelector('.pc-label-text');
    const bar = label.querySelector('.pc-label-bar');
    gsap.killTweensOf([text, bar]);
    gsap.to(text, { clipPath: 'inset(0 100% 0 0)', duration: 0.3, ease: 'power2.in', overwrite: 'auto' });
    gsap.set(bar, { opacity: 0 });
  });
}
```

No delay, no stagger, no bar. **The reveal is a construction; the exit is a cut** — law 3. Running the
sweep backwards on leave is the single most common wrong instinct here, and it reads as an undo.

---

## 5. The host contract

The component that owns a set of labels does four things, in this order:

```ts
labelElements = [...root.value.querySelectorAll<HTMLElement>('.pc-label-inner')];
hideLabels(labelElements);

cleanup = onSectionStatesChange((meta) => {
  if (meta.isEnteringSection(sectionIndex)) playLabelReveals(labelElements, SECTION_ENTER_DELAY + props.delay);
  else if (meta.isLeavingSection(sectionIndex)) playLabelLeave(labelElements);
});

// Cold-mount case: if this screen is already the active one, reveal immediately.
if (currentSection.value === sectionIndex) playLabelReveals(labelElements, SECTION_ENTER_DELAY + props.delay);
```

**The cold-mount branch is not optional.** Screens are never unmounted (`components/04 §1`), so the
state-change subscription fires for every transition *except the one that never happened* — the first
screen, which was already active when the component mounted. Without the branch, exactly one screen
in the app has permanently invisible labels, and only on a deep link or a reload.

`hideLabels` must run before the subscription, not inside it: between mount and the first transition
the text would otherwise be visible unclipped for a frame or for seconds.

---

## 6. The three label modes

One component, three arrangements, all built from the same inner unit:

| Mode | Markup | Reads as |
|---|---|---|
| plain | one `.pc-label-inner` | one sweep across the whole string |
| `wrap` | one `.pc-label-line > .pc-label-inner` **per word**, stacked | a column of words, each swept, top-down |
| `stretch` | a `.pc-label-row` flex of one inner **per word**, side by side | a headline assembling left-to-right |

```scss
.pc-label-line + .pc-label-line { margin-top: 0.18em; }   // em, so the gap scales with the type
.pc-label-row { display: flex; flex-wrap: nowrap; gap: 0.4em; }
```

`flex-wrap: nowrap` on the row is load-bearing: a wrapped row puts two words on a line the stagger
then treats as one row, and the cascade goes sideways.

---

## 7. The styling layer

```scss
.pc-label-inner {
  position: relative;
  display: inline-block;
  overflow: hidden;    // the bar overhangs the text box vertically; this is what keeps it a bar
}

.pc-label-text {
  font-size: clamp(1.375rem, 2.4vw, 2.5rem);
  letter-spacing: 0.188rem;
  line-height: 1.05;
  white-space: pre;
  clip-path: inset(0 100% 0 0);   // hidden by default, so a cold frame never shows raw text

  // Portrait needs its OWN curve, not the landscape one.
  @include verticalLayout {
    font-size: clamp(0.75rem, 3.8vw, 1.6rem);
    letter-spacing: 0.14em;
  }
}

.pc-label-bar {
  position: absolute;
  top: -6%; bottom: -6%; left: 0;
  width: 100%;
  border-radius: 0;               // a rounded sweep bar is the tell of a blanket radius
  transform-origin: left center;
  transform: scaleX(0);
}
```

The bar is injected with the accent inline, glow included, because it is the one element in the
pattern that *is* the accent:

```html
<div class="pc-label-bar" :style="{ background: accent, boxShadow: `0 0 26px ${accent}` }"></div>
```

---

## 8. The numbers, and why they are those numbers

| Number | Why |
|---|---|
| `0.42s` grow, `0.5s` retract | The retract is slower because it is the half that has to be *read* — the growth is a wipe, the retraction is the reveal. Equal halves make the bar look like it bounced. |
| `power3.inOut` on both | The bar has to leave and arrive at rest; a `.out` ease makes it look thrown. |
| `0.3s` `power2.in` leave | Under half the enter, accelerating away. |
| `yFrac * 1.1 + xFrac * 0.4` | ~3:1 vertical:horizontal, matching the screen stagger. |
| `top/bottom: -6%` on the bar | The bar has to cover ascenders and descenders; flush to the text box shaves them and the reveal shows a sliver of un-swept glyph. |
| `0 0 26px` glow | Wide enough to bloom past the bar's own edges so the sweep reads as light rather than as a rectangle. |

---

## 9. The optional glitch

An opt-in RGB-split flicker on already-revealed text. It fires in **bursts** — clean for 90% of its
cycle — because a continuous glitch stops being an event and becomes a texture.

```scss
.label-set--glitch .pc-label-text { animation: label-glitch 3.6s steps(1, end) infinite; }
// Each label gets its own period and a NEGATIVE delay, so they are already out of phase on the
// first cycle rather than falling out of phase over the first ten seconds.
.label-set--glitch .pc-label:nth-child(2) .pc-label-text { animation-delay: -0.9s; animation-duration: 4.2s; }
.label-set--glitch .pc-label:nth-child(3) .pc-label-text { animation-delay: -2.1s; animation-duration: 3.1s; }
.label-set--glitch .pc-label:nth-child(4) .pc-label-text { animation-delay: -1.5s; animation-duration: 4.7s; }

@keyframes label-glitch {
  0%, 90%, 100% { text-shadow: none; transform: translateX(0); }
  91% { text-shadow: -2px 0 #ff2e88, 2px 0 #2ee6ff; transform: translateX(1px); }
  93% { text-shadow:  2px 0 #ff2e88, -2px 0 #2ee6ff; transform: translateX(-2px); }
  95% { text-shadow: -1px 0 #ff2e88, 1px 0 #2ee6ff; transform: translateX(1px); }
  97% { text-shadow: none; transform: translateX(0); }
}

@media (prefers-reduced-motion: reduce) { .label-set--glitch .pc-label-text { animation: none; } }
```

`steps(1, end)` rather than a smooth ease: a glitch that eases is a wobble. The chromatic pair is the
one place in the DNA a colour is named outside the palette, and it is named because it is *chromatic
aberration*, not an accent — see `dna/01-palette.md`.

---

## 10. Traps

**"The labels run off the right edge of every screen on a phone."** The landscape clamp was reused in
portrait. `2.4vw` of a phone is about 9px, so `clamp(1.375rem, 2.4vw, 2.5rem)` always lands on its
**minimum** — which is a desktop size — on the narrow axis. Portrait needs its own curve, and its
tracking has to go to `em` for the same reason. This is `dna/04-layout-and-sizing.md`'s rule arriving
as a bug.

**"One screen's labels never appear, but only sometimes."** That screen was the active one at mount.
See §5 — the cold-mount branch.

**"A word reveals, then the whole line re-reveals."** The selector collected `.pc-label` as well as
`.pc-label-inner`, so a wrapped label was registered as both one unit and N units.

**"The reveal stutters at the handover."** The clip flip was given a duration or left to a CSS
transition instead of being a `.set()` inside the timeline.

**"The bar is rounded at the ends."** The global blanket radius. Text and reveal bars opt out
explicitly (`dna/03-surface.md`).

**"Everything reveals at once after a rotation."** The stagger was computed once and cached. It reads
`getBoundingClientRect()`, so it belongs inside the responsive callback.

---

## Anti-patterns

- Fading text in. The DNA's default reveal is this one; a fade is the documented exception (§ intro).
- Playing the sweep in reverse on leave (§4).
- Renaming `.pc-label-*` in one component (§1).
- Authoring per-label delays by hand instead of letting position schedule them (§3).
- Reusing the landscape type clamp in portrait (§10).
- A continuous glitch instead of a bursting one (§9).
- Calling `playLabelReveals` without `hideLabels` having run at mount (§5).
