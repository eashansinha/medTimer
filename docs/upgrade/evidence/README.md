# Emulator evidence — EE-32 Android 17 upgrade

All files in this directory were produced on **API 35** (`api35` AVD, Google APIs x86_64, 720x1600, headless `-no-window -gpu swiftshader_indirect`) running the **upgraded APK (targetSdk 37, versionName 1.16.5)** on 2026-10-12. Android 17 (API 37) UI evidence could not be produced on the build VM (the API 37 system image crash-loops SurfaceFlinger under the headless emulator); it comes from the GitHub Actions workflow on the PR instead. Synthetic data only.

## How it was produced

Script: [`api35-flow.sh`](api35-flow.sh) (every tap targets a node found in the live `uiautomator dump`; log in [`api35-flow.txt`](api35-flow.txt)). Preparation commands:

```
adb shell pm clear com.futsch1.medtimer
adb shell pm grant com.futsch1.medtimer android.permission.POST_NOTIFICATIONS
adb shell appops set com.futsch1.medtimer SCHEDULE_EXACT_ALARM allow   # what a user grants in Settings > Alarms & reminders
adb shell screenrecord --time-limit 170 /sdcard/demo.mp4 &            # -> api35-demo.mp4 (adb pull)
adb shell am start -W -n com.futsch1.medtimer/.MainActivity           # Status: ok, TotalTime: 1417 ms (cold start)
```

Flow: Overview → Medicine tab → *Add medicine* "Medicine A 500 mg" → *Add reminder* (dosage 1, **interval-based reminder every 2 minutes**, starting now) → *Create reminder* → Overview → wait for the alarm → notification → shade → Overview. The interval variant was chosen because the Material time picker could not be driven reliably through `adb shell input`; it exercises the same `RescheduleWork` → `AlarmManager.setExactAndAllowWhileIdle` → `ReminderProcessor` → `ReminderWork` → notification path (IMP-52, IMP-34).

## Files

| File | Shows |
|---|---|
| `api35-01-overview-empty.png` | App start, empty Overview (edge-to-edge: toolbar below status bar, bottom navigation above gesture bar — IMP-01) |
| `api35-02-medicine-created.png` | Edit screen for "Medicine A 500 mg" |
| `api35-03-reminder-dialog-interval.png` | New-reminder dialog: dosage 1, interval 2 minutes |
| `api35-05-medicine-with-reminder.png` | Medicine with the created reminder |
| `api35-06-overview-next-reminder.png` | Overview after creation |
| `api35-07-notification-statusbar.png` | Status bar with the MedTimer notification icon after the alarm fired |
| `api35-08-notification-shade.png` | Notification shade: "Medicine reminder — It is 5:42 PM - time to take 1 of Medicine A 500 mg" (and the 5:40 PM reminder raised immediately at interval start) |
| `api35-09-overview-after-reminder.png` | Overview after the reminder was raised |
| `api35-demo.mp4` | 170 s screen recording of the whole flow |
| `api35-notification-dumpsys.txt` | `adb shell dumpsys notification --noredact` excerpt proving the posted notification (title/text/channel) |
| `api35-flow.txt` | Timestamped log incl. `dumpsys alarm` line `RTC_WAKEUP … origWhen 1791826920000 com.futsch1.medtimer` = 17:42:00 UTC, i.e. the exact alarm scheduled 2 min ahead |

## Instrumented suite

`./gradlew connectedDebugAndroidTest` results on API 35 (before/after) are in [`../test-evidence.md`](../test-evidence.md).

## Independent second run — `api35-independent/` (API 35, upgraded APK targetSdk 37)

A second, independent end-to-end pass was driven on the same `api35` AVD by a separate testing agent after PR #8 was opened
(synthetic data only, local Room storage, adb-driven UI, one continuous `screenrecord`). All facts are from **API 35**.

| File | What it shows |
|---|---|
| `install-launch.txt` | `adb install -r -g` → Success; `dumpsys package`: `targetSdk=37 minSdk=28`; `am start -W` → `Status: ok` |
| `01-empty-overview.png` … `05-next-reminder.png` | Fresh Overview, "Medicine A 500 mg" created, interval dialog (every 2 minutes), reminder saved, Overview showing the upcoming 7:04 PM reminder |
| `created-at.txt` / `second-notification-at.txt` | Reminder saved 2026-10-12T19:02:58Z; scheduled notification posted 2026-10-12T19:04:00Z (62 s later) |
| `06-notification-shade.png` … `08-notification-actions.png` | Notification shade, expanded group, notification with the **Taken** action |
| `notifications-initial.txt` / `notifications-posted.txt` / `notifications-after-taken.txt` | `dumpsys notification --noredact` before, at posting and after tapping Taken |
| `alarm.txt` | `dumpsys alarm` entry for `com.futsch1.medtimer` |
| `09-event-before-taken.png` / `10-event-taken.png` / `12-taken-persisted.png` | Overview event before Taken, after tapping Taken in the notification, and after force-stop + cold relaunch (state persisted) |
| `13-regression-analysis.png` | Analysis screen: medicine count 1, Taken 100 % (charts use the changed `colorPrimary` attribute lookup) |
| `crash-log.txt` | `logcat -b crash` — empty |
| `api35-reminder-taken.mp4` | Continuous 183 s recording of the whole flow |

Observed caveat: the interval dialog defaults its start time to the current minute, so a first reminder posts immediately after
saving; the run therefore waited for the *next* scheduled notification (7:04 PM) before asserting delivery.
