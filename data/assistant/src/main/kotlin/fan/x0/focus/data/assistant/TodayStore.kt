package fan.x0.focus.data.assistant

import android.content.ContentValues
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.util.UUID

@Single
class TodayStore(private val database: AssistantDatabase) {

    suspend fun updates(limit: Int = 40): List<Update> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            """
            SELECT id, headline, body, reason, category, icon, article_id, created_at, feedback
            FROM updates
            ORDER BY created_at DESC
            LIMIT $limit
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toUpdate()) }
        }
    }

    suspend fun ideas(limit: Int = 40): List<Idea> = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            """
            SELECT id, title, detail, steps, category, icon, created_at
            FROM ideas
            WHERE dismissed = 0
            ORDER BY created_at DESC
            LIMIT $limit
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toIdea()) }
        }
    }

    suspend fun update(id: String): Update? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, headline, body, reason, category, icon, article_id, created_at, feedback " +
                "FROM updates WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toUpdate() else null
        }
    }

    suspend fun idea(id: String): Idea? = withContext(Dispatchers.IO) {
        database.readableDatabase.rawQuery(
            "SELECT id, title, detail, steps, category, icon, created_at FROM ideas WHERE id = ? LIMIT 1",
            arrayOf(id),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toIdea() else null
        }
    }

    suspend fun postUpdate(
        headline: String,
        body: String,
        reason: String,
        category: String,
        icon: String,
        articleId: String?,
    ): Update = withContext(Dispatchers.IO) {
        val update = Update(
            id = UUID.randomUUID().toString(),
            headline = headline.trim().take(140),
            body = body.trim().take(900),
            reason = reason.trim().take(400),
            category = category.trim().take(24),
            icon = TodayIcons.sanitize(icon),
            articleId = articleId?.trim()?.takeIf { it.isNotBlank() },
            createdAt = System.currentTimeMillis(),
            feedback = 0,
        )
        val values = ContentValues().apply {
            put("id", update.id)
            put("headline", update.headline)
            put("body", update.body)
            put("reason", update.reason)
            put("category", update.category)
            put("icon", update.icon)
            put("article_id", update.articleId)
            put("created_at", update.createdAt)
            put("feedback", 0)
        }
        database.writableDatabase.insertOrThrow("updates", null, values)
        update
    }

    suspend fun saveIdea(
        title: String,
        detail: String,
        steps: List<IdeaStep>,
        category: String,
        icon: String,
    ): Idea = withContext(Dispatchers.IO) {
        val idea = Idea(
            id = UUID.randomUUID().toString(),
            title = title.trim().take(140),
            detail = detail.trim().take(1200),
            steps = steps.take(6).map { IdeaStep(it.title.trim().take(120), it.detail.trim().take(300)) }
                .filter { it.title.isNotBlank() },
            category = category.trim().take(24),
            icon = TodayIcons.sanitize(icon),
            createdAt = System.currentTimeMillis(),
        )
        val values = ContentValues().apply {
            put("id", idea.id)
            put("title", idea.title)
            put("detail", idea.detail)
            put("steps", Json.encodeToString(idea.steps))
            put("category", idea.category)
            put("icon", idea.icon)
            put("created_at", idea.createdAt)
            put("dismissed", 0)
        }
        database.writableDatabase.insertOrThrow("ideas", null, values)
        idea
    }

    suspend fun dismissIdea(id: String) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("dismissed", 1) }
        database.writableDatabase.update("ideas", values, "id = ?", arrayOf(id))
    }

    suspend fun deleteUpdate(id: String): Boolean = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("updates", "id = ?", arrayOf(id)) > 0
    }

    suspend fun deleteIdea(id: String): Boolean = withContext(Dispatchers.IO) {
        database.writableDatabase.delete("ideas", "id = ?", arrayOf(id)) > 0
    }

    suspend fun setFeedback(id: String, feedback: Int) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("feedback", feedback.coerceIn(-1, 1)) }
        database.writableDatabase.update("updates", values, "id = ?", arrayOf(id))
    }

    private fun Cursor.toUpdate(): Update = Update(
        id = getString(0),
        headline = getString(1),
        body = getString(2),
        reason = getString(3),
        category = getString(4),
        icon = getString(5),
        articleId = if (isNull(6)) null else getString(6),
        createdAt = getLong(7),
        feedback = getInt(8),
    )

    private fun Cursor.toIdea(): Idea = Idea(
        id = getString(0),
        title = getString(1),
        detail = getString(2),
        steps = runCatching { Json.decodeFromString<List<IdeaStep>>(getString(3)) }
            .getOrDefault(emptyList()),
        category = getString(4),
        icon = getString(5),
        createdAt = getLong(6),
    )
}
