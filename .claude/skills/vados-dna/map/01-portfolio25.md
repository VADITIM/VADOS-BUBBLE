# PORTFOLIO25 — the baseline

`C:\Users\vadim\source\PORTFOLIO` · Vue 3 + TypeScript + GSAP + SCSS · builds to `docs/`, deploys
to `gh-pages` · live portfolio.

**The working directory is `PORTFOLIO`, not `PORTFOLIO25`.** The project keeps the name PORTFOLIO25
throughout these docs — that is what it is called — but anything pointing a path at a `PORTFOLIO25`
directory is stale and will fail to resolve.

**This is where the DNA comes from.** Every rule in `dna/` was extracted from this codebase. When
another project disagrees with it, this one is right unless the disagreement is a recorded platform
accommodation.

## What it is

A single-page portfolio that behaves like a game menu. Sections animate in and out like game screens;
there is no visual scrollbar — the wheel only signals intent. Sections are never unmounted, because
GSAP needs persistent DOM targets.

## The parts worth reading

| Concern | File |
|---|---|
| Screen registry — the single source of truth | `src/modules/sectionsRegistry.ts` |
| Section state machine, enter/leave dispatch | `src/modules/sectionsStateMachine.ts`, `sectionsCore.ts` |
| The canonical enter/leave motion reference | `src/modules/sectionCoverSlices.ts` |
| The screen-swap curtain | `src/modules/sectionsTransition.ts` + `Section-Transition.vue` |
| The bar-sweep text reveal | `src/modules/miscLabelReveal.ts` |
| Reduced-motion substitute pair | `src/modules/animationLiteFallback.ts` |
| Idle drift | `src/modules/animationFloatingElements.ts` |
| Typewriter | `src/modules/miscTypewriter.ts` |
| Input mode, gesture/axis ownership | `src/modules/scrollInput.ts` |
| Device vs layout axes | `src/modules/miscViewport.ts`, `src/style/mixins.scss` |
| Tokens (emits no CSS) | `src/style/variables.scss` |
| Globals, the fluid root, lite-mode kill switch | `src/style/style.scss` |
| The panel primitive | `src/components/Misc/Module.vue` |
| Magnetic button, magnetic dot field | `src/components/Misc/Magnetic-Button.vue`, `Magnetic-Dots.vue` |
| The 3D cubes | `src/components/Main/Logs Section/Cubes.vue`, `Logs-Cube.vue` |
| Fonts (the four identity faces) | `src/assets/fonts/` |
| Font registration (loaded once from `main.ts`) | `src/style/fonts.scss` |

## Which component entry holds which piece

The pieces below are written up with their code and their traps in `components/` — **read the entry,
not the source**, unless the entry is wrong (in which case fix it, per `MAINTAINING.md`).

| Source here | Entry |
|---|---|
| `miscLabelReveal.ts` + `Label-Set.vue` | `components/08-text-reveal.md` |
| `miscTypewriter.ts` + `Typewriter.vue` | `components/09-typewriter.md` |
| `Magnetic-Button.vue`, `Magnetic-Dots.vue` | `components/10-magnetic.md` |
| `Module.vue` | `components/11-module.md` (`components/03 §1` is its reduced form) |
| `Cubes.vue`, `Logs-Cube.vue` | `components/12-cube-3d.md` |
| `sectionsTransition.ts` + `Section-Transition.vue`, `sectionCoverSlices.ts` | `components/04-screen-transition.md` |
| `Settings-Menu.vue` | `components/03-panel-and-field.md` |
| `Loading-Indicator.vue`, `Instant-Cuts-Toast.vue` | `components/07-skeleton-and-toast.md` |

## The shared component shelf

`src/components/Misc/` is where anything used by more than one screen lives. Everything here is
specifically styled — there is no unstyled utility component in the project. **Check this list before
building something for a new project**; the ones marked ✔ already have a `components/` entry with
liftable code, and the rest are candidates the second they are wanted again.

| Component | What it is | Entry |
|---|---|---|
| `Module.vue` | the universal panel — border, micro-label, hue ring, caption, info card | ✔ 11 |
| `Label-Set.vue` | a screen's corner/edge labels, revealed by bar sweep | ✔ 08 |
| `Typewriter.vue` | a line typed out behind a blinking caret | ✔ 09 |
| `Magnetic-Button.vue` | a button that leans toward the cursor; carries no look of its own | ✔ 10 |
| `Magnetic-Dots.vue` | the full-screen canvas dot field and the click hue | ✔ 10 |
| `Section-Transition.vue` | the curtain that masks a screen swap | ✔ 04 |
| `Section-Cover-Slice.vue` | the per-screen diagonal slice backdrop (plus the Projects helix canvas) | ✔ 04 |
| `Settings-Menu.vue` | the settings panel: field cards, drag-only controls, commit-on-save | ✔ 03 |
| `Loading-Indicator.vue` | the one waiting state for anything backed by a request; CSS-only so it survives lite mode | ✔ 07 |
| `Instant-Cuts-Toast.vue` | the offer to skip transitions after two of them; hover to hold, timed on touch | ✔ 07 |
| `Navigator.vue` | the screen nav — a bottom row on a touch device, a right-hand column on a pointer one | — |
| `Terminal.vue` | the command dock: prompt, history, command catalogue, block demos | — |
| `Admin-Menu.vue` | the visitor table and the destructive-action controls | — |
| `Close-Button.vue` | the ring-and-cross close control; follows the screen accent unless overridden | — |
| `Start-Transition.vue` | the intro's explore button, and the prefetch it covers | — |
| `Unlock-Scan-Splash.vue` | the QR-arrival splash | — |
| `Classified-Note.vue`, `Classified-Unlock-Popup.vue` | the locked-screen note and its unlock card | — |
| `Restore-Data-Notice.vue`, `Comment-Deleted-Notice.vue`, `Hardware-Acceleration-Notice.vue` | the notice family — a card, a heading, an accept/dismiss pair | — |
| `Set-Demo.vue` | the miniature grid that demonstrates a terminal command | — |

Three of the notices share one shape and are the obvious next `components/` entry — **the notice
card**: a bordered panel that arrives after the loading screen has handed off, states one fact, and
offers exactly two actions.

## Its own agent docs

- `CLAUDE.md` — commands, key decisions, don'ts.
- `.claude/rules/frontend.md` — sizing, the two axes, hover, device class. The longest one.
- `.claude/rules/animations.md` — binding GSAP rules, timing tables, the label pattern, lite mode.
- `.claude/rules/architecture.md`, `.claude/rules/code-quality.md`.
- `TASKS.md` backlog · `DECISIONS.md` shipped work and reasoning.

## Standing constraints

- **Two apps, one codebase.** Horizontal (desktop) and vertical (mobile) are separate experiences,
  not one responsive design. The vertical layout is the current focus; desktop is settled.
- Section indices are never hardcoded — resolve via `getSectionIndexById('id')`.
- Never edit `docs/` by hand. Never run a preview eval or drive the browser to verify — the user does
  the visual evaluation.
- `npm run check:css-vars` exists because a GSAP tween on a custom property no rule reads is silent
  dead code.

## What it contributed to the DNA

Essentially all of it: the palette and its inheritance mechanism, the panel primitive, the micro-label,
the bar-sweep reveal, the positional stagger, the curtain, the three-phase law, the enter/leave
asymmetry, the two-axis media strategy, the fluid root scale lever, and the registry pattern.

Later extractions, with the rule each one produced:

- **The module** (`components/11`) — the ring mask, the info affordance's `:focus`-for-touch answer,
  and the only sanctioned exception to "never animate a layout property".
- **The typewriter** (`components/09`) — *reduced motion removes movement that carries no content; it
  does not remove the content*, which is why the typing is carved out and the deletion is not.
- **The magnetic pair** (`components/10`) — a pointer field is a hover effect and must be gated on the
  device axis; and a field of more than a few dozen members is a canvas.
- **The cubes** (`components/12`) — *never fake a size with `transform: scale()`* (the layout box stays
  behind and any other tween overwrites it), and *two choreographies must never write the same
  transform property on one element*.
