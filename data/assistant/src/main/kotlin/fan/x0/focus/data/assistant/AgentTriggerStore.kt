package fan.x0.focus.data.assistant

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.time.ZoneId
import java.util.UUID

enum class AgentTriggerKind {
    Cron,
    Webhook,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String): AgentTriggerKind =
            entries.firstOrNull { it.wire == value.lowercase() } ?: Cron
    }
}

data class AgentTrigger(
    val id: String,
    val name: String,
    val kind: AgentTriggerKind,
    val prompt: String,
    val schedule: String = "",
    val timezone: String = "",
    val profile: WorkerProfile = WorkerProfile.Standard,
    val enabled: Boolean = true,
    val webhookToken: String = "",
    val nextRun: Long = 0,
    val lastRun: Long = 0,
    val lastStatus: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val zone: ZoneId get() = runCatching {
        if (timezone.isBlank()) ZoneId.systemDefault() else ZoneId.of(timezone)
    }.getOrDefault(ZoneId.systemDefault())

    val webhookPath: String get() = "/agent/hook/$webhookToken"
}

@Single
class AgentTriggerStore(private val database: AssistantDatabase) {
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    suspend fun list(): List<AgentTrigger> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $COLUMNS FROM agent_triggers ORDER BY updated_at DESC",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toTrigger())
            }
        }
    }

    suspend fun trigger(id: String): AgentTrigger? = withContext(Dispatchers.IO) {
        queryOne("id = ?", arrayOf(id))
    }

    suspend fun byToken(token: String): AgentTrigger? = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext null
        queryOne("webhook_token = ? AND kind = 'webhook'", arrayOf(token))
    }

    suspend fun create(
        name: String,
        kind: AgentTriggerKind,
        prompt: String,
        schedule: String,
        timezone: String = ZoneId.systemDefault().id,
        profile: WorkerProfile = WorkerProfile.Standard,
    ): AgentTrigger = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val trigger = AgentTrigger(
            id = UUID.randomUUID().toString(),
            name = name.trim().take(80),
            kind = kind,
            prompt = prompt.trim().take(8_000),
            schedule = if (kind == AgentTriggerKind.Cron) schedule.trim() else "",
            timezone = timezone,
            profile = profile,
            enabled = true,
            webhookToken = if (kind == AgentTriggerKind.Webhook) UUID.randomUUID().toString().replace("-", "") else "",
            createdAt = now,
            updatedAt = now,
        )
        val withNext = trigger.copy(nextRun = nextRunFor(trigger))
        database.writableDatabase.insertOrThrow("agent_triggers", null, withNext.values())
        _changes.tryEmit(withNext.id)
        withNext
    }

    suspend fun update(
        id: String,
        name: String,
        prompt: String,
        schedule: String,
        timezone: String,
        profile: WorkerProfile,
    ): AgentTrigger? = withContext(Dispatchers.IO) {
        val existing = queryOne("id = ?", arrayOf(id)) ?: return@withContext null
        val updated = existing.copy(
            name = name.trim().take(80),
            prompt = prompt.trim().take(8_000),
            schedule = if (existing.kind == AgentTriggerKind.Cron) schedule.trim() else "",
            timezone = timezone,
            profile = profile,
            updatedAt = System.currentTimeMillis(),
        )
        val withNext = updated.copy(nextRun = nextRunFor(updated))
        database.writableDatabase.update("agent_triggers", withNext.values(), "id = ?", arrayOf(id))
        _changes.tryEmit(id)
        withNext
    }

    suspend fun setEnabled(id: String, enabled: Boolean): AgentTrigger? = withContext(Dispatchers.IO) {
        val existing = queryOne("id = ?", arrayOf(id)) ?: return@withContext null
        val updated = existing.copy(
            enabled = enabled,
            nextRun = if (enabled) nextRunFor(existing) else 0,
            updatedAt = System.currentTimeMillis(),
        )
        database.writableDatabase.update("agent_triggers", updated.values(), "id = ?", arrayOf(id))
        _changes.tryEmit(id)
        updated
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("agent_triggers", "id = ?", arrayOf(id))
        _changes.tryEmit(id)
    }

    suspend fun due(now: Long = System.currentTimeMillis()): List<AgentTrigger> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $COLUMNS FROM agent_triggers WHERE enabled = 1 AND kind = 'cron' " +
                "AND next_run > 0 AND next_run <= ? ORDER BY next_run",
            arrayOf(now.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toTrigger())
            }
        }
    }

    suspend fun claim(
        trigger: AgentTrigger,
        status: String,
        nextRun: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("last_run", now)
            put("last_status", status.take(300))
            put("next_run", nextRun)
            put("updated_at", now)
        }
        val updated = database.writableDatabase.update(
            "agent_triggers",
            values,
            "id = ? AND next_run = ?",
            arrayOf(trigger.id, trigger.nextRun.toString()),
        )
        if (updated > 0) _changes.tryEmit(trigger.id)
        updated > 0
    }

    suspend fun reschedule(trigger: AgentTrigger): AgentTrigger = withContext(Dispatchers.IO) {
        val next = nextRunFor(trigger)
        val updated = trigger.copy(nextRun = next, updatedAt = System.currentTimeMillis())
        database.writableDatabase.update("agent_triggers", updated.values(), "id = ?", arrayOf(trigger.id))
        _changes.tryEmit(trigger.id)
        updated
    }

    suspend fun touch(id: String, status: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("last_status", status.take(300))
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("agent_triggers", values, "id = ?", arrayOf(id))
        _changes.tryEmit(id)
    }

    fun nextRunFor(trigger: AgentTrigger): Long {
        if (trigger.kind != AgentTriggerKind.Cron) return 0
        return runCatching {
            CronExpression.parse(trigger.schedule).next(zoneId = trigger.zone)
        }.getOrNull() ?: 0
    }

    private fun queryOne(where: String, args: Array<String>): AgentTrigger? =
        database.readableDatabase.rawQuery(
            "SELECT $COLUMNS FROM agent_triggers WHERE $where LIMIT 1",
            args,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toTrigger() else null }

    private fun AgentTrigger.values(): ContentValues = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("kind", kind.wire)
        put("prompt", prompt)
        put("schedule", schedule)
        put("timezone", timezone)
        put("profile", profile.wire)
        put("enabled", if (enabled) 1 else 0)
        put("webhook_token", webhookToken)
        put("next_run", nextRun)
        put("last_run", lastRun)
        put("last_status", lastStatus)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
    }

    private fun Cursor.toTrigger(): AgentTrigger = AgentTrigger(
        id = getString(0),
        name = getString(1),
        kind = AgentTriggerKind.fromWire(getString(2)),
        prompt = getString(3).orEmpty(),
        schedule = getString(4).orEmpty(),
        timezone = getString(5).orEmpty(),
        profile = WorkerProfile.fromWire(getString(6)),
        enabled = getInt(7) != 0,
        webhookToken = getString(8).orEmpty(),
        nextRun = getLong(9),
        lastRun = getLong(10),
        lastStatus = getString(11).orEmpty(),
        createdAt = getLong(12),
        updatedAt = getLong(13),
    )

    private companion object {
        const val COLUMNS =
            "id, name, kind, prompt, schedule, timezone, profile, enabled, webhook_token, " +
                "next_run, last_run, last_status, created_at, updated_at"
    }
}
