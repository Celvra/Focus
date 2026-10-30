package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.network.openai.ModelProtocolException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {
    @Test
    fun retriesTransientHttpStatuses() {
        listOf(408, 409, 425, 429, 500, 502, 503).forEach { status ->
            assertTrue("status $status", RetryPolicy.isRetryable(NetworkException.Http(status, null)))
        }
    }

    @Test
    fun doesNotRetryClientErrors() {
        listOf(400, 401, 403, 404, 422).forEach { status ->
            assertFalse("status $status", RetryPolicy.isRetryable(NetworkException.Http(status, null)))
        }
    }

    @Test
    fun retriesTransportAndProtocolErrors() {
        assertTrue(RetryPolicy.isRetryable(NetworkException.Timeout()))
        assertTrue(RetryPolicy.isRetryable(NetworkException.Connection()))
        assertTrue(RetryPolicy.isRetryable(ModelProtocolException("early eof")))
    }

    @Test
    fun honorsRetryAfter() {
        val error = NetworkException.Http(429, null, retryAfterSeconds = 12)
        assertEquals(12L, RetryPolicy.retryAfter(error))
        assertEquals(12_000L, RetryConfig().delayFor(1, 12))
    }

    @Test
    fun backoffGrowsAndStaysCapped() {
        val config = RetryConfig(maxRetries = 6, baseDelayMs = 500, maxDelayMs = 4_000)
        val first = config.delayFor(1, null)
        val fourth = config.delayFor(4, null)
        assertTrue(first in 500..600)
        assertTrue(fourth in 4_000..4_800)
    }
}
