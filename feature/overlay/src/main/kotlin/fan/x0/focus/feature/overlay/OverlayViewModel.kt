package fan.x0.focus.feature.overlay

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AgentMode
import fan.x0.focus.data.assistant.AskAnswer
import fan.x0.focus.data.assistant.AssistantSettings
import fan.x0.focus.data.assistant.AttachmentKind
import fan.x0.focus.data.assistant.ChatAttachment
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatTurnApproval
import fan.x0.focus.data.assistant.ChatTurnApprovalPhase
import fan.x0.focus.data.assistant.ChatTurnAsk
import fan.x0.focus.data.assistant.ChatTurnAskPhase
import fan.x0.focus.data.assistant.ChatTurnEntry
import fan.x0.focus.data.assistant.ChatTurnResult
import fan.x0.focus.data.assistant.ChatTurnRunner
import fan.x0.focus.data.assistant.ChatTurnState
import fan.x0.focus.data.assistant.ChatTurnStep
import fan.x0.focus.data.assistant.ChatTurnStepStatus
import fan.x0.focus.data.assistant.ChatTurnTrust
import fan.x0.focus.data.assistant.ChatTurnTrustPhase
import fan.x0.focus.data.assistant.TurnOptions
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.util.UUID

@KoinViewModel
class OverlayViewModel(
    private val chats: ChatStore,
    private val turns: ChatTurnRunner,
    private val settings: AssistantSettings,
) : BaseViewModel<OverlayAction, OverlayUiState, OverlayEffect>(OverlayUiState()) {

    private var conversationId = ""
    private var activeTurnId = 0L
    private var nextMessageId = 0L

    init {
        viewModelScope.launch {
            combine(turns.states, turns.results) { states, results -> states to results }
                .collect { (states, results) -> applyTurn(states, results) }
        }
    }

    override fun onAction(action: OverlayAction) {
        when (action) {
            is OverlayAction.InputChanged -> updateState { copy(input = action.text) }
            is OverlayAction.Submit -> submit(action.text)
            OverlayAction.Stop -> {
                if (conversationId.isNotBlank()) turns.stop(conversationId)
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
            is OverlayAction.AnswerAsk -> {
                updateState { copy(input = "", ask = null, error = null, errorDetail = null) }
                turns.answer(action.askId, action.answers, skipped = false)
            }
            is OverlayAction.SkipAsk -> turns.answer(action.id, emptyList(), skipped = true)
        }
    }

    private fun submit(text: String) {
        val state = currentState
        val message = text.trim()
        val ask = state.ask?.takeIf { it.phase == OverlayAskPhase.Waiting }
        if (ask != null) {
            if (message.isBlank()) return
            val answers = ask.questions.map { AskAnswer(questionId = it.id, text = message) }
            updateState { copy(input = "", ask = null, error = null, errorDetail = null) }
            turns.answer(ask.id, answers, skipped = answers.isEmpty())
            return
        }
        if (state.sending || state.capturing) return
        if (message.isBlank() && state.attachment == null) return
        val attachment = state.attachment
        activeTurnId = 0L
        launchGuarded(
            onError = { error ->
                updateState {
                    copy(
                        sending = false,
                        error = OverlayError.SendFailed,
                        errorDetail = error.serverMessage ?: error.serverTitle,
                    )
                }
            },
        ) {
            val id = conversationId.ifBlank {
                val created = chats.createConversation("")
                conversationId = created.id
                created.id
            }
            val config = settings.configOnce()
            val provider = config.activeProvider
            val chatAttachment = attachment?.let {
                ChatAttachment(
                    id = UUID.randomUUID().toString(),
                    kind = AttachmentKind.Image,
                    name = it.name,
                    mime = it.mime,
                    base64 = it.base64,
                )
            }
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
                        OverlayMessage(nextId(), OverlayRole.User, message) +
                        OverlayMessage(nextId(), OverlayRole.Assistant, "", streaming = true),
                )
            }
            val options = TurnOptions(
                providerId = provider?.id ?: config.activeProviderId,
                model = provider?.model.orEmpty(),
                attachments = listOfNotNull(chatAttachment),
                agentMode = AgentMode.Build,
                silentReply = true,
            )
            val handle = turns.start(id, message, options)
            if (handle == null) {
                updateState {
                    copy(
                        sending = false,
                        error = OverlayError.SendFailed,
                        errorDetail = null,
                    )
                }
            } else {
                activeTurnId = handle.turnId
                applyTurn(turns.states.value, turns.results.value)
            }
        }
    }

    private fun applyTurn(
        states: Map<String, ChatTurnState>,
        results: Map<String, ChatTurnResult>,
    ) {
        val id = conversationId
        if (id.isBlank() || activeTurnId == 0L) return
        val turn = states[id]?.takeIf { it.turnId == activeTurnId }
        val result = results[id]?.takeIf { it.turnId == activeTurnId }
        if (turn == null && result == null) return
        val running = turn?.running == true
        val entries = turn?.entries.orEmpty()
        val trace = entries.filterIsInstance<ChatTurnEntry.Trace>().lastOrNull()
        val plan = entries.filterIsInstance<ChatTurnEntry.Plan>().lastOrNull()
            ?.takeIf { it.card.phase == ChatTurnApprovalPhase.Waiting }
        val approval = entries.filterIsInstance<ChatTurnEntry.Approval>().lastOrNull()
            ?.takeIf { it.card.phase == ChatTurnApprovalPhase.Waiting }
        val trust = entries.filterIsInstance<ChatTurnEntry.Trust>().lastOrNull()
            ?.takeIf { it.card.phase == ChatTurnTrustPhase.Waiting }
        val ask = entries.filterIsInstance<ChatTurnEntry.Ask>().lastOrNull()
            ?.takeIf { it.card.phase == ChatTurnAskPhase.Waiting }
        val turnAnswer = entries.filterIsInstance<ChatTurnEntry.Answer>().lastOrNull()?.text.orEmpty()
        val answer = turnAnswer.ifBlank { result?.answer.orEmpty() }
        val appError = result?.error
        updateState {
            val last = messages.lastOrNull()
            if (last == null || last.role != OverlayRole.Assistant) return@updateState this
            copy(
                messages = messages.dropLast(1) + last.copy(
                    text = answer.ifEmpty { last.text },
                    streaming = running,
                ),
                sending = running,
                toolSteps = trace?.steps?.map { it.toUi() }.orEmpty(),
                approval = if (running) plan?.card?.toUi(plan = true) ?: approval?.card?.toUi() else null,
                trust = if (running) trust?.card?.toUi() else null,
                ask = if (running) ask?.card?.toUi() else null,
                error = if (appError != null) OverlayError.SendFailed else error,
                errorDetail = appError?.let { it.serverMessage ?: it.serverTitle } ?: errorDetail,
            )
        }
    }

    private fun ChatTurnStep.toUi(): OverlayToolStep = OverlayToolStep(
        id = id,
        label = label,
        status = when (status) {
            ChatTurnStepStatus.Pending -> OverlayStepStatus.Pending
            ChatTurnStepStatus.Running -> OverlayStepStatus.Running
            ChatTurnStepStatus.Done -> OverlayStepStatus.Done
            ChatTurnStepStatus.Failed -> OverlayStepStatus.Failed
        },
        summary = summary,
    )

    private fun ChatTurnApproval.toUi(plan: Boolean = false): OverlayApproval = OverlayApproval(
        id = id,
        title = title,
        summary = summary,
        steps = steps.map { it.label }.filter { it.isNotBlank() },
        phase = when (phase) {
            ChatTurnApprovalPhase.Waiting -> OverlayApprovalPhase.Waiting
            ChatTurnApprovalPhase.Running -> OverlayApprovalPhase.Running
            ChatTurnApprovalPhase.Finished -> OverlayApprovalPhase.Finished
            ChatTurnApprovalPhase.Declined -> OverlayApprovalPhase.Declined
        },
        plan = plan,
    )

    private fun ChatTurnTrust.toUi(): OverlayTrust = OverlayTrust(
        id = id,
        label = label,
        phase = when (phase) {
            ChatTurnTrustPhase.Waiting -> OverlayTrustPhase.Waiting
            ChatTurnTrustPhase.Trusted -> OverlayTrustPhase.Trusted
            ChatTurnTrustPhase.Declined -> OverlayTrustPhase.Declined
        },
    )

    private fun ChatTurnAsk.toUi(): OverlayAsk = OverlayAsk(
        id = id,
        intro = intro,
        questions = questions.map { question ->
            OverlayQuestion(
                id = question.id,
                title = question.title,
                subtitle = question.subtitle,
                kind = question.kind,
                options = question.options.map { it.label },
                allowOther = question.allowOther,
                placeholder = question.placeholder,
            )
        },
        phase = when (phase) {
            ChatTurnAskPhase.Waiting -> OverlayAskPhase.Waiting
            ChatTurnAskPhase.Answered -> OverlayAskPhase.Answered
            ChatTurnAskPhase.Skipped -> OverlayAskPhase.Skipped
        },
    )

    private fun nextId(): Long {
        nextMessageId += 1
        return nextMessageId
    }
}
