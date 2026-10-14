# PROGRESS.md

Heartbeat (UTC): 2026-10-14 02:05 — M1 committed (f0ec55f). Starting M2 (media blocks).

## Phase 0 gate
- [x] Environment checked, missing tools reported (ENVIRONMENT.md)
- [x] All questions asked in one message + machine-readable copy (ASKS.md)
- [x] AI endpoint tested — HTTP 200 on /chat/completions in 0.8s (Vansh supplied URL/key/model 13 Oct; stored in /root/.quick-notes-secrets/ai.env, never in repo)
- [x] Understanding Summary approved ("ok do it")
- [x] DECISIONS.md written + spec updated to match

## Done
- **Phase 0 + M0** (see git log cc1b9a2).
- **M1 Notes core:** Room 2.7.2 entities (notes, blocks, tags, note_tags, media_assets) + real FTS5 virtual table created via DB callback, accessed through @RawQuery SearchDao (`Fts5SearchIndex`); `NoteSearchIndex` interface with `FakeNoteSearchIndex` for JVM tests (Robolectric SQLite lacks the fts5 module — real FTS5 gets androidTest on-device). NotesRepository: create/save (title from first line, debounce coalescing), FTS across text+transcripts+link titles with operator-safe query quoting, pin/archive/trash/restore, 30-day purge + orphan-index cleanup. AutoSave class (500 ms debounce, flush on pause/stop/onCleared via its own scope). HomeScreen (large title, expanding search, pinned row, note cards, capture pill) + EditorScreen (block list, capture bar, keyboard-up on open) + Compose Navigation (home ↔ editor). Hilt wiring done; MainActivity now hosts the real nav graph (demo screen kept for design review/tests).
- **Tests: 27 green** — new: 8 repository/FTS/behavior, 3 AutoSave (virtual time), 3 M1 Compose flows (create→home→search→empty state), 2 evidence screenshots (m1-home.png, m1-editor.png in docs/evidence). Room tests use inline executors to avoid flow-dispatch races (lesson logged below).
- **Lint:** clean (0 errors) after: FocusRequester() wrapped in remember ×2; suppressed test-only ViewModelConstructorInComposable.
- **Gate:** lint + unit tests + assembleDebug all green; APK rebuilt.

## In progress
- (starting M2) Media blocks: CameraX capture, gallery pick, drawing canvas, link cards, share target.

## Blocked / notes
- No emulator here (no KVM): runtime-only behaviors → MANUAL_TESTS.md for Vansh's phone.
- 3.8 GB RAM + 4 GB swap: builds run with org.gradle.workers.max=1, capped heaps; never two heavy jobs at once.
- Reasoning model returns reasoning_content — AI parser must trim (M6).
- **Test-infra lessons:** (1) Robolectric SQLite has no fts5 → abstract search behind interface. (2) Room Flow queries dispatch on a background executor → set inline query/transaction executors in tests or use idleFor settling; plain waitForIdle races the flow. (3) AGP 9: kotlin-android plugin removed, M3 Shapes() wants CornerBasedShape (our QNShapes objects are used directly), Color.value can be 0 in JVM tests (assert components).

## Next
- M2: media store (app-private files), CameraX capture + keep-shooting, gallery pick, drawing canvas (vector strokes + preview render), link card fetcher, share-target activity.
