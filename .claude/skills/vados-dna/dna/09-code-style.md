# Module 09 — Code style

The style of the code is part of the style of the work. These rules are language-independent; the
naming conventions are the only part that changes shape per language.

## Naming

- **No abbreviations. Ever.** `button` not `btn`, `event` not `e`/`evt`, `element` not `el`, `index`
  not `i` (loop counters included), `timeline` not `tl`, `previous` not `prev`. The only exception is
  an acronym that is a word in its own right: `id`, `url`, `api`, `db`, `auth`. Acronyms are cased as
  words: `userId`, not `userID`. This applies to CSS class names too: `.cartridge-title`, never
  `.cart-title`.
- Booleans: `is` / `has` / `should` / `can`. Functions: verb-first. Factories: `create*`. Converters:
  `to*`. Predicates: `is*` / `has*`. Handlers: `handle*` internal, `on*` as props. Constants:
  `SCREAMING_SNAKE`.
- **A class names what a thing *is*, not where it currently sits.** `.crate`, not `.sb-mod-crate`.
  Section prefixes survive only on a screen's root element. The one deliberate exception is a
  cross-cutting pattern's contract (the label-reveal class names read by a shared module) — renamed
  everywhere or nowhere.
- File conventions here: `Pascal-Hyphen.vue` components, `camelCase.ts` modules, `kebab-case.scss`.
- Retired names stay retired, and the reason is written down. Each of the old width-based mixin names
  described a *device* and was implemented as a *width*; reintroducing one is a bug, not a preference.

## Comments

- **WHY, never WHAT.** If code needs a "what" comment, rename it instead.
- **One line per comment, unbounded length.** A 400-word single line carrying the real context — the
  decision, the trap, the "or else" — is correct and preferred over the same content split across
  several lines. It is one logical comment, so it is one physical line.
- A comment restating the code is deleted, not shortened.
- Markup carries no comments. The markup is the documentation; anything recording a *decision* belongs
  in the script beside the code it constrains, where it survives a markup rewrite.
- API docs at module boundaries only, not on every internal function.
- `TODO(author):` planned work · `FIXME(author):` known bug · `HACK(author):` ugly workaround, with
  the proper fix named · `NOTE:` non-obvious context. Never `XXX`, `TEMP`, `REMOVEME`.

## Region banners

Every script and style block is divided by fold markers, and so is any module long enough to need
orienting:

```
  // #region ── fan geometry ──────────────────────────────────────────────
  // #endregion ───────────────────────────────────────────────────────────
```

The marker is what the editor folds and lists in the outline; the trailing bar is what makes the
divider visible in the minimap. Pad to column 96. Regions never nest and never straddle a block
boundary — the first opens after the imports, the last closes before the block ends.

## Anti-defaults

Written as counters to what an assistant or a hurried afternoon produces by default:

- **No premature abstractions.** Three similar lines beats a helper used once.
- **No features beyond what was asked.**
- **No refactoring adjacent code while fixing a bug.**
- **No dead code, no commented-out blocks.** Version control has history.
- **No interface with one implementation**, no factory for one product, no config for a value that
  never changes.
- **Deletion over addition. Boring over clever** — clever is what someone decodes at 3am.
- A deliberate shortcut with a known ceiling gets a marked comment naming the ceiling and the upgrade
  path, so it is tracked debt rather than an accident.

## File organisation

- Imports grouped: builtins, external, internal, relative, types — blank line between groups.
- Exports: named over default. One component or class per file.
- Function order: public API first, then helpers in call order.

## Anti-patterns

Abbreviated identifiers. Comments narrating the code. Location-prefixed class names. An abstraction
with one caller. A bug fix that arrives with a refactor. Commented-out code. A shortcut with no note
of what it costs.
