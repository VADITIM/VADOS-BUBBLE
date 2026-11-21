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
- [ ] Alert with dwell, conversation appending, keyword/time/money/link marking, picture state, gone grace
- [ ] Player (cover swipe for next/previous, tap play/pause, wave scrub, speed for talkers), timer panel, trip panel
- [ ] Notifications menu: grouping, reveal labels, typed bodies, swipe to dismiss, scroll band, delete-all bubble
- [ ] Clock with hold to open the clock app; torch step panel; recording face and panel
- [ ] Status bubble readings (USB, transfer, modus, link, battery, paired charge) and the charge announcement
- [ ] Liquid merge between every bubble; Samsung blur behind each blob
- [ ] Dashboard (`bubble/Dashboard.kt` state, `bubble/DashboardView.kt`): opened by a tap on the Status bubble, a pull down past 34 or a pull stolen by the shade past 12, and by the panel broadcast; closed by a tap on the Status bubble, a swipe up anywhere (24 up, under 40 across), a tap on bare ground, the stage hiding, or Main extending. Status hold opens the settings of what it shows. Clock and Status grow into the two top corners (time with seconds over the date; settings and power), Main rises into the screen's top edge. Frosted scrim through one full-screen blur pane (`syncScrim`), stepped in twelfths. Stats (weather, storage/RAM, battery cell with hundredths read every second and charge bubbles, transfer strip or data today, USB), connection grid with spinning ring readings and searching dots, brightness/volume/call bars with the eased value curve and the merge on hold (brightness clears the panel), endless knob row with fling, the mods clear/restore switch. Pop entrance ranked from the panel's centre outward, collapse leave, fast entrance preference, 12dp grace to the nearest control, toggle read-backs held while the panel moves, asked-for switches and levels held over the read until the phone agrees

- **Now bubble** (unverified): `NowBubble.kt`/`NowView.kt`/`Sling.kt` port `js/lock.js` and `js/sling.js`. On the keyguard and in the Dashboard the media mod leaves Main (`isStolen`) for its own bubble, slung out of Main as a ball and opened in place; it flies home on unlock or when the panel closes, thrown along the flick that closed it, and Main bumps as it melts in. Tap opens/closes it (lock 124↔546, panel compact 190×44↔124), hold opens the app, sideways skips, down pauses when big, the wave scrubs. In the panel the lower divider stands a gap above it.

Not ported yet, in order:

1. Dashboard remainder: the stats-section channel (swipe down into notifications), the slung Alert, the stat rise after the pop, the ring's found burst, the knob row shrinking under an expanded Now bubble (`html.now-open`)
2. The Alert band window, pull-to-focus and the slung dashboard alert
3. The lock screen: notification stacks and their proxies, edge proxies; the Now bubble's FLIP of art and labels between its sizes, its rubber-band drag, `--lock-x`, and melting on entering Main rather than at the shot's end
4. Messaging bubbles and the pull out of the Clock
5. Landscape stage and the summoned panels; screen-dark arrival (fake AOD)
6. Morphs between faces, pop stagger per part, label masks, minute tick on the clock
7. The control panel (`panel.html`, `MainActivity`) — still a WebView; then `dna.css`, `fonts/` and `icons/` leave `assets/`
8. Watchers emitting typed models directly instead of JSON, and bitmaps instead of data URIs
