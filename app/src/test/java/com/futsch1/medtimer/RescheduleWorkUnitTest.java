package com.futsch1.medtimer;

import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.WorkerParameters;

import com.futsch1.medtimer.preferences.PreferencesNames;
import com.futsch1.medtimer.reminders.RescheduleWork;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Protects the exact-alarm scheduling decision of {@link RescheduleWork} (IMP-52):
 * exact alarm when allowed, inexact fallback when the permission or preference is missing,
 * and immediate WorkManager execution for reminders that are already due.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class RescheduleWorkUnitTest {

    private Application mockApplication;
    private AlarmManager mockAlarmManager;
    private SharedPreferences mockSharedPreferences;
    private WorkManager mockWorkManager;

    @BeforeEach
    public void setUp() {
        mockApplication = mock(Application.class);
        when(mockApplication.getPackageName()).thenReturn("test");
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        mockAlarmManager = mock(AlarmManager.class);
        when(mockApplication.getSystemService(AlarmManager.class)).thenReturn(mockAlarmManager);
        mockSharedPreferences = mock(SharedPreferences.class);
        // Weekend mode off, exact reminders on by default
        when(mockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        when(mockSharedPreferences.getBoolean(eq(PreferencesNames.EXACT_REMINDERS), anyBoolean())).thenReturn(true);
        mockWorkManager = mock(WorkManager.class);
    }

    private TestableRescheduleWork createWork() {
        return new TestableRescheduleWork(mockApplication, mock(WorkerParameters.class));
    }

    @Test
    public void testExactAlarmWhenPermitted() {
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);
        Instant timestamp = Instant.now().plus(10, ChronoUnit.MINUTES);

        try (MockedStatic<PreferenceManager> mockedPreferenceManager = mockStatic(PreferenceManager.class)) {
            mockedPreferenceManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            createWork().schedule(new RescheduleWork.ReminderNotificationData(timestamp, 1, "Medicine A"));
        }

        ArgumentCaptor<PendingIntent> cancelled = ArgumentCaptor.forClass(PendingIntent.class);
        verify(mockAlarmManager).cancel(cancelled.capture());
        assertNotNull(cancelled.getValue());
        verify(mockAlarmManager).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(timestamp.toEpochMilli()), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void testInexactAlarmWhenPermissionDenied() {
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(false);
        Instant timestamp = Instant.now().plus(10, ChronoUnit.MINUTES);

        try (MockedStatic<PreferenceManager> mockedPreferenceManager = mockStatic(PreferenceManager.class)) {
            mockedPreferenceManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            createWork().schedule(new RescheduleWork.ReminderNotificationData(timestamp, 1, "Medicine A"));
        }

        verify(mockAlarmManager).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(timestamp.toEpochMilli()), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void testInexactAlarmWhenPreferenceDisabled() {
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);
        when(mockSharedPreferences.getBoolean(eq(PreferencesNames.EXACT_REMINDERS), anyBoolean())).thenReturn(false);
        Instant timestamp = Instant.now().plus(10, ChronoUnit.MINUTES);

        try (MockedStatic<PreferenceManager> mockedPreferenceManager = mockStatic(PreferenceManager.class)) {
            mockedPreferenceManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            createWork().schedule(new RescheduleWork.ReminderNotificationData(timestamp, 1, "Medicine A"));
        }

        verify(mockAlarmManager).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(timestamp.toEpochMilli()), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void testDueReminderIsEnqueuedImmediately() {
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);
        Instant timestamp = Instant.now().minus(1, ChronoUnit.MINUTES);

        try (MockedStatic<PreferenceManager> mockedPreferenceManager = mockStatic(PreferenceManager.class);
             MockedStatic<WorkManagerAccess> mockedWorkManagerAccess = mockStatic(WorkManagerAccess.class)) {
            mockedPreferenceManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            mockedWorkManagerAccess.when(() -> WorkManagerAccess.getWorkManager(mockApplication)).thenReturn(mockWorkManager);
            createWork().schedule(new RescheduleWork.ReminderNotificationData(timestamp, 1, "Medicine A"));
        }

        verify(mockWorkManager).enqueue(any(WorkRequest.class));
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }

    /**
     * Exposes the protected scheduling entry point without touching the database-backed doWork().
     */
    private static class TestableRescheduleWork extends RescheduleWork {
        TestableRescheduleWork(Application application, WorkerParameters workerParams) {
            super(application, workerParams);
        }

        void schedule(ReminderNotificationData data) {
            enqueueNotification(data);
        }
    }
}
