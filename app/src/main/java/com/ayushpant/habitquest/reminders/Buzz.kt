package com.ayushpant.habitquest.reminders

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.ayushpant.habitquest.data.LOG_TAG

/**
 * A short vibration when a reminder popup appears, so it is noticed with the phone in a pocket.
 * Nothing when the phone is on silent. It uses the "notification" vibration type, so the phone's
 * own vibration settings (intensity, off) apply.
 */
object Buzz {
    /** Two soft pulses: wait 0 ms, buzz 220 ms, pause 160 ms, buzz 220 ms. */
    val SOFT = longArrayOf(0, 220, 160, 220)

    fun reminder(context: Context) {
        try {
            val audio = context.getSystemService(AudioManager::class.java)
            if (audio.ringerMode == AudioManager.RINGER_MODE_SILENT) return
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            if (!vibrator.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(SOFT, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Vibration failed", e) // never let a buzz stop a reminder
        }
    }
}
