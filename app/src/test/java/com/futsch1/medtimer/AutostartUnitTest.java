package com.futsch1.medtimer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import android.os.HandlerThread;

import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

import com.futsch1.medtimer.database.MedicineRepository;
import com.futsch1.medtimer.database.ReminderEvent;
import com.futsch1.medtimer.reminders.ReminderProcessor;
import com.futsch1.medtimer.reminders.ReminderWork;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Boot / package-replaced receiver (IMP-14, IMP-15): after a reboot the next reminder must be
 * rescheduled and reminders that were raised but not yet handled must be re-posted.
 * <p>
 * The receiver re-posts on its own HandlerThread, where Mockito static mocks do not apply, so a
 * real (test) WorkManager is used and the enqueued work is inspected through it.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class AutostartUnitTest {

    private Application application;

    @BeforeEach
    public void setUp() {
        application = RuntimeEnvironment.getApplication();
        WorkManagerTestInitHelper.initializeTestWorkManager(application);
    }

    private static ReminderEvent reminderEvent(int reminderId, int reminderEventId, ReminderEvent.ReminderStatus status) {
        ReminderEvent reminderEvent = new ReminderEvent();
        reminderEvent.reminderId = reminderId;
        reminderEvent.reminderEventId = reminderEventId;
        reminderEvent.status = status;
        reminderEvent.remindedTimestamp = 1_700_000_000L;
        return reminderEvent;
    }

    // Robolectric only runs background loopers when they are idled explicitly. Every onReceive
    // starts a new "RestoreReminders" HandlerThread (never quit), so the newest one is used.
    private static void runRestoreThread() {
        Optional<Thread> restoreThread = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> "RestoreReminders".equals(thread.getName()))
                .max(Comparator.comparingLong(Thread::getId));
        assertTrue(restoreThread.isPresent(), "RestoreReminders handler thread not started");
        ((ShadowLooper) Shadow.extract(((HandlerThread) restoreThread.get()).getLooper())).idle();
    }

    private List<WorkInfo> enqueuedReminderWork() throws ExecutionException, InterruptedException {
        return WorkManager.getInstance(application).getWorkInfosByTag(ReminderWork.class.getName()).get();
    }

    @Test
    public void bootCompleted_reschedulesAndRepostsOnlyRaisedReminders() throws ExecutionException, InterruptedException {
        List<ReminderEvent> lastEvents = List.of(
                reminderEvent(1, 10, ReminderEvent.ReminderStatus.TAKEN),
                reminderEvent(2, 20, ReminderEvent.ReminderStatus.RAISED),
                reminderEvent(3, 30, ReminderEvent.ReminderStatus.SKIPPED));

        try (MockedStatic<ReminderProcessor> mockedReminderProcessor = mockStatic(ReminderProcessor.class);
             MockedConstruction<MedicineRepository> ignored = mockConstruction(MedicineRepository.class,
                     (mock, context) -> when(mock.getLastDaysReminderEvents(1)).thenReturn(lastEvents))) {

            new Autostart().onReceive(application, new Intent(Intent.ACTION_BOOT_COMPLETED));
            runRestoreThread();

            mockedReminderProcessor.verify(() -> ReminderProcessor.requestReschedule(application));
        }

        // Exactly one ReminderWork: the RAISED event; TAKEN and SKIPPED events are not re-posted
        assertEquals(1, enqueuedReminderWork().size());
    }

    @Test
    public void packageReplaced_reschedulesWithoutRepostsWhenNothingIsRaised() throws ExecutionException, InterruptedException {
        try (MockedStatic<ReminderProcessor> mockedReminderProcessor = mockStatic(ReminderProcessor.class);
             MockedConstruction<MedicineRepository> ignored = mockConstruction(MedicineRepository.class,
                     (mock, context) -> when(mock.getLastDaysReminderEvents(1)).thenReturn(List.of(
                             reminderEvent(1, 10, ReminderEvent.ReminderStatus.TAKEN))))) {

            new Autostart().onReceive(application, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));
            runRestoreThread();

            mockedReminderProcessor.verify(() -> ReminderProcessor.requestReschedule(application));
        }

        assertEquals(0, enqueuedReminderWork().size());
    }

    @Test
    public void unrelatedAction_isIgnored() throws ExecutionException, InterruptedException {
        try (MockedStatic<ReminderProcessor> mockedReminderProcessor = mockStatic(ReminderProcessor.class)) {
            new Autostart().onReceive(application, new Intent(Intent.ACTION_TIME_CHANGED));
            mockedReminderProcessor.verifyNoInteractions();
        }
        assertEquals(0, enqueuedReminderWork().size());
    }
}
