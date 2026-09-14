# Style

The style is VAS — VADOS APPLICATION SYSTEMS, VADITIM's cross-project design and application
system. It lives in its own repository and is cloned into `.claude/skills/vas/`:

- <https://github.com/VADITIM/VADOS-APPLICATION-SYSTEMS> — `SKILL.md` sits at the repository
  root, so the clone is the skill directory. It used to be a `vados-dna` skill inside the
  global config repo; that copy is dead.
- `dna/` holds the foundations (palette, typography, motion, …); `components/` the pieces
  already built; `pro/` the professional register; `map/` indexes every project;
  `platforms/` holds the per-engine pitfalls.

Check it for changes before relying on it — `git -C .claude/skills/vas fetch` and pull.

Two of its rules are broken most often here, so they are repeated: text that runs out of room
**fades out, never ellipsis**, and a size is never faked with `transform: scale()` — the layout box
keeps its old size and the next transition overwrites the scale.
