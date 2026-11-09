package com.marvneo.companion.data.db

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Applies the shipped migration SQL to a real SQLite database on the JVM.
 *
 * This is the substitute for Room's instrumented `MigrationTestHelper`, which needs a device or
 * emulator. It exercises the exact same [MigrationSql] lists that [AppDatabase] executes.
 */
class MigrationSqlTest {

    private lateinit var db: Connection

    private data class Column(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val primaryKey: Boolean
    )

    @Before
    fun setUp() {
        Class.forName("org.sqlite.JDBC")
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        db.createStatement().use { it.execute("PRAGMA foreign_keys = ON") }
        createVersionOneSchema()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun exec(statements: List<String>) {
        db.createStatement().use { statement ->
            statements.forEach { statement.execute(it) }
        }
    }

    /** The v1 schema from commit 56a5308: four health tables, no transcript/BP/alarm tables. */
    private fun createVersionOneSchema() {
        exec(
            listOf(
                """
                CREATE TABLE `steps_record` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `steps` INTEGER NOT NULL,
                    `calories` INTEGER NOT NULL,
                    `distanceMeters` INTEGER NOT NULL
                )
                """.trimIndent(),
                """
                CREATE TABLE `heart_rate_record` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `bpm` INTEGER NOT NULL
                )
                """.trimIndent(),
                """
                CREATE TABLE `blood_oxygen_record` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `percentage` INTEGER NOT NULL
                )
                """.trimIndent(),
                """
                CREATE TABLE `sleep_record` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `startTime` INTEGER NOT NULL,
                    `endTime` INTEGER NOT NULL,
                    `totalMinutes` INTEGER NOT NULL,
                    `deepMinutes` INTEGER NOT NULL,
                    `lightMinutes` INTEGER NOT NULL,
                    `remMinutes` INTEGER NOT NULL
                )
                """.trimIndent(),
                "INSERT INTO heart_rate_record (timestamp, bpm) VALUES (1000, 72)"
            )
        )
    }

    private fun migrateAll() {
        exec(MigrationSql.V1_TO_V2)
        exec(MigrationSql.V2_TO_V3)
        exec(MigrationSql.V3_TO_V4)
    }

    private fun tableNames(): Set<String> {
        val names = mutableSetOf<String>()
        db.createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { rows ->
                while (rows.next()) names.add(rows.getString(1))
            }
        }
        return names
    }

    private fun columns(table: String): Map<String, Column> {
        val result = linkedMapOf<String, Column>()
        db.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(`$table`)").use { rows ->
                while (rows.next()) {
                    val column = Column(
                        name = rows.getString("name"),
                        type = rows.getString("type"),
                        notNull = rows.getInt("notnull") != 0,
                        defaultValue = rows.getString("dflt_value"),
                        primaryKey = rows.getInt("pk") != 0
                    )
                    result[column.name] = column
                }
            }
        }
        return result
    }

    @Test
    fun `upgrading a v1 database creates every missing table and keeps health rows`() {
        migrateAll()

        val tables = tableNames()
        assertTrue(tables.contains("voice_messages"))
        assertTrue(tables.contains("blood_pressure_record"))
        assertTrue(tables.contains("alarm_record"))
        assertTrue(tables.contains("heart_rate_record"))

        db.createStatement().use { statement ->
            statement.executeQuery("SELECT bpm FROM heart_rate_record").use { rows ->
                assertTrue(rows.next())
                assertEquals(72, rows.getInt(1))
                assertTrue(!rows.next())
            }
        }
    }

    @Test
    fun `v1 to v2 actually creates the transcript table the old empty migration skipped`() {
        exec(MigrationSql.V1_TO_V2)

        val names = columns("voice_messages").keys
        assertEquals(
            listOf("id", "timestamp", "sender", "text", "toolExecuted", "status"),
            names.toList()
        )
        assertTrue(columns("voice_messages").getValue("id").primaryKey)
        assertNull(columns("voice_messages").getValue("toolExecuted").defaultValue)
        assertTrue(columns("voice_messages").getValue("toolExecuted").type == "TEXT")
    }

    @Test
    fun `v1 to v2 can be applied to a database that already has the table`() {
        exec(MigrationSql.V1_TO_V2)
        exec(MigrationSql.V1_TO_V2)
        assertTrue(tableNames().contains("voice_messages"))
    }

    @Test
    fun `v3 to v4 adds the alarm state columns with honest defaults`() {
        migrateAll()

        val alarm = columns("alarm_record")
        assertTrue(alarm.getValue("id").primaryKey)
        assertEquals("PENDING", alarm.getValue("syncState").defaultValue?.trim('\'', '"'))
        assertEquals("0", alarm.getValue("updatedAt").defaultValue?.trim('\'', '"'))
        assertTrue(alarm.getValue("syncState").notNull)
        assertTrue(alarm.getValue("updatedAt").notNull)
    }

    @Test
    fun `v3 to v4 adds the nullable transcript session id`() {
        migrateAll()

        val voice = columns("voice_messages")
        assertNotNull(voice["sessionId"])
        assertEquals("TEXT", voice.getValue("sessionId").type)
        assertTrue(!voice.getValue("sessionId").notNull)
    }

    @Test
    fun `an alarm row inserted without state receives PENDING and zero update time`() {
        migrateAll()

        db.createStatement().use { statement ->
            statement.execute(
                "INSERT INTO alarm_record (id, hour, minute, repeatDays, isEnabled, label) " +
                    "VALUES (0, 7, 30, 127, 1, 'Alarm')"
            )
        }
        db.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT syncState, updatedAt FROM alarm_record WHERE id = 0"
            ).use { rows ->
                assertTrue(rows.next())
                assertEquals("PENDING", rows.getString(1))
                assertEquals(0L, rows.getLong(2))
            }
        }
    }
}
