# Quirks

Traps, written as the symptom someone will arrive with. Most platform workarounds are recorded in `decisions.md` and `architecture.md`.

## Every CI build red on "setup" before Gradle runs (since 2026-09-17)

`android-actions/setup-android`'s default package list asks for the legacy `tools` package, which the SDK repository no longer carries, so `sdkmanager` exits 1. The workflow names `packages: platform-tools` explicitly.
