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

import com.futsch1.medtimer.reminders.SnoozeWork;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Below Android 12 (API 31) RescheduleWork never requests exact alarms, independent of the preference.
 * Separate class because the Robolectric JUnit 5 extension does not allow per-method SDK overrides.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 30)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class ExactAlarmSchedulingPreSUnitTest {

    @Test
    public void testInexactAlarmBelowAndroid12RegardlessOfPreference() {
        Application mockApplication = mock(Application.class);
        when(mockApplication.getPackageName()).thenReturn("test");
        when(mockApplication.getSystemService(NotificationManager.class)).thenReturn(mock(NotificationManager.class));
        AlarmManager mockAlarmManager = mock(AlarmManager.class);
        when(mockApplication.getSystemService(AlarmManager.class)).thenReturn(mockAlarmManager);

        SharedPreferences mockSharedPreferences = mock(SharedPreferences.class);
        when(mockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(true);

        WorkerParameters workerParams = mock(WorkerParameters.class);
        when(workerParams.getInputData()).thenReturn(new Data.Builder()
                .putInt(EXTRA_REMINDER_ID, 11)
                .putInt(EXTRA_REMINDER_EVENT_ID, 12)
                .putInt(EXTRA_SNOOZE_TIME, 15)
                .putInt(EXTRA_NOTIFICATION_ID, 14)
                .build());

        ListenableWorker.Result result;
        try (MockedStatic<PreferenceManager> mockedPreferencesManager = mockStatic(PreferenceManager.class)) {
            mockedPreferencesManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            result = new SnoozeWork(mockApplication, workerParams).doWork();
        }
        assertInstanceOf(ListenableWorker.Result.Success.class, result);

        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any());
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
    }
}
