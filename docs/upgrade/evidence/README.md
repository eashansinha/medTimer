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
