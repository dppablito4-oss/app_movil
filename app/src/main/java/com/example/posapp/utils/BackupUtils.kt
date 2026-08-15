package com.example.posapp.utils

import android.content.Context
import android.net.Uri
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.entities.Cliente
import com.example.posapp.data.entities.DetalleVenta
import com.example.posapp.data.entities.Producto
import com.example.posapp.data.entities.PagoFiado
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
    val detalles: List<DetalleVenta>,
    val pagos: List<PagoFiado> = emptyList()
)

object BackupUtils {
    suspend fun createBackupJson(context: Context): String {
        val db = AppDatabase.getInstance(context)
        val businessId = ActiveBusinessStore(context).businessId()
        require(businessId.isNotBlank()) { "No hay un negocio activo" }
        val backup = BackupData(
            productos = db.productoDao().getAll(businessId).first(),
            clientes = db.clienteDao().getAll(businessId).first(),
            ventas = db.ventaDao().getAllVentas(businessId),
            detalles = db.ventaDao().getAllDetalles(businessId),
            pagos = db.ventaDao().getAllPagos(businessId)
        )
        return GsonBuilder().setPrettyPrinting().create().toJson(backup)
    }

    suspend fun exportDatabaseToUri(context: Context, uri: Uri) {
        val json = createBackupJson(context)
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
            ?: throw IllegalStateException("No se pudo abrir el archivo de destino")
    }

    suspend fun exportDatabaseToJson(context: Context): String {
        val json = createBackupJson(context)

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val filename = "spacesale_backup_${sdf.format(Date())}.json"

        val backupsDir = File(context.filesDir, "backups")
        if (!backupsDir.exists()) backupsDir.mkdirs()

        val outFile = File(backupsDir, filename)
        outFile.writeText(json)

        return outFile.absolutePath
    }
}
