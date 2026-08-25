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

A window is added only when the thing it carries cannot live inside an existing one. Now State (`NowState`) is the second window because the bubble's window is deliberately no wider than the bubble and cannot be stretched across the bar to reach the clock. It started as the torch's own pill and nothing else; it is named for what it is becoming, not for the one thing it currently shows — the same window is where any future state that belongs out by the clock, rather than in the bubble, will stand.

Every overlay window here follows the same shape:

- `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_NO_LIMITS`, `FLAG_LAYOUT_IN_SCREEN`, `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`, and insets consumed — without those it is pushed below the cutout and can never stand in the status bar.
- `FLAG_WATCH_OUTSIDE_TOUCH`, because a tap elsewhere has to close an open panel and the window is too small to hear it from the DOM. `ACTION_OUTSIDE` reports the touch without consuming it, so whatever was really tapped still gets the tap.
- `windowAnimations = 0` and `setCanPlayMoveAnimation(false)`: the page owns the motion, and a window-manager animation on top of it is a second, wrong one.
- Nothing visible, nothing touchable — an invisible window still swallows the shade swipe, so alpha 0 goes together with `FLAG_NOT_TOUCHABLE`.
- A fixed-size WebView stage clipped by the resizable window. Never resize the WebView.

## The bridge

Page-to-host calls are `@JavascriptInterface` methods on an inner `Bridge`; host-to-page calls are `window.on…` / `window.set…` functions pushed as script. Scripts sent before the page says `ready()` queue and replay in order.

The page decides *when*, because it is the only side that knows where its own animation has got to. Cross-window sequencing goes through the host as a relay: the torch page tells the host the flying pill has cleared the bubble, and the host tells the bubble to pull its width in. Do not re-derive that timing on the Kotlin side.

## Vendor reflection

`SamsungBlur` reaches `android.view.SemBlurInfo` by reflection because AOSP's `FLAG_BLUR_BEHIND` is dead on this device. Anything else of that kind belongs in its own small file with the same shape: reflect, fail quietly, and leave the interface correct without it.

## Stacking

Two windows of one type from one app stack in the order they were added, and an accessibility overlay has no z to set. The torch's window goes up before the bubble's, which is the whole of why it sits behind: the pill is born inside the bubble at the punch hole and has to come out from under it rather than slide across its face. The only way to change that later is to be added again — `NowState.raise()` and `BubbleService.raiseBubble()` remove and re-add their own stage, which the torch panel does on the way open and gives back on the way closed, because the panel is wider than the strip of bar it stands on.

Now State never moves off its spot, and that is the point: One UI's own flashlight chip is directly underneath it, so a pill that retreats to make room uncovers the thing it exists to cover. It was briefly built the other way — flying home whenever the bubble grew — and Samsung's blue chip appeared every time. The bubble is the side that yields instead (`CHIP_TAX`), and a state grown wide enough simply passes in front, which is what being the later-added window already buys. Its resting width is measured off One UI's chip rather than off the bubble for the same reason: matched to the bubble it was too narrow, and Samsung's pill stuck out past its right edge.
