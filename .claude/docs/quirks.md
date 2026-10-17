# Quirks

Traps, written as the symptom someone will arrive with. Most platform workarounds are recorded in `decisions.md` and `architecture.md`.

## Every CI build red on "setup" before Gradle runs (since 2026-09-17)

`android-actions/setup-android`'s default package list asks for the legacy `tools` package, which the SDK repository no longer carries, so `sdkmanager` exits 1. The workflow names `packages: platform-tools` explicitly.

## The whole Dashboard flashes back for a split second after it closes

This happens when the panel is closed within ~400ms of letting go of the brightness bar. `solo-in`, the modules swelling back after the hold, outranks the leave's `bubble-collapse` by one class. While it stood, the modules grew back over a closed panel. When it came off, the collapse replayed from full opacity. `closeStatusPanel` ends the hold through `endSolo`. Any new class that animates the modules has to be taken off there too. See motion.md, "A leave outranks every state the panel was standing in".

## `[probe]` lines in logcat

These come from an instrumentation pass that was left in the source. Each console line costs a hop to the host's main thread, the thread that paces the WebView's frames. Remove probes in the same task that reads their numbers.
