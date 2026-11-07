package fan.x0.focus.data.device.display

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Looper
import android.os.Process
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream

class DisplayServerMain {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Looper.prepareMainLooper()
            val socketName = args.getOrNull(0) ?: error("socket name is required")
            val appUid = args.getOrNull(1)?.toIntOrNull() ?: -1
            val systemContext = systemContext()
            val shell = shellContext(systemContext)
            DisplaySocketServer(shell, socketName, appUid).start()
            Looper.loop()
        }

        private fun systemContext(): Context {
            val threadClass = Class.forName("android.app.ActivityThread")
            val thread = threadClass.getMethod("systemMain").invoke(null)
            return threadClass.getMethod("getSystemContext").invoke(thread) as Context
        }
    }
}

internal class DisplaySocketServer(
    private val context: Context,
    socketName: String,
    private val appUid: Int,
) {
    private val server = LocalServerSocket(socketName)

    fun start() {
        Thread({ acceptLoop() }, "focus-display-server").start()
    }

    private fun acceptLoop() {
        while (true) {
            val socket = runCatching { server.accept() }.getOrNull() ?: break
            Thread({ serve(socket) }, "focus-display-client").start()
        }
    }

    private fun serve(socket: LocalSocket) {
        val peer = runCatching { socket.peerCredentials.uid }.getOrDefault(-1)
        if (peer != 0 && peer != 2000 && peer != appUid) {
            runCatching { socket.close() }
            return
        }
        val input = BufferedInputStream(socket.inputStream)
        val output = java.io.BufferedOutputStream(socket.outputStream)
        val session = DisplaySession(context)
        try {
            while (true) {
                val line = readLine(input) ?: break
                val request = JSONObject(line)
                val response = handle(request, session, output)
                if (response != null) {
                    output.write((response.toString() + "\n").toByteArray())
                    output.flush()
                }
            }
        } catch (_: Throwable) {
        } finally {
            session.stop()
            runCatching { socket.close() }
            runCatching { server.close() }
            Process.killProcess(Process.myPid())
        }
    }

    private fun handle(request: JSONObject, session: DisplaySession, output: OutputStream): JSONObject? {
        return runCatching {
            when (request.optString("op")) {
                "start" -> {
                    val result = session.start(
                        request.optInt("width", 1080),
                        request.optInt("height", 1920),
                        request.optInt("density", 320),
                    )
                    JSONObject()
                        .put("ok", true)
                        .put("displayId", result.displayId)
                        .put("width", result.width)
                        .put("height", result.height)
                        .put("density", result.density)
                        .put("trusted", result.trusted)
                }
                "stop" -> {
                    session.stop()
                    JSONObject().put("ok", true)
                }
                "status" -> statusJson(session)
                "capture" -> {
                    val bytes = session.capture(
                        request.optInt("quality", 80),
                        request.optInt("maxWidth", 0),
                    )
                    if (bytes == null) {
                        JSONObject().put("ok", false).put("error", "no frame")
                    } else {
                        output.write((JSONObject().put("ok", true).put("bytes", bytes.size).toString() + "\n").toByteArray())
                        output.write(bytes)
                        output.flush()
                        null
                    }
                }
                "tap" -> JSONObject().put(
                    "ok",
                    session.tap(
                        request.optInt("displayId"),
                        request.optInt("x"),
                        request.optInt("y"),
                        request.optInt("durationMs", 60),
                    ),
                )
                "swipe" -> JSONObject().put(
                    "ok",
                    session.swipe(
                        request.optInt("displayId"),
                        request.optInt("startX"),
                        request.optInt("startY"),
                        request.optInt("endX"),
                        request.optInt("endY"),
                        request.optInt("durationMs", 300),
                    ),
                )
                "key" -> JSONObject().put(
                    "ok",
                    session.key(request.optInt("displayId"), request.optInt("keyCode")),
                )
                "text" -> JSONObject().put(
                    "ok",
                    session.text(request.optInt("displayId"), request.optString("text")),
                )
                "launch" -> JSONObject().put(
                    "ok",
                    session.launch(
                        request.optInt("displayId"),
                        request.optString("packageName"),
                        request.optString("activity"),
                    ),
                )
                else -> JSONObject().put("ok", false).put("error", "unknown op")
            }
        }.getOrElse {
            JSONObject().put("ok", false).put("error", it.message ?: it.javaClass.simpleName)
        }
    }

    private fun statusJson(session: DisplaySession): JSONObject {
        val status = session.status()
        return JSONObject()
            .put("ok", true)
            .put("running", status.running)
            .put("displayId", status.displayId)
            .put("width", status.width)
            .put("height", status.height)
            .put("density", status.density)
            .put("trusted", status.trusted)
            .put("frameReady", status.frameReady)
    }

    private fun readLine(input: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1) return if (bytes.size() == 0) null else bytes.toString("UTF-8")
            if (value == '\n'.code) return bytes.toString("UTF-8")
            if (bytes.size() > 1_000_000) error("request too large")
            bytes.write(value)
        }
    }
}
