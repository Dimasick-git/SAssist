package dev.ryazha.sassist.net

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class OptimizedImage(val bytes: ByteArray, val mime: String, val name: String)

/** Downscales camera photos before network transfer; avoids changing animated or transparent images. */
object MediaPreprocessor {
    private const val MAX_EDGE = 1920
    private const val TARGET_BYTES = 4_500_000

    fun optimizePhoto(resolver: ContentResolver, uri: Uri, mime: String, name: String): OptimizedImage? {
        if (mime.equals("image/gif", true) || mime.equals("image/svg+xml", true)) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth / sample, bounds.outHeight / sample) > MAX_EDGE * 2) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            if (decoded.hasAlpha()) { decoded.recycle(); return null }
            val largest = max(decoded.width, decoded.height)
            val scaled = if (largest > MAX_EDGE) {
                val ratio = MAX_EDGE.toFloat() / largest
                Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt().coerceAtLeast(1), (decoded.height * ratio).toInt().coerceAtLeast(1), true)
                    .also { if (it !== decoded) decoded.recycle() }
            } else decoded
            var quality = 84
            var bytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                bytes = out.toByteArray()
                quality -= 10
            } while (bytes.size > TARGET_BYTES && quality >= 54)
            scaled.recycle()
            val jpegName = name.substringBeforeLast('.', name).ifBlank { "photo" } + ".jpg"
            OptimizedImage(bytes, "image/jpeg", jpegName)
        } catch (_: Exception) { null }
    }
}
