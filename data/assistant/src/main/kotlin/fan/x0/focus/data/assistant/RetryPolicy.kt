package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.network.openai.ModelProtocolException

internal object RetryPolicy {
    private val retryStatuses = setOf(408, 409, 425, 429)

    fun isRetryable(error: Throwable): Boolean = when (error) {
        is NetworkException.Http -> error.statusCode in retryStatuses || error.statusCode in 500..599
        is NetworkException.Timeout, is NetworkException.Connection, is NetworkException.Unknown -> true
        is ModelProtocolException -> true
        is java.io.IOException -> true
        else -> false
    }

    fun retryAfter(error: Throwable): Long? = (error as? NetworkException.Http)?.retryAfterSeconds

    fun reason(error: Throwable): String = when (error) {
        is NetworkException.Http -> "HTTP ${error.statusCode}"
        is NetworkException.Timeout -> "timeout"
        is NetworkException.Connection -> "connection"
        is ModelProtocolException -> error.message ?: "protocol"
        else -> error.message ?: error.javaClass.simpleName
    }
}
