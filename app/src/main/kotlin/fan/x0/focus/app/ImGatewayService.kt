package fan.x0.focus.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import fan.x0.focus.MainActivity
import fan.x0.focus.R
import fan.x0.focus.core.notify.R as NotifyR
import fan.x0.focus.data.im.ImAccountStore
import fan.x0.focus.data.im.ImConnectionState
import fan.x0.focus.data.im.ImGateway
import fan.x0.focus.data.im.ImLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

class ImGatewayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var gateway: ImGateway
    private var firstStart = true

    override fun onCreate() {
        super.onCreate()
        ImLog.i(TAG, "service create")
        startForegroundNotification(0, 0)
        gateway = KoinPlatform.getKoin().get()
        scope.launch {
            gateway.statuses.collect { statuses ->
                val running = statuses.values.count {
                    it.state == ImConnectionState.Connecting || it.state == ImConnectionState.Connected
                }
                val connected = statuses.values.count { it.state == ImConnectionState.Connected }
                notify(running, connected)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = intent?.getBooleanExtra(EXTRA_RELOAD, false) ?: false
        val shouldReload = firstStart || requested
        ImLog.i(TAG, "start command reload=$requested first=$firstStart shouldReload=$shouldReload")
        firstStart = false
        if (!shouldReload) return START_STICKY
        scope.launch {
            try {
                val accounts = KoinPlatform.getKoin().get<ImAccountStore>()
                if (accounts.enabled().isEmpty()) {
                    ImLog.i(TAG, "no enabled accounts, stop service")
                    stopSelf()
                    return@launch
                }
                gateway.reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ImLog.e(TAG, "reload failed", e)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ImLog.i(TAG, "service destroy")
        if (::gateway.isInitialized) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    gateway.stopAll()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    ImLog.e(TAG, "stop failed", e)
                }
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(running: Int, connected: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(running, connected))
    }

    private fun startForegroundNotification(running: Int, connected: Int) {
        val notification = buildNotification(running, connected)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(running: Int, connected: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(NotifyR.drawable.ic_notification_mascot)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_im_running, running, connected))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "focus_im_gateway"
        private const val TAG = "FocusIM/Service"
        private const val EXTRA_RELOAD = "reload"
        private const val NOTIFICATION_ID = 4102

        fun start(context: Context, reload: Boolean = false) {
            try {
                context.startForegroundService(
                    Intent(context, ImGatewayService::class.java)
                        .putExtra(EXTRA_RELOAD, reload),
                )
            } catch (e: Throwable) {
                ImLog.w(TAG, "start service failed", e)
            }
        }

        suspend fun sync(context: Context, accounts: ImAccountStore) {
            try {
                if (accounts.enabled().isEmpty()) {
                    context.stopService(Intent(context, ImGatewayService::class.java))
                } else {
                    start(context, reload = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
    }
}
