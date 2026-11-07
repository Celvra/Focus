package fan.x0.focus.data.plugin

import fan.x0.focus.plugin.PluginApi
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Serializable
data class MarketplacePlugin(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Long,
    val summary: String = "",
    val description: String = "",
    val author: String = "",
    val download: String,
    val sha256: String = "",
    val apiVersion: Int = PluginApi.VERSION,
    val minHostVersion: String = "0.0.0",
)

@Serializable
data class MarketplaceIndex(
    val version: Int = 1,
    val plugins: List<MarketplacePlugin> = emptyList(),
)

@Single
class PluginMarketplace(
    private val http: HttpClient,
    private val settings: PluginSettings,
    private val installer: PluginInstaller,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private var cached: CachedIndex? = null

    suspend fun list(force: Boolean = false): List<MarketplacePlugin> {
        val now = System.currentTimeMillis()
        cached?.takeIf { !force && now - it.at < CACHE_MS }?.let { return it.items }
        val url = settings.registryUrlOnce()
        val response = http.get(url)
        if (response.status.value !in 200..299) {
            throw PluginInstallException("Marketplace HTTP ${response.status.value}")
        }
        val index = runCatching {
            json.decodeFromString(MarketplaceIndex.serializer(), response.bodyAsText())
        }.getOrElse { throw PluginInstallException("Invalid marketplace index: ${it.message}") }
        cached = CachedIndex(index.plugins, now)
        return index.plugins
    }

    fun invalidate() {
        cached = null
    }

    suspend fun search(query: String): List<MarketplacePlugin> {
        val value = query.trim().lowercase()
        if (value.isBlank()) return list()
        return list().filter {
            it.id.lowercase().contains(value) ||
                it.name.lowercase().contains(value) ||
                it.summary.lowercase().contains(value) ||
                it.description.lowercase().contains(value)
        }
    }

    suspend fun install(entry: MarketplacePlugin): InstalledPlugin {
        val plugin = installer.installFromUrl(entry.download, entry.sha256)
        if (plugin.manifest.id != entry.id) {
            throw PluginInstallException("Downloaded plugin id does not match marketplace entry")
        }
        return plugin
    }

    private data class CachedIndex(val items: List<MarketplacePlugin>, val at: Long)

    companion object {
        private const val CACHE_MS = 5 * 60_000L
    }
}
