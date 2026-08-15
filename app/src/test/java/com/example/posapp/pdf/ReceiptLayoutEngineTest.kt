package com.example.posapp.pdf

import com.example.posapp.domain.receipt.ReceiptDocumentTest
import com.example.posapp.domain.receipt.ReceiptFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptLayoutEngineTest {
    @Test
    fun wrapsWordsAndAlsoSplitsAWordThatCannotFit() {
        val lines = TextWrapper.wrap("producto extraordinariamente largo", 10f) { it.length.toFloat() }
        assertTrue(lines.all { it.length <= 10 })
        assertEquals("producto", lines.first())
        assertTrue(lines.size >= 4)
    }

    @Test
    fun sanitizesReadablePdfName() {
        val document = ReceiptDocumentTest.sampleDocument()
        assertEquals(
            "SpaceSale-NV-20260815-A1B2C3D4-80mm.pdf",
            ReceiptFileNames.forDocument(document, ReceiptFormat.TICKET_80MM)
        )
        assertEquals("negocio_malo", ReceiptFileNames.sanitize("../negocio malo"))
    }
}
