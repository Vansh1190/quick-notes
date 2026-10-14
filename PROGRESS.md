# PROGRESS.md

Heartbeat (UTC): 2026-10-14 03:15 — M2 complete & committed. 59 unit tests, lint 0 errors. Starting M3 (voice FGS + Live Update).

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

## Done (M2 Media blocks)
- MediaStore: images/audio/drawing-previews/crash-recovery chunks under filesDir/media with relative-path DB refs. LinkFetcher (HtmlMetaParser unit-tested; offline → plain-URL fallback). Drawing engine: pure StrokeGeometry (Catmull-Rom smoothing, decimation, pressure width, vector eraser) + DrawingState/Reducer (undo/redo, 6 colors, width slider) + DrawingScreen (finger+stylus pressure via PointerInputChange) + StrokeRenderer (android.graphics bitmap preview, unit-tested pixels). CameraScreen: CameraX keep-shooting → temp JPEGs → addImage. Gallery picker (ACTIVITY_RESULT GetContent). Share target: manifest SEND/SEND_MULTIPLE filters, ShareParser (unit-tested URL/prose/image rules), ShareHandler creates notes (tests: text/url-with-metadata/image-on-disk). Editor: image/checklist(toggle+haptic)/link/voice/drawing/divider block views; capture bar wires mic(M3)/camera/draw/link-dialog/checklist/gallery.
- Evidence: docs/evidence m2-editor-blocks.png, m2-drawing.png.
- Known v1 gaps logged: link thumbnail *image* download (title/site done), Scan-text OCR mode (spec marks optional), ML Kit not yet integrated (M2/M6 boundary).

## Next
- M3: AudioRecorder (crash-safe chunks), RecordingService (mic FGS + notification with ProgressStyle/promoted-ongoing on 36), recovery scanner, RecordingSheet UI, on-device transcription (SpeechRecognizer), editor voice block live state.
