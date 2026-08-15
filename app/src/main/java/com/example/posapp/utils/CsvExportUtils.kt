package com.example.posapp.utils

import android.content.Context
import android.net.Uri
import com.example.posapp.data.ActiveBusinessStore
import com.example.posapp.data.AppDatabase
import java.io.OutputStreamWriter

object CsvExportUtils {
    suspend fun exportSales(context: Context, uri: Uri) {
        val database = AppDatabase.getInstance(context)
        val businessId = ActiveBusinessStore(context).businessId()
        require(businessId.isNotBlank()) { "No hay un negocio activo" }
        val sales = database.ventaDao().getAllVentas(businessId)
        val clients = database.clienteDao().getAllForSync(businessId).associateBy { it.id }
        context.contentResolver.openOutputStream(uri)?.use { output ->
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.appendLine("venta_id,fecha,total,metodo,estado,cliente")
                sales.forEach { sale ->
                    writer.appendLine(
                        listOf(
                            sale.sync_id ?: sale.id.toString(),
                            sale.fecha_hora.toString(),
                            sale.total_centavos.toString(),
                            sale.tipo_pago,
                            sale.estado,
                            sale.clienteId?.let { clients[it]?.nombre }.orEmpty()
                        ).joinToString(",", transform = ::csvCell)
                    )
                }
            }
        } ?: error("No se pudo abrir el archivo de destino")
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
