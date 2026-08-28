# Platform — Android (Samsung / One UI, Shizuku, WebView UI)

**Reference project:** Dynamic Bubble (`Android Dynamic Island`). Target device: Galaxy S25
(SM-S931B), Android 16 / SDK 36, One UI 8.5. Sideload only.

## The stance

**Shizuku is the baseline privilege model.** These are personal, sideloaded, single-device apps that
need to do things a normal app may not, and rooting is not the answer. Shizuku runs a user service
under the **shell UID** — the same privilege level `adb shell` has — and the app binds to it. Anything
`cmd`/`dumpsys`/`pm` can do from a shell, the app can do.

This is the second generation of a pattern that started in Brawl Stars Controller, where a C helper
ran as the shell user over adb and published a virtual multitouch device through `/dev/uinput`. Same
idea: shell is already in the groups that matter; use it instead of root.

**The declared direction: injecting modules into the Good Lock applications**, to fix the
inconsistencies One UI leaves in place. Not implemented yet — treat it as the roadmap, not as
existing behaviour.

## UI: host the CSS, do not re-implement the design

The cheapest correct port of the DNA to Android is **not a port**. Host it in a `WebView` and keep
the CSS as the reference implementation it already is:

- A `WebView` with a transparent background is a legitimate UI surface — it renders an overlay window
  as happily as a settings screen.
- The panel primitive, the micro-label, the bar-sweep reveal and the positional stagger stay the
  *same code* as the web app instead of a second implementation that drifts.
- Rebuilding hairline panels and letter-spaced labels out of Android views costs a drawable, a style
  and a custom view per element, and buys nothing.

Structure that works:

```
assets/dna.css      the token layer — faces, palette, panel primitive, micro-label, reveal
assets/<surface>.html   one file per surface, each linking only dna.css plus what is unique to it
assets/fonts/       the four identity faces, @font-face'd once in dna.css
```

**Kotlin owns state and window geometry; CSS owns appearance.** JSON pushed in
(`window.onStateChanged(...)`), intent handed back out (`@JavascriptInterface`). Geometry the window
manager owns — an overlay's size and position — stays in Kotlin. Splitting it any other way ends with
two sources of truth for one pixel.

## Window layering — the one that costs a day

**SystemUI's status bar ranks above every `TYPE_APPLICATION_OVERLAY`.** A window drawn at the cutout
is visible but not touchable: taps go to the status bar and are swallowed.

`TYPE_ACCESSIBILITY_OVERLAY` is **the only window type above the status bar an unprivileged app can
obtain**, and it is granted by the accessibility toggle rather than by a signature permission. So an
overlay at the top of the screen is an `AccessibilityService` for layering reasons alone — subscribe
to window events only, read no screen content, and let Android own the lifecycle. There is nothing to
start or stop from the app.

Flags that go with it: `FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_NO_LIMITS | FLAG_LAYOUT_IN_SCREEN`. The
running service instance is the "is it on?" flag, because the system owns its lifecycle.

## Restricted settings

Android blocks a sideloaded app from being granted accessibility or notification access until the
user opens **App info → ⋮ → Allow restricted settings**. A greyed-out toggle is Android's policy, not
a bug. Ship an *Open app info* button, and a script that does the whole dance over adb:

```
pm grant <package> android.permission.ACCESS_RESTRICTED_SETTINGS
settings put secure enabled_accessibility_services ...
```

Shizuku itself must be restarted after every reboot:

```
adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
```

## Shizuku practice

- **Bind lazily and never assume present.** `isReady` means *running, permitted and bound* — three
  separate conditions. Wrap every Shizuku call in `runCatching`; the binder can be gone at any moment.
- **Degrade honestly.** Without Shizuku, a control shows "no access" and does nothing. It never lies
  about state, and it never silently no-ops while looking enabled.
- **Read state back out of the system, not out of your own last write.** Sensor privacy, for example:
  write with `cmd sensor_privacy enable/disable 0 microphone`, read with `dumpsys sensor_privacy`
  (sensor 1, toggle type 1 — state 1 = blocked, state 2 = allowed).
- Things a sideloaded app may *not* write directly, and therefore go through Shizuku: sensor-privacy
  state, most `Settings.Secure`/`Settings.Global` keys, service enablement.

## WebView specifics on a current device

- **`color-mix()`, `clip-path`, `dvh` and `env(safe-area-inset-*)` are all available.** The CSS needs
  no compatibility dialect. `backdrop-filter` is the one worth measuring before shipping.
- **The activity theme is a real flash of light.** Without `android:theme` set to a dark theme the
  system paints a white window for a frame before the WebView draws. A light background is an
  anti-pattern that arrives *by default* here, so it must be turned off explicitly.
- **Haptics are free and expected.** `VibrationEffect.createPredefined` fired from the bridge on an
  `animationend`/`transitionend` listener is the frame-accurate version of the completion buzz in
  `dna/05-motion.md`.
- **The cutout is the layout.** A punch-hole is a hard obstacle in the top centre and its bounds are
  readable (`dumpsys window displays`). Treat it the way `dna/04-layout-and-sizing.md` treats reserved
  space: one token pair at the root, read blindly by every surface.
- **Wake locks are a budget.** A bubble that wakes the screen holds it for seconds, not minutes —
  long enough to watch it arrive and read it, short enough not to drain.
- **ES modules do not load from `file:///android_asset/`.** A `file://` document has an opaque
  origin, and a module import is a CORS-checked fetch, so `<script type="module">` fails — silently,
  with a dead interface and nothing obvious in logcat. Classic `<script src>` is unaffected, which is
  why a page can work for years and then break the moment it is split into modules. Serve the assets
  over a virtual https origin instead (`WebViewAssetLoader`, `appassets.androidplatform.net`); it is
  a real origin, so modules, `fetch` and workers all behave as they do in a browser. Do not reach for
  `allowFileAccessFromFileURLs` — it widens what page JavaScript may read and does not reliably fix
  module CORS anyway. **Probe this before splitting a page**: one trivial module and one console line
  costs a minute and decides the whole architecture.

- **Vendor effects called by reflection are a per-frame budget, not a free call.** One UI's blur
  (`android.view.SemBlurInfo` via `semSetBlurInfo`) is reached by reflection because AOSP's
  `FLAG_BLUR_BEHIND` is dead on these builds. Caching the `Class`/`Method` lookups is the obvious
  half; the half that gets missed is that *invoking* them still costs — a builder allocation plus
  three `Method.invoke`s per surface per frame, which on five surfaces at 120Hz is thousands of
  reflective calls a second and a fresh object each time. **Gate re-application on the value actually
  changing**, and never read a `SharedPreferences` key inside a per-frame path. The symptom is not a
  crash; it is a phone that runs warm and an interface that micro-stutters under something else's
  load.

## Gesture ownership on a phone

`dna/06-interaction.md` applies literally and bites harder here, because the OS is also a claimant.
In Dynamic Bubble the panel changes screens only by the bottom nav or the number row, **never by a
swipe**, because full-width sliders own the horizontal stroke. Decide who owns each axis before
adding the second thing that wants it.

## Build

```
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.11"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
