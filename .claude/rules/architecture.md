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
| `NowWatch` | a recording running, a transfer in flight — both read off ongoing notifications |
| `AlarmWatch` | an alarm ringing, told apart by its full-screen intent — recognised only so that nothing is drawn for it |
| `BatteryWatch`, `MicrophoneAccess` | charge, sensor privacy |
| `ConnectivityWatch` | the default network and its name, USB, tethering, and what is paired over bluetooth |

A watcher decides *what is true*, never how it looks. Colours and names live in `AppStyles`; the payload carries them as data so the page never learns app names.

**A watcher decides what is true *now*, and the platform is full of things that are merely still there.** A media session outlives the playing it was created for — an app leaves it in the active list, metadata intact, until its process dies — and so does an ongoing notification an app forgot to cancel. A watcher that reads existence as truth turns every one of those leftovers into a bubble opening out of nowhere, which is the worst failure this app has, because it happens while nobody is doing anything. So the test is never "is there one": it is a state the source itself declares, and where a source is genuinely ambiguous during its first seconds (a player is STATE_NONE while it connects) the watcher listens through them rather than believing them. Where the source's own state cannot tell them apart either — Spotify's session stays PAUSED for hours after the app is closed, which is exactly what a genuinely paused player looks like — the second reading is whether the *system* is still offering the thing anywhere else: a player nobody can reach any more has no notification and no card in the media panel.

## Windows

**One window draws, and it draws everything.** Bubbles have to merge, merging is one SVG goo layer, and a goo layer reaches exactly as far as the surface it is drawn on — so a bubble in a second window is a bubble that can never be liquid. That single fact decides the window model: a full-screen **canvas** window holds the one WebView, and every bubble, satellite, dot and panel is drawn in it. A new bubble never gets a window of its own.

The canvas is `FLAG_NOT_TOUCHABLE`. It has to be: it spans the whole status bar, and every pixel a touchable window covers is a pixel the notification-shade swipe cannot start on — a full-width touchable overlay makes the top of the screen dead. Untouchable, it draws over the bar and the swipe passes straight through it.

Touch therefore arrives from somewhere else: small transparent **touch-proxy** windows, placed over whatever is interactive at that moment — the resting bubble, the clock and whatever is standing in it, an open panel's box — and nothing else. A proxy carries no content. It reads the raw `MotionEvent` and forwards the coordinates into the page over the bridge, and the page does its own hit-testing in its own coordinates. The page owns which proxies exist and where they are, because the page is the only side that knows what it is currently drawing.

The obvious alternative does not exist for us: `TOUCHABLE_INSETS_REGION`, which would make one window touchable only where the shapes are, lives on `ViewTreeObserver.OnComputeInternalInsetsListener` — a hidden API, blocked for an app that is not SystemUI. It is not worth another attempt.

Every overlay window here follows the same shape:

- `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_NO_LIMITS`, `FLAG_LAYOUT_IN_SCREEN`, `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`, and insets consumed — without those it is pushed below the cutout and can never stand in the status bar.
- `FLAG_WATCH_OUTSIDE_TOUCH` on the proxies, because a tap elsewhere has to close an open panel and a proxy is far too small to hear it from the DOM. `ACTION_OUTSIDE` reports the touch without consuming it, so whatever was really tapped still gets the tap. Two things about it decide code rather than being trivia. **It arrives before the touched proxy's own down**: the dispatcher walks the windows front to back, and every proxy standing above the one being touched reports outside on the way past — so the page hears "a press happened somewhere else" before it hears the press, and anything keyed to `shared.isTouchDown` is reading a flag that has not been written yet (see [motion.md](motion.md), "One finger may change the bubble's state once"). And **the point is only real when the press is ours**: a touch in another app reports at `0, 0`, coordinates the system zeroes on the way out. That an extended bubble closes on it anyway is luck — `0, 0` is outside the bubble — not a reading of where the finger was.
- `windowAnimations = 0` and `setCanPlayMoveAnimation(false)`: the page owns the motion, and a window-manager animation on top of it is a second, wrong one.
- Nothing visible, nothing touchable — an invisible window still swallows the shade swipe, so alpha 0 goes together with `FLAG_NOT_TOUCHABLE`.
- A fixed-size WebView stage clipped by the window. Never resize the WebView: resizing reallocates its surface, which reads as the interface blinking out for a frame.

The canvas is the **whole screen**, not the status bar. It has to be: the lock screen carries a bubble of its own at the bottom, and a bubble is only liquid with what shares its surface — so the one page reaches from the cutout to the thumb. It is untouchable and transparent where nothing is drawn, so the extra room costs the surface and nothing else.

There are five windows and no more: the canvas, and one proxy per bubble that can be touched — over the main bubble, over the lock screen's Now bubble, over the Status bubble, and over the Clock. There was a sixth over a Now bubble on the bar; the mods it carried are shown in the Clock now, so the Clock's proxy is the one that grows to a mod's width and to the panel a mod opens into, and the window went with the bubble. The Clock's is the one that moves most: it answered no touch at all until it was given the hold that opens the clock app, and it is now the window that has to be the size of the digits, of a mod, or of an open panel in turn — measured off the box every time, because the page is the only side that knows which of the three it is drawing. The one that still answers nothing — the Double — costs no window, which is the rule working rather than an omission. One proxy each rather than one wide one, because the gaps between them are most of the status bar and that has to stay somewhere the shade swipe can start. A proxy is no longer always at the top of the screen either: `forwardTouch` adds the proxy window's own `y` to the point it reports, or a touch on the lock screen bubble arrives in the page as a touch on the status bar.

**One window exists to swallow a gesture rather than to answer one**, and it is off by default: the shade lock (`barLocked`). Hiding the real bar with `immersive.status` is a *reveal* — the swipe still brings it back — and nothing unprivileged can ask SystemUI to stop the gesture: `IStatusBarService.disable(DISABLE_EXPAND)` needs a signature permission and dies with the caller's binder token, and `cmd statusbar` exposes no subcommand for it. So the only mechanism left is standing on the pixels the gesture starts from: a full-width, `SHADE_STRIP`-tall touchable window that returns true and does nothing. It is the deliberate exception to the rule above it, which is why it is a switch and not a default, and every proxy is removed and re-added when it goes up — windows of one type stack in the order they were added, so a strip added last would eat the bubbles' own touches with the shade's. It goes through `applyVisibility()` like every other window here, and it did not: left standing while the interface is hidden it is an invisible bar across the top of a landscape screen, swallowing every touch aimed at whatever the phone is actually drawing there. A window that protects a bubble has no business taking touches at a moment the bubble is not on screen.

**A proxy comes down at the start of a collapse, not at the end of one.** Growing, the window has to be bigger before the bubble animates into it. Shrinking used to be the mirror of that — the bubble finishes first, then the window follows — and that reasoning is about the *drawing*, which has not lived in this window for a long time: the canvas spans the bar, is never resized, and `setWindowBounds` moves the touch proxy alone. So a proxy left at the open size for the length of a collapse is an invisible window most of the bar wide, and every pixel of it is a pixel the shade swipe and the app underneath cannot have — which is exactly "I closed the bubble and the scroll I started was eaten by it". `setSize` applies the smaller window immediately and the bubble animates home inside a window already the size it will land at, clipped by nothing because it is not drawn there. The one exception is a live finger: pulling the surface out from under a touch makes the system cancel the gesture, so `shared.isTouchDown` — written by the bridge for the whole of any proxy touch, whichever bubble it turned out to belong to — keeps the old deferral and the restore timer is what puts the window back. The Status bubble obeyed this rule and broke it anyway, which is the trap worth writing down: it called `fitStatusProxy()` at the top of the collapse exactly as the rule says, and that function places the window from `getBoundingClientRect()` — which on the frame a transition *starts* still reads the size being left. So the small window it thought it was asking for was the open panel, held for the whole of the open panel's 300ms collapse, and the swipe was eaten and a pull inside it read as `openStatusPanel()` on a bubble that was already closed. **Coming down at the start of a collapse means handing the room back, not re-measuring** — there is no box to measure, because the box the bubble is going to is not the box it is in. So `panel-closing` makes both `fitStatusProxy` and `statusHolds` answer nothing for the length of the close, and the window is taken again at the settle. `statusHolds` matters as much as the window: the row's own proxy covers the bar too, and `bridge.js` asks the bubble whether a touch the row heard belongs to it — off the same rect.

**A proxy's grace margin is sideways and upward, never downward.** A touch proxy is deliberately larger than the bubble under it — `GRAB + BLEED` either side, `topGrab()` above — because a 76×26 pill is smaller than a fingertip. Every point in that margin routes to the bubble (`pillTarget` falls back to the pill for anything not over a control), so room below the bubble is a live "tap the bubble" strip standing over whatever the app underneath drew there: it opened the bubble on a tap aimed at the first input field of an app, and it reopened a bubble that was in the middle of closing. Below the bubble the window now ends at `GRAB` and nothing more — a grown state at its own bottom edge (`heightDp + topGrab()`), the compact bubble `GRAB` past its. The pull-down gesture does not need the room: the pointer is captured at the down, so the drag continues outside the window, and the pull starts on the pill itself.

**A proxy is placed from a measurement, never from a constant that agrees with the CSS by hand.** Every `set*Proxy` call now carries a `getBoundingClientRect()` of the bubble it stands over, because the page is the only side that knows where anything is and the layout is increasingly not a sum the script can do: the lock screen's Now bubble is placed by its own flex column, so the three constants that used to derive its window (an inset, a bottom, a height) were three chances to disagree with the stylesheet — and a proxy that disagrees is a bubble that is drawn and cannot be touched. Where a box is animated by hand rather than by a transition, the window is placed again when that animation *finishes*, since a box measured mid-growth still measures as the size it is leaving.

**Whether a proxy may be touched at all is the host's answer, not the page's.** The page says what is standing where; only the host knows the interface is hidden — landscape, a fullscreen app, a dark screen — and the page is never told. Both halves meet in `proxyFlags()`, which every proxy setter routes through, and that is the whole fix for a bubble answering taps on a screen it is not drawn on: hiding took the touch flag off all three windows, and then the next repaint from a page that had no idea handed it straight back.

## The bridge

The page is served from `https://appassets.androidplatform.net/` rather than `file:///android_asset/`, over a `shouldInterceptRequest` in `AssetOrigin` that reads straight out of `assets/`. It is not about security: Chromium gives every `file://` document its own opaque origin and refuses the module fetches an import graph is made of, so a page loaded from `file://` can carry inline script and nothing else — which is the whole reason `pill.html` was one file for as long as it was. The domain resolves nowhere, so a path the interceptor does not answer fails instead of reaching a network. `panel.html` stays on `file://`: it is one page with no imports, and it has nothing to gain from moving.

A **switch** is set and the whole panel is read back, because a command the shell refused must not be left standing as a switch that says it worked. **What was asked for outranks that read until the phone agrees with it.** `svc wifi enable` and every command like it hand the ask to a system service and return before it has landed, so the read taken straight after still describes the switch as it was a moment ago — which is the switch coming on, being knocked back off by its own confirmation, and coming on again at the next read. `toggleIntents` holds the asked-for value over the read, drops it the moment the read agrees, and asks again every `TOGGLE_RECHECK_MILLIS` for as long as one is outstanding, because nothing else would. Past `TOGGLE_SETTLE_MILLIS` the phone's answer wins instead, which is what keeps a refused command from standing as a switch that lies. A name the read does not carry at all — the hotspot and the recorder are asked for by tapping a system tile — is dropped at once rather than held, since nothing could ever confirm it and writing it into the payload would tell the page the panel reports it. A **level** is not: `setLevel` writes and answers nothing, since it is called per frame under a moving thumb and a round-trip on each of those is a queue the hand outruns. The page owns a value while it is being dragged, and sends the value under the finger once more on release, because the step gate that keeps the frames sparse otherwise swallows the last few percent.

**A level does not go through the shell.** `settings put` and `cmd media_session` are not commands, they are Java programs: the shell forks, forks an `app_process` and starts a runtime to write one integer, which is around a fifth of a second per frame — so a drag arrived as a run of late jumps however tightly the calls were queued, and no amount of ordering was ever going to be the fix. Brightness is `Settings.System.putInt` and volume is `AudioManager.setStreamVolume`, both in-process and both immediate. Brightness needs WRITE_SETTINGS, which is an app op rather than a permission a dialog can grant, so `SystemToggles.attach` has Shizuku set it once when the shell connects — `ShizukuShell.onReady`, because binding is a callback and not the return of `bind()`. The shell stays as the fallback for a phone where that op never took. **A switch is still a shell command**, because most of them are `svc` and `cmd` calls with no API an unprivileged app can reach.

**Every switch and level runs on one worker thread, in the order it was asked.** A thread each meant a run of fast taps, or one drag, put a dozen concurrent binder calls into the Shizuku shell and the one that landed last won — so a switch settled on whichever answer the scheduler picked. The read-back is only pushed by the last piece of queued work: an intermediate one costs a dozen shell commands to describe a state still half-applied, and painting it is what made a fast-tapped switch flicker between the two answers. A level does not queue at all; it leaves its value in a slot and the first queued run takes the newest one, so the shell is never asked for a frame the hand has already left behind.

Page-to-host calls are `@JavascriptInterface` methods on an inner `Bridge`; host-to-page calls are `window.on…` / `window.set…` functions pushed as script. Scripts sent before the page says `ready()` queue and replay in order.

The page decides *when*, because it is the only side that knows where its own animation has got to. Do not re-derive an animation's timing on the Kotlin side. Sequencing between two bubbles is not a message at all any more: the clock telling the row to stand aside for a mod used to be a relay through the host, and on one canvas it is one page calling its own function.

Two things about touch follow from there being more than one proxy. A forwarded event carries **which proxy heard it**, because a grown panel is drawn across most of the bar and stands above the other bubbles in the DOM — without it, every tap and hold aimed at a bubble under an open panel is answered by the panel. And `ACTION_OUTSIDE` is a touch outside *one window*, not outside the interface: every proxy but the one under the finger reports a tap on an open panel as an outside tap, so the point travels with it and the page decides whether it landed on something of ours. Closing on it blindly shut the panel on the way down and the tap's own click then re-opened whatever had taken its place.

Touch is the one thing that travels the other way. A touch-proxy window has no content and makes no decisions: it forwards the raw event stream and the page decides what was touched and what the gesture means. Never put hit-testing in Kotlin — the page is the only side that knows where anything is.

## The control panel

`panel.html` is three screens and stays one file — it has no imports and nothing to gain from the
module split `pill.html` needed. Three rules decide its shape and are the ones to keep:

- **A module is one kind of thing, never one page of things.** Both the settings and the debug
  states are built from a table in the script — `FIELDS` and `STATES` — where each entry names its
  group, and a module is generated per group in the order the table lists them. Adding a setting is
  a line in a table; a group appearing is that line naming a group nothing else does. A screen with
  one module called after the screen is the thing this replaced. A group may name a `screen` other
  than its own and be built there instead — the colour channels are the one that does, because they
  are a debug control wearing a settings control's shape.
- **A setting with named answers is buttons, never a slider.** A slider makes the reader translate
  a position back into a word, and where there are two answers a position means nothing at all — so
  a field carrying `labels` is built as one button per answer with a marker sliding between them,
  and only a genuine number gets the drag-only slider beside it.
- **On the System screen the state and the control are one button.** A row saying "blocked" beside
  a button saying "Grant" is two words for one fact; the button wears the state, the press changes
  it, and the change is announced by the same accent bar that writes every heading — a grant with a
  shell UID behind it lands a second after a different app was on screen, so nothing about it is
  where the finger was. A row marked `*` is a system setting rather than a permission of ours and
  reads as broken without the mark on a phone with no Shizuku.

**The System screen can stop the bubbles without stopping the app.** Testing leaves the overlay in
states no repaint gets out of, and the only fix was revoking accessibility in the system settings
and granting it again. `Panel.stopBubbles()` is that in one press: `BubbleService.disableSelf()`,
which is what draws every window, and which takes this component out of
`enabled_accessibility_services` on its way out — so the Accessibility button on the same screen is
the way back and cannot end up adding a second copy of it. It kills the service, never the process:
the panel is the thing being worked and has to survive the press.

**There is a second activity, and it is not a screen.** `StatusPanelActivity` is a launcher entry
whose whole body is `BubbleService.toggleStatusPanel()` and `finish()`. It exists because One UI's
diagonal swipe from the right opens SystemUI's own quick settings and nothing unprivileged can point
that gesture anywhere else — but every gesture host on this phone can *open an app*, so the panel is
given an app to be. It carries `LAUNCHER` because that category is exactly what those pickers
enumerate; the second home-screen icon is the price of being pickable. Translucent so the app behind
is never stopped, `noHistory` and `excludeFromRecents` so it leaves nothing to swipe away, its own
`taskAffinity` so opening it never drags the control panel's task forward, and both window
animations killed, because the panel's own arrival is the animation. With the bubbles not running it
does nothing: the service draws every window, and an activity of ours cannot start it.

The host says *toggle* rather than *open*, and that is the contract rather than a convenience — a
gesture is one thing the hand does, so the same gesture has to put the panel away. `window.onToggleStatusPanel`
is the whole page-side of it.

**A watcher decides what is true; `AppStyles` decides what it looks like — including for apps
nobody named.** An app with no `Style` of its own used to be white, which is honest and is also
every unnamed app on the phone wearing one identity. Its icon already carries the answer, so
`IconColour` reads it: the launcher icon drawn into a 24×24 bitmap, greys and near-blacks and the
transparent margin thrown away before the count, what is left bucketed by *hue* rather than by
colour — an icon is a gradient far more often than it is a flat fill, and thirty shades of one blue
have to count as one blue or they lose to a flat accent covering a tenth of the area — and each
pixel weighted by how colourful it is, so a pale wash over the whole icon cannot outvote the mark in
the middle of it. One read per package for the life of the process. It needs a `PackageManager`,
which none of the eight `AppStyles.of()` callers has to spare, so the object is handed one once
(`learnFrom`, from both services' connect) rather than the signature growing a parameter everywhere.

**A permission grants itself where the shell can grant it.** With Shizuku bound, accessibility is
`settings put secure enabled_accessibility_services` (read and *appended to* — that key holds every
accessibility service on the phone), the listener is `cmd notification allow_listener`, and
`POST_NOTIFICATIONS` is `pm grant`; the settings deep link is the fallback for the one run before
Shizuku exists. `HeadsUp` is no longer asked about at all — see the README.

## The page

**`window.innerWidth` is not the screen, and on this phone it is out by 44.** The canvas window is
exactly the display — 1080×2340 at a device ratio of 3, so 360×780 to the page — and inside it the
WebView reports `innerWidth` 404 and `innerHeight` 877: the layout viewport it hands out is larger
than the surface it is drawn on. Anything sized or centred off it is wrong by half that, which is
invisible while it is only deciding where a drop starts its flight and very visible the moment
something is sized to fill the screen — the full-screen Status panel came out 384 wide on a 360-wide
screen and overhung both edges. `document.documentElement.clientWidth` / `clientHeight` is the
surface. `status.js` asks through `screenWidth()` / `screenHeight()` for this reason.

It is still live elsewhere and deliberately left alone for now, because several of these place the
row and the user's `horizontalOffset` may already be calibrated against them: `row.js` (the punch
hole's centre and so the main bubble's resting place), `clock.js` and `now.js` (a flight home, a
panel's centre), and `liquid.js` (how far a bubble is from the right wall for `edgeMerge`). Fixing
them moves every bubble on the bar and wants its own pass on the phone.

Every lit thing on the bar reads its colour through the one `--section-color` token in `dna.css`, and that token is a **setting**: `accentColor` holds it as a single `0xRRGGBB` int (the store holds ints), the dashboard picks it with a native colour input rather than a control of its own, and `pushAppearance` repoints the token over `setAccent`. It stands on its own at the top right of that screen rather than in a module, because it is the one setting every other one is read against. It is not the `red`/`green`/`blue` sliders, which are the pill's *background* and live on the Debug screen — a different thing, and not one to be reached for by accident.

`pill.html` is markup and the SVG filter definitions. Styles are in `pill.css` beside `dna.css`; logic is ES modules under `assets/js/`, loaded with one `<script type="module" src="js/main.js">` and split by concern rather than by state:

| Module | What it owns |
|---|---|
| `state.js` | the page's handles (`pill`, `faces`, `root`), the `bridge` stub, every shared constant, and `shared` |
| `motion.js` | the rubber band, `toy`/`untoy`, and every gesture on the main bubble |
| `liquid.js` | the mirror, the goo, the blur frames, `stirLiquid`, `catchInto` |
| `row.js` | layout, satellites, dots, the swap, window sizing, the mod hand-over |
| `now.js`, `lock.js`, `status.js`, `clock.js` | the Now mods and the bubble they open into, the lock screen's bubble, the one at the right end of the bar, and the time at the left — `clock.js` owns the box and the hand, `now.js` owns what is standing in it |
| `mods/*.js` | one file per mod: `media`, `timer`, `call`, `notification` |
| `double.js` | the second bubble at the hole, and the announcements it carries |
| `tabs.js` | the Tabs that are nobody's mod. History, now that the quick settings have gone to the Status bubble's own panel |
| `labels.js` | which readings have run out of room, for every bubble at once — the one answer the masks and the scroll both hang off |
| `bridge.js` | the `window.on…` / `set…` entry points and forwarded touch |
| `main.js` | imports every module for its side effects, then `bridge.ready()` |

Two things hold this together, and both are load-bearing:

**`shared` is the state several modules write.** An imported binding can be read anywhere and assigned only where it was declared, so eighteen module-scope `let`s would have become eighteen setters. They are one exported object instead, and a write stays where the decision is made. A value only one module writes is that module's own and does not belong in it.

**`state.js` imports nothing.** The modules are a cycle — the row calls into the mods, the mods paint the row — which ES modules allow as long as nothing reads another module's binding *while modules are still evaluating*. A module with no imports of its own is always evaluated first, so every module's top-level lines may reach `state.js` and their own file and nothing else. `bridge.ready()` sits in `main.js` for the same reason: the host replays everything it has queued the moment it is called, and the page has to be whole before that. A function reference read at the top of a file (`room: timerWindow`) is exactly the read that breaks this, and it is written `room: () => timerWindow()`.

One `Bubble` type carries what [bubbles.md](bubbles.md) says every bubble has; Main, Satellite, Now and Double are instances of it that differ in where they stand, which states they may enter, and what they carry — **not built yet**. There is no factory, no event bus and no configuration layer around it — one type is the whole abstraction.

## What the blur cannot do

A pane blurs what is behind it *in the window*, and everything this project draws is drawn by the WebView above the panes. So a bubble's glass can never contain our own pixels: two of our shapes overlapping means the one on top is glass over the screen, not glass over the shape underneath it.

Moving a pane above the WebView would smear our content rather than show it through glass, and splitting the page across two WebViews to put one of them under the panes would trade every merge in the project for one blur — a goo layer reaches exactly as far as its own surface.

The case that is actually missing is *our content over our content*, and that one never needed the host: `backdrop-filter` inside the page blurs the page behind the element. The host pane goes on blurring the screen; the CSS blurs us. Two blurs of two different things, at two different levels of the stack, composing for free. Nothing overlaps today — the row's bubbles neck rather than stack — so it is written down rather than built, and the first shape that genuinely lands on another one should carry it.

## Vendor reflection

`SamsungBlur` reaches `android.view.SemBlurInfo` by reflection because AOSP's `FLAG_BLUR_BEHIND` is dead on this device. Anything else of that kind belongs in its own small file with the same shape: reflect, fail quietly, and leave the interface correct without it.

## Stacking

Windows of one type from one app stack in the order they were added, and an accessibility overlay has no z to set. That used to decide which bubble stood in front of which, and reordering meant removing a window and adding it back — the torch panel did exactly that on the way open and gave it back on the way closed. None of it survives one canvas: everything is drawn in one page, so one bubble in front of another is a `z-index`, and the windows are only ever added in the order canvas, proxy, proxy.

The bubble at the left end of the bar never moves off its spot, and that is the point: One UI's own clock and its flashlight chip are directly underneath it, so a pill that retreats to make room uncovers the thing it exists to cover. It was briefly built the other way — flying home whenever the bubble grew — and Samsung's chip appeared every time. The row is the side that yields instead — it stands aside rather than the bubble shaving itself: while a Now mod is standing in the clock, the row is pushed right until its left edge is at the punch hole, the clock takes the bar it gave up, and the two rest a few pixels apart so the skin necks between them. A state grown wide enough simply passes in front.

## Nothing runs behind a hidden stage

`isHidden()` — landscape, a fullscreen app, a dark screen — used to be a Kotlin-side fact the page was never told, and the page is where all the repeating work is. So an overnight screen-off ran the clock's per-second tick, the media ticker twice a second, the liquid mirror's per-frame `getBoundingClientRect()` sweep and every always-on CSS animation against a surface nobody could see, at the high frame rate `wakeFrames()` asks for and never gives back. That is what a day's battery page reporting four hours of CPU across eleven hours of background actually was.

`applyVisibility()` now says so, three ways at once, and all three are needed because each stops a different loop: the stage is `View.INVISIBLE` rather than `alpha = 0f`, since alpha leaves the WebView visible to Chromium and its animations running; the frame rate drops to `REQUESTED_FRAME_RATE_CATEGORY_LOW` and `wakeFrames()` refuses to raise it while hidden; and `window.setStageHidden` puts `shared.isStageHidden` in front of every repeating thing the page does — `stirLiquid` returns without starting the mirror, the clock tick reschedules without painting, and the media, timer and call tickers keep counting and stop drawing. The reveal repaints what went stale (`wakeClock`, `fitLabels`, one stir) rather than the page trying to stay current in the dark.

**A watcher publishes a change, never an event.** `ACTION_BATTERY_CHANGED` is broadcast on temperature and voltage as well as on charge, and `onCapabilitiesChanged` fires on every signal-strength wobble — both land every few seconds on a phone doing nothing, and each one was an `evaluateJavascript` into the page, a `wakeFrames()` and a repaint of a reading that had not moved. `BatteryWatch` compares the percent and the cable, `ConnectivityWatch` compares the whole payload's spelling, and neither pushes twice. Any new watcher on a chatty broadcast owes the same guard: the cost is not the broadcast, it is waking the renderer behind it.
