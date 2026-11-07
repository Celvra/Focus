package fan.x0.focus.data.device.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fan.x0.focus.data.device.DeviceRuntime
import org.koin.mp.KoinPlatform

class DeviceStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        runCatching {
            KoinPlatform.getKoin().get<DeviceRuntime>().stopSession()
        }
    }

    companion object {
        const val ACTION_STOP = "fan.x0.focus.device.STOP"
    }
}
