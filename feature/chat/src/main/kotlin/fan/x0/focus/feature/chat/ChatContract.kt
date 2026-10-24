package fan.x0.focus.feature.chat

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.core.network.openai.ModelMeta
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

data class ChatUiState(
    val loading: Boolean = true,
    val conversationId: String = "",
    val title: String = "",
    val name: String? = null,
    val history: List<ChatEntry> = emptyList(),
    val live: List<ChatEntry> = emptyList(),
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
) : UiState {
    val entries: List<ChatEntry>
        get() = history + live

    val streaming: String
        get() = live.filterIsInstance<ChatEntry.Assistant>().lastOrNull()?.text.orEmpty()

    val reasoning: String
        get() = live.filterIsInstance<ChatEntry.Assistant>().lastOrNull()?.reasoning.orEmpty()

    val approval: ApprovalUi?
        get() = live.filterIsInstance<ChatEntry.Approval>().lastOrNull()?.card

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
