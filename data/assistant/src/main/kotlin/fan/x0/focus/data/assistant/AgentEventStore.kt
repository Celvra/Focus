package fan.x0.focus.data.assistant

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class AgentEventStore(private val database: AssistantDatabase) {
    private val _changes = MutableSharedFlow<Long>(extraBufferCapacity = 32)
    val changes: SharedFlow<Long> = _changes.asSharedFlow()

    suspend fun insert(event: AgentEvent): AgentEvent? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("source", event.source.wire)
            put("kind", event.kind.wire)
            put("target", event.target)
            put("thread_key", event.threadKey)
            put("payload", event.payload)
            put("dedupe_key", event.dedupeKey)
            put("status", event.status.wire)
            put("attempts", event.attempts)
            put("due_at", if (event.dueAt > 0) event.dueAt else now)
            put("created_at", if (event.createdAt > 0) event.createdAt else now)
        }
        val id = try {
            database.writableDatabase.insertOrThrow("agent_events", null, values)
        } catch (_: SQLiteConstraintException) {
            return@withContext null
        }
        _changes.tryEmit(id)
        event.copy(
            id = id,
            createdAt = if (event.createdAt > 0) event.createdAt else now,
            dueAt = if (event.dueAt > 0) event.dueAt else now,
        )
    }

    suspend fun byId(id: Long): AgentEvent? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $COLUMNS FROM agent_events WHERE id = ? LIMIT 1",
            arrayOf(id.toString()),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toEvent() else null }
    }

    suspend fun pending(target: String? = null, limit: Int = 20): List<AgentEvent> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val where = buildString {
                append("status = 'pending' AND due_at <= ?")
                if (target != null) append(" AND target = ?")
            }
            val args = buildList {
                add(now.toString())
                if (target != null) add(target)
                add(limit.toString())
            }
            database.readableDatabase.rawQuery(
                "SELECT $COLUMNS FROM agent_events WHERE $where ORDER BY id LIMIT ?",
                args.toTypedArray(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toEvent())
                }
            }
        }

    suspend fun recent(limit: Int = 30, since: Long = 0): List<AgentEvent> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $COLUMNS FROM agent_events WHERE created_at >= ? ORDER BY id DESC LIMIT ?",
            arrayOf(since.toString(), limit.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toEvent())
            }
        }
    }

    suspend fun claim(id: Long): Boolean = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", AgentEventStatus.Running.wire)
            put("attempts", attempts(id) + 1)
        }
        val updated = database.writableDatabase.update(
            "agent_events",
            values,
            "id = ? AND status = 'pending'",
            arrayOf(id.toString()),
        )
        if (updated > 0) _changes.tryEmit(id)
        updated > 0
    }

    suspend fun complete(id: Long) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", AgentEventStatus.Done.wire)
            put("processed_at", System.currentTimeMillis())
            putNull("error")
        }
        database.writableDatabase.update("agent_events", values, "id = ?", arrayOf(id.toString()))
        _changes.tryEmit(id)
    }

    suspend fun fail(id: Long, error: String, retryAt: Long? = null) = withContext(Dispatchers.IO) {
        val attempts = attempts(id)
        val values = ContentValues().apply {
            put("attempts", attempts + 1)
            put("error", error.take(1_000))
            if (retryAt != null) {
                put("status", AgentEventStatus.Pending.wire)
                put("due_at", retryAt)
            } else {
                put("status", AgentEventStatus.Failed.wire)
                put("processed_at", System.currentTimeMillis())
            }
        }
        database.writableDatabase.update("agent_events", values, "id = ?", arrayOf(id.toString()))
        _changes.tryEmit(id)
    }

    suspend fun filter(id: Long, reason: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", AgentEventStatus.Filtered.wire)
            put("error", reason.take(500))
            put("processed_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("agent_events", values, "id = ?", arrayOf(id.toString()))
        _changes.tryEmit(id)
    }

    suspend fun recoverRunning(): Int = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("status", AgentEventStatus.Pending.wire)
            put("due_at", System.currentTimeMillis())
        }
        val count = database.writableDatabase.update(
            "agent_events",
            values,
            "status = 'running'",
            null,
        )
        if (count > 0) _changes.tryEmit(-1)
        count
    }

    suspend fun cleanup(days: Int = 7): Int = withContext(Dispatchers.IO) {
        val before = System.currentTimeMillis() - days.coerceAtLeast(1) * 24L * 60 * 60 * 1000
        database.writableDatabase.delete(
            "agent_events",
            "status IN ('done', 'filtered') AND created_at < ?",
            arrayOf(before.toString()),
        )
    }

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM agent_events WHERE status IN ('pending', 'running')",
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    private fun attempts(id: Long): Int {
        database.readableDatabase.rawQuery(
            "SELECT attempts FROM agent_events WHERE id = ? LIMIT 1",
            arrayOf(id.toString()),
        ).use { cursor -> return if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    private fun Cursor.toEvent(): AgentEvent = AgentEvent(
        id = getLong(0),
        source = AgentEventSource.fromWire(getString(1)),
        kind = AgentEventKind.fromWire(getString(2)),
        target = getString(3),
        threadKey = getString(4).orEmpty(),
        payload = getString(5).orEmpty(),
        dedupeKey = if (isNull(6)) null else getString(6),
        status = AgentEventStatus.fromWire(getString(7)),
        attempts = getInt(8),
        dueAt = getLong(9),
        createdAt = getLong(10),
        processedAt = if (isNull(11)) null else getLong(11),
        error = if (isNull(12)) null else getString(12),
    )

    private companion object {
        const val COLUMNS =
            "id, source, kind, target, thread_key, payload, dedupe_key, status, attempts, " +
                "due_at, created_at, processed_at, error"
    }
}
