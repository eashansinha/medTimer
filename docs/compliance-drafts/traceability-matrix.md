# Traceability matrix - EE-29 Android 17 (API 37) upgrade

**DRAFT for human review.** Engineering input only; no claim of compliance with IEC 62304, FDA guidance or any other standard.
Commit hashes refer to branch `devin/ee-29-android-17-upgrade` (fork `eashansinha/medTimer`). IMP ids refer to
[`docs/upgrade-impact-android-17.md`](../upgrade-impact-android-17.md). Evidence paths are relative to the repository root.

## Ticket → requirement → IMP → commit → test → evidence

| Req | Requirement (from EE-29) | IMP ids | Commit(s) | Test(s) | Evidence |
|---|---|---|---|---|---|
| R1 | Impact assessment before any code change | IMP-01 … IMP-51 | `cc4b1828` | - | `docs/upgrade-impact-android-17.md` (committed before `f5612b0b`) |
| R2 | Baseline measurements recorded, pre-existing failures not hidden | - | `b3795282` | 56 JVM tests, 21 instrumented (API 35) | `docs/upgrade/baseline.md` (lint 19 errors, `TagTest.tagHandling` failure) |
| R3 | Toolchain upgrade (Gradle / AGP / Kotlin) | toolchain section | `f5612b0b`, `1935dff8` | all unit tests, `assembleDebug` | `docs/upgrade/test-evidence.md` §2 |
| R4 | Dependency bumps required by the toolchain | IMP-14 (WorkManager) | `7fb639b7`, `876c4513`, `2ba2b181`, `3ecbb8b7` | unit + instrumented suites | `docs/upgrade/test-evidence.md`, `docs/compliance-drafts/soup-list.md` |
| R5 | compileSdk 37 | - | `7fb7e994` | `assembleDebug`, unit tests | `docs/upgrade/test-evidence.md` |
| R6 | targetSdk 37, minSdk 28 | all "applies" rows | `54f166a5` | unit tests, instrumented suite (API 35), CI (API 37) | `adb shell dumpsys package` output in `docs/upgrade/test-evidence.md` §5 (targetSdk=37, minSdk=28) |
| R7 | Edge-to-edge opt-out removal handled | IMP-01 | `c977631e` | instrumented suite (API 35: toolbar/bottom bar positions), CI launch recording (API 37) | `docs/upgrade/evidence/api35-01-launch-overview.png`, `api35-04-overview-next-reminder.png` |
| R8 | Predictive back | IMP-02, IMP-20 | none needed (already AndroidX callback + manifest flag) | `BasicUITest`, `ReminderTest` (pressBack) on API 35 | `docs/upgrade/test-evidence.md` §4 |
| R9 | Exact alarms / SCHEDULE_EXACT_ALARM, reminder scheduling | IMP-11, IMP-14 | `d9a118a5` | `RescheduleWorkUnitTest` (4), existing `ReminderScheduler*UnitTest` | `docs/upgrade/evidence/api35-05-notification.png` (reminder fired at the scheduled minute) |
| R10 | Boot receiver / WorkManager rescheduling | IMP-14, IMP-15 | `d9a118a5` | `AutostartUnitTest` (3) | coverage `Autostart` 1/26 → 26/26 lines |
| R11 | Notifications and actions | IMP-25, IMP-39, IMP-50 | `211b0e89` | `NotificationSoundManagerUnitTest` (4), `NotificationTest` ×5 (API 35) | `api35-05-notification.png` (Taken / Snooze actions), API 35 instrumented run |
| R12 | App widgets (RemoteViews) | IMP-25 | none needed (text-only widgets) | `ScreenshotsTest` (API 35) | impact table row IMP-25 (search evidence) |
| R13 | Biometric lock | IMP-27, FingerprintManager removal | none needed (androidx.biometric) | - | impact doc toolchain outcome (API 37 stubs lack `FingerprintManager`) |
| R14 | Backup/import reflection | IMP-17, IMP-27 | none needed | `JSONMedicineBackupUnitTest`, `JSONReminderEventBackupUnitTest`, `JSONBackupFuzzTest`, `ExportBackupTest` (API 35) | unit + instrumented results |
| R15 | Large-screen orientation restrictions | IMP-05 | none needed (no restrictions declared) | - | impact table row IMP-05 (manifest search) |
| R16 | Measured coverage before/after | - | `f7758774` (JaCoCo tasks) | `jacocoUnitTestReport` | `docs/upgrade/baseline.md`, `docs/upgrade/test-evidence.md` |
| R17 | Emulator evidence (recording + screenshots, instrumented result) | - | docs commit | `connectedDebugAndroidTest` on API 35 | `docs/upgrade/evidence/api35-demo.mp4`, 6 screenshots, `test-evidence.md` §4 |
| R18 | Android 17 instrumented evidence in CI | - | CI commit (`test.yml`) | GitHub Actions `Test` workflow on API 37.0 | run URL and artifact names in `docs/upgrade/test-evidence.md` §6 / PR description |
| R19 | Documentation (README, CHANGELOG, architecture, compliance drafts) | - | docs commit | - | `README.md`, `CHANGELOG.md`, `docs/architecture.md`, `docs/compliance-drafts/*` |

## IMP → status

| IMP | Status | Where |
|---|---|---|
| IMP-01 | Fixed | `c977631e` |
| IMP-02 / IMP-20 / IMP-21 | Already compliant, verified by inspection + instrumented tests | impact doc |
| IMP-11 / IMP-14 / IMP-15 | No code change; tests added | `d9a118a5` |
| IMP-39 / IMP-50 | Mitigated (restore logic); OS-level effect unverified on device | `211b0e89`, open item |
| IMP-05, IMP-17, IMP-23, IMP-25, IMP-27, IMP-33, IMP-42, IMP-48 | Needs runtime check - partially covered by API 37 CI run | open items |
| all other IMP rows | Not applicable, evidence in impact table | impact doc §4 |
