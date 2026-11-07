package fan.x0.focus.data.device

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

object DeviceImage {
    fun encode(bitmap: Bitmap, maxEdge: Int = 1024, quality: Int = 80): String {
        val scaled = scale(bitmap, maxEdge)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bitmap) scaled.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun encodeBytes(bytes: ByteArray, maxEdge: Int = 1024, quality: Int = 80): String? {
        val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sample = 1
        while (options.outWidth / sample > maxEdge * 2 && options.outHeight / sample > maxEdge * 2) {
            sample *= 2
        }
        val decodeOptions = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: return null
        val encoded = encode(bitmap, maxEdge, quality)
        bitmap.recycle()
        return encoded
    }

    private fun scale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}
