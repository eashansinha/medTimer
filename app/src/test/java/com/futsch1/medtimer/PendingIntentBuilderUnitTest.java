package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_DATE;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_TIME;
import static com.futsch1.medtimer.ActivityCodes.REMINDER_ACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

import com.futsch1.medtimer.reminders.PendingIntentBuilder;
import com.futsch1.medtimer.reminders.ReminderProcessor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPendingIntent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Pins the PendingIntent contract the alarm scheduling relies on: immutable broadcast intents
 * keyed by reminder event id so that a new alarm for the same event replaces the previous one.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class PendingIntentBuilderUnitTest {

    private Context context;

    @BeforeEach
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
    }

    @Test
    public void testBuildsImmutableBroadcastKeyedByReminderEventId() {
        LocalDateTime reminderDateTime = LocalDateTime.of(LocalDate.of(2026, 10, 12), LocalTime.of(8, 30));
        PendingIntent pendingIntent = new PendingIntentBuilder(context)
                .setReminderId(11)
                .setReminderEventId(12)
                .setReminderDateTime(reminderDateTime)
                .build();
        assertNotNull(pendingIntent);

        ShadowPendingIntent shadow = Shadow.extract(pendingIntent);
        assertTrue(shadow.isBroadcast());
        assertEquals(12, shadow.getRequestCode());
        assertEquals(PendingIntent.FLAG_IMMUTABLE, shadow.getFlags() & PendingIntent.FLAG_IMMUTABLE);
        assertEquals(0, shadow.getFlags() & PendingIntent.FLAG_MUTABLE);
        assertEquals(PendingIntent.FLAG_UPDATE_CURRENT, shadow.getFlags() & PendingIntent.FLAG_UPDATE_CURRENT);

        Intent savedIntent = shadow.getSavedIntent();
        assertEquals(REMINDER_ACTION, savedIntent.getAction());
        assertEquals(new ComponentName(context, ReminderProcessor.class), savedIntent.getComponent());
        assertEquals(11, savedIntent.getIntExtra(EXTRA_REMINDER_ID, -1));
        assertEquals(12, savedIntent.getIntExtra(EXTRA_REMINDER_EVENT_ID, -1));
        assertEquals(reminderDateTime.toLocalDate().toEpochDay(), savedIntent.getLongExtra(EXTRA_REMINDER_DATE, -1));
        assertEquals(reminderDateTime.toLocalTime().toSecondOfDay(), savedIntent.getIntExtra(EXTRA_REMINDER_TIME, -1));
    }

    @Test
    public void testSameReminderEventIdUpdatesExistingPendingIntent() {
        PendingIntent first = new PendingIntentBuilder(context)
                .setReminderId(11)
                .setReminderEventId(12)
                .setReminderDateTime(LocalDateTime.of(2026, 10, 12, 8, 0))
                .build();
        PendingIntent second = new PendingIntentBuilder(context)
                .setReminderId(99)
                .setReminderEventId(12)
                .setReminderDateTime(LocalDateTime.of(2026, 10, 13, 9, 0))
                .build();

        // FLAG_UPDATE_CURRENT with the same request code: one PendingIntent, extras replaced
        assertEquals(first, second);
        ShadowPendingIntent shadow = Shadow.extract(second);
        assertEquals(99, shadow.getSavedIntent().getIntExtra(EXTRA_REMINDER_ID, -1));
        assertEquals(LocalDate.of(2026, 10, 13).toEpochDay(), shadow.getSavedIntent().getLongExtra(EXTRA_REMINDER_DATE, -1));
    }

    @Test
    public void testDifferentReminderEventIdsAreDistinctPendingIntents() {
        PendingIntent first = new PendingIntentBuilder(context).setReminderId(11).setReminderEventId(12).build();
        PendingIntent second = new PendingIntentBuilder(context).setReminderId(11).setReminderEventId(13).build();

        assertNotEquals(first, second);
    }

    @Test
    public void testNextReminderIntentMatchesCancelIntentOfRescheduleWork() {
        // RescheduleWork.cancelNextReminder() cancels request code 0 with the bare reminder action
        PendingIntent scheduled = new PendingIntentBuilder(context).setReminderId(11).setReminderEventId(0).setReminderDateTime(LocalDateTime.of(2026, 10, 12, 8, 0)).build();
        Intent cancelIntent = ReminderProcessor.getReminderAction(context, 0, 0, null);
        PendingIntent cancel = PendingIntent.getBroadcast(context, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE);

        assertEquals(scheduled, cancel);
    }
}
