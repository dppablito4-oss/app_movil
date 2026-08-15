package com.example.posapp.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private const val CENTS_PER_UNIT = 100L

fun BigDecimal.toCents(): Long =
    setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2)
        .longValueExact()

fun Double.toCents(): Long {
    require(isFinite()) { "El importe no es válido" }
    return BigDecimal.valueOf(this).toCents()
}

fun Long.toMoneyDouble(): Double = this.toDouble() / CENTS_PER_UNIT

fun Long.formatPen(locale: Locale = Locale("es", "PE")): String =
    NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("PEN")
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(toMoneyDouble())
