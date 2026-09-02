---
paths:
  - "app/src/main/assets/**"
---

# Motion

Every rule here exists because the interface is standing on a phone's status bar, next to a camera cutout, and has to read as one physical thing rather than as several boxes changing size. Motion is the whole product — a state that appears correctly but arrives wrongly is a bug.

## Motion is additive

An interaction never cancels what is already moving. It adds to it: a hold landing on a bubble that is mid-drag layers its scale onto the drag rather than replacing the drag's transform, and the bubble ends up doing both at once because both are true at once. This is the difference between a system that feels alive and one that feels like a set of boxes taking turns.

CSS transitions cannot do this. A second transition on a property replaces the first from wherever it had got to, which is exactly how an animation gets eaten mid-flight and why the same interruption bug keeps returning in a different state. Motion therefore runs through the Web Animations API with `composite: 'add'`, and CSS is left owning appearance only. An animation is an object with a handle: it can be inspected, held, handed over, or let finish — cancelling it is a decision that has to be argued for, not the default that happens by accident.

**One owner per property.** Every animated property has exactly one place in the code that starts animations on it. Two callers animating one property is the race, and no amount of ordering fixes it from the outside.

On the bubble itself that split is now written into the CSS rather than argued about per gesture. `transform` carries everything written from what is happening *right now* — the merge's `--absorb` squash, the pull's lean and its shrink — and may never be given a transition. `scale` carries the one thing with a duration, the hold's swell. `translate` carries the row's place. Three properties, three owners, composing for free because they are separate properties and not two writers over one.

**A transition list is a contract with every other rule on the element**, and a rule that writes `transition:` wholesale rewrites that contract for everybody. It has cost three separate bugs here — the hold snapping mid-drag, the lock bubble opening without animating, and every size change on the main bubble going instant for the rest of the session the first time anything was dragged. So the base list is named once in a custom property (`--pill-transition`, `--lock-transition`) and any rule that wants to *add* a property writes `transition: var(--…-transition), <mine>`. Only a rule that genuinely means "nothing here eases" replaces the list, and it says which property it is keeping.

This is what a blanket `transition: none` breaks. A drag and a lean both have to follow the finger frame for frame, and both used to say so by killing every transition on the element — which took the hold's easing with it, so a hold that was still growing snapped to its end the instant the finger moved four pixels and snapped back when it stopped. `.dragging` and `.pulling` therefore name the transition they keep (`scale`) instead of clearing the lot. Any new gesture that has to be instant owes the same care: turn off the transitions that are yours, never all of them.

## The hold outranks everything

The hold is the senior gesture on a bubble. It runs *underneath* whatever else the finger has started — the swell grows on `scale` while the drag trades width and the pull leans — and if the finger is still down when its timer comes up, the hold takes the touch and the others are let go of without being asked what they wanted (`abandonSwap`, `endPull` with no settle, never `releaseSwap`: a trade committing behind the panel that is opening over it is two answers to one finger).

It used to be the other way round: one slop cancelled the hold and started the drag, so a finger that moved at all was a finger that had given up on holding. That is what made the haptic feel fragile — it was not fragile, it was outranked. The only gesture it does not run underneath is the upward dismiss, which has already acted and leaves nothing to hold.

It outranks the others right up to the moment one of them is *earned*, and not one pixel past it. A drag that crosses the trade's threshold has already said what the finger wanted and the user has already felt it say so, so the hold is ended there (`endHold` at the crossing) rather than left counting underneath a swap that visibly happened. A finger that then rests a moment too long gets nothing, which is correct — it used to get a panel opening on top of the row it had just traded.

A consequence worth naming: the hold may not resize a window while a drag is live. Resizing the proxy pulls the surface out from under the finger and the system answers with a cancel, which would end the very drag the hold is running underneath. The swell is drawn in the room the closed row already has.

**Every gesture that moves the bubble owes a way home.** `transform` is the per-frame property and has no duration by design, so a gesture that writes into it and then simply stops writing puts the bubble back in one frame. The way home is therefore one added animation — `springHome()`, `composite: 'add'`, off wherever the gesture had got to — and the play drag and the pull both hand over to it on release, both taking it off again (`cancelSpring`) the moment the finger comes back. A gesture that adds a springback curve of its own instead is a second answer to the same journey.

It was a class once, `homing`, which gave `transform` a 520ms transition on a curve that overshoots by 1.7 — and `transform` is the property everything written per frame shares. A per-frame value driven through an overshooting spring is a bubble that bounces and lands where nothing sent it, and the class outlived the gesture that added it: `untoy()` guarded on the offsets, which it wrote back as `0px` rather than removing, so after the first drag of a session every later release put the class back and left it there. Three separate bug reports — the bubble teleporting, the haptic feeling fragile, overshoots being clipped — were that one rule. **An offset at rest is removed, never written as zero**: a zero is still an answer, and something will read it as one.

## A reflow is a jump; put it back

Easing a box does not ease a **relayout**. When a state changes what the layout *is* — a row becoming a column, an item moving above the thing it was beside — every part of it arrives at its new place in one frame however carefully its own width and height were transitioned. There is no easing for that, in any browser.

So the layout is allowed to jump and then put back: measure each part before the change and after it, give it the transform that returns it to exactly where it was for one frame, and let go of that transform on the next. Letting go is what the eye reads as the move (`settleLock`). Only things that resample carry a scale with them — an image does, text and icons stretch — so everything else translates and keeps its own size transition.

Two traps come with it. The transform has to be cleared on a *later* frame than it was written, or the browser only ever computes the second value and there is no transition at all. And the "after" measurement has to be taken against the container at its **final** size: a height still easing towards its new value measures as the old one, and in a bottom-anchored box everything inside is placed off that height — so the container's own size comes off the CSS transition list and is animated by hand instead.

## A gesture can be taken away as well as finished

Over an app, SystemUI has its own shade gesture on this strip and pilfers the pointer partway down: the page sees a cancel with the finger still moving, not a lift. Discarding the gesture there is why the pull worked on the home and lock screens and did nothing at all inside an app — and why it got worse the further the pull had to travel. A cancel mid-gesture is *stronger* evidence of an ask than a lift is, so it commits at a lower bar (`PULL_STOLEN` against `PULL_SETTLE`). Any gesture with a distance threshold owes the same reading of a cancel.

## The drag band scales down over a list

The object-like press-and-drag every bubble gets (`motion.js`'s rubber band, `DRAG_RADIUS`) is right for a bare bubble and wrong once it has grown into the notifications tab: that box holds a scrollable list, not something to be pushed around, and a swipe meant for the list underneath was shoving the whole panel with it. `dragRadius()` reads `shared.size` and returns 15% of the resting radius while it is `'history'` — an 85% cut, not zero, so the bubble still answers a touch rather than reading as dead. Cutting the radius alone is enough: `rubberBand()`'s hyperbola takes its whole shape from that one number, so a smaller radius already gives both less reach and less give per pixel, and no second constant is needed for "intensity". Any other tab that turns out to hold a scrollable list of its own owes the same read of `shared.size`, not a second copy of the scale.

## A bubble can be carried

Past `CARRY_GRAB` a drag stops being a rubber band and becomes a carry: the bubble comes off its spot, follows the finger one to one, and stays where it is let go of. Under that threshold everything behaves exactly as it did — band, springback, trade — so this is additive rather than a mode the bubble is put into. Only from a closed, idle bubble: an open panel is centred on the screen and an alert is a thing arriving at the hole, and a drag on either already means something else.

Four collisions, each answered rather than discovered:

- **The place is a `translate`, added to the row own shift.** Two authors, one property, no fight — they are two different reasons for the same bubble to be somewhere other than the middle, so they add. `--carry-ms` is zero while a finger is on it and a real duration when it is being put down or fetched home.
- **The goo layer region travels with it**, as a band around wherever it is standing. Not the whole screen: a filter costs its area, and the whole screen is exactly the cost the bar-sized region exists to avoid. Outside the region a bubble silently loses its skin and paints its own background, which reads as a colour bug.
- **The proxy follows, and only when the finger is off it.** Moving a window under a live finger is the cancel trap the hold already knows about, so the window is placed on release, not per frame.
- **Anything past a mod happens at the cutout.** The bubble is fetched home for an alert or an open panel and given its place back when that closes. One rule, applied in `setSize`, because the alternative is re-deriving every geometry in the row against a moving origin.

Free placement rather than the edge-snap dock Google and Samsung bubbles use, and that is a *window* decision before it is a taste one: a bubble parked half off-screen needs a proxy hanging over the edge and a dock needs a proxy the size of the dock, while free placement keeps the proxy exactly as big as the bubble has always been. Carrying therefore costs no extra pixels of shade swipe — only different ones. Let go within `CARRY_HOME` of the hole it is home again, so there is always a way back.

Where it was put is stored, because a bubble that went home every time the phone slept was never really put anywhere.

## The punch hole is the origin

Everything on this screen is born at the true middle of the screen, which is the middle of the punch hole and the middle of the bubble drawn around it. Nothing spawns where it will eventually live. A pill that belongs somewhere else — the torch out by the clock — is born at the hole as a small drop, travels to its spot, and only then behaves like a pill standing there. The travel distance is the host's to supply (`window.setFlight`): the page does not know how wide the screen is.

## The glyph is the cause; width is the consequence

A mod arriving never widens the bubble and then fills it. The mod's glyph flies in first, lands against the left inset, and the bubble opens out around it *because* it got there. On the way out the same thing runs backwards: the width is given back first, then the glyph leaves. The same applies to a whole pill arriving from the cutout — it hits its stop and opens out off that stop.

A mod that ends because its app was killed leaves the same way: `flyGlyphOut()` gives the width back, fades the reading with it, and only then runs the glyph down into the hole. Pushing `null` is a departure, not a repaint — a face swapped for the idle one on the spot reads as the bubble forgetting rather than as the mod going — and a departure in flight owns the face until it lands, or the next repaint of the closed row cuts it short.

- `LAND` is the share of the arrival at which the flying thing is actually against its stop. The arrival curves overshoot and settle back, so this is under halfway, not the end. Hand over at `LAND`, never on animation end, or there is a visible pause between the landing and the width change. They are one action and one reaction.
- Ask the host for the window room the growth will need *before* the flight, not when the growth starts. The window is resized a frame or two after being asked, and a resize landing mid-growth clips it.

- **The row standing aside is one move, not a split.** When the light comes out the whole row changes place without changing shape, so the bubble and its satellite travel on the same numbers (`SHIFT_HOME`, no delay). The circle's own defaults are a *split's* — 150ms of delay and then 380ms of travel — and left on, the bubble arrived and stopped before its satellite had started. Anything that moves the row as a whole owes both of them the same clock.
- **Where a bubble cannot give way, its content does.** The light stops shrinking at `NOW_COVER` because below that it uncovers the chip it exists to hide, and the row keeps coming regardless. What collides after that is the two contents, and the one with somewhere to go moves: the reading is pushed left by exactly what the row has taken, bounded by the glyph, which never moves. Not a fade and not a clip — it gives way, the way one body of liquid does when another arrives beside it.

## The glyph is a shared element

Every mod's glyph sits at the same left inset in the bubble and in its satellite. A mod moving between the two is therefore a hand-over of one picture that does not move, not two animations of two copies. While a hand-over is in flight the main bubble must be genuinely in its closed state — compact width, idle face — or the illusion breaks: an outgoing mod's icon still showing inside the bubble proves it was never really closed.

## Liquid

Two shapes belong to one body of water when their blurs overlap. That is one `feGaussianBlur` plus one `feColorMatrix` alpha contrast, and shapes bridge at roughly twice the blur's `stdDeviation`.

- Everything inside a goo layer must be **solid**. A half-transparent shape falls below the contrast's threshold and is erased rather than merged; the transparency is worn by the layer, never by what is in it.
- In the bubble row the strength is **not a constant**. A fixed deviation strong enough to fuse a satellite leaving the bubble welds the whole row into one bar for as long as it stands there, and one weak enough to leave the row alone never merges anything: no single number is both. `meltBy()` reads it off the measured gap between the shapes every frame, so they fuse as they come together and let go as they part — which is what liquid does anyway. A fixed deviation is right only where the shapes never rest close together, like the torch's step slider.
- Merging happens across shapes inside **one** goo layer, which means inside one surface. Two windows cannot fuse: separate surfaces are composited by the system and no filter reaches across them. This is the whole reason the canvas window exists — every bubble is drawn in it so that every bubble can merge with every other one. See [architecture.md](architecture.md#windows); a new bubble is never given a window of its own to draw in.
- **Faking it across two windows has been tried once and failed.** Each side flattened the corner facing the other and grew a directional glow towards it, driven by one proximity number the host measured between them. It reads as neither: the flattened corners look like the pill has been *cut off* at the edge of its window, and the glow looks like a stray highlight on the bubble rather than like attraction. It was also on almost permanently, because two pills on one bar are never far apart. Corners and shadows are not what makes the row read as liquid — a shared blur and one alpha contrast is, and that is exactly the thing a second window cannot have. If cross-window merging is wanted, it needs both shapes on one surface, which means one window, not a better approximation.
- **A drag has to melt while the finger is down.** In the bubble row the trade is width-neutral by construction — the bubble gives up exactly what the satellite takes — so the distance between the two centres is constant and the gap never changes on its own. Nothing melts, and the whole liquid hand-over shows itself only on the settle after the finger has gone. `SWAP_PRESS` is what fixes that: the pair is pressed together on top of the trade, peaking at halfway and zero at both ends, so they close to an overlap, fuse through the middle and let go again as the trade finishes. Any future gesture that trades two shapes owes the same thing — if the geometry gives the skin nothing to read, the gesture is static however correct it is.
- **A knock carries the momentum that caused it.** The shove an arriving glyph takes is the row's travel carrying on through it, so its distance is read off the finger's speed at release (`BUMP_MIN`…`BUMP_MAX`, full at `SWAP_FLICK`), never fixed. A constant makes a flick and a crawl end identically, which tells the user their hand did not matter. A finger that stopped before it lifted threw nothing.
- **A threshold is felt when it is crossed, not when the finger lifts.** A buzz on release tells the user what happened after they have already committed to it; the crossing is the moment there was still a decision to make. It fires in both directions and once per crossing. What the threshold *does* fires there too — the lock bubble opens on the upward flick passing the line, not on the finger going — and a threshold that has fired ends the hold running underneath it.
- **A goo layer reaches as far as its filter region and no further.** The region is deliberately small — a filter costs its area, and on a bar-height row that is the one thing free to be cheap — so a bubble drawn outside it has its skin silently dropped while going on painting its own background, with an unfiltered blob stacked behind it. That reads as one bubble with the wrong alpha and no glass, and it looks like a colour bug rather than a geometry one. A bubble that stands anywhere but the row (the lock screen's, at the bottom of the canvas) has to grow the region while it is there, and only while it is there.
- **A bubble that paints its own background must stop when the skin takes over.** `html.liquid` blanks background and border on every bubble on the canvas. Leaving one out is the same bug from the other end: the box and the skin both paint, and that bubble alone reads as solid where the others read as glass.
- The liquid skin mirrors real elements' `getBoundingClientRect()` frame by frame rather than duplicating any geometry rule, so one skin follows transitions, drags and holds for free. Read all rectangles first, then write — interleaving them thrashes layout.

## Blur is the host's, and the bubble's own

Nothing a WebView draws reaches pixels it does not own, so the frosted region is a bare `View` in the host window — one pane per bubble, `BLUR_PANES` on both sides. But it is not a second element with a life of its own: **the glass is mirrored off the bubble, exactly as the liquid skin is.** `sendBlurFrame()` rides the same per-frame measurement, sends `left,top,width,height,corner` per bubble in the page's own coordinates, and the host does nothing but place the panes there. No duration, no curve, no easing crosses the bridge.

It was the other way round once: the page said where each shape was *heading* and how long it would take, and Kotlin replayed that curve on its own clock. Every curve in the CSS then had a twin in `BubbleService`, and anything the page had not thought to describe — a hold's scale, a drag, a swap that renames two boxes without moving them — left the frosted rectangle standing where its bubble was not. Two elements trying to keep step is the bug; one measurement feeding both is the fix. A pane cannot disagree with a rectangle it is read off.

What follows from that:

- **A new bubble needs a pane on both sides.** `BLUR_PANES` in `pill.html` names the order the regions are sent in and `BubbleService.BLUR_PANES` is how many panes stand ready to receive them. Nothing joins them at build time.
- **The mirror only runs while something says it is moving.** `stirLiquid(milliseconds)` is what keeps the frames coming, and it is not gated on the skin — a grown panel has glass exactly as a closed bubble does. A transition started without stirring for its length freezes the glass part-way, which is the same old bug wearing a new hat.
- **An invisible bubble sends an empty region**, and the host hides and clears that pane: a transparent view goes on blurring. Fading is a shrinking pane rather than a fading one, because glass cannot fade.
- A geometry number no longer has to be re-derived for the blur — `restingMain()` versus `compact.width` and the rest of it — because nothing derives it any more. It is measured.

## A merge is measured, not scheduled

When one bubble runs into another and disappears into it, nothing about the receiver's reaction is on a clock. The pair is registered — `catchInto(traveller, receiver, steps)` — and the mirror that already measures every rectangle each frame computes **how much of the traveller is inside the receiver**: the overlap of the two boxes as a share of the traveller's own area. That single number is the whole merge.

- **The stretch is continuous.** `--absorb` is written on the receiver every frame as `entered × bulk × CATCH_SWELL`, where bulk is the traveller's area over the receiver's — a dot barely disturbs the bubble, a satellite of the same size disturbs it fully. CSS turns it into a squash: wider and shorter, on `scale`, because the transform is the hold's and the translate is the row's place.
- **The steps fire on shares, not on times.** `CATCH_ENTERED` (0.8) is where the receiver may change state — the bubble opening out around what arrived. `CATCH_SWALLOWED` (0.97) is where the traveller can be taken off screen without it being seen going. These are the thresholds to tune; nothing else about a merge has a number.
- **The release is a spring, not a keyframe.** While a catch is live the swell is driven, and the *speed* it is being driven at is kept. When the traveller is gone the spring takes that velocity and settles back through zero: liquid taken in jumps its tension and contains itself again, harder for a shape that ran in hard than for one that drifted in. `CATCH_STIFFNESS` / `CATCH_DAMPING`.
- **A catch that cannot finish still finishes.** A traveller can be caught out of the air — dressed again, sent back out — so `releaseCatch()` exists and is called wherever that happens (`dressSatellite`, `dropSatellite`, `stopNowFlight`). `CATCH_CEILING` is the last resort: after it, the steps run whatever the boxes say. It is a safety net, never a schedule.

This replaced a set of timers — travel length, a share of it for the hand-over, a fixed wait before the retire — and those numbers were guesses at where a shape would be. An ease that overshoots, a drag that reverses, a bubble a different width today all put the reaction somewhere other than the moment it belonged to, and that gap between two animations each correct on its own clock is what read as lag.

Two limits worth knowing. The measure is **bounding boxes**, so pills and circles overlap slightly sooner than their outlines do — the thresholds are named against this measure and the goo sells the contact anyway. And it only applies to **a bubble entering a bubble**: a mod's glyph coming up out of the punch hole is inside the bubble the whole way, so there is no overlap to read and that one leg is still timed against its own arrival curve (`handOverAfter`). A swap is not a merge either — two boxes trade names without either of them travelling, and nothing ran into anything.

**A traveller has to become a drop.** `enteredShare` is the share of the *traveller* that is inside the receiver, and it stays that way: measured against whichever box is smaller it reads 1 as soon as a big traveller covers a small receiver, which on the lock bubble's flight was a third of the way in — a shape still most of the screen wide counted as swallowed and went out in front of the user. A traveller wider than what is taking it in has not arrived; it is something that still has to close. So a shape that starts bigger than its receiver ends the journey at the receiver's *measured* width, and the thresholds mean what they say again.

## Timing

Legibility beats speed. A merge the user cannot follow is wrong even when every step is correct — the cause has to be visible as the cause. When something is not readable, the fix is to sequence it properly, not simply to lengthen it.

Never leave the state that is being animated out of a phase in the middle: an element retired in the same frame its retreat begins vanishes on the spot, and the growth it was supposed to explain then happens for no visible reason.

## Room and the hole

Two rules live in [bubbles.md](bubbles.md) because they are properties of being a bubble, and are named here because every motion change runs into them:

- **Nothing is ever clipped.** A bouncy curve overshoots, and a container sized to the target cuts the overshoot off. Overflow is visible on anything that is a bubble, and the window room an overshoot needs is asked for before the animation starts, never while it runs.
- **Nothing crosses the punch hole.** Content stops at the margin either side of screen centre and fades out — fade, never an ellipsis, never a hard cut — and this holds for resting states most of all, since a resting state is over the hole for as long as it stands there.
