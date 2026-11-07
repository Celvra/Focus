package fan.x0.focus.feature.overlay

import fan.x0.focus.core.mvi.UiAction
import fan.x0.focus.core.mvi.UiEffect
import fan.x0.focus.core.mvi.UiState
import fan.x0.focus.data.assistant.AskAnswer
import fan.x0.focus.data.assistant.AskKind

enum class OverlayRole { User, Assistant }

enum class OverlayError { CaptureFailed, SendFailed }

data class OverlayMessage(
    val id: Long,
    val role: OverlayRole,
    val text: String,
    val streaming: Boolean = false,
    val reasoning: String = "",
    val reasoningMs: Long = 0,
)

data class OverlayAttachment(
    val base64: String,
    val mime: String = "image/jpeg",
    val name: String = "Screen",
    val selection: Boolean = false,
)

enum class OverlayStepStatus { Pending, Running, Done, Failed }

data class OverlayToolStep(
    val id: String,
    val label: String,
    val status: OverlayStepStatus,
    val summary: String? = null,
)

enum class OverlayApprovalPhase { Waiting, Running, Finished, Declined }

data class OverlayApproval(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<String> = emptyList(),
    val phase: OverlayApprovalPhase = OverlayApprovalPhase.Waiting,
    val plan: Boolean = false,
)

enum class OverlayTrustPhase { Waiting, Trusted, Declined }

data class OverlayTrust(
    val id: String,
    val label: String,
    val phase: OverlayTrustPhase = OverlayTrustPhase.Waiting,
)

enum class OverlayAskPhase { Waiting, Answered, Skipped }

data class OverlayQuestion(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val kind: AskKind = AskKind.Single,
    val options: List<String> = emptyList(),
    val allowOther: Boolean = false,
    val placeholder: String = "",
)

data class OverlayAsk(
    val id: String,
    val intro: String,
    val questions: List<OverlayQuestion> = emptyList(),
    val phase: OverlayAskPhase = OverlayAskPhase.Waiting,
)

data class OverlayUiState(
    val input: String = "",
    val messages: List<OverlayMessage> = emptyList(),
    val sending: Boolean = false,
    val capturing: Boolean = false,
    val attachment: OverlayAttachment? = null,
    val toolSteps: List<OverlayToolStep> = emptyList(),
    val approval: OverlayApproval? = null,
    val trust: OverlayTrust? = null,
    val ask: OverlayAsk? = null,
    val error: OverlayError? = null,
    val errorDetail: String? = null,
) : UiState

val OverlayUiState.hasResult: Boolean
    get() {
        val answer = messages.lastOrNull { it.role == OverlayRole.Assistant }
        val lastIsUser = messages.lastOrNull()?.role == OverlayRole.User
        return error != null ||
            approval != null ||
            trust != null ||
            toolSteps.isNotEmpty() ||
            lastIsUser ||
            (answer != null && (answer.text.isNotBlank() || answer.streaming || answer.reasoning.isNotBlank()))
    }

sealed interface OverlayAction : UiAction {
    data class InputChanged(val text: String) : OverlayAction

    data class Submit(val text: String) : OverlayAction

    data object Stop : OverlayAction

    data object CaptureRequested : OverlayAction

    data class ScreenshotCaptured(
        val base64: String,
        val selection: Boolean = false,
    ) : OverlayAction

    data object ScreenshotFailed : OverlayAction

    data object RemoveAttachment : OverlayAction

    data class DecideApproval(val id: String, val approve: Boolean) : OverlayAction

    data class DecideTrust(val id: String, val trusted: Boolean) : OverlayAction

    data class AnswerAsk(val askId: String, val answers: List<AskAnswer>) : OverlayAction

    data class SkipAsk(val id: String) : OverlayAction
}

sealed interface OverlayEffect : UiEffect {
    data object Close : OverlayEffect
}
