# Project map

Everything VADITIM has built that is worth looking at, what it is for, and what it contributed to
the DNA. All paths are on this machine under `C:\Users\vadim\source\`.

Read a row before rebuilding something it already solved.

## The three pillars

| Project | Path | Platform | Role in the DNA |
|---|---|---|---|
| **PORTFOLIO25** | `PORTFOLIO25` | Vue 3 + TS + GSAP + SCSS, gh-pages | **The baseline.** Every rule in `dna/` was extracted from here. When two projects disagree, this one is right. |
| **Dynamic Bubble** | `Android Dynamic Island` | Kotlin + WebView + Shizuku, Galaxy S25 / One UI 8.5 | The Android port of the identity — and the proof it survives a `WebView`. Owns the Shizuku and overlay-window knowledge. |
| **VAD/OS** | `VADOS` | Tauri v2 + SvelteKit + Rust + GSAP | The identity applied to a *tool used hundreds of times a day*. Owns the "motion must not be an obstacle by the fiftieth time" tuning, and the structured-output architecture. |

Details: `map/01-portfolio25.md`, `map/02-dynamic-bubble.md`, `map/03-vados.md`.

## Everything else

| Project | Path | What it is | Worth stealing |
|---|---|---|---|
| Anomaly | `Anomaly` | 2D top-down Action-RPG, ontological dark fantasy. Godot + C#. Has its own lore and code-convention skills (`anthropic-skills:lore-anomaly`, `code-anomaly`). | Transformation-system design; the Resource-vs-Export-vs-hardcoded data authority rules. |
| Velvet Deck | `Velvet Deck` | Card game for Android 15, Godot 4.4 Mono / .NET. Revision of an earlier web card game. | Godot-on-mobile packaging; touch-first card interaction. |
| Carcinize Crab Mentality | `Carcinize Crab Mentality` | First 3D Godot project — fast-paced run & gun, leaning hard on UI animation. | Early proving ground for the motion language in a 3D engine. |
| Brawl Stars Controller | `Brawl Stars Controller` | Switch Pro Controller → multi-touch bridge for Brawl Stars. C helper on raw evdev + `/dev/uinput`, run as the shell user over adb. | **The shell-UID pattern that later became the Shizuku approach.** Input injection without root. |
| godot-mcp-tool | `godot-mcp-tool` | MCP server for AI-assisted Godot 4.x work — 99 tools across 13 categories, plus an in-editor bridge addon. | How to expose an engine to an agent: structured tools over guessing at `.tscn`/`.tres` formats. |
| Android Widgets | `Android Widgets` | Custom panel widgets. | Widget/glance surface basics. |
| Android Clock Widget | `Android Clock Widget` | Clock widget. | |
| Android Counter Widget | `Android Counter Widget` | Counter widget. | |
| Portfolio2024 | `Portfolio2024` | The previous portfolio. | Historical — superseded by PORTFOLIO25. Do not copy patterns forward from it without checking them against `dna/`. |
| keychronv6 | `keychronv6` | QMK keymap + layout config for a Keychron V6. | Personal keybinding vocabulary. |
| bash | `bash` | Loose scripts (`run_vados_task.sh`). | |

## Cross-project threads

Recurring problems, and where each was actually solved:

- **Privileged actions without root** — Brawl Stars Controller (shell user over adb, `/dev/uinput`)
  → Dynamic Bubble (Shizuku user service running as shell UID). Same idea, second generation.
- **One identity, several renderers** — PORTFOLIO25 (SCSS + GSAP) → Dynamic Bubble (`dna.css` in a
  WebView, one stylesheet for both surfaces) → VAD/OS (Svelte scoped CSS + GSAP). The Android answer
  is the cheapest: host the CSS, do not re-implement the design in native views.
- **Motion that has to survive interruption** — PORTFOLIO25 (fast section navigation) → VAD/OS
  (Ctrl+C mid-reveal, streaming output). Both landed on: store every tween, kill before rebuild,
  absolute time positions.
- **Agent-facing docs as a first-class artefact** — PORTFOLIO25 (`.claude/rules/`), VAD/OS
  (`.claude/README.md` index, `decisions.md`, `tasks.md`, `tests.md`, `docs/QUIRKS.md`), Anomaly
  (lore + code skills). VAD/OS has the most developed version; steal its structure for anything
  long-running.
- **Godot** — Carcinize (3D, UI animation) → Anomaly (2D ARPG, conventions) → Velvet Deck (mobile)
  → godot-mcp-tool (agent tooling). Four angles on the same engine.
