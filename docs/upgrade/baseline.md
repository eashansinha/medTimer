# EE-30 — Baseline measurement on `main` (v1.16.5, Android 15 / API 35)

**DRAFT — input for human review.** All numbers below come from commands run on this
branch's parent commit (`main` @ `8fde0b91`, "Merge pull request #530 …") before any
code change. No source file was modified for this measurement.

## Environment

| Item | Value |
|---|---|
| Commit measured | `8fde0b91` (`main`, v1.16.5) |
| JDK | OpenJDK 17.0.20.1 (Ubuntu 22.04) |
| Gradle wrapper | 8.11.1 |
| Android Gradle Plugin | 8.9.0 |
| Kotlin | 2.1.10 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 28 |
| Android SDK platforms installed | android-35, android-37.0 |

## Commands

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug --continue   # build + JVM tests + lint
./gradlew JacocoDebugCodeCoverage -x connectedDebugAndroidTest   # unit-test-only JaCoCo report
```

## Results

| Check | Result |
|---|---|
| `assembleDebug` | **SUCCESS** — `app/build/outputs/apk/debug/MedTimer-debug.apk` |
| `testDebugUnitTest` | **56 tests, 0 failures, 0 errors, 0 skipped** (sum of `tests`/`failures`/`errors` attributes over `app/build/test-results/testDebugUnitTest/*.xml`) |
| `lintDebug` | **FAILED — 19 errors, 0 warnings**, all `GradleDependency` ("A newer version of … is available"). Pre-existing on the untouched baseline; recorded here, not suppressed. |
| Fuzz tests (`*FuzzTest`) | excluded by default (`-Dfuzzing=true` not set) — same as CI default |

### Pre-existing lint errors (GradleDependency, 19)

appcompat 1.7.0→1.8.0, constraintlayout 2.2.1→2.2.2, core-ktx 1.15.0→1.19.1,
lifecycle-viewmodel-ktx 2.8.7→2.11.0, navigation-fragment-ktx / navigation-ui-ktx 2.8.9→2.10.2,
room-runtime / room-compiler 2.6.1→2.8.5, espresso-core / -contrib / -idling-resource /
idling-concurrent 3.6.1→3.7.0, test ext junit 1.2.1→1.3.0, uiautomator 2.3.0→2.4.0,
orchestrator 1.5.1→1.6.1, test rules 1.6.1→1.7.0, test runner 1.6.2→1.7.0,
work-runtime 2.10.0→2.12.0, material 1.12.0→1.14.0.

## Coverage (JaCoCo, JVM unit tests only — no instrumented tests included)

Source: `app/build/reports/jacoco/JacocoDebugCodeCoverage/JacocoDebugCodeCoverage.xml`
(copy kept as evidence input; counters are `covered / total (percent)`).

| Scope | Lines | Branches | Instructions |
|---|---|---|---|
| Whole app | 768 / 6934 (11.1 %) | 260 / 2044 (12.7 %) | 4109 / 33347 (12.3 %) |
| `com.futsch1.medtimer.reminders` | 305 / 487 (62.6 %) | 55 / 144 (38.2 %) | 1425 / 2320 (61.4 %) |
| `com.futsch1.medtimer.reminders.scheduling` | 145 / 150 (96.7 %) | 90 / 95 (94.7 %) | 823 / 865 (95.1 %) |
| `com.futsch1.medtimer` (root pkg: `Autostart`, `MainActivity`, …) | 28 / 601 (4.7 %) | 2 / 162 (1.2 %) | 152 / 2793 (5.4 %) |

### Per class — `reminders`

| Class | Lines | Branches |
|---|---|---|
| ReminderProcessor | 46 / 95 (48.4 %) | 4 / 14 (28.6 %) |
| RescheduleWork | 27 / 56 (48.2 %) | 3 / 12 (25.0 %) |
| RescheduleWork$1 (TimeAccess) | 0 / 3 (0 %) | – |
| Notifications | 65 / 98 (66.3 %) | 13 / 32 (40.6 %) |
| NotificationAction | 34 / 35 (97.1 %) | 10 / 16 (62.5 %) |
| ReminderWork | 65 / 66 (98.5 %) | 18 / 26 (69.2 %) |
| SnoozeWork | 13 / 13 (100 %) | – |
| RepeatReminderWork | 18 / 18 (100 %) | 1 / 2 (50 %) |
| PendingIntentBuilder | 12 / 12 (100 %) | – |
| WeekendMode | 14 / 14 (100 %) | 6 / 6 (100 %) |
| NotificationWork / TakenWork / SkippedWork | 100 % | – |
| NotificationSoundManager (+Companion) | 1 / 44 (2.3 %) | 0 / 20 (0 %) |
| StockHandlingWork | 0 / 22 (0 %) | 0 / 16 (0 %) |

### Per class — `reminders.scheduling`

| Class | Lines | Branches |
|---|---|---|
| StandardScheduling | 68 / 68 (100 %) | 48 / 48 (100 %) |
| IntervalScheduling | 28 / 28 (100 %) | 13 / 14 (92.9 %) |
| LinkedScheduling | 14 / 14 (100 %) | 8 / 8 (100 %) |
| ReminderScheduler | 22 / 23 (95.7 %) | 9 / 10 (90 %) |
| SchedulingFactory | 5 / 5 (100 %) | 4 / 5 (80 %) |
| SchedulingKt | 6 / 6 (100 %) | 7 / 8 (87.5 %) |
| CyclesHelper | 2 / 6 (33.3 %) | 1 / 2 (50 %) |

### Not covered by any JVM test on the baseline (relevant to EE-30)

`Autostart` (0 %), `RescheduleWork` exact-alarm branch (`setExactAndAllowWhileIdle`, lines
106–110 only reach the inexact branch), `ReminderProcessor.onReceive` action dispatch,
`CyclesHelper.getCycleCountString` cyclic branch, `Notifications.buildActions` variants
(`dismiss_notification_action` = 1 / 2, custom snooze `-1`, variable amount).

## Known limitations of this baseline

* Instrumented tests (`connectedDebugAndroidTest`) were **not** part of this measurement; they
  need an emulator and are reported separately in `docs/upgrade/test-evidence.md`.
* Coverage percentages are JaCoCo *line/branch* counters, not statement coverage.
