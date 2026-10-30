package fan.x0.focus.data.assistant

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.koin.core.annotation.Single

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
    val source: String,
    val published: String = "",
)

class WebSearchException(message: String) : Exception(message)

@Single
class WebSearchRepository(
    private val http: HttpClient,
    private val settings: WebSearchSettings,
    private val secrets: SecretStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scrapeMutex = Mutex()
    private var lastScrapeAt = 0L

    suspend fun search(
        query: String,
        count: Int = 5,
        provider: WebSearchProvider? = null,
        language: String = "",
        timeRange: String = "",
    ): List<WebSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) throw WebSearchException("Search query is empty")
        val config = settings.configOnce()
        val selected = provider
            ?: config.defaultProvider.takeIf { config.enabled(it) }
            ?: WebSearchProvider.entries.firstOrNull { config.enabled(it) }
            ?: throw WebSearchException("No web search provider is enabled")
        if (!config.enabled(selected)) {
            throw WebSearchException("${selected.name} is disabled in settings")
        }
        val key = secrets.secret(WebSearchSecrets.key(selected)).orEmpty()
        if (selected.requiresKey && key.isBlank()) {
            throw WebSearchException("${selected.name} needs an API key. Add it in Settings, Tools, Web search.")
        }
        val limit = count.coerceIn(1, 10)
        return when (selected) {
            WebSearchProvider.DeepSeek -> deepSeek(trimmed, limit, key)
            WebSearchProvider.Tavily -> tavily(trimmed, limit, key, timeRange)
            WebSearchProvider.TinyFish -> tinyFish(trimmed, limit, key)
            WebSearchProvider.Exa -> exa(trimmed, limit, key)
            WebSearchProvider.Bing -> bing(trimmed, limit)
            WebSearchProvider.DuckDuckGo -> duckDuckGo(trimmed, limit)
            WebSearchProvider.Yandex -> yandex(trimmed, limit)
        }
    }

    suspend fun defaultProvider(): WebSearchProvider = settings.configOnce().defaultProvider

    suspend fun test(provider: WebSearchProvider): List<WebSearchResult> =
        search("OpenAI", 3, provider)

    private suspend fun deepSeek(query: String, count: Int, key: String): List<WebSearchResult> {
        val body = buildJsonObject {
            put("query", query)
            put("count", count)
            put("need_content", true)
        }
        val response = http.post("https://api.deepseek.com/v1/tools/search") {
            header(HttpHeaders.Authorization, "Bearer $key")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw error("DeepSeek", response.status.value, text)
        val root = parseObject(text)
        return root.array("results").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            WebSearchResult(
                title = obj.string("title").orEmpty(),
                url = obj.string("url").orEmpty(),
                snippet = obj.string("summary") ?: obj.string("snippet") ?: obj.string("content").orEmpty(),
                source = obj.string("site_name") ?: "DeepSeek",
                published = obj.string("date").orEmpty(),
            )
        }.filter { it.title.isNotBlank() && it.url.isNotBlank() }.take(count)
    }

    private suspend fun tavily(query: String, count: Int, key: String, timeRange: String): List<WebSearchResult> {
        val body = buildJsonObject {
            put("api_key", key)
            put("query", query)
            put("max_results", count)
            put("include_answer", false)
            put("search_depth", "basic")
            if (timeRange.isNotBlank()) put("time_range", timeRange)
        }
        val response = http.post("https://api.tavily.com/search") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw error("Tavily", response.status.value, text)
        return parseObject(text).array("results").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            WebSearchResult(
                title = obj.string("title").orEmpty(),
                url = obj.string("url").orEmpty(),
                snippet = obj.string("content").orEmpty(),
                source = "Tavily",
                published = obj.string("published_date").orEmpty(),
            )
        }.take(count)
    }

    private suspend fun tinyFish(query: String, count: Int, key: String): List<WebSearchResult> {
        val response = http.get("https://api.search.tinyfish.ai") {
            header("X-API-Key", key)
            parameter("query", query)
            parameter("count", count)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw error("TinyFish", response.status.value, text)
        return parseObject(text).array("results").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            WebSearchResult(
                title = obj.string("title").orEmpty(),
                url = obj.string("url").orEmpty(),
                snippet = obj.string("snippet").orEmpty(),
                source = obj.string("site_name") ?: "TinyFish",
            )
        }.take(count)
    }

    private suspend fun exa(query: String, count: Int, key: String): List<WebSearchResult> {
        val body = buildJsonObject {
            put("query", query)
            put("numResults", count)
            put("type", "auto")
        }
        val response = http.post("https://api.exa.ai/search") {
            header("x-api-key", key)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw error("Exa", response.status.value, text)
        return parseObject(text).array("results").mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val snippet = obj.string("summary")
                ?: obj.string("text")
                ?: obj.array("highlights").firstOrNull()?.let { (it as? JsonPrimitive)?.content }
                ?: ""
            WebSearchResult(
                title = obj.string("title").orEmpty(),
                url = obj.string("url").orEmpty(),
                snippet = snippet,
                source = "Exa",
                published = obj.string("publishedDate").orEmpty(),
            )
        }.take(count)
    }

    private suspend fun bing(query: String, count: Int): List<WebSearchResult> = scrape {
        val url = "https://www.bing.com/search?q=" + encode(query)
        val document = fetch(url)
        document.select("li.b_algo").mapNotNull { element ->
            val link = element.selectFirst("h2 a") ?: return@mapNotNull null
            val snippet = element.selectFirst(".b_caption p")?.text()
                ?: element.selectFirst("p")?.text().orEmpty()
            WebSearchResult(
                title = link.text(),
                url = link.absUrl("href"),
                snippet = snippet,
                source = "Bing",
            )
        }.take(count)
    }

    private suspend fun duckDuckGo(query: String, count: Int): List<WebSearchResult> = scrape {
        val url = "https://html.duckduckgo.com/html/?q=" + encode(query)
        val document = fetch(url)
        document.select(".result").mapNotNull { element ->
            val link = element.selectFirst("a.result__a") ?: return@mapNotNull null
            val snippet = element.selectFirst(".result__snippet")?.text().orEmpty()
            WebSearchResult(
                title = link.text(),
                url = link.absUrl("href"),
                snippet = snippet,
                source = "DuckDuckGo",
            )
        }.take(count)
    }

    private suspend fun yandex(query: String, count: Int): List<WebSearchResult> = scrape {
        val url = "https://yandex.com/search/?text=" + encode(query)
        val document = fetch(url)
        document.select("li.serp-item").mapNotNull { element ->
            val link = element.selectFirst("a.OrganicTitle-Link, a.Link_theme_normal, h2 a")
                ?: return@mapNotNull null
            val snippet = element.selectFirst(".OrganicTextContentSpan, .TextContainer")?.text().orEmpty()
            WebSearchResult(
                title = link.text(),
                url = link.absUrl("href"),
                snippet = snippet,
                source = "Yandex",
            )
        }.take(count)
    }

    private suspend fun scrape(block: suspend () -> List<WebSearchResult>): List<WebSearchResult> =
        scrapeMutex.withLock {
            val now = System.currentTimeMillis()
            val wait = SCRAPE_INTERVAL_MS - (now - lastScrapeAt)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastScrapeAt = System.currentTimeMillis()
            block()
        }

    private suspend fun fetch(url: String): Document {
        val response = http.get(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
            header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.8")
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw WebSearchException("Search page returned HTTP ${response.status.value}")
        }
        return Jsoup.parse(text, url)
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun error(provider: String, status: Int, body: String): WebSearchException {
        val message = runCatching {
            val root = parseObject(body)
            (root["error"] as? JsonObject)?.string("message")
                ?: root.string("message")
                ?: root.string("detail")
        }.getOrNull()
        return WebSearchException("$provider HTTP $status: ${message ?: body.take(200)}")
    }

    private fun parseObject(text: String): JsonObject =
        runCatching { json.parseToJsonElement(text) as JsonObject }
            .getOrElse { throw WebSearchException("Provider returned invalid JSON") }

    private fun JsonObject.array(key: String): List<kotlinx.serialization.json.JsonElement> =
        (this[key] as? JsonArray).orEmpty()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36"
        private const val SCRAPE_INTERVAL_MS = 1500L
    }
}
