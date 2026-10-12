package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.EXTRA_NOTIFICATION_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_SNOOZE_TIME;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.Application;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.futsch1.medtimer.preferences.PreferencesNames;
import com.futsch1.medtimer.reminders.SnoozeWork;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Pins the exact vs. inexact alarm decision in RescheduleWork.enqueueNotification:
 * setExactAndAllowWhileIdle is only used when the "exact reminders" preference is on AND
 * AlarmManager.canScheduleExactAlarms() is true; otherwise setAndAllowWhileIdle is used.
 * SnoozeWork is used as the concrete RescheduleWork subclass (snooze time is always in the future).
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class RescheduleWorkExactAlarmUnitTest {

    private Application mockApplication;
    private AlarmManager mockAlarmManager;
    private SharedPreferences mockSharedPreferences;
    private WorkerParameters workerParams;

    @BeforeEach
    public void setUp() {
        mockApplication = mock(Application.class);
        when(mockApplication.getPackageName()).thenReturn("test");
        when(mockApplication.getSystemService(NotificationManager.class)).thenReturn(mock(NotificationManager.class));

        mockAlarmManager = mock(AlarmManager.class);
        when(mockApplication.getSystemService(AlarmManager.class)).thenReturn(mockAlarmManager);

        mockSharedPreferences = mock(SharedPreferences.class);
        when(mockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(false);

        workerParams = mock(WorkerParameters.class);
        Data inputData = new Data.Builder()
                .putInt(EXTRA_REMINDER_ID, 11)
                .putInt(EXTRA_REMINDER_EVENT_ID, 12)
                .putInt(EXTRA_SNOOZE_TIME, 15)
                .putInt(EXTRA_NOTIFICATION_ID, 14)
                .build();
        when(workerParams.getInputData()).thenReturn(inputData);
    }

    private void runReschedule(boolean exactRemindersPreference, boolean canScheduleExactAlarms) {
        when(mockSharedPreferences.getBoolean(eq(PreferencesNames.EXACT_REMINDERS), anyBoolean())).thenReturn(exactRemindersPreference);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(canScheduleExactAlarms);

        try (MockedStatic<PreferenceManager> mockedPreferencesManager = mockStatic(PreferenceManager.class)) {
            mockedPreferencesManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            ListenableWorker.Result result = new SnoozeWork(mockApplication, workerParams).doWork();
            assertInstanceOf(ListenableWorker.Result.Success.class, result);
        }
    }

    @Test
    public void exactAlarmWhenPreferenceOnAndPermissionGranted() {
        runReschedule(true, true);

        verify(mockAlarmManager, times(1)).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void inexactAlarmWhenPreferenceOnButPermissionDenied() {
        runReschedule(true, false);

        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void inexactAlarmWhenPreferenceOffEvenIfPermissionGranted() {
        runReschedule(false, true);

        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void alarmIsNeverSetExactWithoutWhileIdle() {
        runReschedule(true, true);

        verify(mockAlarmManager, never()).setExact(anyInt(), anyLong(), any(PendingIntent.class));
        verify(mockAlarmManager, never()).set(anyInt(), anyLong(), any(PendingIntent.class));
    }
}
