package fan.x0.focus.core.notify

enum class NotifyActionKind { Approval, Plan, Question }

interface MessageNotifier {
    fun notifyReply(conversationId: String, title: String, preview: String, failed: Boolean)
    fun notifyAction(conversationId: String, title: String, kind: NotifyActionKind)
    fun notifyFeeds(added: Int)
    fun cancel(conversationId: String)
    fun cancelAll()
}
