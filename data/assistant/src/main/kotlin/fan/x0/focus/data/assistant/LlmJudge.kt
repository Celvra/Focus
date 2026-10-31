package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.ProviderRequestConfig
import fan.x0.focus.core.network.openai.ChatCompletionRequest
import fan.x0.focus.core.network.openai.ChatMessageDto
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ChatWireClient
import fan.x0.focus.core.network.openai.ModelEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import org.koin.core.annotation.Single

@Single
class LlmJudge(private val client: ChatWireClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun judge(
        endpoint: ModelEndpoint,
        protocol: ChatProtocol,
        config: ProviderRequestConfig,
        action: JudgeAction,
        history: List<String>,
        timeoutSeconds: Int,
        allowThreshold: Float,
    ): JudgeVerdict {
        val request = ChatCompletionRequest(
            model = endpoint.model,
            messages = listOf(
                ChatMessageDto(role = "system", content = systemPrompt(allowThreshold)),
                ChatMessageDto(role = "user", content = userPrompt(action, history)),
            ),
            stream = false,
            temperature = 0.0,
            maxTokens = 200,
        )
        val reply = try {
            withTimeout(timeoutSeconds * 1000L) {
                client.complete(endpoint, protocol, config, request)
            }
        } catch (e: CancellationException) {
            throw e
        }
        return parse(reply.text)
    }

    private fun userPrompt(action: JudgeAction, history: List<String>): String = buildString {
        if (history.isNotEmpty()) {
            append("Recent actions:\n")
            history.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        append("Action: ").append(action.title).append('\n')
        if (action.detail.isNotBlank()) append(action.detail)
    }

    private fun parse(text: String): JudgeVerdict {
        val body = extractObject(text) ?: throw IllegalStateException("judge reply is not JSON")
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
            ?: throw IllegalStateException("judge reply is not an object")
        val allowed = (root["allowed"] as? JsonPrimitive)?.booleanOrNull
            ?: throw IllegalStateException("judge reply has no allowed")
        val risk = (root["risk"] as? JsonPrimitive)?.intOrNull ?: 0
        val reason = (root["reason"] as? JsonPrimitive)?.content.orEmpty().take(200)
        return JudgeVerdict(
            allowed = allowed,
            risk = risk.coerceIn(0, 3),
            reason = reason.ifBlank { if (allowed) "judge allowed" else "judge refused" },
        )
    }

    private fun extractObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun systemPrompt(allowThreshold: Float): String =
        "You are a safety judge for shell and file actions on the user's own computer. " +
            "Answer with one JSON object and nothing else: " +
            "{\"allowed\": true or false, \"risk\": 0 to 3, \"reason\": \"one short sentence\"}. " +
            "Allow routine, reversible actions the user clearly asked for. " +
            "Refuse actions that delete or overwrite unrelated data, change system settings, " +
            "install or remove software, touch credentials, or reach the network in ways the user " +
            "did not ask for. risk 0 means no lasting effect, risk 3 means hard to undo. " +
            "Only set allowed true when you are at least " + (allowThreshold * 100).toInt() +
            "% confident the action is safe."
}
