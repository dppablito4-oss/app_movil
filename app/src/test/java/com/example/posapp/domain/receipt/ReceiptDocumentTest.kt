package com.example.posapp.domain.receipt

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReceiptDocumentTest {
    @Test
    fun createsStableCodeFromUtcDateAndSyncId() {
        assertEquals(
            "NV-20260815-A1B2C3D4",
            ReceiptCode.from(Instant.parse("2026-08-15T23:59:59Z").toEpochMilli(), "a1b2c3d4-1111-2222")
        )
    }

    @Test
    fun rejectsLineAndDocumentTotalsThatDoNotMatch() {
        assertThrows(IllegalArgumentException::class.java) {
            ReceiptLine("Cafe", 2, 500, 999)
        }
        val line = ReceiptLine("Cafe", 2, 500, 1_000)
        assertThrows(IllegalArgumentException::class.java) {
            sampleDocument(listOf(line), subtotal = 999, total = 999)
        }
    }

    companion object {
        fun sampleDocument(
            lines: List<ReceiptLine> = listOf(ReceiptLine("Cafe", 2, 500, 1_000)),
            subtotal: Long = 1_000,
            total: Long = 1_000
        ) = ReceiptDocument(
            businessId = "business-1",
            saleSyncId = "a1b2c3d4-1111-2222-3333-444455556666",
            displayCode = "NV-20260815-A1B2C3D4",
            issuedAt = 1_776_470_400_000,
            business = ReceiptBusiness("Space Labs", null, null, null),
            customer = null,
            paymentMethod = "EFECTIVO",
            lines = lines,
            subtotalCents = subtotal,
            discountCents = 0,
            totalCents = total,
            amountInWords = total.toPenWords(),
            internalQrValue = null,
            footerMessage = "Gracias por su compra."
        )
    }
}
