# Module 04 — Layout and sizing

The rule that generates every other rule here: **a difference of *amount* is a ramp; a difference of
*kind* is a branch.** Confusing the two is what produces breakpoint soup.

## Two axes, never a width

Width cannot answer either question this system asks. A 90°-rotated 1920×1080 monitor is 1080 CSS
pixels wide; an iPad Pro is 1024. Fifty-six pixels apart, and only one of them has a mouse.

**Axis 1 — device (what the visitor points with).** Decides affordances, control sizes, gestures.

```scss
@mixin mobileDevice  { @media (pointer: coarse) { @content; } }
@mixin desktopDevice { @media (pointer: fine)   { @content; } }
```

**Axis 2 — layout (what shape the screen is).** Decides arrangement.

```scss
@mixin horizontalLayout { @media screen and (orientation: landscape) { @content; } }
@mixin verticalLayout   { @media screen and (orientation: portrait)  { @content; } }
```

**Plus one for hover**, because a touch screen fires `:hover` on tap and leaves it applied:

```scss
@mixin hoverable { @media (hover: hover) and (pointer: fine) { @content; } }
```

The four corner combinations (`desktopHorizontal`, `desktopVertical`, `mobileHorizontal`,
`mobileVertical`) exist for the genuinely rare rule that needs one matrix cell. Reaching for one is a
smell — justify it in a comment at the call site.

**Retired terminology.** `mobile`, `tablet`, `allMobile`, `smallDesktop`, `desktop`, `handheld` are
gone. Each was *named* for a device and *implemented* as a width; the two stopped agreeing the moment
a monitor was rotated. Reintroducing one is a bug.

## Two apps, one codebase

The horizontal and vertical layouts are separate experiences, not one responsive design. A module may
exist in one and not the other, and the same module may behave differently in each. This is a
deliberate stance: a game menu is arranged for the shape of the screen it is on, not reflowed.

Switch on `isVertical` in scripts, `@include verticalLayout` in styles. Never assume a change to one
applies to the other.

## The single scale lever (landscape)

```scss
@media screen and (orientation: landscape) {
  html { font-size: clamp(13px, 0.917vw, 36px); }
}
```

Pure `vw`, **no constant term**. A `vw + px` curve is what makes a layout look right at exactly one
resolution, too big below it and too small above. A pure ratio holds every panel at the same fraction
of the screen at every size. `0.917vw` is `23.5px` at 2560 — **1440p is the reference the layout is
tuned against**, and every other resolution is that same design scaled.

Consequences:

- Every landscape size is written in **`rem`**, never bare `px`. A `px` value (or a `clamp()` whose
  *max* is px) stops growing above 1080p and renders a 1080p-sized panel on a 1440p screen with more
  empty space around it.
- Hairlines and sub-3px nudges (borders, 1–2px insets) stay `px` on purpose.
- A `clamp()` whose rem bounds cross its vw term reintroduces the constant locally. Keep the bounds
  clear of the term: `clamp(4rem, 9vw, 22rem)`, not `clamp(6rem, 12vw, 16rem)`.
- JS that caps a size in px must scale it by the computed root font size.

Portrait keeps a fixed 16px root and sizes off `vw`/`dvh` directly — a `rem` there is a constant, not
a scale.

## Ramp rules

- Every size is `clamp(min, <viewport unit>, max)`. A bare `px` is a desktop size on a phone; a bare
  `vw` collapses in portrait, where the viewport's narrow axis *is* the width.
- **A landscape `clamp()` needs its own portrait curve, and the thing to check is its *minimum*.**
  `2.4vw` of a phone is ~9px, so `clamp(1.375rem, 2.4vw, 2.5rem)` renders at its 22px floor on every
  phone: a desktop size on the narrow axis. The floor, not the vw term, is what runs chrome off the
  right edge.
- **Inside a height-constrained box the preferred term is `min(<vw>, <dvh>)`, not a bare `vw`.**
  Width-only type does not shrink as the window gets shorter, so it overflows the box it sits in.
- **Never `transform: scale()` to change a layout size.** The box keeps its original dimensions (so
  it still overflows its container) and any transform tween silently overwrites it. Size the box, or
  drive it from a custom property the geometry reads.
- Grid tracks holding text are `minmax(0, 1fr)`, never `1fr` — a bare `fr` floors at min-content width
  and pushes the row off-screen.
- CSS Grid for 2D, Flexbox for 1D, `gap` rather than margin hacks.

## Reserved space is a token, not a magic number

```scss
:root { --nav-reserve-bottom: 0px; --nav-reserve-right: 0px; }
@include verticalLayout { :root { --nav-reserve-bottom: 13vh; } }
@include desktopVertical { :root { --nav-reserve-bottom: 4vh; --nav-reserve-right: 9vw; } }
```

Which edge needs the room is a *device* question (the nav is a bottom row on a handheld, a right-hand
column on a portrait desktop). Both are `0` in landscape, so a screen can read them unconditionally
and never needs to know which case it is in. Any global chrome — a nav, a dock, a status bar — gets
this treatment: one token pair at the root, read blindly by every screen.

## Tokens emit no CSS

The shared style entry is a barrel over *variables and mixins only*. Nothing behind it may emit a
rule: a scoped component style block compiles alone, so one emitted rule becomes one copy per
consumer. Global rules live in one global stylesheet, loaded once from the entry point.

Generalised: **shared code that is imported everywhere must be declarations, not output.**

## Anti-patterns

Width breakpoints. `transform: scale()` for sizing. Bare `px` in the landscape layout. A `vw + px`
root. A responsive reflow standing in for two deliberate layouts. Per-screen magic numbers for global
chrome clearance. Emitting CSS from a shared import.
