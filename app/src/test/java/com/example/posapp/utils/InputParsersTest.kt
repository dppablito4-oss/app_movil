package com.example.posapp.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputParsersTest {
    @Test
    fun parsesPeruvianDecimalComma() {
        assertEquals(2.50, parseLocalizedDecimal("2,50")!!, 0.0)
    }

    @Test
    fun parsesDecimalPointAndWhitespace() {
        assertEquals(12.75, parseLocalizedDecimal(" 12.75 ")!!, 0.0)
    }

    @Test
    fun rejectsInvalidAndNonFiniteNumbers() {
        assertNull(parseLocalizedDecimal("2,5,0"))
        assertNull(parseLocalizedDecimal("NaN"))
        assertNull(parseLocalizedDecimal("Infinity"))
    }
}
