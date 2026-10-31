package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.NetworkExceptionMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

data class Contributor(
    val login: String,
    val contributions: Int,
    val url: String,
    val avatarUrl: String,
)

@Single
class ContributorsRepository(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(): List<Contributor> {
        val result = mutableListOf<Contributor>()
        var page = 1
        while (page <= MAX_PAGES) {
            val batch = page(page)
            result += batch
            if (batch.size < PER_PAGE) break
            page++
        }
        return result.distinctBy { it.login }
    }

    suspend fun avatars(contributors: List<Contributor>): Map<String, ByteArray> = coroutineScope {
        val gate = Semaphore(AVATAR_CONCURRENCY)
        contributors.map { contributor ->
            async {
                gate.withPermit {
                    fetchAvatar(contributor.avatarUrl)?.let { contributor.login to it }
                }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    private suspend fun page(page: Int): List<Contributor> {
        val response = try {
            http.get("$API_URL?per_page=$PER_PAGE&page=$page") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                timeout {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MS
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw NetworkExceptionMapper.mapOrNull(e) ?: e
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw NetworkExceptionMapper.http(response.status.value, body)
        }
        return json.decodeFromString(ListSerializer, body)
            .filter { it.login.isNotBlank() }
            .map { contributor ->
                Contributor(
                    login = contributor.login,
                    contributions = contributor.contributions,
                    url = contributor.htmlUrl.ifBlank { "https://github.com/${contributor.login}" },
                    avatarUrl = avatarUrl(contributor.avatarUrl),
                )
            }
    }

    private suspend fun fetchAvatar(url: String): ByteArray? {
        if (url.isBlank()) return null
        val response = try {
            http.get(url) {
                timeout {
                    requestTimeoutMillis = AVATAR_TIMEOUT_MS
                    socketTimeoutMillis = AVATAR_TIMEOUT_MS
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return null
        }
        if (!response.status.isSuccess()) return null
        return response.bodyAsBytes()
    }

    private fun avatarUrl(url: String): String {
        if (url.isBlank()) return ""
        val separator = if ('?' in url) '&' else '?'
        return "$url${separator}s=$AVATAR_SIZE"
    }

    private companion object {
        const val API_URL = "https://api.github.com/repos/Celvra/Focus/contributors"
        const val REQUEST_TIMEOUT_MS = 20_000L
        const val AVATAR_TIMEOUT_MS = 15_000L
        const val PER_PAGE = 100
        const val MAX_PAGES = 5
        const val AVATAR_CONCURRENCY = 6
        const val AVATAR_SIZE = 96
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(GitHubContributor.serializer())
    }
}

@Serializable
private data class GitHubContributor(
    val login: String = "",
    val contributions: Int = 0,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
)
