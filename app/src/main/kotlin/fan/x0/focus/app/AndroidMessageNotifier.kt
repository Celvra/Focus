package fan.x0.focus.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fan.x0.focus.MainActivity
import fan.x0.focus.R
import fan.x0.focus.core.notify.AppPresence
import fan.x0.focus.core.notify.MessageNotifier
import fan.x0.focus.core.notify.NotificationSettings
import fan.x0.focus.core.notify.NotifyActionKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class AndroidMessageNotifier(
    @Provided private val context: Context,
    private val settings: NotificationSettings,
    private val presence: AppPresence,
) : MessageNotifier {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val postedConversations = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var feedPosted = false

    override fun notifyReply(conversationId: String, title: String, preview: String, failed: Boolean) {
        scope.launch {
            if (!allowed(conversationId)) return@launch
            val heading = context.getString(
                if (failed) R.string.notification_reply_failed_title else R.string.notification_reply_title,
            )
            val body = preview.ifBlank {
                context.getString(
                    if (failed) R.string.notification_reply_failed_body else R.string.notification_reply_body,
                )
            }
            post(
                id = idFor(conversationId),
                conversationId = conversationId,
                heading = heading,
                title = title,
                body = body,
            )
            postedConversations.add(conversationId)
        }
    }

    override fun notifyAction(conversationId: String, title: String, kind: NotifyActionKind) {
        scope.launch {
            if (!allowed(conversationId)) return@launch
            val body = context.getString(
                when (kind) {
                    NotifyActionKind.Approval -> R.string.notification_action_approval
                    NotifyActionKind.Plan -> R.string.notification_action_plan
                    NotifyActionKind.Question -> R.string.notification_action_question
                },
            )
            post(
                id = idFor(conversationId),
                conversationId = conversationId,
                heading = context.getString(R.string.notification_action_title),
                title = title,
                body = body,
            )
            postedConversations.add(conversationId)
        }
    }

    override fun notifyFeeds(added: Int) {
        if (added <= 0) return
        scope.launch {
            if (!settings.enabledOnce() || !presence.shouldNotifyBackground()) return@launch
            post(
                id = FEED_NOTIFICATION_ID,
                conversationId = null,
                heading = context.getString(R.string.app_name),
                title = context.getString(R.string.notification_feed_title),
                body = context.resources.getQuantityString(R.plurals.notification_feed_body, added, added),
            )
            feedPosted = true
        }
    }

    override fun cancel(conversationId: String) {
        manager?.cancel(idFor(conversationId))
        postedConversations.remove(conversationId)
    }

    override fun cancelAll() {
        postedConversations.forEach { manager?.cancel(idFor(it)) }
        postedConversations.clear()
        if (feedPosted) {
            manager?.cancel(FEED_NOTIFICATION_ID)
            feedPosted = false
        }
    }

    private suspend fun allowed(conversationId: String): Boolean =
        settings.enabledOnce() && presence.shouldNotify(conversationId)

    private fun post(
        id: Int,
        conversationId: String?,
        heading: String,
        title: String,
        body: String,
    ) {
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title.ifBlank { context.getString(R.string.app_name) })
            .setSubText(heading)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent(conversationId, id))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .build()
        manager?.notify(id, notification)
    }

    private fun contentIntent(conversationId: String?, requestId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (conversationId != null) putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }
        return PendingIntent.getActivity(
            context,
            requestId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun idFor(conversationId: String): Int =
        MESSAGE_ID_BASE + (conversationId.hashCode() and 0x0FFF)

    companion object {
        const val CHANNEL_ID = "focus_messages"
        const val EXTRA_CONVERSATION_ID = "focus_conversation_id"
        private const val MESSAGE_ID_BASE = 5100
        private const val FEED_NOTIFICATION_ID = 4301
    }
}
