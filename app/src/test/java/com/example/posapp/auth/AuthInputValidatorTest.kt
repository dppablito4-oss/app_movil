package com.example.posapp.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import io.github.jan.supabase.auth.OtpType

class AuthInputValidatorTest {
    @Test
    fun otpType_matchesRegistrationPurpose() {
        assertEquals(OtpType.Email.SIGNUP, otpTypeFor(isRegistration = true))
        assertEquals(OtpType.Email.EMAIL, otpTypeFor(isRegistration = false))
    }

    @Test
    fun normalizeEmail_trimsAndLowercases() {
        assertEquals("tienda@example.com", AuthInputValidator.normalizeEmail("  TIENDA@Example.COM "))
    }

    @Test
    fun emailError_rejectsMalformedEmail() {
        assertEquals("Escribe un correo válido.", AuthInputValidator.emailError("tienda@"))
        assertNull(AuthInputValidator.emailError("tienda@example.com"))
    }

    @Test
    fun otpError_requiresEightDigits() {
        assertEquals("El código debe tener 8 números.", AuthInputValidator.otpError("12a45678"))
        assertNull(AuthInputValidator.otpError("12345678"))
    }

    @Test
    fun businessNameError_rejectsBlankName() {
        assertEquals("Escribe el nombre del negocio.", AuthInputValidator.businessNameError("  "))
        assertNull(AuthInputValidator.businessNameError("Bodega Central"))
    }

    @Test
    fun passwordError_requiresLengthLetterAndNumber() {
        assert(AuthInputValidator.passwordError("12345678") != null)
        assert(AuthInputValidator.passwordError("abcdefgh") != null)
        assertNull(AuthInputValidator.passwordError("Space123"))
    }

    @Test
    fun passwordConfirmationError_requiresSameValue() {
        assert(AuthInputValidator.passwordConfirmationError("Space123", "Otra1234") != null)
        assertNull(AuthInputValidator.passwordConfirmationError("Space123", "Space123"))
    }
}
