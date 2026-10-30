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
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val callsSerializer = ListSerializer(ToolCallRecord.serializer())
    private val attachmentsSerializer = ListSerializer(ChatAttachment.serializer())

    suspend fun conversations(archived: Boolean = false): List<Conversation> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT c.id, c.title, c.created_at, c.updated_at, c.archived, c.provider_id, c.model, " +
                "c.reasoning, c.mode, c.plan_json, c.plan_status, c.plan_request_id, c.variant " +
                "FROM conversations c " +
                "WHERE c.archived = ? " +
                "AND EXISTS (SELECT 1 FROM messages m WHERE m.conversation_id = c.id) " +
                "ORDER BY c.updated_at DESC",
            arrayOf(if (archived) "1" else "0"),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toConversation())
            }
        }
    }

    suspend fun conversation(id: String): Conversation? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, title, created_at, updated_at, archived, provider_id, model, reasoning, " +
                "mode, plan_json, plan_status, plan_request_id, variant FROM conversations WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toConversation() else null
        }
    }

    suspend fun ensureConversation(): Conversation = withContext(Dispatchers.IO) {
        val existing = database.readableDatabase.rawQuery(
            "SELECT id, title, created_at, updated_at, archived, provider_id, model, reasoning, " +
                "mode, plan_json, plan_status, plan_request_id, variant FROM conversations WHERE archived = 0 " +
                "ORDER BY updated_at DESC LIMIT 1",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toConversation() else null
        }
        existing ?: createConversation("")
    }

    suspend fun createConversation(title: String, mode: AgentMode = AgentMode.Build): Conversation =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val conversation = Conversation(UUID.randomUUID().toString(), title, now, now, mode = mode)
            val values = ContentValues().apply {
                put("id", conversation.id)
                put("title", conversation.title)
                put("created_at", conversation.createdAt)
                put("updated_at", conversation.updatedAt)
                put("mode", mode.wire)
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

    suspend fun setMode(id: String, mode: AgentMode) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("mode", mode.wire) }
        database.writableDatabase.update("conversations", values, "id = ?", arrayOf(id))
    }

    suspend fun setVariant(id: String, variant: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("variant", variant.take(80)) }
        database.writableDatabase.update("conversations", values, "id = ?", arrayOf(id))
    }

    suspend fun setPlan(
        id: String,
        plan: ProposedPlan?,
        status: PlanStatus,
        requestId: String = "",
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("plan_json", plan?.let { json.encodeToString(ProposedPlan.serializer(), it) })
            put("plan_status", status.wire)
            put("plan_request_id", requestId)
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
            db.delete("conversation_todos", "conversation_id = ?", arrayOf(id))
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
                "SELECT $MESSAGE_COLUMNS FROM messages WHERE conversation_id = ? " +
                    "ORDER BY id DESC LIMIT ?",
                arrayOf(conversationId, limit.toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toMessage())
                }.reversed()
            }
        }

    suspend fun message(id: Long): ChatMessage? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $MESSAGE_COLUMNS FROM messages WHERE id = ? LIMIT 1",
            arrayOf(id.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toMessage() else null }
    }

    suspend fun messagesByIds(ids: Collection<Long>): Map<Long, ChatMessage> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val placeholders = ids.joinToString(",") { "?" }
        database.readableDatabase.rawQuery(
            "SELECT $MESSAGE_COLUMNS FROM messages WHERE id IN ($placeholders)",
            ids.map { it.toString() }.toTypedArray(),
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val message = cursor.toMessage()
                    put(message.id, message)
                }
            }
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
        replyToId: Long? = null,
        stats: MessageStats? = null,
        reaction: String? = null,
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
            put("reply_to_id", replyToId)
            put("stats_json", stats?.let { json.encodeToString(MessageStats.serializer(), it) })
            put("reaction", reaction)
        }
        val id = database.writableDatabase.insertOrThrow("messages", null, values)
        val touch = ContentValues().apply { put("updated_at", now) }
        database.writableDatabase.update("conversations", touch, "id = ?", arrayOf(conversationId))
        id
    }

    suspend fun updateContent(id: Long, content: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("content", content) }
        database.writableDatabase.update("messages", values, "id = ?", arrayOf(id.toString()))
    }

    suspend fun updateStats(id: Long, stats: MessageStats) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("stats_json", json.encodeToString(MessageStats.serializer(), stats))
        }
        database.writableDatabase.update("messages", values, "id = ?", arrayOf(id.toString()))
    }

    suspend fun setReaction(id: Long, reaction: String?) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("reaction", reaction) }
        database.writableDatabase.update("messages", values, "id = ?", arrayOf(id.toString()))
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("messages", "id = ?", arrayOf(id.toString()))
    }

    suspend fun deleteAfter(conversationId: String, id: Long) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete(
            "messages",
            "conversation_id = ? AND id > ?",
            arrayOf(conversationId, id.toString()),
        )
    }

    suspend fun userTurnCount(conversationId: String): Int = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND role = 'user'",
            arrayOf(conversationId),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    suspend fun clear(conversationId: String) = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("conversation_todos", "conversation_id = ?", arrayOf(conversationId))
            db.delete("messages", "conversation_id = ?", arrayOf(conversationId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun todos(conversationId: String): List<ConversationTodo> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, conversation_id, content, status, priority, position, created_at, updated_at " +
                "FROM conversation_todos WHERE conversation_id = ? ORDER BY position, id",
            arrayOf(conversationId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ConversationTodo(
                            id = cursor.getLong(0),
                            conversationId = cursor.getString(1),
                            content = cursor.getString(2),
                            status = TodoStatus.fromWire(cursor.getString(3)),
                            priority = TodoPriority.fromWire(cursor.getString(4)),
                            position = cursor.getInt(5),
                            createdAt = cursor.getLong(6),
                            updatedAt = cursor.getLong(7),
                        ),
                    )
                }
            }
        }
    }

    suspend fun replaceTodos(conversationId: String, todos: List<ConversationTodo>) =
        withContext(Dispatchers.IO) {
            val db = database.writableDatabase
            val now = System.currentTimeMillis()
            db.beginTransaction()
            try {
                db.delete("conversation_todos", "conversation_id = ?", arrayOf(conversationId))
                todos.forEachIndexed { index, todo ->
                    val values = ContentValues().apply {
                        put("conversation_id", conversationId)
                        put("content", todo.content)
                        put("status", todo.status.wire)
                        put("priority", todo.priority.wire)
                        put("position", index)
                        put("created_at", if (todo.createdAt > 0) todo.createdAt else now)
                        put("updated_at", now)
                    }
                    db.insertOrThrow("conversation_todos", null, values)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
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
        mode = AgentMode.fromWire(getString(8).orEmpty()),
        plan = if (isNull(9)) {
            null
        } else {
            runCatching { json.decodeFromString(ProposedPlan.serializer(), getString(9)) }.getOrNull()
        },
        planStatus = PlanStatus.fromWire(getString(10).orEmpty()),
        planRequestId = getString(11).orEmpty(),
        variant = getString(12).orEmpty(),
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
        val rawStats = if (isNull(12)) null else getString(12)
        val stats = rawStats?.let {
            runCatching { json.decodeFromString(MessageStats.serializer(), it) }.getOrNull()
        }
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
            replyToId = if (isNull(11)) null else getLong(11),
            stats = stats,
            reaction = if (isNull(13)) null else getString(13),
        )
    }

    companion object {
        private const val MESSAGE_COLUMNS =
            "id, conversation_id, role, content, reasoning, tool_calls, tool_call_id, name, " +
                "created_at, attachments, label, reply_to_id, stats_json, reaction"
    }
}
