---
name: vados-dna
description: VADITIM's cross-project design and engineering identity — the "VADOS DNA" engine style. Load when the user says "VADOS DNA", "use my DNA", "my style", "house style", or asks to build/restyle UI, write animations, or start a project that should look and behave like their other work. Also load when the user asks what they have built before, points at one of their projects by name (PORTFOLIO25, Dynamic Bubble / Android Dynamic Island, VADOS terminal, Anomaly, Velvet Deck, Brawl Stars Controller, godot-mcp-tool), or asks how a feature was solved somewhere else. Covers palette, typography, surface, layout, motion (enter/idle/leave), interaction, architecture, voice, code style, and per-platform pitfalls for Vue/GSAP, Svelte/Tauri, Android/Shizuku, Godot and CLI.
---

# VADOS DNA

The engine style behind VADITIM's projects. Two halves:

- **The DNA** (`dna/`) — how the work looks, moves, reads and is built. Platform-independent.
- **The map** (`map/`) — what has already been built, where it lives, and which project already
  solved the problem in front of you.

"VADOS" here names the *engine style*, not the terminal — the terminal project happens to share the
name and is one of its consumers.

## Opt-in

**A project only uses this DNA when the user points at it** — "use VADOS DNA", "my style", or an
explicit mention of one of the modules. Do not apply it to a project that has not asked for it, and
do not restyle existing work toward it uninvited. Suggesting it is fine; assuming it is not.

## How to use it

Read only what the task needs. Every module ends with an anti-patterns list — that list is the
fastest correctness check on work already written.

| Task | Read |
|---|---|
| Anything at all | `dna/00-index.md` — laws + quick-reference card |
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

## The five laws

1. **The border is the design.** Depth from a 1px hairline and translucency, never a shadow.
2. **One accent per screen, inherited from the root.** A leaf component never names a colour.
3. **Every element has a start, maybe a middle, and always an end** — the end is faster, immediate,
   and never a reversed start.
4. **A difference of amount is a ramp; a difference of kind is a branch.** Widths are neither.
5. **If you cannot name an animation's intent, it does not ship.**

## The map

`map/00-projects.md` indexes every project: what it is, what stack, what it contributed to the DNA,
and what is worth stealing from it. Read it when the user references past work, asks "how did I do X
before", or starts something adjacent to an existing project.

`platforms/` holds the per-engine notes — the pitfalls, the constraints and the specific
accommodations each platform forced. Read the matching file *before* writing platform code, not
after the first bug.

## Keeping it current

This skill is a living record. `MAINTAINING.md` states when and how to extend it. In short: a
solved platform pitfall becomes a `platforms/` entry, a new project becomes a `map/` row, and a rule
that turned out to generalise gets promoted into `dna/`. Do this in the same task that discovered
it, and say so in the response.
