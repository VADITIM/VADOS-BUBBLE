# VAD/OS — the terminal

`C:\Users\vadim\source\VADOS` · Tauri v2 + SvelteKit (SPA) + Rust (portable-pty) + xterm.js
(raw fallback only) + GSAP · Windows and Arch.

A terminal that treats a session as a structured interaction rather than a stream of text: commands
become blocks, output keeps the structure it already had, and anything the terminal does not
understand is passed through untouched.

## What it contributes to the DNA

**The tuning for motion in a tool used hundreds of times a day.** The portfolio can afford a 1.72s
curtain because a visitor sees it a handful of times. A terminal cannot. Everything below is the
DNA under that pressure, and it is where the "an animation that is delightful once is an obstacle by
the fiftieth time" rule comes from.

- **Nothing in the output path exceeds 0.3s**; chrome and panels may reach 0.4s. Compare the
  portfolio's 0.35–0.95s enters.
- **The motion guides the eye step by step.** If two things move at once and neither is clearly the
  subject, the choreography is *wrong*, not too slow.
- **"Playful, not over-committed" has a line:** anticipation that sells a handoff is character; a
  wiggle after the handoff has landed is a delay wearing a costume.
- **The action *is* the movement.** Submitting a command is the input handing its contents to the
  output region — not "the action happens and then something fades in".
- **The exit tween owns the unmount.** Svelte removes an `{#if}` block the instant the flag flips, so
  a tween started from a click handler animates a node that is already gone and silently does
  nothing — indistinguishable from having no exit animation. Clear the flag in `onComplete`, and
  route every close path through one function.
- **`GLITCH_IN`** (`src/lib/anim.ts`) — the stuttered entrance ported from the portfolio's
  Classified section and retuned: position drags in on a `rough()` jitter while opacity blinks up on
  a different stepped ease, and the two not tracking each other is the whole effect. The portfolio's
  `strength: 2.4, points: 24` over 0.7s became `strength: 1.6, points: 14`, because a section
  entrance there is a destination and a panel here is on the way to something.
- **A shared animation value is a statement that two surfaces are the same gesture.** Anything used
  once stays where it is used.
- **`RoughEase` must be registered** — an unregistered ease string does not throw, it silently
  degrades to the template ease, which is the worst failure mode for an effect whose point is jitter.

## Architecture worth knowing

- Two renderers over one PTY stream: a **block renderer** (plain DOM, one `<section>` per command —
  this is what gets styled and animated) and **xterm.js** as a raw fallback mounted only on
  alt-screen enter for `vim`, `htop`, `claude`.
- **Rust is a dumb pipe.** Command boundaries come from OSC 133, parsed by xterm's own handlers in
  the frontend, never in Rust — a marker delivered out-of-band from the bytes around it arrives out
  of order and the output gets filed under the wrong command.
- **The parser emits nodes, never a markup string**, so command output can never be read as markup.
- **Every block keeps the raw bytes it rendered from** and can be toggled back to them. (Decided;
  `snapshot()` does not yet honour it — the phase doc that makes it true is `phase-h1-raw-fidelity`.)
- **Compatibility first, beauty second.** Structure is entered on evidence, never assumed. A missed
  structure shows plain text; a false positive changes what the program printed, and only the second
  is a bug.
- **Markdown is not the point** — the renderer consumes structured nodes, and markdown is one of the
  representations output can take.

## Its agent docs — the best-developed set, worth copying wholesale

`.claude/README.md` indexes everything. Below it:

| File | Job |
|---|---|
| `tasks.md` | Bugs, blockers, open questions, future ideas. Checked before starting anything. |
| `decisions.md` | Settled calls and why. Checked before re-litigating. |
| `tests.md` | What still has to be checked **on screen**, appended per behaviour-changing turn, enforced by a Stop hook. Anything unchecked is unverified, and calling it done is a lie. |
| `docs/QUIRKS.md` | Bugs whose cause *generalises*, each with the rule that came out of it. |
| `docs/ANIMATION.md` | Binding animation ruleset. |
| `docs/PERFORMANCE.md` | Latency/memory/throughput budgets and the measurement protocol; `BENCHMARKS.md` holds measured numbers. |
| `docs/ACCESSIBILITY.md` | A semantic layer may add a channel, never replace one. |
| `foundation/phase-*.md` | Per-phase plans, read before and updated after. |
| `shells.md` | Rules every shell integration snippet follows. |

## Quirks that generalise beyond this project

From `docs/QUIRKS.md` — see `platforms/svelte-tauri.md` for the full list:

1. **A plain `let` read in a Svelte 5 template is a constant read.** Anything a template reads is
   `$state`. When a value is stuck at its initial state and the code setting it looks right, check
   the declaration before the logic.
2. **Two channels have no ordering guarantee against each other.** Anything meaning "the stream is at
   position X" must be parsed *in* the stream.
3. **Chunk boundaries are an artefact of the pipe, not a fact about the text.** Never let a decision
   depend on how much has arrived.
4. **A rule about finished text, applied to text still arriving, is a different rule.** Growing
   content must not have its node kind re-decided under it.
