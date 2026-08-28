---
paths:
  - "app/src/main/assets/**"
  - "app/src/main/java/**"
---

# Bubbles

A bubble is a place on the screen that can hold something true. Not a component and not a window: a body of the same liquid, standing somewhere, wearing whatever is true there for as long as it is true. [states.md](states.md) says which states each one may enter and what a mod is; [motion.md](motion.md) says how any of it is allowed to move. This file says what all of them share, and it is the file to read before adding one.

Everything here binds every bubble, whichever kind it is. A rule that holds for only one of them is a note in that bubble's row, not a rule.

## The inventory

| Bubble | Where it stands | Built | Owns mods |
|---|---|---|---|
| **Main** | At the punch hole. The bubble. | built | yes |
| **Satellite** | Beside Main, one a side, two at most. | built | borrowed — a mod that is live but not the owner |
| **Hidden** | Past the second satellite. A coloured dot: a count, not a face. | built | no |
| **Now** | Left end of the bar, at the clock, over One UI's own chip. | built | yes, its own set |
| **Lock Now** | Bottom of the lock screen, where a thumb reaches. Replaces the Now Bar. | built, partly | yes — stolen from the row |
| **Status** | Right end of the bar, over the system's icons. | planned | **no** |
| **Clock** | Top-left, over the system clock. Merges visibly with Now, never overlaps it. | planned | **no** |
| **Double** | Beside the punch hole, alongside whatever Main is doing. | planned | no |
| **Lock** | Where the lock icon is. Opens, then merges into Main on unlock. | planned | no |
| **Notification** | Over the lock screen's own notification list, in its place. | planned | no |

Status and Clock are the two that carry no mod at all — they stand for something the system already draws — and both sit a few pixels shorter than the rest, which is how the eye tells a bubble that can be worked from one that only reports.

## Mods are eligible by bubble, not by availability

A mod is content injected into a bubble, and which bubbles may carry it is part of what the mod *is*. A mod does not appear wherever there is room.

| Mod | Eligible bubbles | Built |
|---|---|---|
| Media | Main, Satellite, Lock Now | built |
| Clock / timer | Main, Satellite | built |
| Call | Main, Satellite | built |
| Battery | Main today, becoming the first **Double** | built, moving |
| Torch | **Now only** | built |
| Recording / screen share | Now only | planned |
| Download, Upload | Now only | planned |
| Bluetooth, USB, Hotspot | **Status only** | planned |
| Discord video | Main, Satellite | planned |
| DB Navigator | Main, Satellite, **Lock Now** | planned |

Now carries what is *happening* — a torch burning, a recording running, a download in flight. Status carries what is *connected*. That is the line between the two, and it is why bluetooth is not a Now mod even though it would fit there.

Torch is the one to read twice. It is not a mod the row is currently not showing — it is a mod the row may never show, because the Now bubble exists to cover One UI's flashlight chip and a torch drawn anywhere else covers nothing. Eligibility is what stops a new Now mod from being written as a row mod that happens to start out there.

The Lock Now bubble is the exception that proves the shape: it does not have its own copy of Media, it **takes** the row's for as long as the keyguard is up (`LOCK_STEALS`, one filter in `liveMods()` — see [states.md](states.md#the-steal)). One mod, one bubble at a time, moved rather than duplicated.

## Every bubble is born at the punch hole

Nothing spawns where it will eventually live. The true middle of the screen is the middle of the hole and the middle of the Main bubble drawn around it, and every bubble comes out of there as a small drop, travels to its spot, and only then behaves like a bubble standing there. A bubble that appears at its own position is a box being shown; a bubble that arrives from the hole is the same body of liquid moving. The travel distance is the host's to supply — the page does not know how wide the screen is.

Departure is the same journey backwards, and it is a departure rather than a repaint: width first, then the shape goes. A mod that ends because its app was killed still leaves; it does not blink out. See [motion.md](motion.md#the-glyph-is-the-cause-width-is-the-consequence).

## Every bubble is a fluid

This is the contract the whole window model exists to serve: **every bubble merges with every bubble it comes near, and none of them are exempt.** Not Main with its satellites only — Now with Main when the row stands aside, Clock with Now, Lock with Main on the unlock, Double with whatever it lands over, a dot with the bubble that takes it back in. Two of ours near each other and not necking is a bug, not a bubble that happens not to merge.

What follows, and none of it is negotiable:

- **One canvas.** Merging is one SVG goo layer, a filter reaches exactly as far as its own surface, so a bubble in a second window can never be liquid with anything. A new bubble is drawn in `pill.html` and is never given a window of its own. See [architecture.md](architecture.md#windows).
- **Solid inside the layer.** A half-transparent shape falls under the alpha contrast and is erased instead of merged. The transparency belongs to the layer; what is in it is opaque. `html.liquid` blanks each bubble's own background — a bubble left out of that blanking paints twice and reads as the one solid thing in a row of glass.
- **The strength is read off the gap**, never fixed (`meltBy()`): shapes fuse as they close and let go as they part, because that is what liquid does. A constant strong enough to fuse a leaving satellite welds the resting row into a bar.
- **Nearness is measured only between shapes that share a row.** The melt strength is read off the smallest gap between bubbles, and a gap only means anything horizontally when the two are side by side. Measured as one sorted line it broke the moment a bubble was drawn somewhere other than the bar: the lock bubble is nearly full-width at the bottom of the canvas, so against anything on the row it reported a gap of most of the screen negative, pinned the deviation at its ceiling, and welded the whole row into one bar for as long as the keyguard was up. Five boxes make ten pairs — there is nothing to save by being clever.
- **The skin is mirrored, not told.** One per-frame `getBoundingClientRect()` pass feeds both the goo and the host's blur panes, so the glass cannot disagree with the bubble it is read off. A size change owes that mirror a `stirLiquid()` long enough to cover the whole transition, including every close.
- **A new bubble needs a blur pane on both sides** — a name in `BLUR_PANES` in `pill.html` and a pane counted in `BubbleService.BLUR_PANES`. Nothing joins the two at build time; a missing pane is a bubble with no glass.
- **A bubble standing outside the row grows the filter region while it is there, and only while it is there.** Outside the region the skin is silently dropped and the bubble goes on painting its own background — which reads as a colour bug and is a geometry one.

## Every bubble is bouncy

One press response, one arrival curve, one family of easing across all of them. The Lock Now bubble is the reference because it is the one that is right today:

- `--ease-split`, `cubic-bezier(0.2, 1.7, 0.35, 1)` — it lands past its mark and settles back.
- `scale 320ms var(--ease-split)` for the swell, and **1.03 under a finger** for the press.

A bubble that arrives on a linear or an ease-out is a box being positioned. The same curve everywhere is most of what makes several shapes read as one material.

**A press is one gesture and must not change meaning with where on the bar it lands.** 1.03 is what the lock bubble, the Now bubble and the main bubble's `pressing` state all say. The main bubble's *hold* is a different thing and goes further (`--hold-now`, 1.14): a press is an acknowledgement, a hold is a state on its way to opening something. The Now bubble sat at 1.08 — nearly the main bubble's full hold swell — so the same press read as twice as emphatic out at the clock as at the cutout, and its class had been added on touch-down all along with no rule to match.

**Squircle is not part of this, and that is deliberate.** `corner-shape: squircle` belongs to the lock bubble alone: it is the one shape here wide enough for the difference between a superellipse and a rounded rectangle to read. On a pill or a circle the corners already eat the whole end, so a squircle there is not a subtler shape but a *different* one — a round satellite would become a rounded square. Bounciness is the motion, not the corner.

## Nothing is ever clipped

A bouncy curve overshoots, and a container sized to the target cuts the overshoot off — which reads as the animation being wrong rather than as the box being small. So `overflow` is `visible` on anything that is a bubble. A face that genuinely must clip its contents — artwork, a timeline — clips on an inner element instead, never on the bubble.

The window is the other half of it: a bubble cannot be drawn outside the window holding it, so the room an overshoot needs is asked for **before** the animation starts, never while it runs. A resize landing mid-growth clips the growth it was meant to make room for.

## Nothing crosses the punch hole

The hole is the one place on this screen where content simply cannot go. Content stops at a named margin either side of screen centre and fades out there — a fade, never an ellipsis, never a hard cut. It holds for a resting state most of all, since a resting state is parked over the hole for as long as it stands there: names right-align and expand away from the hole rather than growing across it.

A mod's glyph and its short reading inside the bubble are not subject to the fade. It is a text behaviour at the boundary, not a mask over the bubble, and they only fade when they animate outside the bubble's own bounds.

**How it is held, so a new face does not have to work it out again.** A resting face is exactly **two runs** — the glyph and its reading — and the corridor is a `column-gap` of `--hole-gap` between them. A gap is a floor, not a spacing choice: `space-between` may push the two further apart, and nothing can bring them closer. That is the whole enforcement, and it is why the shape of the face matters more than any width. The call face was three flat children until its name and clock were wrapped into one reading, and being three is precisely why its name sat midway between them — over the camera — while every two-part face was fine.

Two things follow for anything new:

- **A face with three runs has no corridor.** Wrap until it has two, rather than reaching for a width that happens to look right; a hardcoded `max-width` stops the reading short of the room it is allowed and still does not guarantee the middle.
- **The fade covers the end that runs out, which is never the end that is pinned.** A reading set from its left overflows on the right and fades there; one anchored against a clock at the right grows leftwards and fades on the left. A mask copied from a face with the opposite anchor fades where there is nothing to hide and cuts square where there is.

`--hole-gap` is over-reserved in exactly one state: while the Now bubble is out the row is pushed right until the bubble's left edge is at the hole, so the hole is no longer beneath the middle of the bubble. That costs the reading room it could have had; it never puts text under the camera, and it is not worth a second geometry to recover.

## What a new bubble owes

Before writing one, name each of these for it:

1. Where it stands, and what it is standing **on** — a bubble that covers nothing the system draws has to justify the pixels.
2. Which states it may enter, in [states.md](states.md), and which mods are eligible for it, in the table above.
3. Its pane in `BLUR_PANES` on both sides, and whether it stands outside the goo layer's filter region.
4. Whether it is touchable at all. If it is, one small proxy window over exactly what is interactive and no more — every pixel a touchable window covers is a pixel the shade swipe cannot start on. If it is not, it costs no window.
5. Its journey out of the punch hole, and its journey back.
