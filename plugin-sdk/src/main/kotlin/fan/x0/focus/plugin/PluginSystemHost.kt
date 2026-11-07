package fan.x0.focus.plugin

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.Intent
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.Composable

interface PluginOverlayHost {
    fun canDraw(): Boolean
    fun requestPermission()
    fun show(pluginId: String, content: @Composable () -> Unit): Boolean
    fun hide(pluginId: String)
    fun isShowing(pluginId: String): Boolean
}

interface PluginForegroundHost {
    fun start(pluginId: String, title: String, text: String)
    fun stop(pluginId: String)
}

interface PluginNotificationHost {
    fun channel(id: String, name: String, importance: Int)
    fun notify(id: Int, channelId: String, title: String, text: String, intent: Intent? = null)
    fun cancel(id: Int)
}

interface PluginActivityHost {
    fun openPage(pluginId: String, pageId: String, args: String? = null)
    fun openSettings()
    fun openOverlaySettings()
    fun openAccessibilitySettings()
    fun openNotificationListenerSettings()
    fun shareFile(path: String, mime: String)
}

interface PluginPermissionHost {
    fun canDrawOverlays(): Boolean
    fun has(permission: String): Boolean
    fun openAppSettings()
}

interface PluginScreenCaptureHost {
    fun isRunning(): Boolean
    fun start()
    fun stop()
    fun latestFrame(): Bitmap?
}

interface PluginAccessibilityHost {
    fun isEnabled(): Boolean
    fun service(): AccessibilityService?
    fun openSettings()
}

interface PluginNotificationListenerHost {
    fun isEnabled(): Boolean
    fun service(): NotificationListenerService?
    fun openSettings()
}

interface PluginTileHost {
    fun update(content: PluginTileContent)
    fun requestUpdate()
}

interface PluginWidgetHost {
    fun update(widgetId: Int, content: PluginWidgetContent)
    fun updateAll()
}

interface PluginBootHost {
    fun isEnabled(): Boolean
}

interface PluginSystemHost {
    val overlay: PluginOverlayHost
    val foreground: PluginForegroundHost
    val notifications: PluginNotificationHost
    val activity: PluginActivityHost
    val permissions: PluginPermissionHost
    val screen: PluginScreenCaptureHost
    val accessibility: PluginAccessibilityHost
    val notificationListener: PluginNotificationListenerHost
    val tile: PluginTileHost
    val widget: PluginWidgetHost
    val boot: PluginBootHost
}

interface PluginNotificationState {
    val posted: List<Notification>
}
