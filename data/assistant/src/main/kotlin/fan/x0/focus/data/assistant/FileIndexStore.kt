package fan.x0.focus.data.assistant

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

data class IndexedFile(
    val root: String,
    val path: String,
    val size: Long,
    val modifiedAt: Long,
    val contentHash: String,
    val content: String,
    val language: String?,
    val summary: String?,
    val indexedAt: Long,
)

data class FileSearchHit(
    val path: String,
    val line: Int,
    val text: String,
)

@Single
class FileIndexStore(private val database: AssistantDatabase) {
    suspend fun all(root: String? = null): List<IndexedFile> = withContext(Dispatchers.IO) {
        val selection = root?.let { "root = ?" }
        database.readableDatabase.query(
            "file_index",
            arrayOf("root", "path", "size", "modified_at", "content_hash", "content", "language", "summary", "indexed_at"),
            selection,
            root?.let { arrayOf(it) },
            null,
            null,
            "path",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        IndexedFile(
                            root = cursor.getString(0),
                            path = cursor.getString(1),
                            size = cursor.getLong(2),
                            modifiedAt = cursor.getLong(3),
                            contentHash = cursor.getString(4),
                            content = cursor.getString(5),
                            language = if (cursor.isNull(6)) null else cursor.getString(6),
                            summary = if (cursor.isNull(7)) null else cursor.getString(7),
                            indexedAt = cursor.getLong(8),
                        ),
                    )
                }
            }
        }
    }

    suspend fun existing(root: String): Map<String, IndexedFile> =
        all(root).associateBy { it.path }

    suspend fun replace(root: String, files: List<IndexedFile>) = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("file_index", "root = ?", arrayOf(root))
            files.forEach { file ->
                val values = ContentValues().apply {
                    put("root", file.root)
                    put("path", file.path)
                    put("size", file.size)
                    put("modified_at", file.modifiedAt)
                    put("content_hash", file.contentHash)
                    put("content", file.content)
                    put("language", file.language)
                    put("summary", file.summary)
                    put("indexed_at", file.indexedAt)
                }
                db.insertWithOnConflict("file_index", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun clear(root: String? = null) = withContext(Dispatchers.IO) {
        if (root == null) {
            database.writableDatabase.delete("file_index", null, null)
        } else {
            database.writableDatabase.delete("file_index", "root = ?", arrayOf(root))
        }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery("SELECT COUNT(*) FROM file_index", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }
}
