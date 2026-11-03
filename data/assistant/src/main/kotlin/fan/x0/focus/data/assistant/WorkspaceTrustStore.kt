package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

enum class WorkspaceKind { Bridge, Local }

@Serializable
data class WorkspaceTrustRecord(
    val key: String,
    val label: String,
    val kind: WorkspaceKind,
    val trusted: Boolean,
    val decidedAt: Long = 0,
)

@Serializable
data class WorkspaceInstructionBaseline(
    val key: String,
    val path: String,
    val content: String,
    val updatedAt: Long = 0,
)

private val Context.workspaceTrustDataStore by preferencesDataStore(name = "workspace_trust")

@Single
class WorkspaceTrustStore(@Provided private val context: Context) {
    private object Keys {
        val records = stringPreferencesKey("records_json")
        val baselines = stringPreferencesKey("baselines_json")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val recordsSerializer = ListSerializer(WorkspaceTrustRecord.serializer())
    private val baselinesSerializer = ListSerializer(WorkspaceInstructionBaseline.serializer())

    suspend fun recordsOnce(): List<WorkspaceTrustRecord> = readRecords(
        context.workspaceTrustDataStore.data.first(),
    )

    suspend fun record(key: String): WorkspaceTrustRecord? = recordsOnce().firstOrNull { it.key == key }

    suspend fun setRecord(record: WorkspaceTrustRecord) {
        context.workspaceTrustDataStore.edit { prefs ->
            val current = readRecords(prefs)
            val next = current.filterNot { it.key == record.key } + record
            prefs[Keys.records] = json.encodeToString(recordsSerializer, next)
        }
    }

    suspend fun baseline(key: String, path: String): String? =
        readBaselines().firstOrNull { it.key == key && it.path == path }?.content

    suspend fun setBaseline(key: String, path: String, content: String) {
        context.workspaceTrustDataStore.edit { prefs ->
            val next = readBaselines(prefs)
                .filterNot { it.key == key && it.path == path } +
                WorkspaceInstructionBaseline(
                    key = key,
                    path = path,
                    content = content.take(MAX_BASELINE_CHARS),
                    updatedAt = System.currentTimeMillis(),
                )
            prefs[Keys.baselines] = json.encodeToString(baselinesSerializer, next)
        }
    }

    suspend fun clearBaseline(key: String, path: String) {
        context.workspaceTrustDataStore.edit { prefs ->
            val next = readBaselines(prefs).filterNot { it.key == key && it.path == path }
            prefs[Keys.baselines] = json.encodeToString(baselinesSerializer, next)
        }
    }

    suspend fun clearBaselines(key: String) {
        context.workspaceTrustDataStore.edit { prefs ->
            val next = readBaselines(prefs).filterNot { it.key == key }
            prefs[Keys.baselines] = json.encodeToString(baselinesSerializer, next)
        }
    }

    private suspend fun readBaselines(): List<WorkspaceInstructionBaseline> =
        context.workspaceTrustDataStore.data.first()[Keys.baselines]?.let { raw ->
            runCatching { json.decodeFromString(baselinesSerializer, raw) }.getOrNull()
        }.orEmpty()

    private fun readBaselines(
        prefs: androidx.datastore.preferences.core.Preferences,
    ): List<WorkspaceInstructionBaseline> = prefs[Keys.baselines]?.let { raw ->
        runCatching { json.decodeFromString(baselinesSerializer, raw) }.getOrNull()
    }.orEmpty()

    private fun readRecords(
        prefs: androidx.datastore.preferences.core.Preferences,
    ): List<WorkspaceTrustRecord> = prefs[Keys.records]?.let { raw ->
        runCatching { json.decodeFromString(recordsSerializer, raw) }.getOrNull()
    }.orEmpty()

    companion object {
        private const val MAX_BASELINE_CHARS = 40_000
    }
}
