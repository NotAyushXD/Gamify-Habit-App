package com.ayushpant.habitquest.detect

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ayushpant.habitquest.HabitQuestApp
import com.ayushpant.habitquest.data.LOG_TAG
import com.ayushpant.habitquest.data.SessionSource
import com.ayushpant.habitquest.data.SignalKind
import com.ayushpant.habitquest.reminders.PopupPresenter
import com.ayushpant.habitquest.reminders.ReminderScheduler
import com.ayushpant.habitquest.scoring.ScoringConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Catch-up (spec 5.2, 6.3): finds sessions of linked apps that the live audio signal did not see
 * (a silent app, or the listener was not running) in Android's app-usage record, and asks the user
 * to confirm them. Needs "Usage access". Runs every hour and when the app is opened.
 */
object CatchUp {
    private const val TASK_NAME = "catch_up"
    private val running = Mutex()

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CatchUpWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(TASK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Reads the usage record since the last run and suggests the sessions found. Returns how many. */
    suspend fun run(context: Context, cfg: ScoringConfig = ScoringConfig.DEFAULT): Int = running.withLock {
        if (!hasUsageAccess(context)) return 0
        val app = context.applicationContext as HabitQuestApp
        val repo = app.repository
        val packages = repo.watchedPackages()
        if (packages.isEmpty()) return 0

        val now = System.currentTimeMillis()
        val since = app.reminders.catchUpSince() ?: (now - cfg.catchUpLookbackHours * 3_600_000)
        val events = readEvents(context, since, now)
        // Spec 12.1: write each signal to signal_log before using it.
        repo.logSignal(SignalKind.USAGE, "catch-up ${events.size} events since ${java.time.Instant.ofEpochMilli(since)}")

        var suggested = 0
        val quiet = app.reminders.inQuietHoursNow()
        for (s in usageSessions(events, packages, now, cfg)) {
            val id = repo.suggestDetected(s.packageName, s.start, s.end, evidence = false, source = SessionSource.CATCH_UP) ?: continue
            suggested++
            // In quiet hours, during a call or navigation, the question waits on the Today tile.
            if (!quiet && !ReminderScheduler.inCall(context) && !HabitListenerService.isNavigating()) {
                PopupPresenter.showConfirmation(context, id)
            }
        }
        // Look again from a little before now, so a session still going on is read whole next time.
        app.reminders.setCatchUpSince(now - 2 * 3_600_000)
        repo.recordTaskRun(TASK_NAME)
        Log.i(LOG_TAG, "Catch-up: $suggested session(s) suggested")
        suggested
    }

    private fun readEvents(context: Context, from: Long, to: Long): List<UsageEvent> {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(from, to)
        val out = mutableListOf<UsageEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageKind.FOREGROUND
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageKind.BACKGROUND
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageKind.SCREEN_OFF
                else -> null
            } ?: continue
            out += UsageEvent(e.packageName, e.timeStamp, kind)
        }
        return out
    }
}

class CatchUpWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        CatchUp.run(applicationContext)
        Result.success()
    } catch (e: Exception) {
        Log.e(LOG_TAG, "Catch-up failed", e)
        Result.retry()
    }
}
