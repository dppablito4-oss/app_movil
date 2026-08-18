package com.example.posapp.data.dao

import androidx.room.*
import com.example.posapp.data.entities.Job
import com.example.posapp.data.entities.JobItem
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM job WHERE business_id = :businessId AND id = :id LIMIT 1")
    suspend fun getById(businessId: String, id: Long): Job?

    @Query("SELECT * FROM job WHERE business_id = :businessId AND sync_id = :syncId LIMIT 1")
    suspend fun getBySyncId(businessId: String, syncId: String): Job?

    @Query("SELECT * FROM job WHERE business_id = :businessId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeAll(businessId: String): Flow<List<Job>>

    @Query("SELECT * FROM job WHERE business_id = :businessId AND status = :status AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByStatus(businessId: String, status: String): Flow<List<Job>>

    @Query("SELECT * FROM job WHERE business_id = :businessId AND clienteId = :clienteId AND deleted_at IS NULL ORDER BY created_at DESC")
    fun observeByCliente(businessId: String, clienteId: Long): Flow<List<Job>>

    @Query("SELECT * FROM job WHERE business_id = :businessId AND sync_status = 'PENDING' ORDER BY id")
    suspend fun getPendingSyncJobs(businessId: String): List<Job>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: Job): Long

    @Update
    suspend fun updateJob(job: Job)

    @Delete
    suspend fun deleteJob(job: Job)

    @Query("UPDATE job SET status = :status, ready_at = :readyAt, delivered_at = :deliveredAt, updated_at = :now, sync_status = 'PENDING' WHERE business_id = :businessId AND id = :id")
    suspend fun updateStatus(businessId: String, id: Long, status: String, readyAt: Long?, deliveredAt: Long?, now: Long)

    @Query("UPDATE job SET sale_id = :saleSyncId, status = 'delivered', delivered_at = :deliveredAt, updated_at = :now, sync_status = 'PENDING' WHERE business_id = :businessId AND id = :id")
    suspend fun linkSale(businessId: String, id: Long, saleSyncId: String, deliveredAt: Long, now: Long)

    // Items
    @Query("SELECT * FROM job_item WHERE business_id = :businessId AND jobId = :jobId ORDER BY id ASC")
    suspend fun getItemsForJob(businessId: String, jobId: Long): List<JobItem>

    @Query("SELECT * FROM job_item WHERE business_id = :businessId AND jobId = :jobId ORDER BY id ASC")
    fun observeItemsForJob(businessId: String, jobId: Long): Flow<List<JobItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobItem(item: JobItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobItems(items: List<JobItem>)

    @Update
    suspend fun updateJobItem(item: JobItem)

    @Query("DELETE FROM job_item WHERE jobId = :jobId")
    suspend fun deleteItemsForJob(jobId: Long)

    @Query("""
        UPDATE job
        SET sync_status = 'SYNCED', remote_updated_at = :remoteUpdatedAt
        WHERE business_id = :businessId AND sync_id = :syncId AND updated_at <= :localVersion
    """)
    suspend fun markSyncedBySyncId(businessId: String, syncId: String, localVersion: Long, remoteUpdatedAt: Long)
}
