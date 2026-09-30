package com.dpad.messaging.databases

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MessagesDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate5To6() {
        helper.createDatabase(testDb, 5).close()

        helper.runMigrationsAndValidate(
            testDb,
            6,
            true,
            MessagesDatabase.MIGRATION_5_6
        )
    }
}
