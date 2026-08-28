# Module 08 — Voice, copy and content

The words are part of the visual identity. The interface presents itself as a *system* — logs,
sectors, classified files, terminals, visitor records — and the copy has to hold that up.

## Register

Terse, uppercase where it labels, declarative where it explains. Machine-adjacent without pretending
to be a fictional OS: it never role-plays, it just names things the way a control panel would.

- Labels: one or two uppercase words. `YOUR DATA`, `CLASSIFIED`, `PERKS`, `SANDBOX`.
- Captions: one complete sentence, sentence case, explaining what the panel is for.
- Actions: verb-first and specific. `WIPE`, `CONTINUE WITHOUT.`, `DONE!` — not `OK` / `Cancel`.
- Errors: state the fact and the fix in one line. `color: needs a #rrggbb hex`.

## Naming things in the product

The interface names its own parts, and those names are consistent across every surface — a screen is
a *section*, a group of sections is a *sector*, a panel is a *module*, records are *logs*, the
visitor is a *visitor*. Pick the vocabulary once, use it in the UI, the code, and the docs. When code
and interface use different words for the same thing, one of them is wrong.

## Terminal tone system

Where the app has a terminal (and it should — it is the fastest way to expose power features without
designing UI for them), output is typed by **tone**, not styled ad hoc:

```ts
type TerminalTone = 'out' | 'dim' | 'ok' | 'error' | 'echo' | 'art'
```

`out` normal, `dim` secondary, `ok` accent-green success, `error` danger red, `echo` the command as
typed, `art` shape rather than meaning — keeps its own spacing and never wraps. Five tones is the
whole vocabulary. A sixth is a design decision, not a convenience.

## Progressive disclosure

Content is gated and revealed rather than presented flat. Locked screens, unlock sequences, an offer
that appears only after the visitor has demonstrated the relevant behaviour (a transition-speed
setting proposed after two screen changes, once ever, and never while another prompt is unanswered).

The rules:

- **A prompt is earned, not scheduled.** Trigger on demonstrated behaviour, counted in the units that
  matter (screen steps, not raw events).
- **Once ever.** Persist that it was offered.
- **Never two at once.** Two prompts for two settings is fine; both on screen together is not.
- **Locked never means broken.** A locked screen ignores its shortcut silently; it does not error.

## Motion notices

Anything that changes how the app *feels* is offered in the app's own voice, at the moment it matters
— the hardware-acceleration notice with its two answers (`DONE!` / `CONTINUE WITHOUT.`), and the
instant-cuts toast. Both remain changeable later in settings. A one-time choice with no way back is
never acceptable.

## Anti-patterns

Emoji in the interface. Marketing voice. `Lorem ipsum` shipped anywhere. `OK`/`Cancel` on a
destructive action. Two vocabularies for one concept. A modal that interrupts rather than an offer
that waits. Copy that role-plays a fictional machine at the cost of clarity.
