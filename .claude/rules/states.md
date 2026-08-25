---
paths:
  - "app/src/main/assets/**"
  - "app/src/main/java/com/v/island/**"
---

# States

The Dynamic Bubble is a state-driven system: everything on screen is some bubble in some state, and every state is reachable from every other one by a named transition. This file is the vocabulary — which bubbles exist, which states each of them can be in, and which of those are built today. [bubbles.md](bubbles.md) says what all bubbles share; [motion.md](motion.md) says how the transitions between these states are allowed to move.

Rows are tagged **built** (on the phone now) or **planned** (specified, not written). A planned row is binding the moment it is built: it is here so nothing gets built the wrong shape first.

## Bubble kinds

| Kind | Where it stands | Built | States it can enter |
|---|---|---|---|
| **Main** | At the punch hole. The bubble. | built | Idle, Mod, Alert, Active, Haptic, Pull, Push |
| **Satellite** | Beside the Main bubble, one a side, two at most. | built | Mod (by side), Active, Haptic, Push |
| **Now** | Out at the clock, left end of the status bar. It takes the bar from its own spot to the punch hole, and the row stands aside for it. | built | Idle, Mod, Active, Haptic |
| **Status** | Right end of the status bar, over the system's own icons. | planned | Idle, Mod, Active, Haptic |
| **Double** | Beside the punch hole, alongside whatever Main is doing. | planned | none of the common states — see below |
| **Hidden** | Past the second satellite: a coloured dot, not a bubble face. | built | none; it is a count |
| **Lock** | Where the lock icon is on the lock screen. | planned | none; it is an indicator that merges on unlock |
| **Notification** | Over the lock screen's own notification list, replacing it. | planned | Active |
| **Bottom Now** | At the bottom of the lock screen, replacing the Now Bar. | planned | Active, Haptic |

While the Now bubble is out the row is allowed **one** satellite, not two: the bar left over is not wide enough for a second, and a row that overflows into the punch hole is worse than a row that counts. A satellite losing its place is not hidden — it is run into the bubble, which takes the knock, and it comes back as the first dot.

Only the Main bubble takes mods, and only the Main bubble alerts. Everything else on this list either carries one thing for as long as it is true, or stands for something the system already draws.

## Structural states

What a bubble *is*, independent of which mod or content it is showing.

| Name | Was called | What it is |
|---|---|---|
| **Idle** | Closed | No mod true. Bare bubble, idle face, `compact` width. |
| **Mod** | Closed, mod-owned | A mod (media/clock/call) owns the bubble's shape: glyph, short reading, `playing`/`timing`/`calling` width. On the main bubble this is **Main-Mod**. A mod widens the bubble slightly, puts its own glyph against the left inset and its reading or secondary icons on the right. |
| **Satellite-left / Satellite-right** | Satellite | A second or third live mod, not the owner, drawn as a circle beside the bubble instead of inside it. A satellite only exists because a mod is behind it, so it is always a Mod state by side rather than by centre. |
| **Alert** | Alert | A fresh notification arriving: `alerting` (text) or `image` (with a photo), dwells and auto-closes. Main bubble only — an alert is by definition a thing that arrived, and only the bubble at the punch hole stands for arrivals. |
| **Active** | Grow / Open | Tapped open, full detail, own layout, no satellites: `expanded`, `picture`, `player`, `timer`, `history`. What it opens is a Tab (below). |
| **Haptic** | — | Held down, growing under the finger. In code this is the hold in progress, before it resolves either to opening a different Tab than Tap would, or to leaving for the mod's own app. |
| **Pull** | — | Swiped down. Opens the notification Tab from any bubble at all — the one gesture no mod has claimed, so it can mean the same thing everywhere. |
| **Push** | — | Swiped up. Dismisses: an alert back to Idle, a satellite out of existence, a mod off the bubble. Only meaningful when there is something to close. |
| **Double** | Charging | **Planned.** A true second main bubble — standing beside the punch hole the way the bubble itself does, not owned by it, not a Mod and not a Satellite. It runs alongside whatever the main bubble is doing rather than taking its slot, which is what makes it Double. Not a Now bubble either — Now stands out at the clock; Double stays at the cutout. It holds the system's own short-lived announcements: charging, low battery, recording ended. It has no common states because it is never interacted with — it appears, it says one thing for a few seconds, it goes, and it overlaps everything because in that moment what it carries outranks everything. The hard part is that it has to merge with whatever it lands on top of: an alert arriving mid-charge has to read as two bodies of the same liquid touching, not as two unrelated shapes overlapping. |

Code's `state` variable carries `idle | alert | active | haptic`. Pull and Push are named states in the model but resolve inside the gesture handlers rather than living in that variable — Pull ends in Active on the notification Tab, Push ends in Idle or in a bubble that no longer exists. Do not add them to `state` unless something genuinely has to branch on being mid-gesture.

The old `state === 'active'` for a notification showing was renamed to `'alert'` first, freeing `'active'` for what had been `'open'` — the two would otherwise have collided on one string for two different things.

**Merge** is not a state of its own — it is the transition a mod's arrival or departure runs *between* two structural states (Idle↔Mod, Mod↔Mod). The glyph is the cause and travels first; width is the consequence and follows once it lands. `mergeHold` holds the bubble genuinely Idle for the length of that flight so a mod's face never shows before the mod has actually arrived — see [motion.md](motion.md) for the glyph-as-cause rule this transition is built on.

## Mods

A mod is something that is simply true for as long as it is true, and it belongs to the Main bubble alone. Mods are a set and they stack: whichever arrived first owns the bubble's width, the next two become satellites a side, and anything past that is counted as a dot. First come, first served — the order is rotated by a sideways swipe, not by a ranking.

| Name | Code names | What it is | Built |
|---|---|---|---|
| **Media** | `playing` / `player` | A song playing. | built |
| **Clock** | `timing` / `timer` | The clock app's running timer. | built |
| **Call** | `calling` | A connected call. No Active view of its own — tapping opens the call in its app. | built |
| **Battery** | `battery` | Charging or low. Mod-shaped today; it becomes the first Double. | built, moving |
| **Torch** | Now's own | The flashlight. Lives on the Now bubble, not in the Main row. | built |
| **Recording** | — | Screen or voice recording. Now bubble, whole pill red, white content. | planned |
| **Discord video** | — | A stream or camera live in a call. | planned |

The first column is the name to use in prose and in new code; the second is what the existing code still calls it. Each mod pairs a resting shape with its own separate open shape, and there is no single string that could stand for both without collapsing two real states into one.

The glyph and reading a mod puts inside the Idle-Mod bubble are not subject to the boundary fade, which is a text behaviour — they only fade when they are animating outside the bubble's own bounds.

## Tabs

A Tab is the content of an Active state: its own container with its own layout, reusable by anything that wants to show that content. Tabs are not mods. A mod decides which Tab its Tap and its Haptic open; a Tab does not know which mod sent it, which is what lets two mods show the same Tab with different values in it.

| Tab | Opened by | Built |
|---|---|---|
| **Notifications** | Pull, from any bubble. Tap on a bare Main bubble. | built |
| **Quick Settings** | Haptic on a bare Main bubble. | built |
| **Media** | Tap on the Media mod. | built |
| **Timer** | Tap on the Clock mod. | built |
| **Flashlight** | Tap on the Now torch bubble. | built |
| **Picture** | Tap on an alert carrying a photo. Sized to the image rather than to a fixed box. | built |
| **Discord video** | Tap on the Discord video mod. 16:9 to screen width, or the video's own ratio if it is vertical. | planned |
| **Recording** | Haptic on the Now recording bubble: time, pause/resume, stop. | planned |

## Status

**Planned.** The Now bubble's opposite number, at the right end of the bar, standing on the system's own icons the way the Now bubble stands on One UI's flashlight chip. It replaces battery, wifi / mobile connection and the signal bars, and it carries Modus as well.

A Modus is not a mod in the sense the main bubble means: it colours the whole bubble rather than taking a slot in it. While one is active the Status bubble wears that Modus' colour and puts its icon on the left.

Everything in [bubbles.md](bubbles.md) still binds — born at the punch hole, liquid with whatever it comes near, drawn on the canvas, never a window of its own.

## Lock screen

The lock-screen bubbles exist to replace what the lock screen already draws, not to stand next to it. All of them are planned.

- **Lock** stands where the lock icon is. Unlocking animates it open and then merges it into the Main bubble, which ripples for it.
- **Notification** bubbles overlap the system's own lock-screen notifications and take their place.
- **Bottom Now** replaces the Now Bar. A song playing shows as a wide bubble at the bottom carrying that mod's own controls — back, play/pause, next. Tap expands the controls; Haptic opens the app. It is the only lock-screen bubble with states of its own.
