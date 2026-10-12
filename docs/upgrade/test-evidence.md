# Test evidence — EE-32 Android 17 upgrade (final branch vs. baseline)

**Status: DRAFT for human review.** Every number comes from the quoted command run on the Devin build VM (JDK 17, Linux); baseline numbers are from `docs/upgrade/baseline.md` (`main` @ 8fde0b91). Final numbers are from the head of `devin/ee-32-android-17-upgrade`.

## 1. Build, unit tests, lint

```
./gradlew assembleDebug testDebugUnitTest lintDebug --continue     # final branch
```

| Check | Baseline (`main`, API 35) | Final (branch, API 37) |
|---|---|---|
| `assembleDebug` | SUCCESS | SUCCESS |
| `testDebugUnitTest` | SUCCESS — 56 run / 56 passed / 0 failed / 0 skipped | SUCCESS — **65 run / 65 passed / 0 failed / 0 skipped** |
| `lintDebug` | **FAILED** — 19 errors (`GradleDependency`, pre-existing) | **FAILED — 4 errors** (`GradleDependency` for the four Espresso 3.6.1 artifacts, kept deliberately because 3.7.0 hangs the instrumented suite — see §4; 15 of the 19 baseline errors fixed, no rule disabled; with Espresso 3.7.0 lint was 0 errors, commit `e1139d7d`) |
| Overall | `BUILD FAILED in 36s` (lint) | `./gradlew assembleDebug testDebugUnitTest` SUCCESS; `./gradlew lintDebug` → `Lint found 4 errors`, `BUILD FAILED in 24s` |

Unit-test counts are aggregated from `app/build/test-results/testDebugUnitTest/*.xml`.

## 2. Unit-test coverage (JaCoCo, unit tests only)

```
./gradlew testDebugUnitTest --rerun jacocoUnitTestReport
# report: app/build/reports/jacoco/jacocoUnitTestReport/{html,jacocoUnitTestReport.xml,jacocoUnitTestReport.csv}
```

Baseline used an equivalent init-script task (`jacocoUnitTestReportBaseline`, same exclusions). The total line count differs because Room 2.8 / Kotlin 2.4 generate different code and because AGP 9 built-in Kotlin moved class output to `intermediates/built_in_kotlinc` (the report tasks were re-pointed in commit `7a1d0231`; before that fix every Kotlin class showed 0 %).

| Counter | Baseline | Final | Δ |
|---|---|---|---|
| Line coverage | 764 / 6970 = **10.96 %** | 838 / 6796 = **12.33 %** | +74 covered lines, +1.37 pp |
| Branch coverage | 259 / 2036 = **12.72 %** | 279 / 2002 = **13.94 %** | +20 covered branches, +1.22 pp |
| Classes in report | 264 rows | 249 rows | |

### Per touched / risk class (lines covered/total (%) \| branches covered/total)

| Class | Baseline | Final |
|---|---|---|
| `reminders.RescheduleWork` (IMP-52) | 27/56 (48 %) \| 3/12 | **37/56 (66 %) \| 9/12** |
| `Autostart` (IMP-53) | 1/26 (4 %) \| 0/10 | **15/26 (58 %) \| 7/10** |
| `widgets.WidgetImpl` (IMP-26) | 0/42 (0 %) \| 0/8 | **41/42 (98 %) \| 7/8** |
| `widgets.WidgetImplKt` | 0/3 (0 %) \| 0/2 | **3/3 (100 %) \| 2/2** |
| `reminders.PendingIntentBuilder` | 12/12 (100 %) \| 0/0 | 12/12 (100 %) \| 0/0 |
| `reminders.ReminderProcessor` | 46/95 (48 %) \| 4/14 | 46/95 (48 %) \| 4/14 |
| `reminders.scheduling.ReminderScheduler` | 22/23 (96 %) \| 9/10 | 22/23 (96 %) \| 9/10 |
| `reminders.scheduling.StandardScheduling` | 68/68 (100 %) \| 48/48 | 68/68 (100 %) \| 48/48 |
| `reminders.scheduling.IntervalScheduling` | 28/28 (100 %) \| 13/14 | 28/28 (100 %) \| 13/14 |
| `reminders.WeekendMode` | 14/14 (100 %) \| 6/6 | 14/14 (100 %) \| 6/6 |
| `database.JSONBackup.FullDeserialize` (IMP-28) | 7/10 (70 %) \| 3/4 | 7/10 (70 %) \| 3/4 |
| `MedTimerAppIntro` (colorPrimary change) | 0/70 (0 %) \| 0/4 | 0/70 (0 %) \| 0/4 — UI class, covered by instrumented launch only |
| `overview.OverviewFragment` (colorPrimary change) | 0/111 \| 0/18 | 0/111 \| 0/18 — instrumented suite |
| `statistics.TakenSkippedChart` (colorPrimary change) | 0/31 \| 0/4 | 0/31 \| 0/4 — instrumented `CalendarTest`/`ScreenshotsTest` |
| `statistics.MedicinePerDayChart` (colorPrimary change) | 0/105 \| 0/28 | 0/105 \| 0/28 — instrumented |
| `MainActivity` (IMP-01/02) | 0/67 \| 0/26 | 0/67 \| 0/26 — `Android17BehaviorTest` (device) |

### 10 lowest-covered classes after (all 0 % lines; UI and Room-generated code)

| Class | Lines | Branches |
|---|---|---|
| `database.MedicineDao_Impl` (generated) | 0 / 1163 | 0 / 448 |
| `OptionsMenu` | 0 / 132 | 0 / 18 |
| `medicine.EditMedicineFragment` | 0 / 121 | 0 / 12 |
| `medicine.AdvancedReminderSettingsFragment` | 0 / 117 | 0 / 32 |
| `overview.OverviewFragment` | 0 / 111 | 0 / 18 |
| `preferences.PreferencesFragment` | 0 / 110 | 0 / 32 |
| `database.MedicineDao_Impl$EntityDeleteOrUpdateAdapter` (generated) | 0 / 109 | 0 / 36 |
| `database.MedicineRoomDatabase_Impl$RoomOpenDelegate` (generated) | 0 / 107 | 0 / 10 |
| `statistics.MedicinePerDayChart` | 0 / 105 | 0 / 28 |
| `database.MedicineDao_Impl$EntityInsertAdapter` (generated) | 0 / 97 | 0 / 38 |

## 3. New tests and what each protects

| Test | Type | Protects |
|---|---|---|
| `RescheduleWorkUnitTest.testExactAlarmWhenPermitted` | JVM (Robolectric) | exact alarm (`setExactAndAllowWhileIdle`) used when `canScheduleExactAlarms()` and the preference allow it; previous alarm cancelled first (IMP-52) |
| `RescheduleWorkUnitTest.testInexactAlarmWhenPermissionDenied` | JVM | inexact fallback when the `SCHEDULE_EXACT_ALARM` permission is missing — reminder still scheduled (IMP-52) |
| `RescheduleWorkUnitTest.testInexactAlarmWhenPreferenceDisabled` | JVM | user preference `exact_reminders=false` honoured (IMP-52) |
| `RescheduleWorkUnitTest.testDueReminderIsEnqueuedImmediately` | JVM | already-due reminder goes straight to WorkManager, no alarm (IMP-52) |
| `AutostartUnitTest.testBootCompletedRequestsReschedule` | JVM | `BOOT_COMPLETED` enqueues the unique `reschedule` work (IMP-53) |
| `AutostartUnitTest.testPackageReplacedRequestsReschedule` | JVM | `MY_PACKAGE_REPLACED` (app update) re-schedules (IMP-53) |
| `AutostartUnitTest.testUnrelatedActionIsIgnored` | JVM | receiver ignores other actions / null action (IMP-53, IMP-10) |
| `WidgetImplTest.updateAppWidgetPushesSizedRemoteViews` | JVM | RemoteViews with size mapping (API 31+), one text line per slot, correct package — no bitmaps (IMP-26) |
| `WidgetImplTest.performWidgetUpdateUpdatesEveryWidgetId` | JVM | every widget instance updated (IMP-26) |
| `Android17BehaviorTest.edgeToEdgeInsetsKeepToolbarAndBottomNavigationVisible` | instrumented | toolbar below status-bar inset, bottom navigation above navigation-bar inset under enforced edge-to-edge (IMP-01) |
| `Android17BehaviorTest.systemBackReturnsToOverview` | instrumented | system back through the AndroidX dispatcher returns to Overview (IMP-02, IMP-20, IMP-27) |

Each new test was observed failing at least once while being written (e.g. the first Autostart variant failed with `WantedButNotInvoked` until the HandlerThread assertion was moved to device evidence), i.e. none is a tautology.

## 4. Instrumented tests (API 35 emulator)

```
./gradlew connectedDebugAndroidTest --continue      # emulator-5554, ro.build.version.sdk=35
```

| | Baseline (`main`) | Final (branch) |
|---|---|---|
| Tests run / passed / failed / skipped | 21 / 20 / 1 / 0 | **23 / 21 / 2 / 0** (full run, commit `8b3393b3`); the 2 failures: `TagTest.tagHandling` (pre-existing) and `Android17BehaviorTest.edgeToEdgeInsets…` caused by a wrong assertion formula in the test (compared against `getWindowVisibleDisplayFrame`, which already excludes the navigation bar). After the fix (commit below) the class was re-run alone: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.futsch1.medtimer.Android17BehaviorTest` → **2 / 2 / 0 / 0**, `BUILD SUCCESSFUL in 20s`. Results XML: `docs/upgrade/evidence/api35-connected-results.xml` |
| Pre-existing failure | `TagTest.tagHandling` — Barista could not click `android:id/button1` | `TagTest.tagHandling` — same Barista click failure on `android:id/button1` (unchanged) |
| Duration | `BUILD FAILED in 14m 31s` | `BUILD FAILED in 14m 37s` (full run; fails because of the 2 test failures above) |

Espresso note: with Espresso 3.7.0 (first attempt) every instrumented test waited on `IdlingPolicy: These resources are not idle: [LooperIdlingResource-NN-Export]` (the debug-only `Export` HandlerThread registered by `OptionsMenu.setupGenerateTestData()`), `BasicUITest` alone ran > 15 min; with Espresso 3.6.1 and nothing else changed `BasicUITest` = 2 tests, 0 failures, `BUILD SUCCESSFUL in 54s` (`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.futsch1.medtimer.BasicUITest`). Espresso is therefore pinned at 3.6.1 (commit `8b3393b3`).

**API 37**: not run locally (headless SurfaceFlinger crash loop on this VM; install-only evidence in the verification report). The GitHub Actions workflow on the PR targets an API 37.0 emulator; as of hand-back none of its runs executed the suite — see “CI status” below.

## 5. APK facts

`unzip -l app/build/outputs/apk/debug/MedTimer-debug.apk | grep -c '\.so$'` → **0** native libraries before and after (IMP-11/18/36). `adb shell dumpsys package com.futsch1.medtimer` on API 35 after install: `minSdk=28 targetSdk=37`, `versionName=1.16.5`.

## Independent API 35 end-to-end run (after PR creation)

A separate testing agent repeated the manual flow on the `api35` AVD with the upgraded APK (targetSdk 37) and additionally
exercised the notification **Taken** action and the Analysis screen. Result: all steps passed on **API 35**; crash buffer empty.
Evidence: `docs/upgrade/evidence/api35-independent/` (13 screenshots, 183 s recording, dumpsys logs). API 37 runtime behaviour
is not covered by this run.

## CI status (GitHub Actions on PR #8)

- The first run of the API 37 `test` job failed before the emulator started: `reactivecircus/android-emulator-runner` reported
  `Failed to find package 'platforms;android-37'` and adb could not connect to port 5554. Cause: the Android 17 SDK packages are
  published as `platforms;android-37.0` / `system-images;android-37.0;google_apis;x86_64`. Fixed by setting `target_api_level: "37.0"`
  (commit `85d938f9`). Two review findings on the workflow script (recording unreachable after `exit`; monkey status hidden by `|| true`)
  were fixed in `1ee3e976` and `2bd865d0`.
- `dependency-review` fails on every run with `Dependency review is not supported on this repository. Please ensure that Dependency
  graph is enabled ...`. `.github/workflows/dependency-review.yml` is unchanged on this branch (`git diff main...HEAD -- .github/workflows/`
  touches only `test.yml`), so this is a repository-settings issue, not caused by the upgrade. Open item for the repo owner.
- **API 37 emulator runs so far (all red, no instrumented test executed on API 37):**
  - Run 1 `https://github.com/eashansinha/medTimer/actions/runs/38295620801` (commit `2f1365ba`): emulator booted; `JacocoDebugCodeCoverage`
    printed `BUILD SUCCESSFUL in 6m 32s`, but the log shows `AndroidTestRunner failed on emulator-5554 … Failed to install APK …
    Requested internal only, but not enough space` — AGP 9.4.1's connected test task did **not** fail the build when the device runner
    could not install the APK. The follow-up `adb install -g` failed with `cmd: Can't find service: package`. Artifacts: `TestReports`
    (id 11770090027, 469 files, unit-test + lint reports only), `AppLaunchRecording-api37` empty.
  - Run 2 `https://github.com/eashansinha/medTimer/actions/runs/38299027059` (commit `32761cf2`): same install failure inside the test
    runner (`not enough space`), then the multi-line install-retry loop was a shell syntax error — `android-emulator-runner` executes every
    `script:` line in its own `sh -c`, so loops and `EXIT_CODE` do not survive across lines. Artifacts: `TestReports` (id 11769403425),
    `AppLaunchRecording-api37` empty.
  - Run 3 (job id 114987830155 (run URL not resolvable from the build VM; see the PR checks tab), commit `3df9b871`): the runner's own post-boot `adb shell input keyevent 82` failed with
    `cmd: Can't find service: input` right after `sys.boot_completed=1` (fresh AVD with 8 GB data partition); nothing after that ran.
  - Fixes in `3df9b871`: `disk-size: 8192M` + new AVD cache key, post-boot commands moved into `.github/scripts/ci-emulator-run.sh`
    (single shell), and the script now fails unless `app/build/outputs/androidTest-results/connected/debug/TEST-*.xml` exists with
    `tests>0` and `failures=errors=0` — so the AGP silent pass above can no longer turn the job green.
  - Run 4 `https://github.com/eashansinha/medTimer/actions/runs/38303291100` (commit `e3fd6663`, with the `3df9b871` hardening): emulator
    booted, but system services were unavailable (`cmd: Can't find service: activity` / `settings` / `package`); the AGP device runner could
    not install the APK → `No connected test report produced` (the new check correctly turned the job red instead of the earlier silent
    pass). The script's own install succeeded on retry 3 (`Success`), but `am start` → `Failure calling service activity: Broken pipe`,
    `screenrecord` → `Encoder failed (err=-38)` (no MP4), and `monkey` → `No activities found to run, monkey aborted`. Artifacts: `TestReports`
    (id 11770984572), `AppLaunchRecording-api37` (id 11772375198, 232 bytes = the empty screencap only).
  - Unrelated flake in run 4: `androidTest (28, medium_tablet)` (`compatibilityTest.yml`, untouched on this branch) failed during
    `sdkmanager --install 'system-images;android-28;google_apis;x86_64'` with `Error on ZipFile unknown archive` before any test ran; the same
    job passed in runs 1–3.
  - Conclusion at hand-back: **no Android 17 (API 37) instrumented or UI evidence exists** — neither locally (SurfaceFlinger crash loop) nor
    in CI (API 37.0 `google_apis` image: package/input system services unavailable or storage exhausted). The latest run's result is in
    the PR checks tab; `docs/compliance-drafts/verification-report.md` §6 is updated with each observed run.
