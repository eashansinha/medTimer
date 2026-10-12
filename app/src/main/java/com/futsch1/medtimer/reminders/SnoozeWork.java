package com.futsch1.medtimer.reminders;

import static com.futsch1.medtimer.ActivityCodes.EXTRA_NOTIFICATION_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_SNOOZE_TIME;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.WorkerParameters;

import java.time.Instant;

/*
 * Worker that snoozes a reminder and re-raises it once the snooze time has expired.
 */
public class SnoozeWork extends RescheduleWork {

    public static final int DEFAULT_SNOOZE_MINUTES = 15;
    public static final int MAX_SNOOZE_MINUTES = 7 * 24 * 60;

    public SnoozeWork(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Data inputData = getInputData();
        int snoozeTime = sanitizeSnoozeMinutes(inputData.getInt(EXTRA_SNOOZE_TIME, DEFAULT_SNOOZE_MINUTES));
        Instant remindTime = Instant.now().plusSeconds(snoozeTime * 60L);

        int reminderId = inputData.getInt(EXTRA_REMINDER_ID, 0);
        int reminderEventId = inputData.getInt(EXTRA_REMINDER_EVENT_ID, 0);
        int notificationId = inputData.getInt(EXTRA_NOTIFICATION_ID, 0);

        // Cancel a potential repeat alarm
        NotificationAction.cancelPendingAlarms(context, reminderEventId);

        ReminderNotificationData reminderNotificationData = new ReminderNotificationData(
                remindTime,
                reminderId,
                "Snooze",
                reminderEventId);
        enqueueNotification(reminderNotificationData);

        NotificationAction.cancelNotification(context, notificationId);

        return Result.success();
    }

    /**
     * The custom snooze dialog accepts any signed integer. Zero or negative would re-raise the
     * reminder immediately and loop; fall back to the default. Clamp absurd values to one week so a
     * typo cannot hide a reminder indefinitely while multi-day custom snoozes keep working.
     */
    public static int sanitizeSnoozeMinutes(int minutes) {
        if (minutes <= 0) {
            return DEFAULT_SNOOZE_MINUTES;
        }
        return Math.min(minutes, MAX_SNOOZE_MINUTES);
    }
}
