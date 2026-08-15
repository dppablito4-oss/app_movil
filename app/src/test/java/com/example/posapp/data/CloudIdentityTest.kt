package com.example.posapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudIdentityTest {
    @Test
    fun emptyUuidIsRejectedBeforeBuildingRemotePayload() {
        assertNull("".normalizedUuidOrNull())
        assertNull("   ".normalizedUuidOrNull())
        assertThrows(IllegalArgumentException::class.java) {
            "".requireCloudUuid("business_id")
        }
    }

    @Test
    fun canonicalUuidIsNormalized() {
        assertEquals(
            "123e4567-e89b-12d3-a456-426614174000",
            "123E4567-E89B-12D3-A456-426614174000".normalizedUuidOrNull()
        )
    }
}
