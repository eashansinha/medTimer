# Baseline measurements on `main` (v1.16.5, Android 15 / API 35)

**DRAFT for human review.** All numbers below come from the commands listed; nothing is estimated.

- Date: 2026-10-12 (UTC)
- Commit: `8fde0b91` (`main`, "Merge pull request #530 …"), clean working tree, no code changes
- Ticket: [EE-29](https://cog-gtm.atlassian.net/browse/EE-29)

## Tool versions

| Tool | Version |
|---|---|
| JDK | OpenJDK 17.0.20.1 (`java -version`) |
| Gradle wrapper | 8.11.1 (`gradle/wrapper/gradle-wrapper.properties`) |
| Android Gradle Plugin | 8.9.0 (`build.gradle.kts`) |
| Kotlin Gradle plugin | 2.1.10 (`org.jetbrains.kotlin.android`) |
| compileSdk / targetSdk / minSdk | 35 / 35 / 28 (`app/build.gradle.kts`) |
| Build tools used | 35.0.0 (AGP 8.9 default; SDK also has 37.0.0) |
| Android SDK platforms installed | android-35, android-37.0 |
| Emulator for instrumented run | AVD `api35` (Android 15, API 35, google_apis x86_64), headless `-no-window -gpu swiftshader_indirect` |

## Commands

```bash
# 1. build + unit tests + lint (one invocation, --continue so lint runs after tests)
./gradlew assembleDebug testDebugUnitTest lintDebug --continue

# 2. unit-test-only JaCoCo report (no device needed). The repo's JacocoDebugCodeCoverage
#    task depends on connectedDebugAndroidTest, so a temporary init script registered a
#    unit-only JacocoReport task (same class/source dirs and exclusions as the repo task):
./gradlew -I /tmp/init/jacoco-unit.gradle :app:jacocoUnitTestReportBaseline
#    -> app/build/reports/jacoco/jacocoUnitTestReportBaseline/{html,jacocoUnitTestReportBaseline.csv,.xml}
#    (the same task is added permanently to app/build.gradle.kts on the upgrade branch)

# 3. instrumented suite on the API 35 AVD
./gradlew connectedDebugAndroidTest
```

## Results

| Measurement | Result |
|---|---|
| `assembleDebug` | **SUCCESS** → `app/build/outputs/apk/debug/MedTimer-debug.apk` |
| `testDebugUnitTest` (JVM, JUnit 5 + Robolectric) | **56 tests, 56 passed, 0 failed, 0 skipped** |
| `lintDebug` | **FAILED — 19 errors, 0 warnings** (pre-existing, see below) |
| JaCoCo unit-test line coverage (overall) | **10.96 %** (764 / 6 970 lines) |
| JaCoCo unit-test branch coverage (overall) | **12.72 %** (259 / 2 036 branches) |
| Classes in report | 264 (R/BuildConfig/Args/Directions/test classes excluded) |
| `connectedDebugAndroidTest` on API 35 | **21 tests, 20 passed, 1 failed, 0 skipped**; wall clock 14 min 36 s; Gradle exit code 1 |

Combined build wall clock for step 1: 40 s (warm Gradle cache).

### Pre-existing lint failure (recorded, not hidden)

All 19 errors are `GradleDependency` ("A newer version of … is available") in `app/build.gradle.kts`, promoted to errors by `lint { warningsAsErrors = true }`:

| Dependency | Current | Newer reported by lint |
|---|---|---|
| androidx.appcompat:appcompat | 1.7.0 | 1.8.0 |
| com.google.android.material:material | 1.12.0 | 1.14.0 |
| androidx.constraintlayout:constraintlayout | 2.2.1 | 2.2.2 |
| androidx.navigation:navigation-fragment-ktx / navigation-ui-ktx | 2.8.9 | 2.10.2 |
| androidx.work:work-runtime | 2.10.0 | 2.12.0 |
| androidx.core:core-ktx | 1.15.0 | 1.19.1 |
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.8.7 | 2.11.0 |
| androidx.room:room-runtime / room-compiler | 2.6.1 | 2.8.5 |
| androidx.test.espresso:espresso-idling-resource, idling-concurrent, espresso-core, espresso-contrib | 3.6.1 | 3.7.0 |
| androidx.test.ext:junit | 1.2.1 | 1.3.0 |
| androidx.test:rules | 1.6.1 | 1.7.0 |
| androidx.test.uiautomator:uiautomator | 2.3.0 | 2.4.0 |
| androidx.test:runner | 1.6.2 | 1.7.0 |
| androidx.test:orchestrator | 1.5.1 | 1.6.1 |

Report: `app/build/reports/lint-results-debug.html` (text copy kept during the session at `/tmp/baseline/lint.txt`).

### Pre-existing instrumented failure (API 35)

`com.futsch1.medtimer.TagTest > tagHandling` failed with
`com.adevinta.android.barista.internal.failurehandler.BaristaException: Could not perform action single click on view view.getId() is <16908313/android:id/button1>` (dialog "OK" button not clickable in the headless emulator). The other 20 tests (`BasicUITest`, `CalendarTest`, `ExportBackupTest`, `MedicineStockTest`, `NotificationTest` ×5, `ReminderTest`, `ScreenshotsTest`, `TestDataAndDeleteAndManualDoseTest`, …) passed. Recorded as a baseline flake/failure to compare against on the upgrade branch; not fixed here.

## 10 lowest-covered classes (unit tests only)

Many classes have 0 % coverage because the UI layer is only exercised by instrumented tests. The list is therefore the ten *largest* (named, non-anonymous) classes at 0 % line coverage, including Room-generated `*_Impl` classes, followed by the low-coverage classes in the app's risk areas.

| # | Class | Lines covered / total | Line % |
|---|---|---|---|
| 1 | `com.futsch1.medtimer.database.MedicineDao_Impl` | 0 / 916 | 0 % |
| 2 | `com.futsch1.medtimer.OptionsMenu` | 0 / 132 | 0 % |
| 3 | `com.futsch1.medtimer.medicine.EditMedicineFragment` | 0 / 121 | 0 % |
| 4 | `com.futsch1.medtimer.medicine.AdvancedReminderSettingsFragment` | 0 / 117 | 0 % |
| 5 | `com.futsch1.medtimer.overview.OverviewFragment` | 0 / 111 | 0 % |
| 6 | `com.futsch1.medtimer.preferences.PreferencesFragment` | 0 / 110 | 0 % |
| 7 | `com.futsch1.medtimer.statistics.MedicinePerDayChart` | 0 / 105 | 0 % |
| 8 | `com.futsch1.medtimer.medicine.dialogs.NewReminderDialog` | 0 / 92 | 0 % |
| 9 | `com.futsch1.medtimer.MedicineViewModel` | 0 / 85 | 0 % |
| 10 | `com.futsch1.medtimer.BackupManager` | 0 / 85 | 0 % |

Risk-area classes (from `docs/upgrade-impact-android-17.md` §1.5) with low unit coverage:

| Class | Lines covered / total | Branches covered / total |
|---|---|---|
| `ReminderProcessor` | 46 / 95 | 4 / 14 |
| `RescheduleWork` | 27 / 56 | 3 / 12 |
| `Notifications` | 65 / 98 | 13 / 32 |
| `Autostart` | 1 / 26 | 0 / 10 |
| `WorkManagerAccess` | 0 / 7 | 0 / 2 |
| `ReminderSchedulerService` | 0 / 18 | 0 / 0 |
| `NotificationSoundManager` | 1 / 12 | 0 / 4 |
| `scheduling.CyclesHelper` | 2 / 6 | 1 / 2 |
| `scheduling.ReminderScheduler` | 22 / 23 | 9 / 10 |
| `scheduling.StandardScheduling` | 68 / 68 | 48 / 48 |

Source: `jacocoUnitTestReportBaseline.csv` (session copy `/tmp/baseline/coverage.csv`).
