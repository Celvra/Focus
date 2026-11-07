package fan.x0.focus.data.plugin.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import fan.x0.focus.core.notify.R as NotifyR
import java.util.concurrent.ConcurrentHashMap

class PluginForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Plugin background", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            val notification = notification("plugin", "Plugin running", "A plugin is active")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(BASE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(BASE_ID, notification)
            }
            return START_STICKY
        }
        when (intent?.action) {
            ACTION_START -> {
                val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                running[pluginId] = true
                val notification = notification(pluginId, title, text)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(notificationId(pluginId), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(notificationId(pluginId), notification)
                }
            }
            ACTION_STOP -> {
                val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID).orEmpty()
                running.remove(pluginId)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                if (running.isEmpty()) stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(pluginId: String, title: String, text: String): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val content = launch?.let {
            PendingIntent.getActivity(
                this,
                notificationId(pluginId),
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(NotifyR.drawable.ic_notification_mascot)
            .setContentTitle(title.ifBlank { "Plugin running" })
            .setContentText(text)
            .setOngoing(true)
            .apply { if (content != null) setContentIntent(content) }
            .build()
    }

    private fun notificationId(pluginId: String): Int = BASE_ID + (pluginId.hashCode() and 0x0FFF)

    companion object {
        const val CHANNEL_ID = "focus_plugin_foreground"
        private const val ACTION_START = "fan.x0.focus.plugin.START"
        private const val ACTION_STOP = "fan.x0.focus.plugin.STOP"
        private const val EXTRA_PLUGIN_ID = "plugin_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val BASE_ID = 5100
        private val running = ConcurrentHashMap<String, Boolean>()

        fun start(context: Context, pluginId: String, title: String, text: String) {
            val intent = Intent(context, PluginForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PLUGIN_ID, pluginId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context, pluginId: String) {
            val intent = Intent(context, PluginForegroundService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_PLUGIN_ID, pluginId)
            runCatching { context.startService(intent) }
        }
    }
}
