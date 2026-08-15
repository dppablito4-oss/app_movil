package com.example.posapp.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyToSpanishWordsTest {
    @Test
    fun convertsRequiredAmountsWithoutUsingDecimals() {
        assertEquals("CERO CON 00/100 SOLES", 0L.toPenWords())
        assertEquals("UNO CON 00/100 SOLES", 100L.toPenWords())
        assertEquals("VEINTIÚN CON 50/100 SOLES", 2_150L.toPenWords())
        assertEquals("CIEN CON 00/100 SOLES", 10_000L.toPenWords())
        assertEquals("MIL CON 00/100 SOLES", 100_000L.toPenWords())
        assertEquals("UN MILLÓN CON 01/100 SOLES", 100_000_001L.toPenWords())
    }

    @Test
    fun enforcesDocumentedUpperLimit() {
        assertEquals(
            "NOVECIENTOS NOVENTA Y NUEVE MILLONES NOVECIENTOS NOVENTA Y NUEVE MIL NOVECIENTOS NOVENTA Y NUEVE CON 99/100 SOLES",
            99_999_999_999L.toPenWords()
        )
        assertThrows(IllegalArgumentException::class.java) { 100_000_000_000L.toPenWords() }
    }
}
