package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ChatCompletionRequest
import fan.x0.focus.core.network.openai.ChatMessageDto
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.OpenAiChatClient
import org.koin.core.annotation.Single

@Single
class ChatTitleGenerator(
    private val client: OpenAiChatClient,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val chats: ChatStore,
) {
    suspend fun generate(conversationId: String, providerId: String, model: String): String? {
        val conversation = chats.conversation(conversationId) ?: return null
        val config = settings.configOnce()
        val provider = config.providers.firstOrNull { it.id == providerId }
            ?: config.providers.firstOrNull { it.id == conversation.providerId }
            ?: config.activeProvider
            ?: return null
        val endpoint = ModelEndpoint(
            baseUrl = provider.baseUrl,
            model = model.ifBlank { conversation.model }.ifBlank { provider.model },
        )
        if (!endpoint.isUsable) return null
        val apiKey = secrets.apiKey(provider.id).orEmpty()
        if (apiKey.isBlank()) return null
        val history = chats.messages(conversationId, CONTEXT_MESSAGES)
        if (history.none { it.role == ChatRole.User }) return null
        val request = ChatCompletionRequest(
            model = endpoint.model,
            messages = listOf(
                ChatMessageDto(role = "system", content = SYSTEM_PROMPT),
                ChatMessageDto(role = "user", content = buildPrompt(conversation.title, history)),
            ),
            stream = false,
        )
        val raw = client.complete(endpoint, apiKey, request)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?: return null
        val title = clean(raw) ?: return null
        val currentTitle = chats.conversation(conversationId)?.title.orEmpty()
        if (currentTitle != conversation.title) return currentTitle
        if (title != currentTitle) chats.rename(conversationId, title)
        return title
    }

    private fun buildPrompt(current: String, history: List<ChatMessage>): String = buildString {
        appendLine("Existing title: ${current.ifBlank { "none" }}")
        appendLine("Conversation:")
        history.forEach { message ->
            val text = message.content.trim()
            if (text.isEmpty()) return@forEach
            val speaker = when (message.role) {
                ChatRole.User -> "User"
                ChatRole.Assistant -> "Assistant"
                else -> return@forEach
            }
            appendLine("$speaker: ${text.take(400)}")
        }
        appendLine("Reply with the title only.")
    }

    private fun clean(raw: String): String? {
        val line = raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
        var title = line.trim(' ', '"', '\'', '#', '*', '-')
        listOf("Title:", "Title：", "标题:", "标题：").forEach { prefix ->
            if (title.startsWith(prefix, ignoreCase = true)) {
                title = title.removePrefix(prefix).trim(' ', '"', '\'', '#', '*', '-')
            }
        }
        title = title.trimEnd(' ', '.', '。', '!', '！', '?', '？', ':', '：')
        return title.take(MAX_TITLE_LENGTH).ifBlank { null }
    }

    companion object {
        private const val CONTEXT_MESSAGES = 16
        private const val MAX_TITLE_LENGTH = 40
        private const val SYSTEM_PROMPT =
            "You write short titles for chat conversations in a personal assistant app. Reply with one plain title. " +
                "No quotes, no markdown, no trailing punctuation, no explanation. " +
                "Use the language of the conversation. Keep it short, at most 6 words in English or 16 characters in Chinese. " +
                "Name the current topic. If the existing title still fits, reply with the existing title unchanged."
    }
}
