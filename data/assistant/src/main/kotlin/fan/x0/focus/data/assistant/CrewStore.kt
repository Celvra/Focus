package fan.x0.focus.data.assistant

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class CrewStore(private val database: AssistantDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val reactionsSerializer = ListSerializer(String.serializer())
    private val toolsSerializer = ListSerializer(CrewToolTrace.serializer())
    private val attachmentsSerializer = ListSerializer(ChatAttachment.serializer())
    private val _deleted = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val deleted: SharedFlow<String> = _deleted.asSharedFlow()
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    suspend fun groups(includeArchived: Boolean = false): List<AgentGroup> = withContext(Dispatchers.IO) {
        val filter = if (includeArchived) "" else "WHERE archived = 0"
        database.readableDatabase.rawQuery(
            "SELECT $GROUP_COLUMNS FROM agent_groups $filter ORDER BY updated_at DESC",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toGroup())
            }
        }
    }

    suspend fun setArchived(id: String, archived: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("archived", if (archived) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("agent_groups", values, "id = ?", arrayOf(id))
        _changes.tryEmit(id)
    }

    suspend fun group(id: String): AgentGroup? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $GROUP_COLUMNS FROM agent_groups WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toGroup() else null }
    }

    suspend fun groupDetail(id: String): CrewGroupDetail? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $GROUP_COLUMNS FROM agent_groups WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext null
            CrewGroupDetail(cursor.toGroup(), readMembers(id, activeOnly = true))
        }
    }

    suspend fun resolveGroup(reference: String): AgentGroup? = withContext(Dispatchers.IO) {
        val value = reference.trim()
        if (value.isBlank()) return@withContext null
        database.readableDatabase.rawQuery(
            "SELECT $GROUP_COLUMNS FROM agent_groups WHERE id = ? OR lower(name) = lower(?) LIMIT 1",
            arrayOf(value, value),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toGroup() else null }
    }

    suspend fun createGroup(
        name: String,
        topic: String,
        avatarPreset: Int,
        avatarPalette: Int,
    ): AgentGroup = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val group = AgentGroup(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(80),
            topic = topic.trim().take(400),
            avatarPreset = avatarPreset.mod(CrewAvatars.PRESET_COUNT),
            avatarPalette = avatarPalette.mod(CrewAvatars.PALETTE_COUNT),
            createdAt = now,
            updatedAt = now,
            lastMessageAt = 0,
        )
        val values = ContentValues().apply {
            put("id", group.id)
            put("name", group.name)
            put("topic", group.topic)
            put("avatar_preset", group.avatarPreset)
            put("avatar_palette", group.avatarPalette)
            put("created_at", group.createdAt)
            put("updated_at", group.updatedAt)
        }
        database.writableDatabase.insertOrThrow("agent_groups", null, values)
        _changes.tryEmit(group.id)
        group
    }

    suspend fun updateGroup(
        id: String,
        name: String,
        topic: String,
        avatarPreset: Int,
        avatarPalette: Int,
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("name", name.trim().take(80))
            put("topic", topic.trim().take(400))
            put("avatar_preset", avatarPreset.mod(CrewAvatars.PRESET_COUNT))
            put("avatar_palette", avatarPalette.mod(CrewAvatars.PALETTE_COUNT))
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("agent_groups", values, "id = ?", arrayOf(id))
        _changes.tryEmit(id)
    }

    suspend fun deleteGroup(id: String) = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("crew_messages", "group_id = ?", arrayOf(id))
            db.delete("crew_members", "group_id = ?", arrayOf(id))
            db.delete("crew_runs", "group_id = ?", arrayOf(id))
            db.delete("conversation_todos", "conversation_id = ?", arrayOf(id))
            db.delete("messages", "conversation_id = ?", arrayOf(id))
            db.delete("agent_groups", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _deleted.tryEmit(id)
        _changes.tryEmit(id)
    }

    suspend fun members(groupId: String, activeOnly: Boolean = true): List<CrewMember> =
        withContext(Dispatchers.IO) { readMembers(groupId, activeOnly) }

    suspend fun allMembers(groupId: String): List<CrewMember> = withContext(Dispatchers.IO) {
        readMembers(groupId, activeOnly = false)
    }

    suspend fun member(id: String): CrewMember? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $MEMBER_COLUMNS FROM crew_members WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toMember() else null }
    }

    suspend fun resolveMember(groupId: String, reference: String): CrewMember? =
        withContext(Dispatchers.IO) {
            val value = reference.trim().removePrefix("@")
            if (value.isBlank()) return@withContext null
            database.readableDatabase.rawQuery(
                "SELECT $MEMBER_COLUMNS FROM crew_members WHERE group_id = ? " +
                    "AND (id = ? OR lower(name) = lower(?)) ORDER BY active DESC, position LIMIT 1",
                arrayOf(groupId, value, value),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.toMember() else null }
        }

    suspend fun addMember(
        groupId: String,
        name: String,
        duty: String,
        avatarPreset: Int,
        avatarPalette: Int,
        avatarImage: String = "",
        providerId: String = "",
        model: String = "",
        reasoning: String = "",
    ): CrewMember = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val position = nextPosition(groupId)
        val member = CrewMember(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            name = name.trim().take(40),
            duty = duty.trim().take(400),
            avatarPreset = avatarPreset.mod(CrewAvatars.PRESET_COUNT),
            avatarPalette = avatarPalette.mod(CrewAvatars.PALETTE_COUNT),
            avatarImage = avatarImage,
            position = position,
            active = true,
            providerId = providerId,
            model = model,
            reasoning = reasoning,
        )
        val values = ContentValues().apply {
            put("id", member.id)
            put("group_id", member.groupId)
            put("name", member.name)
            put("duty", member.duty)
            put("avatar_preset", member.avatarPreset)
            put("avatar_palette", member.avatarPalette)
            put("avatar_image", member.avatarImage)
            put("position", member.position)
            put("active", 1)
            put("provider_id", member.providerId)
            put("model", member.model)
            put("reasoning", member.reasoning)
            put("created_at", now)
            put("updated_at", now)
        }
        database.writableDatabase.insertOrThrow("crew_members", null, values)
        touchGroup(groupId)
        member
    }

    suspend fun updateMember(member: CrewMember) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("name", member.name.trim().take(40))
            put("duty", member.duty.trim().take(400))
            put("avatar_preset", member.avatarPreset.mod(CrewAvatars.PRESET_COUNT))
            put("avatar_palette", member.avatarPalette.mod(CrewAvatars.PALETTE_COUNT))
            put("avatar_image", member.avatarImage)
            put("position", member.position)
            put("active", if (member.active) 1 else 0)
            put("provider_id", member.providerId)
            put("model", member.model)
            put("reasoning", member.reasoning)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("crew_members", values, "id = ?", arrayOf(member.id))
        touchGroup(member.groupId)
    }

    suspend fun setMemberActive(id: String, active: Boolean) = withContext(Dispatchers.IO) {
        val groupId = database.readableDatabase.rawQuery(
            "SELECT group_id FROM crew_members WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return@withContext
        val values = ContentValues().apply {
            put("active", if (active) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("crew_members", values, "id = ?", arrayOf(id))
        touchGroup(groupId)
    }

    suspend fun removeMember(id: String) = withContext(Dispatchers.IO) {
        val member = database.readableDatabase.rawQuery(
            "SELECT group_id FROM crew_members WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (member == null) return@withContext
        val values = ContentValues().apply {
            put("active", 0)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("crew_members", values, "id = ?", arrayOf(id))
        touchGroup(member)
    }

    suspend fun messages(groupId: String, limit: Int = 200): List<CrewMessage> =
        withContext(Dispatchers.IO) {
            database.readableDatabase.rawQuery(
                "SELECT $MESSAGE_COLUMNS FROM crew_messages WHERE group_id = ? ORDER BY id DESC LIMIT ?",
                arrayOf(groupId, limit.toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toMessage())
                }.reversed()
            }
        }

    suspend fun messagesAfter(groupId: String, afterId: Long): List<CrewMessage> =
        withContext(Dispatchers.IO) {
            database.readableDatabase.rawQuery(
                "SELECT $MESSAGE_COLUMNS FROM crew_messages WHERE group_id = ? AND id > ? ORDER BY id",
                arrayOf(groupId, afterId.toString()),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toMessage())
                }
            }
        }

    suspend fun message(id: Long): CrewMessage? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $MESSAGE_COLUMNS FROM crew_messages WHERE id = ? LIMIT 1",
            arrayOf(id.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toMessage() else null }
    }

    suspend fun lastMessage(groupId: String): CrewMessage? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $MESSAGE_COLUMNS FROM crew_messages WHERE group_id = ? ORDER BY id DESC LIMIT 1",
            arrayOf(groupId),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toMessage() else null }
    }

    suspend fun appendMessage(
        groupId: String,
        authorType: CrewAuthorType,
        authorId: String,
        authorName: String,
        content: String,
        replyToId: Long? = null,
        reactions: List<String> = emptyList(),
        kind: CrewMessageKind = CrewMessageKind.Text,
        reasoning: String = "",
        elapsedMs: Long = 0,
        tools: List<CrewToolTrace> = emptyList(),
        attachments: List<ChatAttachment> = emptyList(),
        stats: MessageStats? = null,
    ): CrewMessage = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("group_id", groupId)
            put("author_type", authorType.name.lowercase())
            put("author_id", authorId)
            put("author_name", authorName)
            put("content", content)
            put("reply_to_id", replyToId)
            put(
                "reactions",
                if (reactions.isEmpty()) null else json.encodeToString(reactionsSerializer, reactions),
            )
            put("kind", kind.name.lowercase())
            put("created_at", now)
            put("reasoning", reasoning)
            put("elapsed_ms", elapsedMs)
            put(
                "tools",
                if (tools.isEmpty()) null else json.encodeToString(toolsSerializer, tools),
            )
            put(
                "attachments",
                if (attachments.isEmpty()) null else json.encodeToString(attachmentsSerializer, attachments),
            )
            put("stats_json", stats?.let { json.encodeToString(MessageStats.serializer(), it) })
        }
        val id = database.writableDatabase.insertOrThrow("crew_messages", null, values)
        val touch = ContentValues().apply {
            put("updated_at", now)
            put("last_message_at", now)
        }
        database.writableDatabase.update("agent_groups", touch, "id = ?", arrayOf(groupId))
        _changes.tryEmit(groupId)
        CrewMessage(
            id = id,
            groupId = groupId,
            authorType = authorType,
            authorId = authorId,
            authorName = authorName,
            content = content,
            replyToId = replyToId,
            reactions = reactions,
            kind = kind,
            createdAt = now,
            reasoning = reasoning,
            elapsedMs = elapsedMs,
            tools = tools,
            attachments = attachments,
            stats = stats,
        )
    }

    suspend fun todos(groupId: String): List<ConversationTodo> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, content, status, priority, position, created_at, updated_at " +
                "FROM conversation_todos WHERE conversation_id = ? ORDER BY position, id",
            arrayOf(groupId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ConversationTodo(
                            id = cursor.getLong(0),
                            conversationId = groupId,
                            content = cursor.getString(1),
                            status = TodoStatus.fromWire(cursor.getString(2)),
                            priority = TodoPriority.fromWire(cursor.getString(3)),
                            position = cursor.getInt(4),
                            createdAt = cursor.getLong(5),
                            updatedAt = cursor.getLong(6),
                        ),
                    )
                }
            }
        }
    }

    suspend fun setReactions(id: Long, reactions: List<String>) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(
                "reactions",
                if (reactions.isEmpty()) null else json.encodeToString(reactionsSerializer, reactions),
            )
        }
        database.writableDatabase.update("crew_messages", values, "id = ?", arrayOf(id.toString()))
    }

    suspend fun addReaction(id: Long, reaction: String): List<String> = withContext(Dispatchers.IO) {
        val message = database.readableDatabase.rawQuery(
            "SELECT reactions FROM crew_messages WHERE id = ? LIMIT 1",
            arrayOf(id.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext emptyList()
            cursor.reactions()
        }
        val next = message + reaction
        val values = ContentValues().apply {
            put("reactions", json.encodeToString(reactionsSerializer, next))
        }
        database.writableDatabase.update("crew_messages", values, "id = ?", arrayOf(id.toString()))
        next
    }

    suspend fun clearMessages(groupId: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("crew_messages", "group_id = ?", arrayOf(groupId))
        touchGroup(groupId)
    }

    suspend fun deleteMessage(id: Long, groupId: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("crew_messages", "id = ?", arrayOf(id.toString()))
        touchGroup(groupId)
    }

    suspend fun deleteMessagesAfter(groupId: String, afterId: Long) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete(
            "crew_messages",
            "group_id = ? AND id > ?",
            arrayOf(groupId, afterId.toString()),
        )
        touchGroup(groupId)
    }

    suspend fun updateMessageContent(id: Long, groupId: String, content: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("content", content) }
        database.writableDatabase.update("crew_messages", values, "id = ?", arrayOf(id.toString()))
        touchGroup(groupId)
    }

    suspend fun saveRun(
        groupId: String,
        startedAt: Long,
        lastUserMessageId: Long,
        focusMemberIds: Set<String>,
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("group_id", groupId)
            put("started_at", startedAt)
            put("last_user_message_id", lastUserMessageId)
            put("focus", focusMemberIds.joinToString(","))
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.insertWithOnConflict(
            "crew_runs",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    suspend fun checkpointRun(groupId: String, lastUserMessageId: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("last_user_message_id", lastUserMessageId)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("crew_runs", values, "group_id = ?", arrayOf(groupId))
    }

    suspend fun clearRun(groupId: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("crew_runs", "group_id = ?", arrayOf(groupId))
    }

    suspend fun runningRuns(): List<PersistedCrewRun> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT group_id, started_at, last_user_message_id, focus FROM crew_runs ORDER BY started_at",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PersistedCrewRun(
                            groupId = cursor.getString(0),
                            startedAt = cursor.getLong(1),
                            lastUserMessageId = cursor.getLong(2),
                            focusMemberIds = cursor.getString(3).orEmpty()
                                .split(',')
                                .filter { it.isNotBlank() }
                                .toSet(),
                        ),
                    )
                }
            }
        }
    }

    private fun readMembers(groupId: String, activeOnly: Boolean): List<CrewMember> {
        val filter = if (activeOnly) "AND active = 1" else ""
        return database.readableDatabase.rawQuery(
            "SELECT $MEMBER_COLUMNS FROM crew_members WHERE group_id = ? $filter ORDER BY position, created_at",
            arrayOf(groupId),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toMember())
            }
        }
    }

    private fun nextPosition(groupId: String): Int {
        database.readableDatabase.rawQuery(
            "SELECT COALESCE(MAX(position), -1) + 1 FROM crew_members WHERE group_id = ?",
            arrayOf(groupId),
        ).use { cursor -> return if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    private fun touchGroup(groupId: String) {
        val values = ContentValues().apply { put("updated_at", System.currentTimeMillis()) }
        database.writableDatabase.update("agent_groups", values, "id = ?", arrayOf(groupId))
        _changes.tryEmit(groupId)
    }

    private fun Cursor.toGroup(): AgentGroup = AgentGroup(
        id = getString(0),
        name = getString(1),
        topic = getString(2).orEmpty(),
        avatarPreset = getInt(3),
        avatarPalette = getInt(4),
        createdAt = getLong(5),
        updatedAt = getLong(6),
        lastMessageAt = getLong(7),
        archived = getInt(8) != 0,
    )

    private fun Cursor.toMember(): CrewMember = CrewMember(
        id = getString(0),
        groupId = getString(1),
        name = getString(2),
        duty = getString(3).orEmpty(),
        avatarPreset = getInt(4),
        avatarPalette = getInt(5),
        avatarImage = getString(6).orEmpty(),
        position = getInt(7),
        active = getInt(8) != 0,
        providerId = getString(9).orEmpty(),
        model = getString(10).orEmpty(),
        reasoning = getString(11).orEmpty(),
    )

    private fun Cursor.toMessage(): CrewMessage {
        val authorType = when (getString(2)) {
            "user" -> CrewAuthorType.User
            "system" -> CrewAuthorType.System
            else -> CrewAuthorType.Agent
        }
        val kind = if (getString(8) == "notice") CrewMessageKind.Notice else CrewMessageKind.Text
        return CrewMessage(
            id = getLong(0),
            groupId = getString(1),
            authorType = authorType,
            authorId = getString(3).orEmpty(),
            authorName = getString(4).orEmpty(),
            content = getString(5).orEmpty(),
            replyToId = if (isNull(6)) null else getLong(6),
            reactions = reactions(),
            kind = kind,
            createdAt = getLong(9),
            reasoning = getString(10).orEmpty(),
            elapsedMs = getLong(11),
            tools = tools(),
            attachments = attachments(),
            stats = stats(),
        )
    }

    private fun Cursor.tools(): List<CrewToolTrace> {
        val raw = if (isNull(12)) null else getString(12)
        return raw?.let {
            runCatching { json.decodeFromString(toolsSerializer, it) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    private fun Cursor.attachments(): List<ChatAttachment> {
        val raw = if (isNull(13)) null else getString(13)
        return raw?.let {
            runCatching { json.decodeFromString(attachmentsSerializer, it) }.getOrDefault(emptyList())
        }.orEmpty()
    }

    private fun Cursor.stats(): MessageStats? {
        val raw = if (isNull(14)) null else getString(14)
        return raw?.let {
            runCatching { json.decodeFromString(MessageStats.serializer(), it) }.getOrNull()
        }
    }

    private fun Cursor.reactions(): List<String> {
        val raw = if (isNull(7)) null else getString(7)
        return raw?.let {
            runCatching { json.decodeFromString(reactionsSerializer, it) }.getOrDefault(emptyList())
        }.orEmpty().filter { it.isNotBlank() }
    }

    companion object {
        private const val GROUP_COLUMNS =
            "id, name, topic, avatar_preset, avatar_palette, created_at, updated_at, last_message_at, archived"
        private const val MEMBER_COLUMNS =
            "id, group_id, name, duty, avatar_preset, avatar_palette, avatar_image, position, active, " +
                "provider_id, model, reasoning"
        private const val MESSAGE_COLUMNS =
            "id, group_id, author_type, author_id, author_name, content, reply_to_id, reactions, kind, created_at, " +
                "reasoning, elapsed_ms, tools, attachments, stats_json"
    }
}
