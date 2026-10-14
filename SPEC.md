# Quick Notes (Android) — Build Spec

*Prepared for Vansh · 13 Oct 2026 · v1.1 · Working app name: **Quick Notes** (rename freely)*
*(Canonical copy of the spec Vansh provided in chat. Vansh's addendum in §6: "I have a phone with live updates feature available, so whatever you can do with it just do it. Make cool features accessible through it.")*

## 0. Phase 0: pre-flight gate
No app code until Vansh answers every question and approves the plan. Vansh is awake for Phase 0, asleep afterwards.
Gate checklist (all ticked before M0):
- [ ] Environment checked and missing tools reported
- [ ] All questions asked in one message and answered
- [ ] AI endpoint tested with one successful call
- [ ] Understanding Summary approved by Vansh
- [ ] DECISIONS.md written and this spec updated to match
After approval: create PROGRESS.md, DECISIONS.md, MANUAL_TESTS.md, commit, begin M0.

## 1. What we are building
Personal, Android-only notes app; whole point is speed of capture (<1 s from anywhere to dump a thought as text/voice/photo/sketch/link/checklist; AI organizes afterward). Feel: Apple Notes + Apple Voice Memos — calm, spacious, springy motion.
Principles: 1) Capture first, organize later. 2) Nothing is ever lost (continuous autosave; no loss on crash/kill). 3) AI assists, never surprises (previews, accept/undo; never silent rewrites/deletes). 4) The UI is the product (polish > features; 60/120 fps). 5) Local-first and private (only network = user's AI endpoint, only during AI features).

## 2. Platform realities
- Power button wake: impossible for 3rd-party apps → not built; onboarding tip for maker's side-key setting (Samsung: Settings → Advanced features → Side key → Double press → Open app).
- Volume buttons: only via opt-in Accessibility Service filtering key events, mostly screen-on. Optional: Volume Up+Down together ~0.4 s → quick capture. Explanation screen before permission.
- "Dynamic island": none on Android; Android 16 Live Updates (promoted ongoing notifications / progress-centric status-bar chip). Older: normal ongoing notification. Use microphone FGS + ongoing notification (chronometer, Stop). Android 16+: promoted ongoing / Live Update. Verify API names against current docs.
- Recording after Home: only if started while app visible in a microphone FGS. Always start from foreground UI.
- Widgets: fully supported (several sizes).
Quick-access priority: home-screen widgets → QS tile → launcher long-press shortcuts → lock-screen shortcut (where allowed) → volume-chord a11y service (opt-in).

## 3. Target setup
Kotlin, Jetpack Compose (own design system on M3 foundations, not stock Material look). minSdk 26, targetSdk 36 (latest stable at build time). Single-activity, MVVM + UDF, Hilt, Coroutines/Flow. Room + FTS5; media as files in app-private storage referenced by relative path. CameraX. Drawing: androidx.ink if stable else custom Compose Canvas with smoothed strokes (pressure/stylus). OCR: ML Kit on-device. STT: on-device SpeechRecognizer default; optional server transcription. WorkManager for AI jobs/retries. API key in Android Keystore-backed encryption; never in plaintext, logs, backups.

## 4. Scope
v1 (tonight): notes w/ rich text, voice notes w/ live-update, checklists/todos w/ voice creation, images/camera, links, drawings, search, widgets, QS tile, shortcuts, AI settings + core AI (§8), onboarding, backup/export.
v1.1 (stub/skip if short): volume-chord a11y service, assistant-role power-button long-press, semantic (embedding) search, handwriting-to-text, home-screen Live Update polish.
Not v1: cloud sync, accounts, collaboration, iOS, desktop.

## 5. Capture features and acceptance criteria
Note = list of blocks in one scrollable canvas. Block types: text (headings, bold, italic, bullets, numbered), checklist, image, camera capture, drawing, voice clip (waveform + transcript), link card, divider.
5.1 New note: opens ≤300 ms from widget, keyboard up, cursor ready. No title field; title from first line or AI. Auto-save on change (debounce ~500 ms) and pause/stop.
5.2 Capture bar: floating pill, 5 buttons Mic · Camera · Draw · Link · Checklist; insert block at cursor w/ spring animation. Same 5 as widget buttons/shortcuts opening app directly in that mode.
5.3 Camera: direct capture, "keep shooting" multiples, gallery pick. Optional Scan text mode: photo → OCR → offer insert.
5.4 Drawing: full-screen canvas; pen, highlighter, eraser, 6 colors, thickness slider, undo/redo, finger+stylus. Vector strokes (re-editable) + rendered preview image.
5.5 Links: paste or share URL (share-target for text/URLs/images); link card (title, site, thumbnail) fetched in background; offline plain-URL fallback.
5.6 Organization: pinned, tags (auto-suggested), optional light Folders, archive + trash (30 days), FTS across text, OCR, transcripts, link titles.

## 6. Voice notes and the Live Update
1) Tap Mic (app/widget/tile) → app opens, recording starts from foreground. 2) Recording sheet: live waveform, timer, Pause, Stop. 3) Microphone FGS owns recording; continues on Home/app switch/lock. 4) Ongoing notification "Recording · 00:42" + Stop; tap returns to note. Android 16+: request promotion to Live Update (status-bar chip). 5) Auto-save: audio written to disk continuously, crash-safe (PCM or short chunks), finalized to AAC/M4A at end. Saved when: Stop, app swiped away, service stopped by system, low storage (graceful stop). Orphaned recordings recovered on next launch as a note. 6) Clip appears immediately; transcript in background, soft fade-in.
Acceptance: record 2 min → Home → 30 s → open app → Stop → clip complete. Force-kill mid-recording → reopen → audio up to that moment recovered.
VANSH ADDENDUM: real phone supports Live Updates → do everything possible with it; make cool features accessible through it (beyond recording: e.g. timers, AI-job progress, reminders — see UNDERSTANDING.md).

## 7. Todo lists, including by voice
Checklists are a block type and a top-level Lists screen (progress rings). Items: drag reorder, swipe delete, one-level nesting, optional due date/time + local-notification reminder, check animation + haptic tick.
Voice → lists ("Dump & Sort"): tap mic in List mode and talk (e.g. "Need to buy milk, eggs and bread. For work, send the invoice to Rahul and fix the login bug by Friday. Also call mom in the evening."). Transcribe → AI splits into separate lists (Groceries, Work, Personal), extracts items/dates, decides new vs existing list by title matching. Preview card → one-tap confirm or edit. English, Hindi, Hinglish.
Date phrases ("kal shaam 5 baje", "tomorrow evening") resolve with device date/time/locale.

## 8. AI features
8.1 Settings → AI: Base URL, API key, Model name, Test connection. OpenAI-compatible POST {base_url}/chat/completions behind AiProvider interface. JSON-only structured responses; schema validation + 1 retry on invalid JSON; friendly offline/wrong-key errors. AI only on user action or opt-in automatic; WorkManager queue + retry; per-feature toggles; "Sent to your AI endpoint" disclosure. STT default on-device; optional /audio/transcriptions toggle (don't assume it exists).
8.2 Features (v1 unless noted): 1 Dump & Sort · 2 Snap to Structure (OCR→LLM structured fields: contact→Add to Contacts, event→calendar, receipt→items+total) · 3 Ask My Notes (FTS retrieve → LLM, cite source notes) · 4 Clean Up (transcript→tidy notes, before/after toggle; summarize/shorten/translate/fix grammar) · 5 Auto title+tags (1–3 tags, one-tap accept) · 6 Link Essence (3-line summary+key points) · 7 Loose Ends (weekly scan "need to/remember to/must" → tasks) · 8 Daily Recap (evening card: today's captures + tomorrow's due) · 9 Rewind (v1.1) · 10 Semantic search (v1.1) · 11 Handwriting→text (v1.1).
Every AI result dismissible + reversible (Undo ≥10 s, version history for AI-edited notes).

## 9. Design language (most important section)
Visual: Inter font bundled; scale Large Title 34 / Title 22 / Body 17 / Caption 13, tight tracking on large titles, system font scaling. Generous whitespace, large collapsing titles → compact top bar on scroll, grouped inset cards on soft background (light #F2F2F7/white; dark #000/#1C1C1E). Continuous squircle corners (custom shape, e.g. androidx.graphics.shapes); cards 20 dp, buttons fully rounded, sheets 28 dp. One accent (default warm yellow like Apple Notes, user-selectable), semantic destructive/success, full dark mode, dynamic color off by default. Frosted glass via RenderEffect blur (Android 12+), translucent solid fallback. Icons: one rounded thin-stroke set (Phosphor or Material Symbols Rounded, weight 300–400).
Motion: springs everywhere (~damping 0.8, medium stiffness; ~200 ms quick, ~350–450 ms large). Shared-element transitions (SharedTransitionLayout) note card→editor and capture button→created block. Predictive back, interruptible gestures. No ripples: press-scale 0.97 + dim. Bottom sheets w/ detents (medium/large), draggable, rubber-band. Haptics: check task, start/stop recording, pin, delete; respect system setting. Reduce-motion setting: fades instead of springs.
Key screens: 1 Home (large title "Notes", expanding search, pinned row, masonry/list cards, floating capture pill + new-note). 2 Editor (edge-to-edge canvas, minimal top bar, capture bar above keyboard, AI sparkle button → bottom sheet). 3 Recording sheet (big waveform, timer, Pause/Stop). 4 Lists (progress-ring cards, quick-add row). 5 Quick Capture (compact partial-height overlay from widget/tile/shortcut). 6 Settings (grouped inset lists, AI setup, quick-access setup, backup/export, appearance). 7 Onboarding (3 calm screens + guided widget/tile/(optional volume-chord) setup).
Perf: cold start → editable note ≤400 ms mid-range; no dropped frames; baseline profile; no main-thread work.

## 10. Widgets and quick access
Glance widgets: (a) Small "New note"; (b) Medium 5 capture buttons (Note, Voice, Camera, Draw, List); (c) Large = 5 buttons + 3 recent notes. Light/dark. QS tile: New note (tap=quick capture, long-press=app). Launcher shortcuts: New note, Voice note, Scan, New list. Share target: text, URLs, images. Volume-chord (opt-in, v1.1 if short): a11y service Volume Up+Down held → Quick Capture; clear explanation + easy off.

## 11. Data model
Note(id,title,createdAt,updatedAt,pinned,archived,deletedAt,folderId?,aiSummary?)
Block(id,noteId,type,orderIndex,payload JSON) — text, checklist, image, drawing, voice, link, divider
ChecklistItem(id, blockId|listId,text,done,dueAt?,parentId?,orderIndex)
TodoList(id,title,createdAt,source: manual|voice)
MediaAsset(id,kind,path,mimeType,durationMs?,width?,height?,transcript?,ocrText?)
Tag(id,name), NoteTag(noteId,tagId)
AiJob(id,type,targetId,status,resultJson?,createdAt)
NoteFts FTS5 over title,text,transcripts,OCR,link titles
Backup/export: ZIP (Room JSON export + media); Markdown-per-note export; import restores exactly.

## 12. Build order
M0 Foundation (project, Hilt, Room, nav, design system) — exit: styled demo runs.
M1 Notes core (list, editor text blocks, autosave, FTS search, pin/archive/trash) — exit: create/edit/search/restore.
M2 Media blocks (CameraX, gallery, drawing, link cards, share target) — exit: all block types survive restart.
M3 Voice + Live Update (mic FGS, crash-safe recording, recovery, ongoing notification + Android 16 promoted ongoing, on-device transcription) — exit: §6 acceptance.
M4 Quick access (3 widgets, QS tile, shortcuts, Quick Capture) — exit: each entry point ≤300 ms.
M5 Lists + voice lists (checklist block, Lists screen, reminders, voice list w/ preview; stub AI ok) — exit: §7 flows.
M6 AI (settings/test, provider, features 1–8, WorkManager queue, undo) — exit: works vs endpoint, graceful offline.
M7 Polish + release (animations, dark mode, reduce-motion, onboarding, backup/export, baseline profile, signed release APK, README).
M8 (if time): v1.1 items.
Protocol: Phase 0 first; small loops (60–90 min max/task); build+test+commit+log each; never >1 task without green build. State in files: PROGRESS.md, DECISIONS.md, BUILD_REPORT.md; resume via files + git log. Time boxes: >2× estimate or 2 fails → feature-flag stub, log, move on. Heartbeat to PROGRESS.md ≥ every 30 min. Milestone quality gate: build green, unit tests pass, no new lint errors, exit criterion shown by test/screenshot in docs/evidence. Safety rails: stay in project folder; no destructive git; stay within Phase-0 usage limits; no secrets to any service except Vansh's endpoint. Morning handoff: finish current task, write BUILD_REPORT.md, tag last green commit.

## 13. Testing & DoD
Unit: repositories, FTS queries, AI JSON parse/validation (incl. malformed), date-phrase resolution, voice-list split, recording recovery. UI: Compose tests — create note, each block type, create list, search. Manual checklist (MANUAL_TESTS.md) for emulator-impossible things: mic FGS after Home, Live Update chip on real Android 16, launcher widgets, QS tile, volume-chord, camera, haptics. Done: release APK without meaningful warnings; all unit tests pass; no crash in 5-min monkey; every v1 criterion met or listed incomplete.

## 14. Rules for the build agent
1 After approval never stop to ask; use §15 defaults; log new questions in PROGRESS.md. 2 Never block on failure: 2 serious attempts → feature-flag stub + log + next. 3 Commit every milestone/risky change. 4 Keep PROGRESS.md current. 5 No fake content/lorem/placeholder screens. 6 Never log/commit secrets. 7 Design review vs §9 before finishing. 8 Dawn deliverable: BUILD_REPORT.md (works/stubbed/bugs/manual tests/APK path).

## 15. Assumptions & open questions
Defaults: OpenAI-compatible endpoint pasted in Settings (nothing hardcoded); local-only; minSdk 26 + Android 16 Live Update when available w/ ongoing-notification fallback; app name placeholder Quick Notes.
Questions (Phase 0): 1 name/package/accent · 2 endpoint OpenAI-compatible? audio transcriptions? embeddings? languages (EN/HI/Hinglish)? · 3 test phone model + Android version · 4 app lock for private notes? · 5 volume-chord tonight or v1.1? · 6 signing: new keystore or provided; APK only or AAB too · 7 code location + push allowed? · 8 report time / hard stop / spend limit · 9 blocked → stub (default) or pause · 10 anything to remove/change/add.
