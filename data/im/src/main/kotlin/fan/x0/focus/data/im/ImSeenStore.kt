package fan.x0.focus.data.im

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import fan.x0.focus.data.assistant.AssistantDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class ImSeenStore(private val database: AssistantDatabase) {
    suspend fun mark(accountId: String, messageId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (messageId.isBlank()) return@withContext true
            val values = ContentValues().apply {
                put("account_id", accountId)
                put("message_id", messageId)
                put("seen_at", System.currentTimeMillis())
            }
            val row = database.writableDatabase.insertWithOnConflict(
                "im_seen",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            row != -1L
        }

    suspend fun prune(before: Long) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("im_seen", "seen_at < ?", arrayOf(before.toString()))
    }
}
