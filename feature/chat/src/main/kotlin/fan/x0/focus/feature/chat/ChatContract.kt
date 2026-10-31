package fan.x0.focus.feature.chat

import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.core.network.openai.ModelMeta
import fan.x0.focus.data.assistant.AgentMode
import fan.x0.focus.data.assistant.AskKind
import fan.x0.focus.data.assistant.AttachmentKind
import fan.x0.focus.data.assistant.ChatDetailConfig
import fan.x0.focus.data.assistant.CommandPolicy
import fan.x0.focus.data.assistant.ConversationTodo
import fan.x0.focus.data.assistant.FileDiff
import fan.x0.focus.data.assistant.MessageStats
import fan.x0.focus.data.assistant.PlanStatus
import fan.x0.focus.data.assistant.ProposedPlan
import fan.x0.focus.data.assistant.ReasoningLevel

enum class TraceStatus { Pending, Running, Done, Failed }

data class TraceStep(
    val label: String,
    val status: TraceStatus,
    val summary: String? = null,
    val id: String = "",
    val tool: String = "",
    val arguments: String = "",
    val output: String? = null,
    val durationMs: Long = 0,
    val startedAt: Long = 0,
    val diffs: List<FileDiff> = emptyList(),
    val media: List<AttachmentUi> = emptyList(),
)

enum class ApprovalPhase { Waiting, Running, Finished, Declined }

enum class ApprovalKind { Action, Plan }

data class ApprovalUi(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<TraceStep>,
    val phase: ApprovalPhase,
    val kind: ApprovalKind = ApprovalKind.Action,
    val plan: ProposedPlan? = null,
)

enum class AskPhase { Waiting, Answered, Skipped }

enum class AskField { Answer, Other }

data class AskOptionUi(
    val label: String,
    val detail: String,
    val icon: String,
)

data class AskQuestionUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: AskKind,
    val icon: String,
    val options: List<AskOptionUi>,
    val allowOther: Boolean,
    val placeholder: String,
)

data class AskAnswerUi(
    val questionId: String,
    val choices: List<String> = emptyList(),
    val text: String = "",
    val number: Double? = null,
    val confirmed: Boolean? = null,
)

data class AskCardUi(
    val id: String,
    val intro: String,
    val questions: List<AskQuestionUi>,
    val phase: AskPhase,
    val answers: List<AskAnswerUi> = emptyList(),
)

data class AskDraft(
    val selections: List<String> = emptyList(),
    val text: String = "",
    val other: String = "",
    val confirmed: Boolean? = null,
)

data class AttachmentUi(
    val id: String,
    val kind: AttachmentKind,
    val name: String,
    val mime: String,
    val text: String? = null,
    val base64: String? = null,
) {
    val dataUrl: String?
        get() = base64?.let { "data:$mime;base64,$it" }
}

data class QuotedMessageUi(
    val id: Long,
    val author: String,
    val text: String,
    val deleted: Boolean = false,
    val fromUser: Boolean = false,
)

data class ChatMenuUi(
    val key: String,
    val messageId: Long,
    val author: String,
    val text: String,
    val fromUser: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
)

data class EditUi(
    val messageId: Long,
    val key: String,
    val original: String,
    val draft: String,
    val saving: Boolean = false,
)

data class ReplyUi(
    val messageId: Long,
    val author: String,
    val preview: String,
)

data class RetryUi(
    val attempt: Int,
    val max: Int,
    val reason: String,
)

data class UiMessage(
    val resId: Int,
    val args: List<Any> = emptyList(),
    val nonce: Long = 0,
)

data class SlashUi(
    val open: Boolean = false,
    val query: String = "",
    val selected: Int = 0,
    val commandIds: List<SlashCommandId> = SlashCommands.all.map { it.id },
) {
    val visible: Boolean get() = open && commandIds.isNotEmpty()
}

data class BtwUi(
    val open: Boolean = false,
    val question: String = "",
    val answer: String = "",
    val loading: Boolean = false,
    val error: AppError? = null,
)

data class SubAgentRunUi(
    val id: String,
    val task: String,
    val providerName: String,
    val model: String,
    val running: Boolean,
    val failed: Boolean,
    val summary: String,
    val error: String?,
    val entries: List<ChatEntry>,
)

sealed interface ChatEntry {
    val key: String
    val messageId: Long? get() = null

    data class User(
        override val key: String,
        val text: String,
        val attachments: List<AttachmentUi> = emptyList(),
        override val messageId: Long? = null,
        val replyTo: QuotedMessageUi? = null,
        val reactions: List<String> = emptyList(),
    ) : ChatEntry

    data class Assistant(
        override val key: String,
        val text: String,
        val live: Boolean,
        val reasoning: String = "",
        val reasoningMs: Long = 0,
        val reasoningStartedAt: Long = 0,
        override val messageId: Long? = null,
        val replyTo: QuotedMessageUi? = null,
        val reactions: List<String> = emptyList(),
        val stats: MessageStats? = null,
    ) : ChatEntry

    data class Trace(override val key: String, val steps: List<TraceStep>) : ChatEntry

    data class Approval(override val key: String, val card: ApprovalUi) : ChatEntry

    data class Plan(override val key: String, val plan: ProposedPlan, val card: ApprovalUi) : ChatEntry

    data class Ask(override val key: String, val card: AskCardUi) : ChatEntry

    data class Failure(override val key: String, val message: String, val retryable: Boolean) : ChatEntry

    data class Notice(override val key: String, val text: String) : ChatEntry

    data class Compaction(
        override val key: String,
        val summary: String,
        val streaming: Boolean = false,
        val tokensBefore: Int = 0,
        val tokensAfter: Int = 0,
    ) : ChatEntry
}

data class ProviderOption(
    val id: String,
    val name: String,
    val model: String,
    val icon: String = "",
)

data class ModelOption(
    val id: String,
    val meta: ModelMeta = ModelMeta(),
)

data class SuggestionUi(
    val title: String,
    val prompt: String,
    val icon: String = "",
)

data class ChatUiState(
    val loading: Boolean = true,
    val conversationId: String = "",
    val title: String = "",
    val assistantName: String = "",
    val workingText: String = "",
    val name: String? = null,
    val history: List<ChatEntry> = emptyList(),
    val live: List<ChatEntry> = emptyList(),
    val suggestions: List<SuggestionUi> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val compacting: Boolean = false,
    val turn: Int = 0,
    val providers: List<ProviderOption> = emptyList(),
    val providerId: String = "",
    val model: String = "",
    val reasoningLevel: ReasoningLevel = ReasoningLevel.Auto,
    val mode: AgentMode = AgentMode.Build,
    val attachments: List<AttachmentUi> = emptyList(),
    val models: List<ModelOption> = emptyList(),
    val modelsLoading: Boolean = false,
    val picker: ComposerPicker? = null,
    val modelQuery: String = "",
    val askDrafts: Map<String, AskDraft> = emptyMap(),
    val retry: RetryUi? = null,
    val policy: CommandPolicy = CommandPolicy.Manual,
    val liveStats: MessageStats = MessageStats(),
    val statsConfig: ChatDetailConfig = ChatDetailConfig(),
    val todos: List<ConversationTodo> = emptyList(),
    val menu: ChatMenuUi? = null,
    val edit: EditUi? = null,
    val reply: ReplyUi? = null,
    val pendingDelete: ChatMenuUi? = null,
    val slash: SlashUi = SlashUi(),
    val btw: BtwUi = BtwUi(),
    val subAgentRuns: Map<String, SubAgentRunUi> = emptyMap(),
    val subAgentOpenId: String? = null,
    val highlightKey: String? = null,
    val selectionKey: String? = null,
    val plan: ProposedPlan? = null,
    val planStatus: PlanStatus = PlanStatus.None,
    val message: UiMessage? = null,
) : UiState {
    val entries: List<ChatEntry>
        get() = history + live

    val stats: MessageStats?
        get() {
            if (liveStats.providerId.isNotBlank() || liveStats.hasUsage || liveStats.round > 0) {
                return liveStats
            }
            return history.filterIsInstance<ChatEntry.Assistant>().lastOrNull()?.stats
        }

    val streaming: String
        get() = live.filterIsInstance<ChatEntry.Assistant>().lastOrNull()?.text.orEmpty()

    val reasoning: String
        get() = live.filterIsInstance<ChatEntry.Assistant>().lastOrNull()?.reasoning.orEmpty()

    val approval: ApprovalUi?
        get() = live.filterIsInstance<ChatEntry.Approval>().lastOrNull()?.card

    val planCard: ChatEntry.Plan?
        get() = entries.filterIsInstance<ChatEntry.Plan>().lastOrNull()

    val ask: AskCardUi?
        get() = live.filterIsInstance<ChatEntry.Ask>().lastOrNull()?.card

    val subAgent: SubAgentRunUi?
        get() = subAgentOpenId?.let { subAgentRuns[it] }

    val providerName: String
        get() = providers.firstOrNull { it.id == providerId }?.name ?: providerId

    val canSend: Boolean get() = (input.isNotBlank() || attachments.isNotEmpty()) && !sending
    val isEmpty: Boolean get() = history.isEmpty() && !sending && streaming.isBlank() && reasoning.isBlank()
}

enum class ComposerPicker { Provider, Model, Reasoning }

sealed interface ChatAction : UiAction {
    data class InputChanged(val value: String) : ChatAction
    data class Suggest(val text: String) : ChatAction
    data object Send : ChatAction
    data object Stop : ChatAction
    data class Decide(val approvalId: String, val approve: Boolean) : ChatAction
    data class DecidePlan(val approvalId: String, val approve: Boolean, val policy: fan.x0.focus.data.assistant.CommandPolicy) : ChatAction
    data class AskSelect(val askId: String, val questionId: String, val label: String) : ChatAction
    data class AskConfirm(val askId: String, val questionId: String, val value: Boolean) : ChatAction
    data class AskEdit(
        val askId: String,
        val questionId: String,
        val field: AskField,
        val value: String,
    ) : ChatAction
    data class AskSubmit(val askId: String) : ChatAction
    data class AskSkip(val askId: String) : ChatAction
    data object NewConversation : ChatAction
    data object CloseBtw : ChatAction
    data object RetryBtw : ChatAction
    data class OpenPicker(val picker: ComposerPicker?) : ChatAction
    data class SelectProvider(val providerId: String) : ChatAction
    data class SelectModel(val model: String) : ChatAction
    data class ModelQueryChanged(val value: String) : ChatAction
    data object RefreshModels : ChatAction
    data class SelectReasoning(val level: ReasoningLevel) : ChatAction
    data class AttachPicked(val uris: List<String>) : ChatAction
    data class RemoveAttachment(val id: String) : ChatAction
    data class SetMode(val mode: AgentMode) : ChatAction
    data class OpenMessageMenu(val key: String) : ChatAction
    data object CloseMessageMenu : ChatAction
    data class ReplyTo(val key: String) : ChatAction
    data object CancelReply : ChatAction
    data class StartEdit(val key: String) : ChatAction
    data class EditDraftChanged(val value: String) : ChatAction
    data object SaveEdit : ChatAction
    data object CancelEdit : ChatAction
    data class AskDelete(val key: String) : ChatAction
    data object ConfirmDelete : ChatAction
    data object CancelDelete : ChatAction
    data class SelectText(val key: String) : ChatAction
    data object ClearSelection : ChatAction
    data class JumpToMessage(val key: String) : ChatAction
    data object ConsumeHighlight : ChatAction
    data class SlashInputChanged(val value: String) : ChatAction
    data class SlashMove(val delta: Int) : ChatAction
    data class SlashPick(val id: SlashCommandId) : ChatAction
    data object SlashSelect : ChatAction
    data object SlashClose : ChatAction
    data object RetryLast : ChatAction
    data object OpenSessions : ChatAction
    data object OpenSettings : ChatAction
    data class OpenSubAgent(val id: String) : ChatAction
    data object CloseSubAgent : ChatAction
    data object ConsumeMessage : ChatAction
}

sealed interface ChatEffect : UiEffect {
    data object Copied : ChatEffect
    data object Deleted : ChatEffect
    data object EditSaved : ChatEffect
    data object ReplySent : ChatEffect
    data object OpenSettings : ChatEffect
    data object OpenSessions : ChatEffect
}
