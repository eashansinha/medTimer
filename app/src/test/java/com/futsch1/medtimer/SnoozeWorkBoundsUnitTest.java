package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.EXTRA_NOTIFICATION_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_SNOOZE_TIME;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
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

import java.time.Instant;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * EE-30 / IMP-20: SnoozeWork bounds — default, minimum and large snooze durations and the
 * exact-vs-inexact alarm decision.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786")
public class SnoozeWorkBoundsUnitTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private Application mockApplication;
    private NotificationManager mockNotificationManager;
    private AlarmManager mockAlarmManager;
    private SharedPreferences mockSharedPreferences;

    @BeforeEach
    public void setUp() {
        mockApplication = mock(Application.class);
        when(mockApplication.getPackageName()).thenReturn("test");
        mockNotificationManager = mock(NotificationManager.class);
        when(mockApplication.getSystemService(NotificationManager.class)).thenReturn(mockNotificationManager);
        mockSharedPreferences = mock(SharedPreferences.class);
        when(mockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        mockAlarmManager = mock(AlarmManager.class);
        when(mockApplication.getSystemService(AlarmManager.class)).thenReturn(mockAlarmManager);
    }

    private ListenableWorker.Result runSnooze(Data inputData) {
        WorkerParameters workerParams = mock(WorkerParameters.class);
        when(workerParams.getInputData()).thenReturn(inputData);
        try (MockedStatic<PreferenceManager> mockedPreferencesManager = mockStatic(PreferenceManager.class);
             MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            mockedPreferencesManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            mockedInstant.when(Instant::now).thenReturn(NOW);
            return new SnoozeWork(mockApplication, workerParams).doWork();
        }
    }

    private static Data input(Integer snoozeMinutes) {
        Data.Builder b = new Data.Builder()
                .putInt(EXTRA_REMINDER_ID, 11)
                .putInt(EXTRA_REMINDER_EVENT_ID, 12)
                .putInt(EXTRA_NOTIFICATION_ID, 14);
        if (snoozeMinutes != null) {
            b.putInt(EXTRA_SNOOZE_TIME, snoozeMinutes);
        }
        return b.build();
    }

    @Test
    public void test_defaultSnoozeIs15Minutes() {
        ListenableWorker.Result result = runSnooze(input(null));
        assertInstanceOf(ListenableWorker.Result.Success.class, result);
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(NOW.plusSeconds(15 * 60).toEpochMilli()), any(PendingIntent.class));
        verify(mockNotificationManager, times(1)).cancel(14);
    }

    @Test
    public void test_oneMinuteSnooze() {
        runSnooze(input(1));
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(NOW.plusSeconds(60).toEpochMilli()), any(PendingIntent.class));
    }

    @Test
    public void test_largeSnoozeOf24HoursDoesNotOverflow() {
        runSnooze(input(24 * 60));
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(NOW.plusSeconds(24 * 3600).toEpochMilli()), any(PendingIntent.class));
    }

    @Test
    public void test_exactAlarmUsedWhenPreferenceAndPermissionAllow() {
        when(mockSharedPreferences.getBoolean(eq(PreferencesNames.EXACT_REMINDERS), anyBoolean())).thenReturn(true);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);
        runSnooze(input(5));
        verify(mockAlarmManager, times(1)).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(NOW.plusSeconds(5 * 60).toEpochMilli()), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(NOW.plusSeconds(5 * 60).toEpochMilli()), any(PendingIntent.class));
    }

    @Test
    public void test_inexactAlarmWhenExactPermissionMissing() {
        when(mockSharedPreferences.getBoolean(eq(PreferencesNames.EXACT_REMINDERS), anyBoolean())).thenReturn(true);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(false);
        runSnooze(input(5));
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(NOW.plusSeconds(5 * 60).toEpochMilli()), any(PendingIntent.class));
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(NOW.plusSeconds(5 * 60).toEpochMilli()), any(PendingIntent.class));
    }
}
