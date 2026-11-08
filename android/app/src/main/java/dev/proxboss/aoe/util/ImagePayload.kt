package dev.proxboss.aoe.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

data class ImageUploadPayload(
    val mimeType: String,
    val base64: String,
)

object ImagePayload {
    private const val MAX_RAW_BYTES = 25 * 1024 * 1024
    private const val MAX_OUTPUT_BYTES = 5 * 1024 * 1024
    private const val MAX_DIMENSION = 1_800
    private const val JPEG_QUALITY = 86

    fun prepare(context: Context, uri: Uri): ImageUploadPayload? =
        runCatching { build(context, uri) }.getOrNull()

    private fun build(context: Context, uri: Uri): ImageUploadPayload? {
        val resolver = context.contentResolver
        val originalMime = resolver.getType(uri) ?: "image/jpeg"
        val raw = runCatching {
            resolver.openInputStream(uri)?.use { stream -> stream.readBytes() }
        }.getOrNull() ?: return null
        if (raw.isEmpty() || raw.size > MAX_RAW_BYTES) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return if (raw.size <= MAX_OUTPUT_BYTES) {
                ImageUploadPayload(originalMime, Base64.encodeToString(raw, Base64.NO_WRAP))
            } else {
                null
            }
        }

        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(raw, 0, raw.size, options)
        }.getOrNull() ?: return null

        val encoded = compress(bitmap)
        bitmap.recycle()
        if (encoded == null || encoded.size > MAX_OUTPUT_BYTES) return null
        return ImageUploadPayload("image/jpeg", Base64.encodeToString(encoded, Base64.NO_WRAP))
    }

    private fun compress(bitmap: Bitmap): ByteArray? {
        var quality = JPEG_QUALITY
        var result: ByteArray?
        while (true) {
            val out = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) return null
            result = out.toByteArray()
            if (result.size <= MAX_OUTPUT_BYTES || quality <= 55) return result
            quality -= 10
        }
    }
}
