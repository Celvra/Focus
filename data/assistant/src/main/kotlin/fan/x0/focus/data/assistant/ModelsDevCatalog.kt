package fan.x0.focus.data.assistant

import android.content.Context
import fan.x0.focus.core.network.openai.ModelMeta
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File

@Single
class ModelsDevCatalog(
    @Provided private val context: Context,
    private val http: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var index: JsonObject? = null

    suspend fun metadata(providerId: String, baseUrl: String, modelIds: List<String>): Map<String, ModelMeta> {
        if (modelIds.isEmpty()) return emptyMap()
        val all = index() ?: return emptyMap()
        val provider = modelsDevProvider(all, providerId, baseUrl) ?: return emptyMap()
        val models = provider["models"] as? JsonObject ?: return emptyMap()
        return modelIds.mapNotNull { id ->
            val key = bestModelKey(models.keys, id) ?: return@mapNotNull null
            val value = models[key] ?: return@mapNotNull null
            val decoded = runCatching {
                json.decodeFromJsonElement(ModelsDevModel.serializer(), value)
            }.getOrNull() ?: return@mapNotNull null
            id to decoded.toMeta()
        }.toMap()
    }

    private suspend fun index(): JsonObject? = index ?: mutex.withLock {
        index ?: withContext(Dispatchers.IO) {
            val text = loadText() ?: return@withContext null
            val parsed = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            if (parsed != null) index = parsed
            parsed
        }
    }

    private suspend fun loadText(): String? =
        readCache(CACHE_TTL_MILLIS) ?: fetchText()?.also { writeCache(it) } ?: readCache(Long.MAX_VALUE)

    private suspend fun fetchText(): String? = try {
        val response = http.get(REMOTE_URL) {
            timeout {
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
        val body = response.bodyAsText()
        if (response.status.isSuccess() && body.isNotBlank()) body else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    private fun readCache(maxAgeMillis: Long): String? {
        val file = cacheFile()
        if (!file.isFile) return null
        if (System.currentTimeMillis() - file.lastModified() > maxAgeMillis) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun writeCache(text: String) {
        runCatching { cacheFile().writeText(text) }
    }

    private fun cacheFile(): File = File(context.cacheDir, CACHE_NAME)

    private companion object {
        const val REMOTE_URL = "https://models.dev/api.json"
        const val CACHE_NAME = "models-dev.json"
        const val CACHE_TTL_MILLIS = 24L * 60 * 60 * 1000
    }
}

internal fun modelsDevProvider(all: JsonObject, providerId: String, baseUrl: String): JsonObject? {
    val id = providerId.lowercase()
    (all[providerId] as? JsonObject)?.let { return it }
    (all[id] as? JsonObject)?.let { return it }
    modelsDevAliases[id]?.let { alias -> (all[alias] as? JsonObject)?.let { return it } }
    val host = hostOf(baseUrl)
    if (host.isBlank()) return null
    return all.entries.firstOrNull { (_, value) ->
        val api = (value as? JsonObject)?.get("api")?.let { it as? JsonPrimitive }?.contentOrNull
        api != null && hostOf(api) == host
    }?.value as? JsonObject
}

internal fun bestModelKey(keys: Collection<String>, modelId: String): String? {
    val target = modelKeyCandidates(modelId)
    return keys.firstOrNull { key -> modelKeyCandidates(key).any { it.isNotBlank() && it in target } }
}

internal fun ModelsDevModel.toMeta(): ModelMeta = ModelMeta(
    contextWindow = limit?.context,
    inputPrice = cost?.input,
    outputPrice = cost?.output,
    supportsImage = when {
        !modalities?.input.isNullOrEmpty() -> modalities?.input?.any { it.equals("image", true) }
        else -> attachment
    },
    supportsTools = toolCall,
    supportsReasoning = reasoning,
    description = description?.trim()?.ifBlank { null },
    knowledgeCutoff = knowledge?.trim()?.ifBlank { null },
    releaseDate = releaseDate?.trim()?.ifBlank { null },
)

private fun modelKeyCandidates(id: String): List<String> {
    val lower = id.lowercase()
    return listOf(
        lower,
        lower.substringAfterLast('/'),
        lower.replace(DATE_SUFFIX, ""),
        lower.removeSuffix("-latest"),
    )
}

private fun hostOf(url: String): String = url
    .substringAfter("://")
    .substringBefore('/')
    .substringBefore(':')
    .lowercase()

private val DATE_SUFFIX = Regex("-(\\d{8}|\\d{4}-\\d{2}-\\d{2})$")

private val modelsDevAliases = mapOf(
    "gemini" to "google",
    "grok" to "xai",
    "kimi" to "moonshotai",
    "moonshot" to "moonshotai",
    "zhipu" to "zhipuai",
    "glm" to "zhipuai",
    "chatglm" to "zhipuai",
    "bigmodel" to "zhipuai",
    "qwen" to "alibaba",
    "dashscope" to "alibaba",
    "together" to "togetherai",
    "fireworks" to "fireworks-ai",
    "siliconcloud" to "siliconflow",
    "silicon" to "siliconflow",
    "bedrock" to "amazon-bedrock",
    "vertex" to "google-vertex",
    "vertexai" to "google-vertex",
    "tencent" to "tencent-tokenhub",
    "zai" to "zai",
)

@Serializable
internal data class ModelsDevModel(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val attachment: Boolean? = null,
    val reasoning: Boolean? = null,
    @SerialName("tool_call") val toolCall: Boolean? = null,
    val knowledge: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val modalities: ModelsDevModalities? = null,
    val limit: ModelsDevLimit? = null,
    val cost: ModelsDevCost? = null,
)

@Serializable
internal data class ModelsDevModalities(
    val input: List<String> = emptyList(),
    val output: List<String> = emptyList(),
)

@Serializable
internal data class ModelsDevLimit(
    val context: Int? = null,
    val output: Int? = null,
)

@Serializable
internal data class ModelsDevCost(
    val input: Double? = null,
    val output: Double? = null,
)