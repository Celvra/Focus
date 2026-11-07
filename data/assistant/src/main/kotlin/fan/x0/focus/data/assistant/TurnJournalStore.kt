package fan.x0.focus.data.assistant

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

data class TurnJournalEntry(
    val id: Long,
    val conversationId: String,
    val turnId: Long,
    val status: String,
    val toolCalls: String,
    val repeatedCalls: Int,
    val note: String,
    val startedAt: Long,
    val updatedAt: Long,
)

@Single
class TurnJournalStore(private val database: AssistantDatabase) {

    suspend fun begin(conversationId: String, turnId: Long, note: String = "") = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("conversation_id", conversationId)
            put("turn_id", turnId)
            put("status", "running")
            put("tool_calls", "")
            put("repeated_calls", 0)
            put("note", note.take(500))
            put("started_at", now)
            put("updated_at", now)
        }
        database.writableDatabase.insertOrThrow("turn_journal", null, values)
    }

    suspend fun toolStarted(
        conversationId: String,
        turnId: Long,
        tool: String,
        arguments: String,
    ) = withContext(Dispatchers.IO) {
        val row = latest(conversationId, turnId) ?: return@withContext
        val entry = "$tool:${arguments.take(120)}"
        val calls = (row.toolCalls.split('\n').filter { it.isNotBlank() } + entry).takeLast(MAX_TRACKED)
        val repeated = calls.count { it == entry }
        val values = ContentValues().apply {
            put("tool_calls", calls.joinToString("\n"))
            put("repeated_calls", repeated)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update(
            "turn_journal",
            values,
            "id = ?",
            arrayOf(row.id.toString()),
        )
    }

    suspend fun touch(conversationId: String, turnId: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("updated_at", System.currentTimeMillis()) }
        database.writableDatabase.update(
            "turn_journal",
            values,
            "conversation_id = ? AND turn_id = ?",
            arrayOf(conversationId, turnId.toString()),
        )
    }

    suspend fun finish(conversationId: String, turnId: Long, status: String, note: String = "") =
        withContext(Dispatchers.IO) {
            val row = latest(conversationId, turnId) ?: return@withContext
            val values = ContentValues().apply {
                put("status", status)
                if (note.isNotBlank()) put("note", note.take(500))
                put("updated_at", System.currentTimeMillis())
            }
            database.writableDatabase.update(
                "turn_journal",
                values,
                "id = ?",
                arrayOf(row.id.toString()),
            )
        }

    suspend fun interruptAll(): Int = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", "interrupted")
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("turn_journal", values, "status = 'running'", null)
    }

    suspend fun cleanup(days: Int = 7): Int = withContext(Dispatchers.IO) {
        val before = System.currentTimeMillis() - days.coerceAtLeast(1) * 24L * 60 * 60 * 1000
        database.writableDatabase.delete(
            "turn_journal",
            "status != 'running' AND updated_at < ?",
            arrayOf(before.toString()),
        )
    }

    suspend fun running(): List<TurnJournalEntry> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $COLUMNS FROM turn_journal WHERE status = 'running' ORDER BY started_at",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toEntry())
            }
        }
    }

    suspend fun latest(conversationId: String): TurnJournalEntry? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $COLUMNS FROM turn_journal WHERE conversation_id = ? ORDER BY id DESC LIMIT 1",
            arrayOf(conversationId),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toEntry() else null }
    }

    private fun latest(conversationId: String, turnId: Long): TurnJournalEntry? {
        database.readableDatabase.rawQuery(
            "SELECT $COLUMNS FROM turn_journal WHERE conversation_id = ? AND turn_id = ? " +
                "ORDER BY id DESC LIMIT 1",
            arrayOf(conversationId, turnId.toString()),
        ).use { cursor -> return if (cursor.moveToFirst()) cursor.toEntry() else null }
    }

    private fun android.database.Cursor.toEntry(): TurnJournalEntry = TurnJournalEntry(
        id = getLong(0),
        conversationId = getString(1),
        turnId = getLong(2),
        status = getString(3),
        toolCalls = getString(4).orEmpty(),
        repeatedCalls = getInt(5),
        note = getString(6).orEmpty(),
        startedAt = getLong(7),
        updatedAt = getLong(8),
    )

    private companion object {
        const val COLUMNS =
            "id, conversation_id, turn_id, status, tool_calls, repeated_calls, note, started_at, updated_at"
        const val MAX_TRACKED = 40
    }
}
