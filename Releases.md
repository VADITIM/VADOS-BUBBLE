# Releases

What changed in each feature version. Planned work is in `.claude/docs/roadmap.md`. Patch versions (x.x.1, x.x.2 …) are fixes and small adjustments; they are folded into the feature version they belong to. `RELEASE_NOTES.md` holds only the text of the latest release.

## 0.8: Compose (0.8.0)

- **Native rebuild:** the bubble, Dashboard, Notifications menu, player and mods now run in Jetpack Compose instead of the WebView canvas; `compose` is the main branch. Notifications groups drag open with stacked layers, Dashboard sliders merge into a screen-centred hold, connection buttons rotate-pop, the clock's seconds are tightened, Main covers the punch hole while it bounces home, and the player's timeline wipes in beside a popping equalizer.

## 0.7: One UI 9 (0.7.0 – 0.7.2)

- **One UI 9 fixes:** the hotspot button works again (One UI 9 silently ignores Samsung's own hotspot call from shell, so the AOSP tethering fallback now runs when the hotspot does not move); the fullscreen bottom-right Dashboard corner is gone; the Notifications menu closes on a tap above it, cannot be reopened by the tap or swipe that closed it, and takes taps while it is still opening; its rows arrive like SHELL's (staggered cards, spinning round icons, the app name swept in, the message typed). The title line is typed too, and the list runs 15% faster, messages type faster, highlights grow with their text, and the screen recorder closes the Dashboard.

## 0.6: messaging bubbles (0.6.0 – 0.6.8)

- **Messaging bubbles:** Telegram conversations collected into threads, with a hand-set list of the conversations allowed to bubble (Settings), and a Debug module that simulates a message or a reply, clears the threads and reads out each conversation. The bubble is pulled out of the Clock by a drag down, carried, and melts back into it; *VADOS Messaging* toggles it from a launcher gesture.
- **Always-on alert fix:** a notification the system lit the screen for, or that arrived before the keyguard had locked, played the normal bubble Alert instead of the always-on text; the screen state is now read when the notification is posted.
- **Charging sweep:** the battery sweep in the Status bubble is brighter and continuous, with no idle gap between passes.
- **Dashboard taps and toggles:** a hold on a connection button no longer leaves the bottom row of toggles deaf; the battery module opens the battery page; the energy saver holds the screen at 60Hz; extra dim and the saver apply from the tap; the hotspot tries every One UI overload; a tapped notification opens with this app's start rights regardless of which windows are standing.
- **Comdirect and hotspot buttons:** the Comdirect icon is centred; the hotspot button is no longer switched off by the read-back of its own tap. A refused One UI hotspot call falls back to Android's tethering service, and what both refused is shown on the Debug screen.
- **Main bubble handover:** its icon and reading shake out and turn in like the Status bubble's instead of sliding sideways, and the reading waits for the bubble to finish growing and is clipped to it, so the equalizer no longer draws outside the bubble while it resizes — including back from the Dashboard.
- **App icon (0.6.8):** the status row, clock, activity and mobile bubbles, on the V/AS squircle (`design/app-icon/icon.svg`).

## 0.5: lock screen, stages and the Dashboard (0.5.0 – 0.5.13)

- **Lock screen:** notification groups on the keyguard, each with its own bubble and a delete-all bubble.
- **Always-on alerts:** a message that wakes the dark screen is plain text rather than a bubble — the app name in its colour on the label sweep, the message typed out — slightly brighter, and can be simulated from Debug.
- **Stage reveal:** hidden, alert, revealed and leaving stages, so an Alert can come down over a hidden bubble and go back up.
- **Mods:** new DB Navigator trip mod; reworked call, media, timer and notification mods, the Status panel and the tabs.
- **Now bubble:** arrives cleanly in the panel and answers taps across its whole width; on the lock screen it sits low, clear of the fingerprint sensor.
- **Setup:** wireless ADB pairing from the app; runtime permissions granted through Shizuku.
- **Landscape:** no bubbles; the Dashboard opens from the Status launcher (the bottom-right double tap was removed in 0.7.0).
- **Dashboard:** toggle icons always show the phone's real state, volume and brightness sliders eased at both ends, brightness let go on close, no fade on entry.
- **Expansions:** no fade-in anywhere a bubble grows open (notifications, the player, mods, the slung Alert and Channel); the contents are whole from their first frame; the media player opens by uncovering its contents rather than stretching them, so it no longer lags, and its frost thickens in after it lands.
- **Polish:** per-state launcher icons, live press feedback and a second motion pass; the Alert's sender fades instead of scrolling, Discord alerts name only the person, the battery sweeps while charging, and the Alert pull zone drops its down-arrow (glow only).
