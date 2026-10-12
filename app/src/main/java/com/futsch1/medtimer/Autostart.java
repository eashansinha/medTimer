package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_DATE;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_TIME;

import android.app.AlarmManager;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkRequest;

import com.futsch1.medtimer.database.MedicineRepository;
import com.futsch1.medtimer.database.ReminderEvent;
import com.futsch1.medtimer.reminders.ReminderProcessor;
import com.futsch1.medtimer.reminders.ReminderWork;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manifest receiver for system broadcasts that invalidate the currently scheduled alarm.
 * <p>
 * BOOT_COMPLETED / MY_PACKAGE_REPLACED: all alarms are gone, so the next reminder is rescheduled and
 * notifications that were raised but not yet processed are restored.
 * TIME_SET / TIMEZONE_CHANGED: the wall clock the reminder times refer to moved, so the next reminder
 * is rescheduled.
 * ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31+): the user granted "Alarms &amp; reminders"
 * after the next reminder was scheduled inexactly, so it is rescheduled (RescheduleWork re-checks
 * canScheduleExactAlarms()).
 * <p>
 * Only WorkManager work is enqueued here, never a foreground service, so the Android 14/15
 * BOOT_COMPLETED foreground service restrictions do not apply.
 */
public class Autostart extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) {
            return;
        }
        if (isRestoreAction(action)) {
            Log.i("Autostart", "Requesting reschedule");
            ReminderProcessor.requestReschedule(context);
            Log.i("Autostart", "Restore reminders");
            restoreReminders(context);
        } else if (isRescheduleOnlyAction(action)) {
            Log.i("Autostart", "Requesting reschedule because of " + action);
            ReminderProcessor.requestReschedule(context);
        }
    }

    static boolean isRestoreAction(@NonNull String action) {
        return Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
    }

    static boolean isRescheduleOnlyAction(@NonNull String action) {
        return Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action));
    }

    private void restoreReminders(Context context) {
        MedicineRepository repo = new MedicineRepository((Application) context.getApplicationContext());
        HandlerThread thread = new HandlerThread("RestoreReminders");
        thread.start();
        new Handler(thread.getLooper()).post(() -> {
            //noinspection SimplifyStreamApiCallChains
            @SuppressWarnings("java:S6204") // Using SDK 33
            List<ReminderEvent> reminderEventList = repo.getLastDaysReminderEvents(1).stream().filter((reminderEvent -> reminderEvent.status == ReminderEvent.ReminderStatus.RAISED)).collect(Collectors.toUnmodifiableList());
            for (ReminderEvent reminderEvent : reminderEventList) {
                ZonedDateTime previousRemindedTimestamp = Instant.ofEpochSecond(reminderEvent.remindedTimestamp).atZone(ZoneId.systemDefault());
                WorkRequest reminderWork =
                        new OneTimeWorkRequest.Builder(ReminderWork.class)
                                .setInputData(new Data.Builder()
                                        .putInt(EXTRA_REMINDER_ID, reminderEvent.reminderId)
                                        .putInt(EXTRA_REMINDER_EVENT_ID, reminderEvent.reminderEventId)
                                        .putLong(EXTRA_REMINDER_DATE, previousRemindedTimestamp.toLocalDate().toEpochDay())
                                        .putInt(EXTRA_REMINDER_TIME, previousRemindedTimestamp.toLocalTime().toSecondOfDay())
                                        .build())
                                .build();
                WorkManagerAccess.getWorkManager(context).enqueue(reminderWork);
            }
        });
    }
}
