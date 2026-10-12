package com.futsch1.medtimer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Intent;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.futsch1.medtimer.database.MedicineRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.robolectric.annotation.Config;

import java.util.List;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Protects the boot / package-replaced autostart path (IMP-53): a reschedule is requested for the
 * two system actions the receiver is registered for; unrelated broadcasts are ignored. Re-posting of
 * already raised reminders happens on a HandlerThread and is verified on the device (see docs/upgrade).
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class AutostartUnitTest {

    private Application mockApplication;
    private WorkManager mockWorkManager;

    @BeforeEach
    public void setUp() {
        mockApplication = mock(Application.class);
        when(mockApplication.getApplicationContext()).thenReturn(mockApplication);
        mockWorkManager = mock(WorkManager.class);
    }

    @Test
    public void testBootCompletedRequestsReschedule() {
        try (MockedStatic<WorkManagerAccess> mockedWorkManagerAccess = mockStatic(WorkManagerAccess.class);
             MockedConstruction<MedicineRepository> ignored = mockConstruction(MedicineRepository.class,
                     (repository, context) -> when(repository.getLastDaysReminderEvents(anyInt())).thenReturn(List.of()))) {
            mockedWorkManagerAccess.when(() -> WorkManagerAccess.getWorkManager(mockApplication)).thenReturn(mockWorkManager);

            new Autostart().onReceive(mockApplication, new Intent(Intent.ACTION_BOOT_COMPLETED));

            verify(mockWorkManager).enqueueUniqueWork(eq("reschedule"), eq(ExistingWorkPolicy.KEEP), any(OneTimeWorkRequest.class));
        }
    }

    @Test
    public void testPackageReplacedRequestsReschedule() {
        try (MockedStatic<WorkManagerAccess> mockedWorkManagerAccess = mockStatic(WorkManagerAccess.class);
             MockedConstruction<MedicineRepository> ignored = mockConstruction(MedicineRepository.class,
                     (repository, context) -> when(repository.getLastDaysReminderEvents(anyInt())).thenReturn(List.of()))) {
            mockedWorkManagerAccess.when(() -> WorkManagerAccess.getWorkManager(mockApplication)).thenReturn(mockWorkManager);

            new Autostart().onReceive(mockApplication, new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));

            verify(mockWorkManager).enqueueUniqueWork(eq("reschedule"), eq(ExistingWorkPolicy.KEEP), any(OneTimeWorkRequest.class));
        }
    }

    @Test
    public void testUnrelatedActionIsIgnored() {
        try (MockedStatic<WorkManagerAccess> mockedWorkManagerAccess = mockStatic(WorkManagerAccess.class)) {
            mockedWorkManagerAccess.when(() -> WorkManagerAccess.getWorkManager(mockApplication)).thenReturn(mockWorkManager);

            new Autostart().onReceive(mockApplication, new Intent(Intent.ACTION_SCREEN_ON));
            new Autostart().onReceive(mockApplication, new Intent());

            verify(mockWorkManager, never()).enqueueUniqueWork(anyString(), any(ExistingWorkPolicy.class), any(OneTimeWorkRequest.class));
        }
    }
}
