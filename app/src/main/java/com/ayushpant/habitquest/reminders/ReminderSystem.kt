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
import com.ayushpant.habitquest.detect.HabitListenerService
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
        // In quiet hours popups are dropped anyway, so there is nothing to wait for: no 60 s retries all night.
        val busy = !app.reminders.inQuietHoursNow() && (ReminderScheduler.inCall(context) || HabitListenerService.isNavigating())
        if (busy) {
            // Spec 6.2: no popup during a call. A quiet notification (short buzz, no sound) tells the user
            // now; the popup comes when the call ends.
            try {
                val ids = app.reminders.takeDue()
                if (ids.isNotEmpty()) {
                    Log.i(LOG_TAG, "In a call or navigating: ${ids.size} reminder(s) as a quiet notification")
                    app.reminders.addAfterCall(ids)
                    PopupPresenter.notifyDuringCall(context, ids)
                }
            } finally {
                if (app.reminders.hasAfterCall()) ReminderScheduler.setAlarm(context, app.reminders.retryMillis())
                else ReminderScheduler.reschedule(context)
            }
            return@runAsync
        }
        try {
            val waiting = app.reminders.takeAfterCall()
            for (id in waiting) NotificationManagerCompat.from(context).cancel(id.toInt())
            val ids = waiting + app.reminders.takeDue()
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
        runAsync {
            ReminderScheduler.reschedule(context)
            com.ayushpant.habitquest.detect.Geofences.sync(context) // geofences are lost after a restart
        }
    }
}

/** Shows popups over other apps, or as notifications if "Display over other apps" is off (spec 11). */
object PopupPresenter {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_SERVICE = "popup_service"
    const val CHANNEL_DURING_CALL = "reminders_call"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Habit reminders when popups over other apps are not allowed"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DURING_CALL, "Reminders during a call", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A quiet reminder while you are in a call or navigating; the popup comes after"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = Buzz.SOFT
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

    /** The zone check (spec 5.3): over other apps, or as a notification that opens it in the app. */
    suspend fun showZoneCheck(context: Context, habitIds: List<Long>, dwellAt: Long) {
        if (ReminderScheduler.canDrawOverlays(context)) {
            try {
                PopupService.startZoneCheck(context, habitIds.toLongArray(), dwellAt)
                return
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Popup service could not start, using a notification", e)
            }
        }
        val app = context.applicationContext as HabitQuestApp
        if (!ReminderScheduler.canNotify(context)) return
        for (id in habitIds) {
            val model = app.reminders.zoneCheckFor(id, dwellAt) ?: continue
            val open = PendingIntent.getActivity(
                context, (200_000 + id).toInt(),
                Intent(context, PopupActivity::class.java).putExtra(PopupActivity.EXTRA_ZONE_HABIT, id).putExtra(PopupActivity.EXTRA_ZONE_TIME, dwellAt),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("At ${model.zoneName}")
                .setContentText("Are you doing ${model.habitName} now?")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(context).notify((200_000 + id).toInt(), notification)
            } catch (e: SecurityException) {
                Log.w(LOG_TAG, "Notification permission missing", e)
            }
        }
    }

    /** The detected-session confirmation: over other apps, or as a notification. */
    suspend fun showConfirmation(context: Context, sessionId: Long) {
        if (ReminderScheduler.canDrawOverlays(context)) {
            try {
                PopupService.startConfirm(context, longArrayOf(sessionId))
                return
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Popup service could not start, using a notification", e)
            }
        }
        val app = context.applicationContext as HabitQuestApp
        val model = app.reminders.confirmationFor(sessionId) ?: return
        if (!ReminderScheduler.canNotify(context)) return
        val open = PendingIntent.getActivity(
            context, (100_000 + sessionId).toInt(),
            Intent(context, PopupActivity::class.java).putExtra(PopupActivity.EXTRA_SESSION_ID, sessionId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Did you do ${model.habitName}?")
            .setContentText("${model.minutes} min detected. Tap to confirm.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((100_000 + sessionId).toInt(), notification)
        } catch (e: SecurityException) {
            Log.w(LOG_TAG, "Notification permission missing", e)
        }
    }

    fun showTest(context: Context) {
        if (ReminderScheduler.canDrawOverlays(context)) {
            PopupService.start(context, longArrayOf(PopupModel.Test.logId))
        } else {
            notify(context, PopupModel.Test.logId, PopupModel.Test)
        }
    }

    /** During a call: the same reminders as quiet notifications; tapping one answers it now. */
    suspend fun notifyDuringCall(context: Context, logIds: List<Long>) {
        val app = context.applicationContext as HabitQuestApp
        for (id in logIds) {
            val model = app.reminders.popupFor(id) ?: continue
            notify(context, id, model, CHANNEL_DURING_CALL, after = "It pops up again after your call.")
        }
    }

    private fun notify(context: Context, logId: Long, model: PopupModel, channel: String = CHANNEL_REMINDERS, after: String? = null) {
        if (!ReminderScheduler.canNotify(context)) {
            Log.w(LOG_TAG, "Popup $logId not shown: no overlay and no notification permission")
            return
        }
        val (title, text) = when (model) {
            is PopupModel.Reminder -> popupTitle(model) to "Tap to start, snooze or skip."
            is PopupModel.Checklist -> "11 PM checklist" to "Tick what you did today."
            is PopupModel.TaskReminder -> taskPopupTitle(model) to "${taskDueLine(model)}. Tap to mark it done."
            is PopupModel.Confirm -> "Did you do ${model.habitName}?" to "${model.minutes} min detected. Tap to confirm."
            is PopupModel.ZoneCheck -> "At ${model.zoneName}" to "Are you doing ${model.habitName} now?"
            PopupModel.Test -> "Test reminder" to "Notifications work. Allow \"Display over other apps\" for popups."
        }
        val open = PendingIntent.getActivity(
            context, logId.toInt(),
            Intent(context, PopupActivity::class.java).putExtra(PopupActivity.EXTRA_LOG_ID, logId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(if (after != null) "$text $after" else text)
            .setSilent(channel == CHANNEL_DURING_CALL)
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
