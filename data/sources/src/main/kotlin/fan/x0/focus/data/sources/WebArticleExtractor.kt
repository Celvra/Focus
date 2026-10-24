package fan.x0.focus.data.sources

import fan.x0.focus.core.error.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.koin.core.annotation.Single

@Single
class WebArticleExtractor(private val http: HttpClient) {
    suspend fun extract(url: String): WebArticle {
        val target = url.trim()
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            throw SourceException(SourceError.Unsupported, "url must start with http")
        }
        val response = try {
            http.get(target) {
                header(HttpHeaders.UserAgent, BROWSER_UA)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            throw SourceException(SourceError.Network, "page request failed", e)
        } catch (e: Exception) {
            throw SourceException(SourceError.Network, "page request failed", e)
        }
        if (!response.status.isSuccess()) {
            throw SourceException(SourceError.Network, "page returned ${response.status.value}")
        }
        return extractFromHtml(target, response.bodyAsText())
    }

    internal fun extractFromHtml(url: String, html: String): WebArticle {
        val document = Jsoup.parse(html, url)
        document.select("script, style, noscript, iframe, form").remove()
        val title = meta(document, "og:title")
            ?: document.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
            ?: document.title().takeIf { it.isNotBlank() }
            ?: url
        val siteName = meta(document, "og:site_name")
            ?: runCatching { java.net.URI(url).host }.getOrNull()
        val byline = meta(document, "author")
            ?: meta(document, "article:author")
            ?: document.selectFirst(".byline, .author")?.text()?.takeIf { it.isNotBlank() }

        val candidate = listOf(
            "article",
            "main",
            "[role=main]",
            ".post-content",
            ".entry-content",
            ".article-body",
            "#content",
            "body",
        ).mapNotNull { document.selectFirst(it) }
            .maxByOrNull { it.text().length }
            ?: document.body()

        candidate.select("nav, header, footer, aside, .share, .comments, .ad, .ads").remove()
        val paragraphs = candidate.select("h1, h2, h3, p, li, blockquote")
            .map { it.text().trim() }
            .filter { it.length > 0 }
        val text = if (paragraphs.size >= 3) paragraphs.joinToString("\n\n") else candidate.text()
        if (text.isBlank()) throw SourceException(SourceError.Parse, "page has no readable text")

        return WebArticle(
            url = url,
            title = title.trim().take(300),
            siteName = siteName,
            byline = byline,
            text = text.take(MAX_CHARS),
        )
    }

    private fun meta(document: Document, key: String): String? {
        val selector = "meta[property=$key], meta[name=$key]"
        return document.selectFirst(selector)?.attr("content")?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val MAX_CHARS = 20_000
        private const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}
