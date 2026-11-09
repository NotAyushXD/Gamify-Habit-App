package com.ayushpant.habitquest

import android.app.Application
import com.ayushpant.habitquest.data.AppDatabase
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.detect.CatchUp
import com.ayushpant.habitquest.detect.Geofences
import com.ayushpant.habitquest.reminders.PopupPresenter
import com.ayushpant.habitquest.reminders.ReminderRepository
import com.ayushpant.habitquest.reminders.ReminderScheduler
import com.ayushpant.habitquest.work.DayCloseWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs once when Android starts the app process (before any screen, worker or alarm).
 * Holds the single database and the repositories that the whole app shares.
 */
class HabitQuestApp : Application() {
    /** For work that must outlive a screen, e.g. setting the next reminder alarm. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { HabitRepository(database, onChange = ::rescheduleReminders) }
    val reminders by lazy { ReminderRepository(database, repository, onChange = ::rescheduleReminders) }

    override fun onCreate() {
        super.onCreate()
        PopupPresenter.createChannels(this)
        DayCloseWorker.schedule(this)
        CatchUp.schedule(this)
        rescheduleReminders()
    }

    /** Any change can move the next reminder, so the alarm is set again. */
    fun rescheduleReminders() {
        appScope.launch { ReminderScheduler.reschedule(this@HabitQuestApp) }
        appScope.launch { runCatching { Geofences.sync(this@HabitQuestApp) } } // zones may have changed too
    }
}
