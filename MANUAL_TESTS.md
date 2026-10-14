# MANUAL_TESTS.md

Things only real hardware can prove (mic, camera, FGS, notifications, stylus). No emulator on
the build box, so these are for Vansh's phone. Build the debug APK and install. Each line
states what to do and what you should see.

## M1 — Notes core
1. New note: tap **+** → keyboard opens with cursor in the body.
2. Autosave: type, wait ~1s, back out, reopen — text persisted (500 ms debounce, coalesced).
3. FTS search: Home → tap Search → type a word from a voice transcript or a saved link's
   title → note appears (FTS5 over text + transcripts + link metadata).
4. Pin: long-press a card → Pin → it shows in the Pinned row at top.
5. Archive/Trash: Archive → gone from Home; Trash (if nav wired) → Restore brings it back.

## M2 — Media
6. Camera: capture bar → Camera → shutter → thumbnail strip → Done → photo is a note block.
7. Gallery: Photo button → system picker → image becomes a block.
8. Drawing: Draw → finger + (if you have one) **stylus** strokes; pressure should vary width
   on a stylus. 6 colors, width slider, Pen/Marker/Erase, Undo/Redo. Done → block with preview.
9. Eraser removes whole strokes it touches.
10. Link: capture bar → Link → paste a URL → card fetches title + site name; airplane mode →
    still saves a plain-URL card (no crash).
11. Share into Notes: share **text** from another app → new note. Share a **single URL** →
    link card. Share an **image** → image note.

## M3 — Voice / recording
12. First recording asks for **Microphone** then **Notifications** permission (Android 13+).
13. Record → big waveform + timer; **Stop** → voice block with duration in the note.
14. Home-screen mic records a **new** note with just the voice block.
15. Recording keeps going with the screen off / app backgrounded (mic foreground service);
    the ongoing notification has **Stop** and a live timer.
16. **Android 16 Live Update:** while recording, a progress chip should appear in the status
    bar / lock-screen Live area (system-discretionary; we request promoted-ongoing + a
    determinate progress). If it never appears, note the device build.
17. Pause/Resume: pause shows **▶**; the timer freezes while paused and resumes.
18. Crash-safety: start a recording, then **force-kill the app from Recents** (Don't save);
    relaunch → the audio is recovered as a "Recovered recording" note (chunked PCM).
19. Very short (<0.3 s) recordings are discarded silently, no empty note.
20. Low storage: with <4 chunks free the recorder stops itself gracefully (hard to test).

## Global
21. Rotate mid-recording: service keeps recording; reopening Home re-attaches the sheet state.
22. App theme: Light + Dark both correct; accent default Apple Notes yellow; reduce-motion
    system setting swaps springs for fades.
