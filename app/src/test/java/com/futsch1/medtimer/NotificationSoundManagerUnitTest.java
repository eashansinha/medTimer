package com.futsch1.medtimer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.NotificationManager;
import android.media.AudioManager;

import com.futsch1.medtimer.reminders.NotificationSoundManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

import tech.apter.junit.jupiter.robolectric.RobolectricExtension;

/**
 * Covers IMP-39 / IMP-50 (Android 17 background audio hardening): the "override do not disturb"
 * path unmutes the ring stream from background reminder work. On Android 17 that volume change
 * may silently fail, so the restore step (mute again + vibrate) must only run if the unmute
 * actually took effect.
 */
@ExtendWith(RobolectricExtension.class)
@Config(sdk = 34)
@SuppressWarnings("java:S5786") // Required for Robolectric extension
public class NotificationSoundManagerUnitTest {

    private AudioManager mockAudioManager;
    private NotificationManager mockNotificationManager;

    @BeforeEach
    public void setUp() throws ReflectiveOperationException {
        mockAudioManager = mock(AudioManager.class);
        mockNotificationManager = mock(NotificationManager.class);
        when(mockNotificationManager.getCurrentInterruptionFilter()).thenReturn(NotificationManager.INTERRUPTION_FILTER_ALL);
        when(mockNotificationManager.getNotificationPolicy()).thenReturn(new NotificationManager.Policy(0, 0, 0));
        resetCompanionState();
    }

    // The companion object keeps static state and a Handler bound to the main looper of the first
    // test; Robolectric recreates the main looper per test, so the state is reset and the scheduled
    // restore runnable is executed directly instead of waiting for the 5 s delay.
    private static Field companionField(String name) throws NoSuchFieldException {
        Field field = NotificationSoundManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void resetCompanionState() throws ReflectiveOperationException {
        companionField("pending").setBoolean(null, false);
        companionField("pendingWasMuted").setBoolean(null, false);
        companionField("scheduledRunnable").set(null, null);
    }

    private void runRestore() throws ReflectiveOperationException {
        NotificationSoundManager.Companion.restorePendingRingerMode(mockAudioManager);
        Runnable scheduledRestore = (Runnable) companionField("scheduledRunnable").get(null);
        assertNotNull(scheduledRestore, "restore runnable was not scheduled");
        scheduledRestore.run();
    }

    @Test
    public void unmuteSucceeded_restoreMutesAgainAndSetsVibrate() throws ReflectiveOperationException {
        // Muted before, unmuted after adjustStreamVolume
        when(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true, false);

        NotificationSoundManager.Companion.loadPendingRingerMode(mockAudioManager, mockNotificationManager);

        verify(mockAudioManager).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0);
        ArgumentCaptor<NotificationManager.Policy> policyCaptor = ArgumentCaptor.forClass(NotificationManager.Policy.class);
        verify(mockNotificationManager).setNotificationPolicy(policyCaptor.capture());
        assertEquals(NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS,
                policyCaptor.getValue().priorityCategories & NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS);

        runRestore();

        verify(mockAudioManager).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0);
        verify(mockAudioManager).setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
    }

    @Test
    public void unmuteSilentlyIgnored_restoreDoesNotTouchRingerMode() throws ReflectiveOperationException {
        // Stream stays muted although ADJUST_UNMUTE was requested (Android 17 background restriction)
        when(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true);

        NotificationSoundManager.Companion.loadPendingRingerMode(mockAudioManager, mockNotificationManager);

        verify(mockAudioManager).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0);

        runRestore();

        verify(mockAudioManager, never()).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0);
        verify(mockAudioManager, never()).setRingerMode(anyInt());
    }

    @Test
    public void notMuted_noVolumeChangeAtAll() throws ReflectiveOperationException {
        when(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(false);

        NotificationSoundManager.Companion.loadPendingRingerMode(mockAudioManager, mockNotificationManager);
        runRestore();

        verify(mockAudioManager, never()).adjustStreamVolume(anyInt(), anyInt(), anyInt());
        verify(mockAudioManager, never()).setRingerMode(anyInt());
        // DND policy is still extended to let the reminder through
        verify(mockNotificationManager).setNotificationPolicy(any(NotificationManager.Policy.class));
    }

    @Test
    public void interruptionFilterNotAll_streamIsLeftAlone() throws ReflectiveOperationException {
        when(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true);
        when(mockNotificationManager.getCurrentInterruptionFilter()).thenReturn(NotificationManager.INTERRUPTION_FILTER_PRIORITY);

        NotificationSoundManager.Companion.loadPendingRingerMode(mockAudioManager, mockNotificationManager);
        runRestore();

        verify(mockAudioManager, never()).adjustStreamVolume(anyInt(), anyInt(), anyInt());
        verify(mockAudioManager, never()).setRingerMode(anyInt());
    }
}
