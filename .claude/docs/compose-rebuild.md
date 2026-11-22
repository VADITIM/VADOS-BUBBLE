# Compose rebuild

The `compose` branch replaces the WebView canvas (`pill.html`, `pill.css`, `js/`) with Jetpack Compose. `main` keeps the WebView app and its releases until this branch reaches parity; then it merges. Each push to `compose` builds in CI and attaches the APK as a workflow artifact (no release). Same application id, so installing it replaces the WebView build and keeps its grants.

## What changed in the foundation

| WebView | Compose |
|---|---|
| `pill.html` in one full-screen untouchable WebView | `IslandStage` in one full-screen untouchable `ComposeView` (`BubbleService`, `OverlayOwner` gives it a lifecycle) |
| SVG goo filter (`feGaussianBlur` + `feColorMatrix`) | `LiquidLayer`: an AGSL shader drawing every registered blob as one smooth-min signed distance field, fill and hairline border in the same pass (`bubble/Liquid.kt`) |
| `window.on…` JSON pushes | watchers still build JSON; `BubbleService.deliver*` decodes it once into typed models (`bubble/Models.kt`) held as snapshot state on `Island` |
| `@JavascriptInterface` bridge | `HostActions`, implemented by `BubbleService` |
| proxies forward coordinates, the page hit-tests | proxies forward the raw `MotionEvent` into the `ComposeView`; Compose hit-tests. Each bubble's touch pad stands at its *target* box plus the grace margin, and that same rect is the proxy (`Island.regions`) |
| `dna.css` tokens | `vas/`: `Palette`, `Faces`, `Curves`/`Durations` (the CSS cubic-beziers), `SquircleShape`, `Haptics`, `pressable` |
| blur panes from `setBlurFrame` strings | panes follow the liquid's blobs once per frame (`syncPanes`) |

Rules carried over unchanged: one surface draws everything; proxies come down at the start of a collapse except under a live finger (`Proxy.place` defers until the lift); grace is sideways and upward; untouchable while hidden; the shade swipe keeps every pixel no window needs.

## Parity ledger

Ported (unverified on the phone):

- [ ] Main bubble states: idle with unread count, closed mods (media, timer, call, trip), satellites, dots, sideways swap and rotation, hold to open the mod's app, pull down for notifications, swipe up to dismiss, rubber band
- [ ] Alert with dwell, conversation appending, keyword/time/money/link marking, picture state, gone grace. Stands a grown pad under Clock and Status, sized off its content (88 to 60% of the screen), no accent outline, sender right-aligned
- [ ] Alert band (`Region.Alert`, `AlertBand`): a 96-wide strip at the right edge across the middle 30% while an Alert stands; a pull down there or on the card past 22 focuses it at the centre (`Size.AlertCentre`, no dwell, frosted, whole-screen band), a pull up dismisses, a tap off the card is replayed to the app underneath with `dispatchGesture`
- [ ] Dark arrival (`DarkAlertView`): a notification on a dark screen wakes it at 0.07 brightness over black with the app name swept in and title and text typed; a tap on the text opens it, elsewhere lights the screen; the dwell fades it and locks the screen again (the debug simulation lights it instead)
- [ ] Notifications menu shield (`Region.Shield`): while the menu is open a full-screen proxy eats every touch beside it and closes the menu. A deliberate exception to "never widen a touchable window", asked for by the user
- [ ] Player (cover swipe for next/previous, tap play/pause, wave scrub, speed for talkers), timer panel, trip panel
- [ ] Notifications menu: grouping, reveal labels, typed bodies, swipe to dismiss, scroll band, delete-all bubble
- [ ] Clock with hold to open the clock app; torch step panel; recording face and panel
- [ ] Status bubble readings (USB, transfer, modus, link, battery, paired charge) and the charge announcement. Battery drain dropped from about 20% to 0.5% after leaving the WebView (user, 2026-11-22)
- [ ] Liquid merge between every bubble; Samsung blur behind each blob
- [ ] Dashboard (`bubble/Dashboard.kt` state, `bubble/DashboardView.kt`): opened by a tap on the Status bubble, a pull down past 34 or a pull stolen by the shade past 12, and by the panel broadcast; closed by a tap on the Status bubble, a swipe up anywhere (24 up, under 40 across), a tap on bare ground, the stage hiding, or Main extending. Status hold opens the settings of what it shows. Clock and Status grow into the two top corners (Clock keeps its time and only hands over the seconds with a turning pop and types the date in; Status shows settings and power, with the same hairline whatever mode is on), Main rises into the screen's top edge. Frosted scrim through one full-screen blur pane (`syncScrim`), stepped in twelfths. Stats (weather, storage/RAM, battery cell with hundredths read every second and charge bubbles, transfer strip or data today, USB), connection grid with spinning ring readings and searching dots, brightness/volume/call bars with the eased value curve and the merge on hold (brightness clears the panel; the far bar stays composed while hidden so it slides back out from behind the held one), endless knob row with fling, the mods clear/restore switch. Pop entrance ranked from the panel's centre outward, collapse leave, fast entrance preference, 12dp grace to the nearest control, toggle read-backs held while the panel moves, asked-for switches and levels held over the read until the phone agrees

- **Now bubble** (unverified): the flight is drawn against the rest box it was aimed at (`NowStand` frozen for a leave, sizes snapped while flying), and the mod changes hands when 80% of the drop stands inside Main (`CATCH_ENTERED`), not at the shot's end. `NowBubble.kt`/`NowView.kt`/`Sling.kt` port `js/lock.js` and `js/sling.js`. On the keyguard and in the Dashboard the media mod leaves Main (`isStolen`) for its own bubble, slung out of Main as a ball and opened in place; it flies home on unlock or when the panel closes, thrown along the flick that closed it, and Main bumps as it melts in. Tap opens/closes it (lock 124↔546, panel compact 190×44↔124), hold opens the app, sideways skips, down pauses when big, the wave scrubs. In the panel the lower divider stands a gap above it.

Not ported yet, in order:

1. Dashboard remainder: the stats-section channel (swipe down into notifications), the slung Alert, the stat rise after the pop, the ring's found burst, the knob row shrinking under an expanded Now bubble (`html.now-open`)
2. The slung dashboard alert; the alert zone's hint glow (`ALERT_ZONE_SHOWN`/`ALERT_ZONE_GLOW`)
3. The lock screen: notification stacks and their proxies, edge proxies; the Now bubble's FLIP of art and labels between its sizes, its rubber-band drag, `--lock-x`, Main's swell as the drop closes in
4. Messaging bubbles and the pull out of the Clock
5. Landscape stage and the summoned panels
6. Morphs between faces, pop stagger per part, label masks, minute tick on the clock
7. The control panel (`panel.html`, `MainActivity`) — still a WebView; then `dna.css`, `fonts/` and `icons/` leave `assets/`
8. Watchers emitting typed models directly instead of JSON, and bitmaps instead of data URIs
