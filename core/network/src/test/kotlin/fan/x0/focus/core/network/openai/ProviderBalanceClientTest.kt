package fan.x0.focus.core.network.openai

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderBalanceClientTest {

    @Test
    fun `openrouter balance subtracts usage`() {
        val body = """{"data":{"total_credits":25.5,"total_usage":5.25}}"""
        val balance = parseOpenRouterBalance(body)
        assertEquals(20.25, balance.amount, 0.001)
        assertEquals("USD", balance.currency)
    }

    @Test
    fun `deepseek balance reads first currency`() {
        val body = """{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"110.00","granted_balance":"10.00","topped_up_balance":"100.00"}]}"""
        val balance = parseDeepSeekBalance(body)
        assertEquals(110.0, balance.amount, 0.001)
        assertEquals("CNY", balance.currency)
    }

    @Test(expected = ModelProtocolException::class)
    fun `openrouter rejects missing data`() {
        parseOpenRouterBalance("""{"data":{}}""")
    }

    @Test
    fun `generic balance reads root amount and currency`() {
        val balance = parseGenericBalance("""{"balance":12.5,"currency":"USD"}""")
        assertEquals(12.5, balance?.amount ?: 0.0, 0.001)
        assertEquals("USD", balance?.currency)
    }

    @Test
    fun `generic balance reads credit grants`() {
        val balance = parseGenericBalance("""{"object":"credit_summary","total_available":42.0}""")
        assertEquals(42.0, balance?.amount ?: 0.0, 0.001)
        assertEquals("USD", balance?.currency)
    }

    @Test
    fun `balance is only supported for known providers`() {
        val client = ProviderBalanceClient(io.ktor.client.HttpClient())
        assertEquals(true, client.supports("openrouter"))
        assertEquals(true, client.supports("deepseek"))
        assertEquals(false, client.supports("openai"))
    }
}
