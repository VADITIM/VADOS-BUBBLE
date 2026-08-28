# Module 07 — Architecture and state

Not CSS at all, but it is where the consistency actually comes from. The visual rules survive because
the structure makes breaking them inconvenient.

## The registry is the single source of truth

One ordered array defines every screen: id, label, accent colour, group, component, and its animation
registration hook.

```ts
export const SECTIONS: SectionDefinition[] = [
  { id: 'perks', label: 'PERKS', color: '#FFDD1B', group: 'main', component: PerksPage,
    registerAnimations: registerPerksAnimations },
  // ...
]
```

Rules that fall out of it:

- **Indices are never hardcoded.** Always resolve by id (`getSectionIndexById('logs')`). Indices move
  as things lock, unlock and get added.
- **Adding a screen is appending one entry.** Nothing else changes — the nav, the key bindings, the
  digit range, the accent switching and the transition labels all derive from the array.
- **The one field with no safe default is stated deliberately.** An appended entry silently joins
  whichever group the one above it is in, so `group` must always be written out. Every registry should
  identify which of its fields is dangerous to infer.
- **Data-driven entries use getters**, so a template reading them tracks the underlying state and
  re-renders when it changes.

Generalise this to any app: levels, tools, tabs, panels, weapon types, dialogue nodes. One ordered
declaration, everything else derived.

## Module layout

Flat, prefix-grouped module names, so the directory sorts into its own subsystems without nesting:

```
sections*   — screen definitions, state machine, per-screen animation handlers, transitions
animation*  — the shared motion vocabulary (handler, floating, lite fallback)
misc*       — cross-cutting utilities (viewport, label reveal, typewriter, storage, locale)
visitor*    — one subsystem's state (session, fingerprint, device, signature, notices)
```

A file's prefix tells you which subsystem owns it, and `ls` groups them for free. One component or
class per file; named exports over default.

## State: modules own their state, components read it

State lives in a module, not a component. Components read reactive values and call the module's
functions. This is why the animation handlers can be registered globally and still address elements a
component owns.

- Persisted choices go through **one safe-storage wrapper** — never raw platform storage, which throws
  in private modes and on quota.
- A choice that changes how handlers register is **read at registration time and applied with a
  reload**, not switched live. Live-switching a motion mode means every handler needs a teardown path
  that nothing exercises.
- **Cycle discipline:** when two modules need a shared primitive, the primitive gets its own module.
  Importing back into a module that already imports you means whichever the bundler evaluates second
  reads uninitialised exports — a silent, ordering-dependent failure.

## Config lives at the boundary it configures

- Design tokens: one variables file, emitting nothing.
- Motion constants: exported from the module that owns the choreography, imported by anything that
  must stay in sync with it. `LITE_ENTER_GATE` is exported by the lite module and consumed by the
  transition gate precisely so the two cannot drift.
- Breakpoint numbers used for *animation fine-tuning* (not layout) are one exported object.
- Nothing is duplicated to avoid an import.

## Documentation follows code, in the same task

When a change renames or moves modules, or alters a documented convention, the docs change in the same
commit. A rules file that describes last month's architecture is worse than no rules file — it is
confidently wrong.

Layered docs, each with a job:

| File | Holds |
|---|---|
| `CLAUDE.md` | Commands, key decisions, don'ts. The orientation page. |
| `.claude/rules/*.md` | Per-domain conventions, path-scoped (frontend, animations, architecture, code quality). |
| `TASKS.md` | The backlog and known issues. Notes raised while working elsewhere go here, not into code. |
| `DECISIONS.md` | Shipped work and the reasoning behind it. |
| `.claude/style-dna/` | This — the portable identity, independent of any one project. |

## Build and verification

- Type checking is a real command, run against the app config specifically (a bare invocation against
  a solution-style root config checks nothing — verify the check actually checks).
- Project-specific invariants get project-specific CI checks. The custom-property check (§ Module 05)
  is the model: a class of silent failure the type system cannot see, turned into a failing command.
- Build output is generated, never hand-edited, and lives in its own directory and branch.

## Anti-patterns

Hardcoded indices. Config duplicated to dodge an import. State owned by a component that another
component needs. Raw platform storage. Circular module imports. Docs updated "later". A verification
command that silently verifies nothing.
