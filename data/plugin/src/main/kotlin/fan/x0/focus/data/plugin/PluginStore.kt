package fan.x0.focus.data.plugin

import android.content.ContentValues
import android.database.Cursor
import fan.x0.focus.plugin.PluginManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

data class InstalledPlugin(
    val manifest: PluginManifest,
    val enabled: Boolean,
    val source: String,
    val installedAt: Long,
    val updatedAt: Long,
    val capabilities: Set<String>,
    val lastError: String,
)

@Single
class PluginStore(private val database: PluginDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val manifestSerializer = PluginManifest.serializer()
    private val capabilitiesSerializer = ListSerializer(String.serializer())

    suspend fun list(): List<InstalledPlugin> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, manifest_json, enabled, source, installed_at, updated_at, capabilities_json, last_error " +
                "FROM plugins ORDER BY name COLLATE NOCASE",
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.toPlugin()?.let { add(it) } }
        }
    }

    suspend fun get(id: String): InstalledPlugin? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, manifest_json, enabled, source, installed_at, updated_at, capabilities_json, last_error " +
                "FROM plugins WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toPlugin() else null }
    }

    suspend fun enabled(): List<InstalledPlugin> = list().filter { it.enabled }

    suspend fun save(
        manifest: PluginManifest,
        source: String,
        enabled: Boolean = true,
        capabilities: Set<String> = emptySet(),
    ) = withContext(Dispatchers.IO) {
        val existing = get(manifest.id)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", manifest.id)
            put("name", manifest.name)
            put("version", manifest.version)
            put("version_code", manifest.versionCode)
            put("api_version", manifest.apiVersion)
            put("main_class", manifest.mainClass)
            put("enabled", if (enabled) 1 else 0)
            put("source", source)
            put("installed_at", existing?.installedAt ?: now)
            put("updated_at", now)
            put("manifest_json", json.encodeToString(manifestSerializer, manifest))
            put("capabilities_json", json.encodeToString(capabilitiesSerializer, capabilities.sorted()))
            put("last_error", "")
        }
        database.writableDatabase.insertWithOnConflict("plugins", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("plugins", values, "id = ?", arrayOf(id))
        Unit
    }

    suspend fun setCapabilities(id: String, capabilities: Set<String>) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("capabilities_json", json.encodeToString(capabilitiesSerializer, capabilities.sorted()))
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("plugins", values, "id = ?", arrayOf(id))
        Unit
    }

    suspend fun setError(id: String, error: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("last_error", error.take(2000))
        }
        database.writableDatabase.update("plugins", values, "id = ?", arrayOf(id))
        Unit
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("plugins", "id = ?", arrayOf(id))
        Unit
    }

    private fun Cursor.toPlugin(): InstalledPlugin? {
        val manifest = runCatching {
            json.decodeFromString(manifestSerializer, getString(1))
        }.getOrNull() ?: return null
        val capabilities = runCatching {
            json.decodeFromString(capabilitiesSerializer, getString(6))
        }.getOrDefault(emptyList()).toSet()
        return InstalledPlugin(
            manifest = manifest,
            enabled = getInt(2) == 1,
            source = getString(3).orEmpty(),
            installedAt = getLong(4),
            updatedAt = getLong(5),
            capabilities = capabilities,
            lastError = getString(7).orEmpty(),
        )
    }
}
