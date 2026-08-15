package com.example.posapp.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthRegistrationStateTest {
    @Test
    fun businessDraftIsNormalizedAndPreservedAfterBackendFailure() {
        val draft = AuthUiState(
            step = AuthStep.REGISTER_BUSINESS,
            mode = AuthMode.REGISTER,
            email = "negocio@correo.com",
            displayName = "Ana Torres"
        ).withBusinessRegistrationDraft(
            businessName = "  Bodega Central  ",
            businessAddress = "  Av. Lima 123  ",
            businessPhone = "  999888777  ",
            logoPath = "/data/user/0/logo.jpg"
        )

        val recovered = draft.recoverBusinessRegistration(
            verifiedUserId = "user-123",
            message = "No se pudo preparar tu negocio. Intenta nuevamente."
        )

        assertEquals(AuthStep.REGISTER_BUSINESS, recovered.step)
        assertEquals("user-123", recovered.userId)
        assertEquals("Bodega Central", recovered.businessName)
        assertEquals("Av. Lima 123", recovered.businessAddress)
        assertEquals("999888777", recovered.businessPhone)
        assertEquals("/data/user/0/logo.jpg", recovered.logoPath)
        assertFalse(recovered.isSubmitting)
    }
}
