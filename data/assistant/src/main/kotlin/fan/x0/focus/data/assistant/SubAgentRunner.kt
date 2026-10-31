package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.ProviderRequestConfig
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
import kotlinx.coroutines.TimeoutCancellationException
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
    val parentProviderId: String = "",
    val parentModel: String = "",
    val maxRounds: Int = 6,
    val runId: String = "",
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
    private val agent: AgentSettings,
    private val tools: ToolExecutor,
    private val runs: SubAgentRunStore,
) {
    suspend fun run(request: SubAgentRequest): SubAgentResult {
        if (request.task.isBlank()) return SubAgentResult("", emptyList(), emptyList(), "Task is empty")
        val config = settings.configOnce()
        val agentConfig = agent.configOnce()
        val candidates = buildList {
            agentConfig.subagentProviderId.takeIf { it.isNotBlank() }?.let { id ->
                config.providers.firstOrNull { it.id == id }?.let(::add)
            }
            request.parentProviderId.takeIf { it.isNotBlank() }?.let { id ->
                config.providers.firstOrNull { it.id == id }?.let(::add)
            }
            config.activeProvider?.let(::add)
        }.distinctBy { it.id }
        if (candidates.isEmpty()) {
            return SubAgentResult("", emptyList(), emptyList(), "No provider is configured")
        }
        val provider = candidates.firstOrNull { candidate ->
            !candidate.requiresKey || !secrets.apiKey(candidate.id).isNullOrBlank()
        } ?: candidates.first()
        val key = secrets.apiKey(provider.id).orEmpty()
        if (key.isBlank() && provider.requiresKey) {
            return SubAgentResult("", emptyList(), emptyList(), "No API key for ${provider.name}")
        }
        val configuredModel = agentConfig.subagentModel.takeIf {
            agentConfig.subagentProviderId.isNotBlank() &&
                provider.id == agentConfig.subagentProviderId &&
                it.isNotBlank()
        }
        val parentModel = request.parentModel.takeIf {
            provider.id == request.parentProviderId && it.isNotBlank()
        }
        val model = request.model.ifBlank { configuredModel ?: parentModel ?: provider.model }
            .ifBlank { provider.model }
        val endpoint = ModelEndpoint(provider.baseUrl, model)
        if (!endpoint.isUsable) return SubAgentResult("", emptyList(), emptyList(), "Model is not usable")
        val requestConfig = provider.requestConfig(key)
        val allowed = allowedTools(request.tools)
        runs.start(request.runId, request.task, provider.id, provider.name, model)
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
        val publish: (ChatStreamEvent) -> Unit = { event ->
            when (event) {
                is ChatStreamEvent.Text -> runs.answer(request.runId, event.delta, false)
                is ChatStreamEvent.Reasoning -> runs.answer(request.runId, event.delta, true)
                else -> Unit
            }
        }
        try {
            withTimeout(TIMEOUT_MS) {
                repeat(request.maxRounds.coerceIn(1, 16)) { round ->
                    val message = stream(
                        endpoint = endpoint,
                        protocol = provider.protocol,
                        config = requestConfig,
                        messages = messages,
                        tools = allowed,
                        onEvent = publish,
                        onRetry = { runs.resetAnswer(request.runId) },
                    )
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
                    val media = mutableListOf<ChatAttachment>()
                    message.toolCalls.forEach { call ->
                        val spec = ToolCatalog.find(call.name)
                        val label = ToolLabels.of(call.name)
                        runs.toolStarted(
                            id = request.runId,
                            callId = call.id,
                            label = label,
                            tool = call.name,
                            arguments = ToolCatalog.redactArguments(call.name, call.arguments).take(500),
                        )
                        val startedAt = System.currentTimeMillis()
                        val result = if (spec == null || call.name !in allowed.map { it.name }) {
                            ToolResult(false, "Error: tool ${call.name} is not allowed", "Not allowed")
                        } else {
                            tools.execute(call, conversationId = "", mode = AgentMode.Build)
                        }
                        val durationMs = System.currentTimeMillis() - startedAt
                        runs.toolFinished(
                            id = request.runId,
                            callId = call.id,
                            label = label,
                            ok = result.ok,
                            summary = result.summary,
                            output = result.content,
                            durationMs = durationMs,
                            diffs = result.diffs,
                            media = result.previews,
                        )
                        steps += SubAgentStep(
                            label = label,
                            ok = result.ok,
                            detail = result.summary.take(200),
                        )
                        collectFiles(call.arguments, files)
                        media += result.media
                        messages += ChatMessageDto(
                            role = "tool",
                            content = result.content.take(RESULT_LIMIT),
                            toolCallId = call.id,
                            name = call.name,
                        )
                    }
                    val images = media.mapNotNull { it.dataUrl }
                    if (images.isNotEmpty()) {
                        messages += ChatMessageDto(
                            role = "user",
                            content = fan.x0.focus.core.network.openai.MessageContent.of(
                                "Images returned by the tool calls above.",
                                images,
                            ),
                        )
                    }
                }
            }
            val text = summary.take(4000).ifBlank { "The subagent finished without a written summary." }
            runs.finish(request.runId, text, null)
            return SubAgentResult(
                summary = text,
                files = files.toList(),
                steps = steps,
            )
        } catch (e: TimeoutCancellationException) {
            runs.finish(request.runId, summary.take(4000), "Subagent timed out")
            return SubAgentResult(
                summary = summary.take(4000),
                files = files.toList(),
                steps = steps,
                error = "Subagent timed out",
            )
        } catch (e: CancellationException) {
            runs.finish(request.runId, summary.take(4000), "Cancelled")
            throw e
        } catch (e: Throwable) {
            val message = e.message ?: e.javaClass.simpleName
            runs.finish(request.runId, summary.take(4000), message)
            return SubAgentResult(
                summary = summary.take(4000),
                files = files.toList(),
                steps = steps,
                error = message,
            )
        }
    }

    private suspend fun stream(
        endpoint: ModelEndpoint,
        protocol: fan.x0.focus.core.network.openai.ChatProtocol,
        config: ProviderRequestConfig,
        messages: List<ChatMessageDto>,
        tools: List<ToolSpec>,
        onEvent: (ChatStreamEvent) -> Unit,
        onRetry: () -> Unit,
    ): fan.x0.focus.core.network.openai.CompletedMessage {
        var last: Throwable? = null
        repeat(3) { attempt ->
            if (attempt > 0) onRetry()
            try {
                val accumulator = StreamAccumulator()
                client.stream(
                    endpoint,
                    protocol,
                    config,
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
                    onEvent(event)
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
