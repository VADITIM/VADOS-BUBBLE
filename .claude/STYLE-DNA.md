# Style DNA

The VADITIM Style DNA is global. It lives in the `vados-dna` skill, in the global config repo:

- `.claude/skills/vados-dna/` — vendored into this repository, so it is here whether or
  not the global config has been fetched. Upstream is
  <https://github.com/VADITIM/claude-config>, branch `global`, which is the root of
  `~/.claude`. The repository was called `.claude` until a leading dot turned out to be
  unattachable on Claude Code on the web; that old URL is dead.
- `dna/` holds the modules (palette, typography, motion, …).
- `map/` indexes every project; `platforms/` holds the per-engine pitfalls.

Say **"use VADOS DNA"** to load it.

Two of its rules are broken most often here, so they are repeated: text that runs out of room
**fades out, never ellipsis**, and a size is never faked with `transform: scale()` — the layout box
keeps its old size and the next transition overwrites the scale.
