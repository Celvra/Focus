package fan.x0.focus.data.device.display

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import fan.x0.focus.data.device.DeviceShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream

class RootDisplayBackend(
    private val context: Context,
    private val shell: DeviceShell,
) : DisplayBackend {
    override val kind: DisplayBackendKind = DisplayBackendKind.Root

    private val mutex = Mutex()
    private var process: Process? = null
    private var socket: LocalSocket? = null
    private var input: DataInputStream? = null
    private var output: BufferedOutputStream? = null
    private var displayId = 0

    suspend fun available(): Boolean {
        val status = shell.refresh()
        return status.root
    }

    override suspend fun start(spec: VirtualDisplaySpec): DisplayStartResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeLocked()
            val status = shell.refresh()
            if (!status.root) error("root shell is unavailable")
            val socketName = "fan.x0.focus.display.${android.os.Process.myPid()}.${System.nanoTime()}"
            val apk = context.packageCodePath
            val command = "CLASSPATH='${quote(apk)}' /system/bin/app_process /system/bin " +
                "fan.x0.focus.data.device.display.DisplayServerMain " +
                "'$socketName' ${context.applicationInfo.uid}"
            val started = shell.startProcess(command) ?: error("could not start root display helper")
            process = started
            Thread({
                runCatching {
                    started.inputStream.bufferedReader().useLines { lines -> lines.forEach { } }
                }
            }, "focus-display-log").start()
            val connected = connect(socketName)
            if (!connected) {
                closeLocked()
                error("root display helper did not start")
            }
            val response = requestLocked(
                JSONObject()
                    .put("op", "start")
                    .put("width", spec.width)
                    .put("height", spec.height)
                    .put("density", spec.density),
            )
            if (!response.optBoolean("ok")) {
                val message = response.optString("error", "root display start failed")
                closeLocked()
                error(message)
            }
            val result = DisplayStartResult(
                displayId = response.optInt("displayId"),
                width = response.optInt("width"),
                height = response.optInt("height"),
                density = response.optInt("density"),
                trusted = response.optBoolean("trusted"),
                backend = DisplayBackendKind.Root,
            )
            if (result.displayId <= 0) {
                closeLocked()
                error("root display returned invalid id")
            }
            displayId = result.displayId
            result
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        mutex.withLock {
            runCatching { requestLocked(JSONObject().put("op", "stop")) }
            closeLocked()
        }
    }

    override suspend fun status(): DisplayStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (socket == null) return@withLock DisplayStatus()
            val response = runCatching { requestLocked(JSONObject().put("op", "status")) }.getOrNull()
                ?: return@withLock DisplayStatus()
            if (!response.optBoolean("ok")) {
                return@withLock DisplayStatus(error = response.optString("error"))
            }
            DisplayStatus(
                running = response.optBoolean("running"),
                displayId = response.optInt("displayId"),
                width = response.optInt("width"),
                height = response.optInt("height"),
                density = response.optInt("density"),
                trusted = response.optBoolean("trusted"),
                frameReady = response.optBoolean("frameReady"),
            )
        }
    }

    override suspend fun capture(quality: Int, maxWidth: Int): ByteArray? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (socket == null) return@withLock null
            runCatching {
                output?.write(
                    (JSONObject()
                        .put("op", "capture")
                        .put("quality", quality)
                        .put("maxWidth", maxWidth)
                        .toString() + "\n").toByteArray(),
                )
                output?.flush()
                val header = readLine(input ?: return@withLock null)
                if (header == null) {
                    closeLocked()
                    return@withLock null
                }
                val response = JSONObject(header)
                if (!response.optBoolean("ok")) {
                    return@withLock null
                }
                val length = response.optInt("bytes")
                if (length <= 0) return@withLock null
                val bytes = ByteArray(length)
                input?.readFully(bytes)
                bytes
            }.getOrNull()
        }
    }

    override suspend fun tap(displayId: Int, x: Int, y: Int, durationMs: Int): Boolean =
        send(JSONObject().put("op", "tap").put("displayId", displayId).put("x", x).put("y", y).put("durationMs", durationMs))

    override suspend fun swipe(
        displayId: Int,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Int,
    ): Boolean = send(
        JSONObject()
            .put("op", "swipe")
            .put("displayId", displayId)
            .put("startX", startX)
            .put("startY", startY)
            .put("endX", endX)
            .put("endY", endY)
            .put("durationMs", durationMs),
    )

    override suspend fun key(displayId: Int, keyCode: Int): Boolean =
        send(JSONObject().put("op", "key").put("displayId", displayId).put("keyCode", keyCode))

    override suspend fun text(displayId: Int, text: String): Boolean =
        send(JSONObject().put("op", "text").put("displayId", displayId).put("text", text))

    override suspend fun launch(displayId: Int, packageName: String, activity: String): Boolean =
        send(
            JSONObject()
                .put("op", "launch")
                .put("displayId", displayId)
                .put("packageName", packageName)
                .put("activity", activity),
        )

    override fun release() {
        runCatching { socket?.close() }
        runCatching { process?.destroy() }
        socket = null
        process = null
        input = null
        output = null
        displayId = 0
    }

    private suspend fun send(request: JSONObject): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (socket == null) return@withLock false
            runCatching { requestLocked(request).optBoolean("ok") }.getOrDefault(false)
        }
    }

    private fun requestLocked(request: JSONObject): JSONObject {
        val currentOutput = output ?: error("display socket is closed")
        val currentInput = input ?: error("display socket is closed")
        currentOutput.write((request.toString() + "\n").toByteArray())
        currentOutput.flush()
        val line = readLine(currentInput) ?: error("display socket closed")
        return JSONObject(line)
    }

    private suspend fun connect(socketName: String): Boolean {
        repeat(60) {
            val candidate = LocalSocket()
            val connected = runCatching {
                candidate.connect(LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT))
                true
            }.getOrDefault(false)
            if (connected) {
                socket = candidate
                input = DataInputStream(BufferedInputStream(candidate.inputStream))
                output = BufferedOutputStream(candidate.outputStream)
                return true
            }
            runCatching { candidate.close() }
            delay(100)
        }
        return false
    }

    private fun closeLocked() {
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        runCatching { process?.destroy() }
        input = null
        output = null
        socket = null
        process = null
        displayId = 0
    }

    private fun readLine(input: DataInputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value == -1) return if (bytes.size() == 0) null else bytes.toString("UTF-8")
            if (value == '\n'.code) return bytes.toString("UTF-8")
            if (bytes.size() > 1_000_000) error("response too large")
            bytes.write(value)
        }
    }

    private fun quote(value: String): String = value.replace("'", "'\\''")
}
