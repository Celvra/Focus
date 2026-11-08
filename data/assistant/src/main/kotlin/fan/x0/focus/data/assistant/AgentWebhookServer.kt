package fan.x0.focus.data.assistant

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.koin.core.annotation.Single
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest

@Single
class AgentWebhookServer(
    private val store: AgentTriggerStore,
    private val bus: AgentEventBus,
) {
    private var engine: EmbeddedServer<*, *>? = null

    fun start() {
        if (engine != null) return
        engine = runCatching {
            embeddedServer(CIO, host = "0.0.0.0", port = PORT) {
                routing {
                    post("/agent/hook/{token}") {
                        val token = call.parameters["token"].orEmpty()
                        val trigger = store.byToken(token)
                        if (trigger == null || !trigger.enabled) {
                            call.respondText(
                                """{"error":"unknown trigger"}""",
                                status = HttpStatusCode.NotFound,
                            )
                            return@post
                        }
                        val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0
                        if (length > MAX_BODY_BYTES) {
                            call.respondText(
                                """{"error":"payload too large"}""",
                                status = HttpStatusCode.PayloadTooLarge,
                            )
                            return@post
                        }
                        val body = call.receiveText().take(MAX_BODY_CHARS)
                        val eventId = call.request.headers["X-Focus-Event-Id"].orEmpty()
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
                        call.respondText(
                            if (event == null) """{"status":"duplicate"}""" else """{"status":"accepted"}""",
                            status = if (event == null) HttpStatusCode.OK else HttpStatusCode.Accepted,
                        )
                    }
                }
            }.start(wait = false)
        }.getOrNull()
    }

    fun stop() {
        engine?.stop(500, 1_000)
        engine = null
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
        return "http://$host:$PORT"
    }

    private fun hash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val PORT = 8765
        private const val MAX_BODY_BYTES = 512 * 1024L
        private const val MAX_BODY_CHARS = 32_000
    }
}
