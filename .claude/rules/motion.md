---
paths:
  - "app/src/main/assets/**"
---

# Motion

Every rule here exists because the interface is standing on a phone's status bar, next to a camera cutout, and has to read as one physical thing rather than as several boxes changing size. Motion is the whole product — a state that appears correctly but arrives wrongly is a bug.

## The punch hole is the origin

Everything on this screen is born at the true middle of the screen, which is the middle of the punch hole and the middle of the bubble drawn around it. Nothing spawns where it will eventually live. A pill that belongs somewhere else — the torch out by the clock — is born at the hole as a small drop, travels to its spot, and only then behaves like a pill standing there. The travel distance is the host's to supply (`window.setFlight`): the page does not know how wide the screen is.

## The glyph is the cause; width is the consequence

A mod arriving never widens the bubble and then fills it. The mod's glyph flies in first, lands against the left inset, and the bubble opens out around it *because* it got there. On the way out the same thing runs backwards: the width is given back first, then the glyph leaves. The same applies to a whole pill arriving from the cutout — it hits its stop and opens out off that stop.

- `LAND` is the share of the arrival at which the flying thing is actually against its stop. The arrival curves overshoot and settle back, so this is under halfway, not the end. Hand over at `LAND`, never on animation end, or there is a visible pause between the landing and the width change. They are one action and one reaction.
- Ask the host for the window room the growth will need *before* the flight, not when the growth starts. The window is resized a frame or two after being asked, and a resize landing mid-growth clips it.

## The glyph is a shared element

Every mod's glyph sits at the same left inset in the bubble and in its satellite. A mod moving between the two is therefore a hand-over of one picture that does not move, not two animations of two copies. While a hand-over is in flight the main bubble must be genuinely in its closed state — compact width, idle face — or the illusion breaks: an outgoing mod's icon still showing inside the bubble proves it was never really closed.

## Liquid

Two shapes belong to one body of water when their blurs overlap. That is one `feGaussianBlur` plus one `feColorMatrix` alpha contrast, and shapes bridge at roughly twice the blur's `stdDeviation`.

- Everything inside a goo layer must be **solid**. A half-transparent shape falls below the contrast's threshold and is erased rather than merged; the transparency is worn by the layer, never by what is in it.
- In the bubble row the strength is **not a constant**. A fixed deviation strong enough to fuse a satellite leaving the bubble welds the whole row into one bar for as long as it stands there, and one weak enough to leave the row alone never merges anything: no single number is both. `meltBy()` reads it off the measured gap between the shapes every frame, so they fuse as they come together and let go as they part — which is what liquid does anyway. A fixed deviation is right only where the shapes never rest close together, like the torch's step slider.
- Merging happens across shapes inside **one** goo layer, which means inside one surface. Two windows cannot fuse: the torch pill and the bubble are separate surfaces composited by the system, and no filter reaches across them.
- **Faking it across two windows has been tried once and failed.** Each side flattened the corner facing the other and grew a directional glow towards it, driven by one proximity number the host measured between them. It reads as neither: the flattened corners look like the pill has been *cut off* at the edge of its window, and the glow looks like a stray highlight on the bubble rather than like attraction. It was also on almost permanently, because two pills on one bar are never far apart. Corners and shadows are not what makes the row read as liquid — a shared blur and one alpha contrast is, and that is exactly the thing a second window cannot have. If cross-window merging is wanted, it needs both shapes on one surface, which means one window, not a better approximation.
- The liquid skin mirrors real elements' `getBoundingClientRect()` frame by frame rather than duplicating any geometry rule, so one skin follows transitions, drags and holds for free. Read all rectangles first, then write — interleaving them thrashes layout.

## Blur is the host's, not the page's

Nothing a WebView draws reaches pixels it does not own, so the frosted region is a bare `View` in the host window sized to whatever the page is drawing. The page therefore sends the journey — size, corner, offset, duration — and the host replays it on the same curve.

**Every size change is also a blur change.** The glass shares nothing with the CSS: it is a region in another process that knows only what it was last told. Any edit that moves a width, a height, a corner, a position or an offset — including one made entirely in CSS, like a custom property the stylesheet subtracts from a width — is unfinished until the matching `setBlurBounds` / `paintBlur` call goes with it, on the same curve and the same duration. A width that animates while its glass does not is the bug that keeps coming back: the frosted rectangle stands there at the old size, sticking out past the shape it belongs to.

Two more consequences: the blur has to be cleared by hand when its pill goes dark, because a transparent view goes on blurring; and a region derived from a configured size (`compact.width`) must be derived from the size the bubble *actually* is right now (`restingMain()`), since taxes and splits change it.

## Timing

Legibility beats speed. A merge the user cannot follow is wrong even when every step is correct — the cause has to be visible as the cause. When something is not readable, the fix is to sequence it properly, not simply to lengthen it.

Never leave the state that is being animated out of a phase in the middle: an element retired in the same frame its retreat begins vanishes on the spot, and the growth it was supposed to explain then happens for no visible reason.
