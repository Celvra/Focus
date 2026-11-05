package fan.x0.focus.data.im

import android.content.ContentValues
import android.database.Cursor
import fan.x0.focus.data.assistant.AssistantDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class ImAccountStore(private val database: AssistantDatabase) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    suspend fun list(): List<ImAccount> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, channel, name, config_json, enabled, status, status_detail, " +
                "platform_user_id, owner_id, cursor FROM im_accounts ORDER BY created_at",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toAccount())
            }
        }
    }

    suspend fun enabled(): List<ImAccount> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, channel, name, config_json, enabled, status, status_detail, " +
                "platform_user_id, owner_id, cursor FROM im_accounts " +
                "WHERE enabled = 1 ORDER BY created_at",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toAccount())
            }
        }
    }

    suspend fun account(id: String): ImAccount? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, channel, name, config_json, enabled, status, status_detail, " +
                "platform_user_id, owner_id, cursor FROM im_accounts WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toAccount() else null
        }
    }

    suspend fun upsert(draft: ImAccountDraft) = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        val now = System.currentTimeMillis()
        val existing = accountOnce(draft.id)
        val config = ImAccountConfig(
            values = draft.values,
            requireMention = draft.requireMention,
            onlyOwnerInGroups = draft.onlyOwnerInGroups,
            allowedSenders = existing?.config?.allowedSenders.orEmpty(),
            baseUrl = draft.baseUrl.ifBlank { existing?.config?.baseUrl.orEmpty() },
        )
        val values = ContentValues().apply {
            put("id", draft.id)
            put("channel", draft.channel)
            put("name", draft.name)
            put("config_json", json.encodeToString(ImAccountConfig.serializer(), config))
            put("enabled", if (draft.enabled) 1 else 0)
            put("updated_at", now)
            if (existing == null) {
                put("status", if (draft.enabled) ImConnectionState.Connecting.name else ImConnectionState.Disabled.name)
                put("status_detail", "")
                put("created_at", now)
            } else {
                put("status", if (draft.enabled) existing.status.name else ImConnectionState.Disabled.name)
                put("status_detail", existing.statusDetail)
            }
        }
        if (existing == null) {
            db.insertOrThrow("im_accounts", null, values)
        } else {
            db.update("im_accounts", values, "id = ?", arrayOf(draft.id))
        }
        _changes.tryEmit(Unit)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
            put("status", if (enabled) ImConnectionState.Connecting.name else ImConnectionState.Disabled.name)
            put("status_detail", "")
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("im_accounts", values, "id = ?", arrayOf(id))
        _changes.tryEmit(Unit)
    }

    suspend fun setStatus(id: String, state: ImConnectionState, detail: String = "") =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("status", state.name)
                put("status_detail", detail.take(300))
                put("updated_at", System.currentTimeMillis())
            }
            database.writableDatabase.update("im_accounts", values, "id = ?", arrayOf(id))
            _changes.tryEmit(Unit)
        }

    suspend fun setPlatformUserId(id: String, value: String) = withContext(Dispatchers.IO) {
        if (value.isBlank()) return@withContext
        val values = ContentValues().apply {
            put("platform_user_id", value)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("im_accounts", values, "id = ?", arrayOf(id))
        _changes.tryEmit(Unit)
    }

    suspend fun setOwnerId(id: String, value: String) = withContext(Dispatchers.IO) {
        if (value.isBlank()) return@withContext
        val values = ContentValues().apply {
            put("owner_id", value)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("im_accounts", values, "id = ?", arrayOf(id))
        _changes.tryEmit(Unit)
    }

    suspend fun setLogin(
        id: String,
        platformUserId: String,
        ownerId: String,
        baseUrl: String,
    ) = withContext(Dispatchers.IO) {
        val existing = accountOnce(id) ?: return@withContext
        val config = existing.config.copy(
            baseUrl = baseUrl.trim().ifBlank { existing.config.baseUrl },
        )
        val values = ContentValues().apply {
            put("config_json", json.encodeToString(ImAccountConfig.serializer(), config))
            if (platformUserId.isNotBlank()) put("platform_user_id", platformUserId)
            if (ownerId.isNotBlank()) put("owner_id", ownerId)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("im_accounts", values, "id = ?", arrayOf(id))
        _changes.tryEmit(Unit)
    }

    suspend fun setCursor(id: String, value: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("cursor", value)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("im_accounts", values, "id = ?", arrayOf(id))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.delete("im_seen", "account_id = ?", arrayOf(id))
            db.delete("im_chats", "account_id = ?", arrayOf(id))
            db.delete("im_accounts", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _changes.tryEmit(Unit)
    }

    private suspend fun accountOnce(id: String): ImAccount? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, channel, name, config_json, enabled, status, status_detail, " +
                "platform_user_id, owner_id, cursor FROM im_accounts WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toAccount() else null
        }
    }

    private fun Cursor.toAccount(): ImAccount {
        val config = runCatching {
            json.decodeFromString(ImAccountConfig.serializer(), getString(3))
        }.getOrDefault(ImAccountConfig())
        return ImAccount(
            id = getString(0),
            channel = getString(1),
            name = getString(2),
            enabled = getInt(4) == 1,
            status = runCatching { ImConnectionState.valueOf(getString(5)) }
                .getOrDefault(ImConnectionState.Disabled),
            statusDetail = getString(6).orEmpty(),
            platformUserId = getString(7).orEmpty(),
            ownerId = getString(8).orEmpty(),
            cursor = getString(9).orEmpty(),
            config = config,
        )
    }
}
