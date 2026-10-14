# Questions for Vansh (Phase 0 — answer in one reply or fill this file in and push)

Every question has a recommended default; reply "**defaults**" to accept all.
Channel: chat reply now, **or** edit this file on GitHub (`Vansh1190/quick-notes`, branch `main`) — I poll it. A field that stays `TBD` at run start uses its default and gets logged in DECISIONS.md.

## A. Identity
A1. App name? → default **Quick Notes**
A2. applicationId / package? → default **com.vansh.quicknotes**
A3. Accent color? → default warm Apple-Notes yellow `#FFD60A`, user-selectable in Settings

## B. AI endpoint (needed to tick the Phase-0 test gate)
B1. Base URL: `TBD` (default: none — I build the full AI layer, test parsing with fixtures, and ship it untested-live; every AI feature degrades gracefully)
B2. API key (paste here or into app Settings — never committed): `TBD`
B3. Model name: `TBD`
B4. Is it OpenAI-compatible `/chat/completions`? → default **yes**
B5. Does it also support `/audio/transcriptions`? → default **no** (on-device SpeechRecognizer only)
B6. Embeddings (`/embeddings`) available? → default **no** (semantic search stays v1.1)
B7. Voice languages → default **English + Hindi + Hinglish**

## C. Device & testing
C1. Phone model + Android version for testing? (decides how much Live Update / shortcut behavior you'll actually see) → default **assume Android 16, API 36, Live Updates available** (per your §6 note)

## D. Feature scope calls
D1. Optional app lock (fingerprint/face) for private notes? → default **no in v1**
D2. Volume-chord Accessibility Service: tonight or v1.1? → default **v1.1** (spec already ranks it last; code behind a feature flag tonight if time allows)
D3. Anything in the spec to remove/change/add? → default **nothing further beyond your §6 Live-Updates addendum**

## E. Delivery
E1. Signing: I generate a new keystore **outside the repo** (`release.keystore` + `keystore.properties`, gitignored, password recorded only in DECISIONS.md local note)? → default **yes, new keystore**
E2. Outputs? → default **release APK + AAB**
E3. Where does code live, and may I push? → default **new private repo `Vansh1190/quick-notes`, push to `main` with my GitHub credentials**. (Existing `pixel-plumber-bros` is an unrelated web game — not touching it.)

## F. Run parameters
F1. Report ready by / hard stop time? → default **hard stop 08:00 UTC 2026-10-14** or when M7 completes, whichever first
F2. Usage/spend limit for this run? → default **none stated; I self-throttle with long-running builds and no wasteful retries**
F3. When blocked: stub behind feature flag and continue, or pause and wait? → default **stub + log + continue** (spec §14 rule 2)

## G. Live Updates addendum — my interpretation (confirm or correct)
You said: "I have a phone with live updates feature available, so whatever you can do with it just do it. Make cool features accessible through it."
Plan → treat Live Updates (promoted ongoing notifications, `Notification.ProgressStyle`) as a first-class surface, used for:
1. **Voice recording** (spec): chronometer chip + Stop action, tap returns to note.
2. **AI job progress**: Dump & Sort / Snap to Structure / batch jobs show a promoted chip with progress + Cancel; tap opens the preview card.
3. **Recording timer / quick timer**: start a voice memo chip straight from widget or QS tile.
4. **Daily Recap / Loose Ends digest**: promoted, tappable evening card.
5. **Upcoming-reminder glance**: next due task chip (only while a reminder countdown is legitimately running).
All promotion requests fail soft to a normal ongoing notification; nothing depends on the system granting promotion.

## P2 — push blocked at GitHub, not locally (final)

Repo `Vansh1190/quick-notes` is public and exists, but every token I have (4 distinct PATs,
all authenticating as `Vansh1190` on api.github.com) is rejected by the git endpoint:
`git-upload-pack`/`git-receive-pack` → 401, HTTPS push → `Permission to
Vansh1190/quick-notes.git denied to Vansh1190`. Owner-owned public repo + valid owner token +
still denied is GitHub's token-class restriction, not a repo/permission setting I can flip:
fine-grained PATs are refused git access where the account enforces 2FA unless the token was
elected through 2FA at creation.

**One-line fix:** create a **classic** PAT with scope `repo` (approve the 2FA prompt when
generating), paste it, and I'll push all 20 commits immediately.

Alternative (no token at all): `git push` from the local clone at
`/root/workspace-agent-server/quick-notes` — it is complete, clean, and carries every commit.

Everything else is finished and gated: 117 tests, lint 0 errors, signed APK (45 MB) + AAB
(26 MB) at `app/build/outputs/…`, docs complete.
