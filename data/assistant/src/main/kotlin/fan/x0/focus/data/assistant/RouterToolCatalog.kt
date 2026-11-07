package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ToolDto
import fan.x0.focus.core.network.openai.ToolFunctionDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object RouterToolCatalog {
    const val START_SIDE_CHAT = "start_side_chat"
    const val LIST_SIDE_CHATS = "list_side_chats"
    const val READ_SIDE_CHAT = "read_side_chat"
    const val STOP_SIDE_CHAT = "stop_side_chat"
    const val RECALL = "recall"
    const val ASK = "ask"
    const val STAY_SILENT = "stay_silent"
    const val SET_WORKING_TEXT = "set_working_text"
    const val READ_MEMORY = "read_memory"
    const val WRITE_MEMORY = "write_memory"
    const val LIST_ROLE_MODELS = "list_role_models"
    const val SET_ROLE_MODEL = "set_role_model"
    const val CLEAR_ROLE_MODEL = "clear_role_model"
    const val SHOW_SIDE_CHAT_RESULT = "show_side_chat_result"

    val names: Set<String> = setOf(
        START_SIDE_CHAT,
        LIST_SIDE_CHATS,
        READ_SIDE_CHAT,
        STOP_SIDE_CHAT,
        RECALL,
        ASK,
        STAY_SILENT,
        SET_WORKING_TEXT,
        READ_MEMORY,
        WRITE_MEMORY,
        LIST_ROLE_MODELS,
        SET_ROLE_MODEL,
        CLEAR_ROLE_MODEL,
        SHOW_SIDE_CHAT_RESULT,
    )

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            name = START_SIDE_CHAT,
            description = "Start a side chat for work that needs tools, files, devices, or several steps. " +
                "Attachments from the current event are forwarded automatically. " +
                "Return the side chat id. Side chats run in parallel and report back as events.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("title", "task")) {
                put("title", string("Short title for the side chat, at most 30 characters"))
                put("task", string("Complete, self-contained task. Include context the worker needs."))
                put("profile", string("light, standard, or heavy. Default standard"))
                put("thread_key", string("Thread key from the current event, when this work continues a thread"))
                put("note", string("One line the user may see later, optional"))
            },
        ),
        ToolSpec(
            name = LIST_SIDE_CHATS,
            description = "List side chats with status, profile, title, and the latest summary.",
            kind = ToolKind.Read,
            parameters = schema {
                put("status", string("all, active, waiting, done, or failed. Default all"))
                put("limit", number("Maximum rows, default 20"))
            },
        ),
        ToolSpec(
            name = READ_SIDE_CHAT,
            description = "Read a side chat's recent messages and tool trace. Use when a result needs detail.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("side_chat_id")) {
                put("side_chat_id", string("Side chat id from start_side_chat or list_side_chats"))
                put("limit", number("Maximum messages, default 20"))
            },
        ),
        ToolSpec(
            name = STOP_SIDE_CHAT,
            description = "Stop a running side chat.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("side_chat_id")) {
                put("side_chat_id", string("Side chat id"))
                put("reason", string("Short reason, optional"))
            },
        ),
        ToolSpec(
            name = RECALL,
            description = "Read a side chat's full stored trajectory by side chat id or message id.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("id")) {
                put("id", string("Side chat id, or message id as a number"))
                put("limit", number("Maximum messages, default 40"))
            },
        ),
        ToolSpec(
            name = ASK,
            description = "Ask the user one concrete question in the main conversation. Use only when you cannot proceed. " +
                "This does not block; the answer arrives as a new user event.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("question")) {
                put("question", string("One short question"))
                put("context", string("One line of context, optional"))
            },
        ),
        ToolSpec(
            name = STAY_SILENT,
            description = "End this turn without writing to the main conversation. Use when no person must act and no result is useful yet.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = SET_WORKING_TEXT,
            description = "Set the short working line shown above the main composer, at most 20 characters.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("text")) {
                put("text", string("Short working line in the user's language"))
            },
        ),
        ToolSpec(
            name = READ_MEMORY,
            description = "Read the router memory file. Keep durable routing preferences and standing rules there.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = WRITE_MEMORY,
            description = "Replace the router memory file. Keep it short and current.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("content")) {
                put("content", string("Full new content of the router memory file"))
            },
        ),
        ToolSpec(
            name = LIST_ROLE_MODELS,
            description = "List every model role and its configured provider and model.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = SET_ROLE_MODEL,
            description = "Set the model for one role. Only use when the user explicitly asks. " +
                "The provider must already exist and have a key.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("role", "provider_id", "model")) {
                put("role", string("router, worker_light, worker_standard, worker_heavy, subagent, compactor, watchdog, judge, title, or suggestion"))
                put("provider_id", string("Provider id"))
                put("model", string("Model id"))
                put("reasoning", string("Reasoning level: off, auto, low, medium, high, xhigh, or max. Default auto"))
            },
        ),
        ToolSpec(
            name = CLEAR_ROLE_MODEL,
            description = "Clear one role's model so it follows its fallback chain again.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("role")) {
                put("role", string("Role key"))
            },
        ),
        ToolSpec(
            name = SHOW_SIDE_CHAT_RESULT,
            description = "Put a side chat result card in the main conversation. Use only when the user can act on or use the result. " +
                "The card links to the side chat.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("side_chat_id")) {
                put("side_chat_id", string("Side chat id"))
                put("message", string("One short line for the user, optional"))
            },
        ),
    )

    private val byName = specs.associateBy { it.name }

    fun find(name: String): ToolSpec? = byName[name]

    fun dto(): List<ToolDto> = specs.map {
        ToolDto(function = ToolFunctionDto(it.name, it.description, it.parameters))
    }

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
}
