package fan.x0.focus.data.assistant

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

data class ThreadSubscription(
    val threadKey: String,
    val conversationId: String,
    val reason: String,
    val createdAt: Long,
)

@Single
class ThreadRegistry(private val database: AssistantDatabase) {

    suspend fun subscribe(threadKey: String, conversationId: String, reason: String = "") =
        withContext(Dispatchers.IO) {
            if (threadKey.isBlank() || conversationId.isBlank()) return@withContext
            val values = ContentValues().apply {
                put("thread_key", threadKey)
                put("conversation_id", conversationId)
                put("reason", reason.take(200))
                put("created_at", System.currentTimeMillis())
            }
            database.writableDatabase.insertWithOnConflict(
                "thread_subscriptions",
                null,
                values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
            )
        }

    suspend fun unsubscribe(threadKey: String, conversationId: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete(
            "thread_subscriptions",
            "thread_key = ? AND conversation_id = ?",
            arrayOf(threadKey, conversationId),
        )
    }

    suspend fun clearConversation(conversationId: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete(
            "thread_subscriptions",
            "conversation_id = ?",
            arrayOf(conversationId),
        )
    }

    suspend fun subscribers(threadKey: String): List<ThreadSubscription> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT thread_key, conversation_id, reason, created_at FROM thread_subscriptions " +
                "WHERE thread_key = ? ORDER BY created_at",
            arrayOf(threadKey),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ThreadSubscription(
                            threadKey = cursor.getString(0),
                            conversationId = cursor.getString(1),
                            reason = cursor.getString(2).orEmpty(),
                            createdAt = cursor.getLong(3),
                        ),
                    )
                }
            }
        }
    }

    suspend fun subscriptions(conversationId: String): List<ThreadSubscription> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT thread_key, conversation_id, reason, created_at FROM thread_subscriptions " +
                "WHERE conversation_id = ? ORDER BY created_at",
            arrayOf(conversationId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ThreadSubscription(
                            threadKey = cursor.getString(0),
                            conversationId = cursor.getString(1),
                            reason = cursor.getString(2).orEmpty(),
                            createdAt = cursor.getLong(3),
                        ),
                    )
                }
            }
        }
    }
}
