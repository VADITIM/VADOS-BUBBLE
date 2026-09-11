# Maintaining this skill

This skill is a living record, not a snapshot. It is expected to grow every time a project teaches
something.

## What goes where

| You learned… | It goes in |
|---|---|
| A platform fought you, and the workaround generalises | `platforms/<platform>.md` — as a *rule*, not a bug report |
| A new project exists, or an existing one changed direction | `map/00-projects.md`, plus a detail file if it is a pillar |
| A visual or motion decision that should hold everywhere | the matching `dna/` module |
| A piece worth using again — a control, an effect, a transition, a loading state | `components/` — a new numbered entry, or a section in the one it belongs to |
| A decision that holds in **both registers** | the `dna/` module — never both trees |
| A decision that is only true of the **professional register** | the matching `pro/` module |
| A one-off fix specific to one repo | **that repo's own docs**, not here |

The PRO test, before writing anything into `pro/`: *is this a different rule, or the same rule in a
different register?* A different rule that holds everywhere belongs in `dna/`, where both registers
get it. Only a genuine register difference — a value, a ceiling, a demoted pattern — belongs in
`pro/`. A base rule restated in `pro/` without being narrowed or contradicted is duplication, and
duplication is what the delta-overlay shape exists to prevent.

`pro/` files open by naming the base module they override, and `pro/00-index.md` accounts for every
base module as overridden or inherited. Adding a `pro/` module means adding its row there in the same
edit, or the next session cannot tell inherited from forgotten. The PRO-only accessibility module is
numbered `11` so the two trees can never collide on a number.

The test for promotion into `dna/`: *would this still be true on a different platform, in a different
language, for a different kind of app?* If not, it is a platform note. If it is only true for one
project, it belongs in that project's `.claude/`.

## How to add a component

A component earns an entry the second time it is wanted, not the first time it is written. New file
in `components/`, numbered, named for the *thing* rather than for the project it came from.
Sections, in order:

1. **What it is**, in one paragraph, and which project it came from.
2. **The layers line** — which of layout / styling / motion / behaviour are worth taking alone.
3. **The code**, in liftable blocks. Real code from a working repo, not a paraphrase; a snippet that
   has never run is a liability in a file people copy from.
4. **The numbers that make it work**, each with the reason it is that number. A constant with no
   stated reason will be "tidied" by the next session.
5. **The traps**, written as the *symptom* someone will arrive with — "reads as stuttering when it
   is actually running perfectly" beats "path length mismatch".
6. **Anti-patterns**, one line each, pointing back at the section that explains them.

Then add a row to `components/00-catalogue.md`. If the component came out of a project, say so in
that project's `map/` file too, so the map and the catalogue point at each other.

## How to add a platform

New file in `platforms/`, named for the engine rather than the project. Sections, in order:

1. **The stance** — what this platform is used for and why it was chosen.
2. **Mapping** — how each DNA mechanism is expressed here (a table).
3. **Pitfalls** — one bold rule per entry, cause stated, symptom named. Write the *rule*, because the
   specific fix is in the code and the changelog already.
4. **What the engine adds** — capabilities the web version does not have, and the DNA's answer for
   them (audio, haptics, particles).
5. **Build/run** — the commands, if they are not obvious.

Then add a row to `map/00-projects.md` and, if it is a pillar, a `map/0N-<project>.md`.

## House style for these files

Same rules as the code: WHY over WHAT, no abbreviations, no filler. A rule is written so that a
future session can *act* on it without re-deriving the bug. Name the symptom, because the symptom is
what someone will arrive with.

Every module ends with an anti-patterns list. Keep it — it is the fastest correctness check on work
already written.

## When updating

Do it in the same task that discovered the thing, and say so in the response. A rule written a week
later is written from memory of a memory.

Keep the numbering stable. `dna/` modules are referenced by number from project code and comments
(`dna/05-motion.md §6.1`), so renumbering breaks references in repos this skill does not control.

## Repository

This skill lives in `https://github.com/VADITIM/agent-config` (branch `global`) alongside the rest
of the global configuration. Changes are committed and pushed there, not left local.

It was called `VADITIM/.claude` until the leading dot turned out to make it unattachable on Claude
Code on the web — the clone path collides with `~/.claude` itself — so a session could not fetch the
DNA at all and worked without it while believing it was bound. It was then `claude-config` for a
while, and is `agent-config` now. Anything pointing at either older name is stale.

Where a project vendors a copy of this skill into its own `.claude/skills/`, that copy is downstream:
edit here, then re-vendor. A fix made only in the vendored copy is a fix that exists in one repo and
looks like it exists everywhere.
