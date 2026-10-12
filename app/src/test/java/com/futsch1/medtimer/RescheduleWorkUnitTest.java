package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_DATE;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.WorkerParameters;

import com.futsch1.medtimer.preferences.PreferencesNames;
import com.futsch1.medtimer.reminders.RescheduleWork;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Alarm time calculation / exact alarm selection of the reminder scheduler worker
 * (IMP-11 exact alarms, IMP-14 WorkManager fallback for due reminders).
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class RescheduleWorkUnitTest {

    private static final int REMINDER_ID = 11;
    private Application mockApplication;
    private AlarmManager mockAlarmManager;
    private SharedPreferences mockSharedPreferences;
    private MockedStatic<PreferenceManager> mockedPreferenceManager;
    private boolean exactRemindersPreference = true;

    // Exposes the protected scheduling entry point
    private static class TestableRescheduleWork extends RescheduleWork {
        TestableRescheduleWork(@NonNull Application application, @NonNull WorkerParameters workerParams) {
            super(application, workerParams);
        }

        void schedule(ReminderNotificationData data) {
            enqueueNotification(data);
        }
    }

    @BeforeEach
    public void setUp() {
        mockApplication = mock(Application.class);
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getPackageName()).thenReturn("test");

        mockSharedPreferences = mock(SharedPreferences.class);
        // Return the requested default for every preference except the exact reminders switch
        when(mockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenAnswer(invocation -> {
            if (PreferencesNames.EXACT_REMINDERS.equals(invocation.getArgument(0))) {
                return exactRemindersPreference;
            }
            return invocation.getArgument(1);
        });
        when(mockSharedPreferences.getInt(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(mockSharedPreferences.getString(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(mockSharedPreferences.getStringSet(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        mockedPreferenceManager = mockStatic(PreferenceManager.class);
        mockedPreferenceManager.when(() -> PreferenceManager.getDefaultSharedPreferences(any())).thenReturn(mockSharedPreferences);

        mockAlarmManager = mock(AlarmManager.class);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);
        when(mockApplication.getSystemService(AlarmManager.class)).thenReturn(mockAlarmManager);
    }

    @AfterEach
    public void tearDown() {
        mockedPreferenceManager.close();
    }

    private TestableRescheduleWork createWork() {
        return new TestableRescheduleWork(mockApplication, mock(WorkerParameters.class));
    }

    @Test
    public void futureReminder_exactAlarmAllowed_usesExactAlarmAtReminderTime() {
        Instant timestamp = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        createWork().schedule(new RescheduleWork.ReminderNotificationData(timestamp, REMINDER_ID, "Medicine A"));

        verify(mockAlarmManager).cancel(any(PendingIntent.class));
        verify(mockAlarmManager).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(timestamp.toEpochMilli()), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
        // Next reminder widget is refreshed
        verify(mockApplication).sendBroadcast(any(Intent.class), eq("com.futsch1.medtimer.NOTIFICATION_PROCESSED"));
    }

    @Test
    public void futureReminder_exactRemindersDisabledByUser_usesInexactAlarm() {
        exactRemindersPreference = false;
        Instant timestamp = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        createWork().schedule(new RescheduleWork.ReminderNotificationData(timestamp, REMINDER_ID, "Medicine A"));

        verify(mockAlarmManager).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(timestamp.toEpochMilli()), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void futureReminder_exactAlarmPermissionRevoked_usesInexactAlarm() {
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(false);
        Instant timestamp = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        createWork().schedule(new RescheduleWork.ReminderNotificationData(timestamp, REMINDER_ID, "Medicine A"));

        verify(mockAlarmManager).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(timestamp.toEpochMilli()), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void dueReminder_isEnqueuedImmediatelyAsReminderWork() {
        Instant timestamp = Instant.now().minus(5, ChronoUnit.MINUTES);
        ZonedDateTime expected = timestamp.atZone(ZoneId.systemDefault());
        WorkManager mockWorkManager = mock(WorkManager.class);

        try (MockedStatic<WorkManagerAccess> mockedWorkManagerAccess = mockStatic(WorkManagerAccess.class)) {
            mockedWorkManagerAccess.when(() -> WorkManagerAccess.getWorkManager(mockApplication)).thenReturn(mockWorkManager);

            createWork().schedule(new RescheduleWork.ReminderNotificationData(timestamp, REMINDER_ID, "Medicine A"));
        }

        ArgumentCaptor<WorkRequest> captor = ArgumentCaptor.forClass(WorkRequest.class);
        verify(mockWorkManager).enqueue(captor.capture());
        assertEquals(REMINDER_ID, captor.getValue().getWorkSpec().input.getInt(EXTRA_REMINDER_ID, -1));
        assertEquals(expected.toLocalDate().toEpochDay(), captor.getValue().getWorkSpec().input.getLong(EXTRA_REMINDER_DATE, -1));
        assertEquals(expected.toLocalTime().toSecondOfDay(), captor.getValue().getWorkSpec().input.getInt(EXTRA_REMINDER_TIME, -1));
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }
}
