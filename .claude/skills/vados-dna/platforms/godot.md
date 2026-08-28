# Platform — Godot (4.x, GDScript and C#/Mono)

**Reference projects:** Anomaly (2D ARPG, C#), Velvet Deck (mobile, 4.4 Mono/.NET), Carcinize Crab
Mentality (first 3D project, UI-animation heavy), godot-mcp-tool (agent tooling for the engine).

Anomaly has its own dedicated skills for conventions and lore (`anthropic-skills:code-anomaly`,
`anthropic-skills:lore-anomaly`) — those win inside that project. This file is the DNA's side.

## Why it ports well

The design is already game-menu shaped. It was *built* as a game menu that happens to run in a
browser, so moving it into an engine removes an impedance rather than adding one.

## Mapping

| DNA concept | Godot |
|---|---|
| `--section-color` | An autoload exposing the accent plus a change signal, or a `Theme` type variation swapped at runtime. Every `Control` reads it; none holds it. |
| `color-mix()` | `Color.lerp()` toward the base. Never a second authored colour. |
| The scale lever | One `uiScale` float on the root container; every size a multiple of it. Recompute from `get_viewport_rect()` on resize. |
| Device axis | `DisplayServer.is_touchscreen_available()` |
| Layout axis | Viewport aspect ratio, re-evaluated on resize |
| GSAP timeline, absolute positions | `Tween` with an explicit `set_delay()` per step — **not** chained `chain()`. Or an `AnimationPlayer` track. |
| `overwrite: 'auto'` | `kill_tweens_of(target)` before building |
| Scoped stylesheet | Per-scene `Theme` overrides, with shared values in **one** resource, never duplicated per scene |

## Rules that carry over unchanged

- **Screens are always-loaded `Control` nodes toggled by a state machine, never `queue_free`d.** Same
  reason the web version never unmounts: a tween needs a live target.
- **The curtain is a `CanvasLayer` above everything**, driven by the same close/hold/open timings.
- **Enter and leave in the same commit.** In an `AnimationPlayer` this is visible in the file: name
  them `enter`/`leave` so a missing half is obvious at a glance.
- **Idle loops pause when their screen is inactive.**
- **One ordered registry** generates the menu, the accents and the shortcuts.

## What the engine adds

**Audio follows the three-phase law.** An enter sound, an optional idle bed, a leave sound — and the
leave sound is shorter, exactly like the motion. A screen with an entrance sting and a silent exit
has the same problem as a reveal with no exit animation.

**Haptics are available and should punctuate completions**, not starts.

**Particles and shaders are ambience** under `dna/05-motion.md`'s definition, which means they are the
first thing a low-spec path drops. Decide the low-spec path when you add them, not later.

## Data authority (from Anomaly's conventions, worth applying generally)

Every value belongs in exactly one of three places, and picking the wrong one is the most common
structural mistake in an engine project:

- **Resource** — data shared by many instances, editable without touching code.
- **`[Export]` / exported field** — per-instance tuning a designer sets in the inspector.
- **Hardcoded constant** — a value that is part of the logic and has no business being tuned.

## Agent tooling

`godot-mcp-tool` exposes the engine to an agent as 99 structured tools across scenes, scripts,
shaders, animation, InputMap, audio, navigation, UI and runtime automation, plus an in-editor bridge
addon for live control and screenshots. **Use structured tools rather than hand-editing `.tscn`,
`.tres`, `.gd` or `project.godot`** — those formats are easy to corrupt by guessing.
