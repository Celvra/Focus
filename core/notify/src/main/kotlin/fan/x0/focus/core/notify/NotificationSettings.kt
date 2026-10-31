package fan.x0.focus.core.notify

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

private val Context.notificationDataStore by preferencesDataStore(name = "notification_settings")

@Single
class NotificationSettings(@Provided private val context: Context) {
    private val enabledKey = booleanPreferencesKey("enabled")

    val enabled: Flow<Boolean> = context.notificationDataStore.data.map { prefs ->
        prefs[enabledKey] ?: true
    }

    suspend fun enabledOnce(): Boolean = enabled.first()

    suspend fun setEnabled(value: Boolean) {
        context.notificationDataStore.edit { prefs -> prefs[enabledKey] = value }
    }
}
