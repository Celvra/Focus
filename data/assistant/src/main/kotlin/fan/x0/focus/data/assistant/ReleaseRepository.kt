package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.NetworkExceptionMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long,
)

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val notes: String,
    val url: String,
    val assets: List<ReleaseAsset>,
)

@Single
class ReleaseRepository(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): ReleaseInfo? {
        val response = try {
            http.get(API_URL) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
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
        if (response.status == HttpStatusCode.NotFound) return null
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw NetworkExceptionMapper.http(response.status.value, body)
        }
        val release = json.decodeFromString<GitHubRelease>(body)
        if (release.tagName.isBlank()) return null
        return ReleaseInfo(
            tagName = release.tagName,
            name = release.name.orEmpty().ifBlank { release.tagName },
            notes = release.body.orEmpty().trim(),
            url = release.htmlUrl,
            assets = release.assets
                .filter { asset ->
                    asset.name?.endsWith(".apk", ignoreCase = true) == true &&
                        !asset.downloadUrl.isNullOrBlank()
                }
                .map { asset ->
                    ReleaseAsset(
                        name = asset.name.orEmpty(),
                        url = asset.downloadUrl.orEmpty(),
                        size = asset.size,
                    )
                },
        )
    }

    private companion object {
        const val API_URL = "https://api.github.com/repos/Celvra/Focus/releases/latest"
        const val REQUEST_TIMEOUT_MS = 20_000L
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubReleaseAsset> = emptyList(),
)

@Serializable
private data class GitHubReleaseAsset(
    val name: String? = null,
    @SerialName("browser_download_url") val downloadUrl: String? = null,
    val size: Long = 0,
)
