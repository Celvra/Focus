package fan.x0.focus.data.device.system

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import fan.x0.focus.core.notify.R as NotifyR
import fan.x0.focus.data.device.DeviceNotifier
import org.koin.mp.KoinPlatform

class DeviceTriggerService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching { KoinPlatform.getKoin().get<DeviceNotifier>().createChannel() }
        val notification = Notification.Builder(this, DeviceNotifier.CHANNEL)
            .setSmallIcon(NotifyR.drawable.ic_notification_mascot)
            .setContentTitle(getString(fan.x0.focus.data.device.R.string.device_trigger_service_title))
            .setContentText(getString(fan.x0.focus.data.device.R.string.device_trigger_service_body))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ID = 6104
        private const val ACTION_STOP = "fan.x0.focus.device.TRIGGER_STOP"

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, DeviceTriggerService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, DeviceTriggerService::class.java))
            }
        }
    }
}
