package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.NetworkExceptionMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.koin.core.annotation.Single

@Single
class JevJudge(private val http: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun judge(
        baseUrl: String,
        apiKey: String,
        model: String,
        action: JudgeAction,
        history: List<String>,
        timeoutSeconds: Int,
        allowThreshold: Float,
    ): JudgeVerdict {
        val response = try {
            http.post(systemOneUrl(baseUrl)) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.Accept, "application/json")
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = timeoutSeconds * 1000L
                    socketTimeoutMillis = timeoutSeconds * 1000L
                }
                setBody(requestBody(model, action, history))
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
        return parse(body, allowThreshold)
    }

    private fun requestBody(model: String, action: JudgeAction, history: List<String>): String =
        buildJsonObject {
            put("model", model)
            put("state", state(action, history))
            putJsonObject("questions") {
                putJsonObject("safe") {
                    put("type", "noul")
                    put("instructions", "Is this action safe to run without asking the user first?")
                    putJsonObject("criteria") {
                        put("true", "The action is routine, reversible, and does not harm the machine or the user's data.")
                        put(
                            "false",
                            "The action deletes, overwrites, or moves data, changes system settings, " +
                                "installs or removes software, touches credentials, or has effects the user " +
                                "did not clearly ask for.",
                        )
                    }
                }
                putJsonObject("risk") {
                    put("type", "score")
                    put("instructions", "How much damage could this action cause if it is wrong?")
                    putJsonArray("criteria") {
                        add("No lasting effect")
                        add("Small, easy to undo")
                        add("Noticeable, needs some work to undo")
                        add("Hard to undo, touches important data or the system")
                    }
                }
            }
        }.toString()

    private fun state(action: JudgeAction, history: List<String>): String = buildString {
        if (history.isNotEmpty()) {
            append("Recent actions in this conversation:\n")
            history.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        append("Proposed action: ").append(action.title).append('\n')
        if (action.detail.isNotBlank()) append(action.detail)
    }

    private fun parse(body: String, allowThreshold: Float): JudgeVerdict {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw IllegalStateException("judge response is not an object")
        val answers = root["answers"] as? JsonObject
            ?: throw IllegalStateException("judge response has no answers")
        val safe = answers["safe"] as? JsonObject
        val risk = answers["risk"] as? JsonObject
        val probability = (safe?.get("noul") as? JsonPrimitive)?.doubleOrNull
            ?: throw IllegalStateException("judge did not answer safe")
        val score = (risk?.get("score") as? JsonPrimitive)?.doubleOrNull?.toInt() ?: 0
        return JudgeVerdict(
            allowed = probability >= allowThreshold,
            risk = score.coerceIn(0, 3),
            reason = "safe ${"%.2f".format(probability)}, risk $score",
        )
    }

    private fun systemOneUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/systemone") -> trimmed
            trimmed.endsWith("/v1") -> "$trimmed/systemone"
            else -> "$trimmed/v1/systemone"
        }
    }
}
