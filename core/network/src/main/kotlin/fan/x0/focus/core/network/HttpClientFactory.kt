package fan.x0.focus.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout

// One configured client for model calls, feeds, and article extraction.
fun createHttpClient(engine: io.ktor.client.engine.HttpClientEngine = OkHttp.create()): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 120_000
        }
    }

// Streaming can pause between deltas, so the request timeout is relaxed per call.
fun io.ktor.client.request.HttpRequestBuilder.allowLongStream() {
    timeout {
        requestTimeoutMillis = 600_000
        socketTimeoutMillis = 600_000
    }
}
