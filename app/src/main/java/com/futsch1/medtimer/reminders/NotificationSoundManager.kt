package com.futsch1.medtimer.reminders

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import com.futsch1.medtimer.preferences.PreferencesNames

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
        private const val TAG = "NotificationSoundManager"
        private var pending = false
        private var pendingWasMuted = false
        private var scheduledRunnable: Runnable? = null
        private val handler = Handler(Looper.getMainLooper())

        @Synchronized
        fun loadPendingRingerMode(
            audioManager: AudioManager,
            notificationManager: NotificationManager
        ) {
            if (!pending) {
                pending = true
                if (audioManager.isStreamMute(AudioManager.STREAM_RING) && notificationManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_RING,
                        AudioManager.ADJUST_UNMUTE,
                        0
                    )
                    // Android 17 background audio hardening may silently ignore volume changes
                    // from background work. Only schedule a restore (mute + vibrate) if the
                    // unmute actually took effect, otherwise the ringer mode would be changed
                    // without ever having been unmuted.
                    pendingWasMuted = !audioManager.isStreamMute(AudioManager.STREAM_RING)
                    if (!pendingWasMuted) {
                        Log.w(TAG, "Unmuting the ring stream had no effect (background audio restriction?)")
                    }
                }
                val policy = notificationManager.notificationPolicy
                notificationManager.notificationPolicy = NotificationManager.Policy(
                    policy.priorityCategories or NotificationManager.Policy.PRIORITY_CATEGORY_REMINDERS,
                    policy.priorityCallSenders,
                    policy.priorityMessageSenders,
                    0
                )
            }
        }

        @Synchronized
        fun restorePendingRingerMode(
            audioManager: AudioManager
        ) {
            if (pending && scheduledRunnable == null) {
                scheduledRunnable = createRunnable(audioManager)
            }
            if (scheduledRunnable != null) {
                handler.removeCallbacks(scheduledRunnable!!)
                handler.postDelayed(scheduledRunnable!!, 5000)
            }
        }

        private fun createRunnable(audioManager: AudioManager) = Runnable {
            if (pendingWasMuted) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_RING,
                    AudioManager.ADJUST_MUTE,
                    0
                )
                if (Build.VERSION.SDK_INT > 28) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                }
            }
            pending = false
            scheduledRunnable = null
        }
    }
}