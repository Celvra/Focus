package fan.x0.focus.data.device.server

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import fan.x0.focus.data.device.DeviceNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

class DeviceServerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                runCatching { KoinPlatform.getKoin().get<DeviceServer>().stop() }
                runCatching { KoinPlatform.getKoin().get<DeviceNotifier>().cancelServer() }
                stopSelf()
            }
            return START_NOT_STICKY
        }
        startForegroundNotification()
        scope.launch {
            val enabled = runCatching {
                KoinPlatform.getKoin().get<fan.x0.focus.data.device.DeviceStore>().remoteEnabledOnce()
            }.getOrDefault(false)
            if (!enabled) {
                stopSelf()
                return@launch
            }
            runCatching { KoinPlatform.getKoin().get<DeviceServer>().start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.launch {
            runCatching { KoinPlatform.getKoin().get<DeviceServer>().stop() }
            runCatching { KoinPlatform.getKoin().get<DeviceNotifier>().cancelServer() }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNotification() {
        runCatching { KoinPlatform.getKoin().get<DeviceNotifier>().createChannel() }
        val stop = Intent(this, DeviceServerService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(
            this,
            42,
            stop,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, DeviceNotifier.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(getString(fan.x0.focus.data.device.R.string.device_notification_controlled))
            .setContentText(getString(fan.x0.focus.data.device.R.string.device_notification_stop))
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(fan.x0.focus.data.device.R.string.device_notification_stop),
                    pending,
                ).build(),
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(DeviceNotifier.SERVER_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(DeviceNotifier.SERVER_ID, notification)
        }
    }

    companion object {
        private const val ACTION_START = "fan.x0.focus.device.server.START"
        private const val ACTION_STOP = "fan.x0.focus.device.server.STOP"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, DeviceServerService::class.java).setAction(ACTION_START),
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, DeviceServerService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}
