# Map

Where everything is. To change X, look in Y.

| Path | Purpose |
|---|---|
| `app/src/main/java/` | Kotlin services, watchers, windows and the WebView bridge |
| `app/src/main/assets/` | The interface: `dna.css` (tokens), `pill.html` (every bubble), `panel.html` (control panel), `fonts/` |
| `app/build.gradle.kts` | The build, and the version (`versionName`) |
| `build-install.ps1`, `grant.ps1` | Windows build-and-install, and first-install permission grants |
| `design/icon.html` | The app icon source |
| `docs/showcase/` | Recorded clips the README links |
| `.github/workflows/build.yml` | Debug build, versioned release on a manual run |
| `RELEASE_NOTES.md` | Text of the latest release |
| `.obsidian/` | Vault settings for the notes opened from this folder |

## Agent docs (`.claude/`)

| Path | Purpose |
|---|---|
| `CLAUDE.md` | Orientation, commands, key decisions, release mode |
| `docs/architecture.md` | Kotlin/WebView split, watchers, windows, bridge |
| `docs/decisions.md` | The long form: what every state is, why each gesture lives where it does, what the platform forced, debug hooks, device facts |
| `docs/quirks.md`, `docs/known-issues.md` | Traps; open bugs and debt |
| `docs/roadmap.md` | The phase plan with what is done |
| `docs/rules/` | Bubbles, states, motion, code quality |
| `hooks/` | Shell hooks wired in `settings.json` |
| `skills/` | Vendored skills (provenance in `skills/SOURCES.md`); `skills/vas/` is the git-ignored V/AS clone |

To change a state or a mod: `docs/rules/states.md`, then `pill.html`. To change a window or the bridge: `docs/architecture.md`.
