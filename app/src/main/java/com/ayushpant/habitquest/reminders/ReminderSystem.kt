package com.ayushpant.habitquest.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ayushpant.habitquest.HabitQuestApp
import com.ayushpant.habitquest.R
import com.ayushpant.habitquest.data.LOG_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/*
 * How a reminder reaches the screen:
 * 1. ReminderScheduler sets ONE exact alarm, for the next planned popup.
 * 2. At that time Android wakes ReminderReceiver, even if the app is not running.
 * 3. The receiver asks ReminderRepository which popups are due, then shows them with
 *    PopupService (drawn over other apps), or as notifications if that is not allowed.
 * 4. It sets the alarm for the next popup. Every change in the app also sets it again.
 */

object ReminderScheduler {
    private const val ACTION_ALARM = "com.ayushpant.habitquest.REMINDER_ALARM"

    /** Plans again and sets the alarm for the next popup. */
    suspend fun reschedule(context: Context) {
        val app = context.applicationContext as HabitQuestApp
        setAlarm(context, app.reminders.nextAlarmMillis())
    }

    fun setAlarm(context: Context, atMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_ALARM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (canUseExactAlarms(context)) {
            // Rings on time, also in Doze (battery saving when the phone is idle).
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    fun canUseExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Spec 6.2: no popup during a phone call or a video/voice call in an app. */
    fun inCall(context: Context): Boolean {
        val mode = context.getSystemService(AudioManager::class.java).mode
        return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION || mode == AudioManager.MODE_RINGTONE
    }
}

/** Runs [block] off the main thread and keeps the receiver alive until it ends (max ~10 s). */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Reminder receiver failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** The alarm rang: show the popups that are due, then set the next alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = runAsync {
        val app = context.applicationContext as HabitQuestApp
        if (ReminderScheduler.inCall(context)) {
            Log.i(LOG_TAG, "In a call: reminders wait")
            ReminderScheduler.setAlarm(context, app.reminders.retryMillis())
            return@runAsync
        }
        try {
            val ids = app.reminders.takeDue()
            if (ids.isNotEmpty()) PopupPresenter.show(context, ids)
        } finally {
            // Always set the next alarm, even if showing failed: otherwise all later reminders are lost.
            ReminderScheduler.reschedule(context)
        }
    }
}

/** After a restart, an app update or a clock change, alarms are gone or wrong: set them again. */
class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        runAsync { ReminderScheduler.reschedule(context) }
    }
}

/** Shows popups over other apps, or as notifications if "Display over other apps" is off (spec 11). */
object PopupPresenter {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_SERVICE = "popup_service"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Habit reminders when popups over other apps are not allowed"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Popup on screen", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while a reminder popup waits for your answer"
            }
        )
    }

    suspend fun show(context: Context, logIds: List<Long>) {
        if (ReminderScheduler.canDrawOverlays(context)) {
            try {
                PopupService.start(context, logIds.toLongArray())
                return
            } catch (e: Exception) {
                // e.g. Android refused to start the popup service from the background.
                Log.w(LOG_TAG, "Popup service could not start, using notifications", e)
            }
        }
        val app = context.applicationContext as HabitQuestApp
        for (id in logIds) {
            val model = app.reminders.popupFor(id) ?: continue
            notify(context, id, model)
        }
    }

    fun showTest(context: Context) {
        if (ReminderScheduler.canDrawOverlays(context)) {
            PopupService.start(context, longArrayOf(PopupModel.Test.logId))
        } else {
            notify(context, PopupModel.Test.logId, PopupModel.Test)
        }
    }

    private fun notify(context: Context, logId: Long, model: PopupModel) {
        if (!ReminderScheduler.canNotify(context)) {
            Log.w(LOG_TAG, "Popup $logId not shown: no overlay and no notification permission")
            return
        }
        val (title, text) = when (model) {
            is PopupModel.Reminder -> popupTitle(model) to "Tap to start, snooze or skip."
            is PopupModel.Checklist -> "11 PM checklist" to "Tick what you did today."
            PopupModel.Test -> "Test reminder" to "Notifications work. Allow \"Display over other apps\" for popups."
        }
        val open = PendingIntent.getActivity(
            context, logId.toInt(),
            Intent(context, PopupActivity::class.java).putExtra(PopupActivity.EXTRA_LOG_ID, logId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(logId.toInt(), notification)
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Notification permission missing", e)
        }
    }
}
