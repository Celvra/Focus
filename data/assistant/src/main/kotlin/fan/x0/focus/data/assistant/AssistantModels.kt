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
