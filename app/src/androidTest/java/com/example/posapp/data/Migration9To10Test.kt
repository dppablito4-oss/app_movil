package com.example.posapp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration9To10Test {
    private val databaseName = "migration-9-10"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrationResetsUnsafeTimestampCursorAndPreservesSuccessTime() {
        helper.createDatabase(databaseName, 9).apply {
            execSQL(
                "INSERT INTO sync_metadata (business_id, entity_type, last_pulled_at, last_success_at) VALUES (?, ?, ?, ?)",
                arrayOf<Any>("business-a", "products", 123456789L, 987654321L)
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 10, true, AppDatabase.MIGRATION_9_10).use { db ->
            db.query(
                "SELECT last_server_timestamp, last_remote_id, last_success_at FROM sync_metadata WHERE business_id = ? AND entity_type = ?",
                arrayOf("business-a", "products")
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals("1970-01-01T00:00:00Z", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals(987654321L, cursor.getLong(2))
            }
        }
    }
}
