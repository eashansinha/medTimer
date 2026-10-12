package com.futsch1.medtimer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.futsch1.medtimer.database.FullMedicine;
import com.futsch1.medtimer.database.MedicineRepository;
import com.futsch1.medtimer.preferences.PreferencesNames;
import com.futsch1.medtimer.reminders.RescheduleWork;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * EE-30 / IMP-20: RescheduleWork exact-alarm policy (SCHEDULE_EXACT_ALARM, API 31+). The exact
 * branch was not covered by any JVM test on the baseline.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786")
public class RescheduleWorkExactAlarmUnitTest {
    private Application mockApplication;
    private AlarmManager mockAlarmManager;
    private SharedPreferences mockSharedPreferences;

    @BeforeEach
    public void setUp() {
        mockApplication = mock(Application.class);
        when(mockApplication.getPackageName()).thenReturn("test");
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        mockSharedPreferences = mock(SharedPreferences.class);
        when(mockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(false);
        mockAlarmManager = mock(AlarmManager.class);
        when(mockApplication.getSystemService(AlarmManager.class)).thenReturn(mockAlarmManager);
    }

    private ListenableWorker.Result runReschedule(List<FullMedicine> medicines) {
        WorkerParameters workerParams = mock(WorkerParameters.class);
        try (MockedStatic<PreferenceManager> mockedPreferencesManager = mockStatic(PreferenceManager.class);
             MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS);
             MockedConstruction<MedicineRepository> ignored = mockConstruction(MedicineRepository.class, (mock, context) -> {
                 when(mock.getMedicines()).thenReturn(medicines);
                 when(mock.getLastDaysReminderEvents(anyInt())).thenReturn(new ArrayList<>());
             })) {
            mockedPreferencesManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            // "now" far in the past so that today's reminder is still in the future -> alarm is armed
            mockedInstant.when(Instant::now).thenReturn(Instant.EPOCH);
            return new RescheduleWork(mockApplication, workerParams).doWork();
        }
    }

    private static List<FullMedicine> oneMedicine() {
        FullMedicine medicine = TestHelper.buildFullMedicine(1, "Medicine A");
        medicine.reminders.add(TestHelper.buildReminder(1, 1, "500 mg", 12 * 60, 1));
        List<FullMedicine> medicines = new ArrayList<>();
        medicines.add(medicine);
        return medicines;
    }

    @Test
    public void test_exactAlarmWhenPreferenceOnAndPermissionGranted() {
        when(mockSharedPreferences.getBoolean(eq(PreferencesNames.EXACT_REMINDERS), anyBoolean())).thenReturn(true);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);
        ListenableWorker.Result result = runReschedule(oneMedicine());
        assertInstanceOf(ListenableWorker.Result.Success.class, result);
        verify(mockAlarmManager, times(1)).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void test_inexactAlarmWhenPermissionRevoked() {
        when(mockSharedPreferences.getBoolean(eq(PreferencesNames.EXACT_REMINDERS), anyBoolean())).thenReturn(true);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(false);
        runReschedule(oneMedicine());
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void test_inexactAlarmWhenPreferenceOffWithoutQueryingPermission() {
        when(mockSharedPreferences.getBoolean(eq(PreferencesNames.EXACT_REMINDERS), anyBoolean())).thenReturn(false);
        when(mockAlarmManager.canScheduleExactAlarms()).thenReturn(true);
        runReschedule(oneMedicine());
        verify(mockAlarmManager, never()).canScheduleExactAlarms();
        verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), anyLong(), any(PendingIntent.class));
    }

    @Test
    public void test_noMedicinesCancelsPendingAlarmAndArmsNothing() {
        ListenableWorker.Result result = runReschedule(new ArrayList<>());
        assertInstanceOf(ListenableWorker.Result.Success.class, result);
        verify(mockAlarmManager, times(1)).cancel(any(PendingIntent.class));
        verify(mockAlarmManager, never()).setExactAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
        verify(mockAlarmManager, never()).setAndAllowWhileIdle(anyInt(), anyLong(), any(PendingIntent.class));
    }
}
