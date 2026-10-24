package fan.x0.focus.data.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.util.UUID

@Single
class AttachmentLoader(@Provided private val context: Context) {

    suspend fun load(uriValue: String): ChatAttachment? = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(uriValue) }.getOrNull() ?: return@withContext null
        val name = displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime.startsWith("image/")) {
            val compressed = readImage(uri) ?: return@withContext null
            ChatAttachment(
                id = UUID.randomUUID().toString(),
                kind = AttachmentKind.Image,
                name = name,
                mime = compressed.first,
                base64 = compressed.second,
            )
        } else {
            val text = readText(uri) ?: return@withContext null
            ChatAttachment(
                id = UUID.randomUUID().toString(),
                kind = AttachmentKind.Text,
                name = name,
                mime = mime.ifBlank { "text/plain" },
                text = text,
            )
        }
    }

    private fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > MAX_TEXT_BYTES) return@use null
            if (!isProbablyText(bytes)) return@use null
            String(bytes, Charsets.UTF_8)
        }
    }.getOrNull()

    private fun isProbablyText(bytes: ByteArray): Boolean {
        val sample = bytes.take(512)
        return sample.none { it == 0.toByte() }
    }

    private fun readImage(uri: Uri): Pair<String, String>? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@runCatching null
        val scaled = scale(decoded)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        "image/jpeg" to Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > MAX_EDGE * 2 && height / sample > MAX_EDGE * 2) {
            sample *= 2
        }
        return sample
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_EDGE) return bitmap
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()

    companion object {
        private const val MAX_EDGE = 1024
        private const val JPEG_QUALITY = 80
        private const val MAX_TEXT_BYTES = 512 * 1024
    }
}
