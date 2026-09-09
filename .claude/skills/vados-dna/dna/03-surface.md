# Module 03 — Surface, shape and light

## The panel is the whole design

A panel is: translucent near-black fill, one 1px hairline border, one radius, and a micro-label in
its corner. No drop shadow, no gradient fill, no elevation system. Depth comes from the *border* and
from translucency, never from shadow.

```scss
.module {
  --accent: var(--section-color, #5bfd5b);
  border: 1px solid #262626;
  border-radius: 0.75rem;
  background: rgba(18, 18, 18, 0.85);
  overflow: hidden;
  transition: border-color 0.25s ease;
}
```

## Radius: one token, reset on text

```scss
:root { --squircle: 0.75rem; }
*, *::before, *::after { border-radius: var(--squircle); }

// Boxes want the radius. Text never does — a rounded label or reveal bar is the
// tell of a design that applied a radius blindly.
p, span, h1, h2, h3, h4, h5, h6, label, li, strong, em, small,
.pc-label, .pc-label *, .module-label, .module-caption { border-radius: 0; }
```

Blanket-apply, then explicitly opt text out. Full pills (`999px`) exist only for scrollbar thumbs,
chips and status dots.

## Light: the masked border glow

The one ornamental effect in the system, and it lives on the *border ring only* — never on a fill.
A radial gradient follows the pointer through two custom properties updated on mousemove, masked to
the ring with `mask-composite: exclude`:

```scss
.module-hue {
  position: absolute; inset: 0; border-radius: inherit;
  padding: 1.5px;               // the ring thickness
  pointer-events: none;
  background: radial-gradient(11rem circle at var(--mx, 50%) var(--my, 50%),
    color-mix(in srgb, var(--accent) 95%, transparent),
    color-mix(in srgb, var(--accent) 50%, transparent) 45%,
    color-mix(in srgb, var(--accent) 20%, transparent) 100%);
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
          mask-composite: exclude;
  opacity: 0;
  transition: opacity 0.25s ease;
}
```

The whole ring lights, but the gradient's last stop keeps everything past the cursor radius dim — so
the edge glows *and* the pointer keeps a hot spot. Paired with a border-colour shift:

```scss
&:hover { border-color: color-mix(in srgb, var(--accent) 30%, #262626); }
```

This is one of only two hover effects deliberately kept on touch, because a tint there reads as
"this is the panel you just touched" rather than a stuck state.

## Screen backgrounds: the diagonal slice

Every screen has one, and a screen without one reads as unfinished. It is a slanted `clip-path`
polygon layer (usually a back/front pair plus corner wedges) in the screen's accent, parked mostly
off-canvas and animated in and out with the screen (Module 05).

- Left/right slices: hidden at `left: -30%`, revealed at `left: -10%`.
- Corner wedges: hidden at `-40%`, revealed at `-10%`.
- The front layer trails the back layer by `0.1s`. Always.

Nothing else in the app uses a diagonal. The slice owns that language.

## Chrome removal

The system hides every piece of browser and OS decoration, then re-supplies its own:

```scss
html { scrollbar-width: none; -ms-overflow-style: none; -webkit-tap-highlight-color: transparent; }
html::-webkit-scrollbar { display: none; }

// Scrollable regions carry `data-scroll-region` and get a thumb only — the track is
// dropped, because a grey gutter draws a hard line down the inside of a box whether
// or not there is anything to scroll.
[data-scroll-region] {
  scrollbar-width: thin;
  scrollbar-color: #3a3a3a transparent;
  overscroll-behavior: contain;
}
[data-scroll-region]::-webkit-scrollbar { width: 5px; height: 5px; }
[data-scroll-region]::-webkit-scrollbar-thumb { background: #3a3a3a; border-radius: 999px; }
[data-scroll-region]::-webkit-scrollbar-thumb:hover { background: #5a5a5a; }
[data-carousel-region] { scrollbar-width: none; }
```

Also `color-scheme: light only` plus `forced-color-adjust: none` on `html` — the app owns its palette
absolutely and refuses OS theme substitution.

## Anti-patterns

Drop shadows doing a border's job. Gradient fills. A multi-level elevation system. Rounded text.
Visible scrollbar tracks. A second ornamental effect competing with the border glow. Glow on a fill
instead of an edge. Component libraries that arrive with their own surface opinions.
