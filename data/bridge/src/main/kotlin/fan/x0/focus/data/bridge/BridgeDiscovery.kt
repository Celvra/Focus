package fan.x0.focus.data.bridge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

@Serializable
data class BridgeDiscoveryPayload(
    val name: String = "",
    val version: String = "",
    val port: Int = 0,
    val os: String = "",
    val arch: String = "",
)

data class BridgeDiscoveredHost(
    val host: String,
    val port: Int,
    val name: String,
    val os: String,
    val arch: String,
    val version: String,
)

@org.koin.core.annotation.Single
class BridgeDiscovery {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    suspend fun scan(timeoutMs: Int = 900): List<BridgeDiscoveredHost> = withContext(Dispatchers.IO) {
        val probe = """{"kind":"focus-bridge-discover"}""".toByteArray()
        val targets = broadcastTargets()
        val found = mutableListOf<BridgeDiscoveredHost>()
        val sockets = targets.mapNotNull { target ->
            runCatching {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 300
                socket.send(DatagramPacket(probe, probe.size, target, DISCOVERY_PORT))
                socket
            }.getOrNull()
        }
        if (sockets.isEmpty()) return@withContext emptyList()

        val deadline = System.currentTimeMillis() + timeoutMs
        val buffer = ByteArray(2048)
        coroutineScope {
            sockets.map { socket ->
                async {
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        try {
                            socket.receive(packet)
                        } catch (_: SocketTimeoutException) {
                            continue
                        } catch (_: Throwable) {
                            break
                        }
                        val payload = runCatching {
                            json.decodeFromString(
                                BridgeDiscoveryPayload.serializer(),
                                String(packet.data, 0, packet.length),
                            )
                        }.getOrNull() ?: continue
                        val host = packet.address?.hostAddress ?: continue
                        val entry = BridgeDiscoveredHost(
                            host = host,
                            port = if (payload.port == 0) 7788 else payload.port,
                            name = payload.name.ifBlank { host },
                            os = payload.os,
                            arch = payload.arch,
                            version = payload.version,
                        )
                        synchronized(found) { found += entry }
                    }
                }
            }.awaitAll()
        }
        sockets.forEach { socket -> runCatching { socket.close() } }
        found.distinctBy { it.host to it.port }
    }

    private fun broadcastTargets(): List<InetAddress> {
        val targets = mutableListOf<InetAddress>()
        runCatching { targets += InetAddress.getByName("255.255.255.255") }
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
                .forEach { targets += it }
        }
        return targets.distinct()
    }

    companion object {
        const val DISCOVERY_PORT = 7789
    }
}
