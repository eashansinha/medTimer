package com.futsch1.medtimer;

import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_EVENT_ID;
import static com.futsch1.medtimer.ActivityCodes.EXTRA_REMINDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;
import android.os.HandlerThread;
import android.os.Looper;

import androidx.work.Configuration;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.futsch1.medtimer.database.MedicineRepository;
import com.futsch1.medtimer.database.ReminderEvent;
import com.futsch1.medtimer.reminders.ReminderWork;
import com.futsch1.medtimer.reminders.RescheduleWork;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * EE-30 / IMP-23: Autostart (BOOT_COMPLETED / MY_PACKAGE_REPLACED) requests a reschedule and restores
 * RAISED reminder events of the last day. 0 % covered on the baseline.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786")
public class AutostartUnitTest {
    private Application mockApplication;
    private WorkManager mockWorkManager;

    @BeforeEach
    public void setUp() {
        mockApplication = mock(Application.class);
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        when(mockApplication.getPackageName()).thenReturn("test");
        mockWorkManager = mock(WorkManager.class);
    }

    private void receive(String action, List<ReminderEvent> lastDayEvents) {
        try (MockedStatic<WorkManagerAccess> mockedAccess = mockStatic(WorkManagerAccess.class);
             MockedConstruction<MedicineRepository> ignored = mockConstruction(MedicineRepository.class,
                     (mock, context) -> when(mock.getLastDaysReminderEvents(anyInt())).thenReturn(lastDayEvents))) {
            mockedAccess.when(() -> WorkManagerAccess.getWorkManager(any())).thenReturn(mockWorkManager);
            new Autostart().onReceive(mockApplication, new Intent(action));
            idleRestoreReminderLoopers();
        }
    }

    /** restoreReminders posts to a HandlerThread; in Robolectric PAUSED looper mode we have to idle it. */
    private static void idleRestoreReminderLoopers() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread instanceof HandlerThread handlerThread && "RestoreReminders".equals(thread.getName())) {
                Looper looper = handlerThread.getLooper();
                if (looper != null) {
                    Shadows.shadowOf(looper).idle();
                }
            }
        }
    }

    private static ReminderEvent event(int reminderId, int reminderEventId, ReminderEvent.ReminderStatus status) {
        ReminderEvent e = new ReminderEvent();
        e.reminderId = reminderId;
        e.reminderEventId = reminderEventId;
        e.status = status;
        e.remindedTimestamp = Instant.now().getEpochSecond();
        return e;
    }

    @Test
    public void test_bootCompletedRequestsUniqueReschedule() {
        receive(Intent.ACTION_BOOT_COMPLETED, new ArrayList<>());
        ArgumentCaptor<OneTimeWorkRequest> captor = ArgumentCaptor.forClass(OneTimeWorkRequest.class);
        verify(mockWorkManager).enqueueUniqueWork(eq("reschedule"), eq(ExistingWorkPolicy.KEEP), captor.capture());
        assertEquals(RescheduleWork.class.getName(), captor.getValue().getWorkSpec().workerClassName);
    }

    @Test
    public void test_packageReplacedRequestsReschedule() {
        receive(Intent.ACTION_MY_PACKAGE_REPLACED, new ArrayList<>());
        verify(mockWorkManager).enqueueUniqueWork(eq("reschedule"), eq(ExistingWorkPolicy.KEEP), any(OneTimeWorkRequest.class));
    }

    @Test
    public void test_unrelatedActionIsIgnored() {
        try (MockedStatic<WorkManagerAccess> mockedAccess = mockStatic(WorkManagerAccess.class)) {
            mockedAccess.when(() -> WorkManagerAccess.getWorkManager(any())).thenReturn(mockWorkManager);
            new Autostart().onReceive(mockApplication, new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED));
            new Autostart().onReceive(mockApplication, new Intent());
        }
        verifyNoInteractions(mockWorkManager);
    }

    /**
     * restoreReminders enqueues from a HandlerThread, where Mockito static mocks are not visible, so the
     * restore path is verified against a real WorkManager whose executor never runs the work.
     */
    private List<WorkInfo> restoreWithRealWorkManager(List<ReminderEvent> events) throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(app, new Configuration.Builder().setExecutor(runnable -> { /* never run work */ }).build());
        }
        WorkManager workManager = WorkManager.getInstance(app);
        workManager.cancelAllWork().getResult().get();
        workManager.pruneWork().getResult().get();
        try (MockedConstruction<MedicineRepository> ignored = mockConstruction(MedicineRepository.class,
                (mock, context) -> when(mock.getLastDaysReminderEvents(anyInt())).thenReturn(events))) {
            new Autostart().onReceive(app, new Intent(Intent.ACTION_BOOT_COMPLETED));
            idleRestoreReminderLoopers();
        }
        return workManager.getWorkInfosByTag(ReminderWork.class.getName()).get().stream()
                .filter(info -> info.getState() == WorkInfo.State.ENQUEUED).collect(Collectors.toList());
    }

    @Test
    public void test_raisedEventIsRestoredAsReminderWork() throws Exception {
        List<ReminderEvent> events = new ArrayList<>();
        events.add(event(7, 70, ReminderEvent.ReminderStatus.RAISED));
        events.add(event(8, 80, ReminderEvent.ReminderStatus.TAKEN));
        List<WorkInfo> restored = restoreWithRealWorkManager(events);
        assertEquals(1, restored.size(), "exactly the RAISED event must be re-enqueued as ReminderWork");
        List<WorkInfo> reschedules = WorkManager.getInstance(RuntimeEnvironment.getApplication())
                .getWorkInfosForUniqueWork("reschedule").get();
        assertEquals(1, reschedules.size());
    }

    @Test
    public void test_nonRaisedEventsAreNotRestored() throws Exception {
        List<ReminderEvent> events = new ArrayList<>();
        events.add(event(8, 80, ReminderEvent.ReminderStatus.TAKEN));
        events.add(event(9, 90, ReminderEvent.ReminderStatus.SKIPPED));
        assertEquals(0, restoreWithRealWorkManager(events).size());
    }
}
