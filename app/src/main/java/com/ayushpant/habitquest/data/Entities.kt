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
