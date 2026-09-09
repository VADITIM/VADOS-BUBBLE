# Dynamic Bubble

A system-wide overlay pill for one Samsung Galaxy S25 (SM-S931B, Android 16 / SDK 36). Sideload only — no Play Store, no other devices, no API-level fallbacks for hardware this phone does not have. Kotlin services host WebViews; the interface itself is HTML and CSS.

`README.md` is the long form: what every state is, why each gesture belongs to the mod it belongs to, and what the platform forced. Read it before changing behaviour — most of what looks like an odd choice in the code is a workaround recorded there.

## The documents

Four rule files in `.claude/rules/`, and each one owns a different question. A change touching an area updates its file in the same task.

- `bubbles.md` — what every bubble is, whichever kind it is. Read before adding a bubble.
- `states.md` — the state vocabulary: kinds, states, mods, tabs, and which of them are built.
- `motion.md` — how anything is allowed to move: additive motion, the glyph-as-cause rule, liquid, the blur contract.
- `architecture.md` — the Kotlin/WebView split, the watchers, the windows, the bridge.
- `code-quality.md` — naming, comments, and what not to build.

## How to work here

1. **Read in this order:** this file, the rule file covering what is being touched, then the backlog note in the vault. Nothing about open work is written in this repository.
2. **One backlog item at a time.** Tick it `[x]` in the vault only once it has been verified on the phone.
3. **Name the contracts the change touches before editing** — state, motion, window, blur, mirrored constant. The glass is mirrored off the real boxes now rather than told where to go, so what a size change owes it is a `stirLiquid()` long enough to cover the transition.
4. **Docs change in the same task**, never afterwards.
5. **The check is the phone.** Build, install over wifi, walk the states, and say plainly what was verified on the device and what was not. Never report an animation as working when only the build succeeded.
6. **Fix the cause, not the caller.** A backlog item names one symptom; the same bug is usually live in every sibling that routes through the same function.

## Commands

- `export JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" && ./gradlew assembleDebug` — the build. `JAVA_HOME` is not set globally and Gradle will not find a JDK without it.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` — reinstalling keeps the accessibility and notification-listener grants, so `grant.ps1` is only needed on a first install or after an uninstall.
- `adb logcat -s IslandBubble` — every WebView console line from every page lands here, tagged with which page it came from.

`adb` is not on PATH; it lives under the Android SDK's `platform-tools`. The phone is normally on wifi rather than USB — `adb connect <phone>:5555`. The port `adb mdns services` announces refuses the connection; 5555 works without pairing.

**The address is DHCP and does change**, so a hardcoded one here is a trap rather than a shortcut: it was `192.168.0.83` and is `192.168.0.192` today, and a failed `connect` on the old one reads as the phone being off rather than as the note being stale. With the cable in, `adb shell ip -f inet addr show wlan0` says what it is; `adb tcpip 5555` then opens the port and `adb connect` takes it over wifi, after which the cable can come out. `adb tcpip` restarts the daemon on the phone, so the USB entry leaves the device list — that is the mode switching, not the phone going away.

The JDK and the SDK exist only on the machine the phone is flashed from. A checkout on any other machine can read and edit but cannot build or install, and must say so rather than reporting a phase done.

## Where the work is tracked

Two notes in the user's Obsidian vault, which is a configured working directory for this session:

- `Dynamic Bubble.md` — the live backlog. The user edits it directly; tick an item `[x]` there when it ships.
- `Dynamic Bubble Stability & Improvements.md` — the system overhaul: the model every bubble is meant to obey, and the current issue list. Its contents are being moved into `.claude/rules/` as they become binding.

Nothing about open work belongs in this repository.

## Key decisions

- **The bubble is an accessibility overlay, not a `SYSTEM_ALERT_WINDOW`.** SystemUI's status bar ranks above every `TYPE_APPLICATION_OVERLAY`, so a bubble drawn at the cutout could be seen but not touched. `TYPE_ACCESSIBILITY_OVERLAY` is the only window type above the status bar an unprivileged app can get.
- **The interface is a WebView because it is one identity, not several.** `assets/dna.css` is the token layer, `pill.html` is the bubble and everything beside it, `panel.html` the control panel. Every bubble is in `pill.html`, whichever end of the bar it stands at. Anything visual is written there, not in Kotlin.
- **One window draws, and it draws everything.** Bubbles merge through one shared blur filter, and a filter reaches only as far as its own surface — so a bubble in a second window can never be liquid. See `architecture.md`.
- **Every pixel a *touchable* window covers is a pixel the shade swipe cannot start on.** The canvas is untouchable and may span the bar; the touch proxies are kept exactly as big as what is interactive, and any extra room is asked for while it is needed and given straight back.
- **One device, no defensive breadth.** Version checks, alternative vendors and unreachable branches are noise here: this runs on a phone whose exact behaviour can be tested in a minute.

## Style

The interface follows the VADITIM Style DNA — the `vados-dna` skill in the global config repo (`~/.claude/skills/vados-dna/`). Say **"use VADOS DNA"** to load it. Its typography and motion rules bind here; where a rule in `.claude/rules/` is more specific, the more specific one wins.

## Don'ts

- Never widen a touchable window "to be safe". See the shade-swipe decision above.
- Never resize the WebView itself to animate something — resizing it reallocates its surface, which reads as the interface blinking out for a frame. The WebView is a fixed stage; the window clips it.
- Never give a new bubble its own window. It cannot merge from there.
- Never do hit-testing in Kotlin. The page is the only side that knows where anything is.
- Never add a dependency, an abstraction layer or a settings toggle that was not asked for.
- Never commit or push unless asked.

## Output style

Output less text; compact everything at the end.
