package fan.x0.focus.data.assistant

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class ChatStore(private val database: AssistantDatabase) {
    private val json = Json { ignoreUnknownKeys = true }
    private val callsSerializer = ListSerializer(ToolCallRecord.serializer())
    private val attachmentsSerializer = ListSerializer(ChatAttachment.serializer())

    suspend fun conversations(archived: Boolean = false): List<Conversation> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT c.id, c.title, c.created_at, c.updated_at, c.archived, c.provider_id, c.model, c.reasoning " +
                "FROM conversations c " +
                "WHERE c.archived = ? " +
                "AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = c.id) " +
                "ORDER BY c.updated_at DESC",
            arrayOf(if (archived) "1" else "0"),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toConversation())
                }
            }
        }
    }

    suspend fun conversation(id: String): Conversation? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, title, created_at, updated_at, archived, provider_id, model, reasoning " +
                "FROM conversations WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toConversation() else null
        }
    }

    suspend fun ensureConversation(): Conversation = withContext(Dispatchers.IO) {
        val existing = database.readableDatabase.rawQuery(
            "SELECT id, title, created_at, updated_at, archived, provider_id, model, reasoning " +
                "FROM conversations WHERE archived = 0 ORDER BY updated_at DESC LIMIT 1",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toConversation() else null
        }
        existing ?: createConversation("")
    }

    suspend fun createConversation(title: String): Conversation = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val conversation = Conversation(UUID.randomUUID().toString(), title, now, now)
        val values = ContentValues().apply {
            put("id", conversation.id)
            put("title", conversation.title)
            put("created_at", conversation.createdAt)
            put("updated_at", conversation.updatedAt)
        }
        database.writableDatabase.insertOrThrow("conversations", null, values)
        conversation
    }

    suspend fun rename(id: String, title: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("title", title.take(80)) }
        database.writableDatabase.update("conversations", values, "id = ?", arrayOf(id))
    }

    suspend fun setProvider(id: String, providerId: String, model: String, reasoning: String) =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("provider_id", providerId)
                put("model", model)
                put("reasoning", reasoning)
            }
            database.writableDatabase.update("conversations", values, "id = ?", arrayOf(id))
        }

    suspend fun archive(id: String) = withContext(Dispatchers.IO) {
        setArchived(id, archived = true)
    }

    suspend fun restore(id: String) = withContext(Dispatchers.IO) {
        setArchived(id, archived = false)
    }

    private fun setArchived(id: String, archived: Boolean) {
        val values = ContentValues().apply { put("archived", if (archived) 1 else 0) }
        database.writableDatabase.update("conversations", values, "id = ?", arrayOf(id))
    }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("messages", "conversation_id = ?", arrayOf(id))
            db.delete("conversations", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun messages(conversationId: String, limit: Int = 80): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            database.readableDatabase.rawQuery(
                """
                SELECT id, conversation_id, role, content, reasoning, tool_calls, tool_call_id, name, created_at, attachments, label
                FROM messages WHERE conversation_id = ? ORDER BY id DESC LIMIT ?
                """.trimIndent(),
                arrayOf(conversationId, limit.toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toMessage())
                }.reversed()
            }
        }

    suspend fun append(
        conversationId: String,
        role: ChatRole,
        content: String,
        reasoning: String = "",
        toolCalls: List<ToolCallRecord> = emptyList(),
        toolCallId: String? = null,
        name: String? = null,
        attachments: List<ChatAttachment> = emptyList(),
        label: String? = null,
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("conversation_id", conversationId)
            put("role", role.name.lowercase())
            put("content", content)
            put("reasoning", reasoning)
            put("tool_calls", if (toolCalls.isEmpty()) null else json.encodeToString(callsSerializer, toolCalls))
            put("tool_call_id", toolCallId)
            put("name", name)
            put("created_at", now)
            put(
                "attachments",
                if (attachments.isEmpty()) null else json.encodeToString(attachmentsSerializer, attachments),
            )
            put("label", label)
        }
        val id = database.writableDatabase.insertOrThrow("messages", null, values)
        val touch = ContentValues().apply { put("updated_at", now) }
        database.writableDatabase.update("conversations", touch, "id = ?", arrayOf(conversationId))
        id
    }

    suspend fun userTurnCount(conversationId: String): Int = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND role = 'user'",
            arrayOf(conversationId),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    suspend fun clear(conversationId: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("messages", "conversation_id = ?", arrayOf(conversationId))
    }

    private fun Cursor.toConversation(): Conversation = Conversation(
        id = getString(0),
        title = getString(1),
        createdAt = getLong(2),
        updatedAt = getLong(3),
        archived = getInt(4) != 0,
        providerId = getString(5).orEmpty(),
        model = getString(6).orEmpty(),
        reasoning = getString(7).orEmpty(),
    )

    private fun Cursor.toMessage(): ChatMessage {
        val roleValue = getString(2)
        val rawCalls = if (isNull(5)) null else getString(5)
        val calls = rawCalls?.let {
            runCatching { json.decodeFromString(callsSerializer, it) }.getOrDefault(emptyList())
        }.orEmpty()
        val rawAttachments = if (isNull(9)) null else getString(9)
        val attachments = rawAttachments?.let {
            runCatching { json.decodeFromString(attachmentsSerializer, it) }.getOrDefault(emptyList())
        }.orEmpty()
        return ChatMessage(
            id = getLong(0),
            conversationId = getString(1),
            role = when (roleValue) {
                "user" -> ChatRole.User
                "assistant" -> ChatRole.Assistant
                "tool" -> ChatRole.Tool
                else -> ChatRole.System
            },
            content = getString(3).orEmpty(),
            reasoning = getString(4).orEmpty(),
            toolCalls = calls,
            toolCallId = if (isNull(6)) null else getString(6),
            name = if (isNull(7)) null else getString(7),
            createdAt = getLong(8),
            attachments = attachments,
            label = if (isNull(10)) null else getString(10),
        )
    }
}
