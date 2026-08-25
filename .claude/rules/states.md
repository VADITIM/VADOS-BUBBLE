---
paths:
  - "app/src/main/assets/**"
  - "app/src/main/java/com/v/island/**"
---

# States

The names this codebase uses for what the bubble, its satellites, and Now State are doing. The structural axis (`state`) and the `idle`/`alert`/`haptic` corners of the content axis (`size`) use these names directly in code. The rest of the content axis — `playing`/`player`, `timing`/`timer`, `calling`, `battery`, `picture`, `history` — keeps its own code names below; each pairs a mod's resting shape with its own separate open shape, and there is no single string that could stand for both without collapsing two real states into one.

## Structural states

What a bubble *is*, independent of which mod or content it's showing.

| Name | Was called | What it is |
|---|---|---|
| **Idle** | Closed | No mod true. Bare bubble, idle face, `compact` width. |
| **Mod** | Closed, mod-owned | A mod (media/clock/call) owns the bubble's shape: glyph, short reading, `playing`/`timing`/`calling` width. On the main bubble this is **Main-Mod**. |
| **Satellite-left / Satellite-right** | Satellite | A second or third live mod, not the owner, drawn as a circle beside the bubble instead of inside it. A satellite only exists because a mod is behind it, so it is always a Mod state by side rather than by centre. |
| **Alert** | Alert | A fresh notification arriving: `alerting` (text) or `image` (with a photo), dwells and auto-closes. |
| **Active** | Grow / Open | Tapped open, full detail, own layout, no satellites: `expanded`, `picture`, `player`, `timer`, `history`. Covers both a mod's own detail view and the bubble's own panels (quick settings, notification list) that have no mod behind them at all. |
| **Double** | Charging | Reserved, not built. A true second main bubble — standing beside the punch hole the way the bubble itself does, not owned by it, not a Mod and not a Satellite. It runs alongside whatever the main bubble is doing (Idle, Mod, Active, anything) rather than taking its slot, which is what makes it Double and not just another mod. Not a Now State either — Now State is a second window out at the clock; Double stays at the cutout. Battery/charging is its first and, for now, only occupant. The hard part: it needs the same liquid proximity-melt the main↔satellite pair has (see [motion.md](motion.md#liquid)) — an alert landing mid-charge, or charging starting mid-alert, has to read as two bodies of the same liquid touching, not as two unrelated shapes overlapping. |

Code's `state` variable is exactly this column now — `idle | alert | active | haptic` — and `size` carries `idle`, `alert`, and `haptic` for the corners that are content-agnostic. The old `state === 'active'` for a notification showing was renamed to `'alert'` first, freeing `'active'` for what had been `'open'` — the two would otherwise have collided on one string for two different things.

**Merge** is not a state of its own — it's the transition a mod's arrival or departure runs *between* two structural states (Idle↔Mod, Mod↔Mod). The glyph is the cause and travels first; width is the consequence and follows once it lands. `mergeHold` holds the bubble genuinely Idle for the length of that flight so a mod's face never shows before the mod has actually arrived — see [motion.md](motion.md) for the glyph-as-cause rule this transition is built on.

Now State is this same vocabulary applied to a second window rather than a second concept: it stands beside Main-Mod and Satellite as another place a mod-like state can live, out at the clock instead of at the punch hole. It is currently carrying one thing (the torch) but is named for the role, not the content — see [architecture.md](architecture.md#stacking).

## Content states

Which mod — or which of the bubble's own panels — a Mod or Active state is currently showing.

| Name | Was called | Where it shows |
|---|---|---|
| **Idle** | compact | The bare bubble itself, no content. |
| **Media** | playing / player | Mod at rest / Active when tapped open. |
| **Clock** | timing / timer | Mod at rest / Active when tapped open. |
| **Call** | calling | Mod at rest. No Active view of its own — tapping it opens the call in its own app. |
| **Battery** | battery | Charging. Mod-shaped but never a Satellite: exclusive with every other mod. |

Two Active states belong to the bubble itself rather than to any mod, and have no Mod-state counterpart to grow from:

- **Haptic** (`expanded`) — the bubble's own settings (currently: microphone access), reached by holding the Idle bubble rather than tapping it. Named for the gesture that opens it, the same way the code's own `state = 'haptic'` marks the hold in progress before it resolves to this or to leaving for a mod's app.
- **History** (`history`) — the notification list panel.

A photo attached to a notification opens its own Active state, **Picture** (`picture`), sized to the image rather than to a fixed box.
