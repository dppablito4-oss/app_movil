package com.example.posapp.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Test

class RemotePullCursorTest {
    @Test
    fun initialCursorUsesBlankOnlyAsLocalSentinel() {
        assertEquals("", RemotePullCursor().remoteId)
        assertNull(RemotePullCursor().remoteIdForFilter())
    }

    @Test
    fun cursorKeepsExactServerTimestampAndRemoteId() {
        val next = advanceRemoteCursor(
            current = RemotePullCursor(),
            serverTimestamp = "2026-08-15T06:42:10.123456Z",
            remoteId = "00000000-0000-4000-8000-000000000123"
        )

        assertEquals("2026-08-15T06:42:10.123456Z", next.serverTimestamp)
        assertEquals("00000000-0000-4000-8000-000000000123", next.remoteId)
    }

    @Test
    fun sameTimestampCanAdvanceUsingRemoteId() {
        val current = RemotePullCursor(
            serverTimestamp = "2026-08-15T06:42:10Z",
            remoteId = "00000000-0000-4000-8000-000000000001"
        )

        val next = advanceRemoteCursor(
            current,
            serverTimestamp = current.serverTimestamp,
            remoteId = "00000000-0000-4000-8000-000000000002"
        )

        assertEquals("00000000-0000-4000-8000-000000000002", next.remoteId)
    }

    @Test
    fun repeatedLastRowIsRejectedToPreventInfinitePaging() {
        val cursor = RemotePullCursor("2026-08-15T06:42:10Z", "same-id")

        assertThrows(IllegalArgumentException::class.java) {
            advanceRemoteCursor(cursor, cursor.serverTimestamp, cursor.remoteId)
        }
    }

    @Test
    fun olderTimestampIsRejected() {
        val cursor = RemotePullCursor("2026-08-15T06:42:10Z", "current-id")

        assertThrows(IllegalArgumentException::class.java) {
            advanceRemoteCursor(cursor, "2026-08-15T06:42:09Z", "later-id")
        }
    }
}
