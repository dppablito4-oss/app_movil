package com.example.posapp.data.dao

import androidx.room.*
import com.example.posapp.data.entities.QrToken
import com.example.posapp.data.entities.QrBatch
import kotlinx.coroutines.flow.Flow

@Dao
interface QrTokenDao {
    @Query("SELECT * FROM qr_token WHERE business_id = :businessId AND token = :token LIMIT 1")
    suspend fun getByToken(businessId: String, token: String): QrToken?

    @Query("SELECT * FROM qr_token WHERE business_id = :businessId AND id = :id LIMIT 1")
    suspend fun getById(businessId: String, id: Long): QrToken?

    @Query("SELECT * FROM qr_token WHERE business_id = :businessId AND jobId = :jobId LIMIT 1")
    suspend fun getByJobId(businessId: String, jobId: Long): QrToken?

    @Query("SELECT * FROM qr_token WHERE business_id = :businessId AND status = 'unused' ORDER BY id ASC")
    fun observeUnusedTokens(businessId: String): Flow<List<QrToken>>

    @Query("SELECT * FROM qr_token WHERE business_id = :businessId ORDER BY created_at DESC")
    fun observeAll(businessId: String): Flow<List<QrToken>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: QrToken): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokens(tokens: List<QrToken>)

    @Update
    suspend fun updateToken(token: QrToken)

    @Query("UPDATE qr_token SET status = 'assigned', jobId = :jobId, job_sync_id = :jobSyncId, assigned_at = :assignedAt, updated_at = :now, sync_status = 'PENDING' WHERE business_id = :businessId AND token = :token")
    suspend fun assignTokenToJob(businessId: String, token: String, jobId: Long, jobSyncId: String?, assignedAt: Long, now: Long)

    @Query("UPDATE qr_token SET status = 'unused', jobId = NULL, job_sync_id = NULL, released_at = :releasedAt, updated_at = :now, sync_status = 'PENDING' WHERE business_id = :businessId AND token = :token")
    suspend fun releaseToken(businessId: String, token: String, releasedAt: Long, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: QrBatch): Long

    @Query("""
        UPDATE qr_token
        SET sync_status = 'SYNCED', remote_updated_at = :remoteUpdatedAt
        WHERE business_id = :businessId AND sync_id = :syncId AND updated_at <= :localVersion
    """)
    suspend fun markSyncedBySyncId(businessId: String, syncId: String, localVersion: Long, remoteUpdatedAt: Long)
}
