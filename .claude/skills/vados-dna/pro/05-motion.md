# PRO 05 — Motion

Overrides [`../dna/05-motion.md`](../dna/05-motion.md). Read that first, all of it. This is the
module where the two registers actually differ, and the difference is **not** "less animation" — it
is *where the animation is spent*.

## What is kept whole, and is not negotiable

These are the DNA. Softening them produces a generic admin panel, which is the failure mode PRO
exists to avoid:

- **§1 Intent.** Four intents — arrival, departure, life, consequence. No name, no animation.
- **§2 The three-phase law.** Enter required, idle optional, leave required. The pairing rule in all
  three of its statements.
- **§4 The asymmetry law.** Leave fires at `0`. A leave is never a reversed enter. No delayed exit,
  no staggered exit, no `timeline.reverse()`.
- **§5 Absolute time positions**, never `>` chaining.
- **§7 Engine discipline**, every bullet. The transition-lease rule and the additive-gesture rule are
  *more* relevant here, not less — a tool has more concurrent state than a portfolio does.
- **§10 The checklist.**

## The register change, in one sentence

**The base DNA animates the screen; PRO animates the event.**

An arrival in the base register is a performance: half a second of anticipation, a diagonal assembly,
a curtain. In PRO an arrival is an acknowledgement — the screen is simply *there*, having taken a
fifth of a second to confirm it changed. The budget freed up is spent entirely on **consequence**:
the button that was pressed, the toggle that flipped, the row that saved, the dialog that opened.
Those keep their full punch, and are the only places the overshoot survives.

If a PRO app feels flat, the mistake is almost always that consequence was flattened along with
everything else. Flatten arrival. Never flatten consequence.

## 3. Timing — the PRO table

Replaces the base table wholesale. Roughly half, and the ceiling matters more than the exact values:
**nothing an operator waits for exceeds `0.32s`.**

| Constant | PRO | Base | Meaning |
|---|---|---|---|
| `ENTER_DELAY` | **`0.12s`** | `0.5s` | Enough to read as sequence, not as a wait |
| Enter — UI | **`0.18–0.28s`** | `0.35–0.6s` | Panels, controls, rows |
| Enter — backgrounds | **`0.24–0.32s`** | `0.45–0.95s` | |
| Leave | **`0.12–0.20s`** | `0.21–0.5s` | Still always shorter than its enter |
| Sibling stagger | **`0.02–0.05s`** | `0.03–0.12s` | **Capped at `0.15s` total across the group** |
| Multi-layer offset | `0.05s` | `0.1s` | |
| **Press / consequence** | **`0.09s`, `back.out(2.2)`** | — | The punch. Unchanged in character, shorter in time |
| Toggle flip | `0.22s`, `back.out(1.7)` | — | See the toggle primitive in [`06-interaction.md`](06-interaction.md) |
| Dialog open | `0.24s`, `power3.out` | — | |
| Dialog close | `0.16s`, `power2.in` | — | |
| Curtain (reserved) | close `0.28s` · hold to `0.5s` · open `0.3s` | `0.42 / 0.92 / 0.5` | Context switches only |
| Hover / state | `0.15–0.2s ease` | `0.2–0.25s` | CSS, not the engine |
| Idle drift | **none** | `~3s` | Dropped as a default — see below |
| Typewriter | **not used** | `0.06s/char` | A tool does not type at its operator |

**The stagger cap is the rule that does the most work.** A `0.05s` stagger over 40 table rows is two
seconds of the operator watching a list arrive. Cap the *total*, so the per-item value shrinks as the
list grows, and stagger the first 6–8 items at most — beyond that the group animates as one block.

```js
const per = Math.min(0.05, 0.15 / Math.max(items.length, 1))
```

## 4. Easing

| Phase | PRO ease | Base |
|---|---|---|
| Enter — everything structural | `power3.out` | `back.out` |
| Leave — everything | `power2.in` | `back.in` / `power2.in` |
| **Consequence — press, toggle, save, confirm** | **`back.out(2.2)`** | `back.out` |
| Dialog / panel open | `power3.out` | |
| Wipe / sweep | `power3.inOut` | unchanged |
| Progress, spinners, constant rotation | `linear` | unchanged — the only legal use |

**Overshoot is now a semantic, not a default.** In the base register `back.out` is how big things
arrive. In PRO, `back.*` means *you did that* — so an element that overshoots without the operator
having caused it is a bug, because it is claiming credit for an event that did not happen.

## 5. Choreography

- **The positional diagonal stagger (base §5) is dropped.** It is the base's signature and it costs
  up to 1.5s across a viewport. Replaced by index order within a group, capped as above.
- **Direction still has meaning**, and it is narrowed to one axis: content arrives from **8px below**
  and leaves upward, or does not move at all. Side-entry is reserved for things that genuinely come
  from a side — a drawer, a side panel, a detail pane sliding out of the row it belongs to.
- **Distance shrinks with the duration.** `y: 8` for rows and controls, `y: 12` for panels, `y: 16`
  for a full pane. The base's larger travel needs the base's longer durations to not look thrown.

## 6. The named patterns

| Base pattern | PRO |
|---|---|
| 6.1 Bar-sweep reveal | **Demoted from default to accent.** Keep it for a screen title and for a value that just changed (a total recalculating, a status flipping). Never for body text, table cells, form labels or anything that appears in quantity. Body text simply arrives |
| 6.2 The curtain | **Reserved.** Fires on a genuine context switch — signing in or out, changing module, changing the customer or company being worked on. Never on routine navigation. 3–4 bars, not 6, and the random per-bar offsets are kept: a fixed cadence still reads mechanical in the bad way |
| 6.3 The slice background | **Optional and quiet.** A screen may have one; it is a static slanted layer, and it does not animate on every entry. `dna/03` surface rules unchanged |
| 6.4 The drift (idle) | **Dropped.** Nothing drifts. Peripheral motion in a tool is something the operator's eye keeps re-checking for eight hours |
| 6.5 The typewriter | **Dropped** |
| 6.6 The magnetic field | **Dropped.** A pointer field is ambience, and an element that leans toward the cursor moves without the operator having asked it to. The press feedback a magnetic button carries is worth keeping; the field around it is not |
| 6.7 The assembly | **Reserved.** An object that builds itself is arrival choreography at its most expensive. Allowed once, on a landing or a sign-in screen, never on a screen inside the working day |
| 6.8 Haptic punctuation | **Kept where the platform has it**, on consequence only, never on navigation |

### The one idle PRO allows

An element may loop **only while a real process is running**, and it stops the instant the process
does: an active timer, a sync in flight, a queued job, an unsaved-changes indicator. That is the
"life" intent doing actual work — it is telling the operator something is true right now.

Everything in base §7 about pausing off-screen loops still applies, and gains a second condition:
**a loop with no live process behind it is deleted, not paused.**

## 8. Interaction motion

Base §8 applies. Two PRO additions, and the first is the rule the whole project runs on:

- **CSS owns idle states; the engine owns transitions.** Hover, focus, selected, active, disabled and
  the transitions between them are declarative CSS — they are *states*, not choreography, and routing
  them through the engine makes them stutter under load and impossible to interrupt. Enter, leave,
  and every named consequence animation belong to GSAP. The base's warning about never leaving a
  declarative transition on a property the engine also animates is what keeps the two out of each
  other's way; in PRO it is the seam between the two systems, and it is checked, not assumed.
- **Every consequence is acknowledged within one frame of the input**, even when the result takes a
  second. The press animation is not waiting for the server. An action whose result is slow gets the
  press punch immediately and a status change when it lands — never one delayed animation doing both
  jobs badly.

## 9. Degradation

Base §9 applies, with the three settings and the single precedence function unchanged:

```
transitionsAreInstant = userChoseInstant OR liteMode OR prefersReducedMotion
```

Two PRO differences:

- **The lite substitute pair is already close to the PRO default**, so lite mode changes far less
  here than it does in the base register. Lite drops the curtain, the slice and the bar-sweep, and
  leaves the rest — which is most of what PRO does anyway.
- **`prefers-reduced-motion` is mapped onto the PRO timing table, not the base one**, and it is a
  first-class requirement rather than a courtesy — see [`11-accessibility.md`](11-accessibility.md).
  Under reduced motion, consequence animations become an instant state change with no transform, not
  a shortened tween: the acknowledgement survives, the movement does not.

## Anti-patterns

Everything in the base list, plus: an animation the operator waits for. A stagger whose total grows
with the list. `back.*` on something the operator did not cause. A curtain on routine navigation.
Idle motion with no live process behind it. The bar-sweep on body text or table cells. A press
animation that waits for a network response. Hover routed through the animation engine. A "subtle,
professional" flattening that took the punch out of consequence along with everything else.
