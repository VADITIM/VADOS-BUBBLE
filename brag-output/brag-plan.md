# Brag Plan: Dynamic Bubble

## What is this app?
A system overhaul for One UI: the separate pop-ups, chips and indicators One UI scatters across the screen are replaced by one liquid bubble at the camera cutout that grows, splits and reshapes around whatever the phone is doing.

## The angle
Stock One UI answers every event with its own piece of interface — a media chip, a call chip, a timer, a heads-up card dropping over whatever is open. Dynamic Bubble answers every event with the same one. The video *performs* that fold: the scattered stock pieces arrive one by one, then get pulled into the punch hole as liquid, and from then on everything — a song, a timer, a message — comes out of the same shape and goes back into it. The claim is the README's own: "Nothing appears from nowhere."

## Hook (first 2-3 seconds)
A Galaxy S25's status bar, blown up. Stock One UI pieces land one by one on the beat — media chip, call chip, timer chip, then a heads-up card drops over everything. Left column: "Every event gets its own piece of interface." At 3.70s (strong cue) all of it is sucked into the camera cutout as liquid and becomes one black pill.

## Key moments (the middle)
- The song takes the resting bubble: it widens, album art on the left, the three-bar indicator on the right moving with the actual music.
- A timer starts: the bubble pulls in and a satellite splits off through a liquid neck — the sweeping-hand glyph in its own circle. "When a timer runs as well, the two share it."
- A message lands: it grows *out of* the bubble (300×155dp alert, app-accent hairline border) instead of dropping from the top. Then a swipe up returns it to the row.

## Outro / punchline
Everything settles back to the row. Closing line, verbatim from the README: "A phone that feels alive, not one that feels decorated." Then the name, DYNAMIC BUBBLE, with "V/AS · Galaxy S25 · One UI · sideload only".

## User flow worth showing
Song playing (closed mod) → timer starts (satellite split) → notification arrives (alert grows from the bubble) → swipe up (back to rest). This is the working app, recreated from `pill.html` / `pill.css` values.

## Tone
- Preset: polished
- Creative direction: quiet premium system film — the phone as an instrument, not an ad
- Interpretation: few words, long holds, one idea per beat; motion arrives slowly and diagonally and leaves fast (V/AS), transitions are continuous morphs of the one bubble rather than cuts.

## Format: landscape — 1920x1080
## Duration: 22s

## Visual identity (from the project)
- Background: #181818 (`--ground`); phone screen #0e0e0e (`--sunken-deep`)
- Accent: #5bfd5b (`--section-color`, terminal green)
- Text: rgba(255,255,255,0.87) (`--text-primary`), labels #8a8a8a (`--text-label`)
- Bubble: #000 fill, 1px #262626 hairline (`--border`), fully round; alert radius 24dp
- Display font: Clash Display (Semibold/Medium, shipped in `assets/fonts/ClashDisplay`)
- Body/label font: Space Mono — uppercase micro-labels with 0.19rem tracking
- Strongest visual element: the liquid goo merge (`#bubble-goo`: blur 2, alpha ×30 −14) at the cutout

## Share copy (draft)
I replaced One UI's pop-ups, chips and heads-ups with one liquid bubble at the camera cutout. Nothing on my phone appears from nowhere anymore.

## Audio direction
- Role: cinematic support, restrained
- Music: happy-beats-business-moves-vol-11 (114.84 BPM)
- Music treatment: from 0s, moderate bed, fade out over the last ~1.5s under the name card
- Music cue guidance: preset read (`cues/...vol-11...music-cues.md`). Strong cues: 3.70s (merge into one bubble), 8.96s (timer satellite splits), 12.65s (notification grows out), 17.91s (outro line). Beat grid 1.60 / 2.12 / 2.65 / 3.18 for the four stock pieces landing (non-text accents — every beat is fine).
- Audio-reactive treatment: subtle; the bubble's own three-bar equalizer is driven by the music's bands (it is product UI that in the app only moves while sound plays), and the green glow behind the phone breathes with RMS. No other visualizer.
- SFX posture: sparse, motion-matched
- Audio-coupled moments: four soft placements for the stock pieces; one soft impact on the merge; soft drop when the satellite splits; light plate/glass on the notification; soft whoosh-free return on swipe up.
- Restraint rule: no SFX on text reveals; no bells except possibly the final name.

## Storyboard

### Scene 1 — Stock One UI — 0.0–3.7s
Phone top section on the right, status bar at 3.2×: clock 18:42 left, icons right, camera cutout centre. Stock chips land one by one; heads-up card drops. Left column: micro-label "STOCK ONE UI", headline "Every event gets its own piece of interface."
Sequential/interaction: yes — 4 pieces on beats 1.60 / 2.12 / 2.65 / 3.18
Audio intent: tidy, a little crowded
Audio-coupled idea: card-place per piece
Transition mood: liquid — everything drains into the cutout at 3.70 → Scene 2

### Scene 2 — One component — 3.7–8.96s
Pieces merge into the pill (beat-locked 3.70). Headline swaps to "One component at the camera cutout." At ~5.8 the song arrives: bubble widens, art left, three bars right. Title block: "DYNAMIC BUBBLE" + "A system overhaul for One UI."
Sequential/interaction: none
Audio intent: exhale, settle
Audio-coupled idea: impact on merge; bars follow the music
Transition mood: continuous → Scene 3

### Scene 3 — The row, and a message — 8.96–17.9s
8.96 (beat-locked): satellite splits off right with the timer glyph; caption "When a timer runs as well, the two share it." 12.65 (beat-locked): alert grows out of the bubble — "MESSAGES" in accent, sender "Mara", text "Train's four minutes late. Grab me a coffee?" (fictional stand-in). Caption "Nothing appears from nowhere." ~15.9 swipe up: alert returns to the row.
Sequential/interaction: yes — satellite split, alert growth, simulated swipe up
Audio intent: precise, confident
Audio-coupled idea: drop on split, light plate on alert, soft slide on swipe
Transition mood: soft → Scene 4

### Scene 4 — Outro — 17.9–22.0s
"A phone that feels alive, not one that feels decorated." (beat-locked 17.91), then DYNAMIC BUBBLE + "V/AS · GALAXY S25 · ONE UI · SIDELOAD ONLY". Bubble keeps idling in the row.
Sequential/interaction: none
Audio intent: resolve, fade
Transition mood: hold

**Music mood for this video:** upbeat but restrained
**Audio summary:** a light bed that ticks with the stock pieces, exhales on the merge, and fades under the name.

Privacy: the notification sender and text, song title and art are fictional stand-ins; no real contacts or content from the phone.
