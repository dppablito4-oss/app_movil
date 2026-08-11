package com.example.posapp.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInputValidatorTest {
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
    fun otpError_requiresSixDigits() {
        assertEquals("El código debe tener 6 números.", AuthInputValidator.otpError("12a456"))
        assertNull(AuthInputValidator.otpError("123456"))
    }

    @Test
    fun businessNameError_rejectsBlankName() {
        assertEquals("Escribe el nombre del negocio.", AuthInputValidator.businessNameError("  "))
        assertNull(AuthInputValidator.businessNameError("Bodega Central"))
    }
}
