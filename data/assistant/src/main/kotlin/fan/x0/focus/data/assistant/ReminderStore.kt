package fan.x0.focus.data.assistant

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class ReminderStore(private val database: AssistantDatabase) {
    suspend fun upcoming(now: Long): List<Reminder> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, text, at, done FROM reminders WHERE done = 0 AND at >= ? ORDER BY at ASC LIMIT 20",
            arrayOf(now.toString()),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toReminder()) }
        }
    }

    suspend fun add(text: String, at: Long, goalId: String? = null): Reminder = withContext(Dispatchers.IO) {
        val reminder = Reminder(UUID.randomUUID().toString(), text.trim(), at, false)
        val values = ContentValues().apply {
            put("id", reminder.id)
            put("text", reminder.text)
            put("at", reminder.at)
            put("done", 0)
            put("goal_id", goalId)
            put("created_at", System.currentTimeMillis())
        }
        database.writableDatabase.insertOrThrow("reminders", null, values)
        reminder
    }

    suspend fun markDone(id: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("done", 1) }
        database.writableDatabase.update("reminders", values, "id = ?", arrayOf(id))
    }

    private fun Cursor.toReminder(): Reminder = Reminder(
        id = getString(0),
        text = getString(1),
        at = getLong(2),
        done = getInt(3) == 1,
    )
}
