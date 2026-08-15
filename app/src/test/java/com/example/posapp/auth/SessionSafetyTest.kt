package com.example.posapp.auth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSafetyTest {
    private val oldUid = "11111111-1111-4111-8111-111111111111"
    private val newUid = "22222222-2222-4222-8222-222222222222"

    @Test
    fun authenticatedUidChangeRequiresDeletingPreviousRoomOwner() {
        assertTrue(shouldClearForAuthenticatedUser(oldUid, newUid))
        assertFalse(shouldClearForAuthenticatedUser(oldUid, oldUid))
        assertFalse(shouldClearForAuthenticatedUser(null, newUid))
    }

    @Test
    fun remotelyDeletedBusinessCannotReopenItsCachedRoomData() {
        assertTrue(
            shouldDiscardCachedBusiness(
                remoteRequestSucceeded = true,
                remoteBusinessFound = false,
                cachedBusinessExists = true
            )
        )
        assertTrue(isInvalidRemoteSession(IllegalStateException("User not found")))
    }

    @Test
    fun offlineRemoteLogoutStillClearsLocalData() = runBlocking {
        var localDataCleared = false

        signOutAndAlwaysClear(
            remoteSignOut = { throw IllegalStateException("network unavailable") },
            clearLocalData = { localDataCleared = true }
        )

        assertTrue(localDataCleared)
    }
}
