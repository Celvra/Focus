package fan.x0.focus.data.sources

enum class SourceError { PermissionDenied, Network, Parse, File, Storage, NotFound, Unsupported }

class SourceException(val kind: SourceError, message: String, cause: Throwable? = null) :
    Exception(message, cause)

data class Feed(
    val id: String,
    val url: String,
    val title: String,
    val addedAt: Long,
    val lastFetchAt: Long?,
    val error: String?,
)

data class Article(
    val id: String,
    val feedId: String,
    val url: String,
    val title: String,
    val author: String?,
    val summary: String?,
    val content: String,
    val publishedAt: Long?,
    val fetchedAt: Long,
    val read: Boolean,
    val feedTitle: String,
)

data class ParsedFeed(
    val title: String,
    val articles: List<ParsedArticle>,
)

data class ParsedArticle(
    val url: String,
    val title: String,
    val author: String?,
    val summary: String?,
    val content: String,
    val publishedAt: Long?,
)

data class CalendarEvent(
    val id: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
)

data class SourceFile(
    val uri: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long,
)

data class WebArticle(
    val url: String,
    val title: String,
    val siteName: String?,
    val byline: String?,
    val text: String,
)
