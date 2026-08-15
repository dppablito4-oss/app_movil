package com.example.posapp.data.sync

import android.content.Context
import com.example.posapp.data.AppDatabase
import com.example.posapp.data.entities.ImageSyncStatus
import com.example.posapp.data.remote.SupabaseProvider
import com.example.posapp.utils.ImageUtils
import io.github.jan.supabase.storage.storage
import java.io.File

/** Descarga fotos solo cuando una pantalla realmente las necesita. */
class ProductImageCache(context: Context) {
    private val appContext = context.applicationContext
    private val products = AppDatabase.getInstance(appContext).productoDao()

    suspend fun ensureCached(businessId: String, productId: Long): String? {
        val product = products.getById(businessId, productId) ?: return null
        product.ruta_imagen?.takeIf { File(it).isFile }?.let { return it }
        val syncId = product.sync_id ?: return null
        val storagePath = product.storage_path?.takeIf(String::isNotBlank) ?: return null
        if (!SupabaseProvider.isConfigured) return null

        products.updateImageStatus(businessId, syncId, ImageSyncStatus.DOWNLOAD_PENDING)
        return runCatching {
            val bytes = SupabaseProvider.client.storage.from("product-images")
                .downloadAuthenticated(storagePath)
            val localPath = ImageUtils.saveRemoteImage(appContext, syncId, bytes)
            products.setCachedImage(businessId, syncId, localPath)
            ImageUtils.trimRemoteImageCache(appContext)
            localPath
        }.onFailure {
            products.updateImageStatus(businessId, syncId, ImageSyncStatus.ERROR_RETRYABLE)
        }.getOrNull()
    }
}
