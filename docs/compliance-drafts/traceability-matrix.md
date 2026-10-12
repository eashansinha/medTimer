# Traceability Matrix — EE-32 Android 17 upgrade

**Status: DRAFT for human review.** No claim of compliance with IEC 62304, FDA guidance or any other standard. Commit hashes are on branch `devin/ee-32-android-17-upgrade`; "PR" means the final commits of the pull request (hashes listed in the PR description).

## Ticket → impact items → commits → tests → evidence

| Ticket | IMP id(s) | Change | Commit(s) | Test(s) | Evidence |
|---|---|---|---|---|---|
| EE-32 | — (baseline) | Baseline measurement on `main` | `d6405c76` | existing 56 JVM tests; 21 instrumented (API 35) | `docs/upgrade/baseline.md` |
| EE-32 | IMP-01 … IMP-55 | Change impact assessment written before code | `82864342` (+ correction in `cad64932`) | — | `docs/upgrade-impact-android-17.md` |
| EE-32 | IMP-55 | Gradle 9.6.0 → 9.8.1, AGP 9.4.1, Kotlin 2.4.21 built-in, Room/Nav/Play plugin versions | `06ee82e0`, `e1139d7d` | `./gradlew assembleDebug testDebugUnitTest lintDebug` | `docs/upgrade/test-evidence.md` §build |
| EE-32 | IMP-14, IMP-15 (WorkManager 2.12), lint `GradleDependency` | Dependency bumps; `colorPrimary` attr via appcompat | `3c1ecf0a` | full JVM suite (65) + instrumented suite | `docs/upgrade/test-evidence.md` |
| EE-32 | — | `compileSdk` 37 (+ core-ktx 1.19.1) | `c3589585` | `assembleDebug` | build log quoted in PR |
| EE-32 | all "Yes"/"Needs runtime check" rows | `targetSdk` 37 | `7e9b24f8` | JVM suite, instrumented suite (API 35), CI (API 37) | `docs/upgrade/test-evidence.md`, `docs/upgrade/evidence/` |
| EE-32 | IMP-55 | SonarQube plugin removed (incompatible with AGP 9) | `e1139d7d` | lint green | commit message, impact doc §1 |
| EE-32 | IMP-01, IMP-02, IMP-20, IMP-27 | Instrumented edge-to-edge insets + system back checks | `cad64932` | `Android17BehaviorTest.edgeToEdgeInsetsKeepToolbarAndBottomNavigationVisible`, `Android17BehaviorTest.systemBackReturnsToOverview` | API 35 connected run result in `docs/upgrade/test-evidence.md`; CI API 37 run (PR) |
| EE-32 | IMP-52 | Exact vs inexact alarm scheduling pinned | `a1a130ca` | `RescheduleWorkUnitTest` (4 tests) | JaCoCo: `RescheduleWork` 27→37 / 56 lines; API 35 flow screenshots `api35-06…`, `api35-07…`, `api35-08…` |
| EE-32 | IMP-53 | Boot / package-replaced reschedule pinned | `a1a130ca` | `AutostartUnitTest` (3 tests) | JaCoCo: `Autostart` 1→15 / 26 lines |
| EE-32 | IMP-26 | Widget RemoteViews construction pinned | `a1a130ca` | `WidgetImplTest` (2 tests) | JaCoCo: `WidgetImpl` 0→41 / 42 lines |
| EE-32 | IMP-28, IMP-17 | Reflection-based backup validation (no change) | — | existing `JSONMedicineBackupUnitTest`, `JSONReminderEventBackupUnitTest`, `JSONBackupFuzzTest` | pass in JVM suite |
| EE-32 | IMP-34, IMP-40 | Notification posting / actions on device | — | existing `NotificationTest`, `ReminderTest` (instrumented) | API 35 connected run; `api35-08-notification-shade.png` |
| EE-32 | coverage | Unit-only JaCoCo task; AGP 9 class dirs | `a1a130ca`, `7a1d0231` | `./gradlew jacocoUnitTestReport` | `docs/upgrade/test-evidence.md` |
| EE-32 | — | CI on API 37 with app-launch recording artifact | PR | GitHub Actions `Test` workflow | run URL + artifact names in PR / verification report |
| EE-32 | — | README, CHANGELOG, architecture, compliance drafts | PR | — | this directory |

## Requirement coverage (ticket acceptance criteria)

| Acceptance criterion (EE-32) | Where satisfied |
|---|---|
| One PR into `main` with ordered, readable commits and a traceability table | PR description; this matrix |
| Every reported number comes from a command actually run and quoted | `docs/upgrade/baseline.md`, `docs/upgrade/test-evidence.md`, PR |
| Pre-existing issues recorded, not hidden | lint 19 errors on `main`; `TagTest.tagHandling` failure (API 35) — baseline + verification report |
| Nothing merged by Devin | PR left open for human review |
