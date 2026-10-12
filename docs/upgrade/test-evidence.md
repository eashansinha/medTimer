# Test and coverage evidence - EE-29 Android 17 (API 37) upgrade

**DRAFT for human review.** Every number below comes from a command that was run on the development VM on 2026-10-12 (UTC);
the command is quoted next to it. Nothing is estimated. Baseline numbers are from [`baseline.md`](baseline.md) (`main`, commit `8fde0b91`).
Final numbers are from branch `devin/ee-29-android-17-upgrade` at commit `f3385165` (code final; later commits are documentation only).

## 1. Summary before / after

| Measurement | Baseline (`main`, API 35) | Final (branch, API 37) | Command (final) |
|---|---|---|---|
| `assembleDebug` | SUCCESS | SUCCESS | `./gradlew assembleDebug testDebugUnitTest lintDebug :app:jacocoUnitTestReport --continue` |
| JVM unit tests | 56 run / 56 passed / 0 failed / 0 skipped | **67 run / 67 passed / 0 failed / 0 skipped** | same |
| `lintDebug` | FAILED, 19 errors (all `GradleDependency`) | FAILED, **5 errors** (4× `GradleDependency` espresso 3.6.1→3.7.0, 1× `NewerVersionAvailable` org.sonarqube) - see §3 | same |
| Line coverage (unit tests, JaCoCo) | 10.96 % (764 / 6 970) | **15.94 % (1 084 / 6 800)** | `./gradlew :app:jacocoUnitTestReport` → `app/build/reports/jacoco/jacocoUnitTestReport/jacocoUnitTestReport.csv` |
| Branch coverage (unit tests, JaCoCo) | 12.72 % (259 / 2 036) | **15.45 % (310 / 2 006)** | same |
| Classes in report | 264 | 249 (Room 2.8 generates fewer anonymous classes) | same |
| Instrumented tests, **API 35** emulator | 21 run / 20 passed / 1 failed (`TagTest.tagHandling`), 14 min 36 s | **21 run / 20 passed / 1 failed (`TagTest.tagHandling`, same Barista click failure), 16 min 12 s** | `./gradlew connectedDebugAndroidTest` |
| Instrumented tests, **API 37** | not run (no API 37 UI on this VM) | CI only - see §6 | `.github/workflows/test.yml` |

## 2. Toolchain used for the final measurements

JDK 17.0.20.1, Gradle 9.8.1, AGP 9.4.1 (built-in Kotlin), build tools 37.0.0, compileSdk/targetSdk 37, minSdk 28,
Robolectric 4.14.1 (`@Config(sdk = 34)`), JUnit 5.12.0, Mockito 5.16.0, androidx.work:work-testing 2.12.0 (new, test only).

## 3. Lint

Baseline: 19 `GradleDependency` errors (versions available for 14 libraries). Final: 5 errors, none suppressed:

| Rule | Location | Why it remains |
|---|---|---|
| `GradleDependency` ×4 | `app/build.gradle.kts` lines 142, 143, 156, 157 (espresso-idling-resource, idling-concurrent, espresso-core, espresso-contrib 3.6.1 → 3.7.0) | Espresso 3.7.0 hangs the instrumented suite (`BasicUITest`, `LooperIdlingResource-*-Export` never idle; reproduced twice, passes with 3.6.1) - commit `3ecbb8b7` |
| `NewerVersionAvailable` | `app/build.gradle.kts` line 8 (org.sonarqube 5.1.0.4882 → 7.5.0.8588) | 7.5.0.8588 fails to apply under AGP 9: "No Android variant found for project app" |

Report: `app/build/reports/lint-results-debug.html`.

## 4. New JVM tests (11) and what each protects

All new tests run in `./gradlew testDebugUnitTest` (Robolectric, SDK 34). Each was confirmed to be able to fail:
`NotificationSoundManagerUnitTest.unmuteSilentlyIgnored_restoreDoesNotTouchRingerMode` fails against the previous
`NotificationSoundManager.kt` (run with the old file: "4 tests completed, 1 failed"); the `RescheduleWorkUnitTest`
and `AutostartUnitTest` assertions are on mock interactions / WorkManager state that are absent when the code path is not taken
(during development they failed on Mockito thread-scoping and on a stale `RestoreReminders` looper before the test setup was corrected).

| Test | Protects |
|---|---|
| `RescheduleWorkUnitTest.futureReminder_exactAlarmAllowed_usesExactAlarmAtReminderTime` | exact alarm (`setExactAndAllowWhileIdle`, `RTC_WAKEUP`) at the exact reminder epoch millis; previous alarm cancelled; widget refresh broadcast (IMP-11) |
| `RescheduleWorkUnitTest.futureReminder_exactRemindersDisabledByUser_usesInexactAlarm` | user preference `exact_reminders=false` → `setAndAllowWhileIdle`, never exact |
| `RescheduleWorkUnitTest.futureReminder_exactAlarmPermissionRevoked_usesInexactAlarm` | `canScheduleExactAlarms()==false` (SCHEDULE_EXACT_ALARM revoked) → inexact fallback, no crash |
| `RescheduleWorkUnitTest.dueReminder_isEnqueuedImmediatelyAsReminderWork` | already-due reminder → `ReminderWork` enqueued with correct reminder id / epoch day / second-of-day; no alarm set (IMP-14) |
| `AutostartUnitTest.bootCompleted_reschedulesAndRepostsOnlyRaisedReminders` | BOOT_COMPLETED → `requestReschedule`; only RAISED events of the last day are re-posted (not TAKEN/SKIPPED) (IMP-15) |
| `AutostartUnitTest.packageReplaced_reschedulesWithoutRepostsWhenNothingIsRaised` | MY_PACKAGE_REPLACED → reschedule, no spurious re-posts |
| `AutostartUnitTest.unrelatedAction_isIgnored` | receiver ignores other actions |
| `NotificationSoundManagerUnitTest.unmuteSucceeded_restoreMutesAgainAndSetsVibrate` | DND override: unmute, DND policy extended with `PRIORITY_CATEGORY_REMINDERS`, restore mutes again and sets vibrate |
| `NotificationSoundManagerUnitTest.unmuteSilentlyIgnored_restoreDoesNotTouchRingerMode` | **IMP-39/50**: if Android 17 ignores the unmute, the ringer mode is not changed on restore |
| `NotificationSoundManagerUnitTest.notMuted_noVolumeChangeAtAll` | no volume calls when not muted, DND policy still extended |
| `NotificationSoundManagerUnitTest.interruptionFilterNotAll_streamIsLeftAlone` | no volume change while a DND filter is active |

## 5. Per-class coverage of touched / risk classes (lines covered / total, branches covered / total)

| Class | Baseline | Final |
|---|---|---|
| `reminders.RescheduleWork` | 27 / 56 (3 / 12) | **50 / 56 (10 / 12)** |
| `Autostart` | 1 / 26 (0 / 10) | **26 / 26 (9 / 10)** |
| `reminders.NotificationSoundManager.Companion` | 0 / 32 (0 / 16) | **34 / 34 (15 / 20)** (+2 lines from the IMP-39 fix) |
| `reminders.NotificationSoundManager` (instance init) | 1 / 12 (0 / 4) | 1 / 12 (0 / 4) |
| `WorkManagerAccess` | 0 / 7 (0 / 2) | 2 / 7 (1 / 2) |
| `MainActivity` (IMP-01 insets, UI - instrumented only) | 0 / 67 (0 / 26) | 0 / 73 (0 / 26) |
| `MedTimerAppIntro`, `overview.OverviewFragment`, `statistics.TakenSkippedChart`, `statistics.MedicinePerDayChart` (colorPrimary lookup) | 0 % | 0 % (UI classes, covered by the API 35 instrumented suite only) |
| `reminders.scheduling.ReminderScheduler` | 22 / 23 (9 / 10) | 22 / 23 (9 / 10) |
| `reminders.WeekendMode` | 14 / 14 (6 / 6) | 14 / 14 (6 / 6) |
| `reminders.ReminderProcessor` | 46 / 95 (4 / 14) | 46 / 95 (4 / 14) |

## 6. Emulator evidence

### API 35 (AVD `api35`, Android 15, google_apis x86_64, headless swiftshader) - upgraded APK (targetSdk 37)

* `adb install -g -r app/build/outputs/apk/debug/MedTimer-debug.apk` → `Success`; `adb shell dumpsys package com.futsch1.medtimer` → `minSdk=28 targetSdk=37 versionName=1.16.5`.
* `adb shell am start -W -n com.futsch1.medtimer/.MainActivity` → `Status: ok`, `TotalTime: 1026` ms (first launch), `2552` ms (recorded run after `pm clear`).
* Manual flow (driven with `adb shell input`, script `/tmp/final/demo.sh` during the session): grant `POST_NOTIFICATIONS`,
  create medicine "Medicine A 500 mg", reminder amount 1 at 11:21 AM (2 min ahead; `appops set … SCHEDULE_EXACT_ALARM allow`),
  open overview, wait for the notification (posted 11:21:05 UTC), open the notification shade, back to overview.
* Recording: `adb shell screenrecord --time-limit 170 /sdcard/demo.mp4` → [`evidence/api35-demo.mp4`](evidence/api35-demo.mp4) (139 s, 1.7 MB).
* Screenshots (`adb exec-out screencap -p`): [`api35-01-launch-overview.png`](evidence/api35-01-launch-overview.png),
  [`api35-02-medicine-created.png`](evidence/api35-02-medicine-created.png), [`api35-03-new-reminder-dialog.png`](evidence/api35-03-new-reminder-dialog.png),
  [`api35-04-overview-next-reminder.png`](evidence/api35-04-overview-next-reminder.png) ("1 of Medicine A 500 mg, 10/12/26 11:21 AM"),
  [`api35-05-notification.png`](evidence/api35-05-notification.png) ("Medicine reminder - It is 11:21 AM - time to take 1 of Medicine A 500 mg", actions Taken / Snooze),
  [`api35-06-overview-after-notification.png`](evidence/api35-06-overview-after-notification.png).
* Edge-to-edge (IMP-01) check from the UI dump on API 35: toolbar bounds `[0,48][720,176]` (below the 48 px status bar), bottom navigation `[0,1392][720,1552]` (above the 48 px gesture bar), root `mainRoot` `[0,0][720,1600]`.
* Instrumented suite: `./gradlew connectedDebugAndroidTest` → 21 tests, 1 failure (`TagTest.tagHandling`: `BaristaException: Could not perform action single click on view … android:id/button1`), identical to the baseline failure; 16 min 12 s; Gradle exit code 1.
  During the first attempt with espresso 3.7.0 the suite hung in `BasicUITest.basicUITest` (see §3); the result above is with espresso 3.6.1.

### API 37 (AVD `api37`, Android 17, google_apis x86_64, headless swiftshader) - install evidence only

* Boot: `sys.boot_completed=1` after ~30 s; `ro.build.version.sdk=37`, `ro.build.version.release=17`, fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:userdebug/dev-keys`.
* `adb install -r -g app/build/outputs/apk/debug/MedTimer-debug.apk` → `Success`; `dumpsys package` → `minSdk=28 targetSdk=37`.
* `am start -W …/.MainActivity`: first attempt immediately after install → `Error type 3 … does not exist` (package still being processed); second attempt → `Status: ok … Complete`, but the guest UI stack crash-loops as documented for this VM
  (`F mapper.ranchu: Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`, SurfaceFlinger restart, no app process afterwards, `screencap` returns an empty file).
  **No API 37 screenshot or recording was produced locally; Android 17 UI evidence comes from CI only.**

### CI (GitHub Actions `Test` workflow, API 37.0 emulator)

`.github/workflows/test.yml` runs `JacocoDebugCodeCoverage` (unit + instrumented) on `system-images;android-37.0;google_apis;x86_64`,
records the app launch (`adb shell screenrecord`, 30 s) and uploads it as artifact `LaunchRecording-api37.0`; test reports are the `TestReports` artifact.
Run URL and result: see the pull request description (the run was still in progress / result recorded there at hand-back; never guessed).
