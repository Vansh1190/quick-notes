# Build Report — Quick Notes

**Run:** overnight autonomous build, Oct 13–14 2026 (kickoff ~23:40 UTC, hard-stop lifted by
request; work continued through ~08:45 UTC). **Spec:** `SPEC.md` **Repo:** this tree, one commit per milestone.

## Verdict

**All 8 milestones shipped.** 75 Kotlin files (~8.9k LOC main, ~2.4k LOC tests), 117 unit tests
green, lint 0 errors (35 warnings, 2 hints), signed release APK (45 MB) + AAB (26 MB) built with
R8 minify. Every behavior that needs real hardware is unverified by design of the environment
(no emulator, no device) and is listed as 32 concrete steps in `MANUAL_TESTS.md`.

## Milestones

| | Milestone | Commit(s) | Gate |
|---|---|---|---|
| M0 | Custom design system (tokens, springs, squircles, palette, dark mode) | `f78fae7` + | green |
| M1 | Notes core: Room+FTS5, editor blocks, autosave, pin/archive/trash, tags | `f0ec55f` | 47 tests |
| M2 | Media: CameraX, gallery, drawing canvas, links+metadata, share-in | `f0ec55f` | 55 tests |
| M3 | Voice: crash-safe chunked recorder, mic FGS, Android 16 Live Update, recovery | `809ffe0` | 74 tests |
| M4 | Quick access: 3 widgets, QS tile, shortcuts, capture-mode routing | `9efe553` | 79 tests |
| M5 | Lists: progress rings, reorder/swipe/due reminders+boot re-arm, Dump & Sort (heuristic) | `54ab210` | 101 tests |
| M6 | AI: client + 8 features + editor sheet + Ask/Recap/Loose Ends/Link Essence/Snap | `92d5d4b`…`2174333` | 113 tests |
| M7 | Onboarding, backup/export ZIP, recap worker, signed release, docs | `fee0d8d`…this | 117 tests |

## Notable engineering decisions

- **Preview-first AI.** Every AI mutation goes through a preview the user confirms, with a
  pre-edit snapshot in `note_versions` (20 kept/note) powering undo ≥10 s and history —
  per spec §8. Verified the endpoint's native `tool_calls` mode and support both contracts.
- **Crash-safe voice by construction**, not luck: PCM chunks fsynced every 100 ms; surviving
  chunk dirs on next launch *are* the recovery signal (finalization deletes them).
- **Live Update (Android 16) opportunistically** — `setProgress` + promoted-ongoing behind
  SDK guards; everything degrades to an ordinary ongoing notification everywhere else.
- **Exact alarms with an honest fallback** (`setExactAndAllowWhileIdle` → 10-min window) instead
  of pretending permission is granted.
- **Backup = ZIP(lossless DB rows JSON) + Markdown/note + media bytes**; import restores ids
  and timestamps exactly. Round-trip test proves byte-identical media and exact state; it also
  caught a real FK-ordering bug (blocks before note row).
- **Heuristic Dump & Sort stays as the offline path** (EN/HI/Hinglish + "kal shaam 5 baje"-style
  date parsing); the LLM path drops into the identical preview contract.
- Keystore + AI key never touch the repo: signing reads git-ignored `local.properties`;
  the AI key lives in Android Keystore (`SecretStore`, AES-GCM) — the spec's one hard security
  line.

## Honest gaps / deviations

1. **Device-only behaviors unverified** — no KVM/emulator on the build box, phone never
   connected. All 32 `MANUAL_TESTS.md` items await a device (widgets, tile, shortcuts, Live
   Update, alarms, OCR, camera/mic, onboarding feel).
2. **Robolectric harness quirks** shaped some tests: `Dialog` content with weighted rows
   measures zero-bounds in JVM compose (dump & sort commit exercised via ViewModel instead);
   ML Kit refuses init in JVM tests (OCR is lazy-init + camera-captures-only, so tests never
   touch it). Both are harness limits, not app bugs, but they are *coverage* gaps worth a
   device pass.
3. **Live-endpoint AI tests** run as `androidTest` only when creds are pushed to the device
   (skipped in CI by design); server-side transcription stays off (endpoint has no
   `/audio/transcriptions` — confirmed in Phase 0).
4. **Volume-chord quick capture** — spec lists it as optional; not built.
5. **Baseline profile** — Play-recommended startup profile not generated (needs a device
   macrobenchmark run).
6. **APK is big (45 MB)** because ML Kit OCR is bundled across 4 ABIs. The AAB (26 MB uploaded,
   per-ABI splits ≈ 13–17 MB download) is the intended artifact; or drop x86/x86_64 abiFilters
   if installing bare APKs forever.
7. **Folders** exist in the schema (`folderId`) but have no UI — spec didn't require it for v1.

## AI endpoint notes (Phase 0 findings, still true)

`https://backend.aisdlcmarutisuzuki.com/v1`, model `cora_chat`: reasoning-style replies
(`reasoning_content`, `…`) — client normalizes both; native function calling works and
returns clean `tool_calls`; `/embeddings` and `/audio/transcriptions` 404 → semantic search
and server STT stay out of v1.

## How to run

`README.md`. Fastest confidence pass: `./gradlew :app:testDebugUnitTest :app:lintDebug
:app:assembleDebug` then work through `MANUAL_TESTS.md` on the Pixel.
