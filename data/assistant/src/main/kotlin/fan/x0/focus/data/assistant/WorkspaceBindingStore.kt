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

@Serializable
data class FileMentionBinding(
    val key: String,
    val label: String,
    val kind: WorkspaceKind,
    val endpointId: String = "",
    val root: String = "",
    val treeUri: String = "",
    val manual: Boolean = false,
) {
    val path: String
        get() = when (kind) {
            WorkspaceKind.Bridge -> root
            WorkspaceKind.Local -> label
        }
}

@Serializable
private data class WorkspaceBindingRecord(
    val scope: String,
    val binding: FileMentionBinding,
)

private val Context.workspaceBindingDataStore by preferencesDataStore(name = "workspace_binding")

@Single
class WorkspaceBindingStore(@Provided private val context: Context) {
    private object Keys {
        val records = stringPreferencesKey("records_json")
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val serializer = ListSerializer(WorkspaceBindingRecord.serializer())

    suspend fun current(scope: String): FileMentionBinding? {
        val raw = context.workspaceBindingDataStore.data.first()[Keys.records] ?: return null
        return decode(raw).firstOrNull { it.scope == scope }?.binding
    }

    suspend fun set(scope: String, binding: FileMentionBinding) {
        context.workspaceBindingDataStore.edit { prefs ->
            val current = prefs[Keys.records]?.let(::decode).orEmpty()
            val next = (current.filterNot { it.scope == scope } + WorkspaceBindingRecord(scope, binding))
                .takeLast(MAX_RECORDS)
            prefs[Keys.records] = json.encodeToString(serializer, next)
        }
    }

    suspend fun clear(scope: String) {
        context.workspaceBindingDataStore.edit { prefs ->
            val current = prefs[Keys.records]?.let(::decode).orEmpty()
            prefs[Keys.records] = json.encodeToString(serializer, current.filterNot { it.scope == scope })
        }
    }

    private fun decode(raw: String): List<WorkspaceBindingRecord> =
        runCatching { json.decodeFromString(serializer, raw) }.getOrNull().orEmpty()

    private companion object {
        const val MAX_RECORDS = 32
    }
}
