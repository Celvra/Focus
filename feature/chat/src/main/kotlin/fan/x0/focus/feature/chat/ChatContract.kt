package fan.x0.focus.feature.chat

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.core.network.openai.ModelMeta
import fan.x0.focus.data.assistant.AskKind
import fan.x0.focus.data.assistant.AttachmentKind
import fan.x0.focus.data.assistant.ReasoningLevel

enum class TraceStatus { Pending, Running, Done, Failed }

data class TraceStep(
    val label: String,
    val status: TraceStatus,
    val summary: String? = null,
)

enum class ApprovalPhase { Waiting, Running, Finished, Declined }

data class ApprovalUi(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<TraceStep>,
    val phase: ApprovalPhase,
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

sealed interface ChatEntry {
    val key: String

    data class User(
        override val key: String,
        val text: String,
        val attachments: List<AttachmentUi> = emptyList(),
    ) : ChatEntry

    data class Assistant(
        override val key: String,
        val text: String,
        val live: Boolean,
        val reasoning: String = "",
    ) : ChatEntry

    data class Trace(override val key: String, val steps: List<TraceStep>) : ChatEntry

    data class Approval(override val key: String, val card: ApprovalUi) : ChatEntry

    data class Ask(override val key: String, val card: AskCardUi) : ChatEntry
}

data class ProviderOption(
    val id: String,
    val name: String,
    val model: String,
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
    val turn: Int = 0,
    val providers: List<ProviderOption> = emptyList(),
    val providerId: String = "",
    val model: String = "",
    val reasoningLevel: ReasoningLevel = ReasoningLevel.Auto,
    val attachments: List<AttachmentUi> = emptyList(),
    val models: List<ModelOption> = emptyList(),
    val modelsLoading: Boolean = false,
    val picker: ComposerPicker? = null,
    val modelQuery: String = "",
    val askDrafts: Map<String, AskDraft> = emptyMap(),
) : UiState {
    val entries: List<ChatEntry>
        get() = history + live

    val streaming: String
        get() = live.filterIsInstance<ChatEntry.Assistant>().lastOrNull()?.text.orEmpty()

    val reasoning: String
        get() = live.filterIsInstance<ChatEntry.Assistant>().lastOrNull()?.reasoning.orEmpty()

    val approval: ApprovalUi?
        get() = live.filterIsInstance<ChatEntry.Approval>().lastOrNull()?.card

    val ask: AskCardUi?
        get() = live.filterIsInstance<ChatEntry.Ask>().lastOrNull()?.card

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
    data class OpenPicker(val picker: ComposerPicker?) : ChatAction
    data class SelectProvider(val providerId: String) : ChatAction
    data class SelectModel(val model: String) : ChatAction
    data class ModelQueryChanged(val value: String) : ChatAction
    data object RefreshModels : ChatAction
    data class SelectReasoning(val level: ReasoningLevel) : ChatAction
    data class AttachPicked(val uris: List<String>) : ChatAction
    data class RemoveAttachment(val id: String) : ChatAction
}

sealed interface ChatEffect : UiEffect
