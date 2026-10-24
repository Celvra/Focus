package fan.x0.focus.data.assistant

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class GoalStore(private val database: AssistantDatabase) {

    private val columns =
        "id, title, detail, status, due_at, progress, note, created_at, updated_at, category, icon"

    suspend fun list(): List<Goal> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            """
            SELECT $columns
            FROM goals
            ORDER BY CASE status WHEN 'active' THEN 0 WHEN 'paused' THEN 1 ELSE 2 END, updated_at DESC
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toGoal()) }
        }
    }

    suspend fun active(): List<Goal> = list().filter { it.status == GoalStatus.Active }

    suspend fun get(id: String): Goal? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT $columns FROM goals WHERE id = ?",
            arrayOf(id),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.toGoal() else null }
    }

    suspend fun add(
        title: String,
        detail: String?,
        dueAt: Long?,
        category: String = "",
        icon: String = "",
    ): Goal = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val goal = Goal(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            detail = detail?.trim()?.ifBlank { null },
            status = GoalStatus.Active,
            dueAt = dueAt,
            progress = 0,
            note = null,
            createdAt = now,
            updatedAt = now,
            category = category.trim(),
            icon = TodayIcons.sanitize(icon),
        )
        val values = ContentValues().apply {
            put("id", goal.id)
            put("title", goal.title)
            put("detail", goal.detail)
            put("status", "active")
            put("due_at", goal.dueAt)
            put("progress", 0)
            putNull("note")
            put("created_at", now)
            put("updated_at", now)
            put("category", goal.category)
            put("icon", goal.icon)
        }
        database.writableDatabase.insertOrThrow("goals", null, values)
        goal
    }

    suspend fun update(
        id: String,
        title: String? = null,
        detail: String? = null,
        dueAt: Long? = null,
        status: GoalStatus? = null,
        progress: Int? = null,
        note: String? = null,
        category: String? = null,
        icon: String? = null,
    ): Goal? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            if (title != null) put("title", title.trim())
            if (detail != null) put("detail", detail.trim())
            if (dueAt != null) put("due_at", dueAt)
            if (status != null) put("status", status.name.lowercase())
            if (progress != null) put("progress", progress.coerceIn(0, 100))
            if (note != null) put("note", note.trim())
            if (category != null) put("category", category.trim())
            if (icon != null) put("icon", TodayIcons.sanitize(icon))
            put("updated_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("goals", values, "id = ?", arrayOf(id))
        get(id)
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("goals", "id = ?", arrayOf(id)) > 0
    }

    private fun Cursor.toGoal(): Goal = Goal(
        id = getString(0),
        title = getString(1),
        detail = if (isNull(2)) null else getString(2),
        status = when (getString(3)) {
            "done" -> GoalStatus.Done
            "paused" -> GoalStatus.Paused
            else -> GoalStatus.Active
        },
        dueAt = if (isNull(4)) null else getLong(4),
        progress = getInt(5),
        note = if (isNull(6)) null else getString(6),
        createdAt = getLong(7),
        updatedAt = getLong(8),
        category = getString(9),
        icon = getString(10),
    )
}
