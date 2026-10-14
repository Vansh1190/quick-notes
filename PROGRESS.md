# PROGRESS.md

Heartbeat (UTC): 2026-10-14 01:50 — M0 complete, committing.

## Phase 0 gate
- [x] Environment checked, missing tools reported (ENVIRONMENT.md)
- [x] All questions asked in one message + machine-readable copy (ASKS.md)
- [x] AI endpoint tested — HTTP 200 on /chat/completions in 0.8s (Vansh supplied URL/key/model 13 Oct; stored in /root/.quick-notes-secrets/ai.env, never in repo)
- [x] Understanding Summary approved ("ok do it")
- [x] DECISIONS.md written + spec updated to match

## Done
- **Toolchain:** OpenJDK 17, Android SDK (platform-tools, platforms;android-36 + 37.0, build-tools 35/36/37), Gradle 9.8.1 wrapper, 4 GB swap file (machine was thrashing without it), 4 Inter TTF weights bundled.
- **Stack pinned (see DECISIONS D6-D8):** AGP 9.4.1, Kotlin 2.3.21 + Compose plugin, KSP 2.3.12, Hilt 2.60.1, Compose BOM 2026.09.00 (requires compileSdk 37 / AGP 9.1+), Room 2.7.2. compileSdk 37, targetSdk 36, minSdk 26.
- **M0 design system:** continuous-corner QNSquircleShape (pure-Kotlin, k=0.72447), color tokens (spec hexes), Inter type scale (34/22/17/13 + support styles), QNMotion spring specs (damping 0.8, reduced→fade), press-scale 0.97 modifier (no ripples), frosted surface (RenderEffect 31+ / solid fallback), demo screen (collapsing large title, inset cards, capture pill) wired into MainActivity behind QuickNotesTheme.
- **Tests green:** 10 unit/Robolectric tests (squircle geometry, clamp, outline type, tokens, motion, demo-screen compose test) + screenshot evidence in docs/evidence/m0-design-demo.png. `:app:assembleDebug` produces app-debug.apk (14 MB).
- Repo pushed remote pending (M0 commit now, push after M1 per throttle).

## In progress
- (starting M1) Room entities/DAO + note list/editor.

## Blocked / notes
- No emulator here (no KVM): runtime-only behaviors → MANUAL_TESTS.md for Vansh's phone.
- 3.8 GB RAM + 4 GB swap: builds run with org.gradle.workers.max=1, capped heaps; never run two heavy jobs at once (caused the earlier unresponsiveness).
- Model is a reasoning model (returns reasoning_content + content): AI response parser must trim.

## Next
- M1: entities (Note/Block/... + FTS5), repositories, note list, editor w/ autosave, pin/archive/trash.
