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
| **Now** | Bottom of the lock screen, where a thumb reaches. Replaces One UI's Now Bar, and the lock screen is the only place it exists. | built, partly | yes — stolen from the row |
| **Status** | Right end of the bar, over the system's icons. | built | **no** — Modus instead |
| **Clock** | Top-left, over the system clock and One UI's flashlight chip beside it. | built | yes, the Now mods |
| **Double** | Bottom of the screen, centred, alongside whatever Main is doing at the cutout. | built | no |
| **Lock** | Where the lock icon is. Opens, then merges into Main on unlock. | built | no |
| **Notification** | Over the lock screen's own notification list, in its place. | built | no |
| **Quick module** | Inside the Status bubble's open panel: six connectors, six states, the two levels, the foot module. Fifteen of them — the cable is a seventh connector and is deliberately *not* one of them, because it stands inside the foot module rather than beside it, and a bubble inside a bubble is two blur panes over the same pixels. | built | no — each carries one control, and the foot module carries media or a transfer |

Status carries no mod at all — it stands for something the system already draws, and the one thing it *announces* is the charge — plugged in, running low, nearly out — which is news about the reading it is already holding — and it and the Clock both sit a few pixels shorter than the rest, which is how the eye tells a bubble that can be worked from one that only reports. Both are pressed, both are pulled and both are held: Status opens the quick settings panel it owns and holds out to the system's settings, and the Clock holds out to the clock app while nothing is standing in it.

**Reaching a screen edge is a growth, never a move.** The bubbles on the bar stand where they stand — `CLOCK_LEFT`, `STATUS_RIGHT` and the row's own `verticalOffsetDp` are where, not spare margin to be spent — and a state that wants a bubble against the edge of the phone grows the box out to it and past it, leaving everything inside where it already was. Moving the box instead was tried once and is exactly the wrong thing: with the insets zeroed the clock and the Status bubble did reach their edges and their readings travelled with them, so what the eye got was the time sliding sideways rather than a bubble reaching. The extra room a growth takes is paid straight back as padding on the side it grew from, which is what "no realignment" means in code: the box is bigger, the content has not moved a pixel. It costs the touch proxies nothing either — a proxy is measured off its own box, and room *outside* the screen is room no finger can be in.

The Clock is the one that changed. There was a Now bubble on the bar as well, standing on One UI's flashlight chip a few pixels to its right, and the two wanted the same corner: the clock had to leave whenever a mod came out and the row had to stand aside for both of them, which is a lot of motion spent on two shapes getting out of each other's way. The mods are shown in the Clock now — the time when nothing is happening, the thing that is happening when something is — and the name Now belongs to the lock screen's bubble alone.

The two lock screen bubbles — Now and the notification list — are both full-width boxes held by an inset either side, and both of those insets carry a shift from the settings panel (`lockOffsetDp`, `notesOffsetDp`). It is added to one inset and taken off the other so the bubble slides and its width does not, which also keeps `transform` free for the drag that is already using it. The lock proxy is placed from a constant rather than measured, so it carries the same shift by hand.

## Mods are eligible by bubble, not by availability

A mod is content injected into a bubble, and which bubbles may carry it is part of what the mod *is*. A mod does not appear wherever there is room.

| Mod | Eligible bubbles | Built |
|---|---|---|
| Media | Main, Satellite, Now (lock screen) | built |
| Clock / timer | Main, Satellite | built |
| Call | Main, Satellite | built |
| Battery | **Status only** — as its announcement rather than as a mod | built |
| Torch | **Clock only** | built |
| Recording / screen share | Clock only | built |
| Download, Upload | **Status only** — one slot, the way USB is | built |
| Bluetooth, Hotspot, Do not disturb | **Status only**, as a Modus | built |
| Quick settings | **Status only**, as its panel rather than as a mod | built |
| Discord video | Main, Satellite | planned |
| DB Navigator | Main, Satellite, **Now (lock screen)** | planned |

The Now mods are what is *happening* — a torch burning, a recording running — and the Clock is where they are shown. Status carries what is *connected*. That is the line between the two, and it is why bluetooth is not a Now mod even though it would fit there.

A transfer used to be on the Clock's side of that line and it is on Status' now, because the line is about **who started it**. A torch and a recording are things the person did; a file in flight is something the phone is doing for whatever it is attached to, which is the same kind of fact as a cable being in or a device being paired. It stands beside USB as one slot and nothing more — an arrow pointing the way the file is moving, and the tick it wears for a moment when it lands. No reading, no timeline, no tap: what a bubble that is glanced past can say about a transfer is that there is one and which way it is going.

The line has a second half worth naming, because it decides how the Status bubble is written: what is happening **starts and stops**, and what is connected is **true all the time**. So a Now mod is watched and announced — the time stands aside when the light comes on and comes back when it goes — while Status is read and drawn, born once and standing there for as long as the bar does. A Modus is not a mod arriving in it: the bubble is being true about something else, so the swap is a repaint and its own width carries the change. USB left the Modus family and became a slot of its own — a cable is not something riding on top of the link, it is a second thing attached — and do not disturb joined it, because a phone that has been put quiet is exactly a mode it is being kept in.

The Clock carries one Now mod at a time and picks by the row's own rule: first come, first served. Nothing ranks a recording above a torch — the phone cannot know which of two true things matters more, and guessing is how a bubble ends up flickering between two states that are both correct.

Torch is the one to read twice. It is not a mod the row is currently not showing — it is a mod the row may never show, because the bubble at the left end of the bar is the one standing over One UI's flashlight chip and a torch drawn anywhere else covers nothing. Eligibility is what stops a new Now mod from being written as a row mod that happens to start out there. It is also the one mod with no closed face: a light that is on is shown as its panel and nothing else.

The lock screen's Now bubble is the exception that proves the shape: it does not have its own copy of Media, it **takes** the row's for as long as the keyguard is up (`LOCK_STEALS`, one filter in `liveMods()` — see [states.md](states.md#the-steal)). One mod, one bubble at a time, moved rather than duplicated.

## Every bubble is born at the punch hole

Nothing spawns where it will eventually live. The true middle of the screen is the middle of the hole and the middle of the Main bubble drawn around it, and every bubble comes out of there as a small drop, travels to its spot, and only then behaves like a bubble standing there. A bubble that appears at its own position is a box being shown; a bubble that arrives from the hole is the same body of liquid moving. The travel distance is the host's to supply — the page does not know how wide the screen is.

The quick modules are the third case, and they are the one set that arrives from **below its own place** rather than from anywhere on the screen. They came out of the Status bubble for a while — a drop a twentieth of its size crossing the screen and becoming the module at the end — on the argument that a module is a piece of that bubble opening out. That is true and it is not what the eye gets: six drops thrown from one corner is six journeys to sit through before a single reading can be had, and the bubble they left is not what the panel is about. So they rise into place from underneath the bottom edge of the screen instead, crooked by a few degrees and straightening as they land, and the panel is the thing that arrived rather than six pieces of a bubble. **They all start in the same place, which is why the distance is each module's own**: one throw written for the set gives every module the same journey and therefore fifteen different starting heights, so the foot of the panel came off the screen while its top row set off from a third of the way down it, in full view. The distance is measured per module — its own top edge down to the bottom of the display — and what they share is the edge they come from rather than the ground they cover.

**The rule underneath it is that a bubble arrives from somewhere**, and the hole is where a bubble that lives on the bar arrives from. A bubble that lives at the bottom of the screen does not: the lock screen's Now bubble and its notification list are placed rather than flown, and the Double joins them now that it stands down there too — it rises out of the bottom edge, which is the direction it will leave by, and sinks back through it. A drop crossing the whole height of the screen and back for a four-second announcement is a longer journey than the thing it is announcing. What is not negotiable is the half these three still obey: none of them appear at their own position, and none of them blink out.

Departure is the same journey backwards, and it is a departure rather than a repaint: width first, then the shape goes. A mod that ends because its app was killed still leaves; it does not blink out. See [motion.md](motion.md#the-glyph-is-the-cause-width-is-the-consequence).

## Every bubble is a fluid

This is the contract the whole window model exists to serve: **every bubble that stands on its own merges with every bubble it comes near.** Not Main with its satellites only — the Clock with Main when the row stands aside for a mod standing in it, Now with Main on the unlock, Lock with Main too, Double with whatever it lands over at the bottom of the screen, a dot with the bubble that takes it back in. Two of ours near each other and not necking is a bug, not a bubble that happens not to merge.

**The quick settings' modules are the one exemption, and what exempts them is that they are a grid rather than a row.** Merging is a thing shapes do when they *come near* each other — a satellite leaving, a drop landing, the row standing aside — and it says so because the melt is read off the gap and the gap is changing. Fifteen controls laid out in a grid never come near each other: they are already a hair apart and they stay there for as long as the panel is up. Measured, that hair pinned the layer's one melt number at its ceiling for the whole of the open, so the panel drew as a single welded sheet with dents where the modules were, and the row at the cutout — which shares the layer, because every bubble does — welded with it. So these fifteen are out of the goo layer and paint themselves, and the rule that survives is the one worth having: **a shape is liquid because it moves against another shape, not because it is drawn like a bubble.** A new control standing in a panel is this case; a new bubble standing on the bar is not.

What follows, and none of it is negotiable:

- **One canvas.** Merging is one SVG goo layer, a filter reaches exactly as far as its own surface, so a bubble in a second window can never be liquid with anything. A new bubble is drawn in `pill.html` and is never given a window of its own. See [architecture.md](architecture.md#windows).
- **Solid inside the layer.** A half-transparent shape falls under the alpha contrast and is erased instead of merged. The transparency belongs to the layer; what is in it is opaque. `html.liquid` blanks each bubble's own background — a bubble left out of that blanking paints twice and reads as the one solid thing in a row of glass.
- **The strength is read off the gap**, never fixed (`meltBy()`): shapes fuse as they close and let go as they part, because that is what liquid does. A constant strong enough to fuse a leaving satellite welds the resting row into a bar.
- **Nearness is measured only between shapes that share a row.** The melt strength is read off the smallest gap between bubbles, and a gap only means anything horizontally when the two are side by side. Measured as one sorted line it broke the moment a bubble was drawn somewhere other than the bar: the lock bubble is nearly full-width at the bottom of the canvas, so against anything on the row it reported a gap of most of the screen negative, pinned the deviation at its ceiling, and welded the whole row into one bar for as long as the keyguard was up. Five boxes make ten pairs — there is nothing to save by being clever.
- **The skin is mirrored, not told.** One per-frame `getBoundingClientRect()` pass feeds both the goo and the host's blur panes, so the glass cannot disagree with the bubble it is read off. A size change owes that mirror a `stirLiquid()` long enough to cover the whole transition, including every close.
- **A new bubble needs a blur pane on both sides** — a name in `BLUR_PANES` in `liquid.js` and a pane counted in `BubbleService.BLUR_PANES` — unless it is standing on glass already: the quick modules have neither pane nor blob, because the panel has frosted the whole screen behind them and a second frost per module is the same wallpaper blurred twice. See [motion.md](motion.md#blur-is-the-hosts-and-the-bubbles-own). Nothing joins the two at build time; a missing pane is a bubble with no glass. A bubble that is really a *list* of a variable number of things — the lock screen's notification bubbles — still needs a fixed run of panes reserved up front (`note0`…`note4`, matching `NOTES_LIMIT`) rather than one created and destroyed per notification, because the host's panes are a fixed `View` list stood up once at start. `sourceOf()`'s per-pane `getComputedStyle` is cached per *element*, not per pane, for exactly this reason: a list redrawn whole hands a pane a different element on every repaint, and a style cached against the pane rather than the element it currently mirrors goes stale the moment the list changes under it.
- **A bubble standing outside the row grows the filter region while it is there, and only while it is there.** Outside the region the skin is silently dropped and the bubble goes on painting its own background — which reads as a colour bug and is a geometry one.
- **The screen has edges, and the liquid may wet them** (`edgeMerge`, on by default). A bubble standing within `WALL_REACH` of a side grows a neck into it — a shape from off-screen up to the bubble's own edge, narrower than the bubble is tall, which the goo fillets in exactly as it fillets a satellite — and a grown bubble takes that neck up to the top of the screen so the two close the corner between them. It is *drawn* rather than blurred into place because the melt is one number for the whole layer: raising it far enough to bridge fourteen pixels of gap would weld the row into a bar, which is the one thing `meltBy()` exists to prevent.
- **A grown bubble stands clear of the bar it grew out of.** `--grown-pad` (mirrored as `GROWN_PAD`, and added to every grown window height so it is not paid for out of the bottom) holds an extended state below the row: on the row its first line is drawn behind the camera and behind the bubbles standing there, and below them it has the whole width to read across. Growing is otherwise the same case as the lock screen, not an exception to it. An extended state — an alert, a panel, any Active tab — is taller than the row's band, so it grows the region (`html.grown`) rather than leaving the liquid. The skin used to be taken off for the whole of a growth, and that is what "the alert overlapped the Now bubble instead of merging with it" was: a bubble painting its own background is not in the goo layer at all, so it can only stack on what it meets. No state comes out of the liquid.

## Every bubble is bouncy

One press response, one arrival curve, one family of easing across all of them. The lock screen's Now bubble is the reference because it is the one that is right today:

- `--ease-split`, `cubic-bezier(0.2, 1.7, 0.35, 1)` — it lands past its mark and settles back.
- `scale 320ms var(--ease-split)` for the swell, and **1.03 under a finger** for the press.

A bubble that arrives on a linear or an ease-out is a box being positioned. The same curve everywhere is most of what makes several shapes read as one material.

**A press is one gesture and must not change meaning with where on the bar it lands.** 1.03 is what the lock bubble, the Now bubble and the main bubble's `pressing` state all say. The main bubble's *hold* is a different thing and goes further (`--hold-now`, 1.14): a press is an acknowledgement, a hold is a state on its way to opening something. The Now bubble sat at 1.08 — nearly the main bubble's full hold swell — so the same press read as twice as emphatic out at the clock as at the cutout, and its class had been added on touch-down all along with no rule to match.

**Squircle is not part of this, and that is deliberate.** (The rule is about *bubbles*. A button inside a tab is a square with a radius and has room for the difference between a superellipse and a rounded rectangle to read, which is why the media tab's transport wears one.) `corner-shape: squircle` belongs to the lock bubble alone: it is the one shape here wide enough for the difference between a superellipse and a rounded rectangle to read. On a pill or a circle the corners already eat the whole end, so a squircle there is not a subtler shape but a *different* one — a round satellite would become a rounded square. Bounciness is the motion, not the corner.

## Nothing is ever clipped

A bouncy curve overshoots, and a container sized to the target cuts the overshoot off — which reads as the animation being wrong rather than as the box being small. So `overflow` is `visible` on anything that is a bubble. A face that genuinely must clip its contents — artwork, a timeline — clips on an inner element instead, never on the bubble.

The window is the other half of it: a bubble cannot be drawn outside the window holding it, so the room an overshoot needs is asked for **before** the animation starts, never while it runs. A resize landing mid-growth clips the growth it was meant to make room for.

## A cell never says "off"

Anything that stands in a grid and reports a state — the quick panel's connectors are the case that set this — is laid out as a name centred on top, its glyph filling the cell behind both as a watermark, and one line underneath for what it is *attached to*. That last line is written only when there is something to write. "Off" is never one of the things: a cell that is dark, unlit and carrying nothing under its name has already said it, and the single line a cell has is worth more spent on the half that is news — the network, the pair of headphones, the generation. A blank there is the reading, which is only true because the name above it is always present to be the thing that did load.

## Nothing crosses the punch hole

The hole is the one place on this screen where content simply cannot go. Content stops at a named margin either side of screen centre and fades out there — a fade, never an ellipsis, never a hard cut. It holds for a resting state most of all, since a resting state is parked over the hole for as long as it stands there: names right-align and expand away from the hole rather than growing across it.

A mod's glyph and its short reading inside the bubble are not subject to the fade. It is a text behaviour at the boundary, not a mask over the bubble, and they only fade when they animate outside the bubble's own bounds.

**How it is held, so a new face does not have to work it out again.** A resting face is exactly **two runs** — the glyph and its reading — and the corridor is a `column-gap` of `--hole-gap` between them. A gap is a floor, not a spacing choice: `space-between` may push the two further apart, and nothing can bring them closer. That is the whole enforcement, and it is why the shape of the face matters more than any width. The call face was three flat children until its name and clock were wrapped into one reading, and being three is precisely why its name sat midway between them — over the camera — while every two-part face was fine.

Two things follow for anything new:

- **A face with three runs has no corridor.** Wrap until it has two, rather than reaching for a width that happens to look right; a hardcoded `max-width` stops the reading short of the room it is allowed and still does not guarantee the middle.
- **The fade is conditional, and only the layout can answer the condition.** A mask knows how wide the box is and nothing at all about how wide the text is, so a mask written flat into a label's rule dissolves the last quarter of every reading that fitted perfectly well — which is what "text labels fade out everywhere" was. `js/labels.js` measures every reading on the bar and puts `.runs-out` on the ones that overflowed; every mask in `pill.css` hangs off that class, and a label that fits is drawn whole. A new reading that may run out of room is a name added to that file's list and nothing else.
- **A reading too long to fit travels.** Fading the end is honest and it is not readable, so after 0.7s standing where it landed the text moves far enough to show what the fade was hiding and comes back, on a loop. It moves by `text-indent` and never by a transform: the clip and the mask sit on the same box as the glyphs, so a transform carries the window along with what is inside it and nothing appears to move. Which way it travels is read off the text's own anchor — the same fact the fade's direction is read off, asked once.
- **The fade covers the end that runs out, which is never the end that is pinned.** A reading set from its left overflows on the right and fades there; one anchored against a clock at the right grows leftwards and fades on the left. A mask copied from a face with the opposite anchor fades where there is nothing to hide and cuts square where there is.

`--hole-gap` is over-reserved in exactly one state: while the Now bubble is out the row is pushed right until the bubble's left edge is at the hole, so the hole is no longer beneath the middle of the bubble. That costs the reading room it could have had; it never puts text under the camera, and it is not worth a second geometry to recover.

## What a new bubble owes

Before writing one, name each of these for it:

1. Where it stands, and what it is standing **on** — a bubble that covers nothing the system draws has to justify the pixels.
2. Which states it may enter, in [states.md](states.md), and which mods are eligible for it, in the table above.
3. Its pane in `BLUR_PANES` on both sides, and whether it stands outside the goo layer's filter region.
4. Whether it is touchable at all. If it is, one small proxy window over exactly what is interactive and no more — every pixel a touchable window covers is a pixel the shade swipe cannot start on. If it is not, it costs no window.
5. Its journey out of the punch hole, and its journey back.
