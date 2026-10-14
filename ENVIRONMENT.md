# Phase 0 — Environment Report

Date: 2026-10-14 · Agent: Cora (build agent) · Location: `/root/workspace-agent-server`

## Ready
| Item | Status |
| --- | --- |
| Internet | ✅ outbound HTTPS works (google.com 200, dl.google.com 200, repo1.maven.org 200) |
| Disk | ✅ 27 GB free on `/` (SDK + Gradle caches need ~6–8 GB; comfortable) |
| CPU / RAM | ⚠️ 4 vCPU, 3.8 GB RAM, no swap — enough for Gradle builds but **slow**; no emulator possible (see below) |
| git | ✅ 2.34.1 |
| GitHub access | ✅ credentials present; API reachable; authenticated as **Vansh1190** |
| apt package installs | ✅ works (verified with a dry run) — can install JDK, unzip, xz-utils, etc. |

## Missing (all fixable by me before M0; install steps pre-approved in Q-gate)
| Missing | Why it matters | Fix |
| --- | --- | --- |
| JDK / `java` | Gradle needs it | `apt-get install openjdk-17-jdk-headless` (verified available) |
| Android SDK (no `ANDROID_HOME`, no `adb`/`sdkmanager`) | everything | Download commandline-tools (~150 MB, confirmed reachable) → install `platform-tools`, `platforms;android-36`, `build-tools;36.x` |
| Gradle | build tool | Gradle wrapper (downloaded by project itself; nothing to preinstall) |
| `unzip`, `xz` | extracting SDK archives | apt |
| `kotlinc` | not needed — Gradle compiles Kotlin | none |

## Hard blockers (cannot be fixed here — these shape what "verified" means tonight)
1. **No emulator or device.** `/dev/kvm` does not exist, so no hardware-accelerated emulator; software-only emulation is unusable. **Consequence:** everything runtime-only is verified by compile + unit/Robolectric/Compose tests, and goes to `MANUAL_TESTS.md` for Vansh's phone: mic foreground service after Home, Live Update chip, widgets on launcher, QS tile, volume chord, camera, haptics, real-fps feel.
2. **AI endpoint details not supplied** (Q2/Q3 of the question list). I can build everything and test JSON parsing/validation against fixtures, but the spec's gate item "one successful call to your endpoint" **cannot be ticked until you provide base URL + key + model** — see `ASKS.md`, which is also a file on disk you can fill in and push.
3. **Session messaging:** I cannot see new chat messages after this one while you sleep; per the spec the state channel is files + git, so `ASKS.md` in the repo is the reliable way to reach me.

## Verified against live docs (spec asked to confirm API names)
Android 16 "Live Updates" = **promoted ongoing notifications**; the API is `Notification.ProgressStyle` (progress-centric style with points/segments) + promotion via `NotificationManager#setPromotedOngoing(notificationId, key, true)`, shown as a status-bar chip / in the ongoing-apps hub. Confirmed on developer.android.com (Android 16 features page) during Phase 0. Note: `ProgressStyle` docs mention rideshare/delivery/navigation use cases — promotion is system-discretionary, so we request it and keep a polished ongoing notification as fallback.
