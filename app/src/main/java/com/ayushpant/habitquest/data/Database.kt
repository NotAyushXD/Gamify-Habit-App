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

    /** Deleted habits too, so History can still show their names. */
    @Query("SELECT * FROM habit")
    fun observeAll(): Flow<List<Habit>>

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

    @Query("SELECT COALESCE(SUM(points), 0) FROM session WHERE status = 'CONFIRMED'")
    fun observeTotalPoints(): Flow<Int>

    @Query("SELECT * FROM session WHERE day BETWEEN :from AND :to AND status = 'CONFIRMED' ORDER BY day DESC, start_time")
    fun observeConfirmedBetween(from: LocalDate, to: LocalDate): Flow<List<Session>>

    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE session SET points = :points, bonus_points = :bonusPoints WHERE id = :id")
    suspend fun setPoints(id: Long, points: Int, bonusPoints: Int)

    @Query("SELECT id FROM session WHERE status = 'CONFIRMED'")
    suspend fun getConfirmedIds(): List<Long>

    /** The last day before [day] with a confirmed session, for the companion's mood. */
    @Query("SELECT MAX(day) FROM session WHERE status = 'CONFIRMED' AND day < :day")
    fun observeLastConfirmedDayBefore(day: LocalDate): Flow<LocalDate?>

    /** Confirmed points per habit and day, for paying coins (one source per habit per day). */
    @Query("SELECT habit_id AS habitId, day, SUM(points) AS points FROM session WHERE status = 'CONFIRMED' GROUP BY habit_id, day")
    suspend fun pointsByHabitDay(): List<HabitDayPoints>
}

@Dao
interface TaskDao {
    /** Open tasks (any due date) and tasks done on [day]. */
    @Query("SELECT * FROM task WHERE active = 1 AND (done_day IS NULL OR done_day = :day) ORDER BY due_date, id")
    fun observeForDay(day: LocalDate): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun get(id: Long): Task?

    /** Tasks done in the range, and tasks due in the range (for Summary). Deleted ones excluded. */
    @Query("SELECT * FROM task WHERE active = 1 AND ((done_day BETWEEN :from AND :to) OR (due_date BETWEEN :from AND :to)) ORDER BY done_day DESC")
    fun observeDoneOrDueBetween(from: LocalDate, to: LocalDate): Flow<List<Task>>

    /** Points of every task ever done, deleted ones too (their points stay). */
    @Query("SELECT COALESCE(SUM(points), 0) FROM task WHERE done_day IS NOT NULL")
    fun observeTotalPoints(): Flow<Int>

    /** All open tasks, soonest due first. */
    @Query("SELECT * FROM task WHERE active = 1 AND done_day IS NULL ORDER BY due_date, id")
    suspend fun getOpen(): List<Task>

    @Query("SELECT * FROM task WHERE done_day IS NOT NULL")
    suspend fun getDone(): List<Task>

    @Query("SELECT * FROM task WHERE active = 1 AND done_day IS NULL ORDER BY due_date, id")
    fun observeOpen(): Flow<List<Task>>

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): Settings?

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<Settings?>

    @Upsert
    suspend fun put(settings: Settings)
}

@Dao
interface ReminderLogDao {
    @Query("SELECT * FROM reminder_log WHERE day = :day")
    suspend fun getForDay(day: LocalDate): List<ReminderLog>

    @Query("SELECT * FROM reminder_log WHERE id = :id")
    suspend fun get(id: Long): ReminderLog?

    @Query("SELECT * FROM reminder_log ORDER BY due_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ReminderLog>>

    @Insert
    suspend fun insert(row: ReminderLog): Long

    @Update
    suspend fun update(row: ReminderLog)
}

@Dao
interface HabitSkipDao {
    @Query("SELECT * FROM habit_skip WHERE day = :day")
    suspend fun getForDay(day: LocalDate): List<HabitSkip>

    @Query("SELECT * FROM habit_skip WHERE day BETWEEN :from AND :to")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<HabitSkip>>

    @Upsert
    suspend fun put(skip: HabitSkip)

    @Query("DELETE FROM habit_skip WHERE habit_id = :habitId AND day = :day")
    suspend fun delete(habitId: Long, day: LocalDate)
}

@Dao
interface PromiseDao {
    @Query("SELECT * FROM promise WHERE habit_id = :habitId AND status = 'OPEN' ORDER BY due_at LIMIT 1")
    suspend fun getOpen(habitId: Long): Promise?

    @Query("SELECT * FROM promise WHERE due_day = :day")
    suspend fun getDueOn(day: LocalDate): List<Promise>

    @Query("SELECT * FROM promise WHERE due_day = :day")
    fun observeDueOn(day: LocalDate): Flow<List<Promise>>

    @Query("SELECT * FROM promise WHERE due_day BETWEEN :from AND :to")
    fun observeDueBetween(from: LocalDate, to: LocalDate): Flow<List<Promise>>

    @Insert
    suspend fun insert(promise: Promise): Long

    @Update
    suspend fun update(promise: Promise)
}

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zone WHERE active = 1 ORDER BY name")
    fun observeActive(): Flow<List<Zone>>

    @Query("SELECT * FROM zone WHERE active = 1")
    suspend fun getActive(): List<Zone>

    @Query("SELECT * FROM zone WHERE id = :id")
    suspend fun get(id: Long): Zone?

    @Insert
    suspend fun insert(zone: Zone): Long

    @Update
    suspend fun update(zone: Zone)
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

    @Query("SELECT COALESCE(SUM(points), 0) FROM day_bonus")
    fun observeTotalPoints(): Flow<Int>

    @Query("SELECT * FROM day_bonus")
    suspend fun getAll(): List<DayBonus>

    @Query("SELECT * FROM day_bonus WHERE day = :day")
    suspend fun get(day: LocalDate): DayBonus?

    @Query("SELECT * FROM day_bonus WHERE day BETWEEN :from AND :to")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<DayBonus>>
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

    @Query("SELECT value FROM app_state WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(row: AppState)

    @Query("DELETE FROM app_state WHERE `key` = :key")
    suspend fun delete(key: String)
}

/** One row of [SessionDao.pointsByHabitDay]. */
data class HabitDayPoints(val habitId: Long, val day: LocalDate, val points: Int)

@Dao
interface CoinDao {
    @Query("SELECT COALESCE(SUM(amount), 0) FROM coin_ledger")
    fun observeBalance(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM coin_ledger")
    suspend fun balance(): Int

    @Insert
    suspend fun insert(row: CoinLedger): Long

    @Query("SELECT * FROM coin_ledger ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<CoinLedger>>

    @Query("SELECT credited FROM coin_credit WHERE source_key = :key")
    suspend fun credited(key: String): Int?

    @Upsert
    suspend fun putCredit(row: CoinCredit)

    /** The newest ledger row id (0 when empty). Marks where an egg starts, see `HabitRepository.takeBack`. */
    @Query("SELECT COALESCE(MAX(id), 0) FROM coin_ledger")
    suspend fun lastId(): Long

    /** Net coins of one source in rows after [afterId]. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM coin_ledger WHERE source_key = :key AND id > :afterId")
    suspend fun netAfter(key: String, afterId: Long): Int

    /** The Mind/Body/Soul mask a source was last paid with. */
    @Query("SELECT mbs_mask FROM coin_ledger WHERE source_key = :key AND amount > 0 ORDER BY id DESC LIMIT 1")
    suspend fun maskOf(key: String): Int?
}

@Dao
interface EggDao {
    @Query("SELECT * FROM egg WHERE theme_id = :themeId AND hatched_day IS NULL ORDER BY id DESC LIMIT 1")
    fun observeOpen(themeId: String): Flow<Egg?>

    @Query("SELECT * FROM egg WHERE theme_id = :themeId AND hatched_day IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getOpen(themeId: String): Egg?

    @Query("SELECT COUNT(*) FROM egg WHERE theme_id = :themeId AND hatched_day IS NOT NULL")
    suspend fun countHatched(themeId: String): Int

    @Query("SELECT COUNT(*) FROM egg WHERE theme_id = :themeId AND hatched_day IS NOT NULL")
    fun observeCountHatched(themeId: String): Flow<Int>

    @Insert
    suspend fun insert(egg: Egg): Long

    @Query("UPDATE egg SET creature_id = :to WHERE creature_id = :from")
    suspend fun renameCreature(from: String, to: String)

    @Update
    suspend fun update(egg: Egg)
}

/** One row of [CreatureDao.observeBonds]. */
data class BondCount(val ownedId: Long, val bond: Int)

@Dao
interface CreatureDao {
    @Query("SELECT * FROM creature_owned WHERE theme_id = :themeId ORDER BY id")
    fun observeOwned(themeId: String): Flow<List<OwnedCreature>>

    @Query("SELECT * FROM creature_owned WHERE theme_id = :themeId ORDER BY id")
    suspend fun getOwned(themeId: String): List<OwnedCreature>

    @Query("SELECT * FROM creature_owned WHERE id = :id")
    suspend fun get(id: Long): OwnedCreature?

    @Insert
    suspend fun insert(row: OwnedCreature): Long

    @Query("UPDATE creature_owned SET companion = (id = :id) WHERE theme_id = :themeId")
    suspend fun setCompanion(themeId: String, id: Long)

    @Query("SELECT owned_id AS ownedId, COUNT(*) AS bond FROM bond_event GROUP BY owned_id")
    fun observeBonds(): Flow<List<BondCount>>

    @Query("SELECT session_id FROM bond_event")
    suspend fun bondedSessionIds(): List<Long>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertBond(row: BondEvent)

    @Query("DELETE FROM bond_event WHERE session_id = :sessionId")
    suspend fun deleteBond(sessionId: Long)

    @Query("UPDATE creature_owned SET creature_id = :to WHERE creature_id = :from")
    suspend fun renameCreature(from: String, to: String)
}

@Dao
interface RewardDao {
    @Query("SELECT * FROM personal_reward WHERE active = 1 ORDER BY cost, id")
    fun observeActive(): Flow<List<PersonalReward>>

    @Query("SELECT * FROM personal_reward WHERE active = 1 ORDER BY cost, id")
    suspend fun getActive(): List<PersonalReward>

    @Query("SELECT * FROM personal_reward WHERE id = :id")
    suspend fun get(id: Long): PersonalReward?

    @Insert
    suspend fun insert(reward: PersonalReward): Long

    @Update
    suspend fun update(reward: PersonalReward)

    @Insert
    suspend fun insertRedemption(row: RewardRedemption): Long

    /** Newest first, for the "Redeemed" list. */
    @Query("SELECT * FROM reward_redemption ORDER BY time DESC, id DESC")
    fun observeRedemptions(): Flow<List<RewardRedemption>>

    @Query("SELECT * FROM reward_redemption ORDER BY time DESC, id DESC")
    suspend fun getRedemptions(): List<RewardRedemption>
}

/** Tells Room how to store java.time types. Dates and times are stored as ISO text, e.g. "2026-11-04". */
class Converters {
    @TypeConverter fun dateToText(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun textToDate(s: String?): LocalDate? = s?.let(LocalDate::parse)
    @TypeConverter fun timeToText(t: LocalTime?): String? = t?.toString()
    @TypeConverter fun textToTime(s: String?): LocalTime? = s?.let(LocalTime::parse)
}

@Database(
    version = 9,
    entities = [
        Habit::class, Session::class, Streak::class, DayBonus::class, SignalLog::class, AppState::class, Task::class,
        Settings::class, ReminderLog::class, HabitSkip::class, Promise::class, Zone::class,
        CoinLedger::class, CoinCredit::class, Egg::class, OwnedCreature::class, BondEvent::class,
        PersonalReward::class, RewardRedemption::class,
    ],
    // Room compares the schema files in app/schemas and writes each migration itself (new tables only).
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8), AutoMigration(from = 8, to = 9)],
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
    abstract fun settings(): SettingsDao
    abstract fun reminders(): ReminderLogDao
    abstract fun skips(): HabitSkipDao
    abstract fun promises(): PromiseDao
    abstract fun zones(): ZoneDao
    abstract fun coins(): CoinDao
    abstract fun eggs(): EggDao
    abstract fun creatures(): CreatureDao
    abstract fun rewards(): RewardDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "habit_quest.db")
                // No fallbackToDestructiveMigration(): a schema change must never wipe real data.
                .build()
    }
}
