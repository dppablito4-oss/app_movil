package com.example.posapp.auth

object AuthInputValidator {
    const val OTP_LENGTH = 8

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
        value.length != OTP_LENGTH || value.any { !it.isDigit() } ->
            "El código debe tener $OTP_LENGTH números."
        else -> null
    }

    fun businessNameError(value: String): String? = when {
        value.trim().isBlank() -> "Escribe el nombre del negocio."
        value.trim().length > 120 -> "Usa como máximo 120 caracteres."
        else -> null
    }
}
