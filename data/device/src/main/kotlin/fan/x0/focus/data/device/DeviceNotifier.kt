package fan.x0.focus.data.device

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fan.x0.focus.data.device.system.DeviceStopReceiver
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class DeviceNotifier(@Provided private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        createChannel()
    }

    fun showSession(owner: String) {
        val stop = Intent(context, DeviceStopReceiver::class.java).setAction(DeviceStopReceiver.ACTION_STOP)
        val pending = PendingIntent.getBroadcast(
            context,
            41,
            stop,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(context.getString(R.string.device_notification_controlling))
            .setContentText(owner)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        context,
                        android.R.drawable.ic_menu_close_clear_cancel,
                    ),
                    context.getString(R.string.device_notification_stop),
                    pending,
                ).build(),
            )
            .build()
        runCatching { manager?.notify(SESSION_ID, notification) }
    }

    fun cancelSession() {
        runCatching { manager?.cancel(SESSION_ID) }
    }

    fun showBackground(id: String, command: String, result: String) {
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(context.getString(R.string.device_notification_background))
            .setContentText("$command\n$result")
            .setStyle(Notification.BigTextStyle().bigText("$command\n$result"))
            .setAutoCancel(true)
            .build()
        val notificationId = BG_ID + ((id.hashCode() and 0x7fffffff) % 500)
        runCatching { manager?.notify(notificationId, notification) }
    }

    fun showTrigger(name: String, command: String, ok: Boolean, output: String) {
        val body = buildString {
            append(command)
            append('\n')
            append(if (ok) "ok" else "failed")
            if (output.isNotBlank()) append('\n').append(output.take(300))
        }
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(name)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        val notificationId = TRIGGER_ID + ((name.hashCode() and 0x7fffffff) % 500)
        runCatching { manager?.notify(notificationId, notification) }
    }

    fun showServer() {
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(context.getString(R.string.device_notification_controlled))
            .setOngoing(true)
            .build()
        runCatching { manager?.notify(SERVER_ID, notification) }
    }

    fun cancelServer() {
        runCatching { manager?.cancel(SERVER_ID) }
    }

    fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                context.getString(R.string.device_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val CHANNEL = "focus_device_control"
        const val SESSION_ID = 6101
        const val SERVER_ID = 6102
        const val BG_ID = 6200
        const val TRIGGER_ID = 6700
    }
}
