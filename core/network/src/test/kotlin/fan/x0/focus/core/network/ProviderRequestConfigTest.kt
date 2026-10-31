package fan.x0.focus.core.network

import fan.x0.focus.core.network.openai.ChatProtocol
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class ProviderRequestConfigTest {

    @Test
    fun `chat defaults to bearer`() {
        val request = request(
            ProviderRequestConfig(apiKey = "secret"),
            ChatProtocol.ChatCompletions,
        )
        assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `anthropic defaults to api key header`() {
        val request = request(
            ProviderRequestConfig(apiKey = "secret"),
            ChatProtocol.AnthropicMessages,
        )
        assertEquals("secret", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
    }

    @Test
    fun `header and query auth use configured names`() {
        val headerRequest = request(
            ProviderRequestConfig(
                apiKey = "secret",
                auth = ProviderAuth(ProviderAuthType.ApiKeyHeader, name = "api-key"),
            ),
            ChatProtocol.ChatCompletions,
        )
        assertEquals("secret", headerRequest.headers["api-key"])

        val queryRequest = request(
            ProviderRequestConfig(
                apiKey = "secret",
                auth = ProviderAuth(ProviderAuthType.Query, name = "token"),
            ),
            ChatProtocol.ChatCompletions,
        )
        assertEquals("secret", queryRequest.url.parameters["token"])
    }

    @Test
    fun `basic auth pairs username and key`() {
        val request = request(
            ProviderRequestConfig(
                apiKey = "secret",
                auth = ProviderAuth(ProviderAuthType.Basic, username = "user"),
            ),
            ChatProtocol.ChatCompletions,
        )
        val expected = Base64.getEncoder().encodeToString("user:secret".toByteArray())
        assertEquals("Basic $expected", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `custom headers override user agent and auth uses configured header`() {
        val request = request(
            ProviderRequestConfig(
                apiKey = "secret",
                auth = ProviderAuth(ProviderAuthType.ApiKeyHeader, name = "x-key"),
                userAgent = "Focus",
                headers = listOf(
                    ProviderHeader("User-Agent", "Custom"),
                    ProviderHeader("X-Title", "Focus"),
                ),
            ),
            ChatProtocol.ChatCompletions,
        )
        assertEquals("Custom", request.headers[HttpHeaders.UserAgent])
        assertEquals("Focus", request.headers["X-Title"])
        assertEquals("secret", request.headers["x-key"])
    }

    private fun request(config: ProviderRequestConfig, protocol: ChatProtocol): HttpRequestBuilder =
        HttpRequestBuilder().apply {
            url("https://example.com/v1/chat/completions")
            applyProvider(config, protocol)
        }
}
