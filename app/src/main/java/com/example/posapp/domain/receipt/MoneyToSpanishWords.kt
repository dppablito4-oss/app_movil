package com.example.posapp.domain.receipt

import java.util.Locale

fun Long.toPenWords(): String {
    require(this in 0..99_999_999_999L) { "El importe excede el limite soportado" }
    val whole = this / 100
    val cents = this % 100
    val words = numberToSpanish(whole)
    return "$words CON ${cents.toString().padStart(2, '0')}/100 SOLES".uppercase(Locale.forLanguageTag("es-PE"))
}

private fun numberToSpanish(value: Long): String = when {
    value == 0L -> "CERO"
    value < 30 -> listOf(
        "CERO", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE",
        "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE",
        "DIECIOCHO", "DIECINUEVE", "VEINTE", "VEINTIÚN", "VEINTIDÓS", "VEINTITRÉS",
        "VEINTICUATRO", "VEINTICINCO", "VEINTISÉIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE"
    )[value.toInt()]
    value < 100 -> {
        val tens = listOf("", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA", "OCHENTA", "NOVENTA")
        val unit = value % 10
        tens[(value / 10).toInt()] + if (unit == 0L) "" else " Y ${apocopate(numberToSpanish(unit))}"
    }
    value == 100L -> "CIEN"
    value < 1_000 -> {
        val hundreds = listOf("", "CIENTO", "DOSCIENTOS", "TRESCIENTOS", "CUATROCIENTOS", "QUINIENTOS", "SEISCIENTOS", "SETECIENTOS", "OCHOCIENTOS", "NOVECIENTOS")
        val rest = value % 100
        hundreds[(value / 100).toInt()] + if (rest == 0L) "" else " ${numberToSpanish(rest)}"
    }
    value < 1_000_000 -> {
        val thousands = value / 1_000
        val rest = value % 1_000
        (if (thousands == 1L) "MIL" else "${apocopate(numberToSpanish(thousands))} MIL") +
            if (rest == 0L) "" else " ${numberToSpanish(rest)}"
    }
    else -> {
        val millions = value / 1_000_000
        val rest = value % 1_000_000
        (if (millions == 1L) "UN MILLÓN" else "${apocopate(numberToSpanish(millions))} MILLONES") +
            if (rest == 0L) "" else " ${numberToSpanish(rest)}"
    }
}

private fun apocopate(value: String): String = when {
    value.endsWith("VEINTIÚN") -> value
    value.endsWith(" Y UNO") -> value.removeSuffix("UNO") + "UN"
    value.endsWith("UNO") -> value.removeSuffix("UNO") + "UN"
    else -> value
}
