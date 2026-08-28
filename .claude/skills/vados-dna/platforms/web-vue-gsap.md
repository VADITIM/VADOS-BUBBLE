# Platform — Web (Vue 3 + SCSS + GSAP)

The reference implementation. `dna/` is written against this stack, so this file only covers what is
*specific to the engine* rather than to the identity.

**Reference project:** PORTFOLIO25.

## Stack rules

Vue 3 + TypeScript + SCSS + GSAP + vanilla-tilt. Nothing else. No component library, no CSS
framework, no animation library beside GSAP. A library that brings its own visual opinions has to be
fought on every component.

Use the official GSAP skills (`gsap-core`, `gsap-timeline`, `gsap-frameworks`, `gsap-plugins`,
`gsap-utils`, `gsap-performance`) rather than reasoning about the API from memory. Project rules win
where they disagree. All GSAP plugins are free since the Webflow acquisition — install from the
public `gsap` package, never add an auth token or a private registry.

## Pitfalls

**A scoped `<style>` block compiles alone.** Anything emitting CSS that is imported into 45
components is emitted 45 times. Shared imports must be *declarations only* — variables and mixins.
`@font-face` and global rules are loaded once from the entry point.

**Tweening a custom property with no reader is silent dead code.** The tween runs, the value updates,
nothing moves, no error. `npm run check:css-vars` exists for exactly this and should be reproduced in
any project that animates tokens.

**A CSS `transition` on a property GSAP also animates smears every frame the tween writes.** Move the
transition onto the state that needs it (`:hover`, `.is-open`), never the base rule.

**Never animate a component that can be `v-if`'d out.** Screens stay mounted; GSAP needs persistent
targets. This is the DOM twin of the Svelte unmount trap in `svelte-tauri.md`.

**Set initial GSAP state inside the `matchMedia` callback** so it re-runs on a layout change; setting
it once at module scope leaves a stale hidden state when the layout flips. Always return the cleanup
function.

**`gsap.defaults({ immediateRender: false })` is global** — do not override it per tween.

**`vue-tsc --noEmit` against a solution-style root tsconfig checks nothing.** Point the typecheck at
the app config explicitly, and verify the check actually checks.

**The build output directory is not documentation.** In PORTFOLIO25, `docs/` is the gh-pages build
output — generated, never hand-edited. "Docs" in conversation always means `.claude/`.

## The two-layout stance

Horizontal and vertical are separate apps sharing a codebase, not one responsive design. Switch on
`isVertical` in scripts and `@media (orientation: portrait)` in SCSS. A change to one layout is not
a change to the other. See `dna/04-layout-and-sizing.md`.

## Verification

The user does the visual evaluation. Do not drive the browser, click through, dispatch events or
screenshot to verify a change unless explicitly asked. Write the code and stop.
