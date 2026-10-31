package fan.x0.focus.data.assistant

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.io.File

data class DataUsage(
    val conversations: Int = 0,
    val messages: Int = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val indexedFiles: Int = 0,
    val databaseBytes: Long = 0,
)

@Single
class DataUsageStore(private val database: AssistantDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun snapshot(): DataUsage = withContext(Dispatchers.IO) {
        val db = database.readableDatabase
        var input = 0L
        var output = 0L
        db.rawQuery("SELECT stats_json FROM messages WHERE stats_json IS NOT NULL", null).use { cursor ->
            while (cursor.moveToNext()) {
                val stats = runCatching {
                    json.decodeFromString(MessageStats.serializer(), cursor.getString(0))
                }.getOrNull() ?: continue
                input += stats.inputTokens
                output += stats.outputTokens
            }
        }
        DataUsage(
            conversations = count(db, "conversations"),
            messages = count(db, "messages"),
            inputTokens = input,
            outputTokens = output,
            indexedFiles = count(db, "file_index"),
            databaseBytes = databaseBytes(db),
        )
    }

    suspend fun clearConversations() = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("conversation_todos", null, null)
            db.delete("messages", null, null)
            db.delete("conversations", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun clearFileIndex() = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("file_index", null, null)
    }

    private fun count(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun databaseBytes(db: SQLiteDatabase): Long {
        val path = db.path?.takeIf { it.isNotBlank() } ?: return 0
        return listOf(path, "$path-wal", "$path-shm").sumOf { File(it).length() }
    }
}
