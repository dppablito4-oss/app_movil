package com.example.posapp.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.posapp.domain.receipt.ReceiptDocument
import com.example.posapp.domain.receipt.ReceiptLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

class Ticket80PdfRenderer : ReceiptPdfRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val left = ReceiptMetrics.TICKET_MARGIN_PT
    private val right = ReceiptMetrics.TICKET_WIDTH_PT - ReceiptMetrics.TICKET_MARGIN_PT
    private val descX = left + 24f
    private val unitRight = right - 50f
    private val descriptionWidth = 91f
    private val rowStyle = PdfTextStyle(7.2f, lineSpacing = 2f)

    override fun render(document: ReceiptDocument, logo: Bitmap?): PdfDocument {
        val rows = document.lines.map(::layoutRow)
        val pages = paginate(document, rows, logo != null)
        val pdf = PdfDocument()
        pages.forEachIndexed { index, page ->
            val info = PdfDocument.PageInfo.Builder(
                ReceiptMetrics.TICKET_WIDTH_PT,
                page.height,
                index + 1
            ).create()
            val pdfPage = pdf.startPage(info)
            drawPage(pdfPage.canvas, document, page, logo)
            pdf.finishPage(pdfPage)
        }
        return pdf
    }

    private fun paginate(document: ReceiptDocument, rows: List<RowLayout>, hasLogo: Boolean): List<PagePlan> {
        val output = mutableListOf<PagePlan>()
        var first = true
        var pageRows = mutableListOf<RowLayout>()
        var used = headerHeight(document, first, hasLogo)

        rows.forEach { row ->
            val reserved = ReceiptMetrics.TICKET_MARGIN_PT + CONTINUATION_HEIGHT
            if (pageRows.isNotEmpty() && used + row.height + reserved > ReceiptMetrics.MAX_TICKET_HEIGHT_PT) {
                output += PagePlan(first, pageRows, isLast = false, height = pageHeight(used + reserved))
                first = false
                pageRows = mutableListOf()
                used = headerHeight(document, first, hasLogo)
            }
            require(used + row.height + reserved <= ReceiptMetrics.MAX_TICKET_HEIGHT_PT) {
                "Una linea es demasiado larga para el ticket"
            }
            pageRows += row
            used += row.height
        }

        val ending = endingHeight(document)
        if (used + ending + ReceiptMetrics.TICKET_MARGIN_PT > ReceiptMetrics.MAX_TICKET_HEIGHT_PT) {
            output += PagePlan(first, pageRows, isLast = false, height = pageHeight(used + CONTINUATION_HEIGHT))
            first = false
            pageRows = mutableListOf()
            used = headerHeight(document, first, hasLogo)
        }
        output += PagePlan(
            isFirst = first,
            rows = pageRows,
            isLast = true,
            height = pageHeight(used + ending + ReceiptMetrics.TICKET_MARGIN_PT)
        )
        return output
    }

    private fun drawPage(canvas: Canvas, document: ReceiptDocument, page: PagePlan, logo: Bitmap?) {
        canvas.drawColor(Color.WHITE)
        var y = ReceiptMetrics.TICKET_MARGIN_PT
        if (page.isFirst) {
            if (logo != null) {
                val target = fitRect(logo, maxWidth = 113f, maxHeight = 51f, centerX = ReceiptMetrics.TICKET_WIDTH_PT / 2f, top = y)
                canvas.drawBitmap(logo, null, target, paint)
                y = target.bottom + 5f
            }
            y = drawCenteredWrapped(canvas, document.business.name, y, 12f, bold = true, maxWidth = 190f)
            document.business.address?.let { y = drawCenteredWrapped(canvas, it, y, 8f, maxWidth = 190f) }
            document.business.phone?.let { y = drawCenteredWrapped(canvas, it, y, 8f, maxWidth = 190f) }
            y += 3f
        } else {
            y = drawCenteredWrapped(canvas, document.business.name, y, 9f, bold = true, maxWidth = 190f)
            y = drawCenteredWrapped(canvas, "${document.displayCode} · CONTINUACIÓN", y, 7f, maxWidth = 190f)
        }

        y = drawCenteredWrapped(canvas, "NOTA DE VENTA", y, 10f, bold = true, maxWidth = 190f)
        y = drawCenteredWrapped(canvas, "DOCUMENTO INTERNO", y, 7.5f, bold = true, maxWidth = 190f)
        if (page.isFirst) {
            y += 3f
            y = drawLeft(canvas, "Código: ${document.displayCode}", y, 8f)
            y = drawLeft(canvas, "Fecha: ${requireNotNull(DATE_FORMAT.get()).format(Date(document.issuedAt))}", y, 8f)
            y = drawLeft(canvas, "Método: ${document.paymentMethod.replace('_', ' ')}", y, 8f)
            document.customer?.let { customer ->
                y = drawLeftWrapped(canvas, "Cliente: ${customer.name}", y, 8f, ReceiptMetrics.TICKET_CONTENT_WIDTH_PT)
                customer.document?.let { y = drawLeft(canvas, "Documento: $it", y, 8f) }
                customer.phone?.let { y = drawLeft(canvas, "Teléfono: $it", y, 8f) }
            }
        }
        y += 3f
        drawSeparator(canvas, y)
        y += 9f
        paint.applyStyle(PdfTextStyle(7f, bold = true))
        canvas.drawText("CANT", left, y, paint)
        canvas.drawText("DESCRIPCIÓN", descX, y, paint)
        drawRight(canvas, "P.U.", unitRight, y)
        drawRight(canvas, "TOTAL", right, y)
        y += 5f
        drawSeparator(canvas, y)
        y += 9f

        page.rows.forEach { row ->
            paint.applyStyle(rowStyle)
            canvas.drawText(row.line.quantity.toString(), left, y, paint)
            row.descriptionLines.forEachIndexed { index, text ->
                canvas.drawText(text, descX, y + index * row.lineHeight, paint)
            }
            drawRight(canvas, money(row.line.unitPriceCents), unitRight, y)
            drawRight(canvas, money(row.line.lineTotalCents), right, y)
            y += row.height
        }

        if (!page.isLast) {
            drawSeparator(canvas, y)
            y += 12f
            drawCenteredWrapped(canvas, "Continúa…", y, 7f, bold = true, maxWidth = 190f)
            return
        }

        y += 2f
        drawSeparator(canvas, y)
        y += 11f
        y = drawAmountRow(canvas, "Subtotal", money(document.subtotalCents), y, 8f, false)
        if (document.discountCents > 0) y = drawAmountRow(canvas, "Descuento", "-${money(document.discountCents)}", y, 8f, false)
        y += 2f
        y = drawAmountRow(canvas, "TOTAL", money(document.totalCents), y, 12f, true)
        y += 3f
        y = drawLeftWrapped(canvas, document.amountInWords, y, 7.5f, ReceiptMetrics.TICKET_CONTENT_WIDTH_PT)
        y += 4f
        drawSeparator(canvas, y)
        y += 11f
        y = drawCenteredWrapped(canvas, "Documento interno sin valor tributario.", y, 7f, bold = true, maxWidth = 195f)
        y = drawCenteredWrapped(canvas, document.footerMessage, y, 7f, maxWidth = 195f)
        drawCenteredWrapped(canvas, "Generado con SpaceSale · Space Labs", y, 6.5f, maxWidth = 195f)
    }

    private fun layoutRow(line: ReceiptLine): RowLayout {
        paint.applyStyle(rowStyle)
        val lines = TextWrapper.wrap(line.nameSnapshot, descriptionWidth, paint::measureText)
        val lineHeight = rowStyle.size + rowStyle.lineSpacing
        return RowLayout(line, lines, lineHeight, maxOf(13f, lines.size * lineHeight + 4f))
    }

    private fun headerHeight(document: ReceiptDocument, first: Boolean, hasLogo: Boolean): Float {
        var height = ReceiptMetrics.TICKET_MARGIN_PT
        if (first) {
            if (hasLogo) height += 56f
            height += wrappedHeight(document.business.name, 12f, 190f)
            document.business.address?.let { height += wrappedHeight(it, 8f, 190f) }
            document.business.phone?.let { height += wrappedHeight(it, 8f, 190f) }
            height += 3f
        } else {
            height += wrappedHeight(document.business.name, 9f, 190f)
            height += wrappedHeight("${document.displayCode} · CONTINUACIÓN", 7f, 190f)
        }
        height += wrappedHeight("NOTA DE VENTA", 10f, 190f)
        height += wrappedHeight("DOCUMENTO INTERNO", 7.5f, 190f)
        if (first) {
            height += 3f + 3 * lineHeight(8f)
            document.customer?.let {
                height += wrappedHeight("Cliente: ${it.name}", 8f, ReceiptMetrics.TICKET_CONTENT_WIDTH_PT)
                if (it.document != null) height += lineHeight(8f)
                if (it.phone != null) height += lineHeight(8f)
            }
        }
        return height + 3f + 9f + 5f + 9f
    }

    private fun endingHeight(document: ReceiptDocument): Float {
        var height = 2f + 11f + lineHeight(8f) + 2f + lineHeight(12f) + 3f
        if (document.discountCents > 0) height += lineHeight(8f)
        height += wrappedHeight(document.amountInWords, 7.5f, ReceiptMetrics.TICKET_CONTENT_WIDTH_PT) + 4f + 11f
        height += wrappedHeight("Documento interno sin valor tributario.", 7f, 195f)
        height += wrappedHeight(document.footerMessage, 7f, 195f)
        height += wrappedHeight("Generado con SpaceSale · Space Labs", 6.5f, 195f)
        return height
    }

    private fun drawCenteredWrapped(canvas: Canvas, text: String, y: Float, size: Float, bold: Boolean = false, maxWidth: Float): Float {
        paint.applyStyle(PdfTextStyle(size, bold))
        var next = y
        TextWrapper.wrap(text, maxWidth, paint::measureText).forEach { line ->
            next += lineHeight(size)
            canvas.drawText(line, (ReceiptMetrics.TICKET_WIDTH_PT - paint.measureText(line)) / 2f, next, paint)
        }
        return next
    }

    private fun drawLeftWrapped(canvas: Canvas, text: String, y: Float, size: Float, maxWidth: Float): Float {
        paint.applyStyle(PdfTextStyle(size))
        var next = y
        TextWrapper.wrap(text, maxWidth, paint::measureText).forEach { line ->
            next += lineHeight(size)
            canvas.drawText(line, left, next, paint)
        }
        return next
    }

    private fun drawLeft(canvas: Canvas, text: String, y: Float, size: Float): Float {
        paint.applyStyle(PdfTextStyle(size))
        val baseline = y + lineHeight(size)
        canvas.drawText(text, left, baseline, paint)
        return baseline
    }

    private fun drawAmountRow(canvas: Canvas, label: String, amount: String, y: Float, size: Float, bold: Boolean): Float {
        paint.applyStyle(PdfTextStyle(size, bold))
        val baseline = y + lineHeight(size)
        canvas.drawText(label, left, baseline, paint)
        drawRight(canvas, amount, right, baseline)
        return baseline
    }

    private fun drawRight(canvas: Canvas, text: String, x: Float, y: Float) {
        canvas.drawText(text, x - paint.measureText(text), y, paint)
    }

    private fun drawSeparator(canvas: Canvas, y: Float) {
        paint.strokeWidth = 0.7f
        canvas.drawLine(left, y, right, y, paint)
    }

    private fun wrappedHeight(text: String, size: Float, maxWidth: Float): Float {
        paint.applyStyle(PdfTextStyle(size))
        return TextWrapper.wrap(text, maxWidth, paint::measureText).size * lineHeight(size)
    }

    private fun lineHeight(size: Float) = size + 2f
    private fun pageHeight(content: Float) = ceil(content).toInt().coerceIn(ReceiptMetrics.MIN_TICKET_HEIGHT_PT, ReceiptMetrics.MAX_TICKET_HEIGHT_PT)
    private fun money(cents: Long) = "S/ ${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

    private fun fitRect(bitmap: Bitmap, maxWidth: Float, maxHeight: Float, centerX: Float, top: Float): RectF {
        val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        return RectF(centerX - width / 2f, top, centerX + width / 2f, top + height)
    }

    private data class RowLayout(
        val line: ReceiptLine,
        val descriptionLines: List<String>,
        val lineHeight: Float,
        val height: Float
    )

    private data class PagePlan(
        val isFirst: Boolean,
        val rows: List<RowLayout>,
        val isLast: Boolean,
        val height: Int
    )

    private companion object {
        const val CONTINUATION_HEIGHT = 24f
        val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-PE"))
        }
    }
}
