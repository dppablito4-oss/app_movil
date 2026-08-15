package com.example.posapp.pdf

import android.content.Context
import com.example.posapp.domain.receipt.ReceiptDocument
import com.example.posapp.domain.receipt.ReceiptFormat
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReceiptPdfGenerator(context: Context) {
    private val appContext = context.applicationContext

    suspend fun generate(document: ReceiptDocument, format: ReceiptFormat): Result<File> = withContext(Dispatchers.IO) {
        var temporary: File? = null
        runCatching {
            require(format == ReceiptFormat.TICKET_80MM) { "El formato A4 se implementara en la siguiente fase" }
            val businessDirectory = File(
                appContext.cacheDir,
                "receipts/${ReceiptFileNames.sanitize(document.businessId)}"
            )
            check(businessDirectory.exists() || businessDirectory.mkdirs()) { "No se pudo preparar la carpeta temporal" }
            cleanupOldReceipts(businessDirectory)

            val name = ReceiptFileNames.forDocument(document, format)
            val destination = File(businessDirectory, name)
            temporary = File(businessDirectory, "$name.tmp")
            val pendingFile = requireNotNull(temporary)
            if (pendingFile.exists()) check(pendingFile.delete()) { "No se pudo limpiar un temporal anterior" }

            val logo = ReceiptLogoProcessor(appContext).loadTicketLogo(document.business.logoLocalPath)
            val pdf = Ticket80PdfRenderer().render(document, logo)
            try {
                FileOutputStream(pendingFile).use { output ->
                    pdf.writeTo(output)
                    output.fd.sync()
                }
            } finally {
                pdf.close()
                logo?.takeUnless { it.isRecycled }?.recycle()
            }
            check(pendingFile.length() > 0L) { "El PDF generado esta vacio" }
            if (destination.exists()) check(destination.delete()) { "No se pudo reemplazar la nota anterior" }
            check(pendingFile.renameTo(destination)) { "No se pudo completar el PDF" }
            destination
        }.onFailure {
            temporary?.takeIf(File::exists)?.delete()
        }
    }

    private fun cleanupOldReceipts(directory: File, maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        directory.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".tmp") || file.lastModified() < cutoff)) file.delete()
        }
    }

    private companion object {
        const val DEFAULT_MAX_AGE_MILLIS = 7L * 24L * 60L * 60L * 1_000L
    }
}

object ReceiptFileNames {
    fun forDocument(document: ReceiptDocument, format: ReceiptFormat): String {
        val suffix = when (format) {
            ReceiptFormat.TICKET_80MM -> "80mm"
            ReceiptFormat.A4 -> "A4"
        }
        return "SpaceSale-${sanitize(document.displayCode)}-$suffix.pdf"
    }

    fun sanitize(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trim('_', '.')
        .take(80)
        .ifBlank { "documento" }
}
