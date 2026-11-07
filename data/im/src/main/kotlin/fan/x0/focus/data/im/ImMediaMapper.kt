package fan.x0.focus.data.im

import android.util.Base64
import fan.x0.focus.data.assistant.AttachmentKind
import fan.x0.focus.data.assistant.AttachmentLoader
import fan.x0.focus.data.assistant.ChatAttachment
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class ImMediaMapper(private val loader: AttachmentLoader) {

    fun messageLine(message: ImInbound): String {
        val media = message.attachments.joinToString(" ") {
            if (it.kind == ImAttachmentKind.Image) "[图片: ${it.name}]" else "[文件: ${it.name}]"
        }
        return listOf(message.text.trim(), media)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    suspend fun toChatAttachment(media: ImAttachment): ChatAttachment? {
        if (media.bytes.isEmpty()) return null
        return when (media.kind) {
            ImAttachmentKind.Image -> loader.imageFromBytes(media.bytes, media.name)
            ImAttachmentKind.File -> {
                val text = textContent(media.name, media.mime, media.bytes) ?: return null
                ChatAttachment(
                    id = UUID.randomUUID().toString(),
                    kind = AttachmentKind.Text,
                    name = media.name,
                    mime = media.mime.ifBlank { "text/plain" },
                    text = text,
                )
            }
        }
    }

    fun toImAttachment(attachment: ChatAttachment): ImAttachment? = when (attachment.kind) {
        AttachmentKind.Image -> {
            val data = attachment.base64?.let {
                runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull()
            } ?: return null
            if (data.isEmpty()) return null
            ImAttachment(
                kind = ImAttachmentKind.Image,
                name = attachment.name.ifBlank { "image.jpg" },
                mime = attachment.mime.ifBlank { "image/jpeg" },
                bytes = data,
            )
        }
        AttachmentKind.Text -> {
            val text = attachment.text ?: return null
            ImAttachment(
                kind = ImAttachmentKind.File,
                name = attachment.name.ifBlank { "file.txt" },
                mime = attachment.mime.ifBlank { "text/plain" },
                bytes = text.toByteArray(Charsets.UTF_8),
            )
        }
    }

    private fun textContent(name: String, mime: String, bytes: ByteArray): String? {
        if (bytes.size > MAX_TEXT_ATTACHMENT_BYTES) return null
        val ext = name.substringAfterLast('.', "").lowercase()
        if (!mime.startsWith("text/") && ext !in TEXT_EXTENSIONS) return null
        if (bytes.take(512).any { it == 0.toByte() }) return null
        return runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
    }

    companion object {
        private const val MAX_TEXT_ATTACHMENT_BYTES = 512 * 1024
        private val TEXT_EXTENSIONS = setOf(
            "txt",
            "md",
            "json",
            "xml",
            "csv",
            "log",
            "kt",
            "java",
            "js",
            "ts",
            "py",
            "rs",
            "go",
            "html",
            "css",
            "yml",
            "yaml",
            "toml",
            "ini",
        )
    }
}
