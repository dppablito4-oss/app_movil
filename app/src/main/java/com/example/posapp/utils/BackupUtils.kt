package com.example.posapp.utils

import android.content.Context
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.Venta
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BackupData(
    val productos: List<Producto>,
    val clientes: List<Cliente>,
    val ventas: List<Venta>,
    val detalles: List<DetalleVenta>
)

object BackupUtils {
    suspend fun exportDatabaseToJson(context: Context): String {
        val db = AppDatabase.getInstance(context)
        val productoDao = db.productoDao()
        val clienteDao = db.clienteDao()
        val ventaDao = db.ventaDao()

        val productos: List<Producto> = productoDao.getAll().first()
        val clientes: List<Cliente> = clienteDao.getAll().first()
        val ventas: List<Venta> = ventaDao.getAllVentas()
        val detalles: List<DetalleVenta> = ventaDao.getAllDetalles()

        val backup = BackupData(productos, clientes, ventas, detalles)

        val gson: Gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.toJson(backup)

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val filename = "pablito_backup_${sdf.format(Date())}.json"

        val backupsDir = File(context.filesDir, "backups")
        if (!backupsDir.exists()) backupsDir.mkdirs()

        val outFile = File(backupsDir, filename)
        outFile.writeText(json)

        return outFile.absolutePath
    }
}
