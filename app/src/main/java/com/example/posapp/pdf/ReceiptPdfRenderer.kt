package com.example.posapp.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import com.example.posapp.domain.receipt.ReceiptDocument

interface ReceiptPdfRenderer {
    fun render(document: ReceiptDocument, logo: Bitmap?): PdfDocument
}
