package com.example.posapp.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingLocalDataTest {
    @Test
    fun productPendingWithEmptyQueue_blocksSignOut() {
        val pending = PendingLocalData(queuedOperations = 0, products = 1)

        assertFalse(pending.canSignOutSafely)
        assertTrue("1 producto" in pending.userSummary())
    }

    @Test
    fun salePendingWithEmptyQueue_blocksSignOut() {
        val pending = PendingLocalData(queuedOperations = 0, sales = 1, saleItems = 2)

        assertFalse(pending.canSignOutSafely)
        assertTrue("1 venta" in pending.userSummary())
    }

    @Test
    fun imagePendingWithSyncedProductAndEmptyQueue_blocksSignOut() {
        val pending = PendingLocalData(queuedOperations = 0, products = 0, images = 1)

        assertFalse(pending.canSignOutSafely)
        assertTrue("1 imagen" in pending.userSummary())
    }

    @Test
    fun noQueueOrEntityPending_allowsSignOut() {
        assertTrue(PendingLocalData().canSignOutSafely)
    }
}
