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
class Migration11To12Test {
    private val databaseName = "migration-11-12"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrationSeparatesLocalRemoteAndMissingImageStates() {
        helper.createDatabase(databaseName, 11).apply {
            execSQL("INSERT INTO producto (id, nombre, precio_costo, precio_venta, stock, ruta_imagen, storage_path, image_sync_status, busqueda_normalizada, sync_id) VALUES (1, 'Local', 0, 1, 1, '/local/a.jpg', NULL, 'PENDING', 'LOCAL', 'p1')")
            execSQL("INSERT INTO producto (id, nombre, precio_costo, precio_venta, stock, ruta_imagen, storage_path, image_sync_status, busqueda_normalizada, sync_id) VALUES (2, 'Remoto', 0, 1, 1, NULL, 'b/products/p2/main.jpg', 'SYNCED', 'REMOTO', 'p2')")
            execSQL("INSERT INTO producto (id, nombre, precio_costo, precio_venta, stock, ruta_imagen, storage_path, image_sync_status, busqueda_normalizada, sync_id) VALUES (3, 'Sin foto', 0, 1, 1, NULL, NULL, 'SYNCED', 'SIN FOTO', 'p3')")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 12, true, AppDatabase.MIGRATION_11_12).use { db ->
            db.query("SELECT id, image_sync_status FROM producto ORDER BY id").use { cursor ->
                cursor.moveToFirst()
                assertEquals("LOCAL_PENDING", cursor.getString(1))
                cursor.moveToNext()
                assertEquals("DOWNLOAD_PENDING", cursor.getString(1))
                cursor.moveToNext()
                assertEquals("NONE", cursor.getString(1))
            }
        }
    }
}
