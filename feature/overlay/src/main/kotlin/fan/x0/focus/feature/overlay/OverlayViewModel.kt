package fan.x0.focus.feature.overlay

import androidx.lifecycle.viewModelScope
import fan.x0.focus.core.mvi.BaseViewModel
import fan.x0.focus.data.assistant.AttachmentKind
import fan.x0.focus.data.assistant.ChatAttachment
import fan.x0.focus.data.assistant.ChatRole
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.MainChannelSession
import fan.x0.focus.data.assistant.RouterSession
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import java.util.UUID

@KoinViewModel
class OverlayViewModel(
    private val chats: ChatStore,
    private val main: MainChannelSession,
    private val router: RouterSession,
) : BaseViewModel<OverlayAction, OverlayUiState, OverlayEffect>(OverlayUiState()) {

    private var pendingSubmit = false
    private var wasWorking = false

    init {
        viewModelScope.launch {
            main.ensureConversation()
            load()
        }
        viewModelScope.launch {
            main.state.collect { channel ->
                val streaming = channel.streaming
                val reasoning = channel.reasoning
                updateState { copy(toolSteps = channel.activities.toSteps()) }
                if (streaming.isNotBlank() || reasoning.isNotBlank() || channel.reasoningMs > 0) {
                    pendingSubmit = false
                    wasWorking = true
                    updateState {
                        val base = messages.filterNot { it.id == STREAMING_ID }
                        copy(
                            sending = true,
                            messages = base + OverlayMessage(
                                id = STREAMING_ID,
                                role = OverlayRole.Assistant,
                                text = streaming,
                                streaming = true,
                                reasoning = reasoning,
                                reasoningMs = channel.reasoningMs,
                            ),
                        )
                    }
                }
                when {
                    channel.working -> {
                        pendingSubmit = false
                        wasWorking = true
                        updateState {
                            copy(
                                sending = true,
                                toolSteps = channel.activities.toSteps(),
                            )
                        }
                    }
                    wasWorking -> {
                        wasWorking = false
                        pendingSubmit = false
                        updateState { copy(sending = false) }
                        load()
                    }
                    !pendingSubmit -> updateState { copy(sending = false) }
                }
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
                router.stopCurrent()
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
            is OverlayAction.DecideApproval -> Unit
            is OverlayAction.DecideTrust -> Unit
            is OverlayAction.AnswerAsk -> Unit
            is OverlayAction.SkipAsk -> Unit
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
        launchGuarded(
            onError = { error ->
                pendingSubmit = false
                updateState {
                    copy(
                        sending = false,
                        error = OverlayError.SendFailed,
                        errorDetail = error.serverMessage ?: error.serverTitle,
                        messages = messages.filterNot { it.id == STREAMING_ID },
                    )
                }
            },
        ) {
            val delivered = main.submit(
                text = message.ifBlank { "看一下这张图片" },
                attachments = listOfNotNull(chatAttachment),
            )
            if (delivered == null) {
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

    private suspend fun load() {
        val messages = chats.messages(ChatStore.MAIN_CONVERSATION_ID, limit = 40)
            .mapNotNull { message ->
                val role = when (message.role) {
                    ChatRole.User -> OverlayRole.User
                    ChatRole.Assistant -> OverlayRole.Assistant
                    else -> return@mapNotNull null
                }
                val text = message.content.trim()
                val reasoning = message.reasoning.trim()
                if (text.isBlank() && reasoning.isBlank()) return@mapNotNull null
                OverlayMessage(
                    id = message.id,
                    role = role,
                    text = text,
                    reasoning = reasoning,
                    reasoningMs = message.elapsedMs,
                )
            }
        val activities = main.state.value.activities.toSteps()
        updateState {
            copy(
                messages = messages,
                sending = main.state.value.working,
                toolSteps = activities,
            )
        }
    }

    private fun List<fan.x0.focus.data.assistant.WorkerActivity>.toSteps(): List<OverlayToolStep> =
        map { activity ->
            OverlayToolStep(
                id = activity.id,
                label = "${activity.title} · ${activity.label}",
                status = if (activity.running) OverlayStepStatus.Running else OverlayStepStatus.Done,
            )
        }

    private companion object {
        const val STREAMING_ID = -1L
    }
}
