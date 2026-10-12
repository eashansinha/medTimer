package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.EXTRA_NOTIFICATION_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMAINING_REPEATS;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REPEAT_TIME_SECONDS;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_SNOOZE_TIME;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
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

import com.futsch1.medtimer.database.MedicineRepository;
import com.futsch1.medtimer.preferences.PreferencesNames;
import com.futsch1.medtimer.reminders.RepeatReminderWork;
import com.futsch1.medtimer.reminders.SnoozeWork;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;

import java.time.Instant;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Pins the exact vs. inexact alarm decision of RescheduleWork (exercised through SnoozeWork and
 * RepeatReminderWork, which both schedule via RescheduleWork.enqueueNotification) when targeting API 37.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class ExactAlarmSchedulingUnitTest {

    private static final int REMINDER_ID = 11;
    private static final int REMINDER_EVENT_ID = 12;
    private static final int NOTIFICATION_ID = 14;
    private static final int SNOOZE_MINUTES = 15;

    private Application mockApplication;
    private AlarmManager mockAlarmManager;
    private SharedPreferences mockSharedPreferences;
    private WorkerParameters snoozeWorkerParams;

    @BeforeEach
    public void setUp() {
        mockApplication = mock(Application.class);
        when(mockApplication.getPackageName()).thenReturn("test");
        when(mockApplication.getSystemService(NotificationManager.class)).thenReturn(mock(NotificationManager.class));

        mockAlarmManager = mock(AlarmManager.class);
        when(mockApplication.getSystemService(AlarmManager.class)).thenReturn(mockAlarmManager);

        mockSharedPreferences = mock(SharedPreferences.class);
        when(mockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(false);

        snoozeWorkerParams = mock(WorkerParameters.class);
        when(snoozeWorkerParams.getInputData()).thenReturn(new Data.Builder()
                .putInt(EXTRA_REMINDER_ID, REMINDER_ID)
                .putInt(EXTRA_REMINDER_EVENT_ID, REMINDER_EVENT_ID)
                .putInt(EXTRA_SNOOZE_TIME, SNOOZE_MINUTES)
                .putInt(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
                .build());
    }

    private void setExactRemindersPreference(boolean value) {
        when(mockSharedPreferences.getBoolean(eq(PreferencesNames.EXACT_REMINDERS), anyBoolean())).thenReturn(value);
    }

    private ListenableWorker.Result runSnoozeWork() {
        try (MockedStatic<PreferenceManager> mockedPreferencesManager = mockStatic(PreferenceManager.class)) {
            mockedPreferencesManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            return new SnoozeWork(mockApplication, snoozeWorkerParams).doWork();
        }
    }

    private void assertTriggerIsSnoozeTime(long triggerAtMillis, Instant before) {
        long expectedMin = before.plusSeconds(SNOOZE_MINUTES * 60L).toEpochMilli();
        long expectedMax = Instant.now().plusSeconds(SNOOZE_MINUTES * 60L).toEpochMilli();
        assertTrue(triggerAtMillis >= expectedMin && triggerAtMillis <= expectedMax,
                "Alarm trigger " + triggerAtMillis + " not within [" + expectedMin + ", " + expectedMax + "]");
    }

    @Test
    public void testExactAlarmWhenPreferenceOnAndPermissionGranted() {
        setExactRemindersPreference(true);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);
        Instant before = Instant.now();

        ListenableWorker.Result result = runSnoozeWork();
        assertInstanceOf(ListenableWorker.Result.Success.class, result);

        ArgumentCaptor<Long> triggerCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<PendingIntent> intentCaptor = ArgumentCaptor.forClass(PendingIntent.class);
        verify(mockAlarmManager, times(1)).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), triggerCaptor.capture(), intentCaptor.capture());
        verify(mockAlarmManager, never()).setAndAllowWhileIdle(anyInt(), anyLong(), any());
        assertNotNull(intentCaptor.getValue());
        assertTriggerIsSnoozeTime(triggerCaptor.getValue(), before);
        // The alarm for this reminder event is cancelled before it is set again
        verify(mockAlarmManager, times(2)).cancel(intentCaptor.getValue());
    }

    @Test
    public void testInexactAlarmWhenPermissionNotGranted() {
        setExactRemindersPreference(true);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(false);
        Instant before = Instant.now();

        ListenableWorker.Result result = runSnoozeWork();
        assertInstanceOf(ListenableWorker.Result.Success.class, result);

        ArgumentCaptor<Long> triggerCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any());
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), triggerCaptor.capture(), any(PendingIntent.class));
        assertTriggerIsSnoozeTime(triggerCaptor.getValue(), before);
    }

    @Test
    public void testInexactAlarmWhenPreferenceOff() {
        setExactRemindersPreference(false);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);

        ListenableWorker.Result result = runSnoozeWork();
        assertInstanceOf(ListenableWorker.Result.Success.class, result);

        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any());
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
        // The permission is irrelevant if the user switched exact reminders off
        verify(mockAlarmManager, never()).canScheduleExactAlarms();
    }

    @Test
    public void testExactRemindersPreferenceDefaultsToOn() {
        // Unset preference: SharedPreferences returns the default supplied by the caller
        when(mockSharedPreferences.getBoolean(eq(PreferencesNames.EXACT_REMINDERS), anyBoolean())).thenAnswer(invocation -> invocation.getArgument(1));
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);

        ListenableWorker.Result result = runSnoozeWork();
        assertInstanceOf(ListenableWorker.Result.Success.class, result);

        verify(mockAlarmManager, times(1)).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setAndAllowWhileIdle(anyInt(), anyLong(), any());
    }

    @Test
    public void testFallsBackToInexactAlarmWhenExactAlarmThrowsSecurityException() {
        setExactRemindersPreference(true);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);
        // SCHEDULE_EXACT_ALARM revoked between the canScheduleExactAlarms() check and the call
        doThrow(new SecurityException("Caller needs to hold android.permission.SCHEDULE_EXACT_ALARM"))
                .when(mockAlarmManager).setExactAndAllowWhileIdle(anyInt(), anyLong(), any());
        Instant before = Instant.now();

        ListenableWorker.Result result = runSnoozeWork();
        assertInstanceOf(ListenableWorker.Result.Success.class, result);

        ArgumentCaptor<Long> exactTriggerCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<PendingIntent> exactIntentCaptor = ArgumentCaptor.forClass(PendingIntent.class);
        verify(mockAlarmManager, times(1)).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), exactTriggerCaptor.capture(), exactIntentCaptor.capture());
        // Same trigger time and same PendingIntent are re-used for the inexact fallback
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, exactTriggerCaptor.getValue(), exactIntentCaptor.getValue());
        assertTriggerIsSnoozeTime(exactTriggerCaptor.getValue(), before);
    }

    @Test
    public void testRepeatReminderFallsBackToInexactAlarmOnSecurityException() {
        setExactRemindersPreference(true);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);
        doThrow(new SecurityException("revoked"))
                .when(mockAlarmManager).setExactAndAllowWhileIdle(anyInt(), anyLong(), any());

        WorkerParameters repeatWorkerParams = mock(WorkerParameters.class);
        when(repeatWorkerParams.getInputData()).thenReturn(new Data.Builder()
                .putInt(EXTRA_REMINDER_ID, REMINDER_ID)
                .putInt(EXTRA_REMINDER_EVENT_ID, REMINDER_EVENT_ID)
                .putInt(EXTRA_REPEAT_TIME_SECONDS, 30)
                .putInt(EXTRA_REMAINING_REPEATS, 2)
                .build());

        ListenableWorker.Result result;
        try (MockedStatic<PreferenceManager> mockedPreferencesManager = mockStatic(PreferenceManager.class);
             MockedConstruction<MedicineRepository> ignored = mockConstruction(MedicineRepository.class)) {
            mockedPreferencesManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            result = new RepeatReminderWork(mockApplication, repeatWorkerParams).doWork();
        }
        assertInstanceOf(ListenableWorker.Result.Success.class, result);

        ArgumentCaptor<Long> triggerCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<PendingIntent> intentCaptor = ArgumentCaptor.forClass(PendingIntent.class);
        verify(mockAlarmManager, times(1)).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), triggerCaptor.capture(), intentCaptor.capture());
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerCaptor.getValue(), intentCaptor.getValue());
    }
}
