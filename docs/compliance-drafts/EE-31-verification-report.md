# EE-31 — Verification report (DRAFT for human review)

Branch `devin/ee-31-widgets-android-17` (`eashansinha/medTimer`), base `main` @ `8fde0b91`.
Every number below was produced by the quoted command on 2026-10-12; raw outputs are in
`docs/upgrade/`. This is an input for the project's own review; it does not claim conformity with
IEC 62304, FDA guidance or any other standard.

## 1. Summary

| Check | Baseline (`main`) | Branch (final) | Verdict |
|---|---|---|---|
| `./gradlew assembleDebug` | OK (compileSdk/targetSdk 35) | OK — `aapt2 dump badging`: `compileSdkVersion='37'`, `targetSdkVersion:'37'` | pass |
| `./gradlew testDebugUnitTest` | 56 tests, 0 failures | **79 tests, 0 failures** | pass |
| JaCoCo (unit) — whole app lines | 11.0 % (764/6970) | **16.4 %** (1120/6810) | improved |
| JaCoCo (unit) — `widgets` package lines | 2.0 % (3/147) | **96.2 %** (153/159) | improved |
| `./gradlew lintDebug` | FAILED, 19 `GradleDependency` errors | FAILED, **4** `GradleDependency` errors (pinned Espresso artifacts), 0 warnings; no suppression | improved, still red (recorded) |
| `connectedDebugAndroidTest`, API 35 emulator | not run for baseline (3-test subset: 3/3 pass) | **21 tests, 0 failures**, 16 min 37 s | pass |
| `adb install` on API 37 emulator | n/a | **Success**; `dumpsys package` → `targetSdk=37` | pass |
| App launch / widget UI on API 37 | n/a | not verifiable on this VM (SurfaceFlinger crash loop in the headless API 37 image) | not verified |
| CI (`.github/workflows/test.yml`, API 37) | API 34 | run URL in §4 — outcome reported from the real run only | see §4 |

## 2. JVM tests and coverage

Commands and per-class tables: `docs/upgrade/test-evidence.md`. New tests: `WidgetLineProviderTest`
(14), `WidgetImplTest` (7), `WidgetUpdateReceiverTest` (2) — formatting, ordering, empty state,
truncation to 4/1 lines, click `PendingIntent`, Android 17 RemoteViews parcel-size guard (IMP-01),
manifest/intent-filter consistency of the refresh action (IMP-07). The 56 pre-existing tests are
unchanged and pass. Robolectric sandbox is SDK 35 (SDK 36/37 need a JDK 21 test JVM — see
test-evidence.md §4 for the experiment and why it was not adopted).

## 3. Emulator evidence (details: `docs/upgrade/evidence/README.md`)

API 35 (`api35` AVD, Android 15):
- install of the targetSdk-37 build, app start, medicine + reminder created through the UI;
- both widgets placed from the launcher picker; "Next reminders" renders
  `10/13/26 8:00 AM: 2 Aspirin`, "Recent events" renders `10:26 AM: Taken 2 Aspirin`
  (`api35-05`, `api35-07` screenshots); tapping a widget opens `MainActivity` (`api35-08` recording);
- `am broadcast … WIDGET_UPDATE` → `AppWidgetServiceImpl` updates widget ids 2 and 5 (IMP-07 path);
- `connectedDebugAndroidTest`: first run with Espresso 3.7.0 failed 15/21 (all
  `LooperIdlingResource-Export` timeouts); the same tests pass on `main` and with Espresso
  reverted; after pinning Espresso 3.6.1 the full suite passes **21/21**.

API 37 (`api37` AVD, Android 17): `adb install` **Success**, `targetSdk=37` reported by the package
manager. App launch not verifiable (platform `surfaceflinger` assertion loop on this VM, no MedTimer
crash in logcat).

## 4. CI

`.github/workflows/test.yml` now runs the instrumented suite on an API 37 emulator
(`target_api_level: 37`). Run for this branch: first run https://github.com/eashansinha/medTimer/actions/runs/38256240864 — `test` job **failed before any test ran**: the pinned `android-emulator-runner` v2.33.0 ran `sdkmanager --install platforms;android-37`, which does not exist (the API 37 packages are `android-37.0`); `dependency-review` fails with "Dependency review is not supported on this repository" (repository setting, pre-existing). Follow-up commit: runner → v2.38.0 and `target_api_level: "37.0"`; run 2: https://github.com/eashansinha/medTimer/actions/runs/38256772982 — `build` passed; `Analyze (java-kotlin)` (CodeQL, runs `./gradlew build` incl. lint) failed on the 4 remaining `GradleDependency` lint errors (on `main` the same job fails on the 19 baseline lint errors) and reports "Analysis upload status is failed / configuration error" because code scanning is not enabled on the repository; `dependency-review` fails for the same repository-setting reason. `test`/`androidTest` outcomes: see the PR checks.

## 5. Not verified / open items

1. Widget rendering and the Android 17 RemoteViews memory limit **at runtime on API 37** (VM
   limitation); covered indirectly by the SDK 35 parcel-size guard test and the CI API 37 run.
2. Robolectric suite on SDK 37 (needs JDK 21 test JVM; two pre-existing tests break on JDK 21).
3. Espresso 3.7.0 compatibility of the app's debug-only `IdlingRegistry` looper registration
   (currently pinned to 3.6.1; 4 lint findings remain).
4. SonarQube plugin disabled on the branch (incompatible with AGP 9.4 here).
5. Other modules (reminders, notifications, …) were not assessed for Android 16/17 in this ticket.
6. Release build (`assembleRelease`, R8) was not exercised; only debug builds were produced.

## 6. Reproduce

```
git fetch origin && git checkout devin/ee-31-widgets-android-17
./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug --continue
# emulator (API 35): see docs/upgrade/evidence/README.md for the exact emulator/adb commands
ANDROID_SERIAL=emulator-5554 ./gradlew --no-daemon connectedDebugAndroidTest
```
