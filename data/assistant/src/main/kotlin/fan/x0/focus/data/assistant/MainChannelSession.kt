package fan.x0.focus.data.assistant

import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.core.notify.NotifyActionKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

data class MainChannelState(
    val working: Boolean = false,
    val workingText: String = "",
    val activeSideChats: Int = 0,
    val unreadResults: Int = 0,
    val routerModel: String = "",
)

@Serializable
data class SideResultCard(
    val sideChatId: String,
    val title: String,
    val summary: String,
    val failed: Boolean,
    val createdAt: Long = 0,
)

@Single
class MainChannelSession(
    private val chats: ChatStore,
    private val bus: AgentEventBus,
    private val notifier: MessageNotifier,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val attachmentsSerializer = ListSerializer(ChatAttachment.serializer())

    private val _state = MutableStateFlow(MainChannelState())
    val state: StateFlow<MainChannelState> = _state.asStateFlow()

    suspend fun ensureConversation(): Conversation = chats.ensureMainConversation()

    suspend fun submit(
        text: String,
        attachments: List<ChatAttachment> = emptyList(),
        replyToId: Long? = null,
    ): Long? {
        val conversation = chats.ensureMainConversation()
        val messageId = chats.append(
            conversationId = conversation.id,
            role = ChatRole.User,
            content = text,
            attachments = attachments,
            replyToId = replyToId,
        )
        val threadKey = replyToId?.let { "main:$it" } ?: "main:$messageId"
        val attachmentJson = if (attachments.isEmpty()) "" else json.encodeToString(attachmentsSerializer, attachments)
        bus.publish(
            AgentEvent.userMessage(
                text = text,
                threadKey = threadKey,
                messageId = messageId,
                attachments = attachmentJson,
                dedupeKey = "main:user:$messageId",
            ),
        )
        return messageId
    }

    suspend fun appendAssistant(text: String, label: String? = "router") {
        val body = text.trim()
        if (body.isBlank()) return
        val conversation = chats.ensureMainConversation()
        chats.append(
            conversationId = conversation.id,
            role = ChatRole.Assistant,
            content = body,
            label = label,
        )
        notifier.notifyReply(
            conversationId = conversation.id,
            title = conversation.title.ifBlank { MAIN_TITLE },
            preview = body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(140),
            failed = false,
        )
    }

    suspend fun appendNotice(text: String, label: String = "agent_notice") {
        val body = text.trim()
        if (body.isBlank()) return
        val conversation = chats.ensureMainConversation()
        chats.append(
            conversationId = conversation.id,
            role = ChatRole.System,
            content = body,
            label = label,
        )
    }

    suspend fun appendQuestion(text: String) {
        val body = text.trim()
        if (body.isBlank()) return
        val conversation = chats.ensureMainConversation()
        chats.append(
            conversationId = conversation.id,
            role = ChatRole.Assistant,
            content = body,
            label = "router_ask",
        )
        notifier.notifyAction(
            conversation.id,
            conversation.title.ifBlank { MAIN_TITLE },
            NotifyActionKind.Question,
        )
    }

    suspend fun appendSideResult(card: SideResultCard) {
        val conversation = chats.ensureMainConversation()
        chats.append(
            conversationId = conversation.id,
            role = ChatRole.System,
            content = json.encodeToString(SideResultCard.serializer(), card),
            label = SIDE_RESULT_LABEL,
        )
        val preview = card.summary.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(140)
        notifier.notifyReply(
            conversationId = conversation.id,
            title = card.title.ifBlank { MAIN_TITLE },
            preview = preview.ifBlank {
                if (card.failed) "旁聊失败" else "旁聊完成"
            },
            failed = card.failed,
        )
    }

    suspend fun appendSideApproval(sideChatId: String, title: String, note: String) {
        val conversation = chats.ensureMainConversation()
        val line = "旁聊 $title 等待审批"
        chats.append(
            conversationId = conversation.id,
            role = ChatRole.System,
            content = line,
            label = SIDE_APPROVAL_LABEL,
        )
        notifier.notifyAction(sideChatId, title.ifBlank { "旁聊" }, NotifyActionKind.Approval)
    }

    fun setWorking(working: Boolean, text: String = "") {
        _state.update {
            it.copy(
                working = working,
                workingText = if (working) text.ifBlank { it.workingText } else "",
            )
        }
    }

    fun setWorkingText(text: String) {
        _state.update { it.copy(workingText = text.take(40)) }
    }

    fun setActiveSideChats(count: Int) {
        _state.update { it.copy(activeSideChats = count.coerceAtLeast(0)) }
    }

    fun setUnreadResults(count: Int) {
        _state.update { it.copy(unreadResults = count.coerceAtLeast(0)) }
    }

    fun setRouterModel(label: String) {
        _state.update { it.copy(routerModel = label) }
    }

    companion object {
        const val SIDE_RESULT_LABEL = "side_result"
        const val SIDE_APPROVAL_LABEL = "side_approval"
        private const val MAIN_TITLE = "主会话"
    }
}
