package com.example.posapp.pdf

import android.graphics.Paint

data class PdfTextStyle(
    val size: Float,
    val bold: Boolean = false,
    val lineSpacing: Float = 2f
)

data class PdfMargins(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class PdfColumn(val x: Float, val width: Float)

data class PdfTableRow<T>(val value: T, val height: Float)

object TextWrapper {
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        require(maxWidth > 0f) { "El ancho debe ser positivo" }
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return listOf("")
        val output = mutableListOf<String>()
        var current = ""
        normalized.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (measure(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) output += current
                if (measure(word) <= maxWidth) {
                    current = word
                } else {
                    val chunks = splitLongWord(word, maxWidth, measure)
                    output += chunks.dropLast(1)
                    current = chunks.last()
                }
            }
        }
        if (current.isNotEmpty()) output += current
        return output.ifEmpty { listOf("") }
    }

    private fun splitLongWord(word: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        val chunks = mutableListOf<String>()
        var current = ""
        word.forEach { character ->
            val candidate = current + character
            if (current.isNotEmpty() && measure(candidate) > maxWidth) {
                chunks += current
                current = character.toString()
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }
}

object ReceiptMetrics {
    const val TICKET_WIDTH_PT = 227
    const val TICKET_MARGIN_PT = 11f
    const val TICKET_CONTENT_WIDTH_PT = 205f
    const val MAX_TICKET_HEIGHT_PT = 1_600
    const val MIN_TICKET_HEIGHT_PT = 300
}

internal fun Paint.applyStyle(style: PdfTextStyle) {
    textSize = style.size
    isFakeBoldText = style.bold
}
