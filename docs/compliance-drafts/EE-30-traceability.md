# EE-30 — Traceability (DRAFT for human review)

Ticket → impact IDs → commits → tests → evidence. Branch `devin/ee-30-android17-reminders`, repository
`eashansinha/medTimer`, base `main` (v1.16.5, API 35). Not a claim of conformance with any standard.

| Ticket | IMP id | Change | Commit(s) | Test(s) | Evidence |
|---|---|---|---|---|---|
| EE-30 | — | Baseline measurement on `main` | `456bcb18` | 56 existing JVM tests | `docs/upgrade/baseline.md` |
| EE-30 | IMP-01…IMP-24 | Change impact assessment (before any code change) | `17b5812a` | — | `docs/upgrade-impact-android-17-reminders.md` |
| EE-30 | — | Gradle 9.6.0→9.8.1 wrapper, AGP 9.4.1 built-in Kotlin, Safe Args 2.10.2, Play Publisher 4.1.1, SonarQube disabled (AGP 9 variant API) | `e1b88654`, `7c1aa600`, `107c8967` | all JVM tests | `assembleDebug`, `lintDebug` clean |
| EE-30 | — | Dependency bumps (WorkManager 2.12.0, Room 2.8.5, AppCompat 1.8.0, Navigation 2.10.2, Material 1.14.0, Robolectric 4.17, …); `colorPrimary` R-class fix for Material 1.14 | `2aa97d2a`, `d562f295` | all JVM tests | 19 baseline `GradleDependency` lint errors resolved, not suppressed |
| EE-30 | — | `compileSdk = 37` | `969eb5d0` | build | `assembleDebug` |
| EE-30 | — | `targetSdk = 37` | `8046c253` | build + emulators | api35 install/launch, api37 install (verification report) |
| EE-30 | **IMP-07** (Safer Intents) | `SNOOZE_ACTION` declared in `ReminderProcessor` `<intent-filter>`; `VARIABLE_AMOUNT`/`CUSTOM_SNOOZE` via non-exported `activity-alias` `.NotificationActionActivity` (`ReminderProcessor` targets the alias); `NEXT_REMINDER_WIDGET_UPDATE` on `WidgetUpdateReceiver` (review follow-up) | `5a643ee4`, IMP-07 follow-up commit | `ReminderProcessorIntentUnitTest#test_allReminderProcessorActionsDeclaredInManifestFilter`, `#test_notificationActivityAliasAndWidgetReceiverActionsDeclaredInManifestFilter` (both fail on `main`) | api35 Snooze from notification (verification report) |
| EE-30 | IMP-20 (exact alarms) | no production change; test coverage of exact/inexact branch | `9011a126` | `RescheduleWorkExactAlarmUnitTest`, `SnoozeWorkBoundsUnitTest`, `ReminderSchedulerDstMonthBoundaryUnitTest`, `ReminderSchedulerCyclicUnitTest`, `RepeatReminderWorkLimitUnitTest` | `docs/upgrade/test-evidence.md` (RescheduleWork branches 25 %→83 %) |
| EE-30 | IMP-22 (PendingIntent flags) | no production change; intents verified explicit + immutable | `9011a126` | `ReminderProcessorIntentUnitTest` | test-evidence |
| EE-30 | IMP-23 (boot receiver) | no production change | `9011a126` | `AutostartUnitTest` | test-evidence (Autostart 3.8 %→100 % lines) |
| EE-30 | IMP-21, IMP-24 (notification permission / channels) | no production change | — | existing `ReminderWorkUnitTest`, emulator flow | api35 notification screenshots |
| EE-30 | IMP-01, IMP-05, IMP-06 (quotas, edge-to-edge, predictive back) | runtime check only | — | — | api35 flow; api37 UI not possible on this VM (see verification report) |
| EE-30 | IMP-11, IMP-12, IMP-13 and all other "No" rows | not affected, with evidence | `17b5812a` | — | search evidence in impact assessment |
| EE-30 | — | JaCoCo class dirs for built-in Kotlin | `2602cc75` | — | test-evidence (without it Kotlin classes report 0 %) |
| EE-30 | — | CI instrumented tests on API 37 | `062beda9`, `321ddbd0`, `5f6e3a56`, `832cfd1a` | GitHub Actions `test.yml` | API 37 job boots but system_server dies (4 runs, verification report §7); API 28/34 `compatibilityTest.yml` 4/4 passed on the same commits |
| EE-30 | IMP-12 (test stack) | AndroidX Test / Espresso pinned back to 3.6.1 / 1.6.x | `ec979393` | `connectedDebugAndroidTest` on api35: 21/21 (was 15/21 failing with Espresso 3.7.0) | `docs/upgrade/evidence/api35-connectedDebugAndroidTest.xml` |
| EE-30 | — | Emulator evidence | — | manual flow + recording | `docs/upgrade/evidence/api35-0{1..5}-*.png`, `api35-reminder-flow.mp4`; api37 `adb install` result in verification report |
| EE-30 | — | Docs: test evidence, architecture, change record, verification report, traceability, changelog, README | `0faa107b`, (this commit) | — | this directory |
