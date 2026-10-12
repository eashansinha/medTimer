package com.futsch1.medtimer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.app.AlarmManager;
import android.app.Application;
import android.content.Intent;

import androidx.work.WorkManager;

import com.futsch1.medtimer.database.MedicineRepository;
import com.futsch1.medtimer.reminders.ReminderProcessor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Pins which system broadcasts make {@link Autostart} reschedule the next reminder and which additionally
 * restore raised notifications (boot / app update), so that the manifest filter and the receiver stay in sync.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class AutostartUnitTest {

    private Application mockApplication;

    @BeforeEach
    public void setUp() {
        mockApplication = mock(Application.class);
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
    }

    @Test
    public void testBootCompletedReschedulesAndRestores() {
        assertReceive(Intent.ACTION_BOOT_COMPLETED, 1, true);
    }

    @Test
    public void testPackageReplacedReschedulesAndRestores() {
        assertReceive(Intent.ACTION_MY_PACKAGE_REPLACED, 1, true);
    }

    @Test
    public void testTimeSetReschedulesOnly() {
        assertReceive(Intent.ACTION_TIME_CHANGED, 1, false);
    }

    @Test
    public void testTimezoneChangedReschedulesOnly() {
        assertReceive(Intent.ACTION_TIMEZONE_CHANGED, 1, false);
    }

    @Test
    public void testExactAlarmPermissionGrantedReschedulesOnly() {
        assertReceive(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED, 1, false);
    }

    @Test
    public void testUnknownActionIsIgnored() {
        assertReceive("com.futsch1.medtimer.SOME_OTHER_ACTION", 0, false);
        assertReceive(null, 0, false);
    }

    @Test
    public void testActionClassification() {
        assertTrue(Autostart.isRestoreAction(Intent.ACTION_BOOT_COMPLETED));
        assertTrue(Autostart.isRestoreAction(Intent.ACTION_MY_PACKAGE_REPLACED));
        assertFalse(Autostart.isRestoreAction(Intent.ACTION_TIME_CHANGED));
        assertTrue(Autostart.isRescheduleOnlyAction(Intent.ACTION_TIME_CHANGED));
        assertTrue(Autostart.isRescheduleOnlyAction(Intent.ACTION_TIMEZONE_CHANGED));
        assertTrue(Autostart.isRescheduleOnlyAction(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED));
        assertFalse(Autostart.isRescheduleOnlyAction(Intent.ACTION_BOOT_COMPLETED));
        assertFalse(Autostart.isRescheduleOnlyAction(Intent.ACTION_PACKAGE_REPLACED));
    }

    private void assertReceive(String action, int expectedReschedules, boolean expectRestore) {
        Intent intent = new Intent(action);
        try (MockedStatic<ReminderProcessor> mockedProcessor = mockStatic(ReminderProcessor.class);
             MockedStatic<WorkManagerAccess> mockedWorkManagerAccess = mockStatic(WorkManagerAccess.class);
             MockedConstruction<MedicineRepository> repositories = mockConstruction(MedicineRepository.class,
                     (mock, context) -> when(mock.getLastDaysReminderEvents(anyInt())).thenReturn(new ArrayList<>()))) {
            mockedWorkManagerAccess.when(() -> WorkManagerAccess.getWorkManager(mockApplication)).thenReturn(mock(WorkManager.class));

            new Autostart().onReceive(mockApplication, intent);

            mockedProcessor.verify(() -> ReminderProcessor.requestReschedule(mockApplication), times(expectedReschedules));
            assertEquals(expectRestore ? 1 : 0, repositories.constructed().size());
        }
    }
}
