# Verification Report — EE-32 Android 17 upgrade

**Status: DRAFT for human review.** No claim of compliance with IEC 62304, FDA guidance or any other standard. All results below were observed by running the quoted commands; nothing is estimated.

## 1. Scope

Verification that MedTimer built with `compileSdk`/`targetSdk` 37 (branch `devin/ee-32-android-17-upgrade`) builds, passes its automated tests, and performs its core function (scheduled medication reminder → notification) on an emulator, compared with the v1.16.5 baseline on `main`.

## 2. Environment

| Item | Value |
|---|---|
| Build host | Devin VM, Linux 6.8 (aws), OpenJDK 17.0.20.1 |
| Gradle / AGP / Kotlin | 9.8.1 / 9.4.1 / 2.4.21 |
| Emulator A | AVD `api35` — Android 15 (API 35) Google APIs x86_64, 720x1600, headless (`-no-window -gpu swiftshader_indirect`) |
| Emulator B | AVD `api37` — Android 17 (API 37) Google APIs x86_64; **install-only** on this VM (SurfaceFlinger crash loop under the headless emulator) |
| CI | GitHub Actions `Test` workflow, `reactivecircus/android-emulator-runner`, API 37 `google_apis` x86_64 (see §6) |

## 3. Static verification

| Check | Command | Result |
|---|---|---|
| Build | `./gradlew assembleDebug` | SUCCESS (every ordered commit built; final `BUILD SUCCESSFUL`) |
| Lint | `./gradlew lintDebug` (`warningsAsErrors=true`) | **4 errors** (`GradleDependency` on the deliberately pinned Espresso 3.6.1 artifacts; baseline: 19). No rule suppressed. |
| Native code / 16 KB pages | `unzip -l MedTimer-debug.apk \| grep -c '\.so$'` | 0 |

## 4. Automated tests

| Suite | Baseline | Final | Notes |
|---|---|---|---|
| JVM unit tests (`./gradlew testDebugUnitTest`) | 56 / 56 passed | **65 / 65 passed** | +9 tests (IMP-52/53/26) |
| Unit coverage (`./gradlew jacocoUnitTestReport`) | 10.96 % lines, 12.72 % branches | **12.33 % lines, 13.94 % branches** | details in `docs/upgrade/test-evidence.md` |
| Instrumented, API 35 (`./gradlew connectedDebugAndroidTest`) | 21 run, 20 passed, 1 failed (`TagTest.tagHandling`, pre-existing) | 23 run, 21 passed, 2 failed (`TagTest.tagHandling` pre-existing; `Android17BehaviorTest.edgeToEdgeInsets…` test-assertion bug, fixed and re-run alone: 2/2 passed) | +2 tests (`Android17BehaviorTest`) |
| Instrumented, API 37 | not run (no device at baseline) | CI run — see §6 | |

## 5. Device verification on API 35 (manual, recorded)

Procedure and artifacts: [`../upgrade/evidence/README.md`](../upgrade/evidence/README.md).

| Step | Expected | Observed (API 35, targetSdk 37 build) |
|---|---|---|
| Install upgraded APK | success | `adb install -r -g … Success`; `dumpsys package`: `targetSdk=37` |
| Cold start `am start -W …/.MainActivity` | app visible | `Status: ok`, `TotalTime: 1417` ms (`api35-01-overview-empty.png`) |
| Create "Medicine A 500 mg" | medicine listed | `api35-02-medicine-created.png` |
| Create reminder (dosage 1, interval 2 min) | reminder listed; exact alarm scheduled | `api35-05…png`; `dumpsys alarm`: `RTC_WAKEUP … origWhen 1791826920000 com.futsch1.medtimer` (= 17:42:00 UTC, 2 min ahead) |
| Alarm fires | notification posted | `dumpsys notification`: `android.title=Medicine reminder`, `android.text=It is 5:42 PM - time to take 1 of Medicine A 500 mg` (`api35-notification-dumpsys.txt`, `api35-07/08…png`) |
| Return to Overview | event visible | `api35-09-overview-after-reminder.png` |
| Whole flow | recording | `api35-demo.mp4` (170 s) |

Grants used before the flow (equivalent to what a user grants in Settings): `pm grant … POST_NOTIFICATIONS`, `appops set … SCHEDULE_EXACT_ALARM allow`.

## 6. Android 17 (API 37) verification

| Item | Result |
|---|---|
| Local API 37 AVD install | `adb install -r -g MedTimer-debug.apk` → `Success` on the API 37 AVD (Android 17, `ro.build.version.sdk=37`); `dumpsys package`: `targetSdk=37 versionName=1.16.5`. The first install attempt right after boot failed with `Can't find service: package` while SurfaceFlinger was crash-looping (41 crash lines in logcat, screencap empty) — log: `docs/upgrade/evidence/api37-install.txt`. No UI evidence from this device. |
| Local API 37 UI | **not possible** on this VM (known SurfaceFlinger crash loop) — not faked |
| GitHub Actions run (API 37 emulator: connected tests + unit tests + lint + app-launch recording) | CI_PLACEHOLDER |

## 7. Not verified / limitations

- Real Android 17 hardware behavior (exact alarm delivery under Doze, widget rendering, notification tap) — only CI emulator evidence.
- Tablet / large-screen layouts (IMP-05/41) — no tablet AVD.
- Material time picker interaction via `adb input` was unreliable, so the recorded flow uses an interval reminder instead of a fixed-time reminder; the fixed-time path is covered by the JVM scheduling tests and the instrumented `ReminderTest`.
- `TagTest.tagHandling` fails on the headless API 35 emulator before and after the change (pre-existing).
- SonarQube analysis not available on this branch.

## 8. Conclusion (engineering assessment, not a compliance statement)

Build, lint and the full JVM suite pass on the Android 17 toolchain; the instrumented suite on API 35 shows the same pass/fail profile as the baseline plus the two new passing device checks; the core reminder → exact alarm → notification path was observed working on the upgraded build. Remaining open items are listed in `release-notes-android-17.md`.
