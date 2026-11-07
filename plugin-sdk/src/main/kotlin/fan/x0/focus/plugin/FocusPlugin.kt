package fan.x0.focus.plugin

import android.accessibilityservice.AccessibilityService
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent

interface FocusPlugin {
    fun onCreate(context: PluginContext)
    fun onStart() = Unit
    fun onStop() = Unit
    fun onDestroy() = Unit

    fun tools(): List<PluginTool> = emptyList()
    fun pages(): List<PluginPage> = emptyList()
    fun skills(): List<PluginSkill> = emptyList()
    fun channels(): List<fan.x0.focus.data.im.ImChannelFactory> = emptyList()
    fun sources(): List<PluginSource> = emptyList()

    suspend fun prompt(): String? = null
    suspend fun onEvent(event: PluginEvent) = Unit
    suspend fun onSchedule(jobId: String) = Unit
}

interface PluginAccessibilityHandler {
    fun onAccessibilityEvent(event: AccessibilityEvent, service: AccessibilityService)
    fun onInterrupt(service: AccessibilityService)
}

interface PluginNotificationHandler {
    fun onNotificationPosted(notification: StatusBarNotification, service: NotificationListenerService)
    fun onNotificationRemoved(notification: StatusBarNotification, service: NotificationListenerService)
    fun onListenerConnected(service: NotificationListenerService) = Unit
}

interface PluginBootHandler {
    fun onBoot()
    fun onPackageReplaced() = Unit
}

interface PluginTileHandler {
    fun content(): PluginTileContent
    fun onClick()
}

interface PluginWidgetHandler {
    fun content(widgetId: Int): PluginWidgetContent
}
