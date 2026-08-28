# Dynamic Bubble — the Android arm

`C:\Users\vadim\source\Android Dynamic Island` · Kotlin + WebView + Shizuku · Galaxy S25
(SM-S931B, Android 16 / SDK 36, One UI 8.5) · sideload only, one device, no Play Store.

An overlay pill at the punch-hole — a Dynamic Island for a Samsung phone — with the motion language
borrowed from the other projects and retuned for the platform.

## What it proves

**The identity survives a `WebView`, and that is the cheapest correct port.** Both surfaces (the
control panel and the pill itself) are WebViews over one shared stylesheet, so the panel primitive,
the micro-label, the bar-sweep reveal and the positional stagger are the *same implementation* as the
web app rather than a second one that drifts.

```
app/src/main/assets/dna.css     the token layer — faces, palette, panel, label, reveal
app/src/main/assets/pill.css    the bubble's own styles
app/src/main/assets/panel.html  the control panel (two screens: Settings green, System blue)
app/src/main/assets/pill.html   the bubble: markup and the SVG filter definitions only
app/src/main/assets/js/         ES modules — liquid, motion, row, state, bridge, tabs, mods/
app/src/main/assets/fonts/      the four identity faces, registered once in dna.css
```

The page was one 6,600-line `pill.html` until it was split. Two things made that possible and both
are worth stealing: the assets are served over a **virtual https origin** rather than
`file:///android_asset/`, because modules cannot load from an opaque origin
(`platforms/android-shizuku.md`), and the split shipped as its own commits with no behaviour change
in them, checked against a **recorded run of the debug stage** taken before it started.

## Kotlin side

| File | Owns |
|---|---|
| `BubbleService.kt` | The overlay window and its WebView. An `AccessibilityService` **for window layering reasons only** (see below). |
| `IslandNotificationListener.kt` | Notification intake. |
| `AppStyles.kt` | Per-app accent colours, sent to the pill as data — Telegram, Spotify, Discord, WhatsApp, Instagram, Gmail; anything else falls back to terminal green. |
| `ShizukuShell.kt` / `ShellService.kt` | The shell-UID command runner, bound lazily and never assumed present. |
| `MicrophoneAccess.kt` | Sensor-privacy toggle through Shizuku. |
| `Preferences.kt`, `NotificationLog.kt`, `MainActivity.kt` | State, history, the panel host. |

## The hard-won facts

Full detail in `platforms/android-shizuku.md`. The headlines:

- **`TYPE_ACCESSIBILITY_OVERLAY`, not `TYPE_APPLICATION_OVERLAY`.** SystemUI's status bar ranks above
  every application overlay, so a bubble drawn at the cutout is visible but not touchable — taps go
  to the status bar and are swallowed. The accessibility overlay is the only window type above the
  status bar an unprivileged app can obtain, and it is granted by a user toggle rather than a
  signature permission.
- **Kotlin owns state and window geometry; CSS owns appearance.** JSON in via
  `window.onStateChanged(...)`, intent back out via `@JavascriptInterface`.
- **Shizuku is the privilege model** for anything a sideloaded app may not write — the microphone
  toggle is sensor-privacy state, so it goes through a user service running as the shell UID.
- **Restricted settings** block sideloaded apps from being granted accessibility/notification access
  until *Allow restricted settings* is used; `grant.ps1` does the whole dance over adb.
- The interface is **two screens with their own accents and diagonal slices**, changed only by the
  bottom nav or the number row — never by a swipe, because full-width sliders own the horizontal
  stroke. That is the gesture-ownership rule from `dna/06-interaction.md` applied literally.
- **One window draws, and it draws everything.** Bubbles merge through a single SVG goo layer — one
  blur plus one alpha contrast — and a filter reaches exactly as far as the surface it is drawn on.
  So a bubble in a second window can never be liquid, and the window model follows from that one
  fact rather than from anything about layout. Faking it across two windows was tried once and read
  as neither: flattened corners look like the pill was *cut off*, and a directional glow looks like a
  stray highlight rather than attraction.
- **The skin and the glass mirror the real boxes, frame by frame.** Nothing is told where to go: the
  liquid outline and the host's blur panes are both read off `getBoundingClientRect()` every frame,
  so they follow transitions, drags and holds for free. The version that replayed the page's curves
  on the Kotlin side needed a twin of every CSS curve and still left the frosted rectangle standing
  where its bubble was not. **One measurement feeding both beats two things trying to keep step.**
- **A merge is measured, not scheduled.** How much of an arriving shape is inside its receiver is one
  number, computed per frame, and it drives the squash, the state change and the retire. The timer
  version guessed where a shape would be, and an ease that overshoots or a drag that reverses put
  every reaction slightly off the moment it belonged to — which is what read as lag.
- **CI builds the debug APK and publishes it as a rolling release**, so a machine without the SDK can
  still compile a change. It never makes one *verified*: the check is still the phone.

## Direction

The stated target beyond the pill: **injecting modules into the Good Lock applications**, to fix the
inconsistencies One UI leaves in place. Not implemented in this repo yet — recorded here so the next
session knows where it is heading and does not treat it as done.
