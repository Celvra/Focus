package fan.x0.focus.data.device.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fan.x0.focus.data.device.DeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

class DeviceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val store = KoinPlatform.getKoin().get<DeviceStore>()
                if (store.triggersOnce().any { it.enabled }) {
                    DeviceTriggerService.start(context)
                }
            }
            pending.finish()
        }
    }
}
