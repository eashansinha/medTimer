# EE-30 — Verification report (DRAFT for human review)

> DRAFT input for human review. This document records what was executed, with the exact commands and their
> observed results. It does **not** claim compliance with IEC 62304, FDA regulations or any other standard.

Branch: `devin/ee-30-android17-reminders` (one PR into `main`). Every number below comes from a command that
was run on this branch; the command is quoted next to the result. Each emulator fact is labelled with its API level.

## 1. Environment

| Item | Value |
|---|---|
| JDK | OpenJDK 17.0.20.1 |
| Android SDK | platforms `android-35`, `android-37`; build-tools 35.0.0, 37.0.0 |
| Gradle / AGP | 9.8.1 / 9.4.1 (built-in Kotlin) |
| Emulator `api35` | `sdk_gphone64_x86_64`, API 35 / Android 15, headless (`-no-window -gpu swiftshader_indirect`) |
| Emulator `api37` | `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019`, API 37 / Android 17, headless |

## 2. JVM tests and coverage

```
./gradlew testDebugUnitTest --console=plain
./gradlew JacocoDebugCodeCoverage -x connectedDebugAndroidTest --console=plain
```

| Metric | `main` (baseline) | branch |
|---|---|---|
| JVM tests (JUnit 5 + Robolectric) | 56 run, 0 failed | **91 run, 0 failed** |
| Line coverage, whole app | 768/6934 = 11.1 % | 819/6794 = 12.1 % |
| Line coverage, `com.futsch1.medtimer.reminders` | 305/487 = 62.6 % | 329/487 = 67.6 % |
| Branch coverage, `reminders` | 55/144 = 38.2 % | 63/144 = 43.8 % |
| Line coverage, `reminders.scheduling` | 145/150 = 96.7 % | 145/150 = 96.7 % |

Per-class before/after and the list of new test classes: `docs/upgrade/test-evidence.md`.

## 3. Build and lint

```
./gradlew assembleDebug --console=plain          # BUILD SUCCESSFUL (compileSdk 37 / targetSdk 37)
./gradlew lintDebug --continue --console=plain   # BUILD FAILED — 9 errors, all GradleDependency
```

Baseline `main` had 19 `GradleDependency` lint errors. The branch has **9**, all of them "a newer version of
`androidx.test…` / `espresso…` is available" for the test libraries that were deliberately kept at 3.6.1 / 1.6.x
(see §4.2). Nothing was suppressed; no `lint-baseline.xml` was added.

## 4. Instrumented tests — API 35 emulator

### 4.1 Result

```
./gradlew connectedDebugAndroidTest --console=plain
```

**API 35: 21 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESSFUL in 14m 57s.**
Raw report: `docs/upgrade/evidence/api35-connectedDebugAndroidTest.xml`
(classes: BasicUITest, CalendarTest, ExportBackupTest, MedicineStockTest, NotificationTest ×6, ReminderTest ×4,
ScreenshotsTest, TagTest ×3, TestDataAndDeleteAndManualDoseTest).

### 4.2 Finding during verification: Espresso 3.7.0 regression (fixed on the branch)

The first full run on API 35, with the AndroidX Test libraries bumped to Espresso 3.7.0 / test 1.7.0, ended
`21 tests, 15 failures` after 2h 32m. Every failure had the same root cause:

```
androidx.test.espresso.IdlingResourceTimeoutException:
  Wait for [LooperIdlingResource-NN-Export] to become idle timed out
  at androidx.test.espresso.base.TestLooperManagerCompat.execute(...)
```

`Export` is the `HandlerThread` that `OptionsMenu` registers through
`IdlingRegistry.registerLooperAsIdlingResource`. Discriminating experiments on the same `api35` emulator:

| Experiment | Command | Result |
|---|---|---|
| Baseline `main` (Espresso 3.6.1, targetSdk 35) | `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.futsch1.medtimer.BasicUITest` (in a `main` worktree) | 2/2 pass, 1m 10s |
| Branch, targetSdk 37, Espresso **3.6.1** | same command on the branch | 2/2 pass, 41s |
| Branch, targetSdk 37, Espresso **3.7.0** | full run above | 15/21 fail (timeouts) |

Conclusion: the failures are caused by the Espresso 3.7.0 looper-idling refactor
(`TestLooperManagerCompat`, see the AndroidX Test release notes), not by the SDK/target bump or any reminder
change. Commit `ec979393` keeps AndroidX Test / Espresso at the baseline versions; the full suite then passes
(§4.1). Open question for a human: whether Espresso 3.6.1 (reflection on `MessageQueue`) works on an Android 17
*device* — the API 37 CI run answers that (§6), it is not assumed here.

## 5. Manual reminder flow — API 35 emulator

Upgraded APK (`versionCode=105 minSdk=28 targetSdk=37`, from `adb shell dumpsys package com.futsch1.medtimer`)
installed with `adb -s emulator-5554 install -r app/build/outputs/apk/debug/MedTimer-debug.apk`, notification
permission granted with `adb shell pm grant com.futsch1.medtimer android.permission.POST_NOTIFICATIONS`.

| Step (API 35) | Observed | Evidence |
|---|---|---|
| Launch `com.futsch1.medtimer/.MainActivity` | Overview shown | `docs/upgrade/evidence/api35-01-launch.png` |
| Medicine → Add medicine → "Medicine A 500 mg" | medicine screen opens | `api35-02-medicine-created.png` |
| Add reminder, dosage 1, time 1:12 PM (device time 13:10:46) | reminder card "1:12 PM / 1 / Every day" | `api35-03-reminder-created.png` |
| `adb shell dumpsys alarm` right after creation | `RTC_WAKEUP … origWhen=2026-10-12 13:12:00.000 window=0 exactAllowReason=permission com.futsch1.medtimer` — i.e. an **exact** alarm was armed | command output |
| 13:12 — notification | `Medicine reminder / It is 1:12 PM - time to take 1 of Medicine A 500 mg`, `actions=2` (Taken, Snooze) per `dumpsys notification` | `api35-04-notification.png` |
| Tap **Taken** on the notification | notification removed (`dumpsys notification` count for the package = 0); Overview shows the event as Taken and the next reminder 10/13/26 1:12 PM | `api35-05-overview-after-taken.png` |
| Screen recording | `adb shell screenrecord --time-limit 170` ×2 (create flow 13:07–13:10, notification + Taken 13:11–13:14), concatenated with `ffmpeg -f concat -c copy`; 333 s total | `api35-reminder-flow.mp4` |

Snooze was exercised by the instrumented tests (`NotificationTest.customSnooze`, `repeatingReminders`,
`actionOnDismissedNotification` — all pass on API 35, §4.1) and by the JVM tests (`SnoozeWorkBoundsUnitTest`).

## 6. API 37 emulator

```
adb -s emulator-5556 shell getprop ro.build.version.sdk      # 37
adb -s emulator-5556 shell getprop ro.build.version.release  # 17
adb -s emulator-5556 install -r app/build/outputs/apk/debug/MedTimer-debug.apk
```

| Attempt (API 37) | Result |
|---|---|
| 1 (13:05, ~1 min after `sys.boot_completed=1`) | `Failure calling service package: Broken pipe` / `Can't find service: package` — `system_server` was restarting |
| 2 (13:06:29) | **`Success`** — `dumpsys package`: `versionCode=105 minSdk=28 targetSdk=37`, `versionName=1.16.5`, APK at `/data/app/~~…/com.futsch1.medtimer-…/base.apk` |

**Not verified on API 37:** app launch / UI / notifications. `am start` could not be executed reliably
(`Can't find service: activity`), `pidof com.futsch1.medtimer` stayed empty, and `logcat` shows the documented
SurfaceFlinger crash-loop (`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`, SIGABRT in
`surfaceflinger`, followed by a `FATAL EXCEPTION` in `com.google.android.apps.nexuslauncher` — not in MedTimer).
Android 17 UI behaviour therefore has to come from the CI run (§7) or a human on a real device/desktop emulator.

## 7. CI on API 37

`.github/workflows/test.yml` now starts the instrumented tests on `target_api_level: 37` (commit `062beda9`).
Run URL and outcome: **see the PR description / final hand-back** — recorded only after the run exists; not guessed.

## 8. What was NOT verified, and why

| Item | Why |
|---|---|
| Any UI/notification behaviour on Android 17 (API 37) locally | headless emulator SurfaceFlinger crash-loop on this VM (see §6) |
| Exact-alarm *denial* path on a device | only covered by JVM tests (`RescheduleWorkExactAlarmUnitTest`, `SnoozeWorkBoundsUnitTest` — inexact fallback) |
| `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` on a device | JVM only (`AutostartUnitTest`); no emulator reboot was performed |
| Robolectric on SDK 37 | Robolectric 4.17 ships no API 37 `android-all`; JVM tests run with `@Config(sdk = 34)` |
| Espresso 3.6.1 on an Android 17 device | depends on the CI run (§7) |
| Play Store / release build (`assembleRelease`, Play Publisher) | not part of EE-30; debug variant only |

## 9. Sign-off (placeholder)

| Role | Name | Date | Decision |
|---|---|---|---|
| Developer | | | |
| Reviewer | | | |
| QA / verification owner | | | |
