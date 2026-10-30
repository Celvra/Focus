package fan.x0.focus.data.assistant

enum class TodoStatus {
    Pending,
    InProgress,
    Completed,
    Cancelled,
    ;

    val wire: String
        get() = when (this) {
            Pending -> "pending"
            InProgress -> "in_progress"
            Completed -> "completed"
            Cancelled -> "cancelled"
        }

    companion object {
        fun fromWire(value: String): TodoStatus = when (value.lowercase()) {
            "in_progress", "in-progress", "inprogress" -> InProgress
            "completed", "done" -> Completed
            "cancelled", "canceled" -> Cancelled
            else -> Pending
        }
    }
}

enum class TodoPriority {
    High,
    Medium,
    Low,
    ;

    val wire: String
        get() = when (this) {
            High -> "high"
            Medium -> "medium"
            Low -> "low"
        }

    companion object {
        fun fromWire(value: String): TodoPriority = when (value.lowercase()) {
            "high" -> High
            "low" -> Low
            else -> Medium
        }
    }
}

data class ConversationTodo(
    val id: Long = 0,
    val conversationId: String,
    val content: String,
    val status: TodoStatus,
    val priority: TodoPriority,
    val position: Int,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
