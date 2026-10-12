# Baseline measurement — `main` @ 8fde0b91 (v1.16.5, compileSdk/targetSdk 35)

Measured on 2026-10-12 on the Devin VM **before any code change** (branch `devin/ee-32-android-17-upgrade` was created from this commit). Every number below is copied from the quoted command's output; logs were kept at `~/ee32/logs/` on the build VM.

## Tool versions

| Tool | Version | Source |
|---|---|---|
| Gradle wrapper | 8.11.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.9.0 | `build.gradle.kts` |
| Kotlin Gradle plugin | 2.1.10 | `build.gradle.kts` |
| JDK | OpenJDK 17.0.20.1 | `java -version` |
| compileSdk / targetSdk / minSdk | 35 / 35 / 28 | `app/build.gradle.kts` |
| Build-tools installed | 35.0.0, 37.0.0 | `$ANDROID_HOME/build-tools` |
| Robolectric / JUnit Jupiter | 4.14.1 / 5.12.0 | `app/build.gradle.kts` |
| JaCoCo | Gradle `jacoco` plugin default (not pinned in build file) | `app/build.gradle.kts` |

## 1. Build + unit tests + lint

Command (run on clean `main`):

```
./gradlew assembleDebug testDebugUnitTest lintDebug --continue
```

| Task | Result |
|---|---|
| `assembleDebug` | **SUCCESS** — `app/build/outputs/apk/debug/MedTimer-debug.apk` produced |
| `testDebugUnitTest` | **SUCCESS** |
| `lintDebug` | **FAILED (pre-existing)** — `Lint found 19 errors, 0 warnings`; all 19 are `GradleDependency` ("A newer version of … is available") promoted by `warningsAsErrors = true`; report `app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt` |
| Overall | `BUILD FAILED in 36s` (exit 1) solely because of the lint errors above |

The lint failure is recorded, not hidden: on `main` the lint gate is already red because dependency versions are behind Google Maven. The upgrade is expected to fix this by bumping dependencies, not by suppressing the check.

### Unit tests (forced re-run so the numbers are fresh, not from the warm cache)

```
./gradlew -I ~/ee32/init/jacoco-unit.gradle testDebugUnitTest --rerun jacocoUnitTestReportBaseline
# counts aggregated from app/build/test-results/testDebugUnitTest/*.xml
```

| Tests run | Passed | Failed | Skipped |
|---|---|---|---|
| **56** | **56** | 0 | 0 |

`BUILD SUCCESSFUL` (real 0m25s). Test classes: `app/src/test/java/**` (JUnit 5 + Robolectric via the `tech.apter` extension, `@Config(sdk = 34)`).

## 2. Unit-test-only JaCoCo coverage

The repository's `JacocoDebugCodeCoverage` task depends on `connectedDebugAndroidTest` (needs a device), so a unit-test-only `JacocoReport` task with the **same class exclusions** was registered via a Gradle init script for the baseline (the branch adds the equivalent `jacocoUnitTestReport` task to `app/build.gradle.kts` so the final measurement is reproducible from the repo). Report: `app/build/reports/jacoco/jacocoUnitTestReportBaseline/{html,*.xml,*.csv}`.

| Counter | Covered / Total | % |
|---|---|---|
| Lines | 764 / 6970 | **10.96 %** |
| Branches | 259 / 2036 | **12.72 %** |
| Instructions | 4088 / 33269 | 12.29 % |
| Methods | 194 / 1513 | 12.82 % |
| Classes in report | 264 | |

### 10 lowest-covered classes (0 % line coverage, ordered by size; UI/Room-generated code)

| Class | Lines covered/total | Branches |
|---|---|---|
| `database.MedicineDao_Impl` (Room-generated) | 0 / 916 | 0 / 284 |
| `OptionsMenu` | 0 / 132 | 0 / 18 |
| `database.MedicineRoomDatabase_Impl$…Delegate` (Room-generated) | 0 / 123 | 0 / 22 |
| `medicine.EditMedicineFragment` | 0 / 121 | 0 / 12 |
| `medicine.AdvancedReminderSettingsFragment` | 0 / 117 | 0 / 32 |
| `overview.OverviewFragment` | 0 / 111 | 0 / 18 |
| `preferences.PreferencesFragment` | 0 / 110 | 0 / 32 |
| `statistics.MedicinePerDayChart` | 0 / 105 | 0 / 28 |
| `medicine.dialogs.NewReminderDialog` | 0 / 92 | 0 / 18 |
| `MedicineViewModel` | 0 / 85 | 0 / 46 |

### Coverage of the upgrade risk area (reminder scheduling / alarms / notifications)

| Class | Lines | Branches |
|---|---|---|
| `reminders.RescheduleWork` (AlarmManager exact alarms) | 27 / 56 | 3 / 12 |
| `reminders.ReminderProcessor` (receiver, notification actions) | 46 / 95 | 4 / 14 |
| `reminders.Notifications` | 65 / 98 | 13 / 32 |
| `reminders.NotificationAction` | 34 / 35 | 10 / 16 |
| `reminders.ReminderWork` | 65 / 66 | 18 / 26 |
| `reminders.scheduling.ReminderScheduler` | 22 / 23 | 9 / 10 |
| `reminders.scheduling.StandardScheduling` | 68 / 68 | 48 / 48 |
| `reminders.scheduling.IntervalScheduling` | 28 / 28 | 13 / 14 |
| `reminders.scheduling.CyclesHelper` | 2 / 6 | 1 / 2 |
| `reminders.NotificationSoundManager(.Companion)` | 1 / 44 | 0 / 20 |
| `reminders.StockHandlingWork` | 0 / 22 | 0 / 16 |

## 3. Instrumented tests (API 35 emulator, `api35` AVD)

Command:

```
./gradlew connectedDebugAndroidTest --continue   # device: emulator-5554, ro.build.version.sdk=35
```

Result (`BUILD FAILED in 14m 31s`, exit 1; results XML `app/build/outputs/androidTest-results/connected/debug/TEST-api35(AVD) - 15-_app-.xml`):

| Tests run | Passed | Failed | Skipped |
|---|---|---|---|
| **21** | **20** | **1** | 0 |

Pre-existing failure (recorded, not hidden): `com.futsch1.medtimer.TagTest > tagHandling` — `BaristaException: Could not perform action single click on view view.getId() is <16908313/android:id/button1>` (dialog button not clickable in time on the headless API 35 emulator). All other 20 instrumented tests passed on **API 35**.

**API 37**: not run at baseline — the `api37` AVD on this VM crash-loops SurfaceFlinger under the headless emulator (known limitation), so it is usable for `adb install` evidence only.
