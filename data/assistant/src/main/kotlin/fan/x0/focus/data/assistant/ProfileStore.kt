package fan.x0.focus.data.assistant

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class ProfileStore(private val database: AssistantDatabase) {
    private val revisionCount = MutableStateFlow(0)

    val revision: StateFlow<Int> = revisionCount.asStateFlow()

    suspend fun read(): AssistantProfile = withContext(Dispatchers.IO) {
        val values = mutableMapOf<String, String>()
        database.readableDatabase.rawQuery("SELECT key, value FROM profile", null).use { cursor ->
            while (cursor.moveToNext()) values[cursor.getString(0)] = cursor.getString(1)
        }
        AssistantProfile(
            name = values["name"],
            tone = values["tone"],
            focus = values["focus"],
            language = values["language"],
        )
    }

    suspend fun update(
        name: String? = null,
        tone: String? = null,
        focus: String? = null,
        language: String? = null,
    ) {
        withContext(Dispatchers.IO) {
            val db = database.writableDatabase
            db.beginTransaction()
            try {
                mapOf("name" to name, "tone" to tone, "focus" to focus, "language" to language)
                    .forEach { (key, value) ->
                        if (value.isNullOrBlank()) return@forEach
                        val values = ContentValues().apply {
                            put("key", key)
                            put("value", value.trim())
                        }
                        db.insertWithOnConflict("profile", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
                    }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        revisionCount.update { it + 1 }
    }
}
