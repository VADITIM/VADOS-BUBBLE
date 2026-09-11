# Goo — the liquid metaball effect

The signature effect. Two or more separate DOM boxes read as **one body of liquid**: they neck
together as they approach, fuse when they touch, and pull apart into separate drops as they
separate. No element bridges them and nothing is drawn between them — the bridge is an artefact of
one SVG filter.

Origin: Dynamic Bubble (`app/src/main/assets/`), where it holds the bubble, its satellites, the
clock, the status bubble, the lock-screen cards and the padlock in one skin.

**Layers:** the effect is *motion + behaviour*. There is no styling layer to take — the shapes are
solid colour by necessity (see §3). The layout layer is "whatever boxes you already have".

---

## 1. The whole effect in six lines

```html
<svg id="filters" aria-hidden="true">
  <filter id="bubble-goo" x="-15%" y="-40%" width="130%" height="180%">
    <feGaussianBlur id="bubble-melt" in="SourceGraphic" stdDeviation="2" result="blurred"/>
    <feColorMatrix in="blurred" type="matrix"
      values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 30 -14"/>
  </filter>
</svg>
```

```css
#liquid-fill { filter: url(#bubble-goo); }
.blob { position: absolute; top: 0; left: 0; background: #000; }
```

Blur the layer, then push the alpha back to a hard edge. Shapes whose blurs overlap come back as one
outline; shapes whose blurs do not, keep their own. That is the entire mechanism.

**Reading the colour matrix.** The last row is the alpha row: `0 0 0 30 -14` means
`alpha_out = alpha_in × 30 − 14`. Multiplying by 30 makes the blur's soft ramp near-vertical;
subtracting 14 slides the resulting step to where it bites. The threshold sits at
`14 ÷ 30 ≈ 0.47`, so anything the blur left below 47% alpha is erased and anything above it is
solid. Two blurs that overlap sum past the threshold in the gap between them, and the gap fills in.

**Tuning the two numbers.** The multiplier is *edge hardness* — lower it (18–22) for a softer,
gel-like rim, raise it (30–40) for a crisp one. The offset is *where the cut lands* — raise it and
shapes have to be closer before they bridge; lower it and they bridge from further off and start
looking fat. Change them as a pair and keep the ratio near 0.45–0.5, or the shapes gain or lose
apparent size.

**The two tuned pairs in use:**

| Filter | stdDeviation | matrix alpha row | Used for |
|---|---|---|---|
| `bubble-goo` | live, 1.6 → 6 | `0 0 0 30 -14` | bubbles that rest apart and must not weld |
| `switch-goo` | fixed, 5 | `0 0 0 22 -10` | a knob and its pool — either apart or wholly overlapping |

---

## 2. The deviation is not a constant

**This is the single most important thing in this file.** Two shapes bridge at roughly **twice** the
`stdDeviation`. So:

- A deviation strong enough to fuse a satellite leaving the bubble **welds the resting row into one
  bar** for as long as it stands there.
- A deviation weak enough to leave the resting row alone **never merges anything at all**.

There is no number that is both. So it is not a number: it is read off the measured gap between the
shapes every frame, which is what liquid does anyway — drops pull together as they near and let go
as they part, and nothing about that is constant.

```js
function meltBy(measured) {
  const boxes = measured.filter(seen => seen && seen.box.width).map(seen => seen.box);
  let closest = Infinity;
  // Every pair, and only the pairs that actually stand beside each other.
  for (let a = 0; a < boxes.length; a += 1) {
    for (let b = a + 1; b < boxes.length; b += 1) {
      const one = boxes[a];
      const two = boxes[b];
      // Two shapes on different rows are not near each other in any sense the skin cares about.
      if (one.top >= two.bottom || two.top >= one.bottom) continue;
      closest = Math.min(
        closest, one.left < two.left ? two.left - one.right : one.left - two.right
      );
    }
  }
  const melt = closest === Infinity
    ? MELT_MIN
    : Math.min(ceiling, Math.max(MELT_MIN, ceiling - closest * 0.5));
  if (melt !== meltNow) {
    meltNow = melt;
    liquidBlur.setAttribute('stdDeviation', melt);   // MELT_MIN 1.6 · ceiling 6
  }
}
```

**The pair loop is not laziness.** Sorting the boxes by left edge and walking the list treats the
canvas as one line, which it stops being the moment a shape is drawn somewhere other than the row: a
near-full-width card at the bottom of the screen reports, against anything on the row, a gap of most
of the screen *negative* — the deviation pins at its ceiling and the entire row welds into a bar.
The `top`/`bottom` overlap test is what makes "near" mean anything. Five boxes make ten pairs; there
is nothing to save by being clever.

Only the ceiling is user-tunable. A slider that could also raise the *floor* would weld a row that
is standing still, which is the one thing the measured melt exists to prevent.

---

## 3. Everything inside the filter is opaque

A half-transparent shape falls below the alpha threshold and is **erased**, not dimmed. So:

- Shapes in the goo layer are solid colour. Always.
- Transparency belongs to the **layer**: `#liquid-fill { opacity: var(--pill-alpha, 1); }`.
- A shape leaving does not fade — it **shrinks**. By the time it is gone there is nothing left.
- Icons and text never go inside the filter. A 14px glyph is blurred and then cut by the contrast,
  which erases it outright. Content sits in a sibling layer above the skin.

The colour is therefore split in two at the token level: a solid for the shapes, an alpha for the
layer.

```css
:root { --pill-solid: #000; --pill-alpha: 0.8; }
#liquid-fill .blob { background: var(--pill-solid); }
#liquid-fill        { opacity: var(--pill-alpha); }
```

---

## 4. The skin is mirrored, never told

The shapes are **not positioned by rules of their own**. Every frame, each blob is measured off the
box it stands for and written to match. This is the only way a skin follows a transition, a drag and
a hold at once without all three rules being written twice — and the only way it cannot disagree
with the thing it is the skin of.

```js
/** Every rect first, every write after: one layout per frame instead of three. */
function mirrorFrame() {
  const measured = blobs.map(blob => {
    if (!isSkinned(blob.name)) return null;
    const source = sourceOf(blob.name);
    // The declaration getComputedStyle hands back is *live*, so it is resolved once per
    // element and kept — but re-resolved when the element behind a slot changes, or a
    // recycled slot goes on reading the style of whatever used to be there.
    if (blob.source !== source) blob.style = getComputedStyle(source);
    blob.source = source;
    return {
      source,
      box: source.getBoundingClientRect(),
      radius: blob.style.borderRadius,
      corner: blob.style.cornerShape,      // a squircle mirrored as a rounded rect does not fit
      colour: blob.style.borderTopColor,   // an accent border arrives in the skin for free
      alpha: parseFloat(blob.style.opacity),
    };
  });

  blobs.forEach((blob, index) => {
    const seen = measured[index];
    if (!seen || !seen.box.width) { blob.edge.style.display = 'none'; return; }
    blob.edge.style.display = '';
    write(blob.edge, seen.box,  0, seen);   // the edge is the box itself
    write(blob.fill, seen.box, -1, seen);   // the fill is one pixel in from it
    blob.edge.style.background = seen.colour;
  });

  meltBy(measured);
}
```

**Two layers, not one.** The hairline border cannot be a border: a 1px line is thinner than the blur
and is wiped out by it. It is a second, slightly larger shape *underneath* — `#liquid-edge` — which
survives the same filter the fill does and merges with it. The edge is the box's own rectangle and
the fill is a pixel in from it; drawing the edge outside instead leaves the shape two pixels wider
than the box it mirrors.

**Reading the colour off the source** is what makes state changes free. A box that turns its border
to an accent hands that accent to the skin with no second rule to keep in step.

**Batch the reads.** Every `getBoundingClientRect()` first, every style write after. Interleaved,
each write invalidates layout for the next read and the frame costs one forced reflow per blob.

---

## 5. The frame loop runs only while something moves

```js
let liquidUntil = 0;
let liquidFrame = null;

export function stirLiquid(milliseconds) {
  liquidUntil = Math.max(liquidUntil, performance.now() + (milliseconds || 700));
  if (liquidFrame !== null) return;
  liquidFrame = requestAnimationFrame(flowLiquid);
}

function flowLiquid() {
  paintLiquidFrame();
  if (performance.now() < liquidUntil) { liquidFrame = requestAnimationFrame(flowLiquid); return; }
  liquidFrame = null;
}
```

**Every size change owes the mirror a `stirLiquid()` long enough to cover the whole transition —
including every close.** A transition started without stirring freezes the glass part-way through,
which reads as a rendering bug and is a bookkeeping one. This is the most common way to break the
effect after it works.

The default is deliberately just past the longest transition an argument-less caller can have
started. Paying the length of the longest *choreography* on every resting repaint is up to a second
of frame loop after everything has stopped moving.

**Re-entrancy.** A threshold crossing at the end of a frame changes state, and a state change
repaints, which under a finger paints synchronously and lands back inside the mirror on top of
itself. One `mirroring` flag, and the merge pass runs last:

```js
export function paintLiquidFrame() {
  if (mirroring) return;
  mirroring = true;
  try { mirrorFrame(); } finally { mirroring = false; }
}
```

---

## 6. The filter's region is the whole of it

**A filter reaches exactly as far as its own region and not one pixel further.** Outside it, the
skin is silently dropped — no error, no warning — and the shape goes back to painting its own
background, which reads as a colour bug and is a geometry one.

```css
#liquid {
  position: absolute; left: 0; right: 0; top: 0;
  /* Only as tall as the row can ever be: a filter costs its region. */
  height: calc(var(--grab, 0px) + var(--liquid-tall, 60px));
}
/* While something stands outside the row, the region is the whole screen — and only then. */
html.grown  #liquid,
html.locked #liquid { height: 100%; }
```

Two consequences, both learned the hard way:

- **A shape drawn outside the row needs the region grown *before* it gets there**, not while it
  travels. A region that grows mid-flight clips the flight it was meant to make room for.
- **The region shrinks only after the last thing has come home.** A region that shrinks mid-collapse
  clips what is still moving.

The `x`/`y`/`width`/`height` on the `<filter>` element itself are a second, separate region — the
filter's own bounding box, in percentages of the element it applies to. Too tight and the blur is
cut off at the edges; the `-15% / -40% / 130% / 180%` above is sized for a wide, short row.

---

## 7. The source boxes must stop painting themselves

```css
html.liquid #pill,
html.liquid .satellite,
html.liquid #status { background: transparent; border-color: transparent; }
```

A box left out of this rule paints twice: its own opaque background stacked on top of the skin. It
reads as *one shape with the wrong alpha in a row of correct ones* — the tell is that the rim is
solid where everything else is glass. Every box with a blob must be in this rule, no exceptions, and
adding a blob without adding the box here is the single most common porting mistake.

---

## 8. When the goo is not the answer

- **Two shapes that never rest near each other** — a knob and the pool it flies into — take a
  **fixed** deviation. The measured melt buys nothing when the gap is only ever zero or large.
- **A gap wider than about twice the ceiling deviation** cannot be bridged by the filter at any
  sane setting. Draw the neck as a real shape and let the goo fillet it in. Raising the deviation
  far enough to bridge it welds everything else.
- **A shape in a second window/surface can never merge.** The filter reaches as far as its own
  surface. This is the constraint that decides an entire window architecture: one surface draws, and
  it draws everything. See `platforms/android-shizuku.md`.

---

## 9. Cost

One full-screen filtered layer per frame while the loop runs. On a 2024-class phone in a WebView
this is comfortably 60fps for a dozen blobs, provided:

- `will-change: transform, width, height` on the blobs.
- The region is as small as the current state allows (§6).
- The loop stops when nothing moves (§5).
- Nothing else in the same layer animates opacity — an opacity animation on a filtered layer
  re-rasterises the filter every frame.

---

## Anti-patterns

- **A fixed `stdDeviation` for shapes that rest at varying distances.** There is no value that both
  merges and lets go. §2.
- **Semi-transparent shapes inside the filter.** They are erased, not dimmed. §3.
- **Fading a shape out.** Shrink it. §3.
- **Text or icons inside the goo layer.** Erased by the contrast. §3.
- **Positioning the skin with rules of its own instead of mirroring.** It will disagree with the
  box under a drag, a hold or an interrupted transition — the three cases that matter.
- **A size change with no `stirLiquid()`.** Frozen glass mid-transition. §5.
- **Adding a blob without adding its box to the background-blanking rule.** Double-painted rim. §7.
- **Interleaving reads and writes in the mirror.** One forced reflow per blob per frame. §4.
- **Assuming a shape outside the current region will merge.** It will silently not. §6.
