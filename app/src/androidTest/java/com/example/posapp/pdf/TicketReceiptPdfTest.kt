package com.example.posapp.pdf

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.posapp.domain.receipt.ReceiptBusiness
import com.example.posapp.domain.receipt.ReceiptDocument
import com.example.posapp.domain.receipt.ReceiptFormat
import com.example.posapp.domain.receipt.ReceiptLine
import com.example.posapp.domain.receipt.toPenWords
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TicketReceiptPdfTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shortTicketIsExactlyEightyMillimetersWideAndShareable() = runBlocking {
        val file = ReceiptPdfGenerator(context).generate(document(1), ReceiptFormat.TICKET_80MM).getOrThrow()
        open(file).use { renderer ->
            assertEquals(1, renderer.pageCount)
            renderer.openPage(0).use { page -> assertEquals(ReceiptMetrics.TICKET_WIDTH_PT, page.width) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        assertEquals("content", uri.scheme)
    }

    @Test
    fun longTicketUsesMultiplePagesWithoutChangingWidth() = runBlocking {
        val file = ReceiptPdfGenerator(context).generate(document(60), ReceiptFormat.TICKET_80MM).getOrThrow()
        open(file).use { renderer ->
            assertTrue(renderer.pageCount > 1)
            repeat(renderer.pageCount) { index ->
                renderer.openPage(index).use { page -> assertEquals(ReceiptMetrics.TICKET_WIDTH_PT, page.width) }
            }
        }
    }

    private fun document(lineCount: Int): ReceiptDocument {
        val lines = (1..lineCount).map { index ->
            ReceiptLine(
                nameSnapshot = "Producto número $index con descripción histórica muy larga, ñ y tildes para validar el ajuste del texto",
                quantity = 1,
                unitPriceCents = 250,
                lineTotalCents = 250
            )
        }
        val total = lines.sumOf(ReceiptLine::lineTotalCents)
        return ReceiptDocument(
            businessId = "business-test",
            saleSyncId = "a1b2c3d4-1111-2222-3333-444455556666",
            displayCode = "NV-20260815-A1B2C3D4",
            issuedAt = 1_776_470_400_000,
            business = ReceiptBusiness("SpaceSale Pruebas", null, null, null),
            customer = null,
            paymentMethod = "EFECTIVO",
            lines = lines,
            subtotalCents = total,
            discountCents = 0,
            totalCents = total,
            amountInWords = total.toPenWords(),
            internalQrValue = null,
            footerMessage = "Gracias por su compra."
        )
    }

    private fun open(file: File): PdfRenderer = PdfRenderer(
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    )
}
