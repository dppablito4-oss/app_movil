package com.example.posapp.auth

object AuthInputValidator {
    private val emailPattern = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)

    fun normalizeEmail(value: String): String = value.trim().lowercase()

    fun emailError(value: String): String? {
        val email = normalizeEmail(value)
        return when {
            email.isBlank() -> "Escribe tu correo electrónico."
            email.length > 254 || !emailPattern.matches(email) -> "Escribe un correo válido."
            else -> null
        }
    }

    fun otpError(value: String): String? = when {
        value.length != 6 || value.any { !it.isDigit() } -> "El código debe tener 6 números."
        else -> null
    }

    fun businessNameError(value: String): String? = when {
        value.trim().isBlank() -> "Escribe el nombre del negocio."
        value.trim().length > 120 -> "Usa como máximo 120 caracteres."
        else -> null
    }
}
