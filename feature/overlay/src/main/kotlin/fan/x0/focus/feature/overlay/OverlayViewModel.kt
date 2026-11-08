package fan.x0.focus.feature.overlay

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AttachmentKind
import fan.x0.focus.data.assistant.ChatAttachment
import fan.x0.focus.data.assistant.ChatRole
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatTurnApprovalPhase
import fan.x0.focus.data.assistant.ChatTurnAskPhase
import fan.x0.focus.data.assistant.ChatTurnEntry
import fan.x0.focus.data.assistant.ChatTurnRunner
import fan.x0.focus.data.assistant.ChatTurnStepStatus
import fan.x0.focus.data.assistant.ChatTurnTrustPhase
import fan.x0.focus.data.assistant.MainSessionStore
import fan.x0.focus.data.assistant.TurnOptions
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.util.UUID

@KoinViewModel
class OverlayViewModel(
    private val chats: ChatStore,
    private val turns: ChatTurnRunner,
    private val mainSession: MainSessionStore,
) : BaseViewModel<OverlayAction, OverlayUiState, OverlayEffect>(OverlayUiState()) {

    private var mainId = ""
    private var pendingSubmit = false
    private var wasWorking = false

    init {
        viewModelScope.launch {
            mainId = chats.ensureMainConversation().id
            load()
            mainSession.id.collect { id ->
                if (id != mainId) {
                    mainId = id
                    load()
                }
            }
        }
        viewModelScope.launch {
            turns.states.collect { states ->
                if (mainId.isBlank()) return@collect
                val turn = states[mainId] ?: return@collect
                applyTurn(turn)
            }
        }
        viewModelScope.launch {
            chats.changes.collect {
                if (!pendingSubmit && !wasWorking && !currentState.sending) load()
            }
        }
    }

    override fun onAction(action: OverlayAction) {
        when (action) {
            is OverlayAction.InputChanged -> updateState { copy(input = action.text) }
            is OverlayAction.Submit -> submit(action.text)
            OverlayAction.Stop -> {
                if (mainId.isNotBlank()) turns.stop(mainId)
                wasWorking = false
                pendingSubmit = false
                updateState {
                    copy(
                        sending = false,
                        messages = messages.filterNot { it.id == STREAMING_ID } +
                            OverlayMessage(-2, OverlayRole.Assistant, "已停止。"),
                    )
                }
            }
            OverlayAction.CaptureRequested -> updateState {
                copy(capturing = true, error = null, errorDetail = null)
            }
            is OverlayAction.ScreenshotCaptured -> updateState {
                copy(
                    capturing = false,
                    attachment = OverlayAttachment(
                        base64 = action.base64,
                        selection = action.selection,
                    ),
                    error = null,
                    errorDetail = null,
                )
            }
            OverlayAction.ScreenshotFailed -> updateState {
                copy(capturing = false, error = OverlayError.CaptureFailed, errorDetail = null)
            }
            OverlayAction.RemoveAttachment -> updateState { copy(attachment = null) }
            is OverlayAction.DecideApproval -> turns.decide(action.id, action.approve)
            is OverlayAction.DecideTrust -> turns.decideTrust(action.id, action.trusted)
            is OverlayAction.AnswerAsk -> turns.answer(action.askId, action.answers, skipped = false)
            is OverlayAction.SkipAsk -> turns.answer(action.id, emptyList(), skipped = true)
        }
    }

    private fun submit(text: String) {
        val state = currentState
        val message = text.trim()
        if (state.sending || state.capturing) return
        if (message.isBlank() && state.attachment == null) return
        val attachment = state.attachment
        val chatAttachment = attachment?.let {
            ChatAttachment(
                id = UUID.randomUUID().toString(),
                kind = AttachmentKind.Image,
                name = it.name,
                mime = it.mime,
                base64 = it.base64,
            )
        }
        pendingSubmit = true
        updateState {
            copy(
                input = "",
                attachment = null,
                sending = true,
                error = null,
                errorDetail = null,
                toolSteps = emptyList(),
                approval = null,
                trust = null,
                ask = null,
                messages = messages +
                    OverlayMessage(
                        id = -System.currentTimeMillis(),
                        role = OverlayRole.User,
                        text = message.ifBlank { "看一下这张图片" },
                    ) +
                    OverlayMessage(
                        id = STREAMING_ID,
                        role = OverlayRole.Assistant,
                        text = "",
                        streaming = true,
                    ),
            )
        }
        viewModelScope.launch {
            mainId = chats.ensureMainConversation().id
            val handle = turns.start(
                conversationId = mainId,
                text = message.ifBlank { "看一下这张图片" },
                options = TurnOptions(attachments = listOfNotNull(chatAttachment), autoTitle = false),
            )
            if (handle == null) {
                pendingSubmit = false
                updateState {
                    copy(
                        sending = false,
                        error = OverlayError.SendFailed,
                        errorDetail = null,
                        messages = messages.filterNot { it.id == STREAMING_ID },
                    )
                }
            }
        }
    }

    private suspend fun applyTurn(turn: fan.x0.focus.data.assistant.ChatTurnState) {
        if (!turn.running) {
            wasWorking = false
            pendingSubmit = false
            updateState { copy(sending = false, approval = null, trust = null, ask = null) }
            if (currentState.messages.any { it.streaming }) load()
            return
        }
        pendingSubmit = false
        wasWorking = true
        val answer = turn.entries.filterIsInstance<ChatTurnEntry.Answer>().lastOrNull()?.text.orEmpty()
        val steps = turn.entries
            .filterIsInstance<ChatTurnEntry.Trace>()
            .flatMap { it.steps }
            .map { step ->
                OverlayToolStep(
                    id = step.tool.ifBlank { step.label },
                    label = step.label,
                    status = when (step.status) {
                        ChatTurnStepStatus.Pending -> OverlayStepStatus.Pending
                        ChatTurnStepStatus.Running -> OverlayStepStatus.Running
                        ChatTurnStepStatus.Done -> OverlayStepStatus.Done
                        ChatTurnStepStatus.Failed -> OverlayStepStatus.Failed
                    },
                    summary = step.summary,
                )
            }
        val approval = turn.entries.filterIsInstance<ChatTurnEntry.Approval>().lastOrNull()?.card
        val trust = turn.entries.filterIsInstance<ChatTurnEntry.Trust>().lastOrNull()?.card
        val ask = turn.entries.filterIsInstance<ChatTurnEntry.Ask>().lastOrNull()?.card
        val currentStreaming = currentState.messages.lastOrNull { it.streaming }?.text.orEmpty()
        updateState {
            copy(
                sending = true,
                toolSteps = steps,
                approval = approval?.let {
                    OverlayApproval(
                        id = it.id,
                        title = it.title,
                        summary = it.summary,
                        phase = when (it.phase) {
                            ChatTurnApprovalPhase.Waiting -> OverlayApprovalPhase.Waiting
                            ChatTurnApprovalPhase.Running -> OverlayApprovalPhase.Running
                            ChatTurnApprovalPhase.Finished -> OverlayApprovalPhase.Finished
                            ChatTurnApprovalPhase.Declined -> OverlayApprovalPhase.Declined
                        },
                    )
                },
                trust = trust?.let {
                    OverlayTrust(
                        id = it.id,
                        label = it.label,
                        phase = when (it.phase) {
                            ChatTurnTrustPhase.Waiting -> OverlayTrustPhase.Waiting
                            ChatTurnTrustPhase.Trusted -> OverlayTrustPhase.Trusted
                            ChatTurnTrustPhase.Declined -> OverlayTrustPhase.Declined
                        },
                    )
                },
                ask = ask?.let {
                    OverlayAsk(
                        id = it.id,
                        intro = it.intro,
                        phase = when (it.phase) {
                            ChatTurnAskPhase.Waiting -> OverlayAskPhase.Waiting
                            ChatTurnAskPhase.Answered -> OverlayAskPhase.Answered
                            ChatTurnAskPhase.Skipped -> OverlayAskPhase.Skipped
                        },
                    )
                },
                messages = if (answer.isBlank() && currentStreaming.isBlank()) {
                    messages
                } else {
                    val base = messages.filterNot { it.id == STREAMING_ID }
                    base + OverlayMessage(
                        id = STREAMING_ID,
                        role = OverlayRole.Assistant,
                        text = answer,
                        streaming = true,
                    )
                },
            )
        }
    }

    private suspend fun load() {
        if (mainId.isBlank()) mainId = chats.ensureMainConversation().id
        val messages = chats.messages(mainId, limit = 40)
            .mapNotNull { message ->
                val role = when (message.role) {
                    ChatRole.User -> OverlayRole.User
                    ChatRole.Assistant -> OverlayRole.Assistant
                    else -> return@mapNotNull null
                }
                val text = message.content.trim()
                if (text.isBlank()) return@mapNotNull null
                OverlayMessage(
                    id = message.id,
                    role = role,
                    text = text,
                )
            }
        val turn = turns.state(mainId)
        val steps = turn.entries
            .filterIsInstance<ChatTurnEntry.Trace>()
            .flatMap { it.steps }
            .map { step ->
                OverlayToolStep(
                    id = step.tool.ifBlank { step.label },
                    label = step.label,
                    status = when (step.status) {
                        ChatTurnStepStatus.Pending -> OverlayStepStatus.Pending
                        ChatTurnStepStatus.Running -> OverlayStepStatus.Running
                        ChatTurnStepStatus.Done -> OverlayStepStatus.Done
                        ChatTurnStepStatus.Failed -> OverlayStepStatus.Failed
                    },
                    summary = step.summary,
                )
            }
        updateState {
            copy(
                messages = messages,
                sending = turn.running,
                toolSteps = steps,
            )
        }
    }

    private companion object {
        const val STREAMING_ID = -1L
    }
}
