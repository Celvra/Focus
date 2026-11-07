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

data class WorkerActivity(
    val id: String,
    val title: String,
    val label: String,
    val running: Boolean,
)

data class MainChannelState(
    val working: Boolean = false,
    val workingText: String = "",
    val streaming: String = "",
    val reasoning: String = "",
    val reasoningMs: Long = 0,
    val activities: List<WorkerActivity> = emptyList(),
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
    val attachments: List<ChatAttachment> = emptyList(),
)

@Single
class MainChannelSession(
    private val chats: ChatStore,
    private val bus: AgentEventBus,
    private val notifier: MessageNotifier,
    private val settings: AgentEventSettings,
    private val today: TodayStore,
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
        val stored = bus.publish(
            AgentEvent.userMessage(
                text = text,
                threadKey = threadKey,
                messageId = messageId,
                attachments = attachmentJson,
                dedupeKey = "main:user:$messageId",
            ),
        )
        return messageId.takeIf { stored?.status == AgentEventStatus.Pending }
    }

    suspend fun appendAssistant(
        text: String,
        label: String? = "router",
        reasoning: String = "",
        reasoningMs: Long = 0,
    ) {
        val body = text.trim()
        if (body.isBlank()) return
        val conversation = chats.ensureMainConversation()
        chats.append(
            conversationId = conversation.id,
            role = ChatRole.Assistant,
            content = body,
            reasoning = reasoning,
            elapsedMs = reasoningMs,
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

    suspend fun appendWatchdog(text: String) {
        val body = text.trim()
        if (body.isBlank()) return
        val conversation = chats.ensureMainConversation()
        chats.append(
            conversationId = conversation.id,
            role = ChatRole.System,
            content = body,
            label = "watchdog",
        )
        notifier.notifyReply(
            conversationId = conversation.id,
            title = conversation.title.ifBlank { MAIN_TITLE },
            preview = body.take(140),
            failed = true,
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
        if (settings.configOnce().postResultsToToday) {
            runCatching {
                today.postUpdate(
                    headline = card.title.ifBlank { "旁聊" },
                    body = card.summary,
                    reason = if (card.failed) "旁聊失败" else "旁聊完成",
                    category = "旁聊",
                    icon = if (card.failed) "warning" else "task",
                    articleId = null,
                )
            }
        }
        notifier.notifyReply(
            conversationId = card.sideChatId,
            title = card.title.ifBlank { "旁聊" },
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
                streaming = if (working) "" else "",
                reasoning = if (working) "" else "",
                reasoningMs = if (working) 0 else it.reasoningMs,
                activities = if (working) it.activities else emptyList(),
            )
        }
    }

    fun setStreaming(text: String) {
        _state.update { it.copy(streaming = text) }
    }

    fun setReasoning(text: String, durationMs: Long = 0) {
        _state.update { it.copy(reasoning = text, reasoningMs = durationMs) }
    }

    fun setReasoningDone(durationMs: Long) {
        _state.update { it.copy(reasoningMs = durationMs) }
    }

    fun setActivities(activities: List<WorkerActivity>) {
        _state.update { it.copy(activities = activities) }
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
