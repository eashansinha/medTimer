package com.futsch1.medtimer.reminders

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.preference.PreferenceManager
import com.futsch1.medtimer.LogTags
import com.futsch1.medtimer.preferences.PreferencesNames

/**
 * Makes a reminder notification audible when the device is in vibrate mode (ring stream muted,
 * no DND active) by unmuting the ring stream while the notification is posted and muting it again
 * a few seconds later.
 *
 * Platform constraints this class has to live with:
 * - AudioManager / NotificationManager calls that would toggle DND throw SecurityException when
 *   notification policy access is not granted (Android 7+). Access is checked up front, but it can
 *   be revoked between the check and the delayed restore, so every call is also guarded.
 * - Android 17 background audio hardening silently ignores volume and ringer mode calls made
 *   from the background without a while-in-use foreground service. The outcome of the unmute is
 *   therefore verified via isStreamMute(): if it had no effect nothing is restored later, so the
 *   device is never muted by this class. A restore that is ignored is logged and not retried; a
 *   retry would run from the background as well and could mute a ringer mode the user chose later.
 * - The pending state lives in memory only. If the process is killed between the unmute and the
 *   delayed restore (~5 s), the ring stream stays unmuted until the next reminder cycle runs; the
 *   device is never left muted or in a ringer mode it was not in before, because muting only
 *   happens after this class unmuted the stream itself and the previous ringer mode is restored.
 *   Closing that window needs a persisted state plus a caller-scheduled restore (ReminderWork).
 */
class NotificationSoundManager(val context: Context) {
    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        if (notificationManager.isNotificationPolicyAccessGranted() && PreferenceManager.getDefaultSharedPreferences(
                context
            )
                .getBoolean(PreferencesNames.OVERRIDE_DND, false)
        ) {
            loadPendingRingerMode(audioManager, notificationManager)
        }
    }

    fun restore() {
        restorePendingRingerMode(audioManager)
    }

    companion object {
        private const val RESTORE_DELAY_MS = 5000L
        private var pending = false
        private var pendingWasMuted = false
        private var pendingRingerMode = AudioManager.RINGER_MODE_VIBRATE
        private var scheduledRunnable: Runnable? = null
        private var handler: Handler? = null

        @Synchronized
        fun loadPendingRingerMode(
            audioManager: AudioManager,
            notificationManager: NotificationManager
        ) {
            if (pending) {
                return
            }
            pending = true
            pendingWasMuted = false
            try {
                if (audioManager.isStreamMute(AudioManager.STREAM_RING) && notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    pendingRingerMode = ringerModeToRestore(audioManager.ringerMode)
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_RING,
                        AudioManager.ADJUST_UNMUTE,
                        0
                    )
                    pendingWasMuted = !audioManager.isStreamMute(AudioManager.STREAM_RING)
                    if (!pendingWasMuted) {
                        Log.w(LogTags.REMINDER, "Unmuting ring stream had no effect, nothing to restore")
                    }
                }
                allowRemindersInNotificationPolicy(notificationManager)
            } catch (e: SecurityException) {
                Log.w(LogTags.REMINDER, "Notification policy access denied while preparing reminder sound", e)
            }
        }

        private fun ringerModeToRestore(currentRingerMode: Int): Int =
            if (currentRingerMode == AudioManager.RINGER_MODE_SILENT) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_VIBRATE

        private fun allowRemindersInNotificationPolicy(notificationManager: NotificationManager) {
            val policy = notificationManager.notificationPolicy
            notificationManager.notificationPolicy = NotificationManager.Policy(
                policy.priorityCategories or NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS,
                policy.priorityCallSenders,
                policy.priorityMessageSenders,
                0
            )
        }

        @Synchronized
        fun restorePendingRingerMode(
            audioManager: AudioManager
        ) {
            if (!pending) {
                return
            }
            val restoreHandler = handler ?: Handler(Looper.getMainLooper()).also { handler = it }
            val runnable = scheduledRunnable ?: Runnable { completeRestore(audioManager) }.also { scheduledRunnable = it }
            restoreHandler.removeCallbacks(runnable)
            restoreHandler.postDelayed(runnable, RESTORE_DELAY_MS)
        }

        @Synchronized
        private fun completeRestore(audioManager: AudioManager) {
            scheduledRunnable = null
            if (pendingWasMuted) {
                try {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_RING,
                        AudioManager.ADJUST_MUTE,
                        0
                    )
                    if (Build.VERSION.SDK_INT > 28) {
                        audioManager.ringerMode = pendingRingerMode
                    }
                } catch (e: SecurityException) {
                    Log.w(LogTags.REMINDER, "Notification policy access denied while restoring ringer mode", e)
                }
                if (!audioManager.isStreamMute(AudioManager.STREAM_RING)) {
                    Log.w(LogTags.REMINDER, "Ring stream is still unmuted after restore, leaving it as is")
                }
            }
            pending = false
            pendingWasMuted = false
            handler = null
        }

        @VisibleForTesting
        @Synchronized
        internal fun resetForTesting() {
            scheduledRunnable?.let { handler?.removeCallbacks(it) }
            scheduledRunnable = null
            handler = null
            pending = false
            pendingWasMuted = false
        }
    }
}
