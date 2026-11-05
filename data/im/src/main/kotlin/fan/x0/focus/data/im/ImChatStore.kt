package fan.x0.focus.data.im

import android.content.ContentValues
import android.database.Cursor
import fan.x0.focus.data.assistant.AssistantDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class ImChatStore(private val database: AssistantDatabase) {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    suspend fun list(): List<ImChat> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(QUERY, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toChat())
            }
        }
    }

    suspend fun chatById(id: String): ImChat? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery("$QUERY WHERE c.id = ? LIMIT 1", arrayOf(id))
            .use { cursor -> if (cursor.moveToFirst()) cursor.toChat() else null }
    }

    suspend fun bindingForConversation(conversationId: String): ImChat? =
        withContext(Dispatchers.IO) {
            database.readableDatabase.rawQuery(
                "$QUERY WHERE c.conversation_id = ? AND c.enabled = 1 " +
                    "ORDER BY c.last_message_at DESC LIMIT 1",
                arrayOf(conversationId),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.toChat() else null }
        }

    suspend fun ensure(account: ImAccount, ref: ImChatRef, sentAt: Long): ImChat =
        withContext(Dispatchers.IO) {
            val db = database.writableDatabase
            val now = System.currentTimeMillis()
            val existing = find(account.id, ref.externalId, ref.kind)
            if (existing == null) {
                db.insertOrThrow(
                    "im_chats",
                    null,
                    ContentValues().apply {
                        put("id", UUID.randomUUID().toString())
                        put("account_id", account.id)
                        put("external_id", ref.externalId)
                        put("kind", ref.kind.name)
                        put("title", ref.title.take(120))
                        put("enabled", 1)
                        put("platform_context", "")
                        put("last_message_at", if (sentAt > 0) sentAt else now)
                        put("created_at", now)
                        put("updated_at", now)
                    },
                )
            } else {
                db.update(
                    "im_chats",
                    ContentValues().apply {
                        put("title", ref.title.take(120))
                        put("last_message_at", if (sentAt > 0) sentAt else now)
                        put("updated_at", now)
                    },
                    "id = ?",
                    arrayOf(existing.id),
                )
            }
            _changes.tryEmit(Unit)
            find(account.id, ref.externalId, ref.kind) ?: error("im chat missing")
        }

    suspend fun setConversation(id: String, conversationId: String?) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("conversation_id", conversationId)
                put("updated_at", System.currentTimeMillis())
            }
            database.writableDatabase.update("im_chats", values, "id = ?", arrayOf(id))
            _changes.tryEmit(Unit)
        }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("im_chats", values, "id = ?", arrayOf(id))
        _changes.tryEmit(Unit)
    }

    suspend fun setSendThinking(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        updateFlag(id, "send_thinking", enabled)
    }

    suspend fun setSendTools(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        updateFlag(id, "send_tools", enabled)
    }

    private fun updateFlag(id: String, column: String, enabled: Boolean) {
        val values = ContentValues().apply {
            put(column, if (enabled) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("im_chats", values, "id = ?", arrayOf(id))
        _changes.tryEmit(Unit)
    }

    suspend fun setContext(id: String, context: String) = withContext(Dispatchers.IO) {
        if (context.isBlank()) return@withContext
        val values = ContentValues().apply {
            put("platform_context", context)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("im_chats", values, "id = ?", arrayOf(id))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("im_chats", "id = ?", arrayOf(id))
        _changes.tryEmit(Unit)
    }

    private fun find(accountId: String, externalId: String, kind: ImChatKind): ImChat? =
        database.readableDatabase.rawQuery(
            "$QUERY WHERE c.account_id = ? AND c.external_id = ? AND c.kind = ? LIMIT 1",
            arrayOf(accountId, externalId, kind.name),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toChat() else null }

    private fun Cursor.toChat(): ImChat = ImChat(
        id = getString(0),
        accountId = getString(1),
        accountName = getString(2),
        channel = getString(3),
        externalId = getString(4),
        kind = runCatching { ImChatKind.valueOf(getString(5)) }.getOrDefault(ImChatKind.Direct),
        title = getString(6).orEmpty(),
        conversationId = if (isNull(7)) null else getString(7),
        conversationTitle = if (isNull(8)) null else getString(8),
        enabled = getInt(9) == 1,
        sendThinking = getInt(10) == 1,
        sendTools = getInt(11) == 1,
        platformContext = getString(12).orEmpty(),
        lastMessageAt = getLong(13),
    )

    companion object {
        private const val QUERY =
            "SELECT c.id, c.account_id, a.name, a.channel, c.external_id, c.kind, c.title, " +
                "c.conversation_id, conv.title, c.enabled, c.send_thinking, c.send_tools, " +
                "c.platform_context, c.last_message_at " +
                "FROM im_chats c " +
                "JOIN im_accounts a ON a.id = c.account_id " +
                "LEFT JOIN conversations conv ON conv.id = c.conversation_id"
    }
}
