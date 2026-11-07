package fan.x0.focus.data.assistant

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class RouterMemoryStore(private val database: AssistantDatabase) {
    suspend fun read(): String = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT value FROM profile WHERE key = ? LIMIT 1",
            arrayOf(KEY),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }
    }

    suspend fun write(content: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("key", KEY)
            put("value", content.trim().take(MAX_CHARS))
        }
        database.writableDatabase.insertWithOnConflict(
            "profile",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private companion object {
        const val KEY = "router_memory"
        const val MAX_CHARS = 8_000
    }
}
