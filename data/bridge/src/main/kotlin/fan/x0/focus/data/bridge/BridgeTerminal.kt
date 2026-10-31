package fan.x0.focus.data.bridge

import fan.x0.focus.core.error.NetworkException
import fan.x0.focus.core.network.NetworkExceptionMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

@Single
class BridgeTerminalClient(client: HttpClient) {
    private val http: HttpClient by lazy {
        client.config { install(WebSockets) }
    }

    fun attach(endpoint: BridgeEndpoint, token: String, sessionId: String, cols: Int, rows: Int): BridgeTerminal =
        BridgeTerminal(http, endpoint, token, sessionId, cols, rows)
}

class BridgeTerminal internal constructor(
    private val http: HttpClient,
    private val endpoint: BridgeEndpoint,
    private val token: String,
    val sessionId: String,
    private val cols: Int,
    private val rows: Int,
) {
    private val outbound = Channel<String>(capacity = Channel.BUFFERED)
    private val json = Json { encodeDefaults = false }

    fun output(): Flow<BridgeTerminalEvent> = channelFlow {
        try {
            val session = http.webSocketSession(
                "${endpoint.wsUrl}/sessions/$sessionId/ws?cols=$cols&rows=$rows",
            ) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val writer = launch {
                for (text in outbound) {
                    session.send(Frame.Text(text))
                }
            }
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    send(BridgeTerminalEvent.Output(frame.readText()))
                }
            }
            writer.cancel()
            send(BridgeTerminalEvent.Closed)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            val mapped = NetworkExceptionMapper.mapOrNull(throwable) ?: NetworkException.Unknown(throwable)
            send(BridgeTerminalEvent.Failed(mapped))
        } finally {
            close()
        }
    }

    suspend fun send(data: String) {
        outbound.send(json.encodeToString(BridgeSessionInput.serializer(), BridgeSessionInput(type = "input", data = data)))
    }

    suspend fun resize(cols: Int, rows: Int) {
        outbound.send(
            json.encodeToString(
                BridgeSessionInput.serializer(),
                BridgeSessionInput(type = "resize", cols = cols, rows = rows),
            ),
        )
    }

    suspend fun signal(name: String) {
        outbound.send(
            json.encodeToString(
                BridgeSessionInput.serializer(),
                BridgeSessionInput(type = "signal", signal = name),
            ),
        )
    }

    fun close() {
        outbound.close()
    }
}

sealed interface BridgeTerminalEvent {
    data class Output(val text: String) : BridgeTerminalEvent
    data object Closed : BridgeTerminalEvent
    data class Failed(val error: Throwable) : BridgeTerminalEvent
}
