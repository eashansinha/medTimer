package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMAINING_REPEATS;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REPEAT_TIME_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.futsch1.medtimer.database.MedicineRepository;
import com.futsch1.medtimer.database.ReminderEvent;
import com.futsch1.medtimer.reminders.RepeatReminderWork;

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
 * EE-30 / IMP-20: RepeatReminderWork limits — remaining repeats are counted down to exactly zero
 * and the repeat alarm is placed repeatTimeSeconds after now.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786")
public class RepeatReminderWorkLimitUnitTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
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

    private ReminderEvent runRepeat(int repeatTimeSeconds, int remainingRepeats, boolean eventExists) {
        ReminderEvent reminderEvent = new ReminderEvent();
        reminderEvent.reminderEventId = 12;
        reminderEvent.reminderId = 11;
        reminderEvent.remainingRepeats = remainingRepeats;
        WorkerParameters workerParams = mock(WorkerParameters.class);
        when(workerParams.getInputData()).thenReturn(new Data.Builder()
                .putInt(EXTRA_REMINDER_ID, 11)
                .putInt(EXTRA_REMINDER_EVENT_ID, 12)
                .putInt(EXTRA_REPEAT_TIME_SECONDS, repeatTimeSeconds)
                .putInt(EXTRA_REMAINING_REPEATS, remainingRepeats)
                .build());
        ArgumentCaptor<ReminderEvent> captor = ArgumentCaptor.forClass(ReminderEvent.class);
        long expectedMillis = NOW.plusSeconds(repeatTimeSeconds).toEpochMilli();
        try (MockedStatic<PreferenceManager> mockedPreferencesManager = mockStatic(PreferenceManager.class);
             MockedStatic<Instant> mockedInstant = mockStatic(Instant.class, CALLS_REAL_METHODS);
             MockedConstruction<MedicineRepository> repos = mockConstruction(MedicineRepository.class,
                     (mock, context) -> when(mock.getReminderEvent(12)).thenReturn(eventExists ? reminderEvent : null))) {
            mockedPreferencesManager.when(() -> PreferenceManager.getDefaultSharedPreferences(mockApplication)).thenReturn(mockSharedPreferences);
            mockedInstant.when(Instant::now).thenReturn(NOW);
            ListenableWorker.Result result = new RepeatReminderWork(mockApplication, workerParams).doWork();
            assertInstanceOf(ListenableWorker.Result.Success.class, result);
            verify(mockAlarmManager, times(1)).setAndAllowWhileIdle(eq(AlarmManager.RTC_WAKEUP), eq(expectedMillis), any(PendingIntent.class));
            if (eventExists) {
                verify(repos.constructed().get(0), times(1)).updateReminderEvent(captor.capture());
                return captor.getValue();
            }
            verify(repos.constructed().get(0), never()).updateReminderEvent(any());
            return null;
        }
    }

    @Test
    public void test_lastRepeatCountsDownToZero() {
        ReminderEvent updated = runRepeat(600, 1, true);
        assertEquals(0, updated.remainingRepeats);
    }

    @Test
    public void test_repeatsAreDecrementedByOne() {
        assertEquals(2, runRepeat(60, 3, true).remainingRepeats);
    }

    @Test
    public void test_fiveRepeatsBecomeFour() {
        assertEquals(4, runRepeat(60, 5, true).remainingRepeats);
    }

    @Test
    public void test_unlimitedRepeatsStayNegative() {
        // -1 is the "repeat until acknowledged" marker in ReminderWork (remainingRepeats != 0)
        assertEquals(-2, runRepeat(60, -1, true).remainingRepeats);
    }

    @Test
    public void test_missingEventStillSchedulesRepeatWithoutUpdate() {
        runRepeat(120, 2, false);
    }
}
