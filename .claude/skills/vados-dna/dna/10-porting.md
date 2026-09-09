# Module 10 — Porting to non-web platforms

CSS is the reference implementation because it is the fastest to write and to look at. None of the
identity depends on it. This module maps each rule to what it means elsewhere.

## The translation table

| Web mechanism | What it actually is | Elsewhere |
|---|---|---|
| `--section-color` custom property | One app-level theme value, inherited | Godot: an autoload with a `theme_accent` signal, or a `Theme` type variation swapped at runtime. WPF/WinUI: a `DynamicResource` brush. Unity: a ScriptableObject palette asset every widget binds to. |
| `color-mix(in srgb, …)` | Derived tint, computed not authored | `Color.Lerp` / `lerp()` toward the base. Never a second authored colour. |
| `clamp(min, vw, max)` | A ramp over viewport size | `clamp(min, viewportSize * ratio, max)` computed on resize. Godot: a `Control` script recomputing font sizes from `get_viewport_rect()`. |
| Fluid root `font-size` | One scale lever everything hangs off | A single `uiScale` float, applied at the root container. Every size expressed as a multiple of it. |
| `@media (pointer: coarse)` | Device axis | `DisplayServer.is_touchscreen_available()`, `Input.touchSupported`, `PointerDevice` queries. |
| `@media (orientation: portrait)` | Layout axis | Aspect-ratio check on the viewport, evaluated on resize. |
| GSAP timeline with absolute positions | A keyed choreography | Godot `Tween` with explicit `set_delay()` per step (not chained `chain()`), or an `AnimationPlayer` track. Unity: DOTween sequences with `Insert(time, tween)` — `Insert`, never `Append`. |
| `overwrite: 'auto'` | Kill conflicting tweens on the same target | `kill_tweens_of(target)` / `DOTween.Kill(target)` before building. |
| `prefers-reduced-motion` | OS accessibility signal | Query the platform setting; where none exists, expose the app's own toggle and default it off. |
| Scoped stylesheet | Per-component styling | Godot: per-scene `Theme` overrides. Keep shared values in one resource, never duplicated per scene. |

## What survives every platform

These are the identity, and none of them are web features:

1. **Near-black ground, hairline borders, translucent panels, no shadows.**
2. **One accent per screen, inherited from the root, never hardcoded in a leaf.**
3. **Monospace uppercase micro-labels, wide letter-spacing, pinned to a panel corner.**
4. **Every element has an enter and a leave; the leave is faster, immediate, and unstaggered.**
5. **Screens assemble diagonally from the top-left.**
6. **A masked transition covers every screen swap; the incoming screen settles behind it.**
7. **Idle loops exist, are small, are desynchronised, and pause off-screen.**
8. **One ordered registry generates the navigation, the accents and the shortcuts.**
9. **A reduced-motion path that is a substitute pair, not a speed multiplier.**
10. **No abbreviations anywhere in the code.**

## Platform-specific notes

**Godot / game UI.** The whole design is already game-menu shaped, so it ports the most directly.
Screens are always-loaded `Control` nodes toggled by a state machine, never `queue_free`d — the same
reason the web version never unmounts: a tween needs a live target. The curtain is a `CanvasLayer`
above everything. Haptics and audio become available here, and audio should follow the same three-phase
law: an enter sound, an optional idle bed, a leave sound — and the leave sound is shorter.

**Desktop apps (.NET / WPF / WinUI / Avalonia).** The border-glow effect is the one thing that costs
real work; approximate it with a border brush that lerps toward the accent on hover and skip the
pointer hot spot rather than faking it with a bitmap. Everything else maps to resources and storyboards
one-for-one. Storyboards must be defined in pairs and named `Enter*` / `Leave*` so a missing half is
visible in the file.

**Native mobile.** The device axis collapses (always coarse), so the pointer-gated affordances are
simply absent, and the layout axis becomes the only branch. The gesture-ownership rule matters more,
not less: the platform's own back/scroll gestures are a competing owner of the same axis.

**Android (native, WebView-hosted).** The cheapest correct port on this platform is not a port at all:
host the CSS in a `WebView` and keep the identity as the reference implementation it already is. A
`WebView` with a transparent background is a legitimate UI surface — it renders an overlay window
(`TYPE_APPLICATION_OVERLAY`) as happily as a settings screen — and it means the panel primitive, the
micro-label, the bar-sweep reveal and the positional stagger are the *same* code as the web app rather
than a second implementation of them that drifts. Rebuilding hairline panels and letter-spaced labels
out of Android views costs a drawable, a style and a custom view per element, and buys nothing.

What the platform still owes you, and what it takes back:

- **Fonts are assets, registered once.** Copy the four files into `assets/fonts/` and `@font-face`
  them from a single shared stylesheet that every surface links. Never per-page.
- **One stylesheet is the token layer.** `dna.css` holds the ramp, the accent and the panel primitive;
  a page adds only what is unique to it. This is the same "shared code is declarations, not output"
  rule from Module 04, enforced by there being exactly one link tag.
- **Kotlin owns state, CSS owns appearance.** The bridge pushes JSON in
  (`window.onStateChanged(...)`) and takes intent back out (`@JavascriptInterface`). Geometry that the
  window manager owns (an overlay's size and position) stays in Kotlin; everything visual is a custom
  property. Splitting it any other way ends with two sources of truth for one pixel.
- **The activity theme is a real flash of light.** Without `android:theme` set to a dark theme, the
  system paints a white window for a frame before the WebView draws — a light background is an
  anti-pattern that arrives by default here, so it must be turned off explicitly.
- **Haptics are free and expected.** `VibrationEffect.createPredefined` from the bridge, fired on an
  `animationend`/`transitionend` listener, is the frame-accurate version of the `10ms` completion
  buzz from Module 05 §6.6.
- **`color-mix()`, `clip-path`, `dvh` and `env(safe-area-inset-*)` are all available** in the system
  WebView on a current device — the CSS does not need a compatibility dialect. `backdrop-filter` is the
  one worth measuring before shipping.
- **The cutout is the layout.** A phone with a punch-hole has a hard obstacle in the top centre, and
  its bounds are readable (`dumpsys window displays`). Treat it the way Module 04 treats reserved
  space: one token pair at the root, read blindly by every surface.

**Terminal / CLI tools.** The palette reduces to the grey ramp plus one accent; the micro-label
becomes a bracketed uppercase prefix; the tone system (Module 08) is already terminal-native. The
three-phase law still applies — a spinner is the idle phase, and it must have a completion state, not
just stop.

## The porting checklist

1. Copy the four font files, register them once at the app root.
2. Define the grey ramp and the ground as constants. One place.
3. Define one accent value at the root; make every widget read it, never hold it.
4. Build the panel primitive first — fill, hairline border, radius, corner micro-label. Everything else
   is that primitive.
5. Establish the scale lever before building any screen.
6. Write the transition cover before the second screen exists.
7. Write the enter/leave pair for the panel primitive; every later element inherits the pattern.
8. Add the reduced-motion substitute pair at the same time, not later.
