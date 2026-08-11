package com.example.posapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageUtils {
    // Save an image from a URI, downscale and compress to target size (bytes).
    // Returns absolute path.
    fun saveOptimizedImage(context: Context, uri: Uri, maxDim: Int = 800, targetBytes: Int = 50 * 1024): String {
        val resolver = context.contentResolver
        val input = resolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open URI")
        val original = BitmapFactory.decodeStream(input)
        input.close()

        val scaled = downscaleIfNeeded(original, maxDim)

        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()

        val outFile = File(imagesDir, "img_${System.currentTimeMillis()}.jpg")
        // compress to target size by reducing quality
        var quality = 90
        var lastSize = -1
        while (quality >= 20) {
            val baos = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val bytes = baos.toByteArray()
            if (bytes.size <= targetBytes || bytes.size == lastSize) {
                FileOutputStream(outFile).use { it.write(bytes) }
                break
            }
            lastSize = bytes.size
            quality -= 10
        }

        if (scaled != original) original.recycle()

        return outFile.absolutePath
    }

    // Save a Bitmap (from camera preview) into app internal storage, compress to target size.
    fun saveBitmap(context: Context, bitmap: Bitmap, maxDim: Int = 1200, targetBytes: Int = 50 * 1024): String {
        val scaled = downscaleIfNeeded(bitmap, maxDim)

        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) imagesDir.mkdirs()

        val outFile = File(imagesDir, "img_${System.currentTimeMillis()}.jpg")

        var quality = 90
        var lastSize = -1
        while (quality >= 20) {
            val baos = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val bytes = baos.toByteArray()
            if (bytes.size <= targetBytes || bytes.size == lastSize) {
                FileOutputStream(outFile).use { it.write(bytes) }
                break
            }
            lastSize = bytes.size
            quality -= 10
        }

        if (scaled != bitmap) bitmap.recycle()

        return outFile.absolutePath
    }

    private fun downscaleIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val (w, h) = bitmap.width to bitmap.height
        val scale = if (w > h) maxDim.toFloat() / w else maxDim.toFloat() / h
        return if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true) else bitmap
    }
}
