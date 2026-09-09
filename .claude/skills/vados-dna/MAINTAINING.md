# Maintaining this skill

This skill is a living record, not a snapshot. It is expected to grow every time a project teaches
something.

## What goes where

| You learned… | It goes in |
|---|---|
| A platform fought you, and the workaround generalises | `platforms/<platform>.md` — as a *rule*, not a bug report |
| A new project exists, or an existing one changed direction | `map/00-projects.md`, plus a detail file if it is a pillar |
| A visual or motion decision that should hold everywhere | the matching `dna/` module |
| A one-off fix specific to one repo | **that repo's own docs**, not here |

The test for promotion into `dna/`: *would this still be true on a different platform, in a different
language, for a different kind of app?* If not, it is a platform note. If it is only true for one
project, it belongs in that project's `.claude/`.

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

This skill lives in `https://github.com/VADITIM/claude-config` (branch `global`) alongside the rest
of the global configuration. Changes are committed and pushed there, not left local.

It was called `VADITIM/.claude` until the leading dot turned out to make it unattachable on Claude
Code on the web — the clone path collides with `~/.claude` itself — so a session could not fetch the
DNA at all and worked without it while believing it was bound. Anything still pointing at that name
is stale.

Where a project vendors a copy of this skill into its own `.claude/skills/`, that copy is downstream:
edit here, then re-vendor. A fix made only in the vendored copy is a fix that exists in one repo and
looks like it exists everywhere.
