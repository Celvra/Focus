package fan.x0.focus.data.device.system

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import fan.x0.focus.core.notify.R as NotifyR
import fan.x0.focus.data.device.DeviceNotifier
import fan.x0.focus.data.device.DeviceRuntime
import fan.x0.focus.data.device.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

class DeviceDisplayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                runCatching { KoinPlatform.getKoin().get<DeviceRuntime>().stopSessionWithDisplay() }
                stopSelf()
            }
            return START_NOT_STICKY
        }
        startForegroundNotification()
        val remote = runCatching {
            KoinPlatform.getKoin().get<DeviceRuntime>().session.value?.remote == true
        }.getOrDefault(false)
        if (!remote) openDisplayWindow()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { KoinPlatform.getKoin().get<DeviceNotifier>().cancelDisplay() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun openDisplayWindow() {
        runCatching {
            startService(
                Intent().setClassName(packageName, DISPLAY_WINDOW_SERVICE),
            )
        }
    }

    private fun startForegroundNotification() {
        runCatching {
            KoinPlatform.getKoin().get<DeviceNotifier>().createChannel()
        }
        runCatching { buildAndStartForeground() }
    }

    private fun buildAndStartForeground() {
        val stop = Intent(this, DeviceDisplayService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(
            this,
            43,
            stop,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, DeviceNotifier.CHANNEL)
            .setSmallIcon(NotifyR.drawable.ic_notification_mascot)
            .setContentTitle(getString(R.string.device_notification_display))
            .setContentText(getString(R.string.device_notification_display_body))
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    getString(R.string.device_notification_stop),
                    pending,
                ).build(),
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                DeviceNotifier.DISPLAY_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(DeviceNotifier.DISPLAY_ID, notification)
        }
    }

    companion object {
        private const val ACTION_START = "fan.x0.focus.device.display.START"
        private const val DISPLAY_WINDOW_SERVICE = "fan.x0.focus.feature.overlay.DisplayWindowService"
        private const val ACTION_STOP = "fan.x0.focus.device.display.STOP"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(
                    Intent(context, DeviceDisplayService::class.java).setAction(ACTION_START),
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, DeviceDisplayService::class.java))
            }
        }
    }
}
