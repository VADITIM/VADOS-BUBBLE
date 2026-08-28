# Where these skills came from

Every folder beside this file was vendored from a public repository at the commit named
below. Nothing here was written for a specific project, and nothing here outranks a
project's own `CLAUDE.md` or rule files — where a vendored skill and a project rule
disagree, the project rule wins, because it was written against that codebase.

Re-vendoring is a fresh clone at a newer commit and a diff, not a merge: these are
upstream files and no local edit should be made in place.

| Source | Commit | Skills | What it is |
|---|---|---|---|
| [emilkowalski/skills](https://github.com/emilkowalski/skills) | `d23d7f8` | 12 | `apple-design`, `animate`, `improve-animations`, `review-animations`, `animation-vocabulary`, `find-animation-opportunities`, `emil-design-eng`, `prototype`, `write-swift`, `animate-expo`, `pick-ui-library`, `ask-sonner`. |
| [greensock/gsap-skills](https://github.com/greensock/gsap-skills) | `aed9cfd` | 8 | Official GSAP: core, timeline, scrolltrigger, plugins, react, frameworks, utils, performance. |
| [dietrichgebert/ponytail](https://github.com/dietrichgebert/ponytail) | `2ed6c52` | 6 | Technical-debt discipline. `ponytail-debt` harvests `ponytail:` comments into a ledger — the convention Dynamic Bubble already writes. |
| [leonxlnx/taste-skill](https://github.com/leonxlnx/taste-skill) | `ccbc156` | 14 | Design taste: `taste-skill`, `brandkit`, `redesign-skill`, `minimalist-skill`, image-to-code, imagegen. |
| [199-biotechnologies/motion-dev-animations-skill](https://github.com/199-biotechnologies/motion-dev-animations-skill) | `3feedfb` | 1 | Motion.dev (Framer Motion successor) for React, Next, Svelte, Astro. |
| [pbakaus/impeccable](https://github.com/pbakaus/impeccable) | `09506a9` | 1 | Frontend design quality — audit, critique, polish, animate. v4.1.2. |
| [nidhinjs/prompt-master](https://github.com/nidhinjs/prompt-master) | `2bd9251` | 1 | Prompt engineering, with a credential-safety rule. |

`vados-dna`, `ui-ux-pro-max` and `stop-slop` are not vendored — they are this
repository's own and predate this file.

## Skills, not plugins

`settings.json` once enabled ten plugins. **None of them arrived in a Claude Code web
session**: `~/.claude/plugins/` held a single bucket file and nothing else, so `ponytail`
was unavailable until it was vendored and no `gsap-*` skill ever appeared. Vendored
skills loaded every time.

So the rule here is **vendored skills are the floor that travels; plugins are a local
convenience that may not arrive.** GSAP is vendored for that reason, and
`gsap-skills@gsap-skills` was removed from `enabledPlugins` along with its marketplace
entry so it cannot load twice on machines where plugins do resolve.

Nine plugins remain enabled, `ponytail@ponytail` among them — which *is* now vendored, so
on a machine where plugins resolve it will appear twice. Worth pruning; left alone here
because it is a decision about a local setup rather than about this library.

## What was removed, and why

68 style presets from [bergside/awesome-design-skills](https://github.com/bergside/awesome-design-skills)
were vendored and then removed. They were not dropped for being web-only — they were
platform-agnostic token tables and would have transferred to desktop and mobile work
fine. They were dropped for being generated and redundant:

- **49% of their bytes were byte-identical boilerplate**; 36 of the 68 shared one
  literally identical body from `## Accessibility` to end of file.
- **The generator leaked into the output.** `glassmorphism`'s Brand line described a chat
  app; `pacman`'s was arcade ad copy; `material`, `editorial` and `brutalism` had empty
  Brand lines. Every preset declared weights `100–900` and radius `4px/8px` whatever its
  aesthetic.
- Unique content was roughly 400 bytes of a 3.8KB file: seven hex colours, three font
  names, two radii, two spacings.
- **`ui-ux-pro-max` already holds the same ground in better form** — `data/styles.csv` is
  84 styles × 21 columns, beside `colors.csv`, `typography.csv`, `motion.csv` and
  `data/stacks/` for SwiftUI, React Native, Flutter, WPF, WinUI 3, Avalonia and Jetpack
  Compose. Reach for that rather than re-adding presets.

One name collided during that episode and the resolution outlived it: `impeccable` is
pbakaus's design-quality skill. bergside's identically-named colour preset was installed
as `impeccable-style` and went with the rest.

## Vetting

Every `SKILL.md` was scanned for network calls, credential access, destructive commands
and instructions that redirect the agent. Nothing was found. `motion-dev-animations`
ships one executable, `scripts/validate_motion_config.py`, which is a local config
validator and reaches nothing off the machine.
