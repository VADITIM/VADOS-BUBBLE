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
| **Status** | Right end of the bar | built | **no** — connectivity; battery always rightmost |
| **Clock** | Top-left, over the system clock | built | **no** — merges with Now, never overlaps |
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
| Bluetooth, USB, Hotspot | **Status only** — what is *connected* | built |
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

- [x] **P1.** `applyBlur` runs per pane per changed frame; each call did a `SharedPreferences.getInt`
      plus four reflective invocations — **~2,400 reflective calls and ~600 preference reads per
      second at 120Hz**, allocating a `SemBlurInfo` each time. `SamsungBlur`'s own comment says
      reflection per frame at 120Hz is worth avoiding; the lookups were cached, the invocations were
      not. Now: `blurRadius` mirrors the preference and is re-read only in `onSharedPreferenceChanged`,
      and `paneBlurRadius`/`paneBlurCorner` hold what each pane was last actually given, so a pane
      that has not changed costs nothing. Cleared is `-1` rather than a radius of zero, which is a
      different answer and has to survive a re-apply — so every clear goes through `clearBlur(index)`
      and no `SamsungBlur.clear` is called behind the cache's back.
- [x] **P2.** `pane.layoutParams = bounds` was assigned unconditionally — a layout pass per pane per
      frame even when the size is unchanged. Assigned only when width or height actually differs.
- [x] **P3.** The blur spec was re-parsed with `split`/`mapNotNull` per pane per frame. `paneSpec` holds
      the region string each pane was last placed from, so a pane that reads identical is skipped
      whole — no parse, no layout, no blur. The page's own dedupe cannot do this: it compares the
      entire spec, so one bubble moving re-sends all five. `clearBlur` forgets the region, which is
      what keeps it honest at screen-off, where the host clears the panes behind the page's back.
- [x] **P4.** `getComputedStyle` allocated a fresh declaration per blob per frame. The declaration is
      *live*, so it is resolved once per blob and re-read.
- [ ] **P5.** `blobs.map(...)` builds a new array and object per blob every frame. **Left alone
      deliberately**: it is six short-lived objects a frame against a refactor that touches every
      reader of `measured` (`settleSkin`, `sendBlurFrame`, the blob writes, `meltBy`, `renderMerges`)
      and replaces a `null` that means "not skinned" with a flag every one of them has to honour.
      Worth doing only if a profile on the phone says these allocations actually cost something.
- [x] **P6.** `stirLiquid`'s default is 700ms rather than 1,100 — past the longest transition an
      argument-less caller can start (a 460ms growth, a split of 380 after 150 of delay), where 1,100
      was past the longest *choreography*, and a choreography states its own length when it asks.
      `TRACE_MERGES` is off; it was building strings inside the frame loop it exists to measure.
      **Unverified on the phone:** a stir that ends before its transition freezes the glass part-way,
      so the close, the swap, the split and a mod being taken back in all need walking.

## Phase C — new bubbles and mods

One step each, one push each, naming the feature.

- [x] **C1. Status bubble** — connectivity is its whole subject: bluetooth (device icon, charge %),
      USB, hotspot, wifi/mobile, signal, battery always rightmost. A Modus colours the whole bubble
      and puts its icon on the left. Active opens that connection's settings.
      `ConnectivityWatch` reads the default network, the USB state, tethering and what is paired;
      `js/status.js` draws it; a sixth blur pane and a fourth proxy carry it. Two honest limits,
      written into `states.md`: mobile draws no strength bars, because a cellular level needs
      `READ_PHONE_STATE` and that is a runtime prompt for a glyph, and a paired device's charge
      comes from the broadcast SystemUI's own meter reads rather than from the hidden method that
      is blocked. Active is the system's settings screen for what the bubble is reporting rather
      than a panel of ours — a panel that toggled radios would be a second settings app.
      **Not walked on the phone.**
- [x] **C2. Clock bubble** — top-left over the system clock; no mods; visible merge with Now.
      `js/clock.js`, a seventh blur pane, and **no window at all** — nothing about it is
      interactive, so it needs no proxy. Never overlapping Now is held as geometry: its
      `max-width` is capped against `--now-left` less `--clock-reach`, because the Now bubble
      may not move off the chip it covers and a cap cannot be forgotten the way a chosen width
      can. The tick is scheduled to the next minute boundary rather than on a 60s interval,
      which drifts. **Not walked on the phone.**
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

- [x] **C17. Strip the descriptions out of `panel.html`.** Every `<p class="note">` and
      `<span class="module-caption">` across all three screens (Settings, Debug, System) —
      clutter, not documentation the user reads. Keep the labels, rows, sliders and buttons;
      remove only the prose explaining them. The System screen (now the first tab, moved ahead of
      Settings and Debug) is the one with the most of them — five between the Access module and
      its caption. Done: every `<p class="note">` and both `<span class="module-caption">` are gone, along
      with the now-unused `.note b` rule. `.note` itself stays — the Touch module's `<pre id="trace">`
      is readout, not prose, and it is the one thing on the Debug screen that answers a question
      instead of explaining one.

- [ ] **C18. A message from the same conversation still replays the full alert arrival.**
      Reported on the phone: a second message from the same person while the alert is already up
      still pops the bubble out and in as if it had just arrived, instead of appending beneath the
      first line silently. The logic for this already exists and is meant to be built —
      `isSameConversation()` and `paintStack(notification, appending)` in `js/mods/notification.js`
      (C7 in this file, ticked done) — so this is a regression or a gap in that logic, not new work
      to design. `isSameConversation()` reads `notification.key`, falling back to `title` +
      `package`; the arrival animation only fires when `appending` is false. Two places to look,
      in order: whether `appending` is actually landing `false` for a real same-sender pair (the
      messaging app's `key`/`title` may not be as stable as WhatsApp/Telegram's usually are — a
      title that includes an unread count changes every message and breaks the fallback match), or
      whether something downstream still plays an arrival animation even when `appending` is
      `true` despite the check passing. `adb logcat -s IslandBubble` with a temporary log of
      `notification.key`, `title`, `package` and the resulting `appending` value on every `show()`
      call is the fastest way to see which one it is — needs the phone and a real second message
      from a real conversation, not the debug panel's stage buttons.

- [ ] **C20. The bubble goes opaque for a frame as a close lands.** Reported on the phone: exactly at
      the end of a closing animation, as the pill settles back onto its resting position, it briefly
      loses its transparency — it reads as the bubble re-rendering itself. Ruled out on the device:
      it is not the host's blur panes. The suspect is the `html.liquid` handover, which is the one
      thing that trades at that moment: `paintSize` takes the skin off the frame a *growth* starts,
      because a bubble bigger than the goo layer's filter region loses its skin anyway, and
      `settleSkin` is the only thing that puts it back — measured, at the frame the shape has come
      home. So for the whole close the bubble paints its own background with the skin also drawing,
      and at the landing frame the two swap over. Whether the flash is one frame of *both* or one
      frame of *neither* is what the two temporary `console.log` lines already in the tree answer —
      `settleSkin` in `js/liquid.js` and the `liquid off` line in `paintSize` in `js/row.js`. Walk one
      open and close with `adb logcat -s IslandBubble`, then pull both lines back out; they are
      diagnostics, not documentation.

- [ ] **C19. A bubble that can be carried.** Today the pill is fixed at the punch hole and the only
      thing a drag does is rubber-band it back. The ask is the chat-head behaviour Google's and
      Samsung's message bubbles have: pick the bubble up, move it anywhere on the screen, and let it
      stay where it was put — the bubble as an object on the screen rather than a fixture of the
      status bar.

      It is not a gesture change, it is a change to what the bubble *is*, so name what it collides
      with before writing any of it:

      - **The window model.** The canvas is already the whole screen and untouchable, so a bubble
        carried down to the middle of the display is still drawn — and still liquid, since it never
        leaves the one surface. What does not follow it is the goo layer's filter region, which is
        deliberately bar-sized: a bubble outside it silently loses its skin and paints its own
        background, which reads as a colour bug. The region has to travel with the carried bubble,
        the way the lock bubble's does.
      - **The touch proxy is the real cost.** A proxy is exactly as big as what is interactive, and
        every pixel it covers is a pixel the shade swipe cannot start on. A bubble parked at the top
        of the screen keeps that rule cheap; one being *dragged* needs a proxy that follows the
        finger, and the drag has to be able to leave the top strip without the gesture being handed
        back to SystemUI mid-move.
      - **Where it rests is not free either.** Google's and Samsung's bubbles snap to an edge and
        park half off-screen; parking anywhere at all leaves a touchable window sitting over an app
        the user is trying to use. Decide the resting rule (edge-snap with a dock, or free
        placement) before building the drag, because it decides how big the proxy ever has to be.
      - **The punch hole is still the origin, and probably still home.** A carried bubble is a
        bubble that has been taken somewhere; the states that mean something at the cutout — Alert
        arriving, the Now row standing aside, satellites a side — have to say what they do while it
        is somewhere else, or the answer is that it returns home for them.
      - **Persistence.** Whether a carried position survives a screen-off, a rotation and a service
        restart is part of the feature, not a polish pass.

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
