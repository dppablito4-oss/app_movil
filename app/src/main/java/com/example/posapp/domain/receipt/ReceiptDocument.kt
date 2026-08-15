package com.example.posapp.domain.receipt

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ReceiptFormat { TICKET_80MM, A4 }

data class ReceiptBusiness(
    val name: String,
    val address: String?,
    val phone: String?,
    val logoLocalPath: String?
)

data class ReceiptCustomer(
    val name: String,
    val document: String?,
    val phone: String?
)

data class ReceiptLine(
    val nameSnapshot: String,
    val quantity: Int,
    val unitPriceCents: Long,
    val lineTotalCents: Long
) {
    init {
        require(nameSnapshot.isNotBlank()) { "La linea necesita un nombre historico" }
        require(quantity > 0) { "La cantidad debe ser positiva" }
        require(unitPriceCents >= 0) { "El precio no puede ser negativo" }
        require(lineTotalCents == Math.multiplyExact(unitPriceCents, quantity.toLong())) {
            "El total de linea no coincide con precio por cantidad"
        }
    }
}

data class ReceiptDocument(
    val businessId: String,
    val saleSyncId: String,
    val displayCode: String,
    val issuedAt: Long,
    val business: ReceiptBusiness,
    val customer: ReceiptCustomer?,
    val paymentMethod: String,
    val lines: List<ReceiptLine>,
    val subtotalCents: Long,
    val discountCents: Long,
    val totalCents: Long,
    val amountInWords: String,
    val internalQrValue: String?,
    val footerMessage: String
) {
    init {
        require(businessId.isNotBlank() && saleSyncId.isNotBlank()) { "La venta no tiene identidad estable" }
        require(displayCode.isNotBlank() && business.name.isNotBlank()) { "La nota no tiene identificacion visible" }
        require(lines.isNotEmpty()) { "La nota debe tener productos" }
        require(subtotalCents >= 0 && discountCents >= 0 && totalCents > 0) { "Importes invalidos" }
        require(subtotalCents - discountCents == totalCents) { "El total no coincide con subtotal y descuento" }
        require(lines.sumOf(ReceiptLine::lineTotalCents) == subtotalCents) { "El subtotal no coincide con sus lineas" }
        require(amountInWords.isNotBlank()) { "Falta el importe en letras" }
    }
}

object ReceiptCode {
    private val invalidCodeChars = Regex("[^A-Za-z0-9]")

    fun from(issuedAt: Long, saleSyncId: String): String {
        require(issuedAt >= 0 && saleSyncId.isNotBlank()) { "No se puede crear el codigo de nota" }
        val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(issuedAt))
        val suffix = saleSyncId.replace(invalidCodeChars, "").uppercase(Locale.ROOT).take(8)
        require(suffix.length == 8) { "El identificador de venta es demasiado corto" }
        return "NV-$date-$suffix"
    }
}
