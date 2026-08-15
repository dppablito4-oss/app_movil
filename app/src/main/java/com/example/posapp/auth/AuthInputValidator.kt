package com.example.posapp.auth

object AuthInputValidator {
    const val OTP_LENGTH = 8
    const val PASSWORD_MIN_LENGTH = 8

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

    fun displayNameError(value: String): String? = when {
        value.trim().length < 2 -> "Escribe tu nombre."
        value.trim().length > 80 -> "Usa como máximo 80 caracteres."
        else -> null
    }

    fun passwordError(value: String): String? = when {
        value.length < PASSWORD_MIN_LENGTH -> "Usa al menos $PASSWORD_MIN_LENGTH caracteres."
        value.length > 72 -> "Usa como máximo 72 caracteres."
        value.none(Char::isLetter) || value.none(Char::isDigit) -> "Incluye al menos una letra y un número."
        else -> null
    }

    fun passwordConfirmationError(password: String, confirmation: String): String? =
        if (password != confirmation) "Las contraseñas no coinciden." else null
}
