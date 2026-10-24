package fan.x0.focus.data.assistant

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class MemoryStore(private val database: AssistantDatabase) {
    private val revisionCount = MutableStateFlow(0)

    val revision: StateFlow<Int> = revisionCount.asStateFlow()

    suspend fun list(limit: Int = 50): List<Memory> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, content, created_at FROM memories ORDER BY created_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toMemory()) }
        }
    }

    suspend fun search(query: String, limit: Int = 20): List<Memory> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, content, created_at FROM memories WHERE content LIKE ? ORDER BY created_at DESC LIMIT ?",
            arrayOf("%$query%", limit.toString()),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toMemory()) }
        }
    }

    suspend fun get(id: String): Memory? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, content, created_at FROM memories WHERE id = ?",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toMemory() else null }
    }

    suspend fun add(content: String): Memory {
        val memory = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val memory = Memory(UUID.randomUUID().toString(), content.trim(), now)
            val values = ContentValues().apply {
                put("id", memory.id)
                put("content", memory.content)
                put("created_at", now)
                put("updated_at", now)
            }
            database.writableDatabase.insertOrThrow("memories", null, values)
            memory
        }
        revisionCount.update { it + 1 }
        return memory
    }

    suspend fun update(id: String, content: String): Boolean {
        val changed = withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("content", content.trim())
                put("updated_at", System.currentTimeMillis())
            }
            database.writableDatabase.update("memories", values, "id = ?", arrayOf(id)) > 0
        }
        if (changed) revisionCount.update { it + 1 }
        return changed
    }

    suspend fun delete(id: String): Boolean {
        val removed = withContext(Dispatchers.IO) {
            database.writableDatabase.delete("memories", "id = ?", arrayOf(id)) > 0
        }
        if (removed) revisionCount.update { it + 1 }
        return removed
    }

    private fun Cursor.toMemory(): Memory = Memory(
        id = getString(0),
        content = getString(1),
        createdAt = getLong(2),
    )
}
