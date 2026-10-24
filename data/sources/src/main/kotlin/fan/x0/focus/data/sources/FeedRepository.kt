package fan.x0.focus.data.sources

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import fan.x0.focus.core.error.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.ByteArrayInputStream
import java.util.UUID

data class RefreshReport(val added: Int, val feeds: Int, val failed: Int)

@Single
class FeedRepository(
    private val database: SourcesDatabase,
    private val http: HttpClient,
    private val parser: RssParser,
) {
    private val revision = MutableStateFlow(0L)

    // Bumped after every write so screens can reload.
    val changes: StateFlow<Long> = revision.asStateFlow()

    suspend fun listFeeds(): List<Feed> = withContext(Dispatchers.IO) {
        database.read { db -> selectFeeds(db) }
    }

    suspend fun getFeed(id: String): Feed? = withContext(Dispatchers.IO) {
        database.read { db -> selectFeeds(db).firstOrNull { it.id == id } }
    }

    suspend fun listArticles(limit: Int = 50, feedId: String? = null): List<Article> =
        withContext(Dispatchers.IO) {
            database.read { db -> selectArticles(db, limit, feedId, null) }
        }

    suspend fun searchArticles(query: String, limit: Int = 20): List<Article> =
        withContext(Dispatchers.IO) {
            database.read { db -> selectArticles(db, limit, null, query.trim()) }
        }

    suspend fun getArticle(id: String): Article? = withContext(Dispatchers.IO) {
        database.read { db ->
            db.rawQuery("$ARTICLE_SELECT WHERE a.id = ? LIMIT 1", arrayOf(id)).use { cursor ->
                if (cursor.moveToFirst()) cursor.toArticle() else null
            }
        }
    }

    suspend fun unreadCount(): Int = withContext(Dispatchers.IO) {
        database.read { db ->
            db.rawQuery("SELECT COUNT(*) FROM articles WHERE is_read = 0", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    suspend fun addFeed(url: String): Feed = withContext(Dispatchers.IO) {
        val normalized = normalize(url)
        val existing = database.read { db ->
            db.rawQuery("SELECT id FROM feeds WHERE url = ? LIMIT 1", arrayOf(normalized)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }
        if (existing != null) throw SourceException(SourceError.Unsupported, "feed already added")

        val parsed = fetch(normalized)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val title = parsed.title.ifBlank { hostOf(normalized) }
        val values = ContentValues().apply {
            put("id", id)
            put("url", normalized)
            put("title", title)
            put("added_at", now)
            put("last_fetch_at", now)
            putNull("error")
        }
        database.write { db ->
            db.insertOrThrow("feeds", null, values)
            insertArticles(db, id, parsed.articles, now)
        }
        bump()
        Feed(id, normalized, title, now, now, null)
    }

    suspend fun removeFeed(id: String) = withContext(Dispatchers.IO) {
        database.write { db ->
            db.delete("articles", "feed_id = ?", arrayOf(id))
            db.delete("feeds", "id = ?", arrayOf(id))
        }
        bump()
    }

    suspend fun refreshAll(): RefreshReport = withContext(Dispatchers.IO) {
        val feeds = database.read { db -> selectFeeds(db) }
        var added = 0
        var failed = 0
        val now = System.currentTimeMillis()
        feeds.forEach { feed ->
            try {
                val parsed = fetch(feed.url)
                database.write { db ->
                    added += insertArticles(db, feed.id, parsed.articles, now)
                    val values = ContentValues().apply {
                        put("last_fetch_at", now)
                        putNull("error")
                        if (parsed.title.isNotBlank()) put("title", parsed.title)
                    }
                    db.update("feeds", values, "id = ?", arrayOf(feed.id))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SourceException) {
                failed++
                database.write { db ->
                    val values = ContentValues().apply {
                        put("last_fetch_at", now)
                        put("error", e.message)
                    }
                    db.update("feeds", values, "id = ?", arrayOf(feed.id))
                }
            }
        }
        bump()
        RefreshReport(added = added, feeds = feeds.size, failed = failed)
    }

    suspend fun markRead(id: String) = withContext(Dispatchers.IO) {
        database.write { db ->
            val values = ContentValues().apply { put("is_read", 1) }
            db.update("articles", values, "id = ?", arrayOf(id))
        }
        bump()
    }

    suspend fun markAllRead() = withContext(Dispatchers.IO) {
        database.write { db ->
            val values = ContentValues().apply { put("is_read", 1) }
            db.update("articles", values, "is_read = 0", null)
        }
        bump()
    }

    suspend fun deleteArticle(id: String) = withContext(Dispatchers.IO) {
        database.write { db -> db.delete("articles", "id = ?", arrayOf(id)) }
        bump()
    }

    private suspend fun fetch(url: String): ParsedFeed {
        val response = try {
            http.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.5")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            throw SourceException(SourceError.Network, "feed request failed", e)
        } catch (e: Exception) {
            throw SourceException(SourceError.Network, "feed request failed", e)
        }
        if (!response.status.isSuccess()) {
            throw SourceException(SourceError.Network, "feed returned ${response.status.value}")
        }
        val bytes = response.bodyAsBytes()
        return try {
            parser.parse(ByteArrayInputStream(bytes))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SourceException(SourceError.Parse, "feed is not rss or atom", e)
        }
    }

    private fun insertArticles(
        db: SQLiteDatabase,
        feedId: String,
        articles: List<ParsedArticle>,
        now: Long,
    ): Int {
        var added = 0
        articles.take(MAX_ARTICLES_PER_FETCH).forEach { article ->
            val values = ContentValues().apply {
                put("id", UUID.nameUUIDFromBytes(article.url.toByteArray()).toString())
                put("feed_id", feedId)
                put("url", article.url)
                put("title", article.title.take(300))
                put("author", article.author)
                put("summary", article.summary?.take(1000))
                put("content", article.content)
                put("published_at", article.publishedAt)
                put("fetched_at", now)
                put("is_read", 0)
            }
            if (db.insertWithOnConflict("articles", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                added++
            }
        }
        return added
    }

    private fun selectFeeds(db: SQLiteDatabase): List<Feed> =
        db.rawQuery("SELECT id, url, title, added_at, last_fetch_at, error FROM feeds ORDER BY added_at DESC", null)
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Feed(
                                id = cursor.getString(0),
                                url = cursor.getString(1),
                                title = cursor.getString(2),
                                addedAt = cursor.getLong(3),
                                lastFetchAt = cursor.nullableLong(4),
                                error = cursor.nullableString(5),
                            ),
                        )
                    }
                }
            }

    private fun selectArticles(
        db: SQLiteDatabase,
        limit: Int,
        feedId: String?,
        query: String?,
    ): List<Article> {
        val where = when {
            feedId != null -> "WHERE a.feed_id = ?"
            !query.isNullOrBlank() -> "WHERE a.title LIKE ? OR a.content LIKE ?"
            else -> ""
        }
        val args = when {
            feedId != null -> arrayOf(feedId, limit.toString())
            !query.isNullOrBlank() -> arrayOf("%$query%", "%$query%", limit.toString())
            else -> arrayOf(limit.toString())
        }
        return db.rawQuery("$ARTICLE_SELECT $where ORDER BY COALESCE(a.published_at, a.fetched_at) DESC LIMIT ?", args)
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.toArticle())
                }
            }
    }

    private fun Cursor.toArticle(): Article = Article(
        id = getString(0),
        feedId = getString(1),
        url = getString(2),
        title = getString(3),
        author = nullableString(4),
        summary = nullableString(5),
        content = getString(6),
        publishedAt = nullableLong(7),
        fetchedAt = getLong(8),
        read = getInt(9) == 1,
        feedTitle = getString(10),
    )

    private fun normalize(raw: String): String {
        val value = raw.trim()
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            throw SourceException(SourceError.Unsupported, "feed url must start with http")
        }
        return value
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("").ifBlank { url }

    private fun bump() {
        revision.value = revision.value + 1
    }

    private fun Cursor.nullableString(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.nullableLong(index: Int): Long? =
        if (isNull(index)) null else getLong(index)

    companion object {
        private const val MAX_ARTICLES_PER_FETCH = 60
        private const val USER_AGENT = "Focus/1.0 (Android; RSS reader)"

        private const val ARTICLE_SELECT = """
            SELECT a.id, a.feed_id, a.url, a.title, a.author, a.summary, a.content,
                   a.published_at, a.fetched_at, a.is_read, f.title AS feed_title
            FROM articles a JOIN feeds f ON f.id = a.feed_id
        """
    }
}
