package com.example.posapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.posapp.data.dao.ClienteDao
import com.example.posapp.data.dao.ProductoDao
import com.example.posapp.data.dao.VentaDao
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.PagoFiado
import com.example.posapp.data.entities.Venta

@Database(entities = [Producto::class, Cliente::class, Venta::class, DetalleVenta::class, PagoFiado::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun clienteDao(): ClienteDao
    abstract fun ventaDao(): VentaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cliente ADD COLUMN nota TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pago_fiado (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ventaId INTEGER NOT NULL,
                        detalleId INTEGER NOT NULL,
                        monto REAL NOT NULL,
                        fecha_hora INTEGER NOT NULL,
                        FOREIGN KEY(ventaId) REFERENCES venta(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(detalleId) REFERENCES detalle_venta(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pago_fiado_ventaId ON pago_fiado(ventaId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pago_fiado_detalleId ON pago_fiado(detalleId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_venta_clienteId ON venta(clienteId)")
            }
        }

        /**
         * Conserva los IDs existentes de Room y añade un UUID por fila para la nube.
         * No se borra ni se recrea la base de datos del negocio.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE producto ADD COLUMN sync_id TEXT")
                db.execSQL("ALTER TABLE cliente ADD COLUMN sync_id TEXT")
                db.execSQL("ALTER TABLE venta ADD COLUMN sync_id TEXT")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN sync_id TEXT")
                db.execSQL("ALTER TABLE pago_fiado ADD COLUMN sync_id TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_producto_sync_id ON producto(sync_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cliente_sync_id ON cliente(sync_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_venta_sync_id ON venta(sync_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_detalle_venta_sync_id ON detalle_venta(sync_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_pago_fiado_sync_id ON pago_fiado(sync_id)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE venta ADD COLUMN nube_sincronizada INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE detalle_venta ADD COLUMN nube_sincronizada INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE pago_fiado ADD COLUMN nube_sincronizada INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
