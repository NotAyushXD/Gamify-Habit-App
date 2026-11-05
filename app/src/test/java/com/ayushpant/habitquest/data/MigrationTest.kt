package com.ayushpant.habitquest.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/**
 * The developer's real data lives on the phone, so every schema change must keep it.
 * This test builds a database exactly as an older app version made it (from the schema
 * JSON that Room exported), puts data in it, then opens it with the current app code.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    /** Creates the database of [version] from app/schemas, like that app version did on the phone. */
    private fun createDatabaseAtVersion(version: Int): SQLiteDatabase {
        context.deleteDatabase(dbName)
        val schema = JSONObject(File("schemas/com.ayushpant.habitquest.data.AppDatabase/$version.json").readText())
            .getJSONObject("database")
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        return db
    }

    @Test
    fun `version 1 to 2 keeps habits, sessions and streaks and adds tasks`() = runBlocking {
        createDatabaseAtVersion(1).apply {
            execSQL(
                "INSERT INTO habit (id, name, type, window_start, window_end, base_points, creature_line, days_mask, created_date, active) " +
                    "VALUES (1, 'Meditate', 'MANUAL', '06:30', '07:30', 10, 'CALM', 127, '2026-11-04', 1)"
            )
            execSQL(
                "INSERT INTO session (habit_id, day, start_time, end_time, status, source, evidence_found, points, bonus_points) " +
                    "VALUES (1, '2026-11-04', 0, 0, 'CONFIRMED', 'MANUAL', 0, 10, 0)"
            )
            execSQL("INSERT INTO streak (habit_id, current_days, best_days, freezes_used, comeback_pending) VALUES (1, 3, 5, 0, 0)")
            close()
        }

        // Opening with the current code runs the migration. Room checks the result matches the new schema.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build()
        try {
            assertEquals("Meditate", db.habits().get(1)!!.name)
            assertEquals(10, db.sessions().getConfirmed(1, LocalDate.of(2026, 11, 4)).single().points)
            assertEquals(3, db.streaks().get(1)!!.currentDays)

            val taskId = db.tasks().insert(
                Task(name = "Renew passport", dueDate = LocalDate.of(2026, 11, 7), basePoints = 10, createdDate = LocalDate.of(2026, 11, 5))
            )
            assertTrue(taskId > 0)
        } finally {
            db.close()
        }
    }

    @Test
    fun `version 2 to 3 keeps tasks and adds settings, reminder log and skips`() = runBlocking {
        createDatabaseAtVersion(2).apply {
            execSQL(
                "INSERT INTO task (id, name, due_date, base_points, created_date, points, active) " +
                    "VALUES (1, 'Renew passport', '2026-11-07', 10, '2026-11-05', 0, 1)"
            )
            close()
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build()
        try {
            assertEquals("Renew passport", db.tasks().get(1)!!.name)
            db.settings().put(Settings())
            assertEquals(3, db.settings().get()!!.maxReminders)
        } finally {
            db.close()
        }
    }
}
