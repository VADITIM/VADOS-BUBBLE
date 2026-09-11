# Panel, field card, drag-only slider, colour field

The settings-screen family. A panel holds cards; a card holds one setting, its value, its control
and its buttons. Origin: PORTFOLIO25's panel primitive, extended in Dynamic Bubble's control panel.

**Layers:** all four are independently liftable. The panel is pure styling; the field card is
layout + styling; the slider is behaviour + motion; the colour field is behaviour.

---

## 1. The panel primitive

**This is the minimum — the look and nothing else.** The full component it is reduced from is
`components/11-module.md`: the same surface plus the pointer-tracking hue ring, the info affordance,
the caption, and the height tween. Take this version when a project wants the surface language and
will build its own chrome; take 11 otherwise. Where the two disagree, **11 is the source and this is
the port.**

The whole surface language in one rule. **Depth comes from a 1px hairline and translucency, never
from a shadow.**

```css
.module {
  --accent: var(--section-color);
  position: relative;
  border: 1px solid var(--border);
  border-radius: var(--squircle);          /* 0.75rem */
  background: var(--panel);                /* rgba(18,18,18,.85) */
  overflow: hidden;
  transition: border-color 0.25s ease;
}

/* The micro-label: mono, uppercase, wide tracking, parked in the corner. */
.module-label {
  position: absolute;
  top: 0; left: 0;
  font-family: "Mono", monospace;
  font-size: 0.63rem;
  font-weight: 500;
  letter-spacing: 0.19rem;
  text-transform: uppercase;
  color: var(--text-label);
  padding: 0.81rem 1rem;
  pointer-events: none;
}
```

**Radius policy: boxes get the radius, text never does.** A rounded label is the tell of a design
that applied a radius blindly.

```css
*, *::before, *::after { box-sizing: border-box; border-radius: var(--squircle); }
p, span, h1, h2, h3, h4, h5, h6, label, li, strong, em, small { border-radius: 0; }
```

**Squircle is for boxes wide enough to show it.** `corner-shape: squircle` needs room for the
difference between a superellipse and a rounded rectangle to read. On a pill or a circle the corners
already eat the whole end, so a squircle there is not a subtler shape but a *different* one — a
round button becomes a rounded square.

---

## 2. The field card — one setting, one box

```html
<div class="field">
  <div class="field-head"><span>Width</span><span class="value">85</span></div>
  <div class="slider"><div class="slider-track">
    <div class="slider-fill"></div><div class="slider-thumb"></div>
  </div></div>
  <div class="field-buttons"><button>−</button><button>+</button><button>Reset</button></div>
</div>
```

```css
.field {
  padding: 0.75rem 0.85rem 0.85rem;
  margin-bottom: 0.55rem;
  border: 1px solid var(--border);
  border-radius: 18px;
  corner-shape: squircle;
  background: var(--sunken);
}
.field-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  font-size: 0.63rem;
  letter-spacing: 0.19rem;
  text-transform: uppercase;
  /* The name in the screen's accent, the value in plain bright: at a glance the card says
     which setting it is before it says what it is set to. */
  color: var(--section-color);
}
.field-head .value { color: var(--text-bright); font-variant-numeric: tabular-nums; }
.field-buttons { display: flex; justify-content: flex-end; gap: 0.4rem; margin-top: 0.6rem; }
```

**One card per setting.** A column of bare controls reads as one thing with many knobs, and two
settings running into each other is most of why the wrong one gets touched.

**Rows use a grid, not a flex row with `space-between`.** Columns must line up across rows even when
one name wraps to two lines, which `space-between` cannot promise.

---

## 3. The drag-only slider

**A native `<input type="range">` jumps to wherever it was touched.** On a scrollable settings
screen that means a finger on its way past a slider changes the value it happened to land on — a
change nobody asked for and nobody saw.

This one takes its value from **how far the finger has travelled since it went down**. A tap does
nothing. A vertical scroll does nothing. Only sideways movement past a slop is a change.

```js
let from = null;
slider.addEventListener('pointerdown', event => {
  from = { x: event.clientX, value: current, armed: false };
});
slider.addEventListener('pointermove', event => {
  if (!from) return;
  const travelled = event.clientX - from.x;
  // Under the slop this is not a drag yet, so a finger on its way down the list is free to
  // pass over a slider without moving it.
  if (!from.armed) {
    if (Math.abs(travelled) < 8) return;
    from.armed = true;
    slider.classList.add('dragging');
    slider.setPointerCapture(event.pointerId);
  }
  apply(from.value + travelled / track.clientWidth * (field.max - field.min));
});
const release = () => { from = null; slider.classList.remove('dragging'); };
slider.addEventListener('pointerup', release);
slider.addEventListener('pointercancel', release);
```

```css
/* pan-y keeps the vertical scroll the page's. */
.slider { touch-action: pan-y; padding: 0.55rem 0; cursor: ew-resize; }
.slider-track { position: relative; height: 4px; border-radius: 999px; background: var(--border-strong); }
.slider-fill  { position: absolute; left: 0; top: 0; bottom: 0; border-radius: 999px;
                background: color-mix(in srgb, var(--section-color) 55%, transparent); }
.slider-thumb { position: absolute; top: 50%; width: 20px; height: 20px; margin: -10px 0 0 -10px;
                border-radius: 999px; background: var(--ground); border: 1px solid var(--section-color); }

/* Transitioned only when a finger is NOT driving it: a per-frame value must not be eased,
   and this is what makes a step button read as a move rather than a jump. */
.slider:not(.dragging) .slider-fill,
.slider:not(.dragging) .slider-thumb {
  transition: width 0.18s var(--ease-leave), left 0.18s var(--ease-leave);
}
.slider.dragging .slider-thumb { scale: 1.15; }
```

**A whole track's width of movement is the whole range**, whatever the screen is — the ratio is
against `track.clientWidth`, so the control has the same sensitivity on any device.

**Always pair it with `−` / `+` / `Reset` buttons.** A drag-only control has no way to nudge by one,
and the buttons are what make the transition rule above visible.

---

## 4. The colour field — use the platform's wheel

```html
<div class="field">
  <div class="field-head"><span>Accent</span><span class="value" id="accent-value">#5BFD5B</span></div>
  <div class="field-buttons">
    <input type="color" id="accent-picker">
    <button id="accent-reset">Reset</button>
  </div>
</div>
```

```css
/* The swatch IS the control: stretched across the card it reads as the colour rather than
   as a button that opens one. */
#accent-picker {
  flex: 1; height: 2.2rem; padding: 0;
  border: 1px solid var(--border-control);
  border-radius: 14px; corner-shape: squircle;
  background: transparent;
}
```

```js
const apply = number => {
  const hex = '#' + (number & 0xFFFFFF).toString(16).padStart(6, '0').toUpperCase();
  picker.value = hex;
  value.textContent = hex;
  // The screen that owns the setting wears it live: a colour cannot be judged anywhere else.
  document.documentElement.style.setProperty('--section-color', hex);
  store.set('accentColor', number & 0xFFFFFF);
};
picker.addEventListener('input', () => apply(parseInt(picker.value.slice(1), 16)));
```

**Do not build a colour wheel.** `<input type="color">` is the platform's own picker, is better than
anything you would grow, and is the one control in this family that needs no drag rules at all.

**Store a colour as one integer** (`0xRRGGBB`) where the store holds integers, and convert at both
edges. Three separate R/G/B sliders are a *different setting* — they are how a background is mixed,
not how an accent is chosen.

**The accent is a token, not a value.** Everything lit reads `--section-color`; the setting repoints
that one property. No component names a colour. See `dna/01-palette.md`.

---

## 5. Buttons

```css
button {
  font-family: "Audiowide", sans-serif;
  font-size: 0.72rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-body);
  background: var(--sunken);
  border: 1px solid var(--border-control);
  border-radius: var(--squircle);
  padding: 0.55rem 0.8rem;
  /* The platform tap highlight is removed globally, so every pressable thing owns its own
     press feedback. */
  transition: transform 0.12s var(--ease-leave), border-color 0.2s ease, color 0.2s ease;
}
button:active { transform: scale(0.96); border-color: var(--section-color); color: var(--section-color); }
button.danger { color: var(--danger); border-color: color-mix(in srgb, var(--danger) 40%, var(--border-control)); }
button.quiet  { background: transparent; border-color: transparent; color: var(--text-icon);
                font-family: "Mono", monospace; font-size: 0.6rem; letter-spacing: 0.19rem; }
```

**Touch targets are sized for a finger at arm's length, not for a cursor.** 42px is under what a
thumb reliably hits; 54–62px is not. A miss that lands on a panel background which *closes* the
panel reads as the panel refusing to work, which is a far worse failure than a large button.

---

## Anti-patterns

- **A shadow for depth.** The border is the design.
- **A radius on text.**
- **A squircle on a circle or a pill.** It becomes a different shape, not a subtler one.
- **A native range input on a scrollable screen.** §3.
- **Easing a value that is being written per frame.** §3.
- **Building a colour picker.** §4.
- **A leaf component naming a colour.** §4.
- **Controls in a bare column with no card boundaries.** §2.
- **`space-between` where columns must align across rows.** §2.
- **Cursor-sized touch targets.** §5.
