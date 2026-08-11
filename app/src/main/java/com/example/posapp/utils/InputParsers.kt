package com.example.posapp.utils

fun parseLocalizedDecimal(value: String): Double? {
    val normalized = value.trim().replace(',', '.')
    if (normalized.count { it == '.' } > 1) return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}
