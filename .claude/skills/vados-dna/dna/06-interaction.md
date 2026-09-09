# Module 06 — Interaction and input

The interface is a machine you operate, not a document you browse. Input is the largest part of that
feeling, and most of it is decided by the *pointer*, not the screen shape.

## Navigation is stateful, not scrolled

There is no visual scrollbar and no document scroll. The wheel, the keyboard and the swipe all express
*intent* to change screen; a state machine decides what happens. Screens stay mounted; they are
entered and left.

- **Wheel:** one notch = one step, on a pointer device.
- **Swipe:** a drag past a threshold (~30% of viewport height) = one step, on a handheld.
- **Keyboard:** `W`/`S` step one screen; the number row jumps directly (`1` = the first registry
  entry). The digit range is derived from the registry length, so adding a screen extends it without
  a code change. Every key routes through the same change function as the wheel, so locks, skip rules
  and transitions apply for free — a locked screen simply ignores its digit.
- A backtick / slash / zero opens the terminal; a dedicated digit is the dev cache-wipe key.

**Every input path must funnel into one change function.** A key handler that manipulates state
directly is how a lock gets bypassed.

## The pointer decides, not the width

| Follows the **pointer** (`coarse` vs `fine`) | Follows the **orientation** |
|---|---|
| Nav shape (bottom row vs side column) | Which layout is rendered |
| Wheel-step vs drag-navigation | Arrangement within a screen |
| Hover affordances, cursor fields, magnetism | Transition axis (`scaleX` vs `scaleY`) |
| Touch drag initialisation | Slice geometry |
| Control hit sizes | |

Anything that is a *touch affordance* (bottom nav, swipe paging) or a *pointer affordance* (hover
lists, cursor fields) keys off the pointer. Anything about the shape of the screen keys off
orientation.

## Hover on touch is a bug waiting to happen

A touch screen fires `:hover` — and a synthetic `mouseenter`/`mousemove` — on tap, then leaves it
applied until something else is tapped. Every hover effect becomes a stuck state.

- **CSS:** wrap hover rules in `@include hoverable`. Keep the non-hover part of the rule (`&--open`,
  `.selected`) *outside* the mixin, or it disappears on touch too.
- **JS:** gate `mouseenter`/`mousemove` handlers on `!isMobileDevice`.
- **Deliberate exceptions** are allowed and should be listed explicitly in the project's rules, so
  they read as decisions rather than oversights. Here: the panel border glow, and one title's
  character interaction.

## Touch equivalents are different interactions, not bigger hit areas

- **Hover becomes nothing, not tap.** A tap that only previews costs the visitor the one gesture they
  have.
- **Hold and drag become tap.** A drag-to-spin control becomes tap-to-activate seats on a phone,
  because a vertical stroke there already belongs to the screen step and a competing handler loses
  either way.
- **Only one gesture may own an axis.** The axis is locked once per gesture, centrally. Anything
  animating off a stroke *declares its region* (`data-scroll-region` vertical, `data-carousel-region`
  horizontal) rather than binding its own listener. A component that wants a stroke on the screen axis
  must give up that stroke instead.
- **`touch-action` states the intent to the browser too.** `pan-y` on a surface that only reacts to
  taps keeps native scrolling responsive; `none` is for pointer devices, where nothing else wants the
  stroke.

## The mobile address-bar problem (worth knowing anywhere with dynamic viewports)

A mobile browser only collapses its address bar for a genuine document scroll, and this app never
gives it one. So the handheld gets a short runway of real scrollable height that the first upward
swipe of the session is handed, before gesture-claiming begins:

```scss
html.is-mobile-device body { min-height: calc(100svh + 12vh); }
html.is-mobile-device.runway-spent body { min-height: 100svh; }
```

`svh`, not `dvh` — the small viewport is the one with the bar still showing, so the runway is a
constant; a `dvh` term would shrink as the bar collapses and re-trigger the layout it is trying to
settle. And the runway must be *removed* once spent, not merely ignored: left in place, the browser
scrolls the document back to 0 whenever the bar returns.

Scrollable regions set `overscroll-behavior: contain` so a scroll never chains out into that runway.

## Feedback

- Every pressable thing owns its press feedback, because the OS tap highlight is removed globally.
- Completion of a screen transition fires a `10ms` vibration where available.
- Focus is always visible. `outline: none` is only ever acceptable with a replacement in the same rule.
- Destructive controls are always the danger colour, never the screen accent, and always require a
  second, differently-worded confirmation.

## Anti-patterns

Scroll-driven interfaces. Hover as the only route to information. A tap that merely previews. Two
handlers competing for one axis. Removing a platform affordance without replacing it. Key handlers
that bypass the central state change. Width-based decisions about touch.
