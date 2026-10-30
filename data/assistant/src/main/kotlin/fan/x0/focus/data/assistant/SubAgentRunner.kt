package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ChatCompletionRequest
import fan.x0.focus.core.network.openai.ChatMessageDto
import fan.x0.focus.core.network.openai.ChatStreamEvent
import fan.x0.focus.core.network.openai.ChatWireClient
import fan.x0.focus.core.network.openai.CompletedToolCall
import fan.x0.focus.core.network.openai.FunctionCallDto
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.StreamAccumulator
import fan.x0.focus.core.network.openai.ToolCallDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Single

data class SubAgentRequest(
    val task: String,
    val tools: List<String> = emptyList(),
    val model: String = "",
    val maxRounds: Int = 6,
)

data class SubAgentStep(
    val label: String,
    val ok: Boolean,
    val detail: String,
)

data class SubAgentResult(
    val summary: String,
    val files: List<String>,
    val steps: List<SubAgentStep>,
    val error: String? = null,
)

@Single
class SubAgentRunner(
    private val client: ChatWireClient,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val tools: ToolExecutor,
) {
    suspend fun run(request: SubAgentRequest): SubAgentResult {
        if (request.task.isBlank()) return SubAgentResult("", emptyList(), emptyList(), "Task is empty")
        val config = settings.configOnce()
        val provider = config.activeProvider
            ?: return SubAgentResult("", emptyList(), emptyList(), "No provider is configured")
        val key = secrets.apiKey(provider.id).orEmpty()
        if (key.isBlank()) return SubAgentResult("", emptyList(), emptyList(), "No API key for ${provider.name}")
        val model = request.model.ifBlank { provider.model }
        val endpoint = ModelEndpoint(provider.baseUrl, model)
        if (!endpoint.isUsable) return SubAgentResult("", emptyList(), emptyList(), "Model is not usable")
        val allowed = allowedTools(request.tools)
        val messages = mutableListOf<ChatMessageDto>(
            ChatMessageDto(
                role = "system",
                content = buildString {
                    appendLine("You are a focused subagent. Work on one task and return a short result.")
                    appendLine("Do not ask questions. Do not spawn another agent.")
                    appendLine("Use the provided tools only when needed. Read before you claim anything.")
                    appendLine("Return a compact summary, the files you touched or inspected, and any error.")
                    appendLine("Task: ${request.task}")
                },
            ),
            ChatMessageDto(role = "user", content = request.task),
        )
        val steps = mutableListOf<SubAgentStep>()
        val files = linkedSetOf<String>()
        var summary = ""
        try {
            withTimeout(TIMEOUT_MS) {
                repeat(request.maxRounds.coerceIn(1, 16)) { round ->
                    val message = stream(endpoint, provider.protocol, key, messages, allowed)
                    summary = message.text
                    if (message.text.isNotBlank()) {
                        messages += ChatMessageDto(role = "assistant", content = message.text)
                    }
                    if (message.toolCalls.isEmpty()) return@withTimeout
                    messages += ChatMessageDto(
                        role = "assistant",
                        content = message.text.ifBlank { null }?.let {
                            fan.x0.focus.core.network.openai.MessageContent.Text(it)
                        },
                        toolCalls = message.toolCalls.map { call ->
                            ToolCallDto(
                                id = call.id,
                                function = FunctionCallDto(name = call.name, arguments = call.arguments),
                            )
                        },
                    )
                    message.toolCalls.forEach { call ->
                        val spec = ToolCatalog.find(call.name)
                        val result = if (spec == null || call.name !in allowed.map { it.name }) {
                            ToolResult(false, "Error: tool ${call.name} is not allowed", "Not allowed")
                        } else {
                            tools.execute(call, conversationId = "", mode = AgentMode.Build)
                        }
                        steps += SubAgentStep(
                            label = ToolLabels.of(call.name),
                            ok = result.ok,
                            detail = result.summary.take(200),
                        )
                        collectFiles(call.arguments, files)
                        messages += ChatMessageDto(
                            role = "tool",
                            content = result.content.take(RESULT_LIMIT),
                            toolCallId = call.id,
                            name = call.name,
                        )
                    }
                }
            }
            return SubAgentResult(
                summary = summary.take(4000).ifBlank { "The subagent finished without a written summary." },
                files = files.toList(),
                steps = steps,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return SubAgentResult(
                summary = summary.take(4000),
                files = files.toList(),
                steps = steps,
                error = e.message ?: e.javaClass.simpleName,
            )
        }
    }

    private suspend fun stream(
        endpoint: ModelEndpoint,
        protocol: fan.x0.focus.core.network.openai.ChatProtocol,
        key: String,
        messages: List<ChatMessageDto>,
        tools: List<ToolSpec>,
    ): fan.x0.focus.core.network.openai.CompletedMessage {
        var last: Throwable? = null
        repeat(3) { attempt ->
            try {
                val accumulator = StreamAccumulator()
                client.stream(
                    endpoint,
                    protocol,
                    key,
                    ChatCompletionRequest(
                        model = endpoint.model,
                        messages = messages,
                        stream = true,
                        tools = tools.map { it.toDto() },
                        toolChoice = "auto",
                        temperature = 0.2,
                    ),
                ).collect { event ->
                    accumulator.apply(event)
                    if (event is ChatStreamEvent.Text && event.delta.isNotBlank()) Unit
                }
                val result = accumulator.result()
                if (result.text.isBlank() && result.toolCalls.isEmpty() && result.reasoning.isBlank()) {
                    error("subagent stream ended before a reply")
                }
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                last = e
                if (attempt < 2) delay(600L * (attempt + 1))
            }
        }
        throw last ?: IllegalStateException("subagent failed")
    }

    private fun allowedTools(requested: List<String>): List<ToolSpec> {
        val catalog = ToolCatalog.chat().filter { it.kind == ToolKind.Read && it.name != "spawn_agent" }
        if (requested.isEmpty()) return catalog
        val names = requested.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        return catalog.filter { it.name in names }
    }

    private fun collectFiles(arguments: String, into: MutableSet<String>) {
        val obj = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(arguments).jsonObject }.getOrNull()
            ?: return
        listOf("path", "root", "cwd").forEach { key ->
            (obj[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { into += it }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 180_000L
        private const val RESULT_LIMIT = 12_000
    }
}
