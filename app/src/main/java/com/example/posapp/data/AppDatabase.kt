package com.example.posapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.posapp.data.dao.ClienteDao
import com.example.posapp.data.dao.ProductoDao
import com.example.posapp.data.dao.VentaDao
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.Venta

@Database(entities = [Producto::class, Cliente::class, Venta::class, DetalleVenta::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
