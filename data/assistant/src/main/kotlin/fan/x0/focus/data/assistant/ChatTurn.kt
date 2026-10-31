package fan.x0.focus.data.assistant

import fan.x0.focus.core.error.AppError

enum class ChatTurnStepStatus { Pending, Running, Done, Failed }

data class ChatTurnStep(
    val label: String,
    val status: ChatTurnStepStatus,
    val id: String = "",
    val tool: String = "",
    val arguments: String = "",
    val summary: String? = null,
    val output: String? = null,
    val durationMs: Long = 0,
    val startedAt: Long = 0,
    val diffs: List<FileDiff> = emptyList(),
)

enum class ChatTurnApprovalPhase { Waiting, Running, Finished, Declined }

data class ChatTurnApproval(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<ChatTurnStep>,
    val phase: ChatTurnApprovalPhase,
)

enum class ChatTurnAskPhase { Waiting, Answered, Skipped }

data class ChatTurnAsk(
    val id: String,
    val intro: String,
    val questions: List<AskQuestion>,
    val phase: ChatTurnAskPhase,
    val answers: List<AskAnswer> = emptyList(),
)

sealed interface ChatTurnEntry {
    data class Answer(
        val text: String,
        val reasoning: String,
        val reasoningMs: Long = 0,
        val reasoningStartedAt: Long = 0,
    ) : ChatTurnEntry
    data class Trace(val steps: List<ChatTurnStep>) : ChatTurnEntry
    data class Approval(val card: ChatTurnApproval) : ChatTurnEntry
    data class Plan(val plan: ProposedPlan, val card: ChatTurnApproval) : ChatTurnEntry
    data class Ask(val card: ChatTurnAsk) : ChatTurnEntry
}

data class ChatTurnState(
    val conversationId: String = "",
    val turnId: Long = 0,
    val running: Boolean = false,
    val workingText: String = "",
    val entries: List<ChatTurnEntry> = emptyList(),
    val retry: RetryState? = null,
    val stats: MessageStats = MessageStats(),
    val todos: List<ConversationTodo> = emptyList(),
    val plan: ProposedPlan? = null,
    val planRequestId: String = "",
)

data class RetryState(
    val attempt: Int,
    val max: Int,
    val reason: String,
)

data class ChatTurnResult(
    val conversationId: String,
    val turnId: Long,
    val error: AppError? = null,
)
