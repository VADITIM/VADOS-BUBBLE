# Dynamic Bubble (personal build)

Overlay pill for one Samsung Galaxy S25 (SM-S931B, Android 16 / SDK 36).
Sideload only. No Play Store, no other devices.

## Build and install

```
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.11"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

The control panel shows the live state of every grant and has a button for each.

**Accessibility is what brings the bubble up.** The bubble is not a `SYSTEM_ALERT_WINDOW` overlay:
SystemUI's status bar window ranks above every `TYPE_APPLICATION_OVERLAY`, so a bubble drawn at the
cutout could be seen but not touched — taps went to the status bar and were swallowed.
`TYPE_ACCESSIBILITY_OVERLAY` is the only window type above the status bar that an unprivileged app
can obtain, and it is granted by the accessibility toggle instead of a signature permission. The
service subscribes to window events only, reads no screen content, and Android owns its lifecycle —
there is nothing to start or stop from the panel.

If a toggle in Settings is greyed out as **Restricted setting**, that is Android blocking a
sideloaded app rather than a bug: open **App info**, three-dot menu, **Allow restricted settings**,
then grant it again. The panel has an *Open app info* button for exactly this.

To skip the whole dance over adb:

```
powershell -ExecutionPolicy Bypass -File grant.ps1
```

That grants `ACCESS_RESTRICTED_SETTINGS` and `POST_NOTIFICATIONS`, and enables both services —
the notification listener and the accessibility service the bubble window needs.

## Style

The interface follows the VADITIM Style DNA from `PORTFOLIO25/.claude/style-dna`: near-black ground,
hairline-bordered translucent panels, monospaced uppercase micro-labels, one terminal-green accent
inherited from the root, and motion that arrives slowly and diagonally and leaves fast.

The panel is three screens — Settings (terminal green), Debug (amber) and System (electric blue) —
changed only by the bottom nav buttons or the number row, never by a swipe: full-width sliders own
the horizontal stroke. Debug carries the bubble status and one replay button per styled app, which
re-sends that app's last real notification — image and all — because a made-up one never has the
shape the real layout has to survive.
Each screen owns its accent and its own diagonal slices, which slide in from the side it belongs to.

Every surface is a WebView so the identity is one implementation, not several:

- `assets/dna.css` — the token layer: faces, palette, panel primitive, micro-label, bar-sweep reveal.
- `assets/panel.html` — the control panel.
- `assets/pill.html` — every bubble: the one at the cutout, its satellites, the clock at the left end of the bar and the Now mods it carries, and the lock screen's own.

The four identity fonts live in `assets/fonts/` and are registered once, in `dna.css`.

This file is the long form of what the system *does*. The rules it has to obey while doing it are in
`.claude/rules/`: [bubbles.md](.claude/rules/bubbles.md) for what every bubble is,
[states.md](.claude/rules/states.md) for the state vocabulary,
[motion.md](.claude/rules/motion.md) for how anything is allowed to move, and
[architecture.md](.claude/rules/architecture.md) for the Kotlin/WebView split and the windows.

## The bubble

The pill is always on the glass. Closed it carries nothing but the unread count at the configured
size; tapping it grows it into a quick setting (Mikrofonzugriff); a notification takes it over for
five seconds and then it returns to closed. Swipe up dismisses an alert early.

Per-app colours live in `AppStyles.kt` and travel to the bubble as data — Telegram, WhatsApp,
Discord, Instagram, Gmail, DB Navigator, Comdirect and Spotify each own their accent, which becomes
the bubble's border; anything else falls back to terminal green. Each app gets its own layout later:
what matters in a train notification is not what matters in a chat one.

### States

Every gesture means something different depending on what the bubble currently is, so the state is
named in `pill.html` rather than inferred from whichever size happens to be showing:

- **Closed** — the bubble as configured, nothing added.
- **Closed mod** — closed, plus something that is simply true for as long as it is true: a song
  playing, a sensor live. Mods are a set and they stack, so the song and the microphone dot can own
  the closed bubble at once. Only one mod owns the *width*; the sensor dots ride on top of whichever
  that is.
- **Active** — a notification has taken the bubble over for its dwell.
- **Open** — it was tapped open. Any tap closes it again, including one anywhere else on the screen.
  A tap outside only closes an open bubble: an alert owns its dwell and is not dismissed by whatever
  else you were doing.
- **Haptic** — it is being held down, growing under the finger.

The transitions branch on the state, not on the size, which is what makes a hold on a closed mod
bubble able to mean something other than a hold on a bare one.

A tap outside cannot come from a DOM listener, because the host window is only as big as the bubble.
It arrives as the window manager's `ACTION_OUTSIDE`, which reports the touch without consuming it —
whatever was actually tapped still gets the tap.

Gestures:

- **Tap an alert** — opens the app and collapses. A photo message opens to the photo first; the
  second tap opens the app.
- **Tap the closed bubble** — whichever mod owns it opens its own panel: the player, the timer. A
  call has no panel worth drawing, so it leaves for Discord through the notification's own intent,
  which lands inside the call. A bare bubble has no mod, so it opens the notification centre.
- **Hold the closed bubble** — it grows under the finger, with a haptic at 350ms, and leaves for the
  app the mod stands for: the player opens Spotify, the timer opens the clock through its own
  notification, which lands on the running timer rather than on whichever tab was last open. A bare
  bubble and a call have no app left to open — the bare bubble has none and the call already went
  there on tap — so both hold to the bubble's own settings.
- **Swipe down on the closed bubble** — the notification centre, from any bubble at all. Tap and
  hold are both spoken for by whichever mod is in the bubble, and the list belongs to no mod in
  particular, so it takes the one gesture nothing else has claimed and answers the same way
  everywhere. Every notification the shade is holding, not a private log of what the bubble happened
  to announce. Tap a row to open that app, swipe a row sideways to dismiss it — which dismisses it
  everywhere, because it is the same act. Rows are as tall as what they have to say; a notification
  cut to one line is one you still have to open.
- **Hold an open mod** — leaves for the app it stands for: the player opens Spotify, the timer
  opens the clock through its own notification, which lands on the running timer rather than on
  whichever tab was last open. Nothing grows under the finger here — the window is already exactly
  the open bubble, so a scale would only be clipped by it.
- **Swipe up** — dismisses an alert back to idle.
- The unread count sits at the left of the resting bubble and clears when the list is opened or a
  notification is tapped.

Photos ride along as data URIs: `BigPictureStyle` carries them either as a bitmap or as an `Icon`,
scaled down to 1080px before they become JSON, because a full-resolution photo would be megabytes
of it per notification.

## Music

Music is a state, not an event: it does not arrive, dwell and leave, it is simply true until it
stops. So a player's notification is dropped by the listener (`EXTRA_MEDIA_SESSION` is the tell) and
the song owns the **resting** bubble instead — a little wider than bare, album art on the left, a
three-bar indicator on the right that only moves while sound is actually playing.

Tapping it opens the player: art with title and artist beneath on the left, previous / play-pause /
next with a seekable timeline in the middle. Scrubbing follows the finger; the seek is sent once, on
release, because asking a player to jump sixty times a second makes it stutter. An alert still takes
the bubble for its five seconds and then hands it back to the song rather than to the closed bubble.

`MediaSessionManager` hands sessions only to an enabled notification listener, which is a grant the
bubble already has.

## Timer

The clock app ("Uhr") is the same kind of truth as a song: it posts an ongoing notification for as
long as a timer runs and takes it away when it stops, so that notification is the state. It becomes
a closed mod — the sweeping glyph on one side, the reading on the other — and tapping it opens the
clock app's own buttons, Pause and Abbrechen, fired as the notification's actions.

One UI does not put the remaining time in a field; the shade renders it from a private RemoteViews
chronometer. What it does leave behind is `android.ongoingActivityNoti.secondaryInfo` — "40 min, 4 s
/ 18:45" — whose second half is the wall-clock time the timer ends at. That half is worth more than
the first: a target can be counted down against locally every second, while a snapshot goes stale
the moment it arrives, and the notification is only re-posted when the timer is touched.

A timer outranks a song for the closed bubble, but only by default. When more than one mod is
true, the bubble pulls its width in and splits the others off as circles beside it — the song's
moving bars, the timer's sweeping hand — one satellite a side. A swipe sideways across the closed
bubble rotates the order, so the song can be pulled to the front by hand.

The window has to grow for a satellite, and it grows on the side the satellite is on: it is shifted
by half that width and the page pushes the bubble back by the same amount, so the bubble stays
welded to the cutout instead of sliding off it to make room. The blur follows the bubble alone —
one blurred region spanning the bubble and its satellites would be a single smear across the gap.

The closed row is places, not cases:

```
… dot dot [satellite] (bubble) [satellite] dot dot …
```

Whatever leads the order is the bubble; the next two get a circle a side; everything after that is
counted with a dot in its own app's colour, alternating sides, 5px out from the circle it sits
beside. Each circle is its own pane of glass — one region spanning the row would blur the gaps
between them too — while a dot is a marker and gets none. A fifth kind of mod needs nothing added
here: it is the length of the list that decides.

The swap works differently once both sides are taken. With one satellite the sides trade: the
satellite ends up where the bubble retreated to, because that is visibly where it went. With two
the sides are fixed and the contents rotate through them — the circle being pulled in takes the
middle, the bubble takes the slot opposite it, and the third wraps round into the slot just
vacated. That last one is the only piece that would have to fly the whole width to get there, so it
does not: it is shoved further out by the bubble arriving over it, fading as it goes, and by the
halfway mark it is gone and changes sides, coming back in from the other edge. The shove peaks
exactly where the fade reaches nothing, which is also exactly the room the window has spare — so
nothing visible is ever clipped by the window's edge. Its glass cannot fade, so it shrinks on the
same curve instead.

Which way the row rotates is which circle the finger pulled. Dragging away from a satellite pulls
it in, and with both sides taken either one can be pulled, so the direction is read from the finger
and may change its mind as long as it comes back through the middle first. The dots hold still
through all of it: a dot is a count, and the count does not change while the row rotates.

## Call

Discord keeps a foreground service running while a voice call is connected, and the notification
that service must post therefore exists exactly as long as the call does — which makes it the same
kind of state as a song or a timer, and the third closed mod. It carries a ringed circle and the
name of whoever is on the other end, faded out rather than cut where it runs out of room — and the
name starts to the right of centre, because the bubble is welded to the cutout and anything
beginning in the middle is read through the camera hole.

The circle wants the caller's avatar and never gets one: `android.largeIcon` is null on every voice
notification Discord posts. Its small icon is no stand-in — that is a crossed-out microphone, which
says muted rather than who. So the circle holds Discord's own mark, and a real picture is used if
one ever arrives. The only offline route to the actual avatar is caching them out of Discord's
message notifications, which do carry one, and matching by name.

Neither does the ring pulse. The other two mods animate because what they show is changing — the
sound moves, the time runs out — and a call simply is or is not. There is no panel of its own
either: everything worth doing to a call, mute, hang up, see who is talking, is already in the app.
So a tap is a tap on the bubble and opens what the bare bubble opens, and a hold leaves for Discord
through the notification's own intent, which lands in the call rather than on whatever tab was last
left open.

Every ongoing Discord notification counts as a call. Discord posts no other kind for any length of
time, and the alternative is matching on channel names in whatever language the phone is set to.

## The Now mods, and the bubble that carries them

Some states do not belong at the punch hole. The flashlight is one: One UI draws its own blue chip
for it at the far left of the status bar, right beside the clock, and a bubble that says the light is
on has to stand exactly there or Samsung's chip shows through next to it. A recording, a download and
an upload are the same kind of thing — something that is *happening*, as against the row's mods,
which are something that is *playing* or *running*.

There was a second bubble out there for them, standing on the chip a few pixels right of the clock.
There is not any more, and the reason is that the two wanted the same corner: the clock had to leave
whenever a mod came out, the row had to stand aside for both of them, and most of the motion at that
end of the bar was two shapes getting out of each other's way. The clock carries the mods instead —
the time when nothing is happening, the thing that is happening when something is. The name Now
belongs to the lock screen's bubble now, which is the only place a Now bubble still stands.

The hand-over is two beats and the order is the whole of it: the time gives way first, fading and
walking left, and the box grows to the mod afterwards. The reading is the cause and the width is the
consequence, which is the same rule a mod's glyph obeys when it arrives at the main bubble. Going
back is that backwards — the width first, the time after it.

It never retreats. One UI's clock and its chip are directly underneath, so a bubble that moved aside
to make room would uncover the exact thing it exists to cover — that was built once and Samsung's
chip appeared every time. The row is the side that yields instead, and it yields by moving rather
than by shrinking: while a mod is standing in the clock, everything is pushed right until the main
bubble's left edge is at the punch hole, the clock takes the bar that was given up, and the two rest
a few pixels apart so the skin necks between them. The row is allowed one satellite while this is
true; a second is run into the bubble, which takes the knock, and comes back as a dot. A state grown
wide enough simply passes in front.

The torch has no closed face at all: a light that is on is shown as its panel — five steps on a rail,
the dot running between them as one body of liquid — at the width the media player opens to, and it
goes away by the light going out rather than by being tapped shut. The torch is read from
`CameraManager.registerTorchCallback` rather than from this app's own taps, so the reading is right
whoever lit it: Samsung's tile, the quick settings panel, or the switch in the bubble's own panel.
The other three keep a resting face — a glyph, a short reading, and for a transfer a timeline along
the bottom edge — and a tap on each means what that mod's one obvious act is: pause a recording,
open the app carrying a file.

All of it merges with the rest of the row like any other bubble, and that is why it is drawn in
`pill.html` rather than in a page of its own. A shared blur filter reaches exactly as far as the
surface it is drawn on, so for as long as any of this lived in a second window it could only ever
*appear* to be part of the bar.

## Replacing the system pop-up

One UI pops up twice over, and switching off only the first leaves the second on screen — which is
exactly what "the pop-ups are not suppressed" looked like:

- the AOSP heads-up, off with `heads_up_notifications_enabled = 0`;
- Samsung's own **brief** pop-up, drawn by `com.samsung.systemui.notilus` off its own notification
  listener, off with `cmd notification disallow_listener`.

Both need the shell UID, so the **System pop-ups** row does both through Shizuku and `grant.ps1`
sets both directly. The notification still lands in the shade and still reaches the listener.

`NotificationAssistantService` would be the per-notification way to do this, but it is a `@SystemApi`
and cannot be compiled against from a normal app.

## What is never announced

A notification killer — BuzzKill here — is another listener on the same broadcast, so it is
racing this one: it sees the notification at the same instant and cancels it a fraction of a second
later. Announcing immediately meant the bubble showed exactly the things that had been switched off
on purpose, and then sat there for its full dwell after they were already gone.

So an alert waits out the race. The payload is built at once, and 700ms later the shade is asked
whether the notification is still there; only then does the bubble say anything. A kill that lands
later still wins: `onNotificationRemoved` tells the page, and a bubble showing a notification the
shade no longer holds returns to rest. An open panel is the user's own doing and is left alone.

Two more answers the phone has already given are honoured, since the bubble stands in for the
heads-up pop-up and owes the same ones: a channel turned down to `IMPORTANCE_MIN` never pops up,
and neither does anything Do Not Disturb is currently filtering out.

## Battery

The one thing that is always true, and therefore never a mod — a bubble carrying the battery
permanently would be a status bar. What matters is the moment it changes, so it is an alert that
happens to be about the phone itself: it appears, it dwells four seconds, it goes.

Three events. Past 15 percent in yellow, past 5 percent in red, and plugged in, in green. The marks
are edge-triggered and remembered, so a slow drain announces once rather than once per percent, and
charging clears them for the next time down. The first, sticky broadcast only primes the state --
without that a low battery would pop up on every install.

Charging is drawn as the charge arriving: the fill runs from where the battery actually is up to
full, holds, fades and starts again from the real level, so the animation never lies about how full
the thing is. Low is the same fill at rest, breathing. Both can be exercised without waiting for a
real battery:

```
adb shell cmd battery unplug; adb shell cmd battery set level 14
adb shell cmd battery set level 4
adb shell cmd battery set ac 1
adb shell cmd battery reset
```

## Screen off

Nothing an unprivileged app draws reaches the always-on display — that surface belongs to SystemUI —
so a notification takes a `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` for six seconds and the
arrival animation plays on a woken screen.

A dark screen is not an empty one, though, and a phone left charging overnight holds whatever is
drawn in the same pixels for hours — which is how an OLED gets burnt. So the bubble goes away with
the screen: `ACTION_SCREEN_OFF` hides it exactly as landscape does, transparent and untouchable with
the blur taken off, and `ACTION_SCREEN_ON` brings it back. A wake we asked for counts immediately
rather than waiting for its own broadcast, or the arrival would play hidden and be over by the time
the screen agreed it was on. The battery never asks for one at all: plugging in at night is the case
this must not light up for, and the platform posts its own warning for a low battery in a pocket.

## Microphone access toggle

"Mikrofonzugriff" is sensor-privacy state, not a secure setting, so no sideloaded app can write it.
It goes through Shizuku: a user service (`ShellService`) runs with the shell UID and executes
`cmd sensor_privacy enable/disable 0 microphone`, and the state is read back out of
`dumpsys sensor_privacy` (sensor 1, toggle type 1: state 1 = blocked, state 2 = allowed).

Shizuku must be started after every reboot:

```
adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
```

Then grant this app in the panel's System screen. Without it the toggle shows `kein Zugriff` and
does nothing rather than lying about the state.

## What stands, and what is next

Standing: the accessibility overlay and its bridge; the bubble with Idle, Alert, Active and Haptic
states; media, timer and call mods with satellites, dots and the sideways rotation between them; the
liquid skin across the main row; the notification list with swipe-to-dismiss; per-app colours and
keyword highlighting; the picture state; battery alerts; the Now bubble with the torch and its step
panel; Samsung's blur by reflection; both system pop-ups suppressed through Shizuku; the control
panel with permission diagnosis and per-app notification replay.

Next, in order — each of these is a phase, and each is why the one after it is possible:

1. ~~**The state replay harness.**~~ Done. One debug button per state and per transition.
2. ~~**The canvas window.**~~ Done. One full-width untouchable window draws everything; small
   touch-proxy windows carry the touches.
3. ~~**The Now bubble on the canvas.**~~ Done. Liquid across the whole bar; the second window and
   the window-reordering workaround are gone.
4. **Modules and the `Bubble` type.** `pill.html` splits into ES modules, and Main, Satellite, Now
   and Double become one type with one set of shared behaviour.
5. **Additive motion.** Every animated property owned in one place, animated through the Web
   Animations API with `composite: 'add'`, so an interaction layers onto what is already running
   instead of replacing it.

After that: the Double bubble, the lock-screen bubbles, the Discord video tab and
the recording Now state — each one ordinary feature work standing on the contract in
`.claude/rules/`.

The live backlog is a note in the Obsidian vault, not in this repository.

## Device facts measured on-device

- Display 1080x2340, density 480dpi (3.0x).
- Cutout bounding rect `Rect(511, 0 - 569, 103)` px = 58x103 px = ~19.3 x 34.3 dp, horizontally
  centred (centre x = 540 px). The default pill height of 34 dp matches it.

## Debug hooks

Fire the synthetic notification without touching the screen:

```
adb shell am start -n com.v.island/.MainActivity --ez test true
```

Change panel screen without touching the screen (`adb shell input tap` does not reach WebView
content on this device, but key events do):

```
adb shell input keyevent 9
```

Post a real notification through the listener:

```
adb shell cmd notification post -t "Title" tagname "body text"
```

## Layout notes

The window is exactly the size of the visible bubble. That matters more here than for an ordinary
overlay: the window sits above the status bar, so every pixel it covers is a pixel the notification
shade swipe cannot start on — a window wider than the bubble makes the top of the screen dead.

`pill.html` grows the window through `Android.setWindowSize(width, height)` and only starts the CSS
animation once the WebView has actually been re-laid out; animating before that ran the first frames
against the old viewport, which dragged the bubble off centre. It calls `setWindowSize(-1, -1)` after
the collapse ends, so the window shrinks only once the bubble has finished moving.
