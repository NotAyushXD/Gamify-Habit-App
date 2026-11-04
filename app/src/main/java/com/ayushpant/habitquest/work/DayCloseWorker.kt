package com.ayushpant.habitquest.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ayushpant.habitquest.HabitQuestApp
import com.ayushpant.habitquest.data.LOG_TAG
import com.ayushpant.habitquest.scoring.ScoringConfig
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Day close (spec 6.3): runs daily at about 00:05 and closes the previous day.
 *
 * Android decides the exact run time, so it can be late. That is fine: closing is
 * idempotent and catches up on every unclosed day, and the app also runs it on start.
 */
class DayCloseWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as HabitQuestApp).repository
        return try {
            val closed = repo.closePendingDays()
            repo.recordTaskRun(TASK_NAME)
            Log.i(LOG_TAG, "Day close: closed $closed day(s)")
            Result.success()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Day close failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        const val TASK_NAME = "day_close"

        /** Safe to call on every app start: KEEP leaves an existing schedule alone. */
        fun schedule(context: Context, cfg: ScoringConfig = ScoringConfig.DEFAULT) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(cfg.dayCloseTime)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<DayCloseWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TASK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
