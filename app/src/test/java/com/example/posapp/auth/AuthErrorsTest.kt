package com.example.posapp.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthErrorsTest {
    @Test
    fun classifiesOtpNetworkAndSmtpFailures() {
        assertEquals(AuthFailure.INVALID_OTP, classifyAuthFailure(IllegalStateException("invalid otp token")))
        assertEquals(AuthFailure.OFFLINE, classifyAuthFailure(IllegalStateException("failed to connect")))
        assertEquals(AuthFailure.SMTP_FAILURE, classifyAuthFailure(IllegalStateException("smtp unexpected_failure")))
    }

    @Test
    fun unknownMessagesMatchTheOperationAndDoNotExposeInfrastructure() {
        val send = authUserMessage(AuthOperation.SEND_OTP, AuthFailure.UNKNOWN)
        val business = authUserMessage(AuthOperation.CREATE_BUSINESS, AuthFailure.PERMISSION_DENIED)

        assertEquals("No se pudo enviar el codigo. Intenta nuevamente.", send)
        assertEquals("No se pudo preparar tu negocio. Intenta nuevamente.", business)
        assertFalse(send.contains("Supabase", ignoreCase = true))
        assertFalse(business.contains("RLS", ignoreCase = true))
    }
}
