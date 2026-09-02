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
| **Double** | Beside the punch hole, alongside Main | built | no — charging, low battery, recording ended |
| **Lock** | Where the lock icon is | built | no — opens, then merges into Main on unlock |
| **Notification** | Over the lock screen's list | built | no — scrollable, no drag radius yet |

| Mod | Eligible bubbles | Built |
|---|---|---|
| Media | Main, Satellite, Lock Now | built |
| Clock/timer | Main, Satellite | built |
| Call | Main, Satellite | built |
| Battery | **Double only** — it is not a mod | built |
| Torch | **Now only** | built |
| Recording, Download, Upload | **Now only** — what is *happening* | built |
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
- [~] **B3. Debug stage — every state.** Extended again with everything Phase C added: the three
      new Now mods, the Status bubble with and without a Modus, the alarm, and a staged lock
      screen (`stageLock`, which tells the page the keyguard is up without touching `isLocked`,
      so the padlock, the notification bubbles, the bottom Now bubble and the merge home can all
      be walked without locking the phone). The recorder's and the alarm's buttons carry their
      real titles, because what a tap does is decided by matching those titles.
      **Still not everything, and still not ticked**: the swap, the pull and the hold are
      gestures, and a stage cannot press a finger to the glass. Those three need either a
      synthesised touch stream or a hand on the phone, and a stage that pretended to cover them
      would be worse than one that says it does not.
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
- [x] **C3. Double** — charging leaves the Main row, magnetic against Main's edges when they overlap.
      `js/double.js` and an eighth blur pane; `mods/battery.js`, the battery face, the
      `battery` size and the row's `battery` class are all gone. Magnetism is measured rather
      than placed: it rides the main bubble's right edge every frame it is out, so an alert
      opening pushes it along instead of being drawn over it, and the same measurement is what
      lets the two neck. It costs no window — nothing about it is interactive. Home is a merge
      through `catchInto`, not a fade. **Not walked on the phone.**
- [x] **C4. Now mods** — Recording (red pill, white content, tap pause/resume, haptic panel, elapsed),
      Download and Upload (animated glyph, timeline, x/x MB and speed, done-tick then merge). Per-mod
      widths; Torch narrowed. Settings toggle for whether Now pushes the left satellite aside.
      `NowWatch` reads both off ongoing notifications; `setNowMod` in `now.js` is the one place a
      Now mod's life is written, and torch is now one of the set rather than the special case the
      file was built around. Two things are honest rather than pretended: **"Torch narrowed" is
      bounded by `NOW_COVER`** — below it the bubble uncovers the bar it exists to stand on, so
      the torch is the narrowest of the four and no narrower — and the **x/x MB and speed line is
      the app's own text**, not a parse, because every app writes it differently and a wrong parse
      is worse than the app's words. Indeterminate progress bars are left in the shade: there is
      no honest timeline for an app that says it does not know. The toggle is `nowPushesRow`,
      default on, and it governs both the row's shift and its satellite limit. **Not walked on
      the phone** — in particular the recorder package names and the pause/resume button titles
      are the two things only the device can confirm.
- [~] **C5. Now geometry** — same width and position Spotify covers; swipe up dismisses to the punch
      hole on the Lock-Now-derived bounce.
      **The swipe is built**: up past `NOW_SWIPE` sends the bubble home on the lock bubble's
      two-speed flight, ending on the centres rather than on a timer, and the dismissed mod stays
      dismissed until it really ends. **The geometry is half done and says so**: both numbers that
      describe what the bubble covers — `setNowLeft` and `setNowCover` — now come from the host,
      so there is one place to put the right values, but *what* those values are is a measurement
      off One UI's own Now Bar chip and can only be taken on the phone. Tick this when the two
      numbers have been measured there.
- [x] **C6. Alarm state** — full screen with the top margin, orange, circular squircle buttons.
      `AlarmWatch` tells a ringing alarm from everything else the clock app posts by its
      full-screen intent, which is the platform's own way of saying "this cannot wait"; calls
      are excluded by name because they have a bubble already. It is a **state of the main
      bubble**, not a bubble of its own — same shape, same corners, arriving out of the row —
      and it is the one state that costs the shade swipe, because while it rings there is
      nothing else to be doing. The buttons wear the clock app's own words and fire its own
      actions, and pressing stop does not close the bubble: the alarm is over when the clock
      app takes its notification away. **Not walked on the phone.**
- [x] **C7. Alert stacking** — a second message from the same conversation appends beneath the
      first instead of announcing itself as a new alert. Built off `lines`, which the payload
      already carried: a messenger posts one notification per conversation and rewrites it as
      each message lands, so the conversation was in the page all along and only the last of it
      was being drawn. The bubble does not grow — the stack is anchored to its bottom edge once
      it outgrows the alert, so the oldest is pushed up and off the top under a fade, which is
      what was asked for over a growing box.
- [x] **C8. Spotify polish** — dithered hue bottom to ~35%, dot at the timeline head, squircle buttons.
      The hue is the album's own colour poured up from the bottom of the media tab and gone by a
      third of the way, drawn *behind* everything — a ground, not a tint over the artwork — and
      dithered with a sub-pixel noise mask, because a gradient this long over this few stops
      bands on an OLED exactly where it is faintest. The dot rides the head of the filled part
      rather than being placed from the ratio a second time, which also meant taking
      `overflow: hidden` off the track — it was clipping its own marker. The transport wears
      squircles: the rule keeping squircle off the bubbles is about bubbles, and a button is a
      square with a radius. **Not walked on the phone**, and the dither in particular is a thing
      only the panel can judge.
- [~] **C9. Discord video**; **DB Navigator** (Zugnummer/remaining while travelling, Gleis/departure at
      an Umstieg; tap expands the journey, tap again opens the app, haptic goes to the Abo tab).
      Eligible for Lock Now too, so it joins `LOCK_STEALS` and needs a face there. Starts with
      checking whether a saved journey is readable at all. Plus **TimeTree** as a named type
      (green/white) and media speed-up for Telegram/WhatsApp voice notes.
      **The two small halves are built.** TimeTree is in `AppStyles`. The speed control is a
      transport button that only exists for Telegram and WhatsApp — a voice note is the one kind
      of playback where the rate is worth a control, and a speed button on an album is an option
      nobody asked for on every song. It cycles 1 / 1.5 / 2 and resets when the session changes
      underneath it. The session either honours `setPlaybackSpeed` or ignores it and there is no
      way to ask beforehand, so the button offers it and the player answers — **which of the two
      messengers actually honours it is a device question.**
      **The two large halves are not started, and both begin on the phone.** DB Navigator's item
      says so itself: it starts with finding out whether a saved journey is readable at all, and
      the only way to know is to look at what the app posts while travelling. Discord video is
      the same shape — whether a stream or camera being live is visible in the notification at
      all decides whether there is a mod to build. Neither is worth guessing a payload for.
- [~] **C10. Lock screen** — the Lock icon bubble; rubber-band response to unlock swipe progress;
      real Notification bubbles in a scrollable overflow-visible container.
      **Two of the three are built.** The padlock (`js/padlock.js`, a ninth blur pane, no window)
      opens and *then* flies into the Main bubble as a measured merge — open first, then leave.
      The notifications (`js/notes.js`, a fifth proxy) are one bubble per conversation in an
      overflow-visible container whose inner scroller is what clips, with the overflow counted
      rather than listed.
      **The rubber-band is blocked and should be read as blocked, not deferred**: nothing exposes
      the keyguard's own swipe progress to an unprivileged app — no callback, no inset animation,
      and our windows do not receive those touches. It stays a wish until something on the device
      turns out to publish it.
      Two numbers are measurements only the phone can settle: `PADLOCK_BOTTOM` (where One UI
      draws its lock icon) and `--notes-top` (where its notification list starts).
      **Not walked on the phone.**
- [ ] **C11. Notification quick settings** — **blocked**, the two screenshots were never attached.
- [ ] **C12. Status bar replacement** — one-button set/reset of the Samsung settings. **Blocked**,
      the four screenshots were never attached. Disabling the bar itself is its own step now —
      see C15, last in this phase, because it depends on Status and Clock existing first.
- [~] **C13. Rendering order** — the bubble always at the lowest index system-wide so system
      notifications never overlap it, without suppressing them.
      **Answered, and the answer is that the z-order half cannot be had.** Window order between
      *different* apps is the window manager's, keyed off the window type, and an unprivileged app
      picks its type from a very short list. `TYPE_ACCESSIBILITY_OVERLAY` is already the highest
      one available — that is why the bubble is an accessibility service at all — and SystemUI's
      heads-up banner is drawn in a layer above every one of them. There is no ordering call: an
      accessibility overlay has no z to set, and windows of one type from one app stack in the
      order they were added, which is why nothing in this project reorders windows any more.
      One avenue is real but is not this item: `attachAccessibilityOverlayToDisplay` (API 34)
      takes a `SurfaceControl`, and a surface has a settable layer. That places our surface
      *within the accessibility overlay layer* — it does not lift it above SystemUI's — so it
      would change nothing about a heads-up banner while costing the whole window model. Worth
      knowing about, not worth trying for this.
      So the only lever that exists is the one already built: the heads-up itself is switched
      off through Shizuku (`HeadsUp`), which is suppression — the thing this item asked to avoid.
      It is also honest suppression rather than a hack: the notification still lands in the shade
      and still reaches the listener, only the banner is gone. **What can still be built** is the
      other half of the sentence — the bubble getting out of the way rather than being covered:
      when a banner is on screen the row could stand aside for it the way it stands aside for the
      Now bubble. That needs the phone to say when a banner is up, and with pop-ups suppressed
      there are none to react to, so it waits behind C12.
- [x] **C14. Blur over content** — an overlapped bubble's content is not inside the blur. The panes
      are host `View`s *behind* the WebView, so a pane cannot blur WebView pixels. **Research step
      with an honest answer**, not a promised fix.
      **The answer.** The diagnosis in the item is right and it is structural: a pane blurs what is
      *behind it in the window*, our content is drawn by the WebView *above* the panes, so no
      arrangement of the existing pieces puts our own pixels inside our own glass. Three ways out
      were considered and two are dead:
      1. **Move a pane above the WebView.** It would blur our content, which is the opposite of
         what is wanted: the bubble underneath would be smeared rather than seen through glass.
      2. **Two WebViews, one under the panes and one over.** That splits the surface, and a goo
         layer reaches exactly as far as its own surface — it would trade every merge in the
         project for one blur. Not a trade, a demolition.
      3. **Blur our own content inside the page.** `backdrop-filter` on an element blurs its
         backdrop, and *within a page* that backdrop is the rest of the page: a bubble drawn over
         another bubble can blur it, in the page, with no host involvement at all. The host pane
         stays exactly as it is and goes on blurring the screen behind the window. The two are
         different blurs of different things and they compose, because they are at different
         levels of the stack.
      So the honest answer is: the host can never do it, and it does not have to — the only case
      that is missing is *our content over our content*, and that one is CSS. It is written down
      rather than built because nothing today actually overlaps: the row's bubbles neck rather than
      stack, and the states that cover the bar are drawn instead of the row rather than over it.
      The first thing that genuinely overlaps — the Double landing on an open alert is the likely
      one — is what should carry it, and it should carry it as `backdrop-filter` on the shape on
      top, not as a new pane.
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

- [x] **C19. A bubble that can be carried.** Built. Past `CARRY_GRAB` a drag stops being a rubber
      band and becomes a carry: the bubble comes off its spot, follows the finger one to one, and
      stays where it is let go of. Under the threshold nothing changed, so the gesture is additive
      rather than a mode the bubble is put into.

      Each collision the item named, and what answered it:

      - **The window model.** The goo layer's region travels with the bubble, as a band around
        where it stands rather than the whole screen — the whole screen is exactly the cost the
        bar-sized region exists to avoid.
      - **The touch proxy.** It follows, and it is placed on release rather than per frame:
        moving a window under a live finger is the cancel trap the hold already knows about.
      - **The resting rule.** Free placement, decided *before* the drag was written because it
        decides the proxy's size — an edge-snap dock needs a proxy the size of the dock, and a
        bubble parked half off-screen needs one hanging over the edge. Free placement keeps the
        proxy exactly as big as the bubble has always been, so carrying costs no extra pixels of
        shade swipe, only different ones. Let go within `CARRY_HOME` of the hole and it is home.
      - **The cutout states.** Anything past a mod is fetched home for and given its place back
        afterwards, applied once in `setSize` — the alternative is re-deriving every geometry in
        the row against a moving origin.
      - **Persistence.** `carriedX` / `carriedY` in `Preferences`, pushed back to the page on
        start.

      **Not walked on the phone, and this is the item that most needs it**: the threshold, the
      one-to-one follow and how a carried bubble sits over a real app are all feel.

## Content marking

Added 2026-08-28.

- [x] **Time and dates highlighted like a marker, light blue.** `.time` in `pill.css`, `TIME_PATTERN`
      in `js/mods/notification.js`. German formats, since the phone is German: `27.08.2026`, `27.08.`,
      `14:30`, `3. September`, ISO. Deliberately only concrete tokens — "heute" and "morgen" are time
      words too, but a highlighter drawn over every one of them stops meaning anything.
- [x] **Money highlighted like a marker, yellow.** `.money`, `MONEY_PATTERN`. Symbol either side,
      because both are written: `7,80€` and `$5.00`. The symbol is part of the match — a bare number
      is not money.
- [x] **Google apps in Google colours, except Gmail.** `AppStyles.kt`. Calendar, Drive, Photos,
      Keep and Maps now wear the same treatment Wallet does — each app's own primary as the flat
      accent for the nine places in `pill.css` that can only take one colour, and the shared
      four-colour gradient for the two that can take more. Gmail is off Google yellow and on its
      own product red: of all the Google apps it was the only one already wearing a Google colour,
      and it is the one that was asked not to.
      **This settles the open question below as reading 2**, which is what Wallet had already
      shipped as — accent *and* gradient, rather than accent alone. It is five lines to revert if
      the other reading is wanted.

### The Google item's open question, as answered

Answered by what shipped: reading 2, accent plus gradient, which is what Wallet was already
carrying. The paragraphs below are kept because the *measurement* in them is still the reason —
`--app-accent` is read into `color`, `border-color`, `background` and `border` at nine sites, and
a gradient cannot go into `color` or `border-color` at all. That is why the treatment is two
values rather than one, and why any future identity that is genuinely several colours has to
carry both.

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
