package fan.x0.focus.data.plugin.system

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import fan.x0.focus.data.plugin.PluginRegistry
import org.koin.mp.KoinPlatform

class PluginNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        current = this
        val registry = runCatching { KoinPlatform.getKoin().get<PluginRegistry>() }.getOrNull() ?: return
        registry.notificationHandlers().forEach { (plugin, handler) ->
            runCatching { handler.onListenerConnected(this) }
                .onFailure { registry.log().write(plugin.manifest.id, "W", "Notification listener failed", it) }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val registry = runCatching { KoinPlatform.getKoin().get<PluginRegistry>() }.getOrNull() ?: return
        registry.notificationHandlers().forEach { (plugin, handler) ->
            runCatching { handler.onNotificationPosted(sbn, this) }
                .onFailure { registry.log().write(plugin.manifest.id, "W", "Notification posted failed", it) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val registry = runCatching { KoinPlatform.getKoin().get<PluginRegistry>() }.getOrNull() ?: return
        registry.notificationHandlers().forEach { (plugin, handler) ->
            runCatching { handler.onNotificationRemoved(sbn, this) }
                .onFailure { registry.log().write(plugin.manifest.id, "W", "Notification removed failed", it) }
        }
    }

    override fun onListenerDisconnected() {
        current = null
        super.onListenerDisconnected()
    }

    companion object {
        @Volatile
        var current: PluginNotificationListenerService? = null
            private set
    }
}
