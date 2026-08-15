package com.example.posapp.utils

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.posapp.vm.SaleReceipt
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

object ReceiptShare {
    fun sharePdf(context: Context, businessName: String, receipt: SaleReceipt) {
        val directory = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(directory, "SpaceSale-venta-${receipt.saleId}.pdf")
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK }
            var y = 54f

            paint.textSize = 24f
            paint.isFakeBoldText = true
            canvas.drawText(businessName.ifBlank { "SpaceSale" }, 42f, y, paint)
            y += 28f
            paint.textSize = 12f
            paint.isFakeBoldText = false
            canvas.drawText("Comprobante de venta #${receipt.saleId}", 42f, y, paint)
            y += 20f
            canvas.drawText(DateFormat.getDateTimeInstance().format(Date(receipt.createdAt)), 42f, y, paint)
            y += 18f
            canvas.drawText("Metodo: ${receipt.paymentMethod}", 42f, y, paint)
            y += 30f

            receipt.lines.forEach { line ->
                val lineTotal = Math.multiplyExact(line.unitPriceCents, line.quantity.toLong())
                canvas.drawText("${line.quantity} x ${line.name.take(45)}", 42f, y, paint)
                canvas.drawText(lineTotal.formatPen(), 440f, y, paint)
                y += 22f
            }

            y += 14f
            paint.textSize = 18f
            paint.isFakeBoldText = true
            canvas.drawText("Total", 42f, y, paint)
            canvas.drawText(receipt.totalCents.formatPen(), 420f, y, paint)
            y += 36f
            paint.textSize = 11f
            paint.isFakeBoldText = false
            canvas.drawText("Generado por SpaceSale · Space Labs", 42f, y, paint)
            document.finishPage(page)
            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Comprobante de venta #${receipt.saleId}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir comprobante"))
    }
}
