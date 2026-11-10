package com.ayushpant.habitquest.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

/*
 * Database tables (spec section 10). Each @Entity class is one table; each property is a column.
 * Step 1 has the tables it needs. Later steps add zone, wallet, reward, creature and settings
 * with a Room migration, so the data on the phone is kept.
 */

enum class HabitType { APP, ZONE, MANUAL }
enum class CreatureLine { CALM, STRONG, WISE }
enum class SessionStatus { SUGGESTED, CONFIRMED, REJECTED }
enum class SessionSource { LIVE, CATCH_UP, MANUAL, CHECKLIST }
enum class SignalKind { LAUNCH, MEDIA, USAGE, GEOFENCE, TASK }

@Entity(tableName = "habit")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: HabitType = HabitType.MANUAL,
    @ColumnInfo(name = "source_package") val sourcePackage: String? = null,
    @ColumnInfo(name = "zone_id") val zoneId: Long? = null,
    @ColumnInfo(name = "window_start") val windowStart: LocalTime,
    @ColumnInfo(name = "window_end") val windowEnd: LocalTime,
    @ColumnInfo(name = "base_points") val basePoints: Int,
    @ColumnInfo(name = "creature_line") val creatureLine: CreatureLine,
    /** Monday = bit 0 … Sunday = bit 6. Not in the spec; see CLAUDE.md. */
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    @ColumnInfo(name = "created_date") val createdDate: LocalDate,
    val active: Boolean = true,
    /** Mind = 1, Body = 2, Soul = 4 (see balance/Balance.kt). Added in database version 4. */
    @ColumnInfo(name = "mbs_mask", defaultValue = "0") val mbsMask: Int = 0,
    /** "After I [cue], I will [habit]": what the user does right before the habit. Added in version 6. */
    @ColumnInfo(name = "cue_line") val cueLine: String? = null,
    /** An early reminder this many minutes before the window starts; null = none. Added in version 8. */
    @ColumnInfo(name = "remind_before") val remindBefore: Int? = null,
)

@Entity(
    tableName = "session",
    foreignKeys = [ForeignKey(entity = Habit::class, parentColumns = ["id"], childColumns = ["habit_id"])],
    indices = [Index("habit_id"), Index("day")],
)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long,
    /** The local date the session counts for (the date of [startTime]). */
    val day: LocalDate,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "start_time") val startTime: Long,
    /** Epoch milliseconds. Null while the session is in progress. */
    @ColumnInfo(name = "end_time") val endTime: Long?,
    val status: SessionStatus,
    val source: SessionSource,
    @ColumnInfo(name = "evidence_found") val evidenceFound: Boolean,
    /** Total points, including [bonusPoints]. Only confirmed sessions have points. */
    val points: Int = 0,
    /** The comeback bonus part of [points]. */
    @ColumnInfo(name = "bonus_points") val bonusPoints: Int = 0,
)

@Entity(
    tableName = "streak",
    foreignKeys = [ForeignKey(entity = Habit::class, parentColumns = ["id"], childColumns = ["habit_id"])],
)
data class Streak(
    /** State after the last closed day. Today's session is not counted here until day close. */
    @PrimaryKey @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "current_days") val currentDays: Int = 0,
    @ColumnInfo(name = "best_days") val bestDays: Int = 0,
    @ColumnInfo(name = "last_done_date") val lastDoneDate: LocalDate? = null,
    /** Monday of the week that [freezesUsed] counts. */
    @ColumnInfo(name = "freeze_week") val freezeWeek: LocalDate? = null,
    @ColumnInfo(name = "freezes_used") val freezesUsed: Int = 0,
    @ColumnInfo(name = "comeback_pending") val comebackPending: Boolean = false,
)

/** The +10 bonus for a day on which every scheduled habit was done (spec 7.4). */
@Entity(tableName = "day_bonus")
data class DayBonus(
    @PrimaryKey val day: LocalDate,
    val points: Int,
)

@Entity(tableName = "signal_log", indices = [Index("time")])
data class SignalLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch milliseconds. */
    val time: Long,
    val kind: SignalKind,
    val payload: String,
)

/** Small key-value store for app bookkeeping, e.g. the last closed day. */
@Entity(tableName = "app_state")
data class AppState(
    @PrimaryKey val key: String,
    val value: String,
)

/** A one-off task with a due date. Not in the spec; see CLAUDE.md. Added in database version 2. */
@Entity(tableName = "task")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "due_date") val dueDate: LocalDate,
    @ColumnInfo(name = "base_points") val basePoints: Int,
    @ColumnInfo(name = "created_date") val createdDate: LocalDate,
    /** The local date the task was done. Null while it is open. */
    @ColumnInfo(name = "done_day") val doneDay: LocalDate? = null,
    /** Epoch milliseconds. Null while it is open. */
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
    val points: Int = 0,
    val active: Boolean = true,
    /** Mind = 1, Body = 2, Soul = 4. Added in database version 4. */
    @ColumnInfo(name = "mbs_mask", defaultValue = "0") val mbsMask: Int = 0,
    /**
     * Optional time on the due date: a reminder comes then. Points still count by the date only.
     * Added in database version 8, with [remindBefore].
     */
    @ColumnInfo(name = "due_time") val dueTime: LocalTime? = null,
    /** An early reminder this many minutes before the due time; null = none. Needs [dueTime]. */
    @ColumnInfo(name = "remind_before") val remindBefore: Int? = null,
)

// ---- Added in database version 3 (Step 2: popups) ---------------------------------------------

/** User settings (spec 10). One row, id = 1. Quiet hours can cross midnight, e.g. 23:30–06:00. */
@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "quiet_enabled") val quietEnabled: Boolean = true,
    @ColumnInfo(name = "quiet_start") val quietStart: LocalTime = LocalTime.of(23, 30),
    @ColumnInfo(name = "quiet_end") val quietEnd: LocalTime = LocalTime.of(6, 0),
    @ColumnInfo(name = "checklist_enabled") val checklistEnabled: Boolean = true,
    @ColumnInfo(name = "max_reminders") val maxReminders: Int = 3,
)

/**
 * EARLY: a heads-up before a habit's window (the user's choice). TASK_DUE and TASK_EARLY: at a task's
 * due time, and before it. Stored as text, so new kinds need no schema change.
 */
enum class ReminderKind { START, LAST_CHANCE, SNOOZE, CHECKLIST, PROMISE, EARLY, TASK_DUE, TASK_EARLY }

enum class ReminderResult {
    /** A snooze waiting for its time. */
    PENDING,
    SHOWN, STARTED, DONE, SNOOZED, SKIPPED, PROMISED, CLOSED,
    /** Not shown: quiet hours, the daily limit, or too late. */
    SUPPRESSED,
}

/** One reminder or checklist popup: when it was due and what the user did. */
@Entity(tableName = "reminder_log", indices = [Index("day")])
data class ReminderLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null for the checklist. */
    @ColumnInfo(name = "habit_id") val habitId: Long?,
    val day: LocalDate,
    val kind: ReminderKind,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "due_at") val dueAt: Long,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "shown_at") val shownAt: Long? = null,
    val result: ReminderResult,
    /** The task, for TASK_DUE and TASK_EARLY. Added in database version 8. */
    @ColumnInfo(name = "task_id") val taskId: Long? = null,
)

/** "Skip today" or "Can't today, I promise tomorrow". The day still counts as a miss. */
@Entity(tableName = "habit_skip", primaryKeys = ["habit_id", "day"])
data class HabitSkip(
    @ColumnInfo(name = "habit_id") val habitId: Long,
    val day: LocalDate,
    val promised: Boolean,
    /** Epoch milliseconds. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

// ---- Added in database version 4 -----------------------------------------------------------------

enum class OathMethod { TAP, TYPED, SPOKEN }
enum class PromiseStatus { OPEN, KEPT, BROKEN }

/**
 * "Can't today, I promise": the user names the day and time they will do the habit instead.
 * KEPT when the habit is done on [dueDay]; BROKEN when that day closes without it.
 */
@Entity(tableName = "promise", indices = [Index("habit_id"), Index("due_day")])
data class Promise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "made_on") val madeOn: LocalDate,
    @ColumnInfo(name = "due_day") val dueDay: LocalDate,
    /** Epoch milliseconds of the promised time. */
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "oath_method") val oathMethod: OathMethod,
    /** What the user typed or said, when an oath was needed. */
    @ColumnInfo(name = "oath_text") val oathText: String? = null,
    val status: PromiseStatus = PromiseStatus.OPEN,
)

// ---- Added in database version 5 (Step 4: zone habits) ----------------------------------------------

/** A place for zone habits, e.g. the gym (spec 1.1, 10). [radiusM] is 150 or more. */
@Entity(tableName = "zone")
data class Zone(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "radius_m") val radiusM: Int,
    val active: Boolean = true,
)

// ---- Added in database version 6 (Step 5a: coins) --------------------------------------------------

/**
 * Why coins moved. Earned reasons come with points; spent reasons come from the user.
 * CORRECTION: the user undid a tick, so the coins of that item went back (stored as text: no schema change).
 */
enum class CoinReason { HABIT, DAY_BONUS, TASK, FULL_COLLECTION, REWARD, CORRECTION }

/**
 * Every coin movement, one row each. The balance is the sum of [amount]. Coins come with points
 * (1 coin per point) and only leave when the user spends them, or undoes the tick that earned
 * them (developer decision, Nov 10, 2026). Never because of a detection fault or a rescore.
 */
@Entity(tableName = "coin_ledger", indices = [Index("time")])
data class CoinLedger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch milliseconds. */
    val time: Long,
    /** Positive = earned, negative = spent. */
    val amount: Int,
    val reason: CoinReason,
    /** What earned the coins, e.g. "habit:3:2026-11-09". Null for spending. */
    @ColumnInfo(name = "source_key") val sourceKey: String? = null,
    /** Mind / Body / Soul of the source (0 = none, e.g. the day bonus). Feeds the egg in 5b. */
    @ColumnInfo(name = "mbs_mask", defaultValue = "0") val mbsMask: Int = 0,
    /** A short label for the ledger screen, e.g. the habit name. */
    val note: String? = null,
)

/**
 * The most points a source (a habit-day, a day bonus, a task) has ever been paid as coins.
 * Points can go down after a History edit; coins never do. A later rise pays only the part above
 * this number, so the same points are never paid twice. A user undo lowers it by the coins taken back.
 */
@Entity(tableName = "coin_credit")
data class CoinCredit(
    @PrimaryKey @ColumnInfo(name = "source_key") val sourceKey: String,
    val credited: Int,
)

// ---- Added in database version 7 (Step 5b: egg, creatures, companion) ---------------------------

/**
 * One egg. Growth comes with coins (1 each), shared between Mind, Body and Soul by the source's
 * dimensions; [headStart] (and surplus from the egg before) has no dimension. It hatches when
 * the total reaches [needed]. One open egg per theme ([hatchedDay] null).
 */
@Entity(tableName = "egg", indices = [Index("theme_id")])
data class Egg(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "theme_id") val themeId: String,
    val needed: Int,
    @ColumnInfo(name = "head_start") val headStart: Int,
    @ColumnInfo(name = "growth_mind") val growthMind: Int = 0,
    @ColumnInfo(name = "growth_body") val growthBody: Int = 0,
    @ColumnInfo(name = "growth_soul") val growthSoul: Int = 0,
    @ColumnInfo(name = "started_day") val startedDay: LocalDate,
    @ColumnInfo(name = "hatched_day") val hatchedDay: LocalDate? = null,
    /** The creature that came out; null while open, or when the collection was already complete. */
    @ColumnInfo(name = "creature_id") val creatureId: String? = null,
) {
    val total: Int get() = headStart + growthMind + growthBody + growthSoul
}

/** A creature the user has. [creatureId] names a creature of the theme. One per theme is the companion. */
@Entity(tableName = "creature_owned", indices = [Index("theme_id")])
data class OwnedCreature(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "theme_id") val themeId: String,
    @ColumnInfo(name = "creature_id") val creatureId: String,
    @ColumnInfo(name = "hatch_day") val hatchDay: LocalDate,
    val companion: Boolean = false,
)

/**
 * One confirmed session counted as bond for the companion of that moment. Keyed by the session,
 * so a session is never counted twice. The row goes only when the user removes the session.
 */
@Entity(tableName = "bond_event", indices = [Index("owned_id")])
data class BondEvent(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "owned_id") val ownedId: Long,
)

// ---- Added in database version 9 (Step 5c: personal rewards) ------------------------------------

/** A real-life treat the user defines and buys with coins. Deleting only hides it ([active] = false). */
@Entity(tableName = "personal_reward")
data class PersonalReward(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Coins, between ScoringConfig.rewardMinCost and rewardMaxCost. */
    val cost: Int,
    val active: Boolean = true,
)

/**
 * One redeemed reward. The name and cost are copied at that moment, so a later edit or delete of
 * the reward never changes the history.
 */
@Entity(tableName = "reward_redemption", indices = [Index("time")])
data class RewardRedemption(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "reward_id") val rewardId: Long,
    val name: String,
    val cost: Int,
    /** Epoch milliseconds. */
    val time: Long,
    val day: LocalDate,
)
