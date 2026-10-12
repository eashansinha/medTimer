# Verification report - EE-29 Android 17 (API 37) upgrade

**DRAFT for human review.** Engineering input only; no claim of compliance with IEC 62304, FDA guidance or any other standard.
All results are copied from [`../upgrade/test-evidence.md`](../upgrade/test-evidence.md) and [`../upgrade/baseline.md`](../upgrade/baseline.md),
where the exact commands are quoted.

## 1. Item under verification

MedTimer `eashansinha/medTimer`, branch `devin/ee-29-android-17-upgrade` (code final at commit `f3385165`), compared with `main` (`8fde0b91`, v1.16.5, API 35).

## 2. Verification activities and results

| # | Activity | Method | Environment | Result | Evidence |
|---|---|---|---|---|---|
| V1 | Build | `./gradlew assembleDebug` | VM, JDK 17, Gradle 9.8.1, AGP 9.4.1 | PASS (BUILD SUCCESSFUL) | `docs/upgrade/test-evidence.md` §1 |
| V2 | JVM unit tests | `./gradlew testDebugUnitTest` (JUnit 5 + Robolectric SDK 34) | VM | PASS - 67 / 67 (baseline 56 / 56) | §1, §4 |
| V3 | Unit-test coverage | `./gradlew :app:jacocoUnitTestReport` | VM | 15.94 % lines, 15.45 % branches (baseline 10.96 % / 12.72 %) | §1, §5 |
| V4 | Static analysis (lint) | `./gradlew lintDebug` | VM | FAIL - 5 errors (baseline 19); all are "newer version available" findings with documented reasons, no suppressions | §3 |
| V5 | Instrumented UI suite on Android 15 | `./gradlew connectedDebugAndroidTest` | AVD `api35` (API 35) headless | 21 run, 20 pass, 1 fail (`TagTest.tagHandling`, identical to baseline) | §6 |
| V6 | Manual end-to-end reminder flow on Android 15 | adb-driven: create medicine + reminder, wait for notification, check overview | AVD `api35` | PASS - notification "Medicine reminder … 1 of Medicine A 500 mg" with Taken / Snooze actions posted at the scheduled minute | `evidence/api35-demo.mp4`, `api35-0[1-6]*.png` |
| V7 | Edge-to-edge layout (IMP-01) | UI hierarchy bounds + screenshots | AVD `api35` | PASS - toolbar below status bar, bottom navigation above gesture bar | §6 |
| V8 | Install on Android 17 | `adb install -r -g`, `dumpsys package` | AVD `api37` (API 37) headless | PASS - `Success`, `targetSdk=37 minSdk=28` | §6 |
| V9 | Launch / UI on Android 17 (local) | `am start -W` | AVD `api37` | NOT VERIFIABLE on this VM - SurfaceFlinger assertion crash-loop (`hasReadColorBufferDma`), no screenshot possible | §6 |
| V10 | Instrumented suite + launch recording on Android 17 | GitHub Actions `Test` workflow (`test.yml`, API 37.0 emulator) | CI | FAIL (environment) - emulator system services died after boot (`Can't find service: input`, ColorBuffer errors) before any test ran; artifacts empty | `docs/upgrade/test-evidence.md` §6, job 114792819524 |
| V11 | Behavior-change fix IMP-39/50 can fail | run `NotificationSoundManagerUnitTest` against the previous implementation | VM | 4 tests, 1 failed (expected) | §4 |

## 3. Deviations and open items

| Item | Description | Disposition |
|---|---|---|
| D1 | `TagTest.tagHandling` fails on the headless API 35 emulator (dialog button click) | Pre-existing on `main`; not caused by the upgrade; keep as known flake / fix separately |
| D2 | Lint: 5 remaining errors (espresso 3.7.0, sonarqube 7.5.0.8588 available) | Documented reasons (hang / incompatibility); re-evaluate when fixed upstream |
| D3 | Espresso 3.7.0 hangs `BasicUITest` on `LooperIdlingResource-*-Export` | Kept 3.6.1; root cause in the idling-resource registration of `OptionsMenu` to be analysed separately |
| D4 | Android 17 "override DND" audio behaviour (IMP-39/50) | Logic hardened + unit-tested; OS-level effect needs a physical Android 17 device test |
| D5 | No Android 17 UI / instrumented evidence | Local API 37 emulator cannot render and the CI API 37.0 emulator loses its system services; needs a physical Android 17 device or hardware-accelerated emulator |
| D6 | "Needs runtime check" rows IMP-05, 17, 23, 25, 27, 33, 42, 48 | Partly exercised by the CI instrumented suite on API 37; no dedicated tests |
| D7 | `InputMethodManager.SHOW_IMPLICIT` deprecated in API 37 (`NewReminderDialog.kt:57`) | Compiler warning only; no behavior change; follow-up |

## 4. Conclusion (engineering)

The upgraded build compiles against and targets API 37, all JVM tests pass (+11 new tests, coverage up from 10.96 % to 15.94 % lines),
and the Android 15 instrumented suite and manual reminder flow behave as on the baseline. Android 17 runtime behaviour is verified only
to the extent the CI emulator run and code analysis allow; the open items above must be reviewed by a human before release.

Sign-off (to be completed by humans): _______________________
