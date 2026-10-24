package fan.x0.focus.data.sources

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class FileRepository(
    @Provided private val context: Context,
    private val preferences: SourcePreferences,
) {
    suspend fun selectedTree(): String? = preferences.settings.first().fileTreeUri

    suspend fun attachTree(uri: String) = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        preferences.setFileTreeUri(uri)
    }

    suspend fun clearTree() {
        preferences.setFileTreeUri(null)
    }

    suspend fun treeName(): String? = withContext(Dispatchers.IO) {
        val uri = selectedTree() ?: return@withContext null
        DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name
    }

    suspend fun list(): List<SourceFile> = withContext(Dispatchers.IO) {
        val tree = tree()
        tree.listFiles()
            .map { file ->
                SourceFile(
                    uri = file.uri.toString(),
                    name = file.name.orEmpty(),
                    size = file.length(),
                    isDirectory = file.isDirectory,
                    lastModified = file.lastModified(),
                )
            }
            .sortedWith(compareByDescending<SourceFile> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun readText(uri: String, maxChars: Int = MAX_CHARS): String = withContext(Dispatchers.IO) {
        val document = document(uri)
        if (document.isDirectory) throw SourceException(SourceError.Unsupported, "uri is a folder")
        if (!isText(document)) throw SourceException(SourceError.Unsupported, "only text files can be read")
        val text = context.contentResolver.openInputStream(document.uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw SourceException(SourceError.File, "cannot open file")
        text.take(maxChars)
    }

    suspend fun writeText(uri: String, text: String) = withContext(Dispatchers.IO) {
        val document = document(uri)
        if (document.isDirectory) throw SourceException(SourceError.Unsupported, "uri is a folder")
        if (!isText(document)) throw SourceException(SourceError.Unsupported, "only text files can be written")
        val stream = context.contentResolver.openOutputStream(document.uri, "w")
            ?: throw SourceException(SourceError.File, "cannot write file")
        stream.use { it.write(text.toByteArray()) }
    }

    suspend fun createText(name: String, text: String): SourceFile = withContext(Dispatchers.IO) {
        val tree = tree()
        val safeName = name.trim().ifBlank { "note" }
        val mime = when {
            safeName.endsWith(".md", true) -> "text/markdown"
            safeName.endsWith(".json", true) -> "application/json"
            else -> "text/plain"
        }
        val created = tree.createFile(mime, safeName)
            ?: throw SourceException(SourceError.Storage, "cannot create file")
        val stream = context.contentResolver.openOutputStream(created.uri, "w")
            ?: throw SourceException(SourceError.Storage, "cannot write file")
        stream.use { it.write(text.toByteArray()) }
        SourceFile(
            uri = created.uri.toString(),
            name = created.name.orEmpty(),
            size = created.length(),
            isDirectory = false,
            lastModified = created.lastModified(),
        )
    }

    suspend fun delete(uri: String) = withContext(Dispatchers.IO) {
        val document = document(uri)
        if (!document.delete()) throw SourceException(SourceError.File, "cannot delete file")
    }

    private suspend fun tree(): DocumentFile {
        val uri = selectedTree()
            ?: throw SourceException(SourceError.PermissionDenied, "no folder selected")
        return DocumentFile.fromTreeUri(context, Uri.parse(uri))
            ?: throw SourceException(SourceError.PermissionDenied, "folder permission lost")
    }

    private fun document(uri: String): DocumentFile =
        DocumentFile.fromSingleUri(context, Uri.parse(uri))
            ?: DocumentFile.fromTreeUri(context, Uri.parse(uri))
            ?: throw SourceException(SourceError.NotFound, "file not found")

    private fun isText(document: DocumentFile): Boolean {
        val name = document.name.orEmpty()
        val mime = document.type.orEmpty()
        return mime.startsWith("text/") ||
            mime == "application/json" ||
            TEXT_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }
    }

    companion object {
        private const val MAX_CHARS = 60_000
        private val TEXT_EXTENSIONS = listOf(".txt", ".md", ".markdown", ".json", ".csv", ".log", ".yaml", ".yml")
    }
}
