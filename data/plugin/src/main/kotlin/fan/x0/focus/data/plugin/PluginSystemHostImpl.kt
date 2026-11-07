package fan.x0.focus.data.plugin

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import fan.x0.focus.plugin.PluginAccessibilityHost
import fan.x0.focus.plugin.PluginActivityHost
import fan.x0.focus.plugin.PluginBootHost
import fan.x0.focus.plugin.PluginForegroundHost
import fan.x0.focus.plugin.PluginNotificationHost
import fan.x0.focus.plugin.PluginNotificationListenerHost
import fan.x0.focus.plugin.PluginOverlayHost
import fan.x0.focus.plugin.PluginPermissionHost
import fan.x0.focus.plugin.PluginScreenCaptureHost
import fan.x0.focus.plugin.PluginSystemHost
import fan.x0.focus.plugin.PluginTileContent
import fan.x0.focus.plugin.PluginTileHost
import fan.x0.focus.plugin.PluginWidgetHost
import fan.x0.focus.data.plugin.system.PluginAccessibilityService
import fan.x0.focus.data.plugin.system.PluginForegroundService
import fan.x0.focus.data.plugin.system.PluginNotificationListenerService
import fan.x0.focus.data.plugin.system.PluginScreenCaptureService
import fan.x0.focus.data.plugin.system.PluginTileService
import fan.x0.focus.data.plugin.system.PluginWidgetProvider
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.koin.mp.KoinPlatform
import java.io.File

class PluginForegroundManager(private val context: Context) : PluginForegroundHost {
    override fun start(pluginId: String, title: String, text: String) {
        PluginForegroundService.start(context, pluginId, title, text)
    }

    override fun stop(pluginId: String) {
        PluginForegroundService.stop(context, pluginId)
    }
}

class PluginNotificationManager(private val context: Context) : PluginNotificationHost {
    private val manager = context.getSystemService(android.app.NotificationManager::class.java)

    override fun channel(id: String, name: String, importance: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        manager?.createNotificationChannel(
            android.app.NotificationChannel(id, name, importance.coerceIn(1, 5)),
        )
    }

    override fun notify(id: Int, channelId: String, title: String, text: String, intent: Intent?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val launch = intent ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
        val content = launch?.let {
            android.app.PendingIntent.getActivity(
                context,
                id,
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = android.app.Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .apply { if (content != null) setContentIntent(content) }
            .build()
        manager?.notify(id, notification)
    }

    override fun cancel(id: Int) {
        manager?.cancel(id)
    }
}

class PluginActivityManager(private val context: Context) : PluginActivityHost {
    override fun openPage(pluginId: String, pageId: String, args: String?) {
        PluginIntents.launch(context, pluginId, pageId, args)
    }

    override fun openSettings() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun openOverlaySettings() {
        PluginIntents.overlaySettings(context)
    }

    override fun openAccessibilitySettings() {
        PluginIntents.accessibilitySettings(context)
    }

    override fun openNotificationListenerSettings() {
        PluginIntents.notificationListenerSettings(context)
    }

    override fun shareFile(path: String, mime: String) {
        val file = File(path)
        if (!file.isFile) return
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

class PluginPermissionManager(private val context: Context) : PluginPermissionHost {
    override fun canDrawOverlays(): Boolean = PluginIntents.canDrawOverlays(context)

    override fun has(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun openAppSettings() {
        PluginIntents.appSettings(context)
    }
}

class PluginScreenCaptureManager(private val context: Context) : PluginScreenCaptureHost {
    @Volatile
    private var running = false

    override fun isRunning(): Boolean = running

    override fun start() {
        running = true
    }

    override fun stop() {
        running = false
    }

    override fun latestFrame(): Bitmap? = null
}

class PluginAccessibilityManager(private val context: Context) : PluginAccessibilityHost {
    override fun isEnabled(): Boolean = PluginAccessibilityService.current != null

    override fun service() = PluginAccessibilityService.current

    override fun openSettings() {
        PluginIntents.accessibilitySettings(context)
    }
}

class PluginNotificationListenerManager(private val context: Context) : PluginNotificationListenerHost {
    override fun isEnabled(): Boolean = PluginNotificationListenerService.current != null

    override fun service() = PluginNotificationListenerService.current

    override fun openSettings() {
        PluginIntents.notificationListenerSettings(context)
    }
}

class PluginTileManager : PluginTileHost {
    override fun update(content: PluginTileContent) {
        PluginTileService.current?.render(content)
    }

    override fun requestUpdate() {
        PluginTileService.requestUpdate()
    }
}

class PluginWidgetManager(private val context: Context) : PluginWidgetHost {
    override fun update(widgetId: Int, content: fan.x0.focus.plugin.PluginWidgetContent) {
        PluginWidgetProvider.updateWidget(context, widgetId, content)
    }

    override fun updateAll() {
        PluginWidgetProvider.updateAll(context)
    }
}

class PluginBootManager : PluginBootHost {
    override fun isEnabled(): Boolean =
        runCatching {
            KoinPlatform.getKoin().get<PluginRegistry>().bootHandlers().isNotEmpty()
        }.getOrDefault(false)
}

@Single
class PluginSystemHostImpl(@Provided private val context: Context) : PluginSystemHost {
    override val overlay: PluginOverlayHost = PluginOverlayManager(context)
    override val foreground: PluginForegroundHost = PluginForegroundManager(context)
    override val notifications: PluginNotificationHost = PluginNotificationManager(context)
    override val activity: PluginActivityHost = PluginActivityManager(context)
    override val permissions: PluginPermissionHost = PluginPermissionManager(context)
    override val screen: PluginScreenCaptureHost = PluginScreenCaptureManager(context)
    override val accessibility: PluginAccessibilityHost = PluginAccessibilityManager(context)
    override val notificationListener: PluginNotificationListenerHost = PluginNotificationListenerManager(context)
    override val tile: PluginTileHost = PluginTileManager()
    override val widget: PluginWidgetHost = PluginWidgetManager(context)
    override val boot: PluginBootHost = PluginBootManager()

    fun appContext(): Context = context
}
