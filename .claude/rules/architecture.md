---
paths:
  - "app/src/main/java/**"
  - "app/src/main/assets/**"
---

# Architecture

## The split

Kotlin owns everything only the system can answer: windows, permissions, notifications, media sessions, the camera's torch, the vibrator, the blur. The WebViews own everything visual — layout, state names, gestures, timing. Nothing draws on both sides of that line. A new indicator is a watcher in Kotlin plus a face in the page, never a Kotlin view.

`BubbleService` is an `AccessibilityService` and the single host: it holds the bubble's window, forwards every watcher's payload into `pill.html`, and exposes the bridge the page calls back on. Its companion is the one door the watchers knock on (`deliverNotification`, `deliverMedia`, `deliverTorch`, …) — watchers never reach a window themselves.

## Watchers

One file per source of truth, each pushing a JSON payload or `null`:

| File | Source |
|---|---|
| `IslandNotificationListener` | posted notifications, and the shade's full list |
| `MediaControl` | `MediaSessionManager` — the playing song |
| `TimerWatch` | the clock app's ongoing notification |
| `CallWatch` | an ongoing call notification |
| `TorchWatch` | `CameraManager.registerTorchCallback`, and setting strength |
| `BatteryWatch`, `MicrophoneAccess` | charge, sensor privacy |

A watcher decides *what is true*, never how it looks. Colours and names live in `AppStyles`; the payload carries them as data so the page never learns app names.

## Windows

**One window draws, and it draws everything.** Bubbles have to merge, merging is one SVG goo layer, and a goo layer reaches exactly as far as the surface it is drawn on — so a bubble in a second window is a bubble that can never be liquid. That single fact decides the window model: a full-screen **canvas** window holds the one WebView, and every bubble, satellite, dot and panel is drawn in it. A new bubble never gets a window of its own.

The canvas is `FLAG_NOT_TOUCHABLE`. It has to be: it spans the whole status bar, and every pixel a touchable window covers is a pixel the notification-shade swipe cannot start on — a full-width touchable overlay makes the top of the screen dead. Untouchable, it draws over the bar and the swipe passes straight through it.

Touch therefore arrives from somewhere else: small transparent **touch-proxy** windows, placed over whatever is interactive at that moment — the resting bubble, the Now pill, an open panel's box — and nothing else. A proxy carries no content. It reads the raw `MotionEvent` and forwards the coordinates into the page over the bridge, and the page does its own hit-testing in its own coordinates. The page owns which proxies exist and where they are, because the page is the only side that knows what it is currently drawing.

The obvious alternative does not exist for us: `TOUCHABLE_INSETS_REGION`, which would make one window touchable only where the shapes are, lives on `ViewTreeObserver.OnComputeInternalInsetsListener` — a hidden API, blocked for an app that is not SystemUI. It is not worth another attempt.

Every overlay window here follows the same shape:

- `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_NO_LIMITS`, `FLAG_LAYOUT_IN_SCREEN`, `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`, and insets consumed — without those it is pushed below the cutout and can never stand in the status bar.
- `FLAG_WATCH_OUTSIDE_TOUCH` on the proxies, because a tap elsewhere has to close an open panel and a proxy is far too small to hear it from the DOM. `ACTION_OUTSIDE` reports the touch without consuming it, so whatever was really tapped still gets the tap.
- `windowAnimations = 0` and `setCanPlayMoveAnimation(false)`: the page owns the motion, and a window-manager animation on top of it is a second, wrong one.
- Nothing visible, nothing touchable — an invisible window still swallows the shade swipe, so alpha 0 goes together with `FLAG_NOT_TOUCHABLE`.
- A fixed-size WebView stage clipped by the window. Never resize the WebView: resizing reallocates its surface, which reads as the interface blinking out for a frame.

The canvas is the **whole screen**, not the status bar. It has to be: the lock screen carries a bubble of its own at the bottom, and a bubble is only liquid with what shares its surface — so the one page reaches from the cutout to the thumb. It is untouchable and transparent where nothing is drawn, so the extra room costs the surface and nothing else.

There are four windows and no more: the canvas, and one proxy per bubble that can be touched — over the main bubble, over the Now bubble, and over the lock screen bubble. One proxy each rather than one wide one, because the gaps between them are most of the status bar and that has to stay somewhere the shade swipe can start. A proxy is no longer always at the top of the screen either: `forwardTouch` adds the proxy window's own `y` to the point it reports, or a touch on the lock screen bubble arrives in the page as a touch on the status bar.

## The bridge

The page is served from `https://appassets.androidplatform.net/` rather than `file:///android_asset/`, over a `shouldInterceptRequest` in `AssetOrigin` that reads straight out of `assets/`. It is not about security: Chromium gives every `file://` document its own opaque origin and refuses the module fetches an import graph is made of, so a page loaded from `file://` can carry inline script and nothing else — which is the whole reason `pill.html` was one file for as long as it was. The domain resolves nowhere, so a path the interceptor does not answer fails instead of reaching a network. `panel.html` stays on `file://`: it is one page with no imports, and it has nothing to gain from moving.

Page-to-host calls are `@JavascriptInterface` methods on an inner `Bridge`; host-to-page calls are `window.on…` / `window.set…` functions pushed as script. Scripts sent before the page says `ready()` queue and replay in order.

The page decides *when*, because it is the only side that knows where its own animation has got to. Do not re-derive an animation's timing on the Kotlin side. Sequencing between two bubbles is not a message at all any more: the Now bubble telling the main bubble that the flying drop has cleared it used to be a relay through the host, and on one canvas it is one page calling its own function.

Two things about touch follow from there being more than one proxy. A forwarded event carries **which proxy heard it**, because a grown panel is drawn across most of the bar and stands above the Now bubble in the DOM — without it, every tap and hold aimed at a bubble under an open panel is answered by the panel. And `ACTION_OUTSIDE` is a touch outside *one window*, not outside the interface: every proxy but the one under the finger reports a tap on an open panel as an outside tap, so the point travels with it and the page decides whether it landed on something of ours. Closing on it blindly shut the panel on the way down and the tap's own click then re-opened whatever had taken its place.

Touch is the one thing that travels the other way. A touch-proxy window has no content and makes no decisions: it forwards the raw event stream and the page decides what was touched and what the gesture means. Never put hit-testing in Kotlin — the page is the only side that knows where anything is.

## The page

`pill.html` is markup and the SVG filter definitions. Styles are in `pill.css` beside `dna.css`; logic is ES modules under `assets/js/`, loaded with one `<script type="module" src="js/main.js">` and split by concern rather than by state:

| Module | What it owns |
|---|---|
| `state.js` | the page's handles (`pill`, `faces`, `root`), the `bridge` stub, every shared constant, and `shared` |
| `motion.js` | the rubber band, `toy`/`untoy`, and every gesture on the main bubble |
| `liquid.js` | the mirror, the goo, the blur frames, `stirLiquid`, `catchInto` |
| `row.js` | layout, satellites, dots, the swap, window sizing, the mod hand-over |
| `now.js`, `lock.js` | the Now bubble and the lock screen's |
| `mods/*.js` | one file per mod: `media`, `timer`, `call`, `battery`, `notification` |
| `tabs.js` | the Tabs that are nobody's mod: history, quick settings |
| `bridge.js` | the `window.on…` / `set…` entry points and forwarded touch |
| `main.js` | imports every module for its side effects, then `bridge.ready()` |

Two things hold this together, and both are load-bearing:

**`shared` is the state several modules write.** An imported binding can be read anywhere and assigned only where it was declared, so eighteen module-scope `let`s would have become eighteen setters. They are one exported object instead, and a write stays where the decision is made. A value only one module writes is that module's own and does not belong in it.

**`state.js` imports nothing.** The modules are a cycle — the row calls into the mods, the mods paint the row — which ES modules allow as long as nothing reads another module's binding *while modules are still evaluating*. A module with no imports of its own is always evaluated first, so every module's top-level lines may reach `state.js` and their own file and nothing else. `bridge.ready()` sits in `main.js` for the same reason: the host replays everything it has queued the moment it is called, and the page has to be whole before that. A function reference read at the top of a file (`room: timerWindow`) is exactly the read that breaks this, and it is written `room: () => timerWindow()`.

One `Bubble` type carries what [bubbles.md](bubbles.md) says every bubble has; Main, Satellite, Now and Double are instances of it that differ in where they stand, which states they may enter, and what they carry — **not built yet**. There is no factory, no event bus and no configuration layer around it — one type is the whole abstraction.

## Vendor reflection

`SamsungBlur` reaches `android.view.SemBlurInfo` by reflection because AOSP's `FLAG_BLUR_BEHIND` is dead on this device. Anything else of that kind belongs in its own small file with the same shape: reflect, fail quietly, and leave the interface correct without it.

## Stacking

Windows of one type from one app stack in the order they were added, and an accessibility overlay has no z to set. That used to decide which bubble stood in front of which, and reordering meant removing a window and adding it back — the torch panel did exactly that on the way open and gave it back on the way closed. None of it survives one canvas: everything is drawn in one page, so one bubble in front of another is a `z-index`, and the windows are only ever added in the order canvas, proxy, proxy.

The Now bubble never moves off its spot, and that is the point: One UI's own flashlight chip is directly underneath it, so a pill that retreats to make room uncovers the thing it exists to cover. It was briefly built the other way — flying home whenever the bubble grew — and Samsung's blue chip appeared every time. The bubble is the side that yields instead — the whole row stands aside rather than the bubble shaving itself: while the Now bubble is out, the row is pushed right until the bubble's left edge is at the punch hole, the Now bubble takes the bar it gave up, and the two rest a few pixels apart so the skin necks between them. A state grown wide enough simply passes in front. Its resting width is measured off One UI's chip rather than off the bubble for the same reason: matched to the bubble it was too narrow, and Samsung's pill stuck out past its right edge.
