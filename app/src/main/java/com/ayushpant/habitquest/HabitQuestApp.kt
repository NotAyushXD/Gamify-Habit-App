package com.ayushpant.habitquest

import android.app.Application
import com.ayushpant.habitquest.data.AppDatabase
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.work.DayCloseWorker

/**
 * Runs once when Android starts the app process (before any screen or worker).
 * Holds the single database and repository that the whole app shares.
 */
class HabitQuestApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { HabitRepository(database) }

    override fun onCreate() {
        super.onCreate()
        DayCloseWorker.schedule(this)
    }
}
