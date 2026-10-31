package fan.x0.focus.data.assistant

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import fan.x0.focus.data.sources.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.util.Locale
import java.util.UUID

class MediaException(message: String) : Exception(message)

data class MediaRead(
    val kind: String,
    val name: String,
    val mime: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val frameCount: Int,
    val attachments: List<ChatAttachment>,
    val previews: List<ChatAttachment>,
    val note: String,
) {
    val summary: String
        get() = when (kind) {
            "image" -> "Read image $name"
            "video" -> "Read video $name (${attachments.size} ${if (attachments.size == 1) "frame" else "frames"})"
            else -> "Read $name"
        }
}

@Single
class MediaLoader(
    @Provided private val context: Context,
    private val files: FileRepository,
    private val attachments: AttachmentLoader,
) {
    suspend fun loadLocal(uri: String, frameCount: Int = DEFAULT_FRAMES): MediaRead =
        withContext(Dispatchers.IO) {
            val info = files.info(uri)
            if (info.isDirectory) throw MediaException("${info.name} is a folder")
            val mime = mimeOf(info.name, info.mime)
            when {
                mime.startsWith("image/") -> {
                    val blob = files.readBytes(uri, MAX_BYTES)
                    imageRead(blob.name, blob.mime, blob.bytes)
                }
                mime.startsWith("video/") -> videoLocal(uri, info.name, mime, frameCount)
                else -> throw MediaException("${info.name} is $mime, only images and videos can be read")
            }
        }

    suspend fun loadRemote(
        name: String,
        mime: String,
        base64: String,
        frameCount: Int = DEFAULT_FRAMES,
    ): MediaRead = withContext(Dispatchers.IO) {
        val bytes = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
            ?: throw MediaException("cannot decode media data for $name")
        val resolved = mimeOf(name, mime)
        when {
            resolved.startsWith("image/") -> imageRead(name, resolved, bytes)
            resolved.startsWith("video/") -> videoBytes(name, resolved, bytes, frameCount)
            else -> throw MediaException("$name is $resolved, only images and videos can be read")
        }
    }

    private fun imageRead(name: String, mime: String, bytes: ByteArray): MediaRead {
        val attachment = attachments.imageFromBytes(bytes, name)
            ?: throw MediaException("cannot decode image $name")
        val (width, height) = attachments.displaySize(bytes)
        val note = buildString {
            append("Image ").append(name)
            if (width > 0 && height > 0) {
                append(", ").append(width).append('x').append(height)
            }
            append(", ").append(formatSize(bytes.size.toLong()))
            append(". The image is attached to the conversation.")
        }
        return MediaRead(
            kind = "image",
            name = name,
            mime = attachment.mime,
            size = bytes.size.toLong(),
            width = width,
            height = height,
            durationMs = 0,
            frameCount = 1,
            attachments = listOf(attachment),
            previews = listOf(attachment),
            note = note,
        )
    }

    private suspend fun videoLocal(
        uri: String,
        name: String,
        mime: String,
        frameCount: Int,
    ): MediaRead {
        val retriever = MediaMetadataRetriever()
        try {
            val loaded = runCatching { retriever.setDataSource(context, Uri.parse(uri)) }.isSuccess
            if (loaded) return videoRead(retriever, name, mime, 0, frameCount)
        } finally {
            retriever.release()
        }
        val blob = files.readBytes(uri, MAX_BYTES)
        return videoBytes(blob.name, mimeOf(blob.name, blob.mime), blob.bytes, frameCount)
    }

    private fun videoBytes(name: String, mime: String, bytes: ByteArray, frameCount: Int): MediaRead {
        val extension = name.substringAfterLast('.', "")
            .takeIf { value -> value.isNotBlank() && value.all { it.isLetterOrDigit() } }
            ?: extensionOf(mime)
        val file = File(context.cacheDir, "media-${UUID.randomUUID()}.$extension")
        file.writeBytes(bytes)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            return videoRead(retriever, name, mime, bytes.size.toLong(), frameCount)
        } finally {
            retriever.release()
            file.delete()
        }
    }

    private fun videoRead(
        retriever: MediaMetadataRetriever,
        name: String,
        mime: String,
        size: Long,
        frameCount: Int,
    ): MediaRead {
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        val count = frameCount.coerceIn(1, MAX_FRAMES)
        val positions = if (duration > 0) {
            (0 until count).map { index -> duration * 1000L * (index + 1) / (count + 1) }
        } else {
            listOf(0L)
        }
        val frames = positions.mapIndexedNotNull { index, timeUs ->
            val bitmap = runCatching {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull() ?: runCatching {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            }.getOrNull() ?: return@mapIndexedNotNull null
            val encoded = runCatching { attachments.encodeJpeg(bitmap) }.getOrNull()
            bitmap.recycle()
            encoded ?: return@mapIndexedNotNull null
            ChatAttachment(
                id = UUID.randomUUID().toString(),
                kind = AttachmentKind.Image,
                name = "$name frame ${index + 1}",
                mime = encoded.first,
                base64 = encoded.second,
            )
        }.distinctBy { it.base64 }
        if (frames.isEmpty()) throw MediaException("cannot extract frames from $name")
        val note = buildString {
            append("Video ").append(name)
            if (width > 0 && height > 0) append(", ").append(width).append('x').append(height)
            if (duration > 0) {
                append(", ").append(String.format(Locale.US, "%.1f", duration / 1000.0)).append('s')
            }
            if (size > 0) append(", ").append(formatSize(size))
            append(", ").append(frames.size)
            append(if (frames.size == 1) " frame attached" else " frames attached")
            append(". Audio is not included.")
        }
        return MediaRead(
            kind = "video",
            name = name,
            mime = mime,
            size = size,
            width = width,
            height = height,
            durationMs = duration,
            frameCount = frames.size,
            attachments = frames,
            previews = frames,
            note = note,
        )
    }

    private fun mimeOf(name: String, declared: String): String {
        val value = declared.lowercase(Locale.US)
        if (value.startsWith("image/") || value.startsWith("video/")) return value
        return when (name.substringAfterLast('.', "").lowercase(Locale.US)) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            else -> value.ifBlank { "application/octet-stream" }
        }
    }

    private fun extensionOf(mime: String): String = when (mime) {
        "video/quicktime" -> "mov"
        "video/webm" -> "webm"
        "video/x-matroska" -> "mkv"
        "video/x-msvideo" -> "avi"
        "video/3gpp" -> "3gp"
        else -> "mp4"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
        const val DEFAULT_FRAMES = 4
        const val MAX_FRAMES = 6
    }
}
