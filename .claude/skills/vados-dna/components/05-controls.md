# Controls — link buttons, the rotary knob, glyph pairs, the liquid switch

The control family for a compact panel. Origin: Dynamic Bubble's quick-settings panel, which went
through three shapes to get here — the history is kept because each rejection is a rule.

**Layers:** **styling** and **motion** are the value; the behaviour layer is a dozen lines.

---

## 1. Nothing with two states is a switch

**A switch draws a track it does not need.** A link is up or down; a mode is on or off. Neither has
a middle for a knob to travel across, so the track promises a range the control cannot keep. What is
left when the track goes is the knob the icon was already riding in — which is a button.

This replaced a full liquid toggle (§5) and the panel got simpler, larger-targeted and faster to
read in the same change.

---

## 2. The lit state is an inset shadow, never a thicker border

```css
.quick-mode.on {
  color: var(--section-color);
  background: color-mix(in srgb, var(--section-color) 16%, transparent);
  box-shadow: inset 0 0 0 2px var(--section-color);
}
```

**A border that thickens moves everything inside it by a pixel.** An inset shadow paints in the same
place, costs no layout, and transitions cleanly. This is the general answer for any "selected" edge.

---

## 3. The wide link button

```css
.quick-mode {
  flex: 1;
  /* A finger at arm's length, not a cursor. 42px was under what a thumb reliably hits — and a
     miss landed on the panel background, which closes it, so an imprecise tap read as the
     panel refusing to work. */
  height: 62px;
  border-radius: 20px;
  corner-shape: squircle;
  background: var(--sunken);
  border: 1px solid var(--border);
  color: var(--text-muted);
  display: grid;
  place-items: center;
  padding: 0;
  transition: background 200ms ease, box-shadow 200ms ease, color 200ms ease,
              scale 200ms var(--ease-split);
}
.quick-mode svg { width: 28px; height: 28px; fill: currentColor; }
.quick-mode:active { scale: 0.96; }
```

Full width in equal shares (`flex: 1`), and any row beneath it uses the same margins — two rows that
agree on their edges read as one panel rather than as two lists.

---

## 4. The rotary knob — a full turn that carries the change

A round button. Pressed, it **turns one full revolution and swaps its glyph at the half turn**.

```css
.quick-knob {
  width: 58px; height: 58px;
  border-radius: 999px;
  background: var(--sunken);
  border: 1px solid var(--border);
  color: var(--text-icon);
  display: grid; place-items: center; padding: 0;
  transition: background 200ms ease, box-shadow 200ms ease, color 200ms ease,
              scale 200ms var(--ease-split);
}
.quick-knob:active { scale: 0.94; }
.quick-knob.unavailable { opacity: 0.4; }

.quick-knob.turning { animation: knob-turn 460ms var(--ease-enter); }
@keyframes knob-turn { from { rotate: 0deg; } to { rotate: 360deg; } }
```

```js
const KNOB_TURN = 460;   // mirrors the animation above

/**
 * One control put at one end, whether that came from a finger or from a source answering.
 * The turn is only for the finger.
 */
function setControl(control, isOn, isTurning) {
  const wasOn = control.classList.contains('on');
  control.classList.toggle('on', isOn);
  if (!isTurning) {
    if (wasOn !== isOn) faceOf(control).innerHTML = glyphFor(control.dataset.toggle, isOn);
    return;
  }
  // Restarted rather than added: a second tap inside the first turn finds the class already
  // there and gets no animation at all — the button sits still for the press that asked hardest.
  control.classList.remove('turning');
  void control.offsetWidth;
  control.classList.add('turning');
  setTimeout(() => {
    faceOf(control).innerHTML = glyphFor(control.dataset.toggle, control.classList.contains('on'));
  }, KNOB_TURN / 2);
}
```

Four rules, and they generalise to any "the thing changed" micro-interaction:

- **A whole revolution is the one rotation that leaves nothing crooked behind it.** The button ends
  where it started; only what is inside it has changed.
- **The swap lands at the half turn**, where the glyph is upside down and least readable, so what
  the eye keeps is the face it ends on rather than a cut between two.
- **The turn belongs to the finger only.** A correction arriving from a slow source a frame later,
  or the paint that runs when the panel opens, must set the same control **without** spinning a
  button nobody touched. That is the whole reason `setControl` takes `isTurning` rather than
  inferring it.
- **Restart the animation, do not add the class.** `remove` → force reflow → `add`.

---

## 5. Glyph pairs — the icon is the reading

```js
/** Only the controls that have a genuine off *shape* are in here. */
const GLYPHS_OFF = { wifi: '…slashed…', bluetooth: '…slashed…', mic: '…slashed…' };

/** Which face a control wears at the end it is currently at. */
const glyphFor = (name, isOn) => (isOn ? GLYPHS[name] : GLYPHS_OFF[name] || GLYPHS[name]) || '';
```

**A slashed icon says "down" at a glance; a dimmed icon has to be compared against something before
it says anything.** Where a real off shape exists, use it. Where none exists, the control keeps its
one glyph and reads state by being lit — which is correct for *a mode* rather than the lack of one,
and is an honest fallback rather than a gap.

**Stroked SVG needs `fill="none"` on the shape itself, not only on the `<svg>`.** A stylesheet
writing `fill: currentColor` on the `<svg>` beats a presentation attribute on that same element but
loses to one on the child — so an icon that declares `fill="none"` only on the parent comes out as a
solid blob.

---

## 6. The liquid switch — kept for when a switch *is* right

Superseded in the panel above, but correct wherever a control genuinely reads as a thing being
*thrown*. It is the goo effect at control scale: the knob does not slide along a track, it is drawn
across to the end it is going to, and a **pool waiting at that end** is what makes that read — the
two are in one goo layer, so as the knob comes within reach their blurs overlap and they arrive as
one shape rather than as a dot that stopped somewhere.

```css
.liquid-switch { position: relative; width: 64px; height: 38px; }
.switch-rail   { position: absolute; inset: 0; border-radius: 999px;
                 background: var(--sunken); border: 1px solid var(--border-control); }
.switch-flow   { position: absolute; inset: 0; filter: url(#switch-goo); }

/* Everything in a goo layer is solid — a half-transparent shape falls below the contrast's
   threshold and is erased rather than merged — so these change SIZE, never opacity. */
.switch-knob { position: absolute; top: 5px; left: 5px;  width: 28px; height: 28px;
               border-radius: 999px; background: var(--text-icon);
               transition: transform 260ms var(--ease-enter), background 200ms ease; }
.switch-pool { position: absolute; top: 5px; right: 5px; width: 28px; height: 28px;
               border-radius: 0.5rem; background: var(--section-color);
               transform: scale(0); transform-origin: 100% 50%;
               transition: transform 260ms var(--ease-enter); }

.liquid-switch.on .switch-knob { transform: translateX(26px); background: var(--section-color); }
.liquid-switch.on .switch-pool { transform: scale(1); }
.liquid-switch.on .switch-rail { border-color: var(--section-color); }
```

**A toggle is not a scale**, so what carries the state is *which end holds the liquid*, not how much
of the track is coloured in. Off, the pool has drained back to nothing and the knob is alone again.

Its filter takes a **fixed** deviation, unlike the bubble row's measured one: these two shapes are
either apart or wholly on top of each other and never rest a hair's breadth apart, which is the one
case a constant is right for. See [01 — goo §8](01-goo-liquid.md).

**The icon rides in the knob**, above the goo layer rather than inside it — anything inside the
filter is blurred and then cut by the alpha contrast, which erases a small glyph outright.

### If you rotate one

Standing a switch on end is one line, and it has two consequences that are not:

```css
.liquid-switch { rotate: 90deg; }
.quick-cell    { min-height: 68px; }        /* the rotated footprint, reserved by hand */
.quick-icon    { transform: rotate(-90deg); }
.liquid-switch.on .quick-icon { translate: 26px 0; }   /* NOT transform */
```

- **A rotation is not a layout change.** The box the grid reserves is still the unrotated one, so
  the cell has to carry the rotated footprint by hand.
- **The counter-rotated glyph's travel must move to `translate`.** The individual transform
  properties compose `translate → rotate → scale → transform`, with `transform` innermost — so a
  `translateX` written in `transform` is measured in the glyph's own counter-rotated frame, and the
  icon walks sideways out of the knob while the knob goes down.

---

## 7. The throw is felt as a throw

A predefined "click" is the same buzz a tap on anything else gets. A control that changes *the
system* rather than the view deserves its own feel — a short rise into a full click, which is the
shape of something latching.

```kotlin
// Android: a composition is the vibrator's own waveform, not a canned constant.
VibrationEffect.startComposition()
    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.45f)
    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 40)
    .compose()
```

**The control answers under the finger, not after the source has confirmed.** The buzz and the
visual flip are the acknowledgement; the correction comes back on its own and passes through
`setControl(…, isTurning = false)`. A control that is a frame behind is better than a panel that
waits for a slow source before it moves — and a command that was refused must not be left standing
as a control that says it worked.

---

## Anti-patterns

- **A switch for something with no middle.** §1.
- **A thicker border for "selected".** §2.
- **Cursor-sized targets in a panel worked at arm's length.** §3.
- **A partial rotation to signal a change.** It ends crooked. §4.
- **Swapping the glyph at the start or the end of the turn.** The cut is visible. §4.
- **Spinning a control that a correction changed rather than a finger.** §4.
- **A dimmed icon where a slashed one exists.** §5.
- **`fill="none"` only on the `<svg>`.** §5.
- **Opacity inside a goo layer.** §6.
- **A glyph inside the goo filter.** §6.
- **Rotating a control without reserving its footprint, or without moving the travel to
  `translate`.** §6.
- **Waiting for a slow source before the control moves.** §7.
