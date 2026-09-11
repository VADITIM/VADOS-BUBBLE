# PRO 02 — Typography

Overrides [`../dna/02-typography.md`](../dna/02-typography.md). Read that first — the app-level
`@font-face` rule, the role-assignment principle, the micro-label, the caption, the rendering block
and the ban on synthesised weights all still apply. This file changes **which faces fill the roles**
and adds the density rules a data-dense tool needs.

## What changes: which faces are cast

The base module's framing does the work here: **roles come first, and faces are cast into them.**
PRO keeps all four roles and recasts them, which is a substitution the base register explicitly
allows rather than a departure from it.

Wosker, Striker and Audiowide are display faces. They are the base register's signature and they are
the wrong tool here for a reason that has nothing to do with taste: a support tool is mostly a grid
of numbers and names, read at speed, in German, by someone who did not choose to look at it.

| Role | PRO face | Replaces |
|---|---|---|
| Display / screen titles | **Inter**, tight tracking (`-0.02em`), weight 600 | Wosker, Striker |
| Headings, nav, buttons | **Inter**, weight 500–600 | Audiowide |
| Prose, form labels, descriptions | **Inter**, weight 400 | Space Mono |
| **Data, IDs, timestamps, money, code, micro-labels** | **JetBrains Mono** | Space Mono |
| Fallback stack only | `system-ui, "Segoe UI", Roboto, sans-serif` / `ui-monospace, Consolas, monospace` | — |

**The default answer flips.** In the base DNA, "if a role is not on this list, it is Mono." In PRO,
**if a role is not on this list, it is Inter** — and mono is the deliberate choice, reserved for
things that are *machine data*: an identifier, a quantity, a time, a path, a status code. That
inversion is most of the register change.

Both faces are variable and have real weights, so the base ban on synthesised bold is satisfied by
the files rather than by discipline.

**Substitution is allowed, one-for-one.** If the company mandates its own faces, the constraint is
the *pairing*: one neutral grotesque with real weights and true tabular figures, one monospace with
a distinguishable `0`/`O` and `1`/`l`. Do not substitute the mono for a second sans — the split
between "prose" and "data" is doing structural work, not decorative.

## The micro-label survives, tightened

It is the most recognisable element of the whole DNA and PRO does not give it up. It is tuned for
legibility rather than for effect:

```scss
.module-label {
  position: absolute; top: 0; left: 0;
  font-family: 'JetBrains Mono';
  font-size: 0.6875rem;      // was 0.63 — a working tool is read at a working distance
  font-weight: 500;
  letter-spacing: 0.12rem;   // was 0.19 — still machine spacing, no longer a poster
  text-transform: uppercase;
  color: #8a8a8a;
  padding: 0.75rem 1rem;
  pointer-events: none;
}
```

The caption (bottom edge, `#4a4a4a`, half-overhanging) is inherited unchanged, and earns its keep
here: a support tool has more to explain than a portfolio does.

## Density — the PRO-only section

A base screen holds a headline and six elements. A PRO screen holds four hundred rows, and every
rule below exists because that number breaks something the base DNA never had to think about.

- **Tabular figures on every number that sits above another number.**
  `font-variant-numeric: tabular-nums;` on tables, totals, timers, invoice columns, ID columns.
  Proportional digits make a column of amounts jitter and make a running timer visibly twitch.
  This is not optional and it is the single most common miss.
- **Line-height splits by role.** `1.5` for prose (inherited), **`1.35` for table rows and dense
  lists**, `1.15` for display. The global `1.5` wastes a third of a screen in a grid.
- **A three-step size scale below the body size, and no more.** `0.8125rem` dense body,
  `0.75rem` metadata, `0.6875rem` micro-label. Anything smaller fails the contrast floor at
  `#9a9a9a` before it fails legibility.
- **Numbers align right, text aligns left, and neither is ever centred in a data column.** Centring
  destroys the ragged edge that lets someone scan a column for an outlier.
- **Long values fade, never ellipsis.** Inherited from the base DNA and restated because it is the
  most-broken rule in the whole document and a table is where it breaks. A mask-image fade on the
  overflowing edge; `text-overflow: ellipsis` is not used anywhere in this system.
- **German is longer than English.** Every label, button and column header is designed against its
  longest real string, not its English one — `Rechnungspositionen` and `Sachbearbeiter` are the test
  cases, not `Items`. A layout that only holds at the English width is an untested layout.

## Uppercase, narrowed

The base uses uppercase for labels, nav entries, buttons and screen names. PRO keeps it for
**micro-labels and column headers only**. Uppercase costs about 10% reading speed on anything longer
than two words, and buttons and nav entries in a working tool are frequently longer than two words —
especially in German.

## Anti-patterns

Everything in the base list, plus: proportional figures in a numeric column. A single global
line-height applied to a data grid. Mono used for prose because the base DNA did. A sans used for an
identifier or a timestamp. Uppercase on a multi-word button. Centred numbers. `text-overflow:
ellipsis`. A font size below `0.6875rem`. A layout measured against English strings.
