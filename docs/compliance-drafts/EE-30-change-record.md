# EE-30 — Change record (DRAFT for human review)

> Draft input for the project's own change-control process. It does **not** claim conformance with IEC 62304,
> FDA guidance or any other standard; reviewers decide what, if anything, is adopted.

| Field | Content |
|---|---|
| Change ID | EE-30 (Jira, project EE) |
| Title | Modernize the reminder scheduling & notification module for Android 17 (API 37) |
| Description | Raise `compileSdk`/`targetSdk` from 35 to 37 with the toolchain required for it (Gradle 9.8.1, AGP 9.4.1 built-in Kotlin, dependency bumps), assess every Android 16/17 behaviour change against the reminder module, apply the one required code change (IMP-07: declare `SNOOZE_ACTION` in the `ReminderProcessor` intent filter), and add JVM tests for the module's least-covered OS-sensitive logic. Reminder behaviour is intentionally unchanged. |
| Reason | `main` targets API 35; Android 16/17 enforce Safer Intents, new quotas and memory limits for apps targeting ≥ 36. Store policy will require a current `targetSdk`. The module (exact alarms, broadcasts, WorkManager, notifications, `PendingIntent`s) is the most OS-sensitive code in the app. |
| Affected items | `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/*`, `gradlew*`, `app/src/main/AndroidManifest.xml` (receiver filter), `MedTimerAppIntro.kt`, `OverviewFragment.java`, `TakenSkippedChart.java`, `MedicinePerDayChart.java` (`colorPrimary` attribute source only), `.github/workflows/test.yml`, new tests under `app/src/test`, docs under `docs/`. No change to scheduling, alarm, notification or receiver *logic*. |
| Risk assessment | See `docs/upgrade-impact-android-17-reminders.md` (IMP-01…IMP-24, each with H/M/L and rationale). Highest residual: Android 16/17 runtime behaviour (quotas, Safer Intents) can only be partly exercised — API 37 UI is not available on the build VM. Toolchain risk: AGP 9 built-in Kotlin and Material 1.14 changed R-class exposure (fixed) and SonarQube is disabled on the branch. |
| Required verification | JVM tests green with coverage not lower than baseline; `lintDebug` clean; APK installs and launches on API 35 and installs on API 37; reminder → notification → Snooze/Taken flow on API 35; instrumented tests on API 35 locally and API 37 in CI. |
| Verification performed | `docs/compliance-drafts/EE-30-verification-report.md` (actual commands, outputs, API levels). Summary: 91/91 JVM tests, line coverage `reminders` 62.6 %→67.6 %, whole app 11.1 %→12.1 %. |
| Residual risk | Android 17 UI behaviour not observed on a device/emulator with a working display; SonarQube quality gate not run on this branch; Robolectric runs at SDK 34, not 37. |
| Rollback | Revert the branch (all changes are additive or version bumps; no data migration, no Room schema change). Individual IMP commits can be reverted independently; the toolchain commits must be reverted together (AGP 9 ↔ Gradle 9.6+ ↔ Safe Args 2.10). |
| Sign-off | Author: Devin (session linked in PR) · Technical reviewer: ______ · Approver: ______ · Date: ______ |
