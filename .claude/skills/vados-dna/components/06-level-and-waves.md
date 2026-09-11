# Level and waves — a filling gauge with a liquid surface

A box that fills to a percentage, coloured by which **band** that percentage falls in, with a
two-crest wave drifting along the top of the fill. Origin: Dynamic Bubble's battery band. Reusable
for any level a glance should read — storage, progress, capacity, health.

**Layers:** **styling** (the bands) and **motion** (the waves) are independent; take either alone.
The behaviour layer is four lines.

---

## 1. Bands, not a gradient

```js
/**
 * Five bands rather than a gradient: a band is a reading and a gradient is the number said
 * twice. What a glance wants off a gauge is which of the five situations it is in.
 */
function chargeColour(charge) {
  if (charge >= 86) return '#5bfd5b';   // 100–86  bright green
  if (charge >= 61) return '#2fbf2f';   //  85–61  green
  if (charge >= 36) return '#ffd429';   //  60–36  yellow
  if (charge >= 16) return '#ff9020';   //  35–16  orange
  return '#e5342b';                     //  15–0   red
}
```

A gradient makes 62% and 58% two slightly different colours, which is a distinction nobody can read
and nobody needs. Five steps make "yellow" mean something.

This is `dna/01-palette.md`'s rule applied to data: **a difference of amount is a ramp, a difference
of kind is a branch** — and a gauge that must be read at a glance is being asked for the *kind*.

---

## 2. The fill

```js
box.style.setProperty('--charge-height', Math.max(0, charge) + '%');
box.style.setProperty('--charge-color', chargeColour(charge));
```

```css
#quick-battery { position: relative; overflow: hidden; }

#battery-fill {
  position: absolute;
  left: 0; right: 0; bottom: 0;
  height: var(--charge-height, 0%);
  background: var(--charge-color, var(--text-faint));
  opacity: 0.55;
  transition: height 700ms var(--ease-enter), background 500ms ease;
}
```

Two custom properties and nothing else. The transition on `height` is what makes a level *rise*
rather than jump — 700ms on the enter easing, because a level arriving is a thing filling up.

**Labels over a coloured fill go white**, not accent and not band-coloured: the level behind them is
already the colour, and a glyph that changed with it is the same reading said twice in one box.

---

## 3. The two-crest wave

```html
<div id="battery-fill">
  <svg id="battery-waves" viewBox="0 0 200 24" preserveAspectRatio="none">
    <path class="battery-wave back"  d="M0 12 q25 -9 50 0 t50 0 t50 0 t50 0 t50 0 t50 0 V24 H0Z"/>
    <path class="battery-wave front" d="M0 12 q25 -9 50 0 t50 0 t50 0 t50 0 t50 0 t50 0 V24 H0Z"/>
  </svg>
</div>
```

```css
/* Above the fill's own top edge, so the crests break out of it rather than being cut by it.
   The box is exactly the fill's width and the paths overhang it — the viewport is what crops
   them, and that overhang is what gives the drift room to run. */
#battery-waves {
  position: absolute;
  left: 0;
  bottom: 100%;
  width: 100%;
  height: 18px;
  margin-bottom: -1px;      /* one pixel of overlap, or a hairline seam shows */
  overflow: hidden;
}

.battery-wave {
  will-change: transform;
  animation: battery-drift 5s linear infinite;
}
.battery-wave.front { fill: var(--charge-color, var(--text-faint)); }

/* Behind and higher, and all that is ever seen of it is the shaded edge it leaves above the
   front crest. The other direction and a slower pace, so the two never sit still against each
   other — two waves travelling together read as one wave drawn twice. */
.battery-wave.back {
  fill: color-mix(in srgb, var(--charge-color, var(--text-faint)) 55%, #000);
  animation: battery-drift-back 8.5s linear infinite;
}

@keyframes battery-drift      { from { transform: translateX(0);           } to { transform: translateX(-50px);      } }
@keyframes battery-drift-back { from { transform: translate(-50px, -5px);  } to { transform: translate(0, -5px);     } }
```

### The three numbers that make it work

- **One wavelength is 50 user units, and the drift is exactly 50.** A drift that is not a whole
  wavelength cannot loop; a drift of one wavelength loops with nothing visible at the seam.
- **The path runs six wavelengths (0→300) against a viewBox four wide (0→200).** The slack is what
  keeps a leading edge from walking into view at the loop. *This is the bug people hit:* a path that
  spans exactly the viewBox drags its own end across the frame every cycle and the animation reads
  as stuttering when it is actually running perfectly.
- **The two layers must differ in both speed and direction.** 5s forward and 8.5s reverse never
  repeat against each other within a watchable span. Same speed, or the same direction at similar
  speeds, and the pair collapses back into looking like one crest.

`preserveAspectRatio="none"` lets the wave stretch to any box width without re-authoring the path.
CSS `px` in a transform on an SVG child means **user units**, not screen pixels, so the drift stays
one wavelength at every rendered size.

### Cost

Two `transform`-only animations, promoted with `will-change`. Nothing else in the layer may animate
opacity or the compositor re-rasterises. Both run only while the box is on screen — a wave animating
behind a closed panel is free frames burned.

---

## 4. Layout: a band, not a column

A level reads best **across**: the glyph and the number stand at the two ends of the room the fill
is filling.

```css
#quick-battery {
  height: 73px;                         /* 0.7 × the width it had as a column */
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  padding: 0 1rem;
  border-radius: 18px;
  corner-shape: squircle;
  background: var(--sunken);
  border: 1px solid var(--border-control);
  color: #fff;
}
```

A tall narrow gauge in a wide panel is a strip at the edge; the widest reading in a panel should get
the widest box. And the number itself carries the precision, so it can be large — the fill is the
glance and the digits are the second look.

---

## Anti-patterns

- **A gradient where the reading is categorical.** §1.
- **Colouring the label with the band.** The fill already said it. §2.
- **A path that spans exactly the viewBox.** Stutter at every loop. §3.
- **A drift that is not a whole wavelength.** Cannot loop. §3.
- **Two wave layers at the same speed or direction.** They collapse into one. §3.
- **Animating anything but `transform` on the crests.** §3.
- **Running the animation while the container is closed.** §3.
- **A narrow vertical gauge in a wide panel.** §4.
