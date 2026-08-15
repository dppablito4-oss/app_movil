package com.example.posapp.utils

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.content.ClipData
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.posapp.domain.receipt.ReceiptFormat
import com.example.posapp.domain.receipt.RoomReceiptSnapshotFactory
import com.example.posapp.pdf.ReceiptPdfGenerator
import com.example.posapp.vm.SaleReceipt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ReceiptShare {
    fun sharePdf(context: Context, businessName: String, receipt: SaleReceipt) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val snapshot = RoomReceiptSnapshotFactory(context).fromSale(receipt.saleId)
                    val document = if (snapshot.business.name == "SpaceSale" && businessName.isNotBlank()) {
                        snapshot.copy(business = snapshot.business.copy(name = businessName))
                    } else snapshot
                    ReceiptPdfGenerator(context).generate(document, ReceiptFormat.TICKET_80MM).getOrThrow()
                }
            }
            result.onSuccess { file ->
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Nota de venta SpaceSale")
                    clipData = ClipData.newUri(context.contentResolver, "Nota de venta", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Compartir nota de venta"))
            }.onFailure {
                Toast.makeText(context, "La venta se guardo, pero no se pudo generar la nota.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
