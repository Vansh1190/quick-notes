# PROGRESS.md

Heartbeat (UTC): 2026-10-14 06:15 — M5 committed (54ab210). 101 unit tests, lint 0 errors, APK builds. Starting M6 (AI features).

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

## Done (M3 Voice / recording)
- ChunkedPcmWriter: PCM16 100 ms frames fsynced to <media>/recordings/<session>/chunk-*.pcm (survives force-kill) → PcmToAac (MediaCodec AAC + MediaMuxer; WAV fallback). ChunkedPcmWriter + recovery logic are pure/unit-tested.
- RecordingService: mic foreground service (FOREGROUND_SERVICE_TYPE_MICROPHONE) — AudioRecord→chunks, notification with Stop + live timer, pause/resume freezes elapsed, finalizes to AAC voice block on the target note (or new note from Home/widget). On Android 16+ requests promoted-ongoing (Live Update) + determinate progress (NotificationCompat 1.18 setRequestPromotedOngoing/setProgress) — all SDK 36 guarded, silent fallback below.
- RecordingRecoveryRunner on MainActivity start: filesystem scan of chunk dirs (finalize deletes them, so survivors = crash) → each orphan becomes a "Recovered recording" voice note (tests: single, multiple, empty).
- Transcriber interface: Stub (default) + Server (POST /audio/transcriptions, behind Settings.serverTranscription toggle — endpoint confirmed no /audio/transcriptions in Phase 0, so honest default is no transcript). Transcript applied to block + media_assets in background.
- RecordingSheet UI (waveform canvas, timer, pause/stop), RememberRecordingController (mic→notifications permission flow).
- SettingsRepository (DataStore prefs) + SecretStore (Keystore AES-GCM for API key at rest) — foundation for M6 AI.
- Lint: MissingPermission on AudioRecord/notify fixed with explicit checkPermission + SecurityException catch.
- Evidence: docs/evidence/m3-recording-sheet.png.

## Done (M4 Quick access)
- 3 Glance widgets: NewNoteWidget (2x2 tap-anywhere), QuickButtonsWidget (4x1, 5 capture buttons), LargeWidget (4x2 buttons + 3 recent notes, tap→editor). Provider XMLs + manifest receivers.
- CaptureModes single source of truth: widgets/tile/shortcuts → QuickCaptureActivity (forwarder) → MainActivity extras (StateFlow-routed) → nav autoMode (voice auto-starts sheet; camera/draw overlays; list→Lists). WidgetRefresher notify pattern via broadcast.
- QS tile NewNoteTileService (tap=quick capture; API 34 PendingIntent path). Dynamic shortcuts: New note/Voice note/Scan/New list.
- MainActivity.openNoteIntent + EXTRA_CAPTURE_MODE/EXTRA_OPEN_LISTS routing (onNewIntent too).
- Tests: QuickCaptureActivity forward, WidgetProviderTest manifest/intent contract.

## Done (M5 Lists)
- Room v2 additive migration: todo_lists + todo_items (dueAt, parentId, orderIndex).
- ListsRepository: CRUD, progress aggregation, due→AlarmReminderScheduler (exact w/ window fallback), done/remove cancels, purgeDone, title match. BootReceiver re-arms after reboot; ReminderReceiver notification→Lists.
- Lists screen: progress-ring cards, quick-add, Dump & Sort entry card, mic. "New list" shortcut routes here.
- List detail: check w/ haptic, long-press drag reorder, swipe delete, quick-add, due toggle (tomorrow 9am/clear), ring header.
- Dump & Sort v1: DumpAndSortSplitter heuristic (EN/HI/Hinglish) + DatePhraseParser ("kal shaam 5 baje" etc.) → editable preview → commit. Nothing persisted before Done. M6 swaps in LLM behind same preview contract.
- Tests 101 total (DatePhrase 6, Splitter 4, ListsRepo 7, M5 flow 4). Evidence docs/evidence/m5-lists.png.
- Robolectric lesson: Dialog-internal weight() click targets have zero bounds in JVM harness → click real widgets where possible, drive VM otherwise; device check in MANUAL_TESTS #23-32.

## Next
- M6: AI settings + test button, AiClient (OpenAI-compat), features 1-8 via same preview→confirm contract, WorkManager queue, undo (≥10 s) + version history for AI edits.
