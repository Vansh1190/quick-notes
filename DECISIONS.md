# DECISIONS.md — every answer/assumption with date

| # | Date | Topic | Decision | Source |
| --- | --- | --- | --- | --- |
| D0 | 2026-10-14 | Process | Phase 0 gate enforced: environment report + one question message + Understanding Summary posted; no app code until "approved" | spec §0 |
| D1 | 2026-10-14 | Repo | Work in NEW repo `quick-notes` (local: `/root/workspace-agent-server/quick-notes`; remote target `Vansh1190/quick-notes` pending E3 answer). `pixel-plumber-bros` is an unrelated web game — untouched | agent, Phase 0 finding |
| D2 | 2026-10-14 | Toolchain | Install OpenJDK 17 + Android SDK cmdline-tools/platforms;36/build-tools via apt+dl.google.com (all confirmed reachable). No emulator possible (no KVM) → verification = unit/Robolectric/Compose tests + manual list | ENVIRONMENT.md |
| D3 | 2026-10-14 | Live Updates API | Android 16 "Live Updates" = promoted ongoing notifications: `Notification.ProgressStyle` + `NotificationManager.setPromotedOngoing` (verified on developer.android.com). Used for recording, AI-job progress, recap, reminder glance per Vansh's §6 addendum; always fails soft to normal ongoing notification | spec §6 addendum + docs |
| D4 | 2026-10-14 | Pending answers | App name "Quick Notes" / id com.vansh.quicknotes / accent #FFD60A; OpenAI-compatible endpoint assumed; EN+HI+Hinglish; Android 16 test device assumed; no app lock in v1; volume chord → v1.1; new keystore outside repo; APK+AAB; stub-and-continue when blocked; hard stop 08:00 UTC — **all provisional defaults, confirm in ASKS.md** | spec §15 |
| D5 | 2026-10-14 | Secrets | No endpoint credentials received yet. None hardcoded anywhere. Live AI test deferred until ASKS.md B1–B3 filled or entered in Settings | spec §0.3 |
