package fan.x0.focus.data.assistant

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import fan.x0.focus.core.error.StorageException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.util.UUID
import kotlin.math.max

@Single
class CrewAvatarStore(@Provided private val context: Context) {
    private val folder: File
        get() = File(context.filesDir, FOLDER).apply { mkdirs() }
    private val paths = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun path(name: String): String? {
        if (name.isBlank()) return null
        paths[name]?.let { return it }
        val file = File(folder, name)
        return if (file.isFile) file.absolutePath.also { paths[name] = it } else null
    }

    suspend fun import(uri: String, id: String): String = withContext(Dispatchers.IO) {
        try {
            val source = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: error("cannot open image")
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("cannot read image")
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("cannot decode image")
            val orientation = context.contentResolver.openInputStream(source)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
            val oriented = applyOrientation(decoded, orientation)
            val scaled = scaleDown(oriented)
            val name = "${id.take(24)}-${UUID.randomUUID()}.jpg"
            folder.mkdirs()
            File(folder, name).outputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, output)
            }
            if (scaled !== decoded) {
                if (oriented !== decoded && oriented !== scaled) oriented.recycle()
                decoded.recycle()
            }
            name
        } catch (e: Exception) {
            throw StorageException(e)
        }
    }

    suspend fun delete(name: String) = withContext(Dispatchers.IO) {
        if (name.isBlank()) return@withContext
        File(folder, name).delete()
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width, height) / sample > MAX_SIZE * 2) sample *= 2
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= MAX_SIZE) return bitmap
        val scale = MAX_SIZE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val FOLDER = "crew_avatars"
        private const val MAX_SIZE = 512
    }
}
