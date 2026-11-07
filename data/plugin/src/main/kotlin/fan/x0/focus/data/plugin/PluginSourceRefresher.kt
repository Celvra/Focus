package fan.x0.focus.data.plugin

import fan.x0.focus.data.sources.ExternalArticle
import fan.x0.focus.data.sources.FeedRepository
import fan.x0.focus.data.sources.SourceExtension
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single

@Single
class PluginSourceRefresher(
    private val registry: PluginRegistry,
    private val feeds: FeedRepository,
) : SourceExtension {
    override suspend fun refresh(): Int {
        var added = 0
        registry.sources().forEach { loaded ->
            val source = loaded.source
            val articles = runCatching { withTimeout(60_000) { source.fetch() } }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                registry.log().write(loaded.pluginId, "W", "Source ${source.id} failed", it)
                return@forEach
            }
            added += feeds.ingestExternal(
                feedId = "plugin:${loaded.pluginId}:${source.id}",
                feedUrl = "plugin://${loaded.pluginId}/${source.id}",
                title = "${loaded.pluginName}: ${source.name}",
                articles = articles.map { article ->
                    ExternalArticle(
                        title = article.title,
                        url = article.url,
                        content = article.content,
                        summary = article.summary,
                        author = article.author,
                        publishedAt = article.publishedAt,
                    )
                },
            )
        }
        return added
    }
}
