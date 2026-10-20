# Releases

What changed in each feature version. Planned work is in `.claude/docs/roadmap.md`. Patch versions (x.x.1, x.x.2 …) are fixes and small adjustments; they are folded into the feature version they belong to. `RELEASE_NOTES.md` holds only the text of the latest release.

## 0.6: messaging bubbles (0.6.0 – 0.6.2)

- **Messaging bubbles:** Telegram conversations collected into threads, with a hand-set list of the conversations allowed to bubble (Settings), and a Debug module that simulates a message or a reply, clears the threads and reads out each conversation. The bubble is pulled out of the Clock by a drag down, carried, and melts back into it; *VADOS Messaging* toggles it from a launcher gesture.

## 0.5: lock screen, stages and the Dashboard (0.5.0 – 0.5.13)

- **Lock screen:** notification groups on the keyguard, each with its own bubble and a delete-all bubble.
- **Always-on alerts:** a message that wakes the dark screen is plain text rather than a bubble — the app name in its colour on the label sweep, the message typed out — slightly brighter, and can be simulated from Debug.
- **Stage reveal:** hidden, alert, revealed and leaving stages, so an Alert can come down over a hidden bubble and go back up.
- **Mods:** new DB Navigator trip mod; reworked call, media, timer and notification mods, the Status panel and the tabs.
- **Now bubble:** arrives cleanly in the panel and answers taps across its whole width; on the lock screen it sits low, clear of the fingerprint sensor.
- **Setup:** wireless ADB pairing from the app; runtime permissions granted through Shizuku.
- **Landscape:** no bubbles; a double tap bottom-right opens the Dashboard.
- **Dashboard:** toggle icons always show the phone's real state, volume and brightness sliders eased at both ends, brightness let go on close, no fade on entry.
- **Expansions:** no fade-in anywhere a bubble grows open (notifications, the player, mods, the slung Alert and Channel); the contents are whole from their first frame; the media player opens by uncovering its contents rather than stretching them, so it no longer lags, and its frost thickens in after it lands.
- **Polish:** per-state launcher icons, live press feedback and a second motion pass; the Alert's sender fades instead of scrolling, Discord alerts name only the person, the battery sweeps while charging, and the Alert pull zone drops its down-arrow (glow only).
