# MedTimer architecture (Android 17 / API 37 branch)

**DRAFT for human review.** Written during the EE-29 upgrade from the code on branch `devin/ee-29-android-17-upgrade`;
file paths are relative to `app/src/main/java/com/futsch1/medtimer/`.

## 1. Components

| Component | Files | Role |
|---|---|---|
| UI shell | `MainActivity.java`, `res/layout/activity_main.xml`, `MedTimerAppIntro.kt`, `Biometrics.kt` | Single activity with a `NavHostFragment`, toolbar and bottom navigation (overview, medicines, statistics). Applies theme, `FLAG_SECURE`, edge-to-edge insets, biometric lock, predictive back callback. |
| Feature fragments | `overview/`, `medicine/`, `statistics/`, `remindertable/`, `preferences/`, `calendar/` | Navigation-component destinations (Safe Args). |
| Data layer | `database/` (`MedicineRoomDatabase`, `MedicineDao`, `MedicineRepository`, entities `Medicine`, `Reminder`, `ReminderEvent`, `Tag`, …), `database/JSONBackup.java`, `BackupManager.java` | Room 2.8 database, repository, JSON backup/restore (Gson + reflection for field validation). |
| Scheduling | `reminders/scheduling/` (`ReminderScheduler`, `StandardScheduling`, `IntervalScheduling`, `LinkedScheduling`, `CyclesHelper`), `reminders/RescheduleWork.java`, `reminders/WeekendMode.java`, `WorkManagerAccess.java` | Pure-JVM calculation of the next reminder instants from medicines, reminders and recent events; `RescheduleWork` turns the first result into an `AlarmManager` alarm (or immediate work). |
| Reminder delivery | `reminders/ReminderProcessor.java` (BroadcastReceiver), `reminders/ReminderWork.java`, `reminders/Notifications.java`, `reminders/NotificationSoundManager.kt`, `reminders/PendingIntentBuilder.java`, `reminders/SnoozeWork.java`, `reminders/RepeatReminderWork.java`, `reminders/TakenSkippedWork.java`, `reminders/StockHandlingWork.java` | Alarm broadcast → WorkManager worker → notification with taken / skipped / snooze actions; action intents are processed by further workers. |
| Boot / lifecycle | `Autostart.java` (BOOT_COMPLETED, MY_PACKAGE_REPLACED), `ReminderSchedulerService.java` | Re-arms the next alarm and re-posts raised-but-unhandled reminders after reboot/update. |
| Widgets | `widgets/WidgetImpl.kt`, `widgets/NextReminderWidgetProvider`, `widgets/OverviewWidgetProvider`, `WidgetUpdateReceiver` | Text-only `RemoteViews` (next reminder, overview), refreshed after every scheduling/notification step. |
| Export | `helpers/FileHelper.java`, `exporters/` (CSV, PDF) | `FileProvider` share intents with `FLAG_GRANT_READ_URI_PERMISSION`. |

## 2. Reminder → alarm → notification flow

```mermaid
sequenceDiagram
    autonumber
    participant UI as UI / Autostart / ReminderProcessor
    participant WM as WorkManager (single-thread executor)
    participant RW as RescheduleWork
    participant SCH as ReminderScheduler (JVM)
    participant AM as AlarmManager
    participant RP as ReminderProcessor (BroadcastReceiver)
    participant REM as ReminderWork
    participant NM as NotificationManager
    participant WG as Widgets (RemoteViews)

    UI->>WM: ReminderProcessor.requestReschedule(context) → enqueue RescheduleWork
    WM->>RW: doWork()
    RW->>SCH: schedule(medicines, last 2 days of ReminderEvents)
    SCH-->>RW: List<ScheduledReminder> (sorted by Instant)
    alt first reminder is in the future
        RW->>RW: WeekendMode.adjustInstant()
        RW->>AM: cancel(PendingIntent); setExactAndAllowWhileIdle(RTC_WAKEUP, t, PendingIntent)<br/>(setAndAllowWhileIdle if exact alarms disabled / not permitted)
        RW->>WG: sendBroadcast(NEXT_REMINDER_WIDGET_UPDATE)
    else first reminder is already due
        RW->>WM: enqueue ReminderWork(reminderId, date, time)
    end
    AM-->>RP: REMINDER_ACTION broadcast at t
    RP->>WM: enqueue ReminderWork
    WM->>REM: doWork()
    REM->>REM: create/update ReminderEvent (RAISED)
    REM->>NM: Notifications.showNotification(...) with taken / skipped / snooze actions
    REM->>NM: NotificationSoundManager (optional DND override: unmute ring stream, extend DND policy)
    REM->>WM: requestReschedule() → next RescheduleWork
    NM-->>RP: action tap (TAKEN_ACTION / SNOOZE_ACTION / DISMISSED_ACTION)
    RP->>WM: TakenSkippedWork / SnoozeWork / RepeatReminderWork
```

Key properties:

* All scheduling maths is in `reminders/scheduling/` and is covered by JVM tests (`ReminderScheduler*UnitTest`, `IntervalTest`,
  `WeekendModeTest`); it has no Android dependencies besides `java.time`.
* Exactly one alarm is armed at a time (the next reminder). `PendingIntent`s are `FLAG_IMMUTABLE`; the request code is the
  reminder event id so that `cancel()` works.
* WorkManager is initialised manually (`WorkManagerAccess`) with a single-thread executor to serialise database access.

## 3. Where OS-version-specific code lives

| Concern | Location | Note |
|---|---|---|
| Exact alarms (`SCHEDULE_EXACT_ALARM`, API 31+) | `RescheduleWork.canScheduleExactAlarms()`, `preferences/PreferencesFragment.java` (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`) | Falls back to inexact alarms; user preference `exact_reminders`. |
| Notification permission (API 33+) | `RequestPostNotificationPermission`, `MainActivity.showIntro()` | Requested in the intro / on first start. |
| Edge-to-edge (API 35 default, API 36 forced) | `MainActivity.applySystemBarInsets()` (IMP-01), `EdgeToEdge.enable(this)` | Insets applied as padding on `@id/mainRoot`; no `fitsSystemWindows`. |
| Predictive back (API 33+/36 default) | `AndroidManifest.xml` `android:enableOnBackInvokedCallback="true"`, `MainActivity.handleBackPressed()` | AndroidX `OnBackPressedCallback`, no `onBackPressed()` override. |
| Ringer / DND override | `reminders/NotificationSoundManager.kt` | `Build.VERSION.SDK_INT > 28` guard for ringer mode; Android 17 background-audio hardening handled in IMP-39/IMP-50. |
| Widgets with size mapping (API 31+) | `widgets/WidgetImpl.kt` | `RemoteViews(Map<SizeF, RemoteViews>)` on S+, single layout below. |
| Biometric lock | `Biometrics.kt` (androidx.biometric 1.1.0) | No direct `FingerprintManager` use (class is absent from the API 37 stubs). |
| Backup/restore reflection | `database/JSONBackup.java` | Reads instance fields only; no static-final writes (IMP-27). |
| Robolectric tests | `app/src/test/**` `@Config(sdk = 34)` | JVM tests run against SDK 34 regardless of `targetSdk`. |

## 4. Build and test tooling

* Gradle 9.8.1, AGP 9.4.1 (built-in Kotlin), JDK 17, `compileSdk`/`targetSdk` 37, `minSdk` 28.
* Unit tests: JUnit 5 + Robolectric 4.14.1 via `tech.apter.junit5.jupiter.robolectric-extension-gradle-plugin`; Mockito 5 (inline).
* Coverage: `./gradlew jacocoUnitTestReport` (unit only) and `./gradlew JacocoDebugCodeCoverage` (unit + connected).
* Instrumented tests: Espresso/Barista/UIAutomator (`app/src/androidTest`), run in `.github/workflows/test.yml` on an API 37 emulator.
