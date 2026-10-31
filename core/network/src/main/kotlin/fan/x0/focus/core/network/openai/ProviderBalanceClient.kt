package fan.x0.focus.core.network.openai

import fan.x0.focus.core.network.NetworkExceptionMapper
import fan.x0.focus.core.network.ProviderRequestConfig
import fan.x0.focus.core.network.applyProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ProviderBalance(
    val amount: Double,
    val currency: String,
)

class ProviderBalanceClient(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun supports(providerId: String): Boolean = providerId in SUPPORTED

    suspend fun fetch(
        providerId: String,
        baseUrl: String,
        config: ProviderRequestConfig,
        protocol: ChatProtocol = ChatProtocol.ChatCompletions,
    ): ProviderBalance = when (providerId) {
        "openrouter" -> {
            val url = "${baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")}/credits"
            parseOpenRouterBalance(request(url, config, protocol, 25_000), json)
        }
        "deepseek" -> {
            val url = "${rootUrl(baseUrl)}/user/balance"
            parseDeepSeekBalance(request(url, config, protocol, 25_000), json)
        }
        else -> fetchGeneric(baseUrl, config, protocol)
    }

    private suspend fun fetchGeneric(
        baseUrl: String,
        config: ProviderRequestConfig,
        protocol: ChatProtocol,
    ): ProviderBalance {
        var last: Throwable? = null
        for (url in candidateUrls(baseUrl)) {
            try {
                val balance = parseGenericBalance(request(url, config, protocol, 5_000), json)
                if (balance != null) return balance
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                last = e
            }
        }
        throw ModelProtocolException(last?.message ?: "No balance endpoint found")
    }

    private suspend fun request(
        url: String,
        config: ProviderRequestConfig,
        protocol: ChatProtocol,
        timeoutMillis: Long,
    ): String {
        val response = try {
            http.get(url) {
                applyProvider(config, protocol)
                header(HttpHeaders.Accept, "application/json")
                timeout {
                    requestTimeoutMillis = timeoutMillis
                    socketTimeoutMillis = timeoutMillis
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
        return body
    }

    private fun candidateUrls(baseUrl: String): List<String> {
        val base = baseUrl.trim().trimEnd('/').removeSuffix("/chat/completions")
        val root = rootUrl(baseUrl)
        return listOf(
            "$base/credits",
            "$root/user/balance",
            "$root/balance",
            "$root/dashboard/billing/credit_grants",
            "$root/credits",
        ).distinct()
    }

    private fun rootUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/')
            .removeSuffix("/chat/completions")
            .removeSuffix("/v1")

    companion object {
        val SUPPORTED = setOf("openrouter", "deepseek")
    }
}

internal fun parseOpenRouterBalance(body: String, json: Json = Json { ignoreUnknownKeys = true }): ProviderBalance {
    val root = json.parseToJsonElement(body).jsonObject
    val data = root["data"]?.jsonObject ?: throw ModelProtocolException("balance data missing")
    val total = data["total_credits"]?.jsonPrimitive?.doubleOrNull
        ?: throw ModelProtocolException("total_credits missing")
    val usage = data["total_usage"]?.jsonPrimitive?.doubleOrNull ?: 0.0
    return ProviderBalance(amount = total - usage, currency = "USD")
}

internal fun parseDeepSeekBalance(body: String, json: Json = Json { ignoreUnknownKeys = true }): ProviderBalance {
    val root = json.parseToJsonElement(body).jsonObject
    val first = root["balance_infos"]?.jsonArray?.firstOrNull()?.jsonObject
        ?: throw ModelProtocolException("balance_infos missing")
    val amount = first["total_balance"]?.jsonPrimitive?.doubleOrNull
        ?: throw ModelProtocolException("total_balance missing")
    val currency = first["currency"]?.jsonPrimitive?.contentOrNull ?: "CNY"
    return ProviderBalance(amount = amount, currency = currency)
}

internal fun parseGenericBalance(body: String, json: Json = Json { ignoreUnknownKeys = true }): ProviderBalance? {
    runCatching { parseOpenRouterBalance(body, json) }.getOrNull()?.let { return it }
    runCatching { parseDeepSeekBalance(body, json) }.getOrNull()?.let { return it }
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
    val data = root["data"] as? JsonObject
    val amount = sequenceOf(
        root["balance"],
        root["credits"],
        root["total_balance"],
        root["total_available"],
        data?.get("balance"),
        data?.get("credits"),
        data?.get("total_balance"),
        data?.get("total_available"),
    ).mapNotNull { it?.asDouble() }.firstOrNull() ?: return null
    val currency = sequenceOf(
        root["currency"],
        data?.get("currency"),
    ).mapNotNull { it?.asText() }.firstOrNull() ?: "USD"
    return ProviderBalance(amount = amount, currency = currency)
}

private fun JsonElement.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull

private fun JsonElement.asText(): String? = (this as? JsonPrimitive)?.contentOrNull
