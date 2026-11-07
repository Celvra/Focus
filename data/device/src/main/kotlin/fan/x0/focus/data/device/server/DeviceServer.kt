package fan.x0.focus.data.device.server

import fan.x0.focus.data.device.DeviceController
import fan.x0.focus.data.device.DeviceHealth
import fan.x0.focus.data.device.DevicePairRequest
import fan.x0.focus.data.device.DevicePairResult
import fan.x0.focus.data.device.DeviceRpcRequest
import fan.x0.focus.data.device.DeviceRpcResponse
import fan.x0.focus.data.device.DeviceRuntime
import fan.x0.focus.data.device.DeviceStore
import fan.x0.focus.data.device.DeviceStep
import fan.x0.focus.data.device.display.VirtualDisplaySpec
import fan.x0.focus.data.device.describe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.koin.core.annotation.Single
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Single
class DeviceServer(
    private val store: DeviceStore,
    private val runtime: DeviceRuntime,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    @Volatile
    private var engine: EmbeddedServer<*, *>? = null
    private val lifecycle = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()
    @Volatile
    private var codeExpiresAt = 0L
    private val attempts = ConcurrentHashMap<String, Attempt>()

    suspend fun start(port: Int = DEFAULT_PORT) = lifecycle.withLock {
        if (_running.value) return@withLock
        withContext(Dispatchers.IO) {
            val server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                install(WebSockets) {
                    maxFrameSize = 2L * 1024 * 1024
                }
                routing {
                    get("/health") { call.respondText(healthJson(), ContentType.Application.Json) }
                    post("/pair") { pair(call) }
                    post("/rpc") { rpc(call) }
                    webSocket("/stream") {
                        val token = call.request.queryParameters["token"].orEmpty()
                        if (store.verifyController(token) == null) {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                            return@webSocket
                        }
                        val fps = (call.request.queryParameters["fps"]?.toIntOrNull() ?: 5).coerceIn(1, 10)
                        while (runtime.session.value != null) {
                            val image = runCatching { runtime.screenshot(maxWidth = 720) }.getOrNull()
                            if (image != null) {
                                val bytes = runCatching {
                                    android.util.Base64.decode(image, android.util.Base64.DEFAULT)
                                }.getOrNull()
                                if (bytes != null) send(Frame.Binary(fin = true, data = bytes))
                            }
                            delay(1_000L / fps)
                        }
                        close()
                    }
                }
            }
            engine = server.start(wait = false)
            _running.value = true
        }
    }

    suspend fun stop() = lifecycle.withLock {
        runtime.stopSessionWithDisplay()
        withContext(Dispatchers.IO) {
            engine?.stop(1_000, 2_000)
            engine = null
            _running.value = false
            _pairingCode.value = null
        }
    }

    fun newPairingCode(): String {
        val code = (SecureRandom().nextInt(900_000) + 100_000).toString()
        _pairingCode.value = code
        codeExpiresAt = System.currentTimeMillis() + 5 * 60_000
        return code
    }

    fun localAddress(): String {
        return runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull().orEmpty()
    }

    private suspend fun pair(call: ApplicationCall) {
        val request = runCatching {
            json.decodeFromString(DevicePairRequest.serializer(), call.receiveText())
        }.getOrNull()
        if (request == null) {
            respond(call, HttpStatusCode.BadRequest, DeviceRpcResponse(false, "Invalid request"))
            return
        }
        val remote = call.request.local.remoteHost
        if (!allowAttempt(remote)) {
            respond(call, HttpStatusCode.TooManyRequests, DeviceRpcResponse(false, "Too many attempts"))
            return
        }
        val code = _pairingCode.value
        if (code == null || System.currentTimeMillis() > codeExpiresAt) {
            respond(call, HttpStatusCode.BadRequest, DeviceRpcResponse(false, "Pairing code expired"))
            return
        }
        if (request.code.trim() != code) {
            respond(call, HttpStatusCode.BadRequest, DeviceRpcResponse(false, "Wrong pairing code"))
            return
        }
        val token = newToken()
        val controller = store.addController(request.name, token)
        _pairingCode.value = null
        val health = runtime.health()
        val result = DevicePairResult(
            id = controller.id,
            token = token,
            name = health.name,
            model = health.model,
            version = health.version,
            android = health.android,
        )
        respond(call, HttpStatusCode.OK, result)
    }

    private suspend fun rpc(call: ApplicationCall) {
        val token = call.request.headers[HttpHeaders.Authorization]
            ?.removePrefix("Bearer ")
            ?.trim()
            .orEmpty()
        val controller = store.verifyController(token)
        if (controller == null) {
            respond(call, HttpStatusCode.Unauthorized, DeviceRpcResponse(false, "Unauthorized"))
            return
        }
        val request = runCatching {
            json.decodeFromString(DeviceRpcRequest.serializer(), call.receiveText())
        }.getOrNull()
        if (request == null) {
            respond(call, HttpStatusCode.BadRequest, DeviceRpcResponse(false, "Invalid request"))
            return
        }
        val response = runCatching {
            handle(controller, request)
        }.getOrElse {
            DeviceRpcResponse(false, it.message ?: it.javaClass.simpleName)
        }
        respond(call, HttpStatusCode.OK, response)
    }

    private suspend fun handle(controller: DeviceController, request: DeviceRpcRequest): DeviceRpcResponse {
        val args = request.args
        if (request.op !in sessionExempt && runtime.session.value == null) {
            return DeviceRpcResponse(false, "Start a session first")
        }
        return when (request.op) {
            "start" -> {
                if (!store.remoteEnabledOnce()) return DeviceRpcResponse(false, "Remote control is disabled")
                val health = runtime.health()
                if (!health.accessibility && !health.root && !health.shizuku) {
                    return DeviceRpcResponse(false, "Enable accessibility or root on the controlled phone first")
                }
                val session = runtime.startSession(controller.name, remote = true)
                DeviceRpcResponse(true, data = buildJsonObject {
                    put("session", session.id)
                    put("owner", session.owner)
                })
            }
            "stop" -> {
                runtime.stopSessionWithDisplay()
                DeviceRpcResponse(true)
            }
            "snapshot" -> {
                val step = runtime.snapshot(
                    includeScreenshot = args.flag("include_screenshot") ?: true,
                    settleMs = (args.int("settle_ms") ?: 0).toLong(),
                    maxNodes = (args.int("max_nodes") ?: 180).coerceIn(20, 400),
                    deviceId = store.deviceId(),
                    deviceName = store.deviceName(),
                    displayId = args.int("display_id") ?: -1,
                )
                DeviceRpcResponse(true, data = stepJson(step))
            }
            "action" -> {
                val action = args.text("action") ?: return DeviceRpcResponse(false, "action is required")
                DeviceRpcResponse(
                    true,
                    data = stepJson(runtime.action(action, args, args.int("display_id") ?: -1)),
                )
            }
            "display_start" -> {
                val state = runtime.startVirtualDisplay(
                    VirtualDisplaySpec(
                        width = (args.int("width") ?: 1080).coerceIn(320, 2560),
                        height = (args.int("height") ?: 1920).coerceIn(320, 2560),
                        density = (args.int("dpi") ?: 320).coerceIn(120, 640),
                        backend = args.text("backend") ?: "auto",
                    ),
                )
                DeviceRpcResponse(state.running, state.error, data = buildJsonObject {
                    put("display_id", state.displayId)
                    put("width", state.width)
                    put("height", state.height)
                    put("density", state.density)
                    put("backend", state.backend.name.lowercase())
                    put("trusted", state.trusted)
                })
            }
            "display_stop" -> {
                runtime.stopVirtualDisplay()
                DeviceRpcResponse(true)
            }
            "display_status" -> {
                val state = runtime.displayState.value
                DeviceRpcResponse(true, data = buildJsonObject {
                    put("running", state.running)
                    put("display_id", state.displayId)
                    put("width", state.width)
                    put("height", state.height)
                    put("density", state.density)
                    put("backend", state.backend.name.lowercase())
                    put("trusted", state.trusted)
                    put("error", state.error)
                })
            }
            "apps" -> {
                val list = runtime.apps(args.text("query").orEmpty(), args.int("limit") ?: 60)
                DeviceRpcResponse(true, data = buildJsonObject {
                    put("output", list.joinToString("\n") { "${it.label}\t${it.packageName}" })
                })
            }
            "shell" -> {
                val command = args.text("command") ?: return DeviceRpcResponse(false, "command is required")
                val timeout = (args.int("timeout_ms") ?: 120_000).toLong()
                val result = runtime.shellCommand(command, timeout)
                DeviceRpcResponse(result.ok, result.error, buildJsonObject {
                    put("output", result.output)
                    put("exit_code", result.exitCode)
                    put("root", result.root)
                })
            }
            "screencap" -> {
                val image = runtime.screenshot(args.int("display_id") ?: -1)
                DeviceRpcResponse(image != null, if (image == null) "Screenshot failed" else "", buildJsonObject {
                    image?.let { put("screenshot", it) }
                })
            }
            "shell_open" -> {
                val session = runtime.openShell()
                if (session == null) {
                    DeviceRpcResponse(false, "No root or Shizuku shell is available")
                } else {
                    DeviceRpcResponse(true, data = buildJsonObject {
                        put("output", session.info().describe())
                    })
                }
            }
            "shell_send" -> {
                val id = args.text("id") ?: return DeviceRpcResponse(false, "id is required")
                val input = args.text("input") ?: return DeviceRpcResponse(false, "input is required")
                val ok = runtime.sendShell(id, input)
                DeviceRpcResponse(ok, if (ok) "" else "Session not found or closed", buildJsonObject {
                    if (ok) put("output", "sent")
                })
            }
            "shell_read" -> {
                val id = args.text("id") ?: return DeviceRpcResponse(false, "id is required")
                val output = runtime.readShell(id, args.int("max_chars") ?: 20_000)
                if (output == null) {
                    DeviceRpcResponse(false, "Session not found")
                } else {
                    DeviceRpcResponse(true, data = buildJsonObject {
                        put("output", output.ifBlank { "(no output)" })
                    })
                }
            }
            "shell_close" -> {
                val id = args.text("id") ?: return DeviceRpcResponse(false, "id is required")
                val ok = runtime.closeShell(id)
                DeviceRpcResponse(ok, if (ok) "" else "Session not found", buildJsonObject {
                    if (ok) put("output", "closed")
                })
            }
            "shell_list" -> {
                val sessions = runtime.shellSessions()
                DeviceRpcResponse(true, data = buildJsonObject {
                    put("output", sessions.joinToString("\n") { it.describe() }.ifBlank { "(no sessions)" })
                })
            }
            "bg_start" -> {
                val command = args.text("command") ?: return DeviceRpcResponse(false, "command is required")
                val timeout = (args.int("timeout_ms") ?: 600_000).toLong()
                val job = runtime.backgroundStart(command, timeout)
                if (job == null) {
                    DeviceRpcResponse(false, "No root or Shizuku shell is available")
                } else {
                    DeviceRpcResponse(true, data = buildJsonObject {
                        put("id", job.id)
                        put("output", job.describe())
                    })
                }
            }
            "bg_logs" -> {
                val id = args.text("id") ?: return DeviceRpcResponse(false, "id is required")
                val info = runtime.backgroundInfo(id)
                if (info == null) {
                    DeviceRpcResponse(false, "Background job not found")
                } else {
                    val output = runtime.backgroundLogs(id).orEmpty()
                    DeviceRpcResponse(true, data = buildJsonObject {
                        put("id", info.id)
                        put("command", info.command)
                        put("running", info.running)
                        put("exit_code", info.exitCode)
                        put("timed_out", info.timedOut)
                        put("output", buildString {
                            append(info.describe())
                            if (output.isNotBlank()) append('\n').append(output)
                        })
                    })
                }
            }
            "bg_kill" -> {
                val id = args.text("id") ?: return DeviceRpcResponse(false, "id is required")
                val ok = runtime.backgroundKill(id)
                DeviceRpcResponse(ok, if (ok) "" else "Background job not found", buildJsonObject {
                    if (ok) put("output", "stopped")
                })
            }
            "bg_list" -> {
                val jobs = runtime.backgroundList()
                DeviceRpcResponse(true, data = buildJsonObject {
                    put("output", jobs.joinToString("\n") { it.describe() }.ifBlank { "(no jobs)" })
                })
            }
            "bg_clear" -> {
                runtime.backgroundClear()
                DeviceRpcResponse(true, data = buildJsonObject { put("output", "cleared") })
            }
            "events" -> {
                val events = runtime.events(args.int("limit") ?: 50)
                DeviceRpcResponse(true, data = buildJsonObject {
                    put("output", events.joinToString("\n") { it.describe() }.ifBlank { "(no events)" })
                })
            }
            "wait_event" -> {
                val event = runtime.waitEvent(
                    packageName = args.text("package"),
                    text = args.text("text"),
                    timeoutMs = (args.int("timeout_ms") ?: 30_000).toLong(),
                )
                DeviceRpcResponse(true, data = buildJsonObject {
                    put("output", event?.describe() ?: "timeout")
                })
            }
            "trigger_add" -> {
                val name = args.text("name") ?: return DeviceRpcResponse(false, "name is required")
                val action = args.text("action") ?: "shell"
                if (action !in setOf("shell", "notify")) {
                    return DeviceRpcResponse(false, "Chat triggers run on the controller")
                }
                val command = args.text("command").orEmpty()
                if (action == "shell" && command.isBlank()) {
                    return DeviceRpcResponse(false, "command is required")
                }
                val trigger = runtime.addTrigger(
                    name = name,
                    packageFilter = args.text("package").orEmpty(),
                    textFilter = args.text("text").orEmpty(),
                    typeFilter = args.text("type").orEmpty(),
                    action = action,
                    conversationId = "",
                    command = command,
                    cooldownSec = args.int("cooldown_sec") ?: 30,
                )
                DeviceRpcResponse(true, data = buildJsonObject { put("output", trigger.describe()) })
            }
            "trigger_list" -> {
                val triggers = runtime.triggers()
                DeviceRpcResponse(true, data = buildJsonObject {
                    put("output", triggers.joinToString("\n") { it.describe() }.ifBlank { "(no triggers)" })
                })
            }
            "trigger_remove" -> {
                val id = args.text("id") ?: return DeviceRpcResponse(false, "id is required")
                runtime.removeTrigger(id)
                DeviceRpcResponse(true, data = buildJsonObject { put("output", "removed") })
            }
            "trigger_set_enabled" -> {
                val id = args.text("id") ?: return DeviceRpcResponse(false, "id is required")
                val enabled = args.flag("enabled") ?: return DeviceRpcResponse(false, "enabled is required")
                runtime.setTriggerEnabled(id, enabled)
                DeviceRpcResponse(true, data = buildJsonObject { put("output", "updated") })
            }
            "file_read" -> {
                val path = args.text("path") ?: return DeviceRpcResponse(false, "path is required")
                val text = runtime.fileRead(path, args.int("offset"), args.int("limit") ?: 2000)
                DeviceRpcResponse(true, data = buildJsonObject { put("content", text.take(50_000)) })
            }
            "file_write" -> {
                val path = args.text("path") ?: return DeviceRpcResponse(false, "path is required")
                val content = args.text("content") ?: return DeviceRpcResponse(false, "content is required")
                val result = runtime.fileWrite(path, content)
                DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
            }
            "file_list" -> {
                val path = args.text("path") ?: return DeviceRpcResponse(false, "path is required")
                val result = runtime.fileList(path, args.int("depth") ?: 1)
                DeviceRpcResponse(true, data = buildJsonObject { put("output", result) })
            }
            "file_glob" -> {
                val pattern = args.text("pattern") ?: return DeviceRpcResponse(false, "pattern is required")
                val result = runtime.fileGlob(pattern, args.text("root"))
                DeviceRpcResponse(true, data = buildJsonObject { put("output", result) })
            }
            "file_grep" -> {
                val pattern = args.text("pattern") ?: return DeviceRpcResponse(false, "pattern is required")
                val result = runtime.fileGrep(pattern, args.text("root"), args.text("include"))
                DeviceRpcResponse(true, data = buildJsonObject { put("output", result) })
            }
            "file_delete" -> {
                val path = args.text("path") ?: return DeviceRpcResponse(false, "path is required")
                val result = runtime.fileDelete(path)
                DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
            }
            "file_move" -> {
                val from = args.text("from") ?: return DeviceRpcResponse(false, "from is required")
                val to = args.text("to") ?: return DeviceRpcResponse(false, "to is required")
                val result = runtime.fileMove(from, to)
                DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
            }
            "package_list" -> {
                val result = runtime.packageList(args.text("query").orEmpty())
                DeviceRpcResponse(true, data = buildJsonObject { put("output", result) })
            }
            "package_install" -> {
                val path = args.text("path") ?: return DeviceRpcResponse(false, "path is required")
                val result = runtime.packageInstall(path)
                DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
            }
            "package_uninstall" -> {
                val packageName = args.text("package") ?: return DeviceRpcResponse(false, "package is required")
                val result = runtime.packageUninstall(packageName)
                DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
            }
            "package_clear" -> {
                val packageName = args.text("package") ?: return DeviceRpcResponse(false, "package is required")
                val result = runtime.packageClear(packageName)
                DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
            }
            "settings_get" -> {
                val namespace = args.text("namespace") ?: "system"
                val key = args.text("key") ?: return DeviceRpcResponse(false, "key is required")
                val result = runtime.settingsGet(namespace, key)
                DeviceRpcResponse(true, data = buildJsonObject { put("output", result) })
            }
            "settings_put" -> {
                val namespace = args.text("namespace") ?: "system"
                val key = args.text("key") ?: return DeviceRpcResponse(false, "key is required")
                val value = args.text("value") ?: return DeviceRpcResponse(false, "value is required")
                val result = runtime.settingsPut(namespace, key, value)
                DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
            }
            "settings_list" -> {
                val namespace = args.text("namespace") ?: "system"
                val result = runtime.settingsList(namespace)
                DeviceRpcResponse(true, data = buildJsonObject { put("output", result) })
            }
            "appops_get" -> {
                val packageName = args.text("package") ?: return DeviceRpcResponse(false, "package is required")
                val op = args.text("op") ?: return DeviceRpcResponse(false, "op is required")
                val result = runtime.appopsGet(packageName, op)
                DeviceRpcResponse(true, data = buildJsonObject { put("output", result) })
            }
            "appops_set" -> {
                val packageName = args.text("package") ?: return DeviceRpcResponse(false, "package is required")
                val op = args.text("op") ?: return DeviceRpcResponse(false, "op is required")
                val mode = args.text("mode") ?: return DeviceRpcResponse(false, "mode is required")
                val result = runtime.appopsSet(packageName, op, mode)
                DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
            }
            "clipboard_get" -> DeviceRpcResponse(true, data = buildJsonObject {
                put("output", runtime.clipboard())
            })
            "clipboard_set" -> {
                val text = args.text("text").orEmpty()
                DeviceRpcResponse(runtime.setClipboard(text))
            }
            "net" -> {
                val action = args.text("action") ?: return DeviceRpcResponse(false, "action is required")
                val result = runtime.net(action)
                DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
            }
            "proc" -> {
                if (args.text("action") == "kill") {
                    val pid = args.int("pid") ?: return DeviceRpcResponse(false, "pid is required")
                    val result = runtime.kill(pid)
                    DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
                } else {
                    DeviceRpcResponse(true, data = buildJsonObject { put("output", runtime.processes()) })
                }
            }
            "reboot" -> {
                val result = runtime.reboot(args.text("mode").orEmpty())
                DeviceRpcResponse(result.ok, result.error, buildJsonObject { put("output", result.output) })
            }
            "capabilities" -> DeviceRpcResponse(true, data = capabilities())
            else -> DeviceRpcResponse(false, "Unknown operation ${request.op}")
        }
    }

    private suspend fun capabilities(): JsonObject {
        val health = runtime.health()
        return buildJsonObject {
            put("root", health.root)
            put("shizuku", health.shizuku)
            put("accessibility", health.accessibility)
            put("shell", health.root || health.shizuku)
            put("virtual_display", health.root || health.shizuku)
            put("display_active", health.displayRunning)
            put("display_id", health.displayId)
            put("display_backend", health.displayBackend)
            put("display_trusted", health.displayTrusted)
        putJsonArray("ops") {
            listOf(
                "snapshot", "action", "apps", "shell", "screencap",
                "shell_open", "shell_send", "shell_read", "shell_close", "shell_list",
                "bg_start", "bg_logs", "bg_kill", "bg_list", "bg_clear",
                "events", "wait_event",
                "trigger_add", "trigger_list", "trigger_remove", "trigger_set_enabled",
                "file_read", "file_write", "file_list", "file_glob", "file_grep", "file_delete", "file_move",
                "package_list", "package_install", "package_uninstall", "package_clear",
                "settings_get", "settings_put", "settings_list", "appops_get", "appops_set",
                "clipboard_get", "clipboard_set", "net", "proc", "reboot",
                "display_start", "display_stop", "display_status",
            ).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            }
        }
    }

    private suspend fun healthJson(): String {
        val health = runtime.health()
        return json.encodeToString(DeviceHealth.serializer(), health)
    }

    private fun stepJson(step: DeviceStep): JsonObject = buildJsonObject {
        put("text", step.text)
        put("display_id", step.displayId)
        put("changed", step.changed)
        step.screenshot?.let { put("screenshot", it) }
        if (!step.ok) put("error", step.error)
    }

    private suspend fun respond(call: ApplicationCall, status: HttpStatusCode, value: DeviceRpcResponse) {
        val text = json.encodeToString(DeviceRpcResponse.serializer(), value)
        call.respondText(text, ContentType.Application.Json, status)
    }

    private suspend fun respond(call: ApplicationCall, status: HttpStatusCode, value: DevicePairResult) {
        val text = json.encodeToString(DevicePairResult.serializer(), value)
        call.respondText(text, ContentType.Application.Json, status)
    }

    private fun allowAttempt(remote: String): Boolean {
        val now = System.currentTimeMillis()
        val entry = attempts[remote] ?: Attempt(now, 0)
        if (now - entry.at > 60_000) {
            attempts[remote] = Attempt(now, 1)
            return true
        }
        if (entry.count >= 8) return false
        attempts[remote] = entry.copy(count = entry.count + 1)
        return true
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private data class Attempt(val at: Long, val count: Int)

    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.flag(key: String): Boolean? =
        this[key]?.jsonPrimitive?.booleanOrNull

    private val sessionExempt = setOf("start", "stop", "capabilities")

    companion object {
        const val DEFAULT_PORT = 7790
    }
}
