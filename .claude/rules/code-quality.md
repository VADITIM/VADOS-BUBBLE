# Code Quality

## Anti-defaults

- No premature abstractions. Three similar lines beats a helper used once.
- Don't add features, options or safety nets beyond what was asked. This app has one user and one device.
- Don't refactor adjacent code while fixing a bug.
- No dead code, no commented-out blocks, no version guards for versions this phone cannot run.
- WHY comments, never WHAT. If code needs a "what" comment, rename instead.
- **One line per comment, length unbounded.** A comment carries the intent the code cannot: the decision, the trap, the "or else". It never narrates the line below it — a comment restating the code is deleted, not shortened. A 400-word single line holding real context is fine and preferred over splitting it across several lines. It is one logical comment, so it is one physical line. Applies to files being edited anyway, never as a scanning pass of its own.
- The prose comments in this codebase are load-bearing: most of them record a platform trap that cost an afternoon. Do not trim them for brevity.

## Naming

- Kotlin files and classes PascalCase, functions and values camelCase, constants `SCREAMING_SNAKE`. CSS custom properties and class names kebab-case.
- Booleans: `is` / `has` / `should` / `can`. Functions verb-first (`deliverTorch`). Host-to-page entry points `on*` / `set*`.
- **No abbreviations — write the full word**, including locals and callback parameters: `event` not `e`, `index` not `i`, `milliseconds` not `ms` in identifiers, `notification` not `notif`. Only acronyms that are words in their own right (`id`, `url`, `api`, `dp`) survive.
- A name says what a thing *is*, not where it currently sits.

## Constants

Numbers that two files have to agree on (a pill width, a grab margin, a duration) are named on both sides with a comment saying which other file they mirror. There is no build step joining the Kotlin and the HTML, so a silent disagreement between them is the most likely bug in this codebase.

## Checks

There are no tests and no test harness — the check is the phone. Build, install over wifi, and say plainly what was verified on the device and what was not. Never report an animation as working when only the build succeeded.
