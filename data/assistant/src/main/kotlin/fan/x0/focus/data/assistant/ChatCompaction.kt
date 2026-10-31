package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ChatCompletionRequest
import fan.x0.focus.core.network.openai.ChatMessageDto
import fan.x0.focus.core.network.openai.ChatStreamEvent
import fan.x0.focus.core.network.openai.ChatWireClient
import fan.x0.focus.core.network.openai.ModelEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

private val SUMMARY_TEMPLATE = """
    You MUST use this format for your response (you may omit sections that aren't applicable). Do not include the <template> tags in your response.
    <template>
    ## Objective
    - [one or two brief sentences describing what the user is trying to accomplish]

    ## Requirements
    - [constraints, preferences, requirements, and scope boundaries stated by the user, or "(none)"]

    ## Decisions
    - [decisions already made and why, or "(none)"]

    ## Work State
    Break the objective into smaller goals and report which are completed, which are being worked on, and which are blocked.
    ### Completed
    - [goals that have been completed; otherwise "(none)"]

    ### Active
    - [goals currently being worked on; otherwise "(none)"]

    ### Blocked
    - [anything blocking progress, and why; otherwise "(none)"]

    ## Next Move
    1. [ordered list of next actions, or "(none)"]

    ## Relevant Files
    List the files and directories, other than the current working directory, that another assistant would need to open to continue this work. Include at most 15, most important first. Do not list every file that was read or changed. Include paths outside the current working directory when relevant. If none, write "(none)".
    - `[file or directory path]`: [brief reason it matters]

    ## Important Context
    - [facts the next assistant cannot continue without and cannot easily find on its own; or "(none)"]
    </template>
""".trimIndent()

private val SUMMARY_RULES = """
    Rules:
    - Keep each section concise. Use terse, single-line bullets, not prose paragraphs or nested lists.
    - Prefer short references over detailed restatement. It is fine to leave out information the next assistant can recover from the code or the files listed above.
    - Preserve exact file paths, symbols, commands, error strings, URLs, and identifiers.
    - Carry forward only user questions or requests that remain unanswered or require further action. Do not repeat ones that newer history has answered or resolved. Preserve exact wording when carrying one forward.
    - Preserve consequential workflow state, including whether changes are uncommitted, committed, pushed, under review, or merged.
    - Do not mention the summary process or that context was compacted.
""".trimIndent()

private val SYSTEM_PROMPT = """
    You MUST summarize the conversation into a structured summary that will be given to another assistant to resume the work.

    Summarize only what the user and the assistant said and did. Leave out instructions and setup the assistant was given rather than told by the user: repository conventions, instruction files such as AGENTS.md, and environment details like the session ID. The next assistant receives current versions of all of these separately.

    $SUMMARY_TEMPLATE

    $SUMMARY_RULES

    Do not continue the task or call tools.
    Return only the structured summary in the requested format. Do not include a preamble, explanation, or other commentary.
""".trimIndent()

data class ConversationCompaction(
    val conversationId: String,
    val upToId: Long,
    val summary: String,
    val tokensBefore: Int = 0,
    val tokensAfter: Int = 0,
    val createdAt: Long,
)

data class ChatContext(
    val summary: String = "",
    val upToId: Long = 0,
    val messages: List<ChatMessage> = emptyList(),
)

data class CompactionResult(
    val upToId: Long,
    val summary: String,
    val tokensBefore: Int = 0,
    val tokensAfter: Int = 0,
)

@Single
class ChatCompactor(
    private val client: ChatWireClient,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val chats: ChatStore,
    private val catalog: ModelsDevCatalog,
) {
    private val windows = ConcurrentHashMap<String, Int>()

    suspend fun shouldCompact(conversationId: String, providerId: String, model: String): Boolean {
        val context = chats.context(conversationId)
        if (context.messages.size < MIN_MESSAGES) return false
        val usable = usableTokens(contextWindow(providerId, model))
        val estimated = estimateTokens(context.summary) + context.messages.sumOf { messageTokens(it) }
        val used = chats.lastAssistantStats(conversationId, context.upToId)?.lastContextTokens ?: 0
        return maxOf(estimated, used) >= usable
    }

    suspend fun compact(
        conversationId: String,
        providerId: String = "",
        model: String = "",
        onDelta: suspend (String) -> Unit = {},
    ): CompactionResult? {
        val conversation = chats.conversation(conversationId) ?: return null
        val config = settings.configOnce()
        val preferred = providerId.ifBlank { conversation.providerId }
        val provider = config.providers.firstOrNull { it.id == preferred }
            ?: config.providers.firstOrNull { it.id == conversation.providerId }
            ?: config.activeProvider
            ?: return null
        val endpoint = ModelEndpoint(
            baseUrl = provider.baseUrl,
            model = model.ifBlank { conversation.model }.ifBlank { provider.model },
        )
        if (!endpoint.isUsable) return null
        val apiKey = secrets.apiKey(provider.id).orEmpty()
        if (provider.requiresKey && apiKey.isBlank()) return null
        val context = chats.context(conversationId)
        if (context.messages.size < 2) return null
        val window = contextWindow(provider.id, endpoint.model)
        val usable = usableTokens(window)
        var start = tailStart(context.messages, keepTokens(window))
        if (start == 0) {
            val latestUser = context.messages.indexOfLast { it.role == ChatRole.User }
            start = if (latestUser > 0) latestUser else context.messages.size
        } else {
            val boundary = context.messages.indices.lastOrNull {
                it <= start && context.messages[it].role == ChatRole.User
            }
            if (boundary != null && boundary > 0) start = boundary
        }
        val source = context.messages.take(start)
        if (source.isEmpty()) return null
        val tail = context.messages.drop(start)
        val tokensBefore = maxOf(
            estimateTokens(context.summary) + context.messages.sumOf { messageTokens(it) },
            chats.lastAssistantStats(conversationId, context.upToId)?.lastContextTokens ?: 0,
        )
        val transcript = transcript(source, usable - estimateTokens(context.summary))
        if (transcript.isBlank()) return null
        val request = ChatCompletionRequest(
            model = endpoint.model,
            messages = listOf(
                ChatMessageDto(role = "system", content = SYSTEM_PROMPT),
                ChatMessageDto(role = "user", content = buildPrompt(context.summary, transcript)),
            ),
            stream = true,
            temperature = 0.3,
        )
        val builder = StringBuilder()
        try {
            client.stream(endpoint, provider.protocol, provider.requestConfig(apiKey), request).collect { event ->
                if (event is ChatStreamEvent.Text && event.delta.isNotBlank()) {
                    builder.append(event.delta)
                    onDelta(event.delta)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null
        }
        val summary = builder.toString().trim()
        if (summary.isBlank()) return null
        val tokensAfter = estimateTokens(summary) + tail.sumOf { messageTokens(it) }
        chats.saveCompaction(conversationId, source.last().id, summary, tokensBefore, tokensAfter)
        return CompactionResult(source.last().id, summary, tokensBefore, tokensAfter)
    }

    private fun tailStart(messages: List<ChatMessage>, budget: Int): Int {
        var used = 0
        for (index in messages.indices.reversed()) {
            val cost = messageTokens(messages[index])
            if (used + cost > budget && index < messages.lastIndex) return index + 1
            used += cost
        }
        return 0
    }

    private fun transcript(messages: List<ChatMessage>, budget: Int): String {
        val lines = messages.mapNotNull { it.toTranscriptLine() }
        val kept = ArrayDeque<String>()
        var used = 0
        for (index in lines.indices.reversed()) {
            val line = lines[index]
            val cost = estimateTokens(line)
            if (kept.isNotEmpty() && used + cost > budget) break
            kept.addFirst(line)
            used += cost
        }
        return kept.joinToString("\n\n")
    }

    private fun ChatMessage.toTranscriptLine(): String? {
        val body = content.trim().take(MAX_MESSAGE_CHARS)
        val calls = toolCalls.joinToString("\n") {
            "tool ${it.name}: ${it.arguments.take(MAX_ARGUMENT_CHARS)}"
        }
        val text = buildString {
            if (body.isNotEmpty()) append(body)
            if (calls.isNotEmpty()) {
                if (isNotEmpty()) append('\n')
                append(calls)
            }
        }
        if (text.isBlank()) return null
        val speaker = when (role) {
            ChatRole.User -> "User"
            ChatRole.Assistant -> "Assistant"
            ChatRole.Tool -> name?.let { "Tool $it" } ?: "Tool"
            ChatRole.System -> "App"
        }
        return "$speaker: $text"
    }

    private fun buildPrompt(previous: String, transcript: String): String = buildString {
        if (previous.isNotBlank()) {
            appendLine("Existing checkpoint:")
            appendLine(previous.take(MAX_PREVIOUS_CHARS))
            appendLine()
        }
        appendLine("Conversation to summarize:")
        appendLine(transcript)
        appendLine()
        if (previous.isNotBlank()) {
            appendLine("Update the existing checkpoint in the conversation above into one consolidated summary.")
            appendLine(
                "Newer history always takes precedence over the existing checkpoint. " +
                    "Preserve previous information unless newer history clearly contradicts, supersedes, " +
                    "resolves, or makes it stale. If something is no longer relevant to continuing the work, " +
                    "you may remove it.",
            )
            appendLine(
                "Incorporate newer requirements, decisions, progress, and context. " +
                    "Reconcile Work State and Next Move: move completed work out of Active, " +
                    "remove resolved blockers and answered questions, and preserve unresolved or pending work.",
            )
            append("Return only the updated Markdown sections.")
        } else {
            append("Write the structured summary now.")
        }
    }

    private suspend fun contextWindow(providerId: String, model: String): Int {
        val key = "$providerId/$model"
        windows[key]?.let { return it }
        val provider = settings.configOnce().providers.firstOrNull { it.id == providerId }
        val meta = provider?.let {
            withTimeoutOrNull(METADATA_TIMEOUT_MS) {
                catalog.metadata(it.id, it.baseUrl, listOf(model))[model]
            }
        }
        val window = meta?.contextWindow?.takeIf { it > MIN_CONTEXT_WINDOW } ?: DEFAULT_CONTEXT_WINDOW
        windows[key] = window
        return window
    }

    private fun usableTokens(window: Int): Int = (window * USABLE_PERCENT / 100).coerceAtLeast(MIN_CONTEXT_WINDOW)

    private fun keepTokens(window: Int): Int = (window * KEEP_PERCENT / 100).coerceAtLeast(MIN_TAIL_TOKENS)

    private fun messageTokens(message: ChatMessage): Int =
        estimateTokens(message.content) +
            estimateTokens(message.reasoning) +
            message.toolCalls.size * TOOL_CALL_TOKENS +
            MESSAGE_OVERHEAD_TOKENS

    private fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var ascii = 0
        var wide = 0
        text.forEach { char ->
            if (char.code < ASCII_LIMIT) ascii++ else wide++
        }
        return ascii / CHARS_PER_TOKEN + wide
    }

    private companion object {
        const val DEFAULT_CONTEXT_WINDOW = 128_000
        const val MIN_CONTEXT_WINDOW = 2_000
        const val USABLE_PERCENT = 70
        const val KEEP_PERCENT = 15
        const val MIN_TAIL_TOKENS = 2_000
        const val MIN_MESSAGES = 6
        const val METADATA_TIMEOUT_MS = 2_000L
        const val MAX_MESSAGE_CHARS = 6_000
        const val MAX_ARGUMENT_CHARS = 1_500
        const val MAX_PREVIOUS_CHARS = 12_000
        const val TOOL_CALL_TOKENS = 40
        const val MESSAGE_OVERHEAD_TOKENS = 8
        const val ASCII_LIMIT = 128
        const val CHARS_PER_TOKEN = 4
    }
}
