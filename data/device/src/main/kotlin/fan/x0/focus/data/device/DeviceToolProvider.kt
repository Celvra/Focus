package fan.x0.focus.data.device

import android.util.Base64
import fan.x0.focus.data.assistant.AttachmentLoader
import fan.x0.focus.data.assistant.BackgroundCommandMonitor
import fan.x0.focus.data.assistant.BackgroundJobSnapshot
import fan.x0.focus.data.assistant.ExternalToolProvider
import fan.x0.focus.data.assistant.ToolApprovalDraft
import fan.x0.focus.data.assistant.ToolResult
import fan.x0.focus.data.assistant.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.annotation.Single

@Single
class DeviceToolProvider(
    private val runtime: DeviceRuntime,
    private val repository: DeviceRepository,
    private val store: DeviceStore,
    private val accessibility: DeviceAccessibility,
    private val attachments: AttachmentLoader,
    private val backgroundWatch: BackgroundCommandMonitor,
) : ExternalToolProvider {
    override fun specs(): List<ToolSpec> = DeviceToolCatalog.all

    override fun find(name: String): ToolSpec? = DeviceToolCatalog.find(name)

    override fun label(name: String?): String? = null

    override fun redactArguments(name: String, arguments: String): String? = null

    override fun approval(name: String, args: JsonObject): ToolApprovalDraft? = null

    override suspend fun execute(
        name: String,
        args: JsonObject,
        conversationId: String,
    ): ToolResult? {
        if (find(name) == null) return null
        val localTarget = args.text("device_id").isNullOrBlank() || args.text("device_id") == "local"
        if (localTarget && name !in localExempt) {
            if (!store.localEnabledOnce()) {
                return fail("Device control is off. Turn it on in the Device page first.")
            }
            if (runtime.session.value == null) {
                return fail("Call device_start first")
            }
        }
        return when (name) {
            "device_list" -> listDevices()
            "device_capabilities" -> capabilities(args)
            "device_start" -> start(args, conversationId)
            "device_stop" -> stop(args)
            "device_snapshot" -> snapshot(args)
            "device_tap" -> action("tap", args)
            "device_long_press" -> action("long_press", args)
            "device_swipe" -> action("swipe", args)
            "device_type" -> action("type", args)
            "device_key" -> action("key", args)
            "device_open_app" -> action("open_app", args)
            "device_apps" -> apps(args)
            "device_shell" -> shell(args)
            "device_shell_open" -> shellOpen(args)
            "device_shell_send" -> shellSend(args)
            "device_shell_read" -> shellRead(args)
            "device_shell_close" -> shellClose(args)
            "device_shell_list" -> shellList(args)
            "device_bg_start" -> bgStart(args, conversationId)
            "device_bg_logs" -> bgLogs(args)
            "device_bg_kill" -> bgKill(args)
            "device_bg_list" -> bgList(args)
            "device_bg_clear" -> bgClear(args)
            "device_events" -> events(args)
            "device_wait_event" -> waitEvent(args)
            "device_trigger_add" -> triggerAdd(args, conversationId)
            "device_trigger_list" -> triggerList(args)
            "device_trigger_remove" -> triggerRemove(args)
            "device_trigger_set_enabled" -> triggerSetEnabled(args)
            "device_file_read" -> fileRead(args)
            "device_file_write" -> fileWrite(args)
            "device_file_list" -> fileList(args)
            "device_file_glob" -> fileGlob(args)
            "device_file_grep" -> fileGrep(args)
            "device_file_delete" -> fileDelete(args)
            "device_file_move" -> fileMove(args)
            "device_package_list" -> packageList(args)
            "device_package_install" -> packageInstall(args)
            "device_package_uninstall" -> packageUninstall(args)
            "device_package_clear" -> packageClear(args)
            "device_settings_get" -> settingsGet(args)
            "device_settings_put" -> settingsPut(args)
            "device_settings_list" -> settingsList(args)
            "device_appops_get" -> appopsGet(args)
            "device_appops_set" -> appopsSet(args)
            "device_screencap" -> screencap(args)
            "device_clipboard_get" -> clipboardGet(args)
            "device_clipboard_set" -> clipboardSet(args)
            "device_net" -> net(args)
            "device_proc" -> proc(args)
            "device_reboot" -> reboot(args)
            else -> null
        }
    }

    private suspend fun listDevices(): ToolResult {
        val health = runtime.health()
        val endpoints = repository.endpointsOnce()
        val active = repository.activeEndpoint()
        val text = buildString {
            append("local: ").append(health.name)
            append("  accessibility=").append(health.accessibility)
            append("  root=").append(health.root)
            append("  shizuku=").append(health.shizuku)
            append("  session=").append(health.session)
            append('\n')
            if (endpoints.isEmpty()) {
                append("no paired phones")
            } else {
                endpoints.forEach { endpoint ->
                    append(endpoint.id).append(": ").append(endpoint.name)
                    append("  ").append(endpoint.host).append(':').append(endpoint.port)
                    if (endpoint.id == active?.id) append("  active")
                    append('\n')
                }
            }
        }.trimEnd()
        return ToolResult(ok = true, content = text, summary = "${endpoints.size + 1} devices")
    }

    private suspend fun capabilities(args: JsonObject): ToolResult {
        val target = target(args)
        if (target.endpoint == null) {
            val health = runtime.health()
            return ToolResult(
                ok = true,
                content = buildJsonObject {
                    put("root", health.root)
                    put("shizuku", health.shizuku)
                    put("accessibility", health.accessibility)
                    put("shell", health.root || health.shizuku)
                    put("session", health.session)
                }.toString(),
                summary = "Device capabilities",
            )
        }
        val response = repository.rpc(target.endpoint, "capabilities")
        return if (response.ok) {
            ToolResult(ok = true, content = response.data.toString(), summary = "Device capabilities")
        } else {
            fail(response.error)
        }
    }

    private suspend fun start(args: JsonObject, conversationId: String): ToolResult {
        val target = target(args)
        val owner = args.text("owner") ?: "Focus"
        if (target.endpoint == null) {
            if (!store.localEnabledOnce()) return fail("Device control is off. Turn it on in the Device page first.")
            if (!accessibility.ready() && !runtime.health().root && !runtime.health().shizuku) {
                return fail("Enable the Focus accessibility service or root access first.")
            }
            val session = runtime.startSession(owner, conversationId = conversationId)
            return ToolResult(
                ok = true,
                content = "session ${session.id} on local (${session.owner})",
                summary = "Device control started",
            )
        }
        val response = repository.rpc(target.endpoint, "start", buildJsonObject { put("owner", owner) })
        return if (response.ok) {
            ToolResult(ok = true, content = response.data.toString(), summary = "Remote control started")
        } else {
            fail(response.error)
        }
    }

    private suspend fun stop(args: JsonObject): ToolResult {
        val target = target(args)
        if (target.endpoint == null) {
            runtime.stopSession()
            return ToolResult(ok = true, content = "Device control stopped", summary = "Stopped")
        }
        val response = repository.rpc(target.endpoint, "stop")
        return if (response.ok) {
            ToolResult(ok = true, content = "Remote control stopped", summary = "Stopped")
        } else {
            fail(response.error)
        }
    }

    private suspend fun snapshot(args: JsonObject): ToolResult {
        val target = target(args)
        if (target.endpoint == null) {
            requireSession()
            val step = runtime.snapshot(
                includeScreenshot = args.flag("include_screenshot") ?: true,
                settleMs = (args.int("settle_ms") ?: 0).toLong(),
                maxNodes = (args.int("max_nodes") ?: 180).coerceIn(20, 400),
                deviceName = store.deviceName(),
            )
            return stepResult(step)
        }
        val response = repository.rpc(target.endpoint, "snapshot", args)
        return remoteStep(response)
    }

    private suspend fun action(action: String, args: JsonObject): ToolResult {
        val target = target(args)
        val payload = JsonObject(args + ("action" to JsonPrimitive(action)))
        if (target.endpoint == null) {
            requireSession()
            return stepResult(runtime.action(action, payload))
        }
        val response = repository.rpc(target.endpoint, "action", payload)
        return remoteStep(response)
    }

    private suspend fun apps(args: JsonObject): ToolResult {
        val target = target(args)
        if (target.endpoint == null) {
            requireSession()
            val list = runtime.apps(args.text("query").orEmpty(), args.int("limit") ?: 60)
            return ToolResult(
                ok = true,
                content = list.joinToString("\n") { "${it.label}\t${it.packageName}" }.ifBlank { "No apps" },
                summary = "${list.size} apps",
            )
        }
        val response = repository.rpc(target.endpoint, "apps", args)
        return remoteData(response, "output")
    }

    private suspend fun shell(args: JsonObject): ToolResult {
        val target = target(args)
        val command = args.text("command") ?: return fail("command is required")
        val timeout = (args.int("timeout_ms") ?: 120_000).toLong()
        if (target.endpoint == null) {
            val result = runtime.shellCommand(command, timeout)
            return shellResult(result)
        }
        val response = repository.rpc(target.endpoint, "shell", args)
        return if (response.ok) {
            val output = response.data.text("output").orEmpty().take(20_000)
            ToolResult(ok = true, content = output.ifBlank { "(no output)" }, summary = "Remote shell")
        } else {
            fail(response.error)
        }
    }

    private suspend fun shellOpen(args: JsonObject): ToolResult =
        remoteOrLocal(args, "shell_open") {
            val session = runtime.openShell() ?: return@remoteOrLocal fail("No root or Shizuku shell is available")
            ToolResult(ok = true, content = session.info().describe(), summary = "Shell session opened")
        }

    private suspend fun shellSend(args: JsonObject): ToolResult =
        remoteOrLocal(args, "shell_send") {
            val id = args.text("id") ?: return@remoteOrLocal fail("id is required")
            val input = args.text("input") ?: return@remoteOrLocal fail("input is required")
            if (runtime.sendShell(id, input)) {
                ToolResult(ok = true, content = "sent", summary = "Shell input sent")
            } else {
                fail("Shell session not found or closed")
            }
        }

    private suspend fun shellRead(args: JsonObject): ToolResult =
        remoteOrLocal(args, "shell_read") {
            val id = args.text("id") ?: return@remoteOrLocal fail("id is required")
            val output = runtime.readShell(id, args.int("max_chars") ?: 20_000)
                ?: return@remoteOrLocal fail("Shell session not found")
            ToolResult(ok = true, content = output.ifBlank { "(no output)" }, summary = "Shell output")
        }

    private suspend fun shellClose(args: JsonObject): ToolResult =
        remoteOrLocal(args, "shell_close") {
            val id = args.text("id") ?: return@remoteOrLocal fail("id is required")
            if (runtime.closeShell(id)) {
                ToolResult(ok = true, content = "closed", summary = "Shell closed")
            } else {
                fail("Shell session not found")
            }
        }

    private suspend fun shellList(args: JsonObject): ToolResult =
        remoteOrLocal(args, "shell_list") {
            val sessions = runtime.shellSessions()
            ToolResult(
                ok = true,
                content = sessions.joinToString("\n") { it.describe() }.ifBlank { "(no sessions)" },
                summary = "${sessions.size} shell sessions",
            )
        }

    private suspend fun bgStart(args: JsonObject, conversationId: String): ToolResult {
        val target = target(args)
        val command = args.text("command") ?: return fail("command is required")
        val timeout = (args.int("timeout_ms") ?: 600_000).toLong()
        if (target.endpoint == null) {
            val job = runtime.backgroundStart(command, timeout)
                ?: return fail("No root or Shizuku shell is available")
            if (conversationId.isNotBlank()) {
                backgroundWatch.register(conversationId, "device:${job.id}") {
                    val info = runtime.backgroundInfo(job.id)
                    if (info == null) {
                        BackgroundJobSnapshot(done = true, summary = "Background job disappeared")
                    } else {
                        BackgroundJobSnapshot(
                            done = !info.running,
                            summary = backgroundSummary(info.command, "this phone", info.exitCode, info.timedOut),
                            logs = runtime.backgroundLogs(job.id).orEmpty(),
                        )
                    }
                }
            }
            return ToolResult(ok = true, content = job.describe(), summary = "Background job started")
        }
        val response = repository.rpc(target.endpoint, "bg_start", args)
        if (!response.ok) return fail(response.error)
        val id = response.data.text("id") ?: return fail("No background job id")
        if (conversationId.isNotBlank()) {
            backgroundWatch.register(conversationId, "device:$id") {
                val poll = repository.rpc(
                    target.endpoint,
                    "bg_logs",
                    buildJsonObject { put("id", id) },
                )
                if (!poll.ok) {
                    BackgroundJobSnapshot(done = true, summary = "Background job failed: ${poll.error}")
                } else {
                    val running = poll.data["running"]?.jsonPrimitive?.booleanOrNull ?: false
                    val exit = poll.data["exit_code"]?.jsonPrimitive?.intOrNull ?: -1
                    val timed = poll.data["timed_out"]?.jsonPrimitive?.booleanOrNull ?: false
                    val jobCommand = poll.data.text("command") ?: command
                    BackgroundJobSnapshot(
                        done = !running,
                        summary = backgroundSummary(jobCommand, target.endpoint.name, exit, timed),
                        logs = poll.data.text("output").orEmpty(),
                    )
                }
            }
        }
        return ToolResult(
            ok = true,
            content = response.data.text("output").orEmpty().ifBlank { "started $id" },
            summary = "Background job started",
        )
    }

    private fun backgroundSummary(command: String, where: String, exitCode: Int, timedOut: Boolean): String {
        val timeout = if (timedOut) ", timed out" else ""
        return "Background command finished on $where: $command. Exit code $exitCode$timeout."
    }

    private suspend fun bgLogs(args: JsonObject): ToolResult =
        remoteOrLocal(args, "bg_logs") {
            val id = args.text("id") ?: return@remoteOrLocal fail("id is required")
            val info = runtime.backgroundInfo(id) ?: return@remoteOrLocal fail("Background job not found")
            val output = runtime.backgroundLogs(id).orEmpty()
            ToolResult(
                ok = true,
                content = buildString {
                    append(info.describe())
                    if (output.isNotBlank()) append('\n').append(output)
                },
                summary = if (info.running) "Running" else "exit ${info.exitCode}",
            )
        }

    private suspend fun bgKill(args: JsonObject): ToolResult =
        remoteOrLocal(args, "bg_kill") {
            val id = args.text("id") ?: return@remoteOrLocal fail("id is required")
            if (runtime.backgroundKill(id)) {
                ToolResult(ok = true, content = "stopped", summary = "Background job stopped")
            } else {
                fail("Background job not found")
            }
        }

    private suspend fun bgList(args: JsonObject): ToolResult =
        remoteOrLocal(args, "bg_list") {
            val jobs = runtime.backgroundList()
            ToolResult(
                ok = true,
                content = jobs.joinToString("\n") { it.describe() }.ifBlank { "(no jobs)" },
                summary = "${jobs.size} background jobs",
            )
        }

    private suspend fun bgClear(args: JsonObject): ToolResult =
        remoteOrLocal(args, "bg_clear") {
            runtime.backgroundClear()
            ToolResult(ok = true, content = "cleared", summary = "Finished jobs cleared")
        }

    private suspend fun events(args: JsonObject): ToolResult =
        remoteOrLocal(args, "events") {
            val list = runtime.events(args.int("limit") ?: 50)
            ToolResult(
                ok = true,
                content = list.joinToString("\n") { it.describe() }.ifBlank { "(no events)" },
                summary = "${list.size} events",
            )
        }

    private suspend fun waitEvent(args: JsonObject): ToolResult =
        remoteOrLocal(args, "wait_event") {
            val event = runtime.waitEvent(
                packageName = args.text("package"),
                text = args.text("text"),
                timeoutMs = (args.int("timeout_ms") ?: 30_000).toLong(),
            )
            if (event == null) {
                ToolResult(ok = true, content = "timeout", summary = "No matching event")
            } else {
                ToolResult(ok = true, content = event.describe(), summary = "Event received")
            }
        }

    private suspend fun triggerAdd(args: JsonObject, conversationId: String): ToolResult {
        val action = args.text("action")?.lowercase() ?: "shell"
        if (action !in setOf("shell", "notify", "chat")) return fail("action must be shell, notify, or chat")
        val payload = JsonObject(
            args + (
                "action" to JsonPrimitive(action)
                ) + (
                "conversation_id" to JsonPrimitive(conversationId)
                ),
        )
        return remoteOrLocal(payload, "trigger_add") {
            val name = args.text("name") ?: return@remoteOrLocal fail("name is required")
            val command = args.text("command").orEmpty()
            if (action in setOf("shell", "chat") && command.isBlank()) return@remoteOrLocal fail("command is required")
            val trigger = runtime.addTrigger(
                name = name,
                packageFilter = args.text("package").orEmpty(),
                textFilter = args.text("text").orEmpty(),
                typeFilter = args.text("type").orEmpty(),
                action = action,
                conversationId = conversationId,
                command = command,
                cooldownSec = args.int("cooldown_sec") ?: 30,
            )
            ToolResult(ok = true, content = trigger.describe(), summary = "Trigger added")
        }
    }

    private suspend fun triggerList(args: JsonObject): ToolResult =
        remoteOrLocal(args, "trigger_list") {
            val triggers = runtime.triggers()
            ToolResult(
                ok = true,
                content = triggers.joinToString("\n") { it.describe() }.ifBlank { "(no triggers)" },
                summary = "${triggers.size} triggers",
            )
        }

    private suspend fun triggerRemove(args: JsonObject): ToolResult =
        remoteOrLocal(args, "trigger_remove") {
            val id = args.text("id") ?: return@remoteOrLocal fail("id is required")
            runtime.removeTrigger(id)
            ToolResult(ok = true, content = "removed", summary = "Trigger removed")
        }

    private suspend fun triggerSetEnabled(args: JsonObject): ToolResult =
        remoteOrLocal(args, "trigger_set_enabled") {
            val id = args.text("id") ?: return@remoteOrLocal fail("id is required")
            val enabled = args.flag("enabled") ?: return@remoteOrLocal fail("enabled is required")
            runtime.setTriggerEnabled(id, enabled)
            ToolResult(ok = true, content = "updated", summary = if (enabled) "Trigger enabled" else "Trigger disabled")
        }

    private suspend fun fileRead(args: JsonObject): ToolResult =
        remoteOrLocal(args, "file_read") {
            val path = args.text("path") ?: return@remoteOrLocal fail("path is required")
            val content = runtime.fileRead(path, args.int("offset"), args.int("limit") ?: 2000)
            ToolResult(ok = true, content = content.take(50_000).ifBlank { "(empty)" }, summary = path)
        }

    private suspend fun fileWrite(args: JsonObject): ToolResult =
        remoteOrLocal(args, "file_write") {
            val path = args.text("path") ?: return@remoteOrLocal fail("path is required")
            val content = args.text("content") ?: return@remoteOrLocal fail("content is required")
            shellResult(runtime.fileWrite(path, content))
        }

    private suspend fun fileList(args: JsonObject): ToolResult =
        remoteOrLocal(args, "file_list") {
            val path = args.text("path") ?: return@remoteOrLocal fail("path is required")
            ToolResult(ok = true, content = runtime.fileList(path), summary = path)
        }

    private suspend fun fileGlob(args: JsonObject): ToolResult =
        remoteOrLocal(args, "file_glob") {
            val pattern = args.text("pattern") ?: return@remoteOrLocal fail("pattern is required")
            ToolResult(ok = true, content = runtime.fileGlob(pattern, args.text("root")), summary = pattern)
        }

    private suspend fun fileGrep(args: JsonObject): ToolResult =
        remoteOrLocal(args, "file_grep") {
            val pattern = args.text("pattern") ?: return@remoteOrLocal fail("pattern is required")
            ToolResult(
                ok = true,
                content = runtime.fileGrep(pattern, args.text("root"), args.text("include")),
                summary = pattern,
            )
        }

    private suspend fun fileDelete(args: JsonObject): ToolResult =
        remoteOrLocal(args, "file_delete") {
            val path = args.text("path") ?: return@remoteOrLocal fail("path is required")
            shellResult(runtime.fileDelete(path))
        }

    private suspend fun fileMove(args: JsonObject): ToolResult =
        remoteOrLocal(args, "file_move") {
            val from = args.text("from") ?: return@remoteOrLocal fail("from is required")
            val to = args.text("to") ?: return@remoteOrLocal fail("to is required")
            shellResult(runtime.fileMove(from, to))
        }

    private suspend fun packageList(args: JsonObject): ToolResult =
        remoteOrLocal(args, "package_list") {
            val output = runtime.packageList(args.text("query").orEmpty())
            ToolResult(ok = true, content = output.ifBlank { "(empty)" }, summary = "Packages")
        }

    private suspend fun packageInstall(args: JsonObject): ToolResult =
        remoteOrLocal(args, "package_install") {
            val path = args.text("path") ?: return@remoteOrLocal fail("path is required")
            shellResult(runtime.packageInstall(path))
        }

    private suspend fun packageUninstall(args: JsonObject): ToolResult =
        remoteOrLocal(args, "package_uninstall") {
            val packageName = args.text("package") ?: return@remoteOrLocal fail("package is required")
            shellResult(runtime.packageUninstall(packageName))
        }

    private suspend fun packageClear(args: JsonObject): ToolResult =
        remoteOrLocal(args, "package_clear") {
            val packageName = args.text("package") ?: return@remoteOrLocal fail("package is required")
            shellResult(runtime.packageClear(packageName))
        }

    private suspend fun settingsGet(args: JsonObject): ToolResult =
        remoteOrLocal(args, "settings_get") {
            val key = args.text("key") ?: return@remoteOrLocal fail("key is required")
            val output = runtime.settingsGet(args.text("namespace") ?: "system", key)
            ToolResult(ok = true, content = output.ifBlank { "(empty)" }, summary = key)
        }

    private suspend fun settingsPut(args: JsonObject): ToolResult =
        remoteOrLocal(args, "settings_put") {
            val key = args.text("key") ?: return@remoteOrLocal fail("key is required")
            val value = args.text("value") ?: return@remoteOrLocal fail("value is required")
            shellResult(runtime.settingsPut(args.text("namespace") ?: "system", key, value))
        }

    private suspend fun settingsList(args: JsonObject): ToolResult =
        remoteOrLocal(args, "settings_list") {
            val output = runtime.settingsList(args.text("namespace") ?: "system")
            ToolResult(ok = true, content = output.ifBlank { "(empty)" }, summary = "Settings")
        }

    private suspend fun appopsGet(args: JsonObject): ToolResult =
        remoteOrLocal(args, "appops_get") {
            val packageName = args.text("package") ?: return@remoteOrLocal fail("package is required")
            val op = args.text("op") ?: return@remoteOrLocal fail("op is required")
            ToolResult(ok = true, content = runtime.appopsGet(packageName, op), summary = op)
        }

    private suspend fun appopsSet(args: JsonObject): ToolResult =
        remoteOrLocal(args, "appops_set") {
            val packageName = args.text("package") ?: return@remoteOrLocal fail("package is required")
            val op = args.text("op") ?: return@remoteOrLocal fail("op is required")
            val mode = args.text("mode") ?: return@remoteOrLocal fail("mode is required")
            shellResult(runtime.appopsSet(packageName, op, mode))
        }

    private suspend fun screencap(args: JsonObject): ToolResult {
        val target = target(args)
        if (target.endpoint == null) {
            val image = runtime.screenshot() ?: return fail("Screenshot failed")
            return imageResult(image, "Screen captured")
        }
        val response = repository.rpc(target.endpoint, "screencap")
        return if (response.ok) {
            val image = response.data.text("screenshot") ?: return fail("Remote screenshot failed")
            imageResult(image, "Remote screen captured")
        } else {
            fail(response.error)
        }
    }

    private suspend fun clipboardGet(args: JsonObject): ToolResult =
        remoteOrLocal(args, "clipboard_get") {
            ToolResult(ok = true, content = runtime.clipboard(), summary = "Clipboard")
        }

    private suspend fun clipboardSet(args: JsonObject): ToolResult =
        remoteOrLocal(args, "clipboard_set") {
            val text = args.text("text").orEmpty()
            if (runtime.setClipboard(text)) {
                ToolResult(ok = true, content = "Clipboard updated", summary = "Clipboard")
            } else {
                fail("Clipboard write failed")
            }
        }

    private suspend fun net(args: JsonObject): ToolResult =
        remoteOrLocal(args, "net") {
            val action = args.text("action") ?: return@remoteOrLocal fail("action is required")
            shellResult(runtime.net(action))
        }

    private suspend fun proc(args: JsonObject): ToolResult {
        val target = target(args)
        if (target.endpoint == null) {
            if (args.text("action") == "kill") {
                val pid = args.int("pid") ?: return fail("pid is required")
                return shellResult(runtime.kill(pid))
            }
            return ToolResult(ok = true, content = runtime.processes(), summary = "Processes")
        }
        val response = repository.rpc(target.endpoint, "proc", args)
        return remoteData(response, "output")
    }

    private suspend fun reboot(args: JsonObject): ToolResult =
        remoteOrLocal(args, "reboot") {
            shellResult(runtime.reboot(args.text("mode").orEmpty()))
        }

    private suspend fun remoteOrLocal(
        args: JsonObject,
        op: String,
        local: suspend () -> ToolResult,
    ): ToolResult {
        val target = target(args)
        if (target.endpoint == null) return local()
        val response = repository.rpc(target.endpoint, op, args)
        return when {
            !response.ok -> fail(response.error)
            response.data.text("content") != null -> ToolResult(
                ok = true,
                content = response.data.text("content").orEmpty(),
                summary = op,
            )
            else -> ToolResult(
                ok = true,
                content = response.data.text("output").orEmpty().ifBlank { "(no output)" },
                summary = op,
            )
        }
    }

    private suspend fun remoteStep(response: DeviceRpcResponse): ToolResult {
        if (!response.ok) return fail(response.error)
        val step = DeviceStep(
            ok = true,
            text = response.data.text("text").orEmpty(),
            screenshot = response.data.text("screenshot"),
            changed = response.data.flag("changed") ?: false,
        )
        return stepResult(step)
    }

    private fun remoteData(response: DeviceRpcResponse, key: String): ToolResult {
        if (!response.ok) return fail(response.error)
        return ToolResult(
            ok = true,
            content = response.data.text(key).orEmpty().ifBlank { "(no output)" },
            summary = key,
        )
    }

    private fun stepResult(step: DeviceStep): ToolResult {
        if (!step.ok) return fail(step.error)
        val result = ToolResult(
            ok = true,
            content = step.text,
            summary = if (step.changed) "Screen changed" else "Screen read",
        )
        return imageResult(step.screenshot, result)
    }

    private fun imageResult(image: String?, result: ToolResult): ToolResult {
        if (image.isNullOrBlank()) return result
        val bytes = runCatching { Base64.decode(image, Base64.DEFAULT) }.getOrNull() ?: return result
        val attachment = attachments.imageFromBytes(bytes, "screen.jpg") ?: return result
        return result.copy(media = listOf(attachment), previews = listOf(attachment))
    }

    private fun imageResult(image: String, summary: String): ToolResult {
        val result = ToolResult(ok = true, content = summary, summary = summary)
        return imageResult(image, result)
    }

    private fun shellResult(result: DeviceShellResult): ToolResult {
        if (!result.ok) {
            return fail(result.error.ifBlank { result.output }.ifBlank { "Shell command failed" })
        }
        return ToolResult(
            ok = true,
            content = result.output.take(20_000).ifBlank { "(no output)" },
            summary = "exit ${result.exitCode}",
        )
    }

    private fun requireSession() {
        if (runtime.session.value == null) error("Call device_start first")
    }

    private fun fail(message: String): ToolResult =
        ToolResult(ok = false, content = "Error: $message", summary = message.take(80))

    private suspend fun target(args: JsonObject): Target {
        val id = args.text("device_id")
        if (id.isNullOrBlank() || id == "local") return Target(null)
        val endpoint = repository.endpointsOnce().firstOrNull { it.id == id }
            ?: error("No paired device with id $id")
        return Target(endpoint)
    }

    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.flag(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

    private data class Target(val endpoint: DeviceEndpoint?)

    private val localExempt = setOf(
        "device_list",
        "device_capabilities",
        "device_start",
        "device_stop",
    )
}
