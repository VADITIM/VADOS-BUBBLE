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
| **Now** | Out at the clock, left end of the status bar. It takes the bar from its own spot to the punch hole, and the row stands aside for it. | built | Idle, Mod, Active, Haptic, Push |
| **Status** | Right end of the status bar, over the system's own icons. | built | Idle, Modus, Active |
| **Double** | Beside the punch hole, alongside whatever Main is doing. | built | none of the common states — see below |
| **Hidden** | Past the second satellite: a coloured dot, not a bubble face. | built | none; it is a count |
| **Lock** | Where the lock icon is on the lock screen. | built | none; it is an indicator that merges on unlock |
| **Notification** | Over the lock screen's own notification list, replacing it. | built | none of its own; a tap opens the app that posted it |
| **Lock Now** | At the bottom of the lock screen, where a thumb reaches. Replaces the Now Bar. | built, partly | Mod, Active, Haptic; Pull and Push planned |

While the Now bubble is out the row is allowed **one** satellite, not two: the bar left over is not wide enough for a second, and a row that overflows into the punch hole is worse than a row that counts. A satellite losing its place is not hidden — it is run into the bubble, which takes the knock, and it comes back as the first dot.

Only the Main bubble takes mods, and only the Main bubble alerts. Everything else on this list either carries one thing for as long as it is true, or stands for something the system already draws.

## Structural states

What a bubble *is*, independent of which mod or content it is showing.

| Name | Was called | What it is |
|---|---|---|
| **Idle** | Closed | No mod true. Bare bubble, idle face, `compact` width. |
| **Mod** | Closed, mod-owned | A mod (media/clock/call) owns the bubble's shape: glyph, short reading, `playing`/`timing`/`calling` width. On the main bubble this is **Main-Mod**. A mod widens the bubble slightly, puts its own glyph against the left inset and its reading or secondary icons on the right. |
| **Satellite-left / Satellite-right** | Satellite | A second or third live mod, not the owner, drawn as a circle beside the bubble instead of inside it. A satellite only exists because a mod is behind it, so it is always a Mod state by side rather than by centre. |
| **Alert** | Alert | A fresh notification arriving: `alerting` (text) or `image` (with a photo), dwells and auto-closes. Main bubble only — an alert is by definition a thing that arrived, and only the bubble at the punch hole stands for arrivals. **One conversation is one alert**, and it stacks — see below. |
| **Alarm** | — | **Built.** An alarm ringing, which is the one state allowed to take the whole screen: it is not information, it is a demand, and it goes on until it is answered. It is a *state of the bubble* rather than a bubble of its own — same shape, same corners, arriving out of the row — because a second element would have to be given the growth, the liquid, the window and the corners again, and it would arrive from nowhere. Orange, with two circular-squircle buttons wearing the clock app's own words. |
| **Active** | Grow / Open | Tapped open, full detail, own layout, no satellites: `expanded`, `picture`, `player`, `timer`, `history`. What it opens is a Tab (below). |
| **Haptic** | — | Held down, growing under the finger. In code this is the hold in progress, before it resolves either to opening a different Tab than Tap would, or to leaving for the mod's own app. |
| **Pull** | — | Swiped down. Opens the notification Tab from any bubble at all — the one gesture no mod has claimed, so it can mean the same thing everywhere. |
| **Push** | — | Swiped up. Dismisses: an alert back to Idle, a satellite out of existence, a mod off the bubble. Only meaningful when there is something to close. |
| **Double** | Charging | **Built.** A true second main bubble — standing beside the punch hole the way the bubble itself does, not owned by it, not a Mod and not a Satellite. It runs alongside whatever the main bubble is doing rather than taking its slot, which is what makes it Double. Not a Now bubble either — Now stands out at the clock; Double stays at the cutout. It holds the system's own short-lived announcements: charging, low battery, recording ended. It has no common states because it is never interacted with — it appears, it says one thing for a few seconds, it goes, and it overlaps everything because in that moment what it carries outranks everything. The hard part is that it has to merge with whatever it lands on top of: an alert arriving mid-charge has to read as two bodies of the same liquid touching, not as two unrelated shapes overlapping. Held by *measuring*: it rides the main bubble's own right edge every frame it is out, so a growth pushes it along rather than being drawn over it, and it costs no window because nothing about it is interactive. It goes home as a merge — the offset given up first, the shape following it in — and the bubble takes the knock. |

Code's `state` variable carries `idle | alert | active | haptic`. Pull and Push are named states in the model but resolve inside the gesture handlers rather than living in that variable — Pull ends in Active on the notification Tab, Push ends in Idle or in a bubble that no longer exists. Do not add them to `state` unless something genuinely has to branch on being mid-gesture.

The old `state === 'active'` for a notification showing was renamed to `'alert'` first, freeing `'active'` for what had been `'open'` — the two would otherwise have collided on one string for two different things.

**An alert is a conversation, not a message.** A second thing said by the same person while the alert is up appends beneath the first: the bubble does not close, does not announce itself again, and does not play its arrival a second time. Anything else makes a message impossible to read, because the act of reading it is exactly when the next one lands.

What it stacks is not accumulated in the page. A messenger posts one notification per conversation and rewrites it as each message arrives, so the whole run is already in the payload as `lines` — `text` is only the last of it. That run is there to say what has been *added*, and nothing else: **a fresh alert is one message**, whatever the conversation behind it is holding. Drawn in full it would put a backlog on screen and stack an alert nobody watched arrive. What is new is whatever comes after the count already taken — a count, never a search through the run, because someone sending "ok" twice has said two things and looking the last one up finds the wrong one. An app that posts a fresh notification per message has no `lines` and a new key every time: there the same person is recognised by sender and package, and a new message by the run saying something other than what is at the bottom of the stack.

The alert does not grow for it, and **one message is drawn exactly as it always was** — the box is as tall as the message and stands under the head row, with no free space to be anchored in and nothing to fade. Only with a second message does the stack take what the head row leaves and anchor to its *bottom* edge, so the newest line stands still where the eye already is and the oldest runs off the top under a fade, which is the end that has already been read.

The condition is the count of messages, not a measurement of whether they fit, and that distinction cost a build. Whether a stack has outgrown its room can only be measured against a box that has finished growing, and on the frame an alert arrives the bubble is still the closed one — 34px, which one line of anything overflows. Measured there it answered yes for a single message and stood it against the bottom edge of the alert under a fade, with a hole beneath the header. `:has(.message + .message)` cannot be wrong about how many children there are, on that frame or any other. Prefer a condition the first frame already knows over one that has to wait for a transition to be true.

An arriving line grows its own height from nothing, and that *is* the push: everything above it moves up because it is getting taller. Animated separately they read as a fade standing next to a jump. A photo is not a line in a conversation — the image alert is a different face at a different height with nowhere for a stack to grow — so a picture always arrives as its own alert.

**Merge** is not a state of its own — it is the transition a mod's arrival or departure runs *between* two structural states (Idle↔Mod, Mod↔Mod). The glyph is the cause and travels first; width is the consequence and follows once it lands. `mergeHold` holds the bubble genuinely Idle for the length of that flight so a mod's face never shows before the mod has actually arrived — see [motion.md](motion.md) for the glyph-as-cause rule this transition is built on.

## Mods

A mod is something that is simply true for as long as it is true, and it belongs to the Main bubble alone. Mods are a set and they stack: whichever arrived first owns the bubble's width, the next two become satellites a side, and anything past that is counted as a dot. First come, first served — the order is rotated by a sideways swipe, not by a ranking.

| Name | Code names | What it is | Built |
|---|---|---|---|
| **Media** | `playing` / `player` | A song playing. | built |
| **Clock** | `timing` / `timer` | The clock app's running timer. | built |
| **Call** | `calling` | A connected call. No Active view of its own — tapping opens the call in its app. | built |
| **Battery** | Double's own | Charging or low. **Not a mod**: it lives on the Double bubble beside the hole, because a charge level taking the bubble away from a song for four seconds is the one thing a Double exists to prevent. | built |
| **Torch** | Now's own | The flashlight. Lives on the Now bubble, not in the Main row. | built |
| **Recording** | Now's own | Screen or voice recording. Now bubble, whole pill red, white content: it is the one state on this bar that has to be readable from the corner of the eye, and a coloured icon is not. Tap pauses and resumes, hold opens its panel — elapsed, and the button that ends it. | built |
| **Download / Upload** | Now's own | A file in flight. The glyph moves the way the file is moving, the timeline runs along the bubble's bottom edge, and the app's own line under the title is the reading — every app writes "12,4 MB/s" differently and a wrong parse is worse than the app's own words. It wears a tick for a moment when it finishes, then goes home. | built |
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
| **Recording** | Haptic on the Now recording bubble: elapsed and stop. It shares the flashlight panel's box rather than opening a second one — it is the same bubble held down. | built |

### The alarm

Read off the notification, like the timer: a ringing alarm is a state the clock app declares and takes back. What separates it from every other notification that app posts is the **full-screen intent** — an app asking to take over the screen is the platform's own definition of "this cannot wait", and it is the alarms and the incoming calls that use it. Calls are excluded by name, because they have a bubble already.

Three things about it are deliberate:

- **It costs the shade swipe while it rings**, because the window is the whole screen. That is the one time this project accepts that cost: there is nothing else to be doing.
- **The buttons wear the clock app's own words**, and fire the clock app's own actions. A label of ours saying "Snooze" over an action that turns out to be something else is the worst possible thing to press at six in the morning; a button whose action is not offered is not drawn.
- **Pressing stop does not close it.** The alarm is over when the clock app says it is over, and it says so by taking its notification away. Closing on the press would put the bubble back on the bar while the phone was still ringing.

## What the Now bubble carries

**Built.** Torch, Recording, Download and Upload. They are one set with one owner at a time, chosen first-come-first-served, and `setNowMod` is the only place a Now mod's life is written down: the flight out happens when the first one starts and the flight home when the last one ends, whichever mods those happen to be. Torch is one of them rather than the special case it used to be.

Each asks for its own width, and the ask is a **floor** rather than a width — the bubble still grows into whatever the row leaves it. `NOW_COVER` is the hard floor underneath all of them, because below it the bubble hands back the piece of bar it exists to stand on, and that is also the honest answer to "Torch narrowed": the torch's own ask is under the cover it owes, so it is as narrow as this bubble ever gets and the other three are what stand wider.

Neither a recording nor a transfer is watched by a source of its own. Both are ongoing notifications, which is what the platform means by "this is still going on" — the same reading that puts a running timer in the row. A transfer with an *indeterminate* bar is left in the shade: an app saying it has no idea how far along it is has nothing a timeline can honestly draw. A finished one is not dropped on the spot either, because apps take a progress notification away the instant it completes and the only thing the person would ever see is the bubble vanishing; it stands there wearing a tick first.

Paused is read off the recorder's buttons, not off a field — no recorder posts a state, but a running one offers "pause" and a paused one offers "resume". Both words are matched in both languages the phone speaks, because neither word is ours.

Whether the row stands aside for this bubble is a setting (`nowPushesRow`, on by default). Off, the row keeps its place and its two satellites, and the Now bubble takes only the bar that is actually free.

### Push on the Now bubble

A swipe up sends it home to the punch hole and lets go of what it was showing. It is a Push in the sense this file means — the mod comes off the bubble, not out of existence — so the recording goes on recording and the file goes on arriving; the mod stays dismissed until it actually ends, or a transfer's next tick would bring the bubble back a fraction of a second later. If something else is happening it takes the bubble over on the spot rather than the shape flying home and coming straight back out.

The flight is the lock bubble's shape rather than the torch's: gathered in on the spot while already crawling home, then up to full speed, and the merge ends the frame the drop's centre reaches the bubble's. The torch going *out* keeps its own choreography, because that one is a light being switched off and it has been walked on the phone. Two flights until the dismissal has been walked too; then they should become one helper.

Where this bubble stands and how much bar it must cover are **one measurement, taken on the phone**: `setNowLeft` and `setNowCover` both come from the host now, so the page has no geometry of its own to disagree with. What the numbers have to match is what One UI's own Now Bar chip covers — that is a device measurement and nothing else can stand in for it.

## Status

**Built.** The Now bubble's opposite number, at the right end of the bar, standing on the system's own icons the way the Now bubble stands on One UI's flashlight chip. It replaces battery, wifi / mobile connection and the signal bars, and it carries Modus as well.

A Modus is not a mod in the sense the main bubble means: it colours the whole bubble rather than taking a slot in it. While one is active the Status bubble wears that Modus' colour and puts its icon on the left. Hotspot, USB and Bluetooth are the three, in that order — the order is how much of the phone is currently being lent to something else, which is what the person holding it cares about — and exactly one is worn at a time.

The colour arrives through the **border** rather than the background, because the skin reads its edge colour off the box it mirrors: written there it is in the liquid without a second rule keeping the two in step, the same way an alert's accent is on the main bubble.

It is the one bubble with no Haptic and no arrival of its own past the first. There is nothing to hold it for — Active is the system's own settings screen for whatever it is currently reporting, and a panel of ours that toggled radios would be a second settings app rather than a status bubble. It is born at the punch hole once, when the first connectivity payload lands, and it never leaves: what it stands for is true all the time.

What it can and cannot say about a link is a permission boundary, not a design choice. Wifi's strength rides on the capabilities the default-network callback already carries, so its bars are real; a cellular level needs `READ_PHONE_STATE`, which is a runtime prompt for a glyph, so mobile says *which* link it is and stops there rather than drawing a strength it guessed. A paired device's charge comes from the broadcast SystemUI's own meter reads (`android.bluetooth.device.action.BATTERY_LEVEL_CHANGED`), because asking `BluetoothDevice` for it is a hidden method and blocked — unheard, the level is simply unknown rather than wrong.

The paired charge stands in front of the phone's own, and the phone's own is always rightmost. A phone is charged by the person holding it; a pair of headphones runs out in the middle of something.

Everything in [bubbles.md](bubbles.md) still binds — born at the punch hole, liquid with whatever it comes near, drawn on the canvas, never a window of its own.

## Clock

**Built.** The time, over One UI's own clock at the far left of the bar. It has no states at all: no mod, no Active, no press, and it never leaves — it is the one thing up there that is true without anything having happened. It is born at the punch hole once and stands where it lands.

It is also the only bubble that costs no window. Nothing about it is interactive, so it needs no touch proxy, and that is the rule working the way it is meant to rather than an omission: a proxy exists for what can be touched and every pixel one covers is a pixel the shade swipe cannot start on.

"Merges with Now, never overlaps it" is held as geometry rather than as care. The Now bubble stands on the flashlight chip and may not move off it, so the one that gives way is this one: its `max-width` is capped against `--now-left`, leaving `--clock-reach` between them — close enough that the skin necks across the gap the moment the light is on, and near enough to never that no arrangement of the two can put one over the other. A width picked to look right can be forgotten; a cap cannot.

The tick is scheduled to the next minute boundary rather than every 60,000ms. A repeating interval drifts against the wall clock and eventually turns the minute over a second or two late, which on a clock is the whole of what it had to get right.

## Lock screen

The lock-screen bubbles exist to replace what the lock screen already draws, not to stand next to it. All of them are planned.

### The steal

A mod on the lock screen is not copied down to the bottom and it is not drawn twice. It **belongs to a different bubble** for as long as the keyguard is up: the Lock Now bubble takes it, and the row at the cutout no longer has it at all. `LOCK_STEALS` names which mods are taken — media today — and the whole of the steal is one filter in `liveMods()`, because `liveMods()` is what the row's width, its satellites, its dots and its window are every one of them worked out from. Take the mod out there and all of them stop carrying it without being told; putting it back is the same filter going quiet.

So a new mod does not need lock-screen handling written for it. It needs a name in `LOCK_STEALS` and a face on the lock bubble, and the row lets go of it by itself.

Satellites follow the bubble that owns the mods, which on the lock screen is this one — planned, not built.

The stolen mod keeps its own identity down there rather than borrowing the row's. `--app-accent` is scoped to the lock bubble and written from the song it is carrying, because the row does not have that mod any more — nothing up there is keeping the colour right for a bubble that is no longer its.

Active on this bubble is not a panel that appears over it: it is the same box at a greater height. It is hung off the bottom edge of the screen, so height is the only thing that changes and it grows upwards on its own, with the end the thumb is resting on never moving. A second element would have to be told all of that. It is entered by a tap or by a flick upwards, and left by either the same tap or a flick down — the direction of the flick is the direction the box moves.

Its transitions are named once in `--lock-transition` and every rule that adds a property to the list puts that list back rather than replacing it. The play drag's homing rule did replace it, and took the height with it: opening was instant, and the bug read as "Active is not animated" rather than as what it was — a rule about dragging quietly owning every other animation the bubble had. Same trap as the blanket `transition: none` in [motion.md](motion.md#motion-is-additive).

Closed, its layout is three bands rather than one row: the song at the top beside its cover, the transport spread across the whole width beneath it, and the timeline along the bottom. The width matters — this is the bubble worked with the thumb of the hand already holding the phone, and controls huddled at one end of a full-width box are the one place that thumb cannot reach.

Open, it is a record sleeve and takes about seventy per cent of the screen: the cover fills the width at the top, the song is read underneath it rather than beside it, and the transport grows into what is left. Nothing here is a second element — the cover is the same `<img>` at a different size, so it *travels* between the two layouts rather than being swapped for a bigger copy of itself. Its size is written as a length rather than a percentage for exactly that reason: width and height animate, and a height of auto with an aspect ratio does not.

The timeline is flush against the bottom edge with no padding under it, so the squircle cuts the ends of the line and it belongs to the shape. The dot marking the song's position rides the end of the filled part rather than being placed from the ratio a second time — one number, one owner, and it cannot disagree with the line it stands on.

A touch anywhere on a lock-screen bubble has to hold the screen on. These windows are not the keyguard's, so working the controls down there is watched by nobody and the screen goes off mid-gesture. `PowerManager.userActivity` is a signature permission and out of reach; `FLAG_KEEP_SCREEN_ON` set on the canvas at the touch and taken off again a while later is the same thing from the outside. It has to come off again — a flag left standing is a phone that never sleeps.

Unlocking is a **merge**, not a disappearance. It is one journey at two speeds, never two moves. It closes to `LOCK_PINCH_SCALE` on an ease that leaves slowly and ends fast and past its mark, and it is travelling the whole time it does — at `LOCK_CRAWL`, a quarter of full speed — then opens up to full speed for the rest of the way once there is nothing left to close. Held still while it closes it reads as two unrelated things in turn, a resize and then a slide. Size and travel are two animations on two properties rather than one keyframe list, because a keyframe's easing owns every property in it and these two want opposite ones. It travels on one goo layer with the bubble, closing over the journey to the bubble's own measured size — a shape wider than what is taking it in has not arrived yet. The `catchInto` is there for the swell only; the merge itself ends the frame the drop's centre reaches the bubble's, watched per frame rather than timed, because the flight is eased and its distance depends on where the bubble is standing, so that instant is not a number that can be written down beforehand. At it the drop goes out on the spot, the row takes its mod back in the same frame, and the bubble is knocked `LOCK_BUMP` up and back down — added to its transform, never set, since transform belongs to whatever is dragging it.

The steal outlives the keyguard by exactly one flight. `stealHeld` keeps `liveMods()` filtering the mod out while the drop is in the air, and the catch's `entered` step is what lets it go — so the row opens out *because* the drop arrived. Released when the keyguard went instead, the bubble at the cutout had already grown and settled before the thing it grew for had set off, and the two read as unrelated events happening near each other.

The flag saying it is flying is set *before* the row is repainted, never after. Taking the mod back is what repaints the row, and repainting the row repaints this bubble — which asks `lockMod()` again, is told the keyguard has gone, and takes `showing` off the very thing that is about to fly. It keeps `showing` for the whole journey: that class is what the skin reads to decide the shape is one of the row's at all, and taken off at the start it would spend the flight outside the liquid and arrive as a separate object fading on top of the bubble — the exact bug the torch drop had.

- **Lock** stands where the lock icon is. **Built.** Unlocking opens it — the shackle lifts and tilts — and *then* it flies into the Main bubble as a merge the mirror measures. Open first, then leave: a padlock that vanished the instant the phone unlocked would be a box being hidden; one that visibly opens and then goes is the same object answering what just happened, which is the glyph-as-cause rule with the shackle as the glyph. Where it stands (`PADLOCK_BOTTOM`) is a measurement off the phone and the only thing about it that cannot be decided in the page.
- **Rubber-banding the unlock swipe is not built and cannot be**, at least not from here: nothing exposes the keyguard's own swipe progress to an unprivileged app. There is no callback, no window insets animation for it, and reading it off touches we do not receive is not reading it. It stays out of the plan as a wish rather than a task until something on the device turns out to publish it.
- **Notification** bubbles overlap the system's own lock-screen notifications and take their place. **Built.** One bubble per *conversation*, newest line only — this is a glance from across the room, and the conversation itself is what the notification tab is for — with the rest counted rather than listed once past `NOTES_LIMIT`, the way the row counts its hidden mods. Redrawn whole rather than reconciled: a lock screen is looked at and not worked, so a diff of five rows costs more to get wrong than to redo.

  Its container is `overflow: visible` and the scroller *inside* it is what clips, which is the no-clipping rule doing its job rather than an exception to it — these are bubbles and a bubble's overshoot may not be cut off.

  It is the largest touch proxy in the project and the one place that is affordable: it stands on the lock screen well below the strip the shade swipe starts on, and it is given no size at all while the phone is unlocked.
- **Bottom Now** replaces the Now Bar. A song playing shows as a wide bubble at the bottom carrying that mod's own controls — back, play/pause, next. Tap expands the controls; Haptic opens the app. It is the only lock-screen bubble with states of its own.
