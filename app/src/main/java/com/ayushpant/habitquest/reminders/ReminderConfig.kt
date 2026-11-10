package com.ayushpant.habitquest.reminders

import java.time.LocalTime

/**
 * Fixed reminder timings (spec 6). The values the user can change (quiet hours, checklist on/off,
 * reminder limit) live in the settings table and the Settings screen instead.
 */
data class ReminderConfig(
    /** The "last chance" reminder comes this many minutes before the window ends. */
    val lastChanceMinutes: Long = 15,
    val snoozeMinutes: Long = 15,
    val checklistTime: LocalTime = LocalTime.of(23, 0),
    /** Seconds before "I promise" can be tapped on the "Can't today" screen. */
    val promiseWaitSeconds: Int = 5,
    /** Open tasks listed on the first reminder popup of the day. */
    val tasksOnFirstPopup: Int = 3,
    /** During a phone call, check again after this many seconds. */
    val callRetrySeconds: Long = 60,
    /** Early-reminder choices in the editor, in minutes before a habit's window starts. */
    val habitEarlyChoices: List<Int> = listOf(15, 30, 60, 120, 180),
    /** Early-reminder choices in minutes before a task's due time (1440 = 1 day). */
    val taskEarlyChoices: List<Int> = listOf(15, 60, 180, 1440),
) {
    companion object {
        val DEFAULT = ReminderConfig()
    }
}
