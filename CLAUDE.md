# Dynamic Bubble

A system-wide overlay pill for one Samsung Galaxy S25 (SM-S931B, Android 16 / SDK 36). Sideload only — no Play Store, no other devices, no API-level fallbacks for hardware this phone does not have. Kotlin services host WebViews; the interface itself is HTML and CSS.

`README.md` is the long form: what every state is, why each gesture belongs to the mod it belongs to, and what the platform forced. Read it before changing behaviour — most of what looks like an odd choice in the code is a workaround recorded there.

## Commands

- `export JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" && ./gradlew assembleDebug` — the build. `JAVA_HOME` is not set globally on this machine and Gradle will not find a JDK without it.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — reinstalling keeps the accessibility and notification-listener grants, so `grant.ps1` is only needed on a first install or after an uninstall.
- `adb logcat -s IslandBubble` — every WebView console line from both pages lands here, tagged with which page it came from.

`adb` is not on PATH: it lives at `C:/Users/vadim/AppData/Local/Android/Sdk/platform-tools/adb.exe`. The phone is normally on wifi rather than USB — `adb connect 192.168.0.83:5555`. The port `adb mdns services` announces refuses the connection; 5555 works without pairing.

## Where the work is tracked

The task list is a note in the user's Obsidian vault: `C:/Users/vadim/Documents/Obsidian/Vault/Dynamic Bubble.md`. It is the live backlog and the user edits it directly — read it at the start of a session, and tick an item `[x]` there when it ships. Nothing about open work belongs in this repository.

## Key decisions

- **The bubble is an accessibility overlay, not a `SYSTEM_ALERT_WINDOW`.** SystemUI's status bar ranks above every `TYPE_APPLICATION_OVERLAY`, so a bubble drawn at the cutout could be seen but not touched. `TYPE_ACCESSIBILITY_OVERLAY` is the only window type above the status bar an unprivileged app can get.
- **The interface is a WebView because it is one identity, not two.** `assets/dna.css` is the token layer, `pill.html` is the bubble, `panel.html` the control panel, `torch.html` the torch's own pill. Anything visual is written there, not in Kotlin.
- **Every pixel a window covers is a pixel the shade swipe cannot start on.** Windows are kept exactly as big as what they draw, and any extra room is asked for while it is needed and given straight back.
- **One device, no defensive breadth.** Version checks, alternative vendors and unreachable branches are noise here: this runs on a phone whose exact behaviour can be tested in a minute.
- Docs follow code: a change that invalidates `README.md` or a file in `.claude/rules/` updates it in the same task.

## Style

The interface follows the VADITIM Style DNA — the `vados-dna` skill in the global config repo (`~/.claude/skills/vados-dna/`). Say **"use VADOS DNA"** to load it. Its typography and motion rules bind here; where a rule in `.claude/rules/` is more specific, the more specific one wins.

## Don'ts

- Never widen a window "to be safe". See the shade-swipe decision above.
- Never resize the WebView itself to animate something — resizing it reallocates its surface, which reads as the interface blinking out for a frame. The WebView is a fixed stage; the window clips it.
- Never add a dependency, an abstraction layer or a settings toggle that was not asked for.
- Never commit or push unless asked.

## Output style

Output less text; compact everything at the end.
