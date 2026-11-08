package fan.x0.focus.data.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest

@Single
class AgentWebhookServer(
    private val store: AgentTriggerStore,
    private val bus: AgentEventBus,
) {
    private var server: ServerSocket? = null
    private var scope: CoroutineScope? = null

    @Volatile
    private var actualPort = PORT

    fun start() {
        if (server != null) return
        val socket = bind() ?: return
        server = socket
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope ->
            scope.launch {
                while (isActive) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    scope.launch { handle(client) }
                }
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        runCatching { server?.close() }
        server = null
    }

    fun baseUrl(): String {
        val host = runCatching {
            NetworkInterface.getNetworkInterfaces()
                .toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull() ?: "127.0.0.1"
        return "http://$host:$actualPort"
    }

    private fun bind(): ServerSocket? {
        for (port in PORT..PORT + PORT_TRIES) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", port), 50)
                actualPort = port
                return socket
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private suspend fun handle(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = READ_TIMEOUT_MS
                val input = BufferedInputStream(socket.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(' ')
                if (parts.size < 2 || parts[0] != "POST") {
                    respond(socket, 405, """{"error":"method not allowed"}""")
                    return
                }
                val path = parts[1].substringBefore('?')
                val token = path.substringAfterLast('/')
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val index = line.indexOf(':')
                    if (index > 0) {
                        headers[line.substring(0, index).trim().lowercase()] = line.substring(index + 1).trim()
                    }
                }
                val length = headers["content-length"]?.toLongOrNull() ?: 0
                if (length > MAX_BODY_BYTES) {
                    respond(socket, 413, """{"error":"payload too large"}""")
                    return
                }
                val body = readBody(input, length.toInt()).take(MAX_BODY_CHARS)
                val trigger = store.byToken(token)
                if (trigger == null || !trigger.enabled) {
                    respond(socket, 404, """{"error":"unknown trigger"}""")
                    return
                }
                val eventId = headers["x-focus-event-id"].orEmpty()
                val dedupe = if (eventId.isNotBlank()) {
                    "webhook:${trigger.id}:$eventId"
                } else {
                    "webhook:${trigger.id}:${hash(body)}:${System.currentTimeMillis() / 60_000}"
                }
                val event = bus.publish(
                    AgentEvent.webhook(
                        triggerId = trigger.id,
                        name = trigger.name,
                        prompt = trigger.prompt,
                        body = body,
                        profile = trigger.profile,
                        dedupeKey = dedupe,
                    ),
                )
                store.touch(trigger.id, if (event != null) "accepted" else "duplicate")
                if (event == null) {
                    respond(socket, 200, """{"status":"duplicate"}""")
                } else {
                    respond(socket, 202, """{"status":"accepted"}""")
                }
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var count = 0
        while (true) {
            val value = input.read()
            if (value < 0) return if (out.size() == 0) null else out.toString(Charsets.ISO_8859_1.name())
            if (value == '\n'.code) break
            if (value != '\r'.code) out.write(value)
            if (++count > MAX_HEADER_LINE) return null
        }
        return out.toString(Charsets.ISO_8859_1.name())
    }

    private fun readBody(input: InputStream, length: Int): String {
        if (length <= 0) return ""
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(data, read, length - read)
            if (count <= 0) break
            read += count
        }
        return String(data, 0, read, Charsets.UTF_8)
    }

    private fun respond(socket: Socket, status: Int, body: String) {
        val bytes = body.toByteArray()
        val reason = when (status) {
            200 -> "OK"
            202 -> "Accepted"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            413 -> "Payload Too Large"
            else -> "OK"
        }
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray()
        socket.getOutputStream().use { output ->
            output.write(header)
            output.write(bytes)
            output.flush()
        }
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val PORT = 8765
        private const val PORT_TRIES = 10
        private const val READ_TIMEOUT_MS = 5_000
        private const val MAX_HEADER_LINE = 8_192
        private const val MAX_BODY_BYTES = 512 * 1024L
        private const val MAX_BODY_CHARS = 32_000
    }
}
