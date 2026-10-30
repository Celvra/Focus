package fan.x0.focus.core.network.openai

import fan.x0.focus.core.network.NetworkExceptionMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Lists OpenAI-compatible /models. Accepts {data:[]}, {models:[]}, or a bare array.
class OpenAiModelCatalog(
    private val http: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun list(
        endpoint: ModelEndpoint,
        apiKey: String,
        protocol: ChatProtocol = ChatProtocol.ChatCompletions,
    ): List<String> = details(endpoint, apiKey, protocol).map { it.id }

    suspend fun details(
        endpoint: ModelEndpoint,
        apiKey: String,
        protocol: ChatProtocol = ChatProtocol.ChatCompletions,
    ): List<ModelEntry> {
        val response = try {
            http.get(endpoint.modelsUrl) {
                authHeaders(apiKey, protocol)
                header(HttpHeaders.Accept, "application/json")
                timeout {
                    requestTimeoutMillis = 25_000
                    socketTimeoutMillis = 25_000
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
        return parseModelEntries(body, json)
    }

    private fun HttpRequestBuilder.authHeaders(apiKey: String, protocol: ChatProtocol) {
        if (protocol == ChatProtocol.AnthropicMessages) {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
        } else {
            if (apiKey.isNotBlank()) header(HttpHeaders.Authorization, "Bearer $apiKey")
        }
    }
}

internal fun parseModelIds(body: String, json: Json = Json { ignoreUnknownKeys = true }): List<String> =
    parseModelEntries(body, json).map { it.id }

internal fun parseModelEntries(body: String, json: Json = Json { ignoreUnknownKeys = true }): List<ModelEntry> {
    val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
    val array = when (root) {
        is JsonArray -> root
        is JsonObject -> (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray) ?: JsonArray(emptyList())
        else -> JsonArray(emptyList())
    }
    return array.mapNotNull { element ->
        when (element) {
            is JsonPrimitive -> element.textOrNull()?.let { ModelEntry(it) }
            is JsonObject -> element.toEntry()
            else -> null
        }
    }
        .map { entry -> entry.copy(id = entry.id.trim()) }
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
        .sortedBy { it.id }
}

private fun JsonObject.toEntry(): ModelEntry? {
    val id = text("id") ?: text("name") ?: text("model") ?: return null
    return ModelEntry(id, toMeta())
}

private fun JsonObject.toMeta(): ModelMeta {
    val pricing = obj("pricing")
    val prompt = pricing?.number("prompt")
    val completion = pricing?.number("completion")
    val input = if (prompt != null && completion != null) {
        prompt * 1_000_000
    } else {
        pricing?.number("input") ?: number("input_price") ?: obj("cost")?.number("input")
    }
    val output = if (prompt != null && completion != null) {
        completion * 1_000_000
    } else {
        pricing?.number("output") ?: number("output_price") ?: obj("cost")?.number("output")
    }
    val parameters = strings("supported_parameters") + strings("capabilities")
    val inputModalities = obj("architecture")?.strings("input_modalities")
        ?: obj("modalities")?.strings("input")
        ?: strings("input_modalities")
    val context = number("context_length")?.toInt()
        ?: number("context_window")?.toInt()
        ?: obj("top_provider")?.number("context_length")?.toInt()
        ?: obj("limit")?.number("context")?.toInt()
        ?: obj("limits")?.number("context")?.toInt()
    return ModelMeta(
        contextWindow = context,
        inputPrice = input,
        outputPrice = output,
        supportsImage = if (inputModalities.isNotEmpty()) {
            inputModalities.any { it.equals("image", true) }
        } else {
            bool("vision") ?: bool("image_input")
        },
        supportsTools = if (parameters.isNotEmpty()) {
            parameters.any { it.equals("tools", true) || it.equals("tool_choice", true) }
        } else {
            bool("tool_call") ?: bool("tools") ?: bool("function_calling")
        },
        supportsReasoning = if (parameters.isNotEmpty()) {
            parameters.any { it.equals("reasoning", true) || it.equals("reasoning_effort", true) }
        } else {
            bool("reasoning") ?: bool("reasoning_effort")
        },
        description = text("description"),
        knowledgeCutoff = text("knowledge_cutoff") ?: text("knowledge"),
        releaseDate = text("release_date") ?: text("created_at") ?: number("created")?.toLong()?.let(::epochYearMonth),
    )
}

private fun JsonPrimitive.textOrNull(): String? = runCatching { content }.getOrNull()?.takeIf { it.isNotBlank() }

private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

private fun JsonObject.text(key: String): String? =
    primitive(key)?.takeIf { it.isString }?.content?.trim()?.ifBlank { null }

private fun JsonObject.number(key: String): Double? = primitive(key)
    ?.let { runCatching { it.content.toDouble() }.getOrNull() }

private fun JsonObject.bool(key: String): Boolean? = primitive(key)
    ?.let { runCatching { it.content.toBooleanStrict() }.getOrNull() }

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray)
    ?.mapNotNull { (it as? JsonPrimitive)?.textOrNull() }
    ?: emptyList()

val ModelEndpoint.modelsUrl: String
    get() = "$rootUrl/models"