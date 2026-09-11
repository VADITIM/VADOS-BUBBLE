# VADOS DNA — PRO

The professional register of the VADOS DNA. Same system, same laws, company voice.

The base DNA (`../dna/`) is built to make an impression: electric accents, display faces, a screen
that assembles diagonally over a second and a half, a curtain between every screen. That is correct
for a portfolio and wrong for a tool someone is paid to sit in front of for eight hours. PRO keeps
the identity and drops the theatre.

**PRO is not a softer DNA. It is the same DNA aimed at a different room.** The panel, the hairline,
the micro-label, the three-phase law, the asymmetry law and the one-accent rule all survive
untouched — they are what makes the work recognisable. What changes is the register: colours that a
company can put in front of its own customers, faces that survive a 400-row table, and motion that
still punches but no longer costs the operator time.

## The inheritance contract

**PRO is a delta, not a copy.** Every file here overrides exactly one base module, and it opens by
naming it. Read the base module **first**, then this one; where they disagree, this one wins. Where
this one is silent, the base module is still in force.

A rule true in both registers lives in `../dna/` and is fixed there once. A rule that is only a
register difference lives here. Nothing is duplicated between the two — if a base rule appears here
restated, it is because PRO narrows or contradicts it, and the file says which.

## Override table

| Base module | PRO | What changes |
|---|---|---|
| `dna/00-index.md` | *this file* | The quick-reference card below replaces the base card |
| `dna/01-palette.md` | [`01-palette.md`](01-palette.md) | Accents desaturated; a status role added. Ground, grey ramp and text ramp **inherited unchanged** |
| `dna/02-typography.md` | [`02-typography.md`](02-typography.md) | The four faces replaced by two; the micro-label survives, tightened; density rules added |
| `dna/03-surface.md` | — | **Inherited whole.** The hairline panel is already professional; it is the part of the identity that needed no fork |
| `dna/04-layout-and-sizing.md` | — | **Inherited whole**, with one narrowing noted in `06-interaction.md`: a data tool scrolls |
| `dna/05-motion.md` | [`05-motion.md`](05-motion.md) | Timings roughly halved, ambience dropped, the curtain reserved. All four motion laws kept |
| `dna/06-interaction.md` | [`06-interaction.md`](06-interaction.md) | Keyboard-first, scrolling restored, hover legitimate, the toggle primitive |
| `dna/07-architecture.md` | — | **Inherited whole.** The registry-as-source-of-truth holds in any register |
| `dna/08-voice.md` | — | **Inherited whole** in structure; PRO writes plainly and never jokes in an error |
| `dna/09-code-style.md` | — | **Inherited whole** |
| `dna/10-porting.md` | — | **Inherited whole** |
| — | [`11-accessibility.md`](11-accessibility.md) | PRO-only. Not an override; a requirement the base register never had to meet |

No base module is unaccounted for: seven are inherited whole, four are overridden, one PRO module
is new.

## The components library

`../components/` is **not a register**. It holds the pieces already built — markup, CSS, JS and their
traps — and every one of them was written in the base register, because that is where they came from.

A PRO project reads a component in layers, the way its own catalogue says to:

| Layer | In PRO |
|---|---|
| **Layout** | Take as written. Box structure is register-neutral |
| **Behaviour** | Take as written. Gestures, state and the host contract do not change register |
| **Styling** | Re-read through [`01-palette.md`](01-palette.md) and [`02-typography.md`](02-typography.md). The structure survives; the accents and faces do not |
| **Motion** | Re-read through [`05-motion.md`](05-motion.md). Halve the timings, drop the ambience, keep the punch on consequence |

Three entries are **base-only** and PRO does not use them, because `05-motion.md` drops the intent
they serve:

- `components/09-typewriter.md` — a tool does not type at its operator.
- `components/10-magnetic.md` — a pointer field is ambience, and ambience is what PRO spends its
  budget away from. The press feedback a magnetic button carries is worth keeping; the field is not.
- `components/04-screen-transition.md` — the diagonal slices and positional stagger are exactly what
  PRO replaces. Its *curtain* half survives, reserved for context switches only.

Two are load-bearing in PRO and need no change beyond their styling layer:

- `components/11-module.md` — the universal panel. `dna/03-surface.md` is inherited whole, so this is
  the one component that arrives already correct.
- `components/07-skeleton-and-toast.md` — a tool waits on the network constantly, so a loading state
  and a slow-request notice matter *more* here than in the base register, not less. The toast's
  contents are diagnostics, so [`11-accessibility.md`](11-accessibility.md)'s live-region rules apply.

`components/08-text-reveal.md` is the base default for all arriving text. In PRO it is demoted to an
accent — a screen title, a value that just changed — and never used on body text or table cells.

`components/05-controls.md` holds the base toggle. PRO specifies its own in
[`06-interaction.md`](06-interaction.md); take the controls entry's markup and press mechanics, not
its motion.

## The five laws — unchanged

They are the reason this is still the same DNA. PRO does not touch them.

1. **The border is the design.** Depth from a 1px hairline and translucency, never a shadow.
2. **One accent per screen, inherited from the root.** A leaf component never names a colour.
3. **Every element has a start, maybe a middle, and always an end** — the end is faster, immediate,
   and never a reversed start.
4. **A difference of amount is a ramp; a difference of kind is a branch.** Widths are neither.
5. **If you cannot name an animation's intent, it does not ship.**

## The sixth law, PRO only

6. **The operator's time is the budget.** Every animation is spending someone's working day. A
   motion that delays information is a cost with no return, and PRO pays it only where the motion
   *is* the information — a state changing, an action landing, a context switching.

This is what actually separates the two registers. The base DNA optimises for the first thirty
seconds of a visit; PRO optimises for the thousandth hour of use.

## Quick reference card

Replaces the base card. Everything not listed is the base value.

```
GROUND    #181818          PANEL  rgba(18,18,18,.85)   BORDER  #262626   [inherited]
TEXT      rgba(255,255,255,.87) → #d8d8d8 → #9a9a9a → #8a8a8a → #6a6a6a  [inherited]

ACCENTS   #4d8ac9 steel (default)  #3f9e8f teal  #6a6fc9 indigo  #b3853f amber
          #7f8b99 slate  #6f9457 moss  #9a6f9e plum
STATUS    #5aa86f ok · #c9a227 warn · #cf5c5c error · #4d8ac9 info · #7f8b99 neutral
DANGER    #cf5c5c / #a83232                                        [narrowed from base]

FACES     Inter (display · UI · prose) · JetBrains Mono (data · labels · code)
LABEL     Mono 0.6875rem · uppercase · letter-spacing .12rem · #8a8a8a · panel top-left
FIGURES   font-variant-numeric: tabular-nums on every number that sits above another number
RADIUS    0.75rem on boxes · 0 on text                             [inherited]

ENTER     delay 0.12s · 0.18–0.32s · power3.out
LEAVE     delay 0     · 0.12–0.20s · power2.in
STAGGER   0.02–0.05s · capped at 0.15s total, never positional
PRESS     0.09s · back.out(2.2) — the punch survives here and nowhere else
IDLE      none by default. Only a live process may loop, and only while it is running
CURTAIN   context switches only (module change, sign-out). Never routine navigation
```

## Reading order for a new PRO project

`01` → `02` → `../dna/03` (make one panel look right) → `../dna/04` → `05` → `11`, then the rest of
the base as needed. Accessibility (`11`) comes early on purpose: retrofitting focus order and
contrast into a finished tool is the most expensive rework in this document.

## When PRO is the wrong answer

If the thing being built is meant to be *impressive* — a portfolio, a launch page, a demo, anything
where the visitor's attention is the goal rather than the obstacle — use the base DNA. PRO will make
it feel like a settings screen. Point at the register deliberately; do not default to PRO because it
sounds safer.
