package com.example.posapp.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration10To11Test {
    private val databaseName = "migration-10-11"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    @Throws(IOException::class)
    fun migrationCopiesProductIdentityIntoSaleSnapshots() {
        helper.createDatabase(databaseName, 10).apply {
            execSQL(
                "INSERT INTO producto (id, nombre, precio_costo, precio_venta, stock, ruta_imagen, busqueda_normalizada, sync_id) VALUES (1, 'Cafe especial', 5.0, 10.0, 4, NULL, 'CAFE ESPECIAL', 'product-uuid')"
            )
            execSQL(
                "INSERT INTO venta (id, fecha_hora, total, tipo_pago, clienteId, estado, sync_id, nube_sincronizada) VALUES (1, 1, 10.0, 'EFECTIVO', NULL, 'CERRADO', 'sale-uuid', 1)"
            )
            execSQL(
                "INSERT INTO detalle_venta (id, ventaId, productoId, cantidad, precio_unitario_historico, sync_id, nube_sincronizada) VALUES (1, 1, 1, 1, 10.0, 'detail-uuid', 1)"
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 11, true, AppDatabase.MIGRATION_10_11).use { db ->
            db.query(
                "SELECT productoId, product_sync_id_snapshot, product_name_snapshot FROM detalle_venta WHERE id = 1"
            ).use { cursor ->
                assertFalse(cursor.isAfterLast)
                cursor.moveToFirst()
                assertEquals(1L, cursor.getLong(0))
                assertEquals("product-uuid", cursor.getString(1))
                assertEquals("Cafe especial", cursor.getString(2))
            }
        }
    }
}
