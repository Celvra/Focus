package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.AppError

enum class ChatTurnStepStatus { Pending, Running, Done, Failed }

data class ChatTurnStep(
    val label: String,
    val status: ChatTurnStepStatus,
    val summary: String? = null,
)

enum class ChatTurnApprovalPhase { Waiting, Running, Finished, Declined }

data class ChatTurnApproval(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<ChatTurnStep>,
    val phase: ChatTurnApprovalPhase,
)

sealed interface ChatTurnEntry {
    data class Answer(val text: String, val reasoning: String) : ChatTurnEntry
    data class Trace(val steps: List<ChatTurnStep>) : ChatTurnEntry
    data class Approval(val card: ChatTurnApproval) : ChatTurnEntry
}

data class ChatTurnState(
    val conversationId: String = "",
    val turnId: Long = 0,
    val running: Boolean = false,
    val workingText: String = "",
    val entries: List<ChatTurnEntry> = emptyList(),
)

data class ChatTurnResult(
    val conversationId: String,
    val turnId: Long,
    val error: AppError? = null,
)
