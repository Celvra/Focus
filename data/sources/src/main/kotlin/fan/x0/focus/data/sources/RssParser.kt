package fan.x0.focus.data.sources

import org.jsoup.Jsoup
import org.koin.core.annotation.Single
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Single
class RssParser {
    fun parse(input: InputStream): ParsedFeed {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(input, null)

        var feedTitle = ""
        val items = mutableListOf<ParsedArticle>()
        var current: MutableArticle? = null
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase(Locale.ROOT).orEmpty()
            if (event == XmlPullParser.START_TAG) {
                when {
                    name == "item" || name == "entry" -> current = MutableArticle()
                    current != null -> readArticleTag(parser, name, current)
                    name == "title" && feedTitle.isBlank() -> feedTitle = readElementText(parser)
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (name == "item" || name == "entry") {
                    current?.toParsed()?.let { items += it }
                    current = null
                }
            }
            event = parser.next()
        }
        return ParsedFeed(title = feedTitle.trim(), articles = items)
    }

    private fun readArticleTag(parser: XmlPullParser, name: String, article: MutableArticle) {
        when (name) {
            "title", "description", "summary", "content", "encoded",
            "pubdate", "published", "updated", "guid", "id", "creator",
            -> article.applyTag(name, readElementText(parser))
            "name" -> {
                val value = readElementText(parser)
                if (article.author.isBlank()) article.author = value
            }
            "link" -> {
                val href = parser.getAttributeValue(null, "href")
                val rel = parser.getAttributeValue(null, "rel")
                if (!href.isNullOrBlank()) {
                    if ((rel == null || rel == "alternate") && article.url.isBlank()) {
                        article.url = href.trim()
                    }
                } else {
                    val value = readElementText(parser)
                    if (value.isNotBlank() && article.url.isBlank()) article.url = value
                }
            }
        }
    }

    // Collects text until the matching end tag, so nested markup does not break parsing.
    private fun readElementText(parser: XmlPullParser): String {
        val depth = parser.depth
        val builder = StringBuilder()
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth)) {
            when (event) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> builder.append(parser.text)
                XmlPullParser.END_TAG -> builder.append(' ')
                else -> Unit
            }
            event = parser.next()
        }
        return builder.toString().trim()
    }

    internal fun parseDate(raw: String): Long? {
        val value = raw.trim()
        if (value.isBlank()) return null
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
            ?: DATE_FORMATS.firstNotNullOfOrNull { format ->
                runCatching { OffsetDateTime.parse(value, format).toInstant().toEpochMilli() }.getOrNull()
            }
    }

    private class MutableArticle {
        var title = ""
        var url = ""
        var author = ""
        var summary = ""
        var content = ""
        var publishedAt: Long? = null
        var guid = ""

        fun applyTag(name: String, value: String) {
            val text = value.trim()
            if (text.isBlank()) return
            when (name) {
                "title" -> if (title.isBlank()) title = text
                "description", "summary" -> if (summary.isBlank()) summary = text
                "content", "encoded" -> if (content.isBlank()) content = text
                "pubdate", "published", "updated" -> if (publishedAt == null) publishedAt = parseDateStatic(text)
                "guid", "id" -> if (guid.isBlank()) guid = text
                "creator" -> if (author.isBlank()) author = text
            }
        }

        fun toParsed(): ParsedArticle? {
            val link = url.ifBlank { guid }
            if (!link.startsWith("http://") && !link.startsWith("https://")) return null
            return ParsedArticle(
                url = link,
                title = title.ifBlank { link },
                author = author.ifBlank { null },
                summary = htmlToText(summary).ifBlank { null },
                content = htmlToText(content.ifBlank { summary }).take(MAX_CONTENT),
                publishedAt = publishedAt,
            )
        }
    }

    companion object {
        private const val MAX_CONTENT = 20_000

        private val DATE_FORMATS = listOf(
            DateTimeFormatter.RFC_1123_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
        )

        private fun parseDateStatic(raw: String): Long? {
            val value = raw.trim()
            if (value.isBlank()) return null
            return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
                ?: DATE_FORMATS.firstNotNullOfOrNull { format ->
                    runCatching { OffsetDateTime.parse(value, format).toInstant().toEpochMilli() }.getOrNull()
                }
        }

        fun htmlToText(html: String?): String {
            if (html.isNullOrBlank()) return ""
            return runCatching { Jsoup.parse(html).text().trim() }.getOrDefault(html.trim())
        }
    }
}
