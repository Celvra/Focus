package fan.x0.focus.core.network

import fan.x0.focus.core.network.openai.ChatProtocol
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable
enum class ProviderAuthType {
    Default,
    Bearer,
    ApiKeyHeader,
    Query,
    Basic,
    None,
}

@Serializable
data class ProviderAuth(
    val type: ProviderAuthType = ProviderAuthType.Default,
    val name: String = "",
    val username: String = "",
)

@Serializable
data class ProviderHeader(
    val name: String = "",
    val value: String = "",
)

data class ProviderRequestConfig(
    val apiKey: String = "",
    val auth: ProviderAuth = ProviderAuth(),
    val userAgent: String = "",
    val headers: List<ProviderHeader> = emptyList(),
)

fun HttpRequestBuilder.applyProvider(config: ProviderRequestConfig, protocol: ChatProtocol) {
    if (
        config.userAgent.isNotBlank() &&
        config.headers.none { it.name.equals(HttpHeaders.UserAgent, ignoreCase = true) }
    ) {
        headers[HttpHeaders.UserAgent] = config.userAgent
    }
    config.headers
        .filter { it.name.isNotBlank() }
        .forEach { header(it.name, it.value) }
    applyProviderAuth(config.apiKey, config.auth, protocol)
}

private fun HttpRequestBuilder.applyProviderAuth(
    apiKey: String,
    auth: ProviderAuth,
    protocol: ChatProtocol,
) {
    val type = when (auth.type) {
        ProviderAuthType.Default -> {
            if (protocol == ChatProtocol.AnthropicMessages) {
                ProviderAuthType.ApiKeyHeader
            } else {
                ProviderAuthType.Bearer
            }
        }
        else -> auth.type
    }
    if (apiKey.isNotBlank()) {
        when (type) {
            ProviderAuthType.Bearer -> {
                headers[HttpHeaders.Authorization] = "Bearer $apiKey"
            }
            ProviderAuthType.ApiKeyHeader -> {
                headers[auth.name.ifBlank { "x-api-key" }] = apiKey
            }
            ProviderAuthType.Query -> {
                parameter(auth.name.ifBlank { "key" }, apiKey)
            }
            ProviderAuthType.Basic -> {
                val raw = "${auth.username}:$apiKey"
                val token = Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
                headers[HttpHeaders.Authorization] = "Basic $token"
            }
            ProviderAuthType.Default,
            ProviderAuthType.None,
            -> Unit
        }
    }
    if (protocol == ChatProtocol.AnthropicMessages) {
        headers["anthropic-version"] = "2023-06-01"
    }
}
