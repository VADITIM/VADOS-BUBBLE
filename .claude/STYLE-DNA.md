# Style DNA

The VADITIM Style DNA is global. It lives in the `vados-dna` skill, in the global config repo:

- `~/.claude/skills/vados-dna/` — <https://github.com/VADITIM/.claude>
- `dna/` holds the modules (palette, typography, motion, …).
- `map/` indexes every project; `platforms/` holds the per-engine pitfalls.

This project only uses it when Claude is pointed at it: **"use VADOS DNA"**.

Two of its rules are broken most often here, so they are repeated: text that runs out of room
**fades out, never ellipsis**, and a size is never faked with `transform: scale()` — the layout box
keeps its old size and the next transition overwrites the scale.
