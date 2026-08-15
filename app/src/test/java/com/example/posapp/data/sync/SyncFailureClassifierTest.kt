package com.example.posapp.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncFailureClassifierTest {
    @Test
    fun stockConflictRequiresUserAction() {
        assertEquals(
            SyncFailureDisposition.ACTION_REQUIRED,
            classifySyncFailure(statusCode = 400, databaseCode = "P0001")
        )
    }

    @Test
    fun forbiddenRequestRequiresUserAction() {
        assertEquals(
            SyncFailureDisposition.ACTION_REQUIRED,
            classifySyncFailure(statusCode = 403, databaseCode = "42501")
        )
    }

    @Test
    fun serverAndRateLimitErrorsRemainRetryable() {
        assertEquals(SyncFailureDisposition.RETRY, classifySyncFailure(500, null))
        assertEquals(SyncFailureDisposition.RETRY, classifySyncFailure(429, null))
    }

    @Test
    fun networkFailureWithoutHttpStatusRemainsRetryable() {
        assertEquals(SyncFailureDisposition.RETRY, classifySyncFailure(null, null))
    }

    @Test
    fun invalidUuidSyntaxRequiresActionInsteadOfInfiniteRetries() {
        assertEquals(
            SyncFailureDisposition.ACTION_REQUIRED,
            classifySyncFailure(statusCode = 400, databaseCode = "22P02")
        )
    }
}
