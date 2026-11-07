package fan.x0.focus.data.device

import android.content.Context
import android.os.Build
import fan.x0.focus.data.device.system.DeviceAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.util.UUID

@Single
class DeviceRuntime(
    @Provided private val context: Context,
    private val accessibility: DeviceAccessibility,
    private val shell: DeviceShell,
    private val background: DeviceBackgroundJobs,
    private val triggerManager: DeviceTriggerManager,
    private val store: DeviceStore,
    private val notifier: DeviceNotifier,
    private val automationGate: AutomationGate,
) {
    private val _session = MutableStateFlow<DeviceSession?>(null)
    val session: StateFlow<DeviceSession?> = _session.asStateFlow()

    private val _automation = MutableStateFlow<DeviceAutomationState>(DeviceAutomationState.Idle)
    val automation: StateFlow<DeviceAutomationState> = _automation.asStateFlow()

    @Volatile
    private var lastSnapshot: DeviceSnapshot? = null

    @Volatile
    private var lastSignature: String = ""

    @Volatile
    private var shellChecked = false

    suspend fun refresh() {
        shell.refresh()
        shellChecked = true
    }

    fun requestShizukuPermission(): Boolean = shell.requestShizukuPermission()

    suspend fun health(): DeviceHealth {
        if (!shellChecked) {
            shell.refresh()
            shellChecked = true
        }
        val status = shell.status()
        val metrics = context.resources.displayMetrics
        val current = _session.value
        return DeviceHealth(
            id = store.deviceId(),
            name = store.deviceName(),
            model = Build.MODEL.orEmpty(),
            version = Build.VERSION.RELEASE.orEmpty(),
            android = Build.VERSION.SDK_INT.toString(),
            accessibility = accessibility.ready(),
            root = status.root,
            shizuku = status.shizuku,
            remoteEnabled = store.remoteEnabledOnce(),
            session = current != null,
            sessionOwner = current?.owner.orEmpty(),
            width = metrics.widthPixels,
            height = metrics.heightPixels,
        )
    }

    fun startSession(
        owner: String,
        remote: Boolean = false,
        conversationId: String = "",
    ): DeviceSession {
        val current = _session.value
        if (current != null &&
            current.owner == owner &&
            current.remote == remote &&
            current.conversationId == conversationId
        ) {
            return current
        }
        if (current != null) {
            shell.closeAllSessions()
            background.stopAll()
        }
        val next = DeviceSession(
            id = UUID.randomUUID().toString(),
            owner = owner.ifBlank { "local" },
            startedAt = System.currentTimeMillis(),
            remote = remote,
            conversationId = conversationId,
        )
        _session.value = next
        _automation.value = DeviceAutomationState.Running(
            owner = next.owner,
            remote = remote,
            startedAt = next.startedAt,
        )
        notifier.showSession(next.owner)
        return next
    }

    fun stopSession() {
        val current = _session.value
        val hadSession = current != null
        _session.value = null
        shell.closeAllSessions()
        background.stopAll()
        if (hadSession) {
            notifier.cancelSession()
            _automation.value = DeviceAutomationState.Finished(
                owner = current.owner,
                remote = current.remote,
            )
        }
    }

    fun clearAutomation() {
        if (_automation.value is DeviceAutomationState.Finished) {
            _automation.value = DeviceAutomationState.Idle
        }
    }

    suspend fun openShell(): DeviceShellSession? = shell.openSession()

    fun shellSessions(): List<DeviceShellSessionInfo> = shell.sessions()

    fun sendShell(id: String, input: String): Boolean = shell.session(id)?.send(input) ?: false

    fun readShell(id: String, maxChars: Int = 20_000): String? = shell.session(id)?.tail(maxChars)

    fun closeShell(id: String): Boolean = shell.closeSession(id)

    suspend fun backgroundStart(command: String, timeoutMs: Long = 600_000): DeviceBgJobInfo? =
        background.start(command, timeoutMs)

    fun backgroundInfo(id: String): DeviceBgJobInfo? = background.info(id)

    fun backgroundLogs(id: String): String? = background.logs(id)

    fun backgroundKill(id: String): Boolean = background.kill(id)

    fun backgroundList(): List<DeviceBgJobInfo> = background.list()

    fun backgroundClear() = background.clearFinished()

    fun events(limit: Int = 50): List<DeviceEventInfo> = DeviceEvents.recent(limit)

    suspend fun waitEvent(packageName: String?, text: String?, timeoutMs: Long): DeviceEventInfo? =
        DeviceEvents.waitFor(packageName, text, timeoutMs)

    fun triggers(): List<DeviceTrigger> = triggerManager.list()

    suspend fun addTrigger(
        name: String,
        packageFilter: String,
        textFilter: String,
        typeFilter: String,
        action: String,
        conversationId: String,
        command: String,
        cooldownSec: Int,
    ): DeviceTrigger = triggerManager.add(
        name = name,
        packageFilter = packageFilter,
        textFilter = textFilter,
        typeFilter = typeFilter,
        action = action,
        conversationId = conversationId,
        command = command,
        cooldownSec = cooldownSec,
    )

    suspend fun removeTrigger(id: String) = triggerManager.remove(id)

    suspend fun setTriggerEnabled(id: String, enabled: Boolean) = triggerManager.setEnabled(id, enabled)

    suspend fun snapshot(
        includeScreenshot: Boolean = true,
        settleMs: Long = 0,
        maxNodes: Int = 180,
        deviceId: String = "local",
        deviceName: String = "",
    ): DeviceStep {
        if (settleMs > 0) delay(settleMs.coerceIn(0, 5_000))
        val name = deviceName.ifBlank { store.deviceName() }
        val snapshot = accessibility.snapshot(deviceId, name, maxNodes)
        val signature = snapshot.text.hashCode().toString() + ":" + (DeviceAccessibilityService.lastEventAt / 250)
        val changed = signature != lastSignature
        lastSignature = signature
        lastSnapshot = snapshot
        val image = if (includeScreenshot) screenshot() else null
        return DeviceStep(
            ok = true,
            text = snapshot.text,
            screenshot = image,
            changed = changed,
        )
    }

    suspend fun action(action: String, args: JsonObject): DeviceStep {
        val settle = (args["settle_ms"]?.jsonPrimitive?.intOrNull ?: 350).coerceIn(0, 5_000).toLong()
        val index = args["index"]?.jsonPrimitive?.intOrNull
        val node = resolve(index)
        val point = when {
            index != null && node == null -> return stepError("Node index $index is not in the latest snapshot")
            else -> node?.bounds
        }
        if (action == "open_app") {
            val query = args["package"]?.jsonPrimitive?.content
                ?: args["app"]?.jsonPrimitive?.content
                ?: return stepError("app or package is required")
            val app = accessibility.resolveApp(query)
                ?: return stepError("App not found: $query")
            val activity = args["activity"]?.jsonPrimitive?.content.orEmpty()
                .ifBlank { app.activity }
            var opened = accessibility.openApp(app.packageName, activity)
            if (!opened) opened = openAppShell(app.packageName)
            if (!opened) {
                return stepError("Could not open ${app.label} (${app.packageName})")
            }
            delay(settle.coerceAtLeast(700))
            if (accessibility.currentPackage() != app.packageName) {
                if (openAppShell(app.packageName)) delay(500)
            }
            return snapshot(
                includeScreenshot = args["include_screenshot"]?.jsonPrimitive?.booleanOrNull ?: true,
                settleMs = 0,
            )
        }
        val ok = when (action) {
            "tap" -> {
                val bounds = point ?: normalizedBounds(args) ?: return stepError("index or x and y are required")
                accessibility.tap(bounds)
            }
            "long_press" -> {
                val bounds = point ?: normalizedBounds(args) ?: return stepError("index or x and y are required")
                accessibility.tap(bounds, longPress = true)
            }
            "swipe" -> {
                val direction = args["direction"]?.jsonPrimitive?.content.orEmpty()
                swipe(direction, point)
            }
            "type" -> {
                val text = args["text"]?.jsonPrimitive?.content ?: return stepError("text is required")
                val clear = args["clear"]?.jsonPrimitive?.booleanOrNull ?: true
                val submit = args["submit"]?.jsonPrimitive?.booleanOrNull ?: false
                accessibility.type(text, point, clear, submit)
            }
            "key" -> {
                val key = args["key"]?.jsonPrimitive?.content ?: return stepError("key is required")
                accessibility.key(key)
            }
            else -> false
        }
        if (!ok) return stepError("Action failed: $action")
        delay(settle)
        return snapshot(
            includeScreenshot = args["include_screenshot"]?.jsonPrimitive?.booleanOrNull ?: true,
            settleMs = 0,
        )
    }

    suspend fun shellCommand(command: String, timeoutMs: Long = 120_000): DeviceShellResult =
        shell.run(command, timeoutMs.coerceIn(1_000, 600_000))

    suspend fun apps(query: String = "", limit: Int = 60): List<DeviceApp> =
        accessibility.apps(query, limit)

    suspend fun screenshot(): String? {
        if (_automation.value is DeviceAutomationState.Idle) return capture()
        return automationGate.conceal { capture() }
    }

    private suspend fun capture(): String? {
        accessibility.screenshot()?.let { return it }
        if (!shellChecked) {
            shell.refresh()
            shellChecked = true
        }
        val status = shell.status()
        if (!status.root && !status.shizuku) return null
        val file = File(context.cacheDir, "device-shot-${UUID.randomUUID()}.png")
        val result = shellCommand("screencap -p '${file.absolutePath}' && chmod 644 '${file.absolutePath}'", 15_000)
        if (!result.ok) {
            file.delete()
            return null
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
        file.delete()
        return bytes?.let { DeviceImage.encodeBytes(it) }
    }

    suspend fun screenshotWindow(): DeviceWindowShot? = accessibility.screenshotWindow()

    suspend fun fileRead(path: String, offset: Int? = null, limit: Int? = null): String {
        if (!hasShell()) {
            return withContext(Dispatchers.IO) {
                val file = File(path)
                if (!file.isFile) return@withContext "File not found"
                val lines = file.readLines()
                val start = (offset ?: 1).coerceAtLeast(1) - 1
                val end = (start + (limit ?: lines.size)).coerceAtMost(lines.size)
                lines.subList(start.coerceAtMost(lines.size), end.coerceAtMost(lines.size)).joinToString("\n")
            }
        }
        val command = buildString {
            append("cat '").append(quote(path)).append("'")
            if (offset != null || limit != null) {
                append(" | sed -n '")
                append((offset ?: 1).coerceAtLeast(1))
                append(',')
                append(((offset ?: 1) + (limit ?: 2_000)).coerceAtLeast(1))
                append("p'")
            }
        }
        return shellCommand(command, 30_000).output
    }

    suspend fun fileWrite(path: String, content: String): DeviceShellResult {
        val temp = withContext(Dispatchers.IO) {
            File(context.cacheDir, "device-file-${UUID.randomUUID()}").apply { writeText(content) }
        }
        val result = if (hasShell()) {
            val parent = File(path).parent.orEmpty()
            val mkdir = if (parent.isBlank()) "" else "mkdir -p '${quote(parent)}' && "
            shellCommand("${mkdir}cp '${temp.absolutePath}' '${quote(path)}'", 30_000)
        } else {
            runCatching {
                File(path).apply { parentFile?.mkdirs() }.writeText(content)
                DeviceShellResult(ok = true, output = "written")
            }.getOrElse { DeviceShellResult(ok = false, error = it.message.orEmpty()) }
        }
        temp.delete()
        return result
    }

    suspend fun fileList(path: String, depth: Int = 1): String {
        return if (hasShell()) {
            shellCommand("ls -la '${quote(path)}'", 20_000).output
        } else {
            withContext(Dispatchers.IO) {
                val file = File(path)
                if (!file.exists()) return@withContext "Path not found"
                file.listFiles().orEmpty().joinToString("\n") { "${it.name}\t${if (it.isDirectory) "dir" else "file"}\t${it.length()}" }
            }
        }
    }

    suspend fun fileGlob(pattern: String, root: String? = null): String {
        val base = root?.takeIf { it.isNotBlank() } ?: "/"
        return if (hasShell()) {
            shellCommand("find '${quote(base)}' -name '${quote(pattern)}' 2>/dev/null | head -400", 60_000).output
        } else {
            File(base).walkTopDown().filter { it.name.matches(globRegex(pattern)) }.take(400).joinToString("\n") { it.absolutePath }
        }
    }

    suspend fun fileGrep(pattern: String, root: String? = null, include: String? = null): String {
        val base = root?.takeIf { it.isNotBlank() } ?: "/"
        return if (hasShell()) {
            val includeArg = include?.takeIf { it.isNotBlank() }?.let { " --include='${quote(it)}'" }.orEmpty()
            shellCommand("grep -R -n$includeArg '${quote(pattern)}' '${quote(base)}' 2>/dev/null | head -400", 60_000).output
        } else {
            "Shell is not available"
        }
    }

    suspend fun fileDelete(path: String): DeviceShellResult =
        if (hasShell()) shellCommand("rm -rf '${quote(path)}'", 30_000)
        else runCatching { File(path).deleteRecursively(); DeviceShellResult(ok = true, output = "deleted") }
            .getOrElse { DeviceShellResult(ok = false, error = it.message.orEmpty()) }

    suspend fun fileMove(from: String, to: String): DeviceShellResult =
        if (hasShell()) shellCommand("mv '${quote(from)}' '${quote(to)}'", 30_000)
        else runCatching { File(from).renameTo(File(to)); DeviceShellResult(ok = true, output = "moved") }
            .getOrElse { DeviceShellResult(ok = false, error = it.message.orEmpty()) }

    suspend fun packageList(query: String = ""): String {
        val command = if (query.isBlank()) "pm list packages" else "pm list packages | grep -i '${quote(query)}'"
        return if (hasShell()) shellCommand(command, 30_000).output else {
            context.packageManager.getInstalledApplications(0)
                .map { it.packageName }
                .filter { query.isBlank() || it.contains(query, ignoreCase = true) }
                .sorted()
                .joinToString("\n")
        }
    }

    suspend fun packageInstall(path: String): DeviceShellResult =
        shellCommand("pm install -r '${quote(path)}'", 120_000)

    suspend fun packageUninstall(packageName: String): DeviceShellResult =
        shellCommand("pm uninstall '$packageName'", 60_000)

    suspend fun packageClear(packageName: String): DeviceShellResult =
        shellCommand("pm clear '$packageName'", 60_000)

    suspend fun settingsGet(namespace: String, key: String): String =
        shellCommand("settings get '$namespace' '$key'", 20_000).output

    suspend fun settingsPut(namespace: String, key: String, value: String): DeviceShellResult =
        shellCommand("settings put '$namespace' '$key' '${quote(value)}'", 20_000)

    suspend fun settingsList(namespace: String): String =
        shellCommand("settings list '$namespace'", 20_000).output

    suspend fun appopsGet(packageName: String, op: String): String =
        shellCommand("appops get '$packageName' '$op'", 20_000).output

    suspend fun appopsSet(packageName: String, op: String, mode: String): DeviceShellResult =
        shellCommand("appops set '$packageName' '$op' '$mode'", 20_000)

    suspend fun net(action: String): DeviceShellResult = when (action) {
        "wifi_on" -> shellCommand("svc wifi enable", 20_000)
        "wifi_off" -> shellCommand("svc wifi disable", 20_000)
        "data_on" -> shellCommand("svc data enable", 20_000)
        "data_off" -> shellCommand("svc data disable", 20_000)
        "airplane_on" -> shellCommand("settings put global airplane_mode_on 1 && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true", 20_000)
        "airplane_off" -> shellCommand("settings put global airplane_mode_on 0 && am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false", 20_000)
        else -> DeviceShellResult(ok = false, error = "Unknown network action")
    }

    suspend fun processes(): String = shellCommand("ps -A", 20_000).output

    suspend fun kill(pid: Int): DeviceShellResult = shellCommand("kill -9 $pid", 20_000)

    suspend fun reboot(mode: String): DeviceShellResult {
        val target = when (mode) {
            "recovery" -> "recovery"
            "bootloader" -> "bootloader"
            else -> ""
        }
        return shellCommand(if (target.isBlank()) "reboot" else "reboot $target", 20_000)
    }

    fun clipboard(): String = accessibility.clipboard()

    fun setClipboard(text: String): Boolean = accessibility.setClipboard(text)

    fun lastSnapshotNodes(): List<DeviceNode> = lastSnapshot?.nodes.orEmpty()

    private fun resolve(index: Int?): DeviceNode? {
        if (index == null) return null
        return lastSnapshot?.nodes?.firstOrNull { it.index == index }
    }

    private fun normalizedBounds(args: JsonObject): DeviceBounds? {
        val x = args["x"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return null
        val y = args["y"]?.jsonPrimitive?.content?.toFloatOrNull() ?: return null
        val metrics = context.resources.displayMetrics
        val cx = (x.coerceIn(0f, 1f) * metrics.widthPixels).toInt()
        val cy = (y.coerceIn(0f, 1f) * metrics.heightPixels).toInt()
        return DeviceBounds(cx, cy, cx + 2, cy + 2)
    }

    private suspend fun swipe(direction: String, node: DeviceBounds?): Boolean {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val bounds = node ?: DeviceBounds(0, 0, width.toInt(), height.toInt())
        val cx = bounds.centerX.toFloat()
        val cy = bounds.centerY.toFloat()
        val dx = bounds.width * 0.35f
        val dy = bounds.height * 0.35f
        val (sx, sy, ex, ey) = when (direction.lowercase()) {
            "up" -> listOf(cx, cy + dy, cx, cy - dy)
            "down" -> listOf(cx, cy - dy, cx, cy + dy)
            "left" -> listOf(cx + dx, cy, cx - dx, cy)
            "right" -> listOf(cx - dx, cy, cx + dx, cy)
            else -> return false
        }
        return accessibility.swipe(sx, sy, ex, ey, 300)
    }

    private suspend fun openAppShell(packageName: String): Boolean {
        if (!shellChecked) {
            shell.refresh()
            shellChecked = true
        }
        if (!hasShell()) return false
        val resolved = shellCommand(
            "cmd package resolve-activity --brief '${quote(packageName)}' 2>/dev/null",
            15_000,
        ).output.lineSequence().lastOrNull { it.contains('/') }?.trim()
        if (!resolved.isNullOrBlank()) {
            val started = shellCommand("am start -n '${quote(resolved)}'", 20_000)
            if (started.ok) return true
        }
        val monkey = shellCommand(
            "monkey -p '${quote(packageName)}' -c android.intent.category.LAUNCHER 1",
            20_000,
        )
        return monkey.ok && !monkey.output.contains("No activities found", ignoreCase = true)
    }

    private fun stepError(message: String): DeviceStep = DeviceStep(ok = false, text = message, error = message)

    private fun hasShell(): Boolean = shell.status().mode != DeviceShellMode.None

    private fun quote(value: String): String = value.replace("'", "'\\''")

    private fun globRegex(pattern: String): Regex {
        val escaped = Regex.escape(pattern).replace("\\*", ".*").replace("\\?", ".")
        return Regex("^$escaped$")
    }
}
