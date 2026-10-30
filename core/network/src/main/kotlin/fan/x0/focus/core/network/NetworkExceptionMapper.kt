package fan.x0.focus.core.network

import fan.x0.focus.core.error.DecodedHttpError
import fan.x0.focus.core.error.NetworkException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

// Walks the cause chain and maps transport failures. Cancellation is never mapped.
object NetworkExceptionMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun mapOrNull(throwable: Throwable): NetworkException? {
        if (throwable is CancellationException) return null
        if (throwable is NetworkException) return throwable
        var cause: Throwable? = throwable
        while (cause != null) {
            when (cause) {
                is ConnectTimeoutException, is SocketTimeoutException, is java.net.SocketTimeoutException ->
                    return NetworkException.Timeout(cause)
                is SSLException -> return NetworkException.SslHandshake(cause)
                is UnknownHostException -> return NetworkException.Connection(cause)
                is IOException -> return NetworkException.Connection(cause)
            }
            cause = cause.cause
        }
        return null
    }

    fun http(status: Int, body: String?, retryAfterSeconds: Long? = null): NetworkException.Http =
        NetworkException.Http(status, decodeError(body), snippet(body), retryAfterSeconds)

    fun decodeError(body: String?): DecodedHttpError? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val error = root["error"]?.jsonObject
            if (error != null) {
                DecodedHttpError(
                    title = error["type"]?.jsonPrimitive?.contentOrNull(),
                    message = error["message"]?.jsonPrimitive?.contentOrNull(),
                )
            } else {
                DecodedHttpError(
                    title = root["message"]?.jsonPrimitive?.contentOrNull(),
                    message = root["detail"]?.jsonPrimitive?.contentOrNull(),
                )
            }
        }.getOrNull()
    }

    private fun snippet(body: String?): String? {
        val text = body?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return text.replace(Regex("\\s+"), " ").take(240)
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
        runCatching { content }.getOrNull()
}
