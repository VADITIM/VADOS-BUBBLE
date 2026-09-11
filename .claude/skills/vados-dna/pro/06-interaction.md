# PRO 06 — Interaction and input

Overrides [`../dna/06-interaction.md`](../dna/06-interaction.md). Read that first. The pointer-decides
rule, the hover-on-touch trap, the one-gesture-owns-one-axis rule, `touch-action`, and the feedback
section all still apply. This file changes **the navigation model**, promotes the keyboard to
first-class, and defines the toggle primitive.

## The base premise that does not survive

> "There is no visual scrollbar and no document scroll."

That is correct for a screen-swapping portfolio and impossible for a tool that renders four hundred
invoice positions. **PRO scrolls.** The state machine survives for *screen changes*; within a screen,
content scrolls normally, with a visible scrollbar styled to the border ramp.

What is kept from the base model: **every input path funnels into one change function.** A keyboard
shortcut that manipulates state directly is still how a lock gets bypassed, and a tool has far more
locks than a portfolio — permissions, unsaved changes, a record someone else is editing.

What is dropped: wheel-to-navigate, swipe-to-navigate, and digit-key screen jumping. In a tool the
wheel scrolls, and the number row types numbers into the field the operator is standing in.

## The keyboard is a first-class input, not an accessibility feature

An operator using this eight hours a day will out-type the mouse within a week. Designing for that
is what separates a tool people tolerate from one they defend.

- **Everything reachable by mouse is reachable by keyboard**, in an order that matches the visual
  layout. This is a design constraint on the layout, not a retrofit on the markup.
- **Tab moves between regions; arrow keys move within one.** A table is one tab stop, then arrows
  walk the rows. Tabbing through four hundred rows is the same bug as an uncapped stagger.
- **`Enter` commits, `Escape` cancels, everywhere, with no exceptions.** `Escape` in a dialog with
  unsaved changes asks; it does not silently discard and it does not silently refuse.
- **Shortcuts are discoverable or they do not exist.** A keyboard-shortcut panel, and the shortcut
  printed next to the action in any menu that offers it.
- **A shortcut never overrides a browser or OS shortcut** the operator already relies on.
- **Focus is never trapped except in a modal**, and a modal always releases focus back to the control
  that opened it.

## Focus is a visible, designed state

The base rule — `outline: none` only ever with a replacement in the same rule — is inherited and
tightened into a positive requirement:

```scss
:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
  border-radius: inherit;
}
```

`:focus-visible`, not `:focus`, so a mouse click does not leave a ring behind. The ring uses the
screen accent because that is the one colour guaranteed to be present and to contrast — and it is
the base DNA's own mechanism, inherited from the root, never named by the leaf.

A focused row in a table gets the ring *and* a background step, because a 2px ring around a
full-width row at the edge of the viewport is easy to lose.

## Hover is legitimate here

The base register treats hover with suspicion because it is a touch-first portfolio. PRO is a desktop
tool used with a mouse, and hover is a real affordance again — row highlight, a truncated value's
tooltip, an action revealed on the row it belongs to.

The base constraints still hold, and one is worth restating because it is the one that fails an
audit: **hover is never the only route to information.** Anything a hover reveals is also reachable
by keyboard focus and present on touch. A row action that only appears on hover has to appear on
focus too, in the same commit.

`@include hoverable` still wraps every hover rule, and the non-hover parts of the rule still live
outside the mixin.

## The toggle primitive

The one component PRO specifies outright, because it is the register's signature interaction and it
appears everywhere in a tool — filters, view modes, feature switches, sidebar sections.

**Shape.** A round container, one icon, nothing else. No label inside, no pill, no sliding knob. The
label sits outside it, or the toggle is in a row whose micro-label names the group.

**States.** Idle, hover, focus-visible, on, disabled. All five are CSS, all five transition
declaratively at `0.15–0.2s`.

**The press animation.** GSAP, `0.22s`, `back.out(1.7)`, and it does two things at once:

1. **The edge lights up.** The container's border goes from `border-control` to the screen accent,
   with a tint behind it — `color-mix(in srgb, var(--accent) 14%, transparent)`. This is the base
   DNA's "the border is the design" law doing the work a filled background would do elsewhere.
2. **The icon rotates clockwise.** 90° for a binary toggle, 180° for something that reverses, 45°
   for a plus that becomes a close. **Clockwise on activation, counter-clockwise on release** — the
   reverse direction is the one case where a leave *is* a mirrored enter, because a rotation that
   unwinds the wrong way reads as a second, unrelated event.

**The size is never faked with `transform: scale()`.** Inherited from the base DNA and restated
because a round icon container is exactly where it gets broken — a scaled toggle scales its border to
a non-integer width and the hairline goes soft, which is the one thing this design system cannot
afford. Different sizes are different values.

Under reduced motion the rotation is dropped and the edge state changes instantly. The
acknowledgement survives; see [`11-accessibility.md`](11-accessibility.md).

## Step sliders

The base module makes the liquid dot rail the answer for any control with a handful of discrete
settings. **PRO keeps the rail and drops the goo.**

The rail itself is right: a row of squircle slots with one travelling dot reads its value from across
the room, which is exactly what a dense screen needs. What PRO does not keep is the metaball merge —
the `feGaussianBlur` plus `feColorMatrix` filter is a full-surface repaint on every frame of a drag,
and a tool has several of these on screen at once inside a scrolling panel. The base register affords
it because a portfolio shows one at a time.

So: same layout, same "size is the only variable" rule, same rail-and-flow split, and the dot moves
between slots on the PRO timing table with `back.out(1.7)` — the consequence ease, because the
operator caused it. The slot the dot reaches goes to full size; the filter is not applied.

Where a step slider carries a value that must be read exactly rather than approximately — an hourly
rate, a dunning level — **the number is printed beside the rail**, not inferred from the dot's
position. That is a data tool's requirement and the base register never had it.

## Destructive actions

Base rule inherited — danger colour, never the screen accent, always a second differently-worded
confirmation — with one addition that only matters in a tool: **the confirmation names the thing.**
"Delete invoice 2024-0417 for Müller GmbH?", never "Are you sure?". An operator confirming forty
things an hour stops reading a generic prompt by the fourth one.

## Anti-patterns

Everything in the base list, plus: a screen reachable only by mouse. Tabbing through table rows. A
row action that exists only on hover. `:focus` where `:focus-visible` was meant. `outline: none`
without a replacement. An undiscoverable shortcut. A shortcut that shadows a browser one. A generic
confirmation prompt. A toggle with a sliding knob or an inside label. A toggle sized with `scale()`.
An `Escape` that discards unsaved work without asking.
