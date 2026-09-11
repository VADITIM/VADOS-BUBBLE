# Module 02 — Typography

**Roles come first and faces are cast into them.** There are four roles; there happen to be four
faces today, and there will be more. A face never crosses into another's role, and a face with no
role is not in the design.

## The font registry

The master copies live in the reference project (`map/01-portfolio25.md`). Copy the files into any new
project — they are the identity, not a dependency:

```
C:\Users\vadim\source\PORTFOLIO\src\assets\fonts\
  Audiowide-Regular.ttf
  SpaceMono-Regular.ttf
  Striker.otf
  Wosker.otf
```

| Family name in CSS | File | Format | Role (below) |
|---|---|---|---|
| `Wosker` | `Wosker.otf` | `opentype` | display |
| `Striker` | `Striker.otf` | `opentype` | display |
| `Audiowide` | `Audiowide-Regular.ttf` | `truetype` | techno heading |
| `Mono` | `SpaceMono-Regular.ttf` | `truetype` | functional — **the default** |

**The CSS family name is the role's name where it can be, not the foundry's.** Space Mono is declared
as `Mono`, so the functional face can be swapped for another monospace by changing one `src` and
nothing else. Wosker, Striker and Audiowide keep their own names because they *are* chosen for their
particular shape — a different display face is a different decision, not a substitution.

Declared once, in one file (`src/style/fonts.scss`), loaded once from the app entry — **never
imported into a component**:

```scss
// Loaded once from `main.ts`. Never `@use`d by a component: a Vue <style> block compiles on its own,
// so anything imported into 45 of them is emitted 45 times.
@font-face { font-family: "Wosker";    src: url('../assets/fonts/Wosker.otf')            format('opentype'); }
@font-face { font-family: "Mono";      src: url('../assets/fonts/SpaceMono-Regular.ttf') format('truetype'); }
@font-face { font-family: "Audiowide"; src: url('../assets/fonts/Audiowide-Regular.ttf') format('truetype'); }
@font-face { font-family: "Striker";   src: url('../assets/fonts/Striker.otf')           format('opentype'); }
```

The same reasoning holds on any platform: font registration is an app-level concern, declared at the
root, never per widget.

## Role assignment

| Role | Faces cast in it today | Notes |
|---|---|---|
| Display / screen titles | **Wosker**, **Striker** | Big, sparse, uppercase. One per screen at most. |
| Techno headings, nav, buttons | **Audiowide** | Wide geometric. Reads as machine-labelled. |
| Everything functional | **Mono** | Labels, captions, data, terminal, forms, values, timestamps. |
| Fallback stack only | `system-ui, Avenir, Helvetica, Arial, sans-serif` | Never a *chosen* face — only what shows before the webfonts land. |

If a role is not on this list, it is Mono. **The default answer is always Mono.**

### What the roles actually look like in a built app

Counted across the reference project's components — useful as a sanity check on new work, because a
new screen whose ratios are wildly different is usually a screen that reached for a display face
where it needed a label:

| Face | Declarations | Where |
|---|---|---|
| Mono | ~76 | every label, caption, value, form field, terminal line, timestamp, body paragraph |
| Audiowide | ~60 | nav entries, buttons, menu headings, card titles, info-card titles, score readouts |
| Wosker | ~17 | screen names on the transition curtain, the landing name, detail-window titles, notice headings |
| Striker | 3 | two game-surface readouts and one crate label — a deliberately rare face |

Roughly **55% functional, 40% heading, 5% display**. Display is scarce on purpose; a face used
everywhere is not a display face, it is a body face with an attitude.

## Adding a face

Fonts will be added. The rules that keep four faces from becoming eleven:

1. **A face joins by taking a role, not by being liked.** Name the role in the same commit: either it
   is cast into one of the four above (alongside or replacing an existing face), or it comes with a
   fifth role that the design genuinely lacks. "It looks good" is not a role.
2. **Adding a face to the display role does not license using two on one screen.** The display rule
   is *one per screen*, and it does not become "one per face".
3. **Register it in `fonts.scss` and in the registry table above, in the same task.** A face that is
   loaded but not documented gets rediscovered as an unexplained `@font-face` and deleted, or worse,
   used for something arbitrary.
4. **Add a `format()` to the `src`.** `.woff2` → `woff2`, `.ttf` → `truetype`, `.otf` → `opentype`.
   Prefer `.woff2` for anything new; the `.ttf`/`.otf` above are what the originals shipped as.
5. **One weight per file, and the weight is in the file.** `font-synthesis: none` is global — if the
   family has no bold file, there is no bold, and asking for `font-weight: 700` gets the regular back
   silently. A face needed at two weights is two `@font-face` blocks with explicit
   `font-weight` descriptors, never a synthesised one.
6. **Declare `font-display` deliberately if the face carries text that must be readable immediately.**
   The default (`auto`, in practice a short block) is right for display faces — a title flashing in
   the fallback and re-flowing is worse than a beat of nothing. Functional text is the opposite case.
7. **A new face never arrives inside a component.** Rule from the registry above; adding one is the
   most common way it gets broken, because the component being built is where the need was felt.
8. **Give it a real fallback in the stack** (`font-family: 'NewFace', 'Mono', monospace`) so a failed
   load degrades to the nearest cast face rather than to the browser default.

If a new face makes an existing one roleless, **remove the old one**. Four faces with four jobs is
the identity; six faces where two overlap is a font folder.

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

The label ships as part of the panel it names — see `components/11-module.md §4` for the component
that owns it, including why the well's top padding and the label's size are two numbers that must be
changed together.

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

## Text that does not fit

**Text too long for its box fades out. It is never cut off at a hard edge and never ellipsised.** A
hard edge reads as a rendering fault — the eye sees a sliced glyph and starts looking for the bug —
and an ellipsis is a different sentence: it announces "there is more" in the typeface's voice rather
than in the design's. A fade says the same thing without adding a character nobody wrote.

```css
.clipped {
  overflow: hidden;
  white-space: nowrap;
  /* The clip has to start left of the first glyph: letters overhang their own box and the
     box edge otherwise shaves the first one. Paid back with the same negative margin, so
     nothing actually moves. */
  padding: 0 0.5rem 0 0.2rem;
  margin-left: -0.2rem;
  mask-image: linear-gradient(90deg, #000 calc(100% - 1.6rem), transparent);
}
```

The fade spans roughly one and a half characters — long enough to read as deliberate, short enough
that no whole word is lost inside it. It goes on the clipping element, never on the text, so a title
and the line beneath it fade on the same edge instead of each on its own.

**The fade goes on the edge the text overflows, which is the opposite of the edge it is aligned to.**
Left-aligned text runs off to the right and fades there; right-aligned text runs off to the *left*
and has to fade there instead. Reaching for the same right-hand mask on both is the easy mistake: it
leaves the right-aligned run sliced clean at its left edge — the hard cut this rule exists to
prevent — while fading the end that was never in danger.

```css
/* Right-aligned, so the overflow is on the left and the mask is mirrored. */
mask-image: linear-gradient(90deg, transparent, #000 1.6rem);
```

Two runs of text sharing one row each need their own half of it, declared as a half and not as
"whatever is left". A run given the remainder grows into its neighbour's space the moment its own
content is long, and the two collide in the middle — where, on a screen with a camera cutout, they
are also least readable.

Wrapping is the other honest answer and it is the better one wherever the box is free to grow.
Fading is for the boxes that are not: a fixed-height card, a status line, a bubble welded to a
camera cutout.

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
running prose. Sentence-case labels. **A face added without a role, or a face left in the registry
after its role went to another** — the count is not the problem, the overlap is. `@font-face` inside
a component. A weight asked for that no file supplies. Bare `px` font sizes in the landscape layout.
Rounded corners on text (Module 03). Overflowing text cut off at a hard edge, or ended with an
ellipsis, instead of faded out.
