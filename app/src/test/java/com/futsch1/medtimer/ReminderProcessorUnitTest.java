package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.DISMISSED_ACTION;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_NOTIFICATION_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_DATE;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_TIME;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_SNOOZE_TIME;
import static com.futsch1.medtimer.ActivityCodes.REMINDER_ACTION;
import static com.futsch1.medtimer.ActivityCodes.SNOOZE_ACTION;
import static com.futsch1.medtimer.ActivityCodes.TAKEN_ACTION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Application;
import android.content.Intent;
import android.os.Build;

import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.impl.model.WorkSpec;

import com.futsch1.medtimer.reminders.ReminderProcessor;
import com.futsch1.medtimer.reminders.ReminderWork;
import com.futsch1.medtimer.reminders.SkippedWork;
import com.futsch1.medtimer.reminders.SnoozeWork;
import com.futsch1.medtimer.reminders.TakenWork;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Pins the work requests {@link ReminderProcessor} enqueues from its broadcasts, in particular that the
 * alarm-triggered reminder work is expedited on API 31+ (and only there).
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class ReminderProcessorUnitTest {

    private final int reminderId = 11;
    private final int reminderEventId = 12;
    private final int notificationId = 14;
    private Application mockApplication;
    private MockedStatic<WorkManagerAccess> mockedWorkManagerAccess;
    private int originalSdkInt;

    @BeforeEach
    public void setUp() {
        mockApplication = mock(Application.class);
        mockedWorkManagerAccess = mockStatic(WorkManagerAccess.class);
        originalSdkInt = Build.VERSION.SDK_INT;
    }

    @AfterEach
    public void tearDown() {
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", originalSdkInt);
        mockedWorkManagerAccess.close();
    }

    @Test
    public void testReminderActionIsExpeditedOnApi31AndAbove() {
        WorkSpec workSpec = receiveAndCapture(buildReminderIntent());

        assertEquals(ReminderWork.class.getName(), workSpec.workerClassName);
        assertTrue(workSpec.expedited);
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, workSpec.outOfQuotaPolicy);
        assertEquals(0, workSpec.initialDelay);
        assertEquals(reminderId, workSpec.input.getInt(EXTRA_REMINDER_ID, 0));
        assertEquals(reminderEventId, workSpec.input.getInt(EXTRA_REMINDER_EVENT_ID, 0));
        assertEquals(20000, workSpec.input.getLong(EXTRA_REMINDER_DATE, 0));
        assertEquals(3600, workSpec.input.getInt(EXTRA_REMINDER_TIME, 0));
    }

    @Test
    public void testReminderActionIsNotExpeditedBelowApi31() {
        // Below S, WorkManager would run expedited work as a foreground service (needs getForegroundInfo())
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", Build.VERSION_CODES.R);

        WorkSpec workSpec = receiveAndCapture(buildReminderIntent());

        assertEquals(ReminderWork.class.getName(), workSpec.workerClassName);
        assertFalse(workSpec.expedited);
        assertEquals(reminderId, workSpec.input.getInt(EXTRA_REMINDER_ID, 0));
    }

    @Test
    public void testActionBroadcastsStayRegularWork() {
        Intent dismissed = new Intent(DISMISSED_ACTION).putExtra(EXTRA_REMINDER_EVENT_ID, reminderEventId);
        WorkSpec workSpec = receiveAndCapture(dismissed);
        assertEquals(SkippedWork.class.getName(), workSpec.workerClassName);
        assertFalse(workSpec.expedited);
        assertEquals(reminderEventId, workSpec.input.getInt(EXTRA_REMINDER_EVENT_ID, 0));

        Intent taken = new Intent(TAKEN_ACTION).putExtra(EXTRA_REMINDER_EVENT_ID, reminderEventId);
        workSpec = receiveAndCapture(taken);
        assertEquals(TakenWork.class.getName(), workSpec.workerClassName);
        assertFalse(workSpec.expedited);

        Intent snooze = new Intent(SNOOZE_ACTION)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_REMINDER_EVENT_ID, reminderEventId)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_SNOOZE_TIME, 30);
        workSpec = receiveAndCapture(snooze);
        assertEquals(SnoozeWork.class.getName(), workSpec.workerClassName);
        assertFalse(workSpec.expedited);
        assertEquals(30, workSpec.input.getInt(EXTRA_SNOOZE_TIME, 0));
        assertEquals(notificationId, workSpec.input.getInt(EXTRA_NOTIFICATION_ID, 0));
    }

    private Intent buildReminderIntent() {
        return new Intent(REMINDER_ACTION)
                .putExtra(EXTRA_REMINDER_ID, reminderId)
                .putExtra(EXTRA_REMINDER_EVENT_ID, reminderEventId)
                .putExtra(EXTRA_REMINDER_DATE, 20000L)
                .putExtra(EXTRA_REMINDER_TIME, 3600);
    }

    private WorkSpec receiveAndCapture(Intent intent) {
        WorkManager workManager = mock(WorkManager.class);
        mockedWorkManagerAccess.when(() -> WorkManagerAccess.getWorkManager(mockApplication)).thenReturn(workManager);

        new ReminderProcessor().onReceive(mockApplication, intent);

        ArgumentCaptor<WorkRequest> captor = ArgumentCaptor.forClass(WorkRequest.class);
        verify(workManager, times(1)).enqueue(captor.capture());
        return captor.getValue().getWorkSpec();
    }
}
