package fan.x0.focus.data.assistant

import android.content.Context
import android.net.Uri
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI

@Single
class SkillTransfer(
    @Provided private val context: Context,
    private val http: HttpClient,
    private val archive: SkillArchive,
) {
    suspend fun previewFile(uri: String): List<SkillCandidate> = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val input = context.contentResolver.openInputStream(parsed) ?: error("Cannot open the selected file")
        val bytes = input.use { readStream(it, MAX_BYTES) }
        previewBytes(bytes)
    }

    suspend fun previewUrl(raw: String, onProgress: (Float?) -> Unit = {}): List<SkillCandidate> {
        val url = requestUrl(raw) ?: error("Only http or https links are supported")
        val response = http.get(url) {
            timeout {
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
        if (!response.status.isSuccess()) error("Download failed: ${response.status}")
        val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            ?.takeIf { it > 0 }
        if (length != null && length > MAX_BYTES) error("File is too large")
        val bytes = withContext(Dispatchers.IO) {
            readBody(response, length, onProgress)
        }
        return previewBytes(bytes)
    }

    suspend fun install(candidates: List<SkillCandidate>): SkillImportReport =
        archive.install(candidates)

    private suspend fun readBody(
        response: HttpResponse,
        total: Long?,
        onProgress: (Float?) -> Unit,
    ): ByteArray {
        val channel = response.bodyAsChannel()
        val out = ByteArrayOutputStream(total?.toInt() ?: 8192)
        val buffer = ByteArray(BUFFER_SIZE)
        var downloaded = 0L
        var lastPercent = -1
        onProgress(total?.let { 0f })
        while (true) {
            val read = channel.readAvailable(buffer)
            if (read == -1) break
            if (read == 0) continue
            downloaded += read
            if (downloaded > MAX_BYTES) error("File is too large")
            out.write(buffer, 0, read)
            if (total != null) {
                val percent = (downloaded * 100 / total).toInt().coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent / 100f)
                }
            }
        }
        if (total != null && downloaded != total) error("Download incomplete")
        onProgress(1f)
        return out.toByteArray()
    }

    suspend fun exportZip(): File = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "focus-skills.zip")
        file.writeBytes(archive.exportZip())
        file
    }

    private fun requestUrl(raw: String): String? {
        val url = raw.trim()
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        if (parsed.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = parsed.host?.lowercase() ?: return null
        return if (host == "github.com" || host == "www.github.com") {
            githubUrl(parsed) ?: url
        } else {
            url
        }
    }

    private fun githubUrl(uri: URI): String? {
        val path = uri.path.orEmpty()
        if (path.endsWith(".zip")) return uri.toString()
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        if (owner.isBlank() || repo.isBlank()) return null
        val action = parts.getOrNull(2)
        return when (action) {
            null -> "https://codeload.github.com/$owner/$repo/zip/HEAD"
            "blob", "raw" -> {
                if (parts.size < 5) return null
                val branch = parts[3]
                val file = parts.drop(4).joinToString("/")
                "https://raw.githubusercontent.com/$owner/$repo/$branch/$file"
            }
            "tree" -> {
                if (parts.size < 4) return null
                val branch = parts[3]
                "https://codeload.github.com/$owner/$repo/zip/refs/heads/$branch"
            }
            else -> null
        }
    }

    private suspend fun previewBytes(bytes: ByteArray): List<SkillCandidate> {
        return if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            archive.previewZip(bytes)
        } else {
            archive.previewMarkdown(bytes.toString(Charsets.UTF_8))
        }
    }

    private fun readStream(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > max) error("File is too large")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAX_BYTES = 4 * 1024 * 1024
        private const val BUFFER_SIZE = 16 * 1024
    }
}
