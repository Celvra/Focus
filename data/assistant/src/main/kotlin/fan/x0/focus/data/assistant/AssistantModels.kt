package fan.x0.focus.data.assistant

import kotlinx.serialization.Serializable

enum class ChatRole { User, Assistant, Tool, System }

@Serializable
data class ToolCallRecord(
    val id: String,
    val name: String,
    val arguments: String,
)

data class ChatMessage(
    val id: Long,
    val conversationId: String,
    val role: ChatRole,
    val content: String,
    val reasoning: String = "",
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
    val createdAt: Long = 0,
    val attachments: List<ChatAttachment> = emptyList(),
    val label: String? = null,
    val replyToId: Long? = null,
    val stats: MessageStats? = null,
    val reactions: List<String> = emptyList(),
    val elapsedMs: Long = 0,
    val stepDiffs: List<List<FileDiff>> = emptyList(),
)

enum class AgentMode {
    Build,
    Plan,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String): AgentMode = when (value.lowercase()) {
            "plan" -> Plan
            else -> Build
        }
    }
}

enum class PlanStatus {
    None,
    Pending,
    Approved,
    Rejected,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String): PlanStatus = when (value.lowercase()) {
            "pending" -> Pending
            "approved" -> Approved
            "rejected" -> Rejected
            else -> None
        }
    }
}

@Serializable
data class ProposedPlan(
    val title: String = "",
    val summary: String = "",
    val steps: List<PlanStep> = emptyList(),
    val files: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val verification: List<String> = emptyList(),
)

@Serializable
data class PlanStep(
    val title: String,
    val detail: String = "",
    val tool: String = "",
    val arguments: String = "",
)

enum class AttachmentKind { Text, Image }

@Serializable
data class ChatAttachment(
    val id: String,
    val kind: AttachmentKind,
    val name: String,
    val mime: String = "",
    val text: String? = null,
    val base64: String? = null,
) {
    val dataUrl: String?
        get() = base64?.let { "data:$mime;base64,$it" }
}

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
    val providerId: String = "",
    val model: String = "",
    val reasoning: String = "",
    val mode: AgentMode = AgentMode.Build,
    val plan: ProposedPlan? = null,
    val planStatus: PlanStatus = PlanStatus.None,
    val planRequestId: String = "",
)

enum class GoalStatus { Active, Paused, Done }

data class Goal(
    val id: String,
    val title: String,
    val detail: String?,
    val status: GoalStatus,
    val dueAt: Long?,
    val progress: Int,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val category: String = "",
    val icon: String = "",
)

@Serializable
data class ApprovalStep(
    val tool: String,
    val arguments: String,
    val label: String,
)

enum class ApprovalStatus { Pending, Approved, Rejected, Expired }

data class ApprovalRequest(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<ApprovalStep>,
    val status: ApprovalStatus,
    val createdAt: Long,
    val result: String? = null,
)

@Serializable
data class IdeaStep(
    val title: String,
    val detail: String = "",
)

data class Idea(
    val id: String,
    val title: String,
    val detail: String,
    val steps: List<IdeaStep>,
    val category: String,
    val icon: String,
    val createdAt: Long,
)

data class Update(
    val id: String,
    val headline: String,
    val body: String,
    val reason: String,
    val category: String,
    val icon: String,
    val articleId: String?,
    val createdAt: Long,
    val feedback: Int,
)

data class Reminder(
    val id: String,
    val text: String,
    val at: Long,
    val done: Boolean,
)
