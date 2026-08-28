# Module 01 — Palette

Colour is not decoration here. It carries one piece of information at a time: *where you are* (the
screen accent), *what will happen* (destructive red), or *what layer this is* (the grey ramp).
A screen that uses colour for anything else is off-brand.

## The rule

**One ground, one grey ramp, one accent, one danger colour.** Everything else on screen is a tint of
those four. If a new colour is needed, it is a new *accent* for a new screen — never a fifth role.

## Ground and structure (never changes between screens)

| Token | Hex | Where |
|---|---|---|
| `ground` | `#181818` | The page. Set with `!important` on `:root` so nothing overrides it. |
| `panel` | `rgba(18,18,18,0.85)` | Every card, module, dialog. Translucent so layers read through. |
| `panel-solid` | `#121212` | When a panel must not be see-through (menus over content). |
| `sunken-deep` | `#0e0e0e` | Inputs, wells, code and terminal backgrounds. |
| `sunken` | `#1c1c1c` | Secondary fills, inactive tabs, chips. |
| `raised` | `#232323`, `#2a2a2a` | The rare surface that must sit *above* the panel. |
| `border` | `#262626` | The default 1px hairline. This is the primary structural element of the whole design. |
| `border-strong` | `#2c2c2c` | Dividers, separators, control outlines. |
| `border-control` | `#3a3a3a` | Buttons, icon rings, scrollbar thumbs. |
| `border-hover` | `#5a5a5a` | Neutral hover on a non-accent control. |

Everything darker than `#0e0e0e` (`#060606`, `#0a0506`, `#050203`) exists only inside a screen that
tints its ground toward its own accent — see *accent-tinted grounds* below.

## Text ramp (five fixed steps — pick one, never invent one)

| Token | Hex | Use |
|---|---|---|
| `text-primary` | `rgba(255,255,255,0.87)` | Running text. **Never pure `#ffffff` for prose.** |
| `text-bright` | `#f0f0f0`, `#f4f4f4` | Headings, values, the number in a stat. |
| `text-body` | `#d8d8d8`, `#cfcfcf` | Secondary prose, descriptions. |
| `text-muted` | `#9a9a9a`, `#a8a8a8` | Metadata, timestamps, helper text. |
| `text-label` | `#8a8a8a` | The uppercase mono micro-labels. |
| `text-icon` | `#6a6a6a` | Icon strokes, affordance marks, placeholder. |
| `text-faint` | `#4a4a4a` | Captions under a panel. The dimmest thing allowed to be readable. |

`#ffffff` is permitted only for: an accent-on-solid label, a glyph inside a filled accent button, and
pure-white masks/gradients used as compositing tools (not as text).

## Screen accents — the main colours

Each screen owns exactly one. They are electric, high-chroma, and deliberately *unbalanced* against
each other — this is a menu of distinct rooms, not a harmonised palette.

| Screen | Hex | Character |
|---|---|---|
| Perks | `#FFDD1B` | Sodium yellow |
| Logs | `#0040ff` | Hard electric blue (also `#001eff`) |
| Projects | `#DC143C` | Crimson |
| Sandbox / default / loading | `#5bfd5b` | Terminal green — the identity colour of the whole system |
| Guestbook | `#f09b3a` | Amber |
| Classified | `#8a2be2` | Violet |
| Custom | user-chosen `#rrggbb` | Validated `/^#[0-9a-fA-F]{6}$/`, falls back to the default green |

**The mechanism is the important part.** A single custom property (`--section-color`) is repointed at
the active screen's accent, and every component reads it through a local alias:

```css
.module { --accent: var(--section-color, #5bfd5b); }
```

A component never names an accent. It inherits the room it is standing in. Porting this to a
non-CSS platform means the same thing: one observable/theme value at the app root, every widget
binding to it, zero hardcoded accents in leaf components.

### Secondary accents (inside a screen, for categories)

Used when one screen needs to distinguish several items — chart series, skill branches, project cards.
They stay in the same electric register:

`#2ee6ff` cyan · `#ff2e88` hot pink · `#3664fc` indigo · `#4db8ff` sky · `#7e55dd` / `#9532e6` /
`#d98cff` violet family · `#ffb347` / `#ffd08c` / `#f5c542` amber family · `#ff506e` / `#ff7588` /
`#ff2b4d` rose family · `#0bc993` mint · `#fd5bfd` magenta · `#5bc4fd` ice · `#ff6bd6` orchid.

Desaturated/earth variants for *content-owned* colour (project cards, where seven tiles must coexist
without shouting): `#b0306b`, `#3f8f6f`, `#c8792a`, `#2f8fc8`, `#8a6fd0`, `#c4453f`, `#5a6f8a`,
`#6fae7c`, `#8a9a6f`, `#6a86a8`, `#9dc0e8`, `#b07c43`, `#a75f13`, `#9a8a52`.

### Accent-tinted grounds

When a screen needs atmosphere, the ground is nudged a few points toward the accent rather than
replaced: `#170a0e`, `#17080c`, `#15090c`, `#2a151a`, `#2a1218`, `#0c0608`, `#0d0507` (crimson room),
`#4a2020`, `#5a2630`, `#7c4a55`, `#b06a6a`, `#b02b2b`, `#910d28`, `#8d0d24` for its structure.
The rule: **tint, never swap.** The room still has to read as the same dark app.

## Danger

| Token | Hex | Use |
|---|---|---|
| `danger` | `#ff6b6b` | Every delete/wipe/destructive control, error text, error overlay. |
| `danger-hard` | `#DC143C` | The filled state of a destructive button, error borders. |
| `danger-extreme` | `#ff2b2b` | Confirmation of an irreversible action only. |

One red means one meaning. A destructive control is never the screen accent, even on the crimson
screen — that is exactly when the distinction matters most.

## Derived colour — never hand-mix a tint

Tints come from the engine, not from a designer picking a lighter hex:

```css
border-color: color-mix(in srgb, var(--accent) 30%, #262626);
background:   color-mix(in srgb, var(--accent) 12%, transparent);
```

Two helpers exist in code for the same reason and should be ported to any platform:
`isLightColor(hex)` (luminance `r*0.299 + g*0.587 + b*0.114 > 0.5`, decides black-vs-white text on an
accent fill) and `desaturate(hex, amount)` (blends toward that same luminance grey, used to mute an
accent without shifting its hue).

## Anti-patterns

Light backgrounds. A second accent on the same screen with equal weight. Pure `#fff` prose.
Gradients as surfaces (gradients are for *glow masks* only). A colour chosen because it "looks nice"
rather than because it encodes a state. Hardcoded accents in a leaf component.
