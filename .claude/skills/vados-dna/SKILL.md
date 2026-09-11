---
name: vados-dna
description: VADITIM's cross-project design and engineering identity — the "VADOS DNA" engine style, the library of components already built to it, and its professional fork "VADOS DNA PRO". Load when the user says "VADOS DNA", "VADOS DNA PRO", "use my DNA", "my style", "house style", or asks to build/restyle UI, write animations, or start a project that should look and behave like their other work. Also load to reuse a piece they have built before — a goo/metaball liquid effect, a draggable bubble, a settings panel with drag-only sliders, a screen transition, toggles and rotary icon buttons, a banded level gauge with a wave surface, or a skeleton loader. Also load when the user asks what they have built before, points at one of their projects by name (PORTFOLIO25, Dynamic Bubble / Android Dynamic Island, VADOS terminal, Anomaly, Velvet Deck, Brawl Stars Controller, godot-mcp-tool), or asks how a feature was solved somewhere else. Covers palette, typography, surface, layout, motion (enter/idle/leave), interaction, architecture, voice, code style, and per-platform pitfalls for Vue/GSAP, Svelte/Tauri, Android/Shizuku, Godot and CLI.
---

# VADOS DNA

The engine style behind VADITIM's projects. Four parts:

- **The DNA** (`dna/`) — how the work looks, moves, reads and is built. Platform-independent.
- **The components** (`components/`) — the actual pieces, with code: markup, CSS, JS and the traps
  that come with them. Reach here whenever the answer is "I have built that before".
- **The PRO fork** (`pro/`) — the same DNA in a professional register, as a delta over `dna/`.
- **The map** (`map/`) — what has already been built, where it lives, and which project already
  solved the problem in front of you.

"VADOS" here names the *engine style*, not the terminal — the terminal project happens to share the
name and is one of its consumers.

## Opt-in

**A project only uses this DNA when the user points at it** — "use VADOS DNA", "my style", or an
explicit mention of one of the modules. Do not apply it to a project that has not asked for it, and
do not restyle existing work toward it uninvited. Suggesting it is fine; assuming it is not.

## Variants — read this before reading anything else

There are two registers, and **neither loads unless the project names one.**

| The project says | Read | For |
|---|---|---|
| *nothing* | **neither tree** | Not a DNA project. Do not apply either register |
| "VADOS DNA", "use my DNA", "my style", "house style" | `dna/` only | Portfolios, launch pages, demos, anything whose goal is the visitor's attention |
| **"VADOS DNA PRO"**, "PRO", "the professional register" | the base `dna/` module **then** its `pro/` override | Company work, tools, dashboards, anything an operator is paid to sit in front of |

**PRO is a delta, not a copy.** Loading it means reading the base module first and then the `pro/`
file that overrides it — where they disagree, `pro/` wins; where `pro/` is silent, the base module is
still in force. Seven of the eleven base modules have no PRO override at all and are read exactly as
written. `pro/00-index.md` carries the full override table and the PRO quick-reference card; read it
first when the register is PRO.

Do not guess the register from the project sounding serious. If a project has not said, ask.

**`components/` belongs to neither register.** The pieces are written in the base register because
that is where they were built, so a PRO project takes a component's *layout and behaviour* layers as
they are, and re-reads its *styling and motion* layers through `pro/01`, `pro/02` and `pro/05`.
Some components are base-only by nature — the typewriter and the magnetic pointer field are
ambience, and `pro/05-motion.md` drops ambience. `pro/00-index.md` says which.

## How to use it

Read only what the task needs. Every module ends with an anti-patterns list — that list is the
fastest correctness check on work already written.

Every row below is the *base* module. In the PRO register, read the base module and then its `pro/`
override if one exists — the override table in `pro/00-index.md` says which do.

| Task | Read |
|---|---|
| Anything at all | `dna/00-index.md` — laws + quick-reference card |
| **Anything at all, in PRO** | `pro/00-index.md` first — override table + PRO card |
| **Accessibility (PRO only)** | `pro/11-accessibility.md` — no base counterpart |
| **Reusing something already built** | `components/00-catalogue.md` — then the entry it names |
| Metaballs, goo, blobs merging, liquid | `components/01-goo-liquid.md` |
| A draggable / holdable / flickable object | `components/02-bubble.md` |
| A settings screen, a slider, a colour picker | `components/03-panel-and-field.md` |
| A screen-to-screen transition | `components/04-screen-transition.md` |
| Toggles, switches, icon buttons | `components/05-controls.md` |
| A gauge, a level, a fill with a liquid surface | `components/06-level-and-waves.md` |
| A loading state, a skeleton, a slow-request notice | `components/07-skeleton-and-toast.md` |
| **Any text arriving on screen** | `components/08-text-reveal.md` — the default, not an option |
| Text typed out, a caret, a terminal line | `components/09-typewriter.md` |
| A button that leans toward the cursor, a pointer-reactive field | `components/10-magnetic.md` |
| **Any bordered box holding content** | `components/11-module.md` — the panel every screen uses |
| A 3D object, a drag-to-rotate, an object that assembles | `components/12-cube-3d.md` |
| Colour, accents, theming | `dna/01-palette.md` |
| Fonts, labels, type scale | `dna/02-typography.md` |
| Panels, borders, glow, backgrounds | `dna/03-surface.md` |
| Responsive sizing, breakpoint questions | `dna/04-layout-and-sizing.md` |
| **Any animation work** | `dna/05-motion.md` — binding, not advisory |
| Input, gestures, hover, navigation | `dna/06-interaction.md` |
| Project structure, state, registries, docs | `dna/07-architecture.md` |
| Copy, labels, terminal output, prompts | `dna/08-voice.md` |
| Naming, comments, region banners | `dna/09-code-style.md` |
| Building on a non-web platform | `dna/10-porting.md` + the matching `platforms/` file |

Starting a new project, in order: `01` → `02` → `03` (make one panel look right), `04` (establish
the scale lever), `05` (build the transition before the second screen exists), then the rest.

## Laws

1. **Every element has a start, maybe a middle, and always an end animation** — the end is faster, immediate,
   and never a reversed start.
2. **A difference of amount is a ramp; a difference of kind is a branch.** Widths are neither.
3. **If you cannot name an animation's intent, it does not ship.**
4. **Corners are exclusively Squircle until told different** 

## The components

`components/00-catalogue.md` indexes every reusable piece. Each entry is written in four layers —
**layout, styling, motion, behaviour** — and states which are safe to lift alone, so a project can
take just the animation, just the look, just the arrangement, or the whole thing. Read the catalogue
before writing any UI piece that sounds like something that already exists; re-deriving a component
that is in here is how two copies start drifting.

## The map

`map/00-projects.md` indexes every project: what it is, what stack, what it contributed to the DNA,
and what is worth stealing from it. Read it when the user references past work, asks "how did I do X
before", or starts something adjacent to an existing project.

`platforms/` holds the per-engine notes — the pitfalls, the constraints and the specific
accommodations each platform forced. Read the matching file *before* writing platform code, not
after the first bug.

## Keeping it current

This skill is a living record. `MAINTAINING.md` states when and how to extend it. In short: a
solved platform pitfall becomes a `platforms/` entry, a new project becomes a `map/` row, a piece
worth using twice becomes a `components/` entry, and a rule that turned out to generalise gets
promoted into `dna/`. Do this in the same task that discovered
it, and say so in the response.
