package com.example.posapp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration8To9Test {
    private val databaseName = "migration-8-9"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrate8To9CreatesTheScopedBarcodeIndex() {
        helper.createDatabase(databaseName, 8).close()
        helper.runMigrationsAndValidate(databaseName, 9, true, AppDatabase.MIGRATION_8_9).close()
    }
}
