# Change Request and Impact Analysis — MedTimer 1.17 (Android 15 → Android 17)

**Status: DRAFT for human review.** This document is an input a regulated software team could reuse; it does **not** claim compliance with IEC 62304, FDA guidance, ISO 14971 or any other standard. Risk ratings are engineering judgements by the author of the change, not a formal risk analysis.

## 1. Change request

| Field | Value |
|---|---|
| Ticket | [EE-32 — Upgrade MedTimer from Android 15 (API 35) to Android 17 (API 37) — release 1.17](https://cog-gtm.atlassian.net/browse/EE-32) |
| Repository / branch | `eashansinha/medTimer`, branch `devin/ee-32-android-17-upgrade` from `main` @ `8fde0b91` (v1.16.5) |
| Requested change | Raise `compileSdk`/`targetSdk` from 35 to 37, keep `minSdk` 28; upgrade the build toolchain accordingly; assess and handle Android 16/17 behavior changes; improve automated tests; provide emulator evidence and draft documentation |
| Change type | Platform / toolchain upgrade (no functional feature change intended) |
| Requester | Jira automation label `devin-android-upgrade` on EE-32 |
| Implementer | Devin (AI), session https://devin-gtm.devinenterprise.com/sessions/fa1e3744c73d4e1ca223e6c405b3bc8b |
| Reviewer / approver | *human reviewer — open* |

## 2. Reason for change

Google Play and the Android platform require apps to target recent API levels; Android 16 and 17 introduce behavior changes that take effect once `targetSdk` ≥ 36/37 (edge-to-edge enforcement, predictive back, widget memory limits, local-network permission, etc.). The app's core function — medication reminders through exact alarms and notifications — must keep working unchanged on Android 17 devices.

## 3. Items affected

| Item | Change | Reference |
|---|---|---|
| Build configuration | `gradle/wrapper/*`, `build.gradle.kts`, `app/build.gradle.kts` — Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21 (built-in), Room 2.8.5, Navigation 2.10.2, Play publisher 4.1.1, SonarQube plugin removed, JaCoCo 0.8.15 + `jacocoUnitTestReport`, dependency bumps, `compileSdk`/`targetSdk` 37 | commits `06ee82e0`, `3c1ecf0a`, `c3589585`, `7e9b24f8`, `e1139d7d`, `7a1d0231` |
| Production source | `MedTimerAppIntro.kt`, `OverviewFragment.java`, `TakenSkippedChart.java`, `MedicinePerDayChart.java`: `colorPrimary` attribute read via `androidx.appcompat.R.attr` (Material 1.14 R-class change). **No other production code change.** | commit `3c1ecf0a` |
| Tests | new `RescheduleWorkUnitTest`, `AutostartUnitTest`, `WidgetImplTest` (JVM); new `Android17BehaviorTest` (instrumented) | commits `a1a130ca`, `cad64932` |
| CI | `.github/workflows/test.yml`: API 37 emulator, app-launch screen recording artifact, SonarQube step removed | see PR |
| Documentation | `docs/upgrade-impact-android-17.md`, `docs/upgrade/*`, `docs/architecture.md`, `docs/compliance-drafts/*`, `README.md`, `CHANGELOG.md` | see PR |

## 4. Impact analysis

The full per-behavior-change analysis (IMP-01 … IMP-55, with code evidence, risk and verification per row) is in [`docs/upgrade-impact-android-17.md`](../upgrade-impact-android-17.md). Summary:

| Category | IMP ids | Result |
|---|---|---|
| Applies, already handled by existing code — verified by new tests | IMP-01 (edge-to-edge), IMP-02/IMP-20 (predictive back) | no production change; `Android17BehaviorTest` |
| Needs runtime check — verified on device / CI | IMP-05, IMP-14, IMP-15, IMP-26, IMP-27, IMP-34, IMP-43, IMP-46, IMP-49, IMP-52, IMP-53 | API 35 emulator flow + instrumented suite; API 37 via CI (see verification report) |
| Not affected — evidence in table | all remaining rows | no network, native code, Bluetooth, SMS, Keystore, Contacts, MediaStore, periodic jobs or reflective writes in the app |
| Toolchain | IMP-55 | AGP 9 migration done; SonarQube plugin removed (incompatible) |

### Risk summary (engineering judgement)

| Area | Risk before mitigation | Mitigation | Residual |
|---|---|---|---|
| Exact alarm scheduling / reminders missed (IMP-52) | High (core function) | No code change in the path; new `RescheduleWorkUnitTest` (4 tests) pins exact/inexact decision; API 35 device flow shows a scheduled reminder firing as a notification | Low — Android 17 device behavior only verified through CI emulator (see open items) |
| Notification tap / actions (IMP-34) | Medium | existing `NotificationTest`, `ReminderTest` (instrumented) + manual API 35 flow | Low |
| UI overlapped by system bars (IMP-01) | Medium | `Android17BehaviorTest.edgeToEdgeInsets…` | Low |
| Build/toolchain regressions (IMP-55) | Medium | ordered commits each verified with `assembleDebug`; final `assembleDebug testDebugUnitTest lintDebug` green | Low |
| Third-party library behavior changes (Material 1.14, Room 2.8, WorkManager 2.12) | Medium | full unit + instrumented suites; visual check in recording | Medium — no dedicated regression tests for charts/PDF export |

## 5. Verification approach

See [`verification-report.md`](verification-report.md) and [`../upgrade/test-evidence.md`](../upgrade/test-evidence.md).

## 6. Rollback

See the rollback plan in the impact assessment (§6): revert the PR, or revert the `targetSdk 37` commit alone; no database schema change.
