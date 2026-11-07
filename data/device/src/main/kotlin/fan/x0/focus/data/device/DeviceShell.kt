package fan.x0.focus.data.device

import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import rikka.shizuku.Shizuku
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Single
class DeviceShell {
    private val sessions = ConcurrentHashMap<String, DeviceShellSession>()
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var cached = DeviceShellStatus()

    suspend fun refresh(): DeviceShellStatus = withContext(Dispatchers.IO) {
        val root = detectRoot()
        val shizuku = detectShizuku()
        val status = when {
            root -> DeviceShellStatus(DeviceShellMode.Root, root = true, shizuku = shizuku, detail = "su")
            shizuku -> DeviceShellStatus(DeviceShellMode.Shizuku, root = false, shizuku = true, detail = "shizuku")
            else -> DeviceShellStatus(DeviceShellMode.None, detail = "root and shizuku are unavailable")
        }
        cached = status
        status
    }

    fun status(): DeviceShellStatus = cached

    fun requestShizukuPermission(): Boolean = runCatching {
        if (!Shizuku.pingBinder()) return false
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.requestPermission(1)
        true
    }.getOrDefault(false)

    private val permissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                cached = DeviceShellStatus(DeviceShellMode.Shizuku, shizuku = true, detail = "shizuku")
            }
            runCatching { Shizuku.removeRequestPermissionResultListener(this) }
        }
    }

    suspend fun openSession(): DeviceShellSession? {
        val status = if (cached.mode == DeviceShellMode.None) refresh() else cached
        val process = when (status.mode) {
            DeviceShellMode.Root -> runCatching {
                ProcessBuilder("su").redirectErrorStream(true).start()
            }.getOrNull()
            DeviceShellMode.Shizuku -> shizukuProcessRaw(arrayOf("sh"))
            else -> null
        } ?: return null
        val session = DeviceShellSession(
            shell = status.mode.name.lowercase(),
            root = status.root,
            process = process,
            scope = sessionScope,
        )
        sessions[session.id] = session
        return session
    }

    fun session(id: String): DeviceShellSession? = sessions[id]

    fun sessions(): List<DeviceShellSessionInfo> =
        sessions.values.map { it.info() }.sortedByDescending { it.startedAt }

    fun closeSession(id: String): Boolean {
        val session = sessions.remove(id) ?: return false
        session.close()
        return true
    }

    fun closeAllSessions() {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }

    suspend fun startProcess(command: String): Process? {
        val status = if (cached.mode == DeviceShellMode.None) refresh() else cached
        return when (status.mode) {
            DeviceShellMode.Root -> runCatching {
                ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            }.getOrNull()
            DeviceShellMode.Shizuku -> shizukuProcessRaw(arrayOf("sh", "-c", command))
            else -> null
        }
    }

    suspend fun run(command: String, timeoutMs: Long = 120_000): DeviceShellResult =
        withContext(Dispatchers.IO) {
            val status = if (cached.mode == DeviceShellMode.None) refresh() else cached
            when (status.mode) {
                DeviceShellMode.Root -> execute(listOf("su", "-c", command), timeoutMs, root = true)
                DeviceShellMode.Shizuku -> executeShizuku(command, timeoutMs)
                else -> DeviceShellResult(
                    ok = false,
                    error = "No root or Shizuku shell is available",
                    exitCode = -1,
                )
            }
        }

    private suspend fun detectRoot(): Boolean {
        val result = execute(listOf("su", "-c", "id"), 4_000, root = true)
        return result.ok && result.output.contains("uid=0")
    }

    private fun detectShizuku(): Boolean = runCatching {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    private suspend fun execute(
        command: List<String>,
        timeoutMs: Long,
        root: Boolean,
    ): DeviceShellResult = coroutineScope {
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: Throwable) {
            return@coroutineScope DeviceShellResult(
                ok = false,
                error = e.message ?: e.javaClass.simpleName,
                root = root,
            )
        }
        val reader = async(Dispatchers.IO) {
            runCatching { process.inputStream.bufferedReader().use { it.readText() } }
                .getOrDefault("")
        }
        val finished = runCatching {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        }.getOrDefault(false)
        if (!finished) {
            runCatching { process.destroyForcibly() }
        }
        val output = reader.await()
        val exit = runCatching { process.exitValue() }.getOrDefault(-1)
        DeviceShellResult(
            ok = finished && exit == 0,
            output = output.trim(),
            error = if (finished) "" else "Command timed out",
            exitCode = exit,
            root = root,
        )
    }

    private suspend fun executeShizuku(command: String, timeoutMs: Long): DeviceShellResult =
        coroutineScope {
            val process = try {
                shizukuProcess(command)
            } catch (e: Throwable) {
                null
            } ?: return@coroutineScope DeviceShellResult(
                ok = false,
                error = "Shizuku shell is unavailable",
            )
            val reader = async(Dispatchers.IO) {
                runCatching { process.inputStream.bufferedReader().use { it.readText() } }
                    .getOrDefault("")
            }
            val errorReader = async(Dispatchers.IO) {
                runCatching { process.errorStream.bufferedReader().use { it.readText() } }
                    .getOrDefault("")
            }
            val finished = runCatching {
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            }.getOrDefault(false)
            if (!finished) runCatching { process.destroyForcibly() }
            val output = reader.await()
            val error = errorReader.await()
            val exit = runCatching { process.exitValue() }.getOrDefault(-1)
            DeviceShellResult(
                ok = finished && exit == 0,
                output = listOf(output, error).filter { it.isNotBlank() }.joinToString("\n").trim(),
                error = if (finished) "" else "Command timed out",
                exitCode = exit,
            )
        }

    private fun shizukuProcess(command: String): Process? =
        shizukuProcessRaw(arrayOf("sh", "-c", command))

    private fun shizukuProcessRaw(command: Array<String>): Process? = runCatching {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        method.invoke(null, command, null, null) as? Process
    }.getOrNull()
}
