package com.example.posapp.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

class ReceiptLogoProcessor(private val context: Context) {
    fun loadTicketLogo(path: String?): Bitmap? = runCatching {
        if (path.isNullOrBlank()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Logo no valido" }
        var sample = 1
        while (bounds.outWidth / sample > 800 || bounds.outHeight / sample > 800) sample *= 2
        val decoded = open(path).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("No se pudo leer el logo")
        val exif = open(path).use(::ExifInterface)
        val matrix = Matrix().apply {
            if (exif.isFlipped) postScale(-1f, 1f)
            if (exif.rotationDegrees != 0) postRotate(exif.rotationDegrees.toFloat())
        }
        val oriented = if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else decoded
        toHighContrastGrayscale(oriented)
    }.getOrNull()

    private fun open(path: String): InputStream {
        return if (path.startsWith("content://")) {
            context.contentResolver.openInputStream(Uri.parse(path))
                ?: error("No se pudo abrir el logo")
        } else {
            File(path).inputStream()
        }
    }

    private fun toHighContrastGrayscale(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val contrast = 1.25f
        val offset = (-0.5f * contrast + 0.5f) * 255f
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, offset,
                0f, contrast, 0f, 0f, offset,
                0f, 0f, contrast, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        Canvas(output).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        })
        source.recycle()
        return output
    }
}
