package fan.x0.focus.data.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class DeviceShellSession(
    val shell: String,
    val root: Boolean,
    private val process: Process,
    scope: CoroutineScope,
) {
    val id: String = UUID.randomUUID().toString()
    private val startedAt = System.currentTimeMillis()
    private val lock = Any()
    private val buffer = StringBuilder()

    @Volatile
    var alive: Boolean = true
        private set

    @Volatile
    var exitCode: Int = -1
        private set

    init {
        scope.launch(Dispatchers.IO) {
            val output = launch(Dispatchers.IO) { read(process.inputStream) }
            val error = launch(Dispatchers.IO) { read(process.errorStream) }
            val code = runCatching { process.waitFor() }.getOrDefault(-1)
            output.join()
            error.join()
            exitCode = code
            alive = false
        }
    }

    fun send(input: String): Boolean {
        if (!alive) return false
        val value = if (input.endsWith("\n")) input else "$input\n"
        return runCatching {
            process.outputStream.write(value.toByteArray())
            process.outputStream.flush()
            true
        }.getOrDefault(false)
    }

    fun tail(maxChars: Int = 20_000): String = synchronized(lock) {
        buffer.takeLast(maxChars.coerceIn(1, MAX_BUFFER)).toString()
    }

    fun info(): DeviceShellSessionInfo = DeviceShellSessionInfo(
        id = id,
        shell = shell,
        root = root,
        startedAt = startedAt,
        alive = alive,
    )

    fun close() {
        alive = false
        runCatching { process.destroy() }
    }

    private fun read(stream: java.io.InputStream) {
        runCatching {
            stream.bufferedReader().use { reader ->
                val chunk = CharArray(2048)
                while (alive) {
                    val count = reader.read(chunk)
                    if (count < 0) break
                    if (count > 0) append(String(chunk, 0, count))
                }
            }
        }
    }

    private fun append(text: String) {
        if (text.isEmpty()) return
        synchronized(lock) {
            buffer.append(text)
            if (buffer.length > MAX_BUFFER) buffer.delete(0, buffer.length - MAX_BUFFER)
        }
    }

    companion object {
        private const val MAX_BUFFER = 200_000
    }
}
