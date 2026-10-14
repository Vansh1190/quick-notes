# Understanding Summary (Phase 0 playback — approve with "approved" or list changes)

## What will be built (v1, tonight, in milestone order)
A single-activity Kotlin/Compose Android notes app ("Quick Notes", placeholder) where capture is instant and AI organizes later. Blocks-in-a-canvas editor (text/checklist/image/drawing/voice/link/divider), continuous autosave (debounce ~500 ms + on pause/stop), FTS5 search across text+OCR+transcripts+link titles, pin/archive/trash(30d), tags. CameraX capture + gallery + Scan-text OCR. Full-screen drawing canvas (vector strokes + preview render, stylus-aware). Link cards w/ background metadata fetch + offline fallback. Voice: microphone foreground service, crash-safe continuous disk writes → AAC/M4A finalize, orphan recovery on next launch, ongoing notification w/ chronometer+Stop, **Android 16 promoted ongoing / Live Update used aggressively** (your addendum: recording chip, AI-job progress chips, recap digest, reminder glance — all fail-soft to normal notifications). Quick access: 3 Glance widgets, QS tile, 4 launcher shortcuts, share target, partial-height Quick Capture screen. Lists: Lists screen w/ progress rings, drag/swipe/nesting/reminders/haptics, Dump-&-Sort voice→lists with AI preview card, EN/HI/Hinglish date-phrase resolution ("kal shaam 5 baje"). AI: Settings (base URL/key/model + Test), OpenAI-compatible provider behind an interface, JSON-schema validation + 1 retry, WorkManager queue, per-feature toggles, undo ≥10 s + version history; features 1–8 (Dump&Sort, Snap to Structure, Ask My Notes, Clean Up, Auto title/tags, Link Essence, Loose Ends, Daily Recap). Design: Inter, collapsing large titles, continuous-corner shapes, inset grouped cards, squircle cards 20dp, spring motion (~0.8 damping), shared-element transitions, predictive back, press-scale (no ripples), sheet detents, haptics, reduce-motion setting, full dark mode, frosted bars (RenderEffect 12+, fallback below). Onboarding (3 screens + guided widget/tile setup). Backup: ZIP (JSON+media) + Markdown export, exact-restore import. Signed release APK + AAB, baseline profile, README, BUILD_REPORT.md.

## What will NOT be built
Cloud sync, accounts, collaboration, iOS/desktop, power-button wake (impossible — onboarding tip for maker side-key instead), stock-Material look.

## What will be stubbed / unverifiable here
- Volume-chord Accessibility Service → v1.1 behind feature flag (spec default D2).
- Semantic search, Rewind, handwriting→text → v1.1 (skip unless time).
- Server transcription / embeddings → only if B5/B6 say the endpoint has them.
- Runtime on a device: **this machine has no KVM/emulator/device.** CI-level verification = compile + unit + Robolectric + Compose UI tests + lint + monkey on… nothing: instrumented tests compile but cannot run here. Anything device-dependent is proven by tests + written into MANUAL_TESTS.md for your phone.
- Live AI calls: build + fixture tests now; live test happens the moment B1–B3 are filled (file or Settings at dawn).

## Milestones & time estimates (wall-clock on this 4-core box; builds are slow here)
| M | What | Est |
| --- | --- | --- |
| M0 | Toolchain install (JDK17, SDK 36, build-tools), project scaffold, Hilt, Room+FTS5 schema, nav, design system (type/color/squircle/springs/press-scale/frosted/sheets), styled demo | 2.5 h |
| M1 | Note list + editor (text blocks, rich formatting, autosave), FTS search, pin/archive/trash | 3 h |
| M2 | CameraX, gallery, drawing canvas, link cards + fetcher, share target | 3.5 h |
| M3 | Mic FGS, crash-safe recorder + recovery, transcription (on-device), notification + **ProgressStyle/promoted-ongoing Live Update**, recording sheet w/ waveform | 3.5 h |
| M4 | 3 widgets, QS tile, shortcuts, Quick Capture ≤300 ms | 2 h |
| M5 | Checklist block, Lists screen, reminders, voice Dump-&-Sort + preview card (stub AI until M6) | 2.5 h |
| M6 | AiProvider, settings/test, features 1–8, WorkManager, undo/versioning | 3.5 h |
| M7 | Animation/dark/reduce-motion pass, onboarding, backup/export, baseline profile, signing, README, report | 2.5 h |
| M8 | v1.1 items if time remains | — |
Total ≈ 23 h of focused work; with a hard stop at 08:00 UTC I will sequence M0→M7 and feature-flag anything at risk (spec rule 2).

## Needs a real phone (your manual list, preview)
Mic FGS survives Home + lock + 30 s; force-kill recovery; Live Update chip appearance/behavior; widget rendering on launcher; QS tile; share-sheet entry; camera; haptics; 60/120 fps feel; predictive-back gestures; Oreo–12 notification layouts; app-icon shortcuts; ML Kit model download; EN/HI/Hinglish STT.

## Risks I accept per spec
RAM 3.8 GB → single-worker Gradle, no parallel emulator dreams; Compose SharedTransitionLayout + predictive back on API 26 min → API-gated; androidx.ink stability → fallback custom canvas decided at M2 by a 20-min spike; Live-Update promotion is system-discretionary → always a great normal notification underneath.

**Reply "approved" (or with changes) to open the gate. Until then: zero app code.**
