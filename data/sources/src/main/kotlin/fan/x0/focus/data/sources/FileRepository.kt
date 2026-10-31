package fan.x0.focus.data.sources

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream

data class SourceInfo(
    val name: String,
    val mime: String,
    val size: Long,
    val isDirectory: Boolean,
)

data class SourceBlob(
    val name: String,
    val mime: String,
    val size: Long,
    val bytes: ByteArray,
)

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

    suspend fun list(uri: String? = null): List<SourceFile> = withContext(Dispatchers.IO) {
        val root = treeUri()
        val target = uri?.let(Uri::parse)
        if (target == null) {
            queryChildren(root, DocumentsContract.getTreeDocumentId(root))
        } else {
            if (!isDirectory(target)) {
                throw SourceException(SourceError.Unsupported, "uri is not a folder")
            }
            if (target.authority != root.authority || treeIdOf(target) != DocumentsContract.getTreeDocumentId(root)) {
                throw SourceException(SourceError.PermissionDenied, "folder is outside the connected folder")
            }
            queryChildren(root, documentIdOf(target))
        }
    }

    suspend fun nameOf(uri: String): String? = withContext(Dispatchers.IO) {
        runCatching { document(uri).name }.getOrNull()
    }

    suspend fun info(uri: String): SourceInfo = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        ensureInTree(parsed)
        val document = document(uri)
        SourceInfo(
            name = document.name ?: parsed.lastPathSegment?.substringAfterLast('/') ?: "file",
            mime = document.type.orEmpty(),
            size = document.length(),
            isDirectory = isDirectory(document.uri),
        )
    }

    suspend fun readBytes(uri: String, maxBytes: Long): SourceBlob = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        ensureInTree(parsed)
        val document = document(uri)
        if (isDirectory(document.uri)) {
            throw SourceException(SourceError.Unsupported, "uri is a folder, use list_files with this uri")
        }
        val name = document.name ?: parsed.lastPathSegment?.substringAfterLast('/') ?: "file"
        val mime = document.type.orEmpty()
        val declared = document.length()
        if (declared > maxBytes) {
            throw SourceException(SourceError.Unsupported, "file is too large: $declared bytes, limit $maxBytes")
        }
        val stream = context.contentResolver.openInputStream(document.uri)
            ?: throw SourceException(SourceError.File, "cannot open file")
        val bytes = stream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw SourceException(SourceError.Unsupported, "file is too large, limit $maxBytes bytes")
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
        SourceBlob(name = name, mime = mime, size = bytes.size.toLong(), bytes = bytes)
    }

    suspend fun readText(uri: String, maxChars: Int = MAX_CHARS): String = withContext(Dispatchers.IO) {
        val document = document(uri)
        if (isDirectory(document.uri)) throw SourceException(SourceError.Unsupported, "uri is a folder, use list_files with this uri")
        if (!isText(document)) throw SourceException(SourceError.Unsupported, "only text files can be read")
        val text = context.contentResolver.openInputStream(document.uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: throw SourceException(SourceError.File, "cannot open file")
        text.take(maxChars)
    }

    suspend fun writeText(uri: String, text: String) = withContext(Dispatchers.IO) {
        val document = document(uri)
        if (isDirectory(document.uri)) throw SourceException(SourceError.Unsupported, "uri is a folder")
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
        return DocumentFile.fromTreeUri(context, treeUri())
            ?: throw SourceException(SourceError.PermissionDenied, "folder permission lost")
    }

    private suspend fun treeUri(): Uri {
        val uri = selectedTree()
            ?: throw SourceException(SourceError.PermissionDenied, "no folder selected")
        return Uri.parse(uri)
    }

    private fun document(uri: String): DocumentFile =
        DocumentFile.fromSingleUri(context, Uri.parse(uri))
            ?: DocumentFile.fromTreeUri(context, Uri.parse(uri))
            ?: throw SourceException(SourceError.NotFound, "file not found")

    private fun queryChildren(root: Uri, parentId: String): List<SourceFile> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(root, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val result = mutableListOf<SourceFile>()
        val cursor = context.contentResolver.query(children, projection, null, null, null)
            ?: throw SourceException(SourceError.File, "cannot list folder")
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                result += SourceFile(
                    uri = DocumentsContract.buildDocumentUriUsingTree(root, id).toString(),
                    name = it.getString(1).orEmpty(),
                    size = if (it.isNull(3)) 0L else it.getLong(3),
                    isDirectory = it.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    lastModified = if (it.isNull(4)) 0L else it.getLong(4),
                )
            }
        }
        return result.sortedWith(compareByDescending<SourceFile> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun isDirectory(uri: Uri): Boolean {
        val documentUri = if (DocumentsContract.isTreeUri(uri)) {
            DocumentsContract.buildDocumentUriUsingTree(uri, documentIdOf(uri))
        } else {
            uri
        }
        val projection = arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE)
        return runCatching {
            context.contentResolver.query(documentUri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR
            } ?: false
        }.getOrDefault(false)
    }

    private suspend fun ensureInTree(uri: Uri) {
        val root = treeUri()
        val rootId = DocumentsContract.getTreeDocumentId(root)
        val uriId = treeIdOf(uri)
        if (uri.authority != root.authority || uriId != rootId) {
            throw SourceException(SourceError.PermissionDenied, "file is outside the connected folder")
        }
    }

    private fun treeIdOf(uri: Uri): String? = runCatching {
        DocumentsContract.getTreeDocumentId(uri)
    }.getOrNull()

    private fun documentIdOf(uri: Uri): String =
        runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrElse { DocumentsContract.getTreeDocumentId(uri) }

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
