package com.marvneo.companion.data.db

/**
 * The exact SQL used by the additive Room migrations.
 *
 * Keeping the statements in one pure object lets a JVM test apply them to a real SQLite database
 * (see `MigrationSqlTest`) without an emulator. [AppDatabase] executes the same lists, so the
 * statements that pass the test are the statements that ship.
 */
internal object MigrationSql {

    /**
     * v1 -> v2 added the transcript table. The previous build shipped this migration empty, which
     * made a v1 database fail its v2 validation and then fail `ALTER TABLE voice_messages` at v3
     * -> v4. Creating the table is additive and preserves every v1 health row.
     */
    val V1_TO_V2: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `voice_messages` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `timestamp` INTEGER NOT NULL,
            `sender` TEXT NOT NULL,
            `text` TEXT NOT NULL,
            `toolExecuted` TEXT,
            `status` TEXT NOT NULL
        )
        """.trimIndent()
    )

    /** v2 -> v3 added blood pressure and alarm tables. */
    val V2_TO_V3: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS `blood_pressure_record` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `timestamp` INTEGER NOT NULL,
            `systolic` INTEGER NOT NULL,
            `diastolic` INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS `alarm_record` (
            `id` INTEGER NOT NULL,
            `hour` INTEGER NOT NULL,
            `minute` INTEGER NOT NULL,
            `repeatDays` INTEGER NOT NULL,
            `isEnabled` INTEGER NOT NULL,
            `label` TEXT NOT NULL,
            PRIMARY KEY(`id`)
        )
        """.trimIndent()
    )

    /** v3 -> v4: alarm acknowledgement state, alarm update time and transcript session identity. */
    val V3_TO_V4: List<String> = listOf(
        "ALTER TABLE `alarm_record` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'PENDING'",
        "ALTER TABLE `alarm_record` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE `voice_messages` ADD COLUMN `sessionId` TEXT"
    )
}
