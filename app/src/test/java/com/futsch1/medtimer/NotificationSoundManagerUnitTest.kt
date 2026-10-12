package com.futsch1.medtimer

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import androidx.preference.PreferenceManager
import com.futsch1.medtimer.preferences.PreferencesNames
import com.futsch1.medtimer.reminders.NotificationSoundManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [35])
@Suppress("java:S5786") // Required for Robolectric extension
class NotificationSoundManagerUnitTest {

    private lateinit var mockContext: Context
    private lateinit var mockNotificationManager: NotificationManager
    private lateinit var mockAudioManager: AudioManager
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockedPreferenceManager: MockedStatic<PreferenceManager>

    @BeforeEach
    fun setUp() {
        NotificationSoundManager.resetForTesting()

        mockContext = mock(Context::class.java)
        mockNotificationManager = mock(NotificationManager::class.java)
        mockAudioManager = mock(AudioManager::class.java)
        `when`(mockContext.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(mockNotificationManager)
        `when`(mockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager)

        mockSharedPreferences = mock(SharedPreferences::class.java)
        `when`(mockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean(PreferencesNames.OVERRIDE_DND, false)).thenReturn(true)
        mockedPreferenceManager = mockStatic(PreferenceManager::class.java)
        mockedPreferenceManager.`when`<SharedPreferences> { PreferenceManager.getDefaultSharedPreferences(mockContext) }
            .thenReturn(mockSharedPreferences)

        // Default device state: policy access granted, vibrate mode (ring stream muted), no DND
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(true)
        `when`(mockNotificationManager.currentInterruptionFilter).thenReturn(NotificationManager.INTERRUPTION_FILTER_ALL)
        `when`(mockNotificationManager.notificationPolicy).thenReturn(
            NotificationManager.Policy(NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS, 0, 0, NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS)
        )
        `when`(mockAudioManager.ringerMode).thenReturn(AudioManager.RINGER_MODE_VIBRATE)
    }

    @AfterEach
    fun tearDown() {
        mockedPreferenceManager.close()
        NotificationSoundManager.resetForTesting()
    }

    private fun runReminderCycle(): NotificationSoundManager {
        val soundManager = NotificationSoundManager(mockContext)
        soundManager.restore()
        return soundManager
    }

    private fun verifyUnmuted(wantedNumberOfInvocations: Int) {
        verify(mockAudioManager, times(wantedNumberOfInvocations)).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
    }

    private fun verifyMuted(wantedNumberOfInvocations: Int) {
        verify(mockAudioManager, times(wantedNumberOfInvocations)).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
        verify(mockAudioManager, times(wantedNumberOfInvocations)).ringerMode = AudioManager.RINGER_MODE_VIBRATE
    }

    @Test
    fun test_noPolicyAccess_nothingChanged() {
        `when`(mockNotificationManager.isNotificationPolicyAccessGranted).thenReturn(false)
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verifyNoInteractions(mockAudioManager)
        verify(mockNotificationManager, never()).notificationPolicy = any()
        verify(mockNotificationManager, never()).setInterruptionFilter(anyInt())
    }

    @Test
    fun test_overrideDndDisabled_nothingChanged() {
        `when`(mockSharedPreferences.getBoolean(PreferencesNames.OVERRIDE_DND, false)).thenReturn(false)
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verifyNoInteractions(mockAudioManager)
        verify(mockNotificationManager, never()).notificationPolicy = any()
    }

    @Test
    fun test_vibrateMode_unmutesAndRestoresAfterDelay() {
        // muted before, unmuted after our unmute, muted again after our restore
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true, false, true)

        runReminderCycle()

        verifyUnmuted(1)
        val policyCaptor = ArgumentCaptor.forClass(NotificationManager.Policy::class.java)
        verify(mockNotificationManager).notificationPolicy = policyCaptor.capture()
        assertNotEquals(0, policyCaptor.value.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS)
        assertNotEquals(0, policyCaptor.value.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS)
        assertEquals(0, policyCaptor.value.suppressedVisualEffects)
        verify(mockNotificationManager, never()).setInterruptionFilter(anyInt())
        // Restore is delayed so that the notification sound can play
        verifyMuted(0)

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verifyMuted(1)
        verify(mockAudioManager, never()).ringerMode = AudioManager.RINGER_MODE_SILENT
        verify(mockAudioManager, never()).ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    @Test
    fun test_silentMode_restoresSilentInsteadOfVibrate() {
        `when`(mockAudioManager.ringerMode).thenReturn(AudioManager.RINGER_MODE_SILENT)
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true, false, true)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verifyUnmuted(1)
        verify(mockAudioManager).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
        verify(mockAudioManager).ringerMode = AudioManager.RINGER_MODE_SILENT
        verify(mockAudioManager, never()).ringerMode = AudioManager.RINGER_MODE_VIBRATE
    }

    @Test
    fun test_normalModeWithMutedRingStream_fallsBackToVibrate() {
        `when`(mockAudioManager.ringerMode).thenReturn(AudioManager.RINGER_MODE_NORMAL)
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true, false, true)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verifyUnmuted(1)
        verifyMuted(1)
        verify(mockAudioManager, never()).ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    @Test
    fun test_dndActive_ringerNotTouched() {
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true)
        `when`(mockNotificationManager.currentInterruptionFilter).thenReturn(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify(mockAudioManager, never()).adjustStreamVolume(anyInt(), anyInt(), anyInt())
        verify(mockAudioManager, never()).ringerMode = anyInt()
        verify(mockNotificationManager).notificationPolicy = any()
    }

    @Test
    fun test_ringStreamNotMuted_nothingToRestore() {
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(false)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify(mockAudioManager, never()).adjustStreamVolume(anyInt(), anyInt(), anyInt())
        verify(mockAudioManager, never()).ringerMode = anyInt()
    }

    @Test
    fun test_securityExceptionWhileReadingPolicy_isSwallowedAndRingerStillRestored() {
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true, false, true)
        `when`(mockNotificationManager.notificationPolicy).thenThrow(SecurityException("Notification policy access denied"))

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verifyUnmuted(1)
        verifyMuted(1)
        verify(mockNotificationManager, never()).notificationPolicy = any()
    }

    @Test
    fun test_securityExceptionWhileUnmuting_isSwallowed() {
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true)
        doThrow(SecurityException("Not allowed to change Do Not Disturb state"))
            .`when`(mockAudioManager).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verifyUnmuted(1)
        verifyMuted(0)
    }

    @Test
    fun test_securityExceptionWhileRestoring_isSwallowedAndRetriedOnNextReminder() {
        // muted, unmuted by us, still unmuted after failed restore, muted after successful retry
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true, false, false, true)
        doThrow(SecurityException("Not allowed to change Do Not Disturb state")).doNothing()
            .`when`(mockAudioManager).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        verifyUnmuted(1)
        verify(mockAudioManager, times(1)).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)

        // Next reminder: the ring stream is still unmuted from the first reminder, so it is not unmuted again
        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        verifyUnmuted(1)
        verify(mockAudioManager, times(2)).adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
        verify(mockAudioManager, times(1)).ringerMode = AudioManager.RINGER_MODE_VIBRATE
    }

    @Test
    fun test_restoreIgnoredByPlatform_isRetriedOnNextReminder() {
        // Android 17 background audio hardening: adjustStreamVolume() is silently ignored
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true, false, false, true)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        verifyMuted(1)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        verifyUnmuted(1)
        verifyMuted(2)
    }

    @Test
    fun test_unmuteIgnoredByPlatform_doesNotChangeRingerOnRestore() {
        // Android 17 background audio hardening: the unmute is silently ignored, the stream stays muted
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verifyUnmuted(1)
        verifyMuted(0)
    }

    @Test
    fun test_secondReminderWithinRestoreWindow_restoresOnce() {
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true, false, true)

        runReminderCycle()
        runReminderCycle()
        verifyMuted(0)

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verifyUnmuted(1)
        verifyMuted(1)
    }

    @Test
    fun test_previousCycleDoesNotMuteDeviceThatWasNotMuted() {
        // Cycle 1: vibrate mode -> unmute -> restore. Cycle 2: device is in normal mode -> must stay untouched.
        `when`(mockAudioManager.isStreamMute(AudioManager.STREAM_RING)).thenReturn(true, false, true, false, false)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        verifyMuted(1)

        runReminderCycle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verifyUnmuted(1)
        verifyMuted(1)
        assertTrue(ShadowLooper.shadowMainLooper().isIdle)
    }
}
