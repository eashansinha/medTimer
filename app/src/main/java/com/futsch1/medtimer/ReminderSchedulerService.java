package com.futsch1.medtimer;

import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleService;

import com.futsch1.medtimer.database.FullMedicine;
import com.futsch1.medtimer.database.MedicineRepository;
import com.futsch1.medtimer.reminders.ReminderProcessor;

import java.util.List;

/**
 * Started service that re-runs the reminder scheduling whenever the medicine list changes.
 * <p>
 * It is intentionally <b>not</b> a foreground service: it is only started by {@code MainActivity.onResume()}
 * while the app process is in the foreground (see the importance check there), it never calls
 * {@code startForeground()} and all actual work is handed to WorkManager. Therefore the Android 14
 * foreground service type requirement and the Android 12+/14/15 restrictions on starting foreground
 * services from the background or from BOOT_COMPLETED do not apply to it.
 */
public class ReminderSchedulerService extends LifecycleService {

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        MedicineRepository medicineRepository = new MedicineRepository(this.getApplication());

        medicineRepository.getLiveMedicines().observe(this, this::updateMedicine);

        Log.i(LogTags.SCHEDULER, "Service created");
    }

    public void updateMedicine(List<FullMedicine> ignoredFullMedicine) {
        scheduleRequest();
    }

    private void scheduleRequest() {
        ReminderProcessor.requestReschedule(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(LogTags.SCHEDULER, "Service destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        scheduleRequest();
        return START_STICKY;
    }
}
