# Platform — Svelte 5 + Tauri v2 (+ Rust, PTY, xterm.js)

**Reference project:** VAD/OS. Its `.claude/docs/QUIRKS.md` is the long-form source; this file keeps
the entries whose cause generalises past that project.

## Svelte 5

**A plain `let` read in a template is a constant read.** Svelte 5 compiles it as one, so the value is
pinned to its initial state for the life of the session no matter what assigns to it. Anything a
template reads is `$state`. When a value is visibly stuck and the code that sets it looks right,
**check the declaration before the logic**. The compiler warns — read the warnings. This was the
single worst bug in that codebase and it cost four sessions.

**The exit tween owns the unmount.** `{#if}` removes the node the instant the flag flips, so a tween
started from a click handler animates a node that is already gone — silently doing nothing, which
looks exactly like having no exit animation. Clear the flag in `onComplete`, and route every close
path through the one function that does it.

**Never split DOM the framework owns.** A per-character wave that replaces text nodes with spans
hands its work to a re-render that is about to throw it away. If it must be done: detach the
*original text nodes* and hand those back — never restore from a saved HTML string, which leaves
every framework reference pointing at a node that will never be on screen again — and call the
teardown on every early-exit path.

Load `svelte-runes` before touching reactive state and `svelte-styling` before scoped CSS or
`:global`.

## Tauri v2

**Windows are created before `setup` runs.** A config-dependent decision about whether this process
should exist cannot be made in `setup` — by then a webview exists.

**`ShellExecute` across a UAC boundary gives the child a fresh environment block.** An env-var marker
to detect a relaunch does not survive; use an argv flag.

**A debug build is console-subsystem** (`windows_subsystem = "windows"` is release-only), and an
elevated child cannot inherit the dev CLI's console, so Windows hands it a second one. Two windows is
not always a bug in your code.

**Two processes race for the shared `EBWebView` data folder.** Killing a parent to relaunch elevated
is not free.

## Streams, PTYs and IPC

These generalise to any streaming source — sockets, log tails, SSE.

**Two channels have no ordering guarantee against each other.** Anything that means "the stream is at
position X" must be parsed *in* the stream, where the handler runs with the cursor exactly where the
marker sat. Keep the transport layer a dumb pipe.

**Chunk boundaries are an artefact of the pipe, not a fact about the text.** 8 KB is not a semantic
unit. Never let a decision depend on how much has arrived. Symptom: a bug that happens "sometimes",
on fast commands, or only on one machine.

**A rule about finished text, applied to text still arriving, is a different rule.** If a parse rule's
answer depends on how many lines it has seen, it cannot run on live output — make it
length-independent, or defer it until the stream closes.

**Structure re-derived from the whole buffer is not stable while growing.** Mid-stream a parse is
provably not final, so mounting and animating nodes on their way to being replaced is worse than not
animating them. Take the buffer on a **settle gate**: quiet for ~80 ms, capped at ~240 ms so a
command that never stops talking still flows, with a longer first-paint delay (~1.5 s) because half a
block arriving reads as broken.

**Anything that runs per chunk must be O(new bytes), not O(buffer).** Resume reads from where the
last one committed. A repaint or a reflow invalidates that cache, because rows already read have been
rewritten.

**A cheap guard placed after the expensive work is worth nothing.** When something is slow *despite*
having a guard for exactly that case, check where the guard sits before assuming it is missing.

**Interactive programs that avoid the alternate screen cannot be parsed.** Ink apps repaint inline;
git sets `LESS=FRX` and `-X` means no alternate screen. Reading back a buffer being overwritten
dozens of times a second produces garbage, and no parser fix improves it. The generalisation:
**when every way of inferring something costs the common case, stop inferring and ask something that
already knows** — the command line knew the program's name before it ran.

**The browser moves the scroll for you.** Scroll anchoring adjusts `scrollTop` to keep a growing
container "stable" and fights any anchor you implement — `overflow-anchor: none`. And in a container
the app positions itself, *every* size change needs an explicit answer including **shrinking**; a
missing case is a handover to the browser's heuristic, not a no-op.

## Shell integration (Windows especially)

**Windows PowerShell 5.1 is not PowerShell 7.** No `` `e `` escape (use `[char]27`), no `&&`/`||`, no
ternary, no null-coalescing, `ConvertFrom-Json` returns a `PSCustomObject`. Target 5.1 — it is what
ships with Windows.

**Terminate OSC with BEL, one byte, never ST (`ESC \`).** Anything that drops the ESC leaves a
literal backslash on screen that the line editor never typed and cannot erase.

**Everything that is not visible prompt text goes straight to the console**, not into the returned
prompt string. PSReadLine derives its redraw geometry from the prompt string, and escape sequences
are zero-width on screen but not in the string.

**A shell's exit code is not one number.** `$LASTEXITCODE` is set only for native executables and is
never cleared. Read `$?` as the very first statement of `prompt`, fall back to `$LASTEXITCODE`, then
`1`, and reset it.

**When a fix is written against the shell a bug was found in, check whether the *cause* was.** The
BEL/ST bug was found under PSReadLine and fixed only there; nothing about the cause was
PowerShell-specific, and bash, zsh and fish stayed broken for two months.

**Prefer terminal operations that move nothing.** `term.clear()` renumbers rows and the shell is
never told; `\x1b[3J` frees the same memory without it. A desync should produce nothing, never an
invented reading.

## Motion under this stack

Terminal-tuned timings, not portfolio timings: nothing in the output path over **0.3s**, chrome and
panels to **0.4s**. See `map/03-vados.md` for the full retuning and the `GLITCH_IN` case study.
