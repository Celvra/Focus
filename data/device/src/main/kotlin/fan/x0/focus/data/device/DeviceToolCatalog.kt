package fan.x0.focus.data.device

import fan.x0.focus.data.assistant.ToolKind
import fan.x0.focus.data.assistant.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object DeviceToolCatalog {
    val all: List<ToolSpec> = listOf(
        ToolSpec(
            name = "device_list",
            description = "List this phone and trusted remote phones. The local target is always available. Remote devices must be paired first.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "device_capabilities",
            description = "Show available device capabilities and whether root, Shizuku, or accessibility is ready.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
            },
        ),
        ToolSpec(
            name = "device_start",
            description = "Start a control session on this phone or a trusted remote phone. Call this once before using device actions.",
            kind = ToolKind.Write,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("owner", string("Short name for the control session, optional"))
            },
        ),
        ToolSpec(
            name = "device_stop",
            description = "Stop the active control session on this phone or a remote phone.",
            kind = ToolKind.Write,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
            },
        ),
        ToolSpec(
            name = "device_snapshot",
            description = "Read the current screen as indexed accessibility nodes. Screenshot is attached by default. Use the returned node index with device_tap and device_type.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("include_screenshot", boolean("Attach a screenshot, default true"))
                put("settle_ms", number("Wait before reading, default 0"))
                put("max_nodes", number("Maximum nodes, default 180"))
            },
        ),
        ToolSpec(
            name = "device_tap",
            description = "Tap one node from the latest snapshot, or tap a relative screen position. One action per response. The result contains the new screen.",
            kind = ToolKind.Write,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("index", number("Node index from the latest snapshot"))
                put("x", decimal("Relative x from 0 to 1, used when index is absent"))
                put("y", decimal("Relative y from 0 to 1, used when index is absent"))
                put("include_screenshot", boolean("Attach a screenshot, default true"))
            },
        ),
        ToolSpec(
            name = "device_long_press",
            description = "Long press one node or a relative screen position. The result contains the new screen.",
            kind = ToolKind.Write,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("index", number("Node index from the latest snapshot"))
                put("x", decimal("Relative x from 0 to 1"))
                put("y", decimal("Relative y from 0 to 1"))
                put("include_screenshot", boolean("Attach a screenshot, default true"))
            },
        ),
        ToolSpec(
            name = "device_swipe",
            description = "Swipe or scroll. Direction is the finger direction. Pass index to swipe inside one scrollable node.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("direction")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("direction", string("up, down, left, or right"))
                put("index", number("Node index to swipe inside, optional"))
                put("include_screenshot", boolean("Attach a screenshot, default true"))
            },
        ),
        ToolSpec(
            name = "device_type",
            description = "Type text into the focused field or the field at index. Prefer this over tapping keyboard keys.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("text")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("text", string("Text to type"))
                put("index", number("Editable node index, optional"))
                put("clear", boolean("Clear the field first, default true"))
                put("submit", boolean("Press enter after typing, default false"))
            },
        ),
        ToolSpec(
            name = "device_key",
            description = "Press a system key or navigation action.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("key")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("key", string("back, home, recents, notifications, quick_settings, enter, delete, escape, or wake"))
            },
        ),
        ToolSpec(
            name = "device_open_app",
            description = "Open an installed app by label or package name.",
            kind = ToolKind.Write,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("app", string("App label, for example 微信"))
                put("package", string("Package name, used instead of app when present"))
                put("activity", string("Launch activity class, optional"))
            },
        ),
        ToolSpec(
            name = "device_apps",
            description = "Search launchable apps on the target device.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("query", string("Label or package text"))
                put("limit", number("Maximum apps, default 60"))
            },
        ),
        ToolSpec(
            name = "device_shell",
            description = "Run a shell command on the target device. Uses root when available, then Shizuku. This is the full system escape hatch.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("command")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("command", string("Shell command"))
                put("timeout_ms", number("Timeout in milliseconds, default 120000"))
            },
        ),
        ToolSpec(
            name = "device_shell_open",
            description = "Open a persistent interactive shell session. Returns a session id for device_shell_send and device_shell_read.",
            kind = ToolKind.Write,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
            },
        ),
        ToolSpec(
            name = "device_shell_send",
            description = "Send text to a persistent shell session. A newline is added when missing.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id", "input")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("id", string("Session id from device_shell_open or device_shell_list"))
                put("input", string("Text to send"))
            },
        ),
        ToolSpec(
            name = "device_shell_read",
            description = "Read the recent output of a persistent shell session.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("id")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("id", string("Session id"))
                put("max_chars", number("Maximum characters, default 20000"))
            },
        ),
        ToolSpec(
            name = "device_shell_close",
            description = "Close a persistent shell session.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("id", string("Session id"))
            },
        ),
        ToolSpec(
            name = "device_shell_list",
            description = "List persistent shell sessions.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
            },
        ),
        ToolSpec(
            name = "device_bg_start",
            description = "Start a shell command in the background. Returns a job id. Use device_bg_logs to read output and device_bg_kill to stop it.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("command")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("command", string("Shell command"))
                put("timeout_ms", number("Maximum time before the process is stopped, default 600000"))
            },
        ),
        ToolSpec(
            name = "device_bg_logs",
            description = "Read the captured output of a background command.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("id")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("id", string("Background job id"))
            },
        ),
        ToolSpec(
            name = "device_bg_kill",
            description = "Stop a background command.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("id", string("Background job id"))
            },
        ),
        ToolSpec(
            name = "device_bg_list",
            description = "List background commands.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
            },
        ),
        ToolSpec(
            name = "device_bg_clear",
            description = "Remove finished background commands from the list.",
            kind = ToolKind.Write,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
            },
        ),
        ToolSpec(
            name = "device_events",
            description = "Read recent accessibility events such as window changes, clicks, and text changes.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("limit", number("Maximum events, default 50"))
            },
        ),
        ToolSpec(
            name = "device_wait_event",
            description = "Wait until a matching accessibility event arrives or the timeout expires.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("package", string("Package name filter, optional"))
                put("text", string("Text in event text or class name, optional"))
                put("timeout_ms", number("Timeout in milliseconds, default 30000"))
            },
        ),
        ToolSpec(
            name = "device_trigger_add",
            description = "Add a persistent trigger that runs a shell command when a matching accessibility event arrives.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("name", "command")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("name", string("Short trigger name"))
                put("command", string("Shell command to run"))
                put("package", string("Package name filter, optional"))
                put("text", string("Text or class name filter, optional"))
                put("type", string("Accessibility event type filter, optional"))
                put("action", string("shell, notify, or chat. Default shell"))
                put("cooldown_sec", number("Minimum seconds between runs, default 30"))
            },
        ),
        ToolSpec(
            name = "device_trigger_list",
            description = "List persistent event triggers.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
            },
        ),
        ToolSpec(
            name = "device_trigger_remove",
            description = "Remove a persistent event trigger.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("id", string("Trigger id"))
            },
        ),
        ToolSpec(
            name = "device_trigger_set_enabled",
            description = "Enable or disable a persistent event trigger.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("id", "enabled")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("id", string("Trigger id"))
                put("enabled", boolean("True to enable"))
            },
        ),
        ToolSpec(
            name = "device_file_read",
            description = "Read a text file on the target device.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("path")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("path", string("Absolute path"))
                put("offset", number("First line, 1-based, optional"))
                put("limit", number("Maximum lines, optional"))
            },
        ),
        ToolSpec(
            name = "device_file_write",
            description = "Write a whole text file on the target device.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("path", "content")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("path", string("Absolute path"))
                put("content", string("Full file content"))
            },
        ),
        ToolSpec(
            name = "device_file_list",
            description = "List a directory on the target device.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("path")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("path", string("Absolute directory path"))
            },
        ),
        ToolSpec(
            name = "device_file_glob",
            description = "Find files by name pattern on the target device.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("pattern")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("pattern", string("File name pattern, for example *.json"))
                put("root", string("Directory to search from, default /"))
            },
        ),
        ToolSpec(
            name = "device_file_grep",
            description = "Search file contents on the target device.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("pattern")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("pattern", string("Text or regular expression"))
                put("root", string("Directory to search from, default /"))
                put("include", string("Only files matching this pattern, optional"))
            },
        ),
        ToolSpec(
            name = "device_file_delete",
            description = "Delete a file or directory on the target device.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("path")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("path", string("Absolute path"))
            },
        ),
        ToolSpec(
            name = "device_file_move",
            description = "Move or rename a path on the target device.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("from", "to")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("from", string("Source path"))
                put("to", string("Destination path"))
            },
        ),
        ToolSpec(
            name = "device_package_list",
            description = "List installed package names on the target device.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("query", string("Filter text, optional"))
            },
        ),
        ToolSpec(
            name = "device_package_install",
            description = "Install an APK on the target device. The path must be readable by the target.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("path")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("path", string("Absolute APK path"))
            },
        ),
        ToolSpec(
            name = "device_package_uninstall",
            description = "Uninstall a package on the target device.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("package")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("package", string("Package name"))
            },
        ),
        ToolSpec(
            name = "device_package_clear",
            description = "Clear app data on the target device.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("package")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("package", string("Package name"))
            },
        ),
        ToolSpec(
            name = "device_settings_get",
            description = "Read one Android settings value.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("key")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("namespace", string("system, secure, or global, default system"))
                put("key", string("Settings key"))
            },
        ),
        ToolSpec(
            name = "device_settings_put",
            description = "Write one Android settings value.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("key", "value")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("namespace", string("system, secure, or global, default system"))
                put("key", string("Settings key"))
                put("value", string("New value"))
            },
        ),
        ToolSpec(
            name = "device_settings_list",
            description = "List Android settings values in one namespace.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("namespace", string("system, secure, or global, default system"))
            },
        ),
        ToolSpec(
            name = "device_appops_get",
            description = "Read an appops permission state.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("package", "op")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("package", string("Package name"))
                put("op", string("Appops operation, for example CAMERA"))
            },
        ),
        ToolSpec(
            name = "device_appops_set",
            description = "Change an appops permission state.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("package", "op", "mode")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("package", string("Package name"))
                put("op", string("Appops operation"))
                put("mode", string("allow, deny, ignore, or default"))
            },
        ),
        ToolSpec(
            name = "device_screencap",
            description = "Capture the screen using accessibility or root. Returns a base64 JPEG attachment.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
            },
        ),
        ToolSpec(
            name = "device_clipboard_get",
            description = "Read the clipboard.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
            },
        ),
        ToolSpec(
            name = "device_clipboard_set",
            description = "Write the clipboard.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("text")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("text", string("Clipboard text"))
            },
        ),
        ToolSpec(
            name = "device_net",
            description = "Toggle Wi-Fi, mobile data, or airplane mode.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("action")) {
                put("device_id", string("local or a remote device id from device_list"))
                put("action", string("wifi_on, wifi_off, data_on, data_off, airplane_on, or airplane_off"))
            },
        ),
        ToolSpec(
            name = "device_proc",
            description = "List processes or kill one process.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("action", string("list or kill, default list"))
                put("pid", number("Process id for kill"))
            },
        ),
        ToolSpec(
            name = "device_reboot",
            description = "Reboot the target device.",
            kind = ToolKind.Write,
            parameters = schema {
                put("device_id", string("local or a remote device id from device_list"))
                put("mode", string("system, recovery, or bootloader, default system"))
            },
        ),
    )

    private val byName = all.associateBy { it.name }

    fun find(name: String): ToolSpec? = byName[name]

    private fun schema(required: List<String> = emptyList(), props: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties", props)
            if (required.isNotEmpty()) {
                putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
            }
            put("additionalProperties", false)
        }

    private fun string(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun number(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    private fun decimal(description: String): JsonObject = buildJsonObject {
        put("type", "number")
        put("description", description)
    }

    private fun boolean(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }
}
