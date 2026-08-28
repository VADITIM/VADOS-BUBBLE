# PORTFOLIO25 — the baseline

`C:\Users\vadim\source\PORTFOLIO25` · Vue 3 + TypeScript + GSAP + SCSS · builds to `docs/`, deploys
to `gh-pages` · live portfolio.

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
| Fonts (the four identity faces) | `src/assets/fonts/` |

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
