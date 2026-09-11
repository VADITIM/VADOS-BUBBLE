# PRO 11 — Accessibility

**PRO-only.** This module overrides nothing; it is a requirement the base register never had to meet.
A portfolio is visited; a support tool is *assigned*. The person using it did not choose it, cannot
leave it, and may be doing so with a keyboard only, at 200% zoom, with a screen reader, or with
deuteranopia — which affects roughly one man in twelve, and a support desk is not a small sample.

Numbered `11` so it cannot collide with a future base module. It is read early in a project, not
late: focus order and contrast are the most expensive things to retrofit in this entire document.

## The target

**WCAG 2.2 Level AA**, treated as the floor rather than the goal. Where a company mandates
something stricter (BITV 2.0 and EN 301 549 both apply to German public-sector work and to anyone
selling into it), that wins.

## Contrast, against a near-black ground

The dark ground helps text and hurts everything else. Every number below is measured against
`#181818` unless stated.

| Element | Ratio | Status against `#181818` |
|---|---|---|
| `text-primary` `rgba(255,255,255,.87)` | ~13.8:1 | passes |
| `text-body` `#d8d8d8` | ~11.2:1 | passes |
| `text-muted` `#9a9a9a` | ~5.6:1 | passes AA for body text |
| `text-label` `#8a8a8a` | ~4.5:1 | **at the floor.** Legal for the micro-label, not for anything an operator must read |
| `text-icon` `#6a6a6a` | ~2.6:1 | **fails.** Decorative marks and placeholders only, never information |
| `text-faint` `#4a4a4a` | ~1.6:1 | **fails.** Never carries meaning. The base DNA's caption use is decorative |
| `border` `#262626` | ~1.2:1 | invisible as a boundary to some operators — see below |

Three rules follow, and they are the ones that get broken:

- **`text-icon` and `text-faint` never carry information.** A required-field marker, an error hint, a
  disabled-but-still-meaningful value — none of these may live at those steps. Reach for `text-muted`
  and change the layout if it looks too loud.
- **The hairline cannot be the only boundary.** "The border is the design" is a visual law, not an
  accessibility one. Every panel and every input also carries a background step (`sunken` `#1c1c1c`
  against `panel`), so the boundary survives for someone who cannot resolve a 1.2:1 hairline.
- **Non-text contrast is 3:1.** Focus rings, control borders, chart series, status dots, icon-only
  buttons. `border-control` `#3a3a3a` is ~2.1:1 and is **not** sufficient as the only indication of
  an interactive control; pair it with the shape, the fill, or a stronger border on focus.

Every accent in [`01-palette.md`](01-palette.md) is chosen to clear 4.5:1 against `#181818` as text
and 3:1 as a boundary. **An accent substituted from a company brand deck is re-measured, not
assumed** — logo colours are chosen against white paper and usually fail here by a wide margin.

## Colour is never the only carrier

Restated from `01-palette.md` because it is the single most common accessibility failure in tools
that encode state:

- Every status colour is paired with **a word or a distinct glyph**. `● Offen`, not a green dot.
- Chart series are distinguished by **shape, dash pattern or direct labelling**, not by hue alone.
  A legend that maps hue to meaning is a legend that fails.
- **A validation error is text next to the field**, not a red border. The border may also turn red.
- Required fields are marked with a word or a persistent marker, never by colour.

## Structure and semantics

- **One `<h1>` per screen, and heading levels never skip.** The screen title is the `h1`, panel
  micro-labels are the section headings. The micro-label is *visually* small and *structurally*
  a heading — that is what makes it navigable by a screen reader's heading list, and it is a free
  win from an element the DNA already has.
- **The panel primitive carries its own semantics.** A panel is a `<section>` with
  `aria-labelledby` pointing at its micro-label. A panel that is a dialog is `role="dialog"`
  `aria-modal="true"` with focus trapped and returned.
- **Tables are `<table>`.** A grid of `<div>`s loses row and column association, which is the entire
  navigational affordance of a data table. `<th scope>` on both axes; a caption naming what the table
  holds, visually hidden if the micro-label already says it on screen.
- **Icon-only controls carry an accessible name**, and their tooltip is not it — a tooltip is a
  hover affordance and hover is not available to every operator.
- **Landmarks:** one `<main>`, navigation in `<nav>`, and a skip link as the first focusable element.
  In a tool with a persistent sidebar the skip link is used constantly, not decoratively.

## Live regions — what a support tool actually needs

State changes without an operator action are common here: a ticket arrives, a sync finishes, a timer
crosses a threshold, a save succeeds.

- **`aria-live="polite"`** for anything informational. Announces at the next pause.
- **`aria-live="assertive"`** only for something that interrupts the operator's task — a lost
  connection, a failed save, a session about to expire. Assertive interrupts speech mid-word; using
  it for a success toast is hostile.
- **The region exists in the DOM before the message does.** A live region injected together with its
  content is not announced — the most common reason a correct-looking implementation is silent.
- **Announce the change, not the whole panel.** `"Ticket 4417 gespeichert"`, not the re-rendered form.
- **A toast is not a live region by default.** If it carries information the operator needs, it is
  wired to one; if it does not, it should not exist.

## Motion and `prefers-reduced-motion`

The PRO timing table in [`05-motion.md`](05-motion.md) is the input to this, not the base one.

- **Reduced motion removes movement, never feedback.** A press still acknowledges — the border and
  fill change instantly instead of the transform overshooting. The base DNA's warning applies: a
  drift under a time scale *shakes*, which is worse than not moving. Early-return, do not time-scale.
- **Every animated element is registered in the reduced path in the same commit as the full path.**
  The base DNA already states this as a correctness rule (a missing branch strands content on screen);
  in PRO it is also a legal one.
- **No animation flashes more than three times per second.** Nothing in this system should come near
  that, but an error state that pulses is exactly where it happens.
- **Auto-advancing anything is pausable** — a carousel, a refresh countdown, a rotating dashboard.

## Zoom, sizing and input

- **Reflows at 400% zoom / 320 CSS px** without horizontal scrolling, apart from data tables, which
  are permitted to scroll horizontally inside their own container. The base DNA's fluid-scale lever
  (`dna/04`) is the mechanism; PRO adds the requirement that it be *tested* at the top of the range.
- **Text is resizable to 200%** without loss of content. Nothing is sized so that a larger root font
  clips it — the base module's reserved-space rule is what makes this hold.
- **Target size 24×24 CSS px minimum**, 44×44 where the layout allows. A dense table row's inline
  actions are the usual offender; give them padding rather than shrinking the icon.
- **No keyboard trap** anywhere except an open modal, which always releases focus to its opener.
- **A timeout warns before it expires** and can be extended. A session that drops an operator's
  half-written ticket is a data-loss bug wearing an authentication costume.

## The debug modal is part of this

The project-level rule that replaces devtools with an in-app debug modal has an accessibility
consequence worth stating once: **the debug modal is a modal like any other.** Focus trapped, focus
returned, `Escape` closes it, its output is selectable text rather than a canvas, and its error
messages are readable at `text-body`, not `text-faint`. A diagnostic surface that only a sighted
mouse user can operate is a diagnostic surface that half the team cannot file a bug from.

## Verification

Automated checks catch roughly a third of this. Do all four:

1. **Automated:** axe or Lighthouse in CI, on every screen, failing the build on new violations.
2. **Keyboard only:** unplug the mouse and complete a real task end to end — find a customer, open a
   ticket, log time, save. Every step, or it is not done.
3. **Screen reader:** NVDA on Windows is the realistic target for a German office. Read one screen
   of each kind — a form, a table, a dialog.
4. **Zoom:** 200% text and 400% page, on the densest screen in the app.

## Anti-patterns

Information at `text-icon` or `text-faint`. A status carried by hue alone. A hairline as the only
boundary. A `<div>` grid pretending to be a table. An icon button whose only name is a tooltip. A
live region injected with its content. `assertive` on a success message. Reduced motion implemented
as a time scale. A focus ring below 3:1. A brand colour dropped in without re-measuring. A modal that
does not return focus. A session timeout with no warning. Treating any of this as a final-sprint
task.
