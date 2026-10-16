# Quick Notes

An offline-first notes app for Android, built to feel like Apple Notes on iOS: fast capture,
calm UI, your data on your device. Built overnight (Oct 13-14 2026) by an autonomous build
agent against `SPEC.md`, in Kotlin + Jetpack Compose, single activity, MVVM + UDF, Hilt,
Room + FTS5, WorkManager.

## What's in it

- **Notes** — rich blocks: text, checklists, images, drawings (custom smoothed-stroke canvas),
  links (metadata preview), voice notes. Autosave every 400 ms, survives process death.
  Pin, archive, 30-day trash, FTS5 search across text + checklists + transcripts + OCR.
- **Voice** — one-tap recording sheet with live waveform + timer. Crash-safe: audio is chunked
  and fsynced every 100 ms; a killed app's takes are recovered as notes on next launch.
  Foreground service notification becomes an Android 16 Live Update (promoted ongoing) when
  the platform offers it.
- **Lists** — progress-ring cards, check haptics, drag reorder, swipe delete, due-date
  reminders (exact alarms with permissionless fallback, re-armed after reboot).
- **Dump & Sort** — talk or type one brain-dump ("buy milk, send invoice, call mom in the
  evening") and it splits into lists (AI when configured, heuristic otherwise; the heuristic
  handles English/Hindi/Hinglish and date phrases like "kal shaam 5 baje"). Nothing is written
  until you tap Done.
- **Quick access** — 3 home-screen widgets (new note / capture buttons / capture + recents),
  Quick Settings tile, launcher shortcuts (note/voice/scan/list), share-to-note.
- **AI (bring your own endpoint)** — OpenAI-compatible `/chat/completions` with native
  tool-calling support, wired to: Clean Up/Tidy/Summarize, AI title + tags, Dump & Sort,
  Link Essence, Ask My Notes (cited), Loose Ends, Snap to Structure (OCR → contact/receipt/
  event), Daily Recap. Every AI edit is preview-first with a real undo + 20-version history
  per note. API key lives in Android Keystore; never in files, logs, or backups.
- **Backup** — Settings → Export ZIP: lossless JSON of every note/block/tag/media row +
  media bytes + a Markdown copy per note. Import restores exactly (ids, timestamps, pins,
  trash state). Keys never travel.
- **Design** — custom design system (not stock Material): Apple-Notes-style large titles,
  springy press physics, yellow-on-black accent by default, full dark mode, reduce-motion
  switch, onboarding.

## Tech stack

| Layer | Choice | Version |
|---|---|---|
| Language | Kotlin (JVM 17) | 2.3.21 |
| Build | AGP + KSP, version catalog (`gradle/libs.versions.toml`) | AGP 9.4.1 / KSP 2.3.12 |
| UI | Jetpack Compose (BOM) + custom design system on M3 foundations, Navigation Compose | BOM 2026.09.00 / Nav 2.10.2 |
| Architecture | Single activity, MVVM + UDF, Coroutines/Flow, Lifecycle | 2.11.0 |
| DI | Hilt (+ hilt-navigation-compose, hilt-work) | 2.60.1 / 1.4.0 |
| Database | Room (KSP, exported schemas, migrations) + raw-SQL FTS5 search index | 2.7.2 |
| Preferences | DataStore Preferences | 1.2.1 |
| Background | WorkManager (`HiltWorkerFactory`, on-demand init), mic foreground service | 2.12.0 |
| Serialization | kotlinx.serialization JSON (block payloads, backups, AI messages) | 1.9.0 |
| Widgets | Glance AppWidget | 1.1.1 |
| Camera | CameraX (core, camera2, lifecycle, view) | 1.6.2 |
| OCR | ML Kit on-device text recognition | 16.0.1 |
| Images | Coil 3 Compose | 3.6.3 |
| AI client | Hand-rolled OpenAI-compatible HTTP client (`HttpsURLConnection`, no Retrofit) | — |
| Secrets | Android Keystore AES-GCM (`SecretStore`) | — |
| Voice | `AudioRecord` chunked PCM → `MediaCodec` AAC/M4A; on-device `SpeechRecognizer` STT | — |
| Testing | JUnit 4, Robolectric, Room migration tests, Compose UI tests, coroutines-test | Robolectric 4.17 |

## Build

Requirements: JDK 17, Android SDK 37 (compileSdk 37 / targetSdk 36 / minSdk 26).

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # release APK (needs signing entry in local.properties)
./gradlew :app:bundleRelease          # AAB for Play
./gradlew :app:testDebugUnitTest      # 117 unit/Robolectric tests
./gradlew :app:lintDebug              # 0 errors
```

Release signing stays on your machine: put these in `local.properties` (git-ignored) and keep
the keystore outside the repo:

```properties
qnKeystorePath=/path/to/your-release.jks
qnKeystoreAlias=quick-notes
QN_KEYSTORE_PASS=<password>
```

Without that entry the build still works; the release build simply stays unsigned.

## Device verification

The build box has no emulator/KVM and the test phone wasn't connected, so every behavior that
needs real hardware (widgets, Live Update, alarms, OCR, mic, camera) is listed with exact
steps in `MANUAL_TESTS.md` (32 items). Everything else is covered by unit tests
(`app/src/test/`) including Room migrations 1→2→3, backup round-trips, and Compose flows.

## Docs

- `SPEC.md` — the original build spec
- `DECISIONS.md` — judgment calls made while building
- `PROGRESS.md` — running log with milestones
- `BUILD_REPORT.md` — final report: what shipped, what didn't, known gaps
