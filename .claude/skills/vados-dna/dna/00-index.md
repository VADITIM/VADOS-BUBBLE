# Style DNA — VADITIM

Part of the `vados-dna` skill. A project uses this only when the user points at it — "use VADOS DNA".
The project map lives in `../map/`, the per-engine pitfalls in `../platforms/`.

The portable identity behind these apps, extracted from PORTFOLIO25 and written to be handed to
another model or dropped into another project. It describes *how the work looks, moves, reads and is
built* — never what any one app does.

CSS is used throughout as the reference implementation because it is the quickest thing to write and
to look at. The identity does not live in CSS; Module 10 maps every rule to other platforms.

## The one-sentence version

A near-black terminal/game-menu system: hairline-bordered translucent panels on `#181818`, one
saturated accent per screen, monospaced uppercase micro-labels with wide letter-spacing, and motion
that treats every screen change as a menu transition — arriving slowly and diagonally, leaving fast
and all at once.

## The five laws

1. **The border is the design.** Depth comes from a 1px hairline and translucency, never from a shadow.
2. **One accent per screen, inherited from the root.** A leaf component never names a colour.
3. **Every element has a start, maybe a middle, and always an end** — and the end is faster,
   immediate, and never a reversed start.
4. **A difference of amount is a ramp; a difference of kind is a branch.** Widths are neither.
5. **If you cannot name an animation's intent, it does not ship.**

## Modules

| # | Module | Covers |
|---|---|---|
| 01 | [Palette](01-palette.md) | Ground, grey ramp, text steps, the seven screen accents, secondary and tinted palettes, derived colour |
| 02 | [Typography](02-typography.md) | The four faces and their absolute paths, role assignment, the micro-label, scale |
| 03 | [Surface](03-surface.md) | The panel primitive, radius policy, the masked border glow, diagonal slices, chrome removal |
| 04 | [Layout and sizing](04-layout-and-sizing.md) | Two axes never a width, two apps one codebase, the fluid scale lever, ramp rules, reserved space |
| 05 | [Motion](05-motion.md) | Intent, the three-phase law, timing tables, easing and asymmetry, choreography, the named patterns, degradation, the authoring checklist |
| 06 | [Interaction](06-interaction.md) | Stateful navigation, pointer-vs-orientation decisions, hover on touch, touch equivalents, gesture ownership |
| 07 | [Architecture](07-architecture.md) | The registry as source of truth, module layout, state ownership, docs discipline, verification |
| 08 | [Voice](08-voice.md) | Register, product vocabulary, terminal tones, progressive disclosure, motion notices |
| 09 | [Code style](09-code-style.md) | Naming, comment policy, region banners, anti-defaults, file organisation |
| 10 | [Porting](10-porting.md) | Web mechanism → Godot / .NET / Unity / native / CLI, and what survives everywhere |

## Quick reference card

```
GROUND    #181818          PANEL  rgba(18,18,18,.85)   BORDER  #262626
SUNKEN    #0e0e0e #1c1c1c  EDGES  #2c2c2c #3a3a3a #5a5a5a
TEXT      rgba(255,255,255,.87) → #d8d8d8 → #9a9a9a → #8a8a8a → #6a6a6a → #4a4a4a
DANGER    #ff6b6b / #DC143C
ACCENTS   #5bfd5b green (default)  #FFDD1B  #0040ff  #DC143C  #f09b3a  #8a2be2

FACES     Wosker · Striker (display) · Audiowide (headings) · Space Mono (everything else)
LABEL     Mono 0.63rem · uppercase · letter-spacing .19rem · #8a8a8a · panel top-left
RADIUS    0.75rem on boxes · 0 on text

ENTER     delay 0.50s · 0.35–0.95s · back.out / power2.out · staggered from top-left
LEAVE     delay 0    · 0.21–0.50s · back.in  / power2.in   · no stagger
IDLE      ~3s · ±20px · sine.inOut · random direction + delay · paused off-screen
CUT       close 0.42s · hold to 0.92s · open 0.5s · 6 bars · random staggers
```

## How to use this with a model

Point it at this index and the modules relevant to the task. For a new project, the order that works
is: 01 → 02 → 03 (make one panel look right), then 04 (establish the scale), then 05 (build the
transition before the second screen exists), then the rest as needed.

The anti-patterns section at the end of every module is the fastest correctness check — a design that
avoids all of them is already most of the way to belonging here.
