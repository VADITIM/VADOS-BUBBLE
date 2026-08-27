# Dynamic Bubble — backlog plan

## Context

You handed over a ~40-item backlog note covering everything from live bugs to whole new bubbles, and asked for a step-by-step development pass at iOS-level interaction quality, with the note itself preserved in `.claude/` so nothing is lost.

Three things shape the plan:

1. **`pill.html` is 6,643 lines** and nearly every item edits it. `architecture.md` already specifies the target split (`pill.css` + ES modules under `assets/js/`) and calls it "not built yet". You chose to refactor first, so all ~40 items land in modules.
2. **Most bug reports are symptoms of the same few contract violations.** The swipe bounce, the fragile haptic, the clipped overshoots and the alert hue are not four unrelated bugs — they are `transition:` rules written wholesale over a shared property, which `motion.md` already records twice as the recurring trap. Fixing the cause fixes siblings.
3. **I cannot build or verify here.** The JDK and Android SDK exist only on the machine the phone is flashed from; this checkout can read and edit but cannot `assembleDebug` or `adb install`. Every step ships for you to walk on the device. I will never report an animation as working — only that it compiles by inspection.

---

## The bubble inventory (asked for before anything else)

**Bubbles — a place on screen that can hold a mod.**

| Bubble | Where | Built | Owns mods | Notes |
|---|---|---|---|---|
| **Main** | At the punch hole | built | yes | The bubble. Idle, Mod, Alert, Active, Haptic, Pull, Push |
| **Satellite** | Beside Main, one a side, two max | built | yes (borrowed) | A mod that is live but not the owner |
| **Hidden** | Past the second satellite | built | no | A coloured dot; a count, not a face |
| **Now** | Left end of the bar, at the clock | built | yes, its own set | Torch today; Recording, Download, Upload, Bluetooth, USB, Hotspot planned |
| **Lock Now** | Bottom of the lock screen | built, partly | yes (stolen) | Replaces the Now Bar. The bounciness reference |
| **Status** | Right end of the bar | planned | **no** | Battery, wifi/mobile, signal, Modus colour |
| **Clock** | Top-left, over the system clock | planned | **no** | Merges visibly with Now, never overlaps it |
| **Double** | Beside the punch hole, alongside Main | planned | no | Charging, low battery, recording ended. Magnetic/liquid against Main's edges |
| **Lock** | Where the lock icon is | planned | no | Indicator; opens then merges into Main on unlock |
| **Notification** | Over the lock screen's list | planned | no | Scrollable, overflow visible, no drag radius for now |

**Mods are content injected into a bubble, and they are strictly split by which bubble may carry them:**

| Mod | Eligible bubbles | Built |
|---|---|---|
| Media (Spotify etc.) | Main, Satellite, Lock Now | built |
| Clock/timer | Main, Satellite | built |
| Call | Main, Satellite | built |
| Battery | Main today → becomes the first **Double** | built, moving |
| Torch | **Now only** | built |
| Recording / screen share | Now only | planned |
| Download, Upload | Now only | planned |
| Bluetooth, USB, Hotspot | Now only | planned |
| Discord video | Main, Satellite | planned |
| DB Navigator | Main, Satellite | planned |

**The rule that binds all of them:** every bubble is bouncy, gooey, animated, smooth and state-driven; born at the punch hole; liquid with whatever it comes near; drawn on the canvas in `pill.html`; never its own window. Clock and Status are the two exceptions to owning mods, and both sit a few pixels shorter than the rest.

---

## Step 0 — Global config, and what we are running

Attach `VADITIM/.claude` via `add_repo`, clone branch `global` to a gitignored path, add it to `.gitignore`, and report the actual skills and plugins it carries. Right now this session has **no** `vados-dna` skill — `/root/.claude/skills/` holds only `session-start-hook` plus the Anthropic-synced set (docx, pdf, pptx, xlsx, md, skill-creator, study-modes, import-memory, code-anomaly, lore-anomaly), and `/root/.claude/plugins/synced/` is empty. So the VADITIM Style DNA that `STYLE-DNA.md` says binds this project is currently unavailable, and I would be working without it.

**Blocked as of this writing.** `add_repo` refuses the repository outright: a repo whose name begins with `.` would clone to a hidden path that could collide with `~/.claude`, so `VADITIM/.claude` cannot be attached to this session at all. It is a hard restriction, not a permissions problem, and retrying will not change it.

Three ways round it, in order of preference:

1. **Mirror the repo under a non-dot name** (e.g. `VADITIM/claude-config`) and attach that. One-time cost, works for every future session.
2. **Paste or upload `vados-dna/`** into this conversation — `STYLE-DNA.md` says that skill is the only part of the global config that binds this project, so it is the only part actually needed here.
3. **Continue without it**, working from `CLAUDE.md` plus the four rule files, and say so rather than guessing at the DNA's contents.

Until one of those happens, treat the VADITIM typography and motion rules as unavailable and defer to `.claude/rules/motion.md`, which is more specific anyway.

## Step 1 — Persist the backlog

- `.claude/backlog/SOURCE.md` — the note verbatim, replacing the older copy left in the working tree from the interrupted run.
- `.claude/backlog/PLAN.md` — every item as a numbered step with a status box, the phase it belongs to, and the contracts it touches. This is the file that gets ticked, and the only place progress lives.
- `.claude/rules/bubbles.md` — **currently missing**, though `CLAUDE.md`, `states.md` and `motion.md` all link to it. It owns: what every bubble is, the fluid-merging contract (your item: *"EVERY SINGLE BUBBLE IS SUPPOSED TO BE A FLUID"*), the bounciness contract, nothing-is-clipped, nothing-crosses-the-punch-hole, and the bubble/mod eligibility table above.

## Step 2 — The refactor (chosen: first)

Follow the split `architecture.md` already prescribes, and nothing more:

- `pill.html` → markup and the SVG filter definitions only.
- `pill.css` beside `dna.css`.
- `assets/js/` ES modules loaded with `<script type="module">` from `file:///android_asset/`, split by concern: `liquid.js` (mirror, goo, blur frames), `motion.js` (toy/untoy, catchInto, springs), `row.js` (layout, satellites, dots, swap), `main.js`, `now.js`, `lock.js`, `mods/*.js`, `bridge.js`.
- No factory, no event bus, no configuration layer. One `Bubble` type is the whole abstraction.

**This step changes no behaviour.** It ships on its own so that if the phone shows a regression, the diff to search is a move, not a move plus a feature.

---

## Phase A — The broken things

Ordered so causes land before symptoms.

**A1. The transform-transition leak — fixes items 2, 8, 9, 30 (partly).**
`html.homing #pill` gives `transform` a 520ms transition on `--ease-split` (`cubic-bezier(0.2, 1.7, 0.35, 1)` — a 1.7 overshoot). `homing` is added by `untoy()` and `endPull()` and removed only by `toy()`. `untoy()`'s guard reads the inline `--drag-x`, which is written as `"0px"` and never removed — so after the *first* play-drag of the session, every later touchend re-adds `homing` and `#pill`'s `transform` stays permanently sprung.

`#pill`'s `transform` also carries `--absorb`, the merge squash the mirror writes **every frame**, and `--pull`. Driving a per-frame value through a 520ms overshooting spring is exactly your *"the whole main bubble bounces and teleports to random positions"*. It is the third recurrence of the trap `motion.md` records under *"A transition list is a contract"*.

Fix at the cause: the springback home becomes a Web Animations API animation with `composite: 'add'` — which is what `motion.md` prescribes anyway — so `transform` never gets a transition at all, and `homing` disappears as a concept. Same for `#lock-now.homing`. Then `--drag-x/-y` are *removed* at rest rather than set to `0px`.

Secondary in the same area: `enterSide` is never reset after use, so a mod hand-over that was *not* caused by a swipe still plays `glyph-bump` with a stale side instead of `glyph-enter`. Reset it to `'hole'` once consumed, so only the icon animates and only when a swipe caused it.

**A2. Haptic fragility and additive scale (items 8, 9).** With A1 in place the hold's `scale` no longer competes with a sprung `transform`. Audit the remaining `transition:` wholesale writes (`.dragging`, `.pulling`) against the named-list contract, and confirm the swell composes onto a live drag rather than replacing it.

**A3. Nothing is clipped (item 10).** 14 `overflow: hidden` rules in `pill.html`. Each one that is on a bubble becomes `visible`, and the window room an overshoot needs is claimed *before* the animation, per `motion.md`. Faces that genuinely must clip (the art, the timeline) keep it on an inner element instead.

**A4. Alert close bugs (item 22) and the un-blurred close (item 34).** The accent hue on close and the alert frozen mid-animation, worse when locked; and `closing an expanded bubble back to idle renders the whole bubble without alpha and blur`. Both point at the skin/`stirLiquid` coverage not spanning the close transition. Verify `stirLiquid()` is stirred for the full length of every close, and that `html.liquid` blanking is not lifted mid-flight.

**A5. Abrupt mod close (item 21).** A mod that ends because its app was killed currently vanishes with no animation. The watchers already push `null`; the page needs to treat a disappearance as a departure — width back first, then the glyph leaves — rather than as a repaint.

**A6. Punch-hole margin and Discord alignment (item 5).** Names right-aligned, expanding right-to-left, stopping at a named margin either side of screen centre with a fade — never an ellipsis, never a hard cut. Applied as one rule to every resting state, not to Discord alone.

**A7. Small, contained fixes.** Satellite-Spotify icon radius (item 26); Satellite-Clock continuing its tick across the swap rather than re-rendering (item 27); everything I control in English (item 6); notification age in minutes (item 32); blurs missing on the locked Now bar at wake (item 19); media timeline drag not stealing the bubble's haptic (item 24).

**A8. New default settings (item at the end of the note).** `Preferences.defaults` → width 85, height 30, horizontal 0, vertical 3, R/G/B 0, alpha 80, blur 60, identity Both(2). Currently 130/34/0/12/0/0/0/100/0/2.

---

## Phase B — System-wide contracts

**B1. Bounciness everywhere (item 1).** The Lock Now bubble is the reference: `scale 320ms var(--ease-split)` overshoot, `.pressed { scale: 1.03 }`, `border-radius` + `corner-shape: squircle`. Main, Satellite, Now and Status adopt the same curve family and the same press response. Written into `bubbles.md` and `motion.md` as binding, not just applied.

**B2. Fluid merging everywhere (items 3, 12).** The mechanism exists and is good — one goo layer, `meltBy()` reading deviation off the measured gap, `catchInto()` measuring overlap rather than scheduling. What is missing is that it is not applied to every pair. Audit which bubbles are in the goo layer's mirror and which are not, and make Now↔Main, Clock↔Now and Lock↔Main merge the same way Main↔Satellite already does. Record the contract in `bubbles.md`.

**B3. The debug stage (item 4).** `DebugStage.kt` exists. Extend it to drive **every** state with a one-tap example that builds its environment first: notification arriving, satellite swiped to main, satellite closing, mod closing to idle, alert with image, unlock merge, charge overlap, and so on. Explicitly never ticked — it grows with every new state.

**B4. Drag radius (item 28).** Double `DRAG_RADIUS` (20 → 40) and add a threshold constrainer at the current 80%: crossing it during a drag resets the haptic to idle and prevents it firing.

**B5. Goo strength slider (item 17)** and per-bubble width settings (item 25) in the panel.

---

## Phase C — New bubbles and mods

Each is one step, each ends with a push naming the feature.

- **C1. Status bubble** (item 16) — right end of the bar; battery, wifi/mobile, signal, Modus colour and icon.
- **C2. Clock bubble** — top-left over the system clock; no mods; visible merge with Now, never overlapping.
- **C3. Double** (item 12) — charging leaves the Main row and becomes the first true Double, magnetic and liquid against Main's edges when they overlap.
- **C4. Now mods** (items 18, 20, 23, 33) — Recording (red pill, white content, tap pause/resume, haptic panel), Download and Upload (animated glyph, timeline, size and speed, done-tick then merge), Bluetooth (device icon, charge %), USB, Hotspot. Per-mod widths; Torch narrowed. Settings toggle for whether Now pushes the left satellite aside.
- **C5. Now geometry** (item 13) — Now covers the same width/position/sizes Spotify does; swipe up dismisses it to the punch hole with the Lock-Now-derived bounce.
- **C6. Alarm state** (item 11) — full screen with the top margin, orange, circular squircle buttons at the bottom.
- **C7. Alert stacking** (item 7) — a second notification from the same person inside the alert window appends and slides in from the bottom under a fade, growing the bubble downwards *animated*, resetting each time until close.
- **C8. Spotify polish** (item 14) — dithered hue from the bottom to ~35%, dot at the timeline head, squircle button borders.
- **C9. Discord video** (item 15) and **DB Navigator mod** (item 23) and **TimeTree type** (item 27) and media speed-up (item 23).
- **C10. Lock screen** (items 12, 20, 29) — the Lock icon bubble, rubber-band response to unlock swipe progress, real Notification bubbles in a scrollable overflow-visible container, no drag radius yet.
- **C11. Notification quick settings** (item 31) — needs the two screenshots; they are referenced by the note but were not attached, so this step starts by asking for them.
- **C12. Status bar replacement** — the one-button set/reset of Samsung settings, and an option to disable the status bar entirely. Needs the four Settings screenshots, also not attached.
- **C13. Rendering order** (item 30) — the bubble always drawn at the lowest index system-wide so system notifications never overlap it, without suppressing them.
- **C14. Blur over content** (item 35) — an overlapped bubble's content is not inside the blur. The panes are host `View`s *behind* the WebView, so a pane cannot blur WebView pixels. This one is a research step with an honest answer, not a promised fix.

---

## Working rules for every step

- One item at a time. Name the contracts it touches (state, motion, window, blur, mirrored constant) before editing.
- Docs change in the same task — `bubbles.md`, `states.md`, `motion.md`, `architecture.md` as applicable, never afterwards.
- Tick the item in `.claude/backlog/PLAN.md` only once you confirm it on the phone.
- **Push after every feature/fix**, commit message naming exactly what the feature is, to `claude/ios-dev-plan-progress-xnyhqr`.
- Fix the cause, not the caller — the same bug is usually live in every sibling routing through the same function.

## Verification

I cannot run it. For each step I will state what changed and what is unverified; you walk it on the device:

```
export JAVA_HOME="/c/Program Files/Java/jdk-21.0.11" && ./gradlew assembleDebug
adb connect 192.168.0.83:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s IslandBubble
```

Per-phase walk: **A1** drag the bubble sideways, release, and confirm only the mod's icon bumps and its force tracks release speed — then repeat after ten drags, since the old bug only appeared after the first. **A3** watch every overshoot to its end for a clipped edge. **B1** press each bubble in turn and confirm one press response. **B3** the debug stage is itself the verification harness for everything after it.
