# EE-31 — Emulator / device evidence (DRAFT for human review)

Every fact below is labelled with the API level it was produced on. Commands are quoted.
Build under test: `app/build/outputs/apk/debug/MedTimer-debug.apk` from this branch
(`./gradlew --no-daemon assembleDebug`; `aapt2 dump badging` → `compileSdkVersion='37'`,
`targetSdkVersion:'37'`, `versionName='1.16.5'`).

## API 35 (AVD `api35`, `sdk_gphone64_x86_64`, Android 15, headless)

```
emulator -avd api35 -no-window -gpu swiftshader_indirect -no-audio -no-boot-anim -no-metrics -memory 3072 -cores 4 -partition-size 6144 -no-snapshot
adb install -r -g app/build/outputs/apk/debug/MedTimer-debug.apk      # -> Performing Streamed Install / Success
adb shell dumpsys package com.futsch1.medtimer | grep -E "versionName|targetSdk"  # -> versionCode=105 minSdk=28 targetSdk=37, versionName=1.16.5
adb shell am start -W -n com.futsch1.medtimer/.MainActivity          # -> Status: ok
adb exec-out screencap -p > <file>.png
```

| File | What it shows (API 35) |
|---|---|
| `api35-01-app-start.png` | targetSdk-37 build starts; Overview screen, empty |
| `api35-02-medicine-with-reminder.png` | medicine "Aspirin" with one reminder (dosage 2, 8:00 AM) created through the UI (`uiautomator dump` + `input tap/text`) |
| `api35-03-overview-next-reminders.png` | Overview shows "2 of Aspirin, 10/13/26 8:00 AM" |
| `api35-04-widget-picker.png` | Launcher3 widget picker lists both MedTimer widgets with their `previewLayout` (4 × 1, "Next reminders" / "Recent events") |
| `api35-05-home-next-reminders-widget.png` | "Next reminders" widget placed on the home screen (long-press in the picker), line `10/13/26 8:00 AM: 2 Aspirin` — i.e. `NextRemindersLineProvider` → `WidgetImpl` → launcher host rendering with the real date prefix |
| `api35-06-overview-after-logged-dose.png` | a dose logged via "Log additional dose" (creates a TAKEN reminder event) |
| `api35-07-home-both-widgets.png` | both widgets on the home screen; "Recent events" shows `10:26 AM: Taken 2 Aspirin` (`LatestRemindersLineProvider`) |
| `api35-08-widget-tap-opens-app.mp4` | `adb shell screenrecord`: tapping each widget opens `MainActivity` (the `FLAG_IMMUTABLE` activity `PendingIntent`, IMP-04/05) |

Widget placement: the first widget was placed by a long press on its picker cell
(`input swipe x y x y 1200`), the second needed `input draganddrop <cell-x> <cell-y> 360 950 2000`.

Refresh broadcast (IMP-07), API 35, run as `adb root` because the receiver is `exported="false"`:

```
adb shell am broadcast -n com.futsch1.medtimer/.widgets.WidgetUpdateReceiver -a com.futsch1.medtimer.WIDGET_UPDATE
# Broadcast completed: result=0
# logcat: AppWidgetServiceImpl: Trying to notify widget update for package com.futsch1.medtimer with widget id: 2
#         AppWidgetServiceImpl: Trying to notify widget update for package com.futsch1.medtimer with widget id: 5
```

→ both placed widgets (ids 2 and 5) were updated by one `WidgetUpdateReceiver` broadcast.

### `connectedDebugAndroidTest` (API 35)

```
./gradlew --no-daemon connectedDebugAndroidTest --continue
```

Two runs on the same AVD (`ANDROID_SERIAL=emulator-5554`), result XML in
`api35-connectedDebugAndroidTest/`:

| Run | Build | Tests | Failures | Duration | File |
|---|---|---|---|---|---|
| 1 | branch with Espresso **3.7.0** (as first bumped) | 21 | **15** | 2 h 33 min | `TEST-api35-espresso-3.7.0-before-pin.xml` |
| 2 | branch with Espresso pinned at **3.6.1** (commit "IMP-T6: pin Espresso") | 21 | **0** | 16 min 37 s | `TEST-api35-final-espresso-3.6.1.xml` |

All 15 failures of run 1 had one root cause: `androidx.test.espresso.IdlingResourceTimeoutException:
Wait for [LooperIdlingResource-NN-Export] to become idle timed out` — the `HandlerThread("Export")`
looper that `OptionsMenu` registers with `IdlingRegistry` in debug builds never reports idle under
Espresso 3.7.0 (9 tests surfaced it as Barista "Could not perform action single click on view
R.id.medicinesFragment", 6 directly). Classification runs on the same emulator:

```
# main @ 8fde0b91 (AGP 8.9, Espresso 3.6.1), BasicUITest + CalendarTest -> 3 tests, 0 failures, 1m13s
# branch, all test libs new except espresso 3.6.1, BasicUITest           -> 2 tests, 0 failures, 1m17s
```

→ regression caused by the Espresso 3.7.0 bump, not by compileSdk/targetSdk 37 or the widget
changes; fixed by pinning Espresso (see `docs/upgrade/test-evidence.md` §5). Run 2 is the final
state of the branch: **21 / 21 instrumented tests pass on API 35**, including
`ScreenshotsTest`, `NotificationTest` and `TestDataAndDeleteAndManualDoseTest`.


## API 37 (AVD `api37`)

```
rm -f ~/.android/avd/api37.avd/*.lock
emulator -avd api37 -port 5556 -no-window -gpu swiftshader_indirect -no-audio -no-boot-anim -no-metrics -memory 3072 -cores 2 -partition-size 6144 -no-snapshot
adb -s emulator-5556 shell getprop ro.build.version.sdk ro.build.version.release   # -> 37 / 17
adb -s emulator-5556 install -r -g app/build/outputs/apk/debug/MedTimer-debug.apk
# -> Performing Streamed Install
# -> Success
adb -s emulator-5556 shell dumpsys package com.futsch1.medtimer | grep -E "versionName|targetSdk"
# -> versionCode=105 minSdk=28 targetSdk=37 / versionName=1.16.5
```

Full transcript: `api37-adb-install.log`. Facts (API 37):

- **`adb install` of the targetSdk-37 APK succeeds on Android 17 (API 37)**; the package manager
  accepts the manifest/targetSdk (first attempt without `-partition-size` failed with "Requested
  internal only, but not enough space" — AVD disk, not the app).
- Launching `MainActivity` and placing widgets **could not be verified on API 37 on this VM**:
  `surfaceflinger` aborts in a loop (`Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma`,
  20 crashes in the logcat buffer), `system_server` restarts with it and `am start` /
  `pm`/`dumpsys appwidget` intermittently answer "Can't find service: package" / "Activity class …
  does not exist". No MedTimer crash appears in the logcat. This matches the known limitation in
  the environment notes; the API 37 runtime check of IMP-01 relies on the CI emulator run and on
  the Robolectric guard test.


Known limitation of this VM (from the environment notes): the API 36/37 system images crash-loop
SurfaceFlinger under the headless emulator (gfxstream `hasReadColorBufferDma` assertion), so API 37
is used for `adb install` evidence only; UI/instrumented coverage on API 37 comes from the CI run.

## CI (GitHub Actions, API 37 emulator)

`.github/workflows/test.yml` → `target_api_level: "37.0"`, `android-emulator-runner` v2.38.0.

- Run 1 (commit `a2d19a40`, runner v2.33.0, `target_api_level: 37`): https://github.com/eashansinha/medTimer/actions/runs/38256240864 — `test` job failed at "Install Android SDK": `Warning: Failed to find package 'platforms;android-37'` (no test executed). `dependency-review` job: "Dependency review is not supported on this repository" (GitHub setting, unrelated to this change).
- Run 2 (after the workflow fix): see the checks on https://github.com/eashansinha/medTimer/pull/4 — outcome reported from the real run only.
