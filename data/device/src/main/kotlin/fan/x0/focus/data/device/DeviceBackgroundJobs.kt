package fan.x0.focus.data.device

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Single
class DeviceBackgroundJobs(
    private val shell: DeviceShell,
    private val notifier: DeviceNotifier,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun start(command: String, timeoutMs: Long = 600_000): DeviceBgJobInfo? {
        val process = shell.startProcess(command) ?: return null
        val job = Job(UUID.randomUUID().toString(), command, process)
        jobs[job.id] = job
        scope.launch {
            val out = launch(Dispatchers.IO) { job.read(process.inputStream) }
            val err = launch(Dispatchers.IO) { job.read(process.errorStream) }
            val timeoutJob = scope.launch {
                delay(timeoutMs.coerceIn(1_000, 86_400_000))
                if (job.running) {
                    job.timedOut = true
                    runCatching { process.destroyForcibly() }
                }
            }
            val code = withContext(Dispatchers.IO) { runCatching { process.waitFor() }.getOrDefault(-1) }
            timeoutJob.cancel()
            out.join()
            err.join()
            job.exitCode = code
            job.endedAt = System.currentTimeMillis()
            job.running = false
            notifier.showBackground(
                id = job.id,
                command = job.command,
                result = if (job.timedOut) "timed out" else "exit code $code",
            )
        }
        return job.info()
    }

    fun info(id: String): DeviceBgJobInfo? = jobs[id]?.info()

    fun logs(id: String): String? = jobs[id]?.output()

    fun kill(id: String): Boolean {
        val job = jobs[id] ?: return false
        job.timedOut = false
        job.running = false
        runCatching { job.process.destroyForcibly() }
        return true
    }

    fun list(): List<DeviceBgJobInfo> =
        jobs.values.map { it.info() }.sortedByDescending { it.startedAt }

    fun clearFinished() {
        jobs.entries.removeIf { !it.value.running }
    }

    fun stopAll() {
        jobs.values.forEach {
            it.running = false
            runCatching { it.process.destroyForcibly() }
        }
        jobs.clear()
    }

    private class Job(
        val id: String,
        val command: String,
        val process: Process,
    ) {
        val startedAt = System.currentTimeMillis()
        private val lock = Any()
        private val buffer = StringBuilder()

        @Volatile
        var running = true

        @Volatile
        var timedOut = false

        @Volatile
        var exitCode = -1

        @Volatile
        var endedAt = 0L

        fun read(stream: java.io.InputStream) {
            runCatching {
                stream.bufferedReader().use { reader ->
                    val chunk = CharArray(2048)
                    while (running) {
                        val count = reader.read(chunk)
                        if (count < 0) break
                        if (count > 0) append(String(chunk, 0, count))
                    }
                }
            }
        }

        fun output(): String = synchronized(lock) { buffer.toString() }

        fun info(): DeviceBgJobInfo = DeviceBgJobInfo(
            id = id,
            command = command,
            pid = 0,
            startedAt = startedAt,
            endedAt = endedAt,
            exitCode = exitCode,
            running = running,
            timedOut = timedOut,
        )

        private fun append(text: String) {
            if (text.isEmpty()) return
            synchronized(lock) {
                buffer.append(text)
                if (buffer.length > MAX_BUFFER) buffer.delete(0, buffer.length - MAX_BUFFER)
            }
        }
    }

    companion object {
        private const val MAX_BUFFER = 200_000
    }
}
