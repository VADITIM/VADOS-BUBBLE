# PRO 01 — Palette

Overrides [`../dna/01-palette.md`](../dna/01-palette.md). Read that first — the rule, the ground and
structure table, the text ramp, the derived-colour helpers and the marks-in-running-text section all
still apply exactly as written. This file changes two things: **which accents exist**, and **the
addition of a status role**.

## What is inherited unchanged

- **Ground and structure.** `#181818`, the panel translucency, the sunken and raised fills, and the
  four border steps. This is already the most professional part of the DNA and there is nothing to
  soften. A PRO app is the same near-black app.
- **The five-step text ramp.** Including the ban on pure `#ffffff` for prose.
- **Derived colour.** `color-mix` for every tint, `isLightColor()` for black-vs-white on a fill,
  `desaturate()` for muting. PRO leans on these *more* than the base does, because a professional
  palette lives or dies on its tints.
- **Marks inside running text.** Link blue, date band, money band. A support tool renders customer
  messages and log lines constantly, which is precisely the case that section was written for.

## What changes: the accents

The base accents are electric and deliberately unbalanced — "a menu of distinct rooms". PRO keeps
the *mechanism* (one accent per screen, repointed at the root, never named by a leaf) and replaces
the *set*. These are chosen to sit at roughly equal chroma against `#181818`, so a screen change
reads as a change of subject rather than a change of voltage.

| Domain | Hex | Character |
|---|---|---|
| Default / system / neutral screens | `#4d8ac9` | Steel blue — the identity colour of the PRO register |
| Records and directories | `#3f9e8f` | Teal |
| Planning and scheduling | `#6a6fc9` | Indigo |
| Money, billing, invoicing | `#b3853f` | Amber |
| Administration and settings | `#7f8b99` | Slate |
| Reporting and analysis | `#6f9457` | Moss |
| Anything remaining | `#9a6f9e` | Plum |

Custom accents follow the base rule: validated `/^#[0-9a-fA-F]{6}$/`, falling back to steel.

**The domain column is a suggestion; the count is not.** Seven accents, one per screen, is the
ceiling. An eighth colour means two screens have to share, not that the palette grows.

**Chroma ceiling.** A PRO accent's saturation stays under roughly 60%. This is the single test that
separates the two registers at a glance: if an accent glows against the ground, it belongs to the
base DNA. `#5bfd5b` is the base identity colour and is never a PRO accent — not even as a success
state, which is what `status-ok` is for.

### Accent-tinted grounds

The base rule survives — **tint, never swap** — but the budget shrinks. PRO tints the ground by no
more than a couple of points (`#181a1c` under steel, `#181a19` under teal). A room that is visibly
coloured is a room someone has to look at all day.

## What is added: the status role

A support tool encodes state in almost every row it renders — a ticket is open or closed, an invoice
is paid or overdue, a sync succeeded or failed. The base DNA has no answer for this, because a
portfolio has no state to encode; it has one accent and one danger colour and that is enough.

Status is **a sixth role, fixed globally**, on the same footing and for the same reason as the
marks-in-running-text exception in the base module: its whole value is that the reader learns it
once and never re-learns it per screen.

| Token | Hex | Means |
|---|---|---|
| `status-ok` | `#5aa86f` | Done, paid, passing, in sync, active |
| `status-warn` | `#c9a227` | Due, pending review, degraded, approaching a limit |
| `status-error` | `#cf5c5c` | Failed, overdue, rejected, out of sync |
| `status-info` | `#4d8ac9` | Informational, in progress, queued |
| `status-neutral` | `#7f8b99` | Archived, cancelled, not applicable, unknown |

Three rules, all of which are the sort of thing that goes wrong quietly:

- **Status never takes the screen accent, and the screen accent never takes a status colour.** On the
  steel screen, `status-info` and the accent are the same hex and that is fine — they are still two
  roles, and the day the accent changes they must not move together.
- **Status is never the only carrier.** Every status colour is paired with a word or a glyph. A
  colour-blind operator, a greyscale print of an invoice list and a screenshot pasted into a ticket
  all lose the hue and none of them may lose the meaning. See [`11-accessibility.md`](11-accessibility.md).
- **Status is a fill or a band, never a text colour on a data row.** Coloured text in a table column
  is unreadable at `#5aa86f` on `#181818` at small sizes; a `12%` tinted chip with the ramp's own
  text colour is readable and stays inside the contrast floor.

```css
.status-chip {
  background: color-mix(in srgb, var(--status) 14%, transparent);
  border: 1px solid color-mix(in srgb, var(--status) 34%, #262626);
  color: #d8d8d8;
}
.status-chip::before { background: var(--status); }  /* the 6px dot carries the hue */
```

## Danger, narrowed

The base danger ramp (`#ff6b6b` / `#DC143C` / `#ff2b2b`) is too hot for a screen an operator reads
all day, and its top step collides with the crimson accent that PRO does not have anyway.

| Token | Hex | Use |
|---|---|---|
| `danger` | `#cf5c5c` | Destructive controls, error text, error borders |
| `danger-hard` | `#a83232` | The filled state of a destructive button |

Two steps, not three. The base's third step existed to shout about irreversibility; PRO handles
irreversibility with a differently-worded confirmation (base Module 06) rather than a redder red.
`danger` and `status-error` are the same hex on purpose — a failure and a destruction are the same
news to the person reading the row.

## Anti-patterns

Everything in the base list, plus: an accent above the chroma ceiling. A status colour used as a
screen accent, or vice versa. Status carried by hue alone. Coloured status text in a table cell. A
"brand colour" from a company deck dropped in without checking it against the ground and the contrast
floor — a logo colour is chosen against white paper and almost never survives `#181818` unadjusted.
Adding an eighth accent instead of making two screens share.
