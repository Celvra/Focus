package fan.x0.focus.data.device

import android.content.Context
import fan.x0.focus.data.device.server.DeviceServer
import fan.x0.focus.data.device.server.DeviceServerService
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class DeviceServerController(
    @Provided private val context: Context,
    private val server: DeviceServer,
) {
    val running: StateFlow<Boolean> = server.running
    val pairingCode: StateFlow<String?> = server.pairingCode

    fun start() {
        DeviceServerService.start(context)
    }

    fun stop() {
        DeviceServerService.stop(context)
    }

    fun newPairingCode(): String = server.newPairingCode()

    fun address(): String = server.localAddress()

    fun port(): Int = DeviceServer.DEFAULT_PORT
}
