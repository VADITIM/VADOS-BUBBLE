# Components

The reusable half of the skill. `dna/` says how the work should look and move; `map/` says which
project solved what; **this directory holds the actual pieces** — markup, CSS, JS, and the traps
that come with them — so a component can be lifted into a new project without re-deriving it.

## How to take a component

Every entry here is written in **four layers**, and you are meant to take only the ones you need:

| Layer | What it is | Take it when |
|---|---|---|
| **Layout** | The box structure and what sizes what | You want the arrangement, your own visuals |
| **Styling** | The tokens, borders, radii, colours | You want the look, your own structure |
| **Motion** | The transitions, keyframes, timings | You want the feel, nothing else |
| **Behaviour** | The JS: state, gestures, host contract | You want the whole thing |

Each entry states which layers depend on which. A layer with no dependencies is safe to lift alone.
Where a layer needs something from `dna/`, the entry names the module and section.

Everything is written in vanilla HTML/CSS/JS with no build step and no framework, because that is
what ports. `dna/10-porting.md` maps the mechanisms to other engines.

## The catalogue

| # | Component | Layers worth taking alone | Origin |
|---|---|---|---|
| 01 | [Goo / liquid metaballs](01-goo-liquid.md) | motion, behaviour — the whole effect is one filter plus one mirror | Dynamic Bubble |
| 02 | [The bubble](02-bubble.md) | layout, motion — press, hold-to-open, rubber-band drag, pull, push | Dynamic Bubble |
| 03 | [Panel, field card, drag-only slider](03-panel-and-field.md) | all four, independently | PORTFOLIO25 → Dynamic Bubble |
| 04 | [Screen transition](04-screen-transition.md) | motion — diagonal slices, positional stagger, bar-sweep reveal | PORTFOLIO25 |
| 05 | [Controls](05-controls.md) | styling, motion — link buttons, rotary knob, on/off glyph pairs, the liquid switch | Dynamic Bubble |
| 06 | [Level and waves](06-level-and-waves.md) | motion, styling — a filling level with a two-crest liquid surface | Dynamic Bubble |
| 07 | [Skeleton loader and the slow toast](07-skeleton-and-toast.md) | all four | Written for this skill |
| 08 | [Bar-sweep text reveal](08-text-reveal.md) | motion, behaviour — the default arrival for all text, plus the positional stagger | PORTFOLIO25 |
| 09 | [Typewriter](09-typewriter.md) | motion, behaviour — stepped typing, blinking caret, front-delete, retype | PORTFOLIO25 |
| 10 | [Magnetism — button, dot field, click hue](10-magnetic.md) | behaviour — a pointer field, at one target and at a thousand | PORTFOLIO25 |
| 11 | [The module — the universal panel](11-module.md) | all four; the styling layer alone is the whole surface language | PORTFOLIO25 |
| 12 | [3D cube](12-cube-3d.md) | motion, behaviour — quaternion drag, the raining build, the impact shadow | PORTFOLIO25 |

## The rules that bind all of them

1. **A component never names a colour.** It reads `--section-color` (or `--accent`, which inherits
   from it). Lifting a component into a project means the project sets the accent, not the piece.
   See `dna/01-palette.md`.
2. **A component owns its own press feedback.** The tap highlight is removed globally, so anything
   pressable that does not answer reads as broken. `scale: 0.96` under a finger, `--ease-split` for
   anything that lands.
3. **Nothing clips an overshoot.** Every bouncy curve goes past its mark; a container sized to the
   target cuts that off and the animation reads as wrong rather than the box as small.
4. **A constant two files have to agree on is named on both sides**, with a comment saying which
   other file it mirrors. There is no build step joining them, so a silent disagreement is the most
   likely bug in anything lifted from here.
5. **Enter is slow and staggered; leave is fast, uniform, and never the enter reversed.**
   `dna/05-motion.md` is binding on anything in this directory.
6. **Text arrives by bar sweep, and a component's boxes arrive as modules.** Those two are the
   defaults (`08-text-reveal.md`, `11-module.md`), not options — a fade on text or a bespoke bordered
   box is the exception and needs a reason at the call site.

## Anti-patterns

- **Copying a component and then editing the copy in the new project.** If the change generalises, it
  belongs back here; if it does not, it belongs in that project's own docs. Two drifting copies is
  how the identity dies.
- **Taking the behaviour layer without the motion layer.** Every one of these components has motion
  that *is* the affordance — a knob that changes state without turning is a different control.
- **Lifting a piece that mirrors a host constant without lifting the constant.** The bubble, the goo
  panes and the touch proxies all have a number on the other side of a bridge.
- **Reaching for a framework's component library first.** Everything here is a few dozen lines of
  vanilla. A dependency that draws a slider is heavier than the slider.
