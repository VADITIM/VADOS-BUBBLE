# Platform — CLI, TUI and terminal output

For scripts, tools and anything whose entire interface is text. VAD/OS is the reference for the
*rendering* side; this file is for the tools themselves.

## Palette

Reduces to the grey ramp plus one accent. Concretely, with 256-colour or truecolour available:

- Dim/secondary — grey ramp equivalent of `#6a6a6a`
- Normal output — default foreground, unstyled
- Accent/success — terminal green `#5bfd5b`
- Error — `#ff6b6b`
- Echo of what the user typed — dimmed

**Never colour alone.** A semantic layer may add a channel, never replace one — an error is the word
plus the colour, so it survives a pipe, a log file, and a colourblind reader.

Respect `NO_COLOR` and a non-TTY stdout. Colour is decoration on a pipe and corruption in a log.

## The micro-label becomes a prefix

The panel label translates to a bracketed uppercase prefix in the same register:

```
[ BUILD ]  compiling 42 files
[ ERROR ]  no such target: release
```

Wide letter-spacing is not available, so the spacing inside the brackets does the same job. Keep it
consistent across every tool — it is the recognisable part.

## Tone system

The five tones from `dna/08-voice.md` are already terminal-native: `out` normal, `dim` secondary,
`ok` accent, `error` danger, `echo` the input as typed, `art` shape rather than meaning (keeps its own
spacing, never wraps, never re-flowed).

## The three-phase law still applies

- **Enter** — announce the work: one line naming what is starting.
- **Idle** — the spinner or progress line. This is the middle phase, and it is subject to the same
  rule as any idle loop: it exists to say *this is alive*, not to entertain.
- **Leave** — **a spinner must have a completion state, not just stop.** Clearing the line and
  printing nothing is the CLI's version of content blinking out of existence. Replace the spinner
  with its result on the same line.

## Copy

Verb-first, specific, lowercase for messages and uppercase for labels. State the fact and the fix in
one line: `color: needs a #rrggbb hex`. Never a stack trace where a sentence would do, and never a
sentence where the stack trace is what the user actually needs.

## Shell scripting on this machine

Windows with Git Bash and PowerShell 5.1 both present. The traps that keep recurring:

- **PowerShell here is 5.1, not 7.** No `&&`/`||`, no ternary, no null-coalescing, no `` `e ``
  escape (use `[char]27`), `ConvertFrom-Json` returns a `PSCustomObject`.
- `Set-Content`/`Add-Content` default to the system ANSI codepage — pass `-Encoding utf8` for
  anything another tool will read.
- Heredocs are a Bash feature; PowerShell needs a here-string with `'@` at column 0.
- `-ErrorAction SilentlyContinue` suppresses the message, not the failure exit code.
