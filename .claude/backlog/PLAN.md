# Dynamic Bubble — development plan

Progress lives here. `SOURCE.md` is the backlog note verbatim; this file is what is being done
about it, in order, with what is finished marked. Tick an item only once it has been walked on
the phone.

## Context

Dynamic Bubble is a **system overhaul for One UI 8.5 and onwards**: it replaces the status bar,
the notification system and the Now Bar with one connected surface whose parts interact. The
standard follows from the fact that it runs permanently on a carried phone — a stutter that
happens once an hour happens sixteen times a day.

**Verification changed.** CI now builds the debug APK and publishes it as a rolling release, so
a change can be compiled here even though it still cannot be *walked* here. A green build is
never a working animation; the phone remains the check.

---

## The bubble inventory

| Bubble | Where | Built | Owns mods |
|---|---|---|---|
| **Main** | At the punch hole | built | yes — Idle, Mod, Alert, Active, Haptic, Pull, Push |
| **Satellite** | Beside Main, one a side, two max | built | yes (borrowed) |
| **Hidden** | Past the second satellite | built | no — a dot; a count, not a face |
| **Now** | Left end of the bar, at the clock | built | yes, its own set |
| **Lock Now** | Bottom of the lock screen | built, partly | yes (stolen via `LOCK_STEALS`) |
| **Status** | Right end of the bar | planned | **no** — connectivity; battery always rightmost |
| **Clock** | Top-left, over the system clock | planned | **no** — merges with Now, never overlaps |
| **Double** | Beside the punch hole, alongside Main | planned | no — charging, low battery, recording ended |
| **Lock** | Where the lock icon is | planned | no — opens, then merges into Main on unlock |
| **Notification** | Over the lock screen's list | planned | no — scrollable, no drag radius yet |

| Mod | Eligible bubbles | Built |
|---|---|---|
| Media | Main, Satellite, Lock Now | built |
| Clock/timer | Main, Satellite | built |
| Call | Main, Satellite | built |
| Battery | Main today → becomes the first **Double** | built, moving |
| Torch | **Now only** | built |
| Recording, Download, Upload | **Now only** — what is *happening* | planned |
| Bluetooth, USB, Hotspot | **Status only** — what is *connected* | planned |
| Discord video | Main, Satellite | planned |
| DB Navigator | Main, Satellite, **Lock Now** | planned |

Every bubble is bouncy, gooey, animated, smooth and state-driven; born at the punch hole; liquid
with whatever it comes near; drawn on the canvas; never its own window. Clock and Status own no
mods and sit a few pixels shorter.

---

## Done

- [x] **Backlog persisted** — `SOURCE.md`, this file. `cf44f17`
- [x] **Skill library** — 44 skills vendored and pinned, `vados-dna` finally present. `50f4d52`
- [x] **`bubbles.md` written** — the rule file every other doc linked to and which did not exist. `c4c59e9`
- [x] **CI builds the APK** and publishes a rolling release. `6fd929a`, `62c9baf`
- [x] **Refactor** — virtual https origin so ES modules load, `pill.css` split out, script split into
      modules under `assets/js/`. `598437a`, `3cc620f`, `f2642a9`
- [x] **A1 — the transform-transition leak.** The springback is a WAAPI animation with
      `composite: 'add'`, so `transform` never carries a transition and `homing` is gone. This was
      the cause behind the swipe bounce *and* the fragile haptic. `3ae3569`
- [x] **A3 — nothing is clipped.** `8581ce3`
- [x] **A4 — alpha and blur held the whole way closed.** `a0ad6bb`
- [x] **A5 — a mod that ends gets a departure, not a repaint.** `9e62ba7`
- [x] **Satellite still fades and grows under a finger.** `2034894`

## Reported from the phone

Walked on the device and reported back, in the order they were reported.

- [x] **The lock bubble's overflow, gestures and the marker.** `0d72b8b` — `#lock-now` back to
      `overflow: hidden` (A3 was wrong: an element's own `overflow` never clips its own transform,
      so hiding cost the bounce nothing and the thing it actually contained was `.open`'s cover);
      `stirLiquid(420)` on the press, because the mirror was not running during the 320ms scale;
      `rubberBandPast()` exported so the lock bubble's own hold hears the drag threshold; the
      transport buttons and the timeline own their whole touch stream rather than the click alone;
      a scrub on `#lock-timeline`, which had none; the marker centred on the line box.
- [x] **The bubble expanded into the media mod with nothing playing.** `MediaControl.attach` chose
      a session on `metadata != null` alone — any session carrying a song, whatever state it was
      in. That fallback was written for a player's *first* seconds, which are STATE_NONE or
      BUFFERING while it connects; the sessions apps leave behind when they are *finished* look
      identical and sit in the active list for as long as the process lives. A browser tab that
      played a video, a game that made a sound, a closed podcast app: each of them opened the
      bubble out into a media mod out of nowhere. A session is now listened to from the moment it
      appears and only *chosen* once it plays or pauses, and it keeps the bubble until it says
      STOPPED, ERROR or dies. Two smaller bugs came out with it: the callback was on the chosen
      controller alone, so a second player starting to sound behind a leftover session was never
      heard, and it called `publish()` rather than re-selecting, so a song that ended kept the
      bubble.
- [x] **Closing YouTube handed the bubble Spotify, which was not running.** The half above did
      not cover it: the leftover a session list falls back to is regularly a *paused* one, and
      Spotify keeps its session alive long after the app has been closed and swiped away — a
      paused session passes the second test, so closing a video put that morning's song in the
      bubble. A session is now also asked whether the phone is still offering that player a
      notification at all (`IslandNotificationListener.isOfferingPlayer`). A media session is not
      proof a player exists; the notification is, because it is what the shade and Samsung's own
      media panel are drawn from. Null — the shade could not be read — passes, since it is a
      different answer from no.
- [x] **A new message from the same person replayed the whole alert.** C7, above. The read that
      matters: the payload already carried `lines`, the whole conversation, and the alert was
      drawing only `text` — the last of it. So this was never about accumulating state in the
      page; it was about drawing what had already arrived. What is new on each payload is
      whatever follows what is on screen — a prefix match, not a membership test, or someone
      sending "ok" twice has said one thing. An arriving line grows its own height out of
      nothing, which is what pushes the stack up: one animation rather than a fade next to a
      jump. `alert-stacking` in `DebugStage` sent the identical notification twice and could
      never have shown this; it sends a conversation of three now.
- [x] **A single message came to rest at the bottom of the alert, faded at the top.** Two
      causes, and the second is the one that mattered. The measurement was taken on the frame
      the alert arrived, when the bubble is still the *closed* one — 34px, which one line
      overflows — so the outgrown layout was applied to a stack with room to spare. But timing
      the measurement better was the wrong fix: the condition should never have been a
      measurement. It is `:has(.message + .message)` now, so **one message renders exactly as it
      did before any of this**, and only a second message changes anything. Prefer a condition
      the first frame already knows over one that has to wait for a transition to be true.
      A fresh alert also draws one message rather than the whole run `lines` is holding — the
      run is there to say what has been *added* while the alert stands, and drawn in full it put
      a backlog on screen and stacked an alert nobody watched arrive.

## Phase A — done

- [x] **A6 — the punch-hole corridor.** `--hole-gap` was reserved in exactly one place, the alert
      head row, and in none of the resting states — which are the ones parked over the camera for
      hours rather than for a dwell. It is now a `column-gap` floor on all four resting faces.
      The call face was three flat children, which is *why* Discord's name sat midway between the
      avatar and the clock: name and clock are one reading now, right-anchored, growing leftwards,
      fading on the left because that is the end that runs out.
- [x] **A7 — contained fixes.** Satellite-Spotify icon radius; Satellite-Clock continuing its tick
      across the swap instead of re-rendering; everything under our control in English;
      notification age in minutes; blurs missing on the locked Now bar at wake; the media timeline
      drag not stealing the bubble's haptic.
- [x] **A8 — new defaults.** `Preferences.defaults` → width 85, height 30, horizontal 0, vertical 3,
      R/G/B 0, alpha 80, blur 60, identity Both. Currently 130/34/0/12/0/0/0/100/0/2.
- [x] **A2 — haptic audit.** Passes by inspection, no change needed: A1 removed the cause; confirm the remaining wholesale `transition:`
      writes (`.dragging`, `.pulling`) still honour the named-list contract and that the swell
      composes onto a live drag.

## Phase B — system contracts

- [x] **B1. Bounciness everywhere.** Lock Now is the reference: `scale 320ms var(--ease-split)`,
      `.pressed { scale: 1.03 }`, `corner-shape: squircle`. Main, Satellite, Now and Status adopt
      the same curve family and press response. Written into `bubbles.md` as binding.
- [x] **B2. Fluid merging everywhere.** The mechanism is good; the coverage is not. Make Now↔Main,
      Clock↔Now and Lock↔Main merge the way Main↔Satellite already does. Mind the filter-region
      trap: a bubble outside it silently loses its skin and reads as a colour bug.
- [~] **B3. Debug stage — every state.** Six stages added; never ticked. Extend `DebugStage.SEQUENCE` to cover the swap, a satellite
      closing, the pull, the hold, the lock merge, the Now flight. **Never ticked.**
- [x] **B4. Drag radius.** Double `DRAG_RADIUS` (20 → 40) with a constrainer at the current 80%:
      crossing it resets the haptic to idle and stops it firing.
- [x] **B5. Settings.** Goo-strength slider; per-bubble widths for mod/idle/now/status.

## Phase P — performance

Everything here was read out of the code, not guessed. Ordered by cost.

- [ ] **P1.** `applyBlur` runs per pane per changed frame; each call does a `SharedPreferences.getInt`
      plus four reflective invocations — **~2,400 reflective calls and ~600 preference reads per
      second at 120Hz**, allocating a `SemBlurInfo` each time. `SamsungBlur`'s own comment says
      reflection per frame at 120Hz is worth avoiding; the lookups were cached, the invocations were
      not. Re-apply only when radius or corner actually changed.
- [ ] **P2.** `pane.layoutParams = bounds` is assigned unconditionally — a layout pass per pane per
      frame even when the size is unchanged.
- [ ] **P3.** The blur spec is re-parsed with `split`/`mapNotNull` per pane per frame. `sendBlurFrame`
      already diffs the whole spec; extend that per-pane.
- [ ] **P4.** `getComputedStyle` allocates a fresh declaration per blob per frame. The object is
      *live* — resolve once per element and re-read.
- [ ] **P5.** `blobs.map(...)` builds a new array and object per blob every frame.
- [ ] **P6.** `stirLiquid` defaults to 1,100ms, outliving its animation by up to a second; `TRACE_MERGES`
      is marked "temporary" in the source and should be gated out of normal running.

## Phase C — new bubbles and mods

One step each, one push each, naming the feature.

- [ ] **C1. Status bubble** — connectivity is its whole subject: bluetooth (device icon, charge %),
      USB, hotspot, wifi/mobile, signal, battery always rightmost. A Modus colours the whole bubble
      and puts its icon on the left. Active expands to that connection's settings.
- [ ] **C2. Clock bubble** — top-left over the system clock; no mods; visible merge with Now.
- [ ] **C3. Double** — charging leaves the Main row, magnetic against Main's edges when they overlap.
- [ ] **C4. Now mods** — Recording (red pill, white content, tap pause/resume, haptic panel, elapsed),
      Download and Upload (animated glyph, timeline, x/x MB and speed, done-tick then merge). Per-mod
      widths; Torch narrowed. Settings toggle for whether Now pushes the left satellite aside.
- [ ] **C5. Now geometry** — same width and position Spotify covers; swipe up dismisses to the punch
      hole on the Lock-Now-derived bounce.
- [ ] **C6. Alarm state** — full screen with the top margin, orange, circular squircle buttons.
- [x] **C7. Alert stacking** — a second message from the same conversation appends beneath the
      first instead of announcing itself as a new alert. Built off `lines`, which the payload
      already carried: a messenger posts one notification per conversation and rewrites it as
      each message lands, so the conversation was in the page all along and only the last of it
      was being drawn. The bubble does not grow — the stack is anchored to its bottom edge once
      it outgrows the alert, so the oldest is pushed up and off the top under a fade, which is
      what was asked for over a growing box.
- [ ] **C8. Spotify polish** — dithered hue bottom to ~35%, dot at the timeline head, squircle buttons.
- [ ] **C9. Discord video**; **DB Navigator** (Zugnummer/remaining while travelling, Gleis/departure at
      an Umstieg; tap expands the journey, tap again opens the app, haptic goes to the Abo tab).
      Eligible for Lock Now too, so it joins `LOCK_STEALS` and needs a face there. Starts with
      checking whether a saved journey is readable at all. Plus **TimeTree** as a named type
      (green/white) and media speed-up for Telegram/WhatsApp voice notes.
- [ ] **C10. Lock screen** — the Lock icon bubble; rubber-band response to unlock swipe progress;
      real Notification bubbles in a scrollable overflow-visible container.
- [ ] **C11. Notification quick settings** — **blocked**, the two screenshots were never attached.
- [ ] **C12. Status bar replacement** — one-button set/reset of the Samsung settings. **Blocked**,
      the four screenshots were never attached. Disabling the bar itself is its own step now —
      see C15, last in this phase, because it depends on Status and Clock existing first.
- [ ] **C13. Rendering order** — the bubble always at the lowest index system-wide so system
      notifications never overlap it, without suppressing them.
- [ ] **C14. Blur over content** — an overlapped bubble's content is not inside the blur. The panes
      are host `View`s *behind* the WebView, so a pane cannot blur WebView pixels. **Research step
      with an honest answer**, not a promised fix.
- [ ] **C15. Disable the status bar.** The last step, and depends on C1 and C2 being on the phone
      first — the real bar is the only thing showing the clock and connectivity/battery until
      Status and Clock exist as bubbles, so hiding it earlier would delete information rather than
      replace it. This is the overhaul's own logic: nothing stock is removed until what it did is
      fletched out and standing in its place.

      Mechanism, researched, not yet tried on the phone: no root and no privileged permission is
      needed. `pm grant com.v.island android.permission.WRITE_SECURE_SETTINGS` (once, from an
      `adb shell` — a normal app cannot grant itself a secure-settings permission) unlocks the
      hidden AOSP flag `Settings.Global.policy_control`, which SystemUI reads on every window
      layout: `settings put global policy_control immersive.full=*` hides status and nav bars
      system-wide, `immersive.status=*` status bar alone, `null*` reverts. Grantable once at
      install and then driven entirely from `onServiceConnected()` — no further adb step. It is a
      swipe-revealable immersive mode, not a removal: the real bar still exists and a swipe from
      the top edge brings it back momentarily, the way a fullscreen video player behaves — which
      is the one property this project needs kept, since the shade swipe must never be blocked.

      What is unverified: Samsung's own community reports `policy_control` as unreliable on One
      UI, honoured on some builds and silently ignored on others, because their SystemUI fork
      does not always take the AOSP path stock Android does. Whether One UI 8.5 on the SM-S931B
      honours it at all **can only be answered on the phone** — this is a one-button
      set/reset toggle to build and walk, not a promise. Good Lock's NavStar module was checked
      as a fallback and rejected: it reports as icons-only, not a real hide, so there is no second
      mechanism waiting if this one does not stick.

- [ ] **C16. Fullscreen-game bubble visibility is backwards.** In Clash Royale the bubble is
      visible by default over the fullscreen game — it should be hidden by default, the way
      `isFullScreen` in `BubbleService.onAccessibilityEvent` is meant to make it. Swiping down or
      the back gesture then reveals the status bar and makes the bubble disappear, which is also
      backwards: the reveal should bring the bubble back with the bar, not take it away. A
      diagnostic log line was added and pushed (`c9d59dd`) on `statusBar event=…` in
      `onAccessibilityEvent` to tell apart two candidate causes: the event never firing for this
      game at all (in which case the disappearance is the real system status bar visually drawn
      over our accessibility overlay during the peek, not our code hiding anything), versus the
      event firing with `isBarHidden` read backwards. Needs the phone: `adb logcat -s IslandBubble`
      through opening the game and doing the swipe/back gesture. Pull the log line back out once
      diagnosed — it is temporary. This is also the gate for C15: swipe-to-reveal has to actually
      show the bubbles, not hide them, before disabling the real bar is worth doing at all.

## Content marking

Added 2026-08-28.

- [x] **Time and dates highlighted like a marker, light blue.** `.time` in `pill.css`, `TIME_PATTERN`
      in `js/mods/notification.js`. German formats, since the phone is German: `27.08.2026`, `27.08.`,
      `14:30`, `3. September`, ISO. Deliberately only concrete tokens — "heute" and "morgen" are time
      words too, but a highlighter drawn over every one of them stops meaning anything.
- [x] **Money highlighted like a marker, yellow.** `.money`, `MONEY_PATTERN`. Symbol either side,
      because both are written: `7,80€` and `$5.00`. The symbol is part of the match — a bare number
      is not money.
- [~] **Google apps in Google colours, except Gmail.** `AppStyles.kt`. **Google Wallet is done** —
      Google Blue `#4285f4`, the primary rather than a share of the four. The rest of the Google apps
      still need the decision below.

### Open question on the Google item

Gmail is currently `#fbbc04`, which *is* Google yellow — so today it is the one Google app already
wearing a Google colour, and the ask is to take it off. Two readings, and they build differently:

1. **Each Google app gets its own official brand colour** (Drive green, Calendar blue, Maps
   green-blue, Photos multicolour), and Gmail is excluded — keeping something of its own, presumably
   its red `#EA4335`, or a neutral.
2. **All Google apps share one four-colour Google treatment** — a gradient or a multi-stop accent
   across blue/red/yellow/green — and Gmail alone stays flat.

Reading 2 is the harder one, and now measured rather than guessed: `--app-accent` is read into
`color`, `border-color`, `background` and `border` at **nine sites** in `pill.css`. A gradient cannot
go into `color` or `border-color` at all without rewriting each of them, so the four-colour treatment
is not a colour change — it is a change to how the accent is consumed everywhere. Wallet was shipped
under reading 1 for that reason; it is reversible if you want reading 2.

Google Wallet is now in `AppStyles` beside Gmail. Whichever reading wins, the rest of the item is
adding the other Google apps that actually notify — Calendar, Drive, Photos, Keep, Maps.

Worth noting the two features meet: a Wallet notification is a payment, so `7,80 € bezahlt bei REWE`
gets the yellow money marker inside a Google-blue bubble without either feature knowing about the
other.

---

## Working rules

- One item at a time; name the contracts it touches (state, motion, window, blur, mirrored constant)
  before editing.
- Docs change in the same task, never afterwards.
- **Everything pushes to `claude/ios-dev-plan-progress-xnyhqr`.** `main` is never pushed to.
- A move never travels with a fix — one thing per commit, each independently revertable.
- Load-bearing comments travel with their code; most record a platform trap that cost an afternoon.
- Fix the cause, not the caller: the same bug is usually live in every sibling routing through the
  same function.


---

## What Phase A and B actually found

Three of these were not the bug that was reported, and are worth remembering as a class.

- **The camera corridor existed in one place only** — the alert head row — and in none of the
  resting states, which are the ones parked over the hole for hours. Discord's name looked
  centred because the call face was three flat children under `space-between`, so "midway
  between the avatar and the clock" *is* the middle of the bubble.
- **`meltBy` measured the canvas as one line.** The lock bubble is nearly full-width at the
  bottom, so against anything on the row it reported a gap of most of the screen negative,
  pinned the deviation at its ceiling, and welded the row into one bar for as long as the
  keyguard was up. Nobody reported this; it fell out of auditing B2's coverage.
- **The blur on the locked bar was a disagreement, not a failure.** The host clears every pane
  at screen-off because a transparent view goes on blurring; the page kept believing the panes
  were there and said nothing on wake, since `sendBlurFrame` dedupes and the geometry was
  identical. "Sometimes" was whether anything moved while the phone slept.

And two near-misses worth the same memory: `#now.pressing` already existed at 1.08 further down
the file, so a second rule would have been dead on arrival; and `#battery-face` had a `gap`
that would have quietly won on source order and put its reading back over the camera.
