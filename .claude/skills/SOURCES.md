# Where these skills came from

Every folder beside this file was vendored from a public repository at the commit
named below. Nothing here was written for this project, and nothing here outranks
`CLAUDE.md` or the files in `.claude/rules/` — where a vendored skill and a project
rule disagree, the project rule wins, because it was written against this phone.

Re-vendoring is a fresh clone at a newer commit and a diff, not a merge: these are
upstream files and no local edit should ever be made in place. A change worth keeping
belongs in `.claude/rules/`.

| Source | Commit | Skills | What it is |
|---|---|---|---|
| [bergside/awesome-design-skills](https://github.com/bergside/awesome-design-skills) | `f631a09` | 67 | Visual style presets — one aesthetic each (`neon`, `brutalism`, `editorial`, `claymorphism`, …). Web-oriented. |
| [leonxlnx/taste-skill](https://github.com/leonxlnx/taste-skill) | `ccbc156` | 14 | Design taste and judgement: `taste-skill`, `brandkit`, `redesign-skill`, `minimalist-skill`, image-to-code. |
| [emilkowalski/skills](https://github.com/emilkowalski/skills) | `d23d7f8` | 12 | **The set that matters most here.** `apple-design`, `animate`, `improve-animations`, `review-animations`, `animation-vocabulary`, `find-animation-opportunities`. |
| [dietrichgebert/ponytail](https://github.com/dietrichgebert/ponytail) | `2ed6c52` | 6 | Technical-debt discipline: `ponytail`, `-review`, `-audit`, `-debt`, `-gain`, `-help`. This codebase already writes `ponytail:` comments; this is where that convention comes from. |
| [pbakaus/impeccable](https://github.com/pbakaus/impeccable) | `09506a9` | 1 | Frontend design quality — audit, critique, polish, animate. v4.1.2, user-invocable. |
| [nidhinjs/prompt-master](https://github.com/nidhinjs/prompt-master) | `2bd9251` | 1 | Prompt engineering, with a credential-safety rule. |

## Two things that were changed on the way in

- **`impeccable` collided.** `bergside`'s is a cream-and-burnt-orange style preset;
  `pbakaus`'s is the design-quality skill. They share nothing but a name, so the
  preset is installed as **`impeccable-style`** and the name `impeccable` is
  pbakaus's, which is the one the repository was asked for by URL.
- **Ponytail's hooks were not installed.** `hooks/*.js` and `hooks/*.sh` in that
  repository run on Claude Code lifecycle events and only do anything once wired into
  `settings.json`. Wiring an upstream script into this project's hooks is a separate
  decision from reading its skills, so only `skills/` was taken.

## Vetting

All 100 `SKILL.md` files were scanned for network calls, credential access,
destructive commands and instructions that redirect the agent. Nothing was found:
every hit on `API_KEY`, `secret` or `credential` is a benchmark README describing the
upstream project's own test harness, or `prompt-master`'s rule *forbidding*
credentials in generated prompts.

## A note on the cost

100 skill descriptions load into the context of every session in this repository.
Roughly two thirds are web-page style presets with no bearing on an Android status-bar
overlay drawn in one WebView. If sessions start feeling crowded, the honest fix is to
keep `emilkowalski`, `ponytail`, `impeccable`, `taste-skill` and `prompt-master`, and
drop the presets — which is a `git rm` of the folders listed under `awesome-design-skills`
above and nothing else.
