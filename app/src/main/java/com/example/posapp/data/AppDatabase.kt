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

@Database(entities = [Producto::class, Cliente::class, Venta::class, DetalleVenta::class, PagoFiado::class], version = 2, exportSchema = true)
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
                ).addMigrations(MIGRATION_1_2).build()
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
    }
}
