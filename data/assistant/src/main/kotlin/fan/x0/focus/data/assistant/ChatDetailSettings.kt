package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private val Context.chatDetailDataStore by preferencesDataStore(name = "chat_detail_settings")

@Single
class ChatDetailSettings(@Provided private val context: Context) {
    private val key = stringPreferencesKey("config_json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val fieldsSerializer = ListSerializer(ChatStatField.serializer())

    val config: Flow<ChatDetailConfig> = context.chatDetailDataStore.data.map { prefs ->
        prefs[key]?.let { raw ->
            runCatching { json.decodeFromString(ChatDetailConfig.serializer(), raw) }
                .getOrNull()
                ?.normalized()
        } ?: ChatDetailConfig()
    }

    suspend fun configOnce(): ChatDetailConfig = config.first()

    suspend fun setEnabled(field: ChatStatField, enabled: Boolean) {
        context.chatDetailDataStore.edit { prefs ->
            val current = read(prefs[key])
            val next = if (enabled) current.enabled + field else current.enabled - field
            prefs[key] = encode(current.copy(enabled = next))
        }
    }

    suspend fun move(field: ChatStatField, delta: Int) {
        context.chatDetailDataStore.edit { prefs ->
            val current = read(prefs[key])
            val order = current.order.toMutableList()
            val index = order.indexOf(field)
            if (index < 0) return@edit
            val target = (index + delta).coerceIn(0, order.lastIndex)
            if (target == index) return@edit
            order.removeAt(index)
            order.add(target, field)
            prefs[key] = encode(current.copy(order = order))
        }
    }

    suspend fun reset() {
        context.chatDetailDataStore.edit { prefs -> prefs.remove(key) }
    }

    private fun read(raw: String?): ChatDetailConfig = raw
        ?.let { runCatching { json.decodeFromString(ChatDetailConfig.serializer(), it) }.getOrNull() }
        ?.normalized()
        ?: ChatDetailConfig()

    private fun encode(config: ChatDetailConfig): String =
        json.encodeToString(ChatDetailConfig.serializer(), config.normalized())
}
