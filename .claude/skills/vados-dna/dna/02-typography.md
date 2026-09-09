# Module 02 — Typography

Four faces, each with exactly one job. A face never crosses into another's role.

## The files

Absolute paths on this machine (copy these files into any new project — they are the identity):

```
C:\Users\vadim\source\PORTFOLIO25\src\assets\fonts\Audiowide-Regular.ttf
C:\Users\vadim\source\PORTFOLIO25\src\assets\fonts\SpaceMono-Regular.ttf
C:\Users\vadim\source\PORTFOLIO25\src\assets\fonts\Striker.otf
C:\Users\vadim\source\PORTFOLIO25\src\assets\fonts\Wosker.otf
```

Declared once, in one file, loaded once from the app entry — never imported into a component:

```scss
@font-face { font-family: "Wosker";    src: url('../assets/fonts/Wosker.otf')            format('opentype'); }
@font-face { font-family: "Mono";      src: url('../assets/fonts/SpaceMono-Regular.ttf') format('truetype'); }
@font-face { font-family: "Audiowide"; src: url('../assets/fonts/Audiowide-Regular.ttf') format('truetype'); }
@font-face { font-family: "Striker";   src: url('../assets/fonts/Striker.otf')           format('opentype'); }
```

A scoped component style block compiles alone, so a `@font-face` imported into 45 components is
emitted 45 times. The same reasoning holds on any platform: font registration is an app-level
concern, declared at the root, never per widget.

## Role assignment

| Role | Face | Notes |
|---|---|---|
| Display / screen titles | **Wosker**, **Striker** | Big, sparse, uppercase. One per screen at most. |
| Techno headings, nav, buttons | **Audiowide** | Wide geometric. Reads as machine-labelled. |
| Everything functional | **Mono** (Space Mono) | Labels, captions, data, terminal, forms, values, timestamps. |
| Fallback stack only | `system-ui, Avenir, Helvetica, Arial, sans-serif` | Never a *chosen* face — only what shows before the webfonts land. |

If a role is not on this list, it is Mono. The default answer is always Mono.

## The signature: the micro-label

The single most recognisable element of the style. Every panel carries one, pinned to its top-left
corner, non-interactive, naming what the panel *is*:

```scss
.module-label {
  position: absolute; top: 0; left: 0;
  font-family: 'Mono';
  font-size: 0.63rem;
  font-weight: 500;
  letter-spacing: 0.19rem;   // the tell — wide enough to read as machine spacing
  text-transform: uppercase;
  color: #8a8a8a;
  padding: 0.81rem 1rem;
  pointer-events: none;
}
```

The portrait override lives *with the label*, not in each consumer:
`font-size: clamp(0.688rem, 3vw, 1.063rem); letter-spacing: 0.35vw;`

## The counterpart: the caption

Bottom edge of the panel, centred, half-overhanging the border (`transform: translateY(55%)`),
colour `#4a4a4a`, `0.75rem` Mono. Full sentences. The caption explains; the label names.

## Scale and rhythm

- Landscape sizes in **`rem`**, hanging off one fluid root (Module 04). Portrait sizes in
  `clamp(min, vw, max)`.
- `line-height: 1.5` global, `1.1` for display headings.
- Letter-spacing is a *role marker*, not a fine-tuning knob: `0.19rem` on micro-labels, `~0.1em` on
  Audiowide headings, `0` on running Mono prose.
- Uppercase for labels, nav entries, buttons and screen names. Sentence case for prose and captions.

## Rendering

```css
font-synthesis: none;
text-rendering: optimizeLegibility;
-webkit-font-smoothing: antialiased;
-moz-osx-font-smoothing: grayscale;
```

No synthesised bold or italic — if a weight does not exist in the file, it does not exist in the design.

## Anti-patterns

Mixing display faces within one screen. Bold Mono standing in for the display face. Letter-spaced
running prose. Sentence-case labels. A fifth typeface. Bare `px` font sizes in the landscape layout.
Rounded corners on text (Module 03).
