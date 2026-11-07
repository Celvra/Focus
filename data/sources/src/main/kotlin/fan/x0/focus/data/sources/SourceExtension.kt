package fan.x0.focus.data.sources

data class ExternalArticle(
    val title: String,
    val url: String,
    val content: String,
    val summary: String = "",
    val author: String = "",
    val publishedAt: Long = 0,
)

interface SourceExtension {
    suspend fun refresh(): Int
}
