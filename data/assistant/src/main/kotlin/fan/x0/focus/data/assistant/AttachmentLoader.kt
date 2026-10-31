package fan.x0.focus.data.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayInputStream
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

    fun imageFromBytes(bytes: ByteArray, name: String): ChatAttachment? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val oriented = applyOrientation(decoded, orientationOf(bytes))
        val encoded = encodeJpeg(oriented)
        if (oriented !== decoded) decoded.recycle()
        oriented.recycle()
        return ChatAttachment(
            id = UUID.randomUUID().toString(),
            kind = AttachmentKind.Image,
            name = name,
            mime = encoded.first,
            base64 = encoded.second,
        )
    }

    internal fun encodeJpeg(bitmap: Bitmap): Pair<String, String> {
        val scaled = scale(bitmap)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (scaled !== bitmap) scaled.recycle()
        return "image/jpeg" to Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
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
        val oriented = applyOrientation(decoded, orientationOf(uri))
        val encoded = encodeJpeg(oriented)
        if (oriented !== decoded) decoded.recycle()
        oriented.recycle()
        encoded
    }.getOrNull()

    internal fun displaySize(bytes: ByteArray): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return 0 to 0
        return if (isQuarterTurn(orientationOf(bytes))) {
            bounds.outHeight to bounds.outWidth
        } else {
            bounds.outWidth to bounds.outHeight
        }
    }

    private fun orientationOf(bytes: ByteArray): Int = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun orientationOf(uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    private fun isQuarterTurn(orientation: Int): Boolean = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE,
        -> true
        else -> false
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun sampleSize(width: Int, height: Int, target: Int = MAX_EDGE): Int {
        var sample = 1
        while (width / sample > target * 2 && height / sample > target * 2) {
            sample *= 2
        }
        return sample
    }

    private fun scale(bitmap: Bitmap): Bitmap = scaleTo(bitmap, MAX_EDGE)

    private fun scaleTo(bitmap: Bitmap, target: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= target) return bitmap
        val ratio = target.toFloat() / longest
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
