package com.ayushpant.habitquest.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

/*
 * DAOs ("data access objects") hold the SQL queries. Room checks every query against the
 * tables when the app is compiled, so a typo in SQL is a build error, not a crash.
 *
 * `suspend fun` = runs off the main thread (like an `async def`).
 * `Flow<T>` = a live query: the UI is told again every time the rows change.
 */

@Dao
interface HabitDao {
    @Query("SELECT * FROM habit WHERE active = 1 ORDER BY window_start, name")
    fun observeActive(): Flow<List<Habit>>

    @Query("SELECT * FROM habit WHERE active = 1")
    suspend fun getActive(): List<Habit>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun get(id: Long): Habit?

    @Insert
    suspend fun insert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM session WHERE day = :day ORDER BY start_time")
    fun observeForDay(day: LocalDate): Flow<List<Session>>

    @Query("SELECT * FROM session WHERE day = :day")
    suspend fun getForDay(day: LocalDate): List<Session>

    @Query("SELECT * FROM session WHERE habit_id = :habitId AND day = :day AND status = 'CONFIRMED'")
    suspend fun getConfirmed(habitId: Long, day: LocalDate): List<Session>

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun get(id: Long): Session?

    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE session SET points = :points, bonus_points = :bonusPoints WHERE id = :id")
    suspend fun setPoints(id: Long, points: Int, bonusPoints: Int)
}

@Dao
interface TaskDao {
    /** Open tasks (any due date) and tasks done on [day]. */
    @Query("SELECT * FROM task WHERE active = 1 AND (done_day IS NULL OR done_day = :day) ORDER BY due_date, id")
    fun observeForDay(day: LocalDate): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun get(id: Long): Task?

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)
}

@Dao
interface StreakDao {
    @Query("SELECT * FROM streak")
    fun observeAll(): Flow<List<Streak>>

    @Query("SELECT * FROM streak WHERE habit_id = :habitId")
    suspend fun get(habitId: Long): Streak?

    @Upsert
    suspend fun upsert(streak: Streak)
}

@Dao
interface DayBonusDao {
    @Query("SELECT * FROM day_bonus WHERE day = :day")
    fun observe(day: LocalDate): Flow<DayBonus?>

    @Upsert
    suspend fun upsert(bonus: DayBonus)

    @Query("DELETE FROM day_bonus WHERE day = :day")
    suspend fun delete(day: LocalDate)
}

@Dao
interface SignalLogDao {
    @Insert
    suspend fun insert(row: SignalLog)

    @Query("DELETE FROM signal_log WHERE time < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface AppStateDao {
    @Query("SELECT value FROM app_state WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(row: AppState)
}

/** Tells Room how to store java.time types. Dates and times are stored as ISO text, e.g. "2026-11-04". */
class Converters {
    @TypeConverter fun dateToText(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun textToDate(s: String?): LocalDate? = s?.let(LocalDate::parse)
    @TypeConverter fun timeToText(t: LocalTime?): String? = t?.toString()
    @TypeConverter fun textToTime(s: String?): LocalTime? = s?.let(LocalTime::parse)
}

@Database(
    version = 2,
    entities = [Habit::class, Session::class, Streak::class, DayBonus::class, SignalLog::class, AppState::class, Task::class],
    // Room compares app/schemas/…/1.json with 2.json and writes the migration itself (here: add the task table).
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habits(): HabitDao
    abstract fun sessions(): SessionDao
    abstract fun streaks(): StreakDao
    abstract fun dayBonus(): DayBonusDao
    abstract fun signals(): SignalLogDao
    abstract fun appState(): AppStateDao
    abstract fun tasks(): TaskDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "habit_quest.db")
                // No fallbackToDestructiveMigration(): a schema change must never wipe real data.
                .build()
    }
}
