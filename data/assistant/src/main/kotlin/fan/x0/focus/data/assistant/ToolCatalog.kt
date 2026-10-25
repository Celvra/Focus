package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ToolDto
import fan.x0.focus.core.network.openai.ToolFunctionDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class ToolKind { Read, Write, Gated, Plan }

data class ToolSpec(
    val name: String,
    val description: String,
    val kind: ToolKind,
    val parameters: JsonObject,
) {
    fun toDto(): ToolDto = ToolDto(
        function = ToolFunctionDto(name = name, description = description, parameters = parameters),
    )
}

object ToolCatalog {
    const val SET_WORKING_TEXT = "set_working_text"

    val all: List<ToolSpec> = listOf(
        ToolSpec(
            name = "list_calendar_events",
            description = "List calendar events in the next N days. Use before answering anything about schedule.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("days")) {
                put("days", number("How many days ahead, 1 to 60"))
                put("limit", number("Maximum events, default 40"))
            },
        ),
        ToolSpec(
            name = "search_calendar",
            description = "Search calendar events by text in the next N days.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("query")) {
                put("query", string("Text to look for in event title or description"))
                put("days", number("How many days ahead, default 60"))
            },
        ),
        ToolSpec(
            name = "list_feeds",
            description = "List the user's RSS feeds and whether the last refresh failed.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "list_articles",
            description = "List recent articles from the user's feeds, newest first.",
            kind = ToolKind.Read,
            parameters = schema {
                put("limit", number("Maximum articles, default 15"))
                put("unread_only", boolean("Only unread articles"))
                put("feed_id", string("Restrict to one feed id"))
            },
        ),
        ToolSpec(
            name = "search_articles",
            description = "Search saved articles by text.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("query")) {
                put("query", string("Text to look for in article title or content"))
            },
        ),
        ToolSpec(
            name = "read_article",
            description = "Read the full stored content of one article.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("article_id")) {
                put("article_id", string("Article id from list_articles"))
            },
        ),
        ToolSpec(
            name = "list_files",
            description = "List files in the folder the user connected. Use before reading or writing a file.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "read_text_file",
            description = "Read a text file from the connected folder.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("uri")) {
                put("uri", string("File uri from list_files"))
            },
        ),
        ToolSpec(
            name = "list_workspace_files",
            description = "List the assistant context files with name, size, and a short preview.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "read_workspace_file",
            description = "Read one assistant context file in full.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("file")) {
                put("file", string("One of: soul, persona, user, agent, memory"))
            },
        ),
        ToolSpec(
            name = "read_web_article",
            description = "Fetch a web page by url and extract its readable article text.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("url")) {
                put("url", string("Public http or https url"))
            },
        ),
        ToolSpec(
            name = "list_goals",
            description = "List the user's goals with status and progress.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "list_reminders",
            description = "List upcoming reminders.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "list_updates",
            description = "List the cards currently on the user's Today feed, newest first.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "list_ideas",
            description = "List the idea cards currently saved on the user's Today ideas tab.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "list_icons",
            description = "List the icon keys you may assign to feed cards, ideas, and goals.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "mark_article_read",
            description = "Mark one article as read.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("article_id")) {
                put("article_id", string("Article id"))
            },
        ),
        ToolSpec(
            name = "add_feed",
            description = "Subscribe to an RSS or Atom feed url and fetch it once.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("url")) {
                put("url", string("Feed url"))
            },
        ),
        ToolSpec(
            name = "create_goal",
            description = "Create a goal for the user.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("title")) {
                put("title", string("Short goal title"))
                put("detail", string("Why it matters or how to start"))
                put("due_at", string("Due time in ISO-8601, optional"))
                put("category", string("Grouping label you choose, reuse an existing one when it fits"))
                put("icon", string("Icon key from list_icons"))
            },
        ),
        ToolSpec(
            name = "update_goal",
            description = "Update a goal's title, detail, status, progress, note, category, or due time.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("goal_id")) {
                put("goal_id", string("Goal id from list_goals"))
                put("title", string("New goal title"))
                put("detail", string("New goal detail"))
                put("status", string("active, paused, or done"))
                put("progress", number("0 to 100"))
                put("note", string("Short progress note"))
                put("due_at", string("Due time in ISO-8601"))
                put("category", string("Grouping label"))
                put("icon", string("Icon key from list_icons"))
            },
        ),
        ToolSpec(
            name = "save_memory",
            description = "Append one durable fact about the user to MEMORY.md.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("content")) {
                put("content", string("One sentence fact"))
            },
        ),
        ToolSpec(
            name = "create_reminder",
            description = "Schedule a one-off reminder notification.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("text", "at")) {
                put("text", string("Reminder text"))
                put("at", string("When to fire, ISO-8601"))
            },
        ),
        ToolSpec(
            name = "write_text_file",
            description = "Overwrite an existing text file in the connected folder.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("uri", "content")) {
                put("uri", string("File uri from list_files"))
                put("content", string("New file content"))
            },
        ),
        ToolSpec(
            name = "create_text_file",
            description = "Create a new text file in the connected folder.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("name", "content")) {
                put("name", string("File name with extension"))
                put("content", string("File content"))
            },
        ),
        ToolSpec(
            name = "save_profile",
            description = "Store the user's name, focus, tone, or language in USER.md.",
            kind = ToolKind.Write,
            parameters = schema {
                put("name", string("What to call the user"))
                put("focus", string("Their main focus right now"))
                put("tone", string("How replies should sound"))
                put("language", string("Preferred reply language"))
            },
        ),
        ToolSpec(
            name = "set_assistant_name",
            description = "Change your own name. Use when the user asks you to go by a different name, or when a new name fits.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("name")) {
                put("name", string("New name, under 40 characters"))
            },
        ),
        ToolSpec(
            name = "list_providers",
            description = "List saved model providers with their base URL, model, protocol, and whether a key is stored.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "save_provider",
            description = "Save an OpenAI-compatible provider and model. The API key is stored encrypted on the device. Never repeat the key back to the user.",
            kind = ToolKind.Write,
            parameters = schema {
                put("provider_id", string("Saved provider id to update, optional"))
                put("name", string("Short provider name, for example OpenAI"))
                put("base_url", string("Base URL, for example https://api.openai.com/v1"))
                put("model", string("Model id"))
                put("api_key", string("API key. Stored encrypted and never shown again."))
                put("protocol", string("chat, responses, or anthropic. Default chat."))
                put("make_active", boolean("Make this the active provider. Default true."))
            },
        ),
        ToolSpec(
            name = "test_provider",
            description = "Check that a provider streams text and calls tools. Use a saved provider id or pass the connection details.",
            kind = ToolKind.Read,
            parameters = schema {
                put("provider_id", string("Saved provider id, optional"))
                put("base_url", string("Base URL, when no provider id is given"))
                put("api_key", string("API key, when no provider id is given"))
                put("model", string("Model id, when no provider id is given"))
                put("protocol", string("chat, responses, or anthropic"))
            },
        ),
        ToolSpec(
            name = "list_models",
            description = "Fetch the model list from a provider's /models endpoint, enriched with context window, price, and capability metadata from models.dev. Use a saved provider id or pass the connection details. Always call this before switch_model; never guess model ids.",
            kind = ToolKind.Read,
            parameters = schema {
                put("provider_id", string("Saved provider id, optional"))
                put("base_url", string("Base URL, when no provider id is given"))
                put("api_key", string("API key, when no provider id is given"))
                put("protocol", string("chat, responses, or anthropic"))
            },
        ),
        ToolSpec(
            name = "switch_model",
            description = "Change the model. Pass default=true to also save it as the app default model, otherwise it only affects the current conversation.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("model")) {
                put("model", string("Model id"))
                put("provider_id", string("Saved provider id, optional"))
                put("default", boolean("true to also set it as the app default model"))
            },
        ),
        ToolSpec(
            name = "write_workspace_file",
            description = "Replace one assistant context file. Keep it short, plain, and current.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("file", "content")) {
                put("file", string("One of: soul, persona, user, agent, memory"))
                put("content", string("Full new file content"))
            },
        ),
        ToolSpec(
            name = "append_workspace_file",
            description = "Append a short block to one assistant context file.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("file", "content")) {
                put("file", string("One of: soul, persona, user, agent, memory"))
                put("content", string("Text to append"))
            },
        ),
        ToolSpec(
            name = "rename_conversation",
            description = "Rename the current chat conversation. Use a short title for the current topic, and keep the existing title when it still fits.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("title")) {
                put("title", string("Short plain title, under 40 characters"))
            },
        ),
        ToolSpec(
            name = SET_WORKING_TEXT,
            description = "Set the short line under the chat title while you work. Call it before a slow tool, in the user's language, for example 正在拉取 or Fetching feeds. The app clears it when the turn ends.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("text")) {
                put("text", string("Full working line, under 20 characters"))
            },
        ),
        ToolSpec(
            name = "post_update",
            description = "Post one card to the user's Today feed. Call it once per card, after reading the source it comes from.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("headline", "body", "reason")) {
                put("headline", string("Short headline, under 60 characters"))
                put("body", string("Two or three sentences of plain summary, no markdown"))
                put("reason", string("Why this card belongs on the feed, one sentence"))
                put("category", string("Grouping label you choose for this card"))
                put("icon", string("Icon key from list_icons, for example news or code"))
                put("article_id", string("Article id when the card comes from a saved article"))
            },
        ),
        ToolSpec(
            name = "save_idea",
            description = "Save one idea card on the user's Today ideas tab. Use it when the user asks what to do next or wants suggestions.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("title", "detail")) {
                put("title", string("One sentence idea title"))
                put("detail", string("Two to four sentences explaining the idea"))
                put(
                    "steps",
                    objectArray(
                        description = "What the idea includes, 1 to 4 items",
                        fields = listOf("title", "detail"),
                    ),
                )
                put("category", string("Grouping label you choose, reuse a label you already used when it fits"))
                put("icon", string("Icon key from list_icons"))
            },
        ),
        ToolSpec(
            name = "create_calendar_event",
            description = "Create a calendar event. Requires the user's approval before it runs.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("title", "start", "end")) {
                put("title", string("Event title"))
                put("start", string("Start time, ISO-8601"))
                put("end", string("End time, ISO-8601"))
                put("description", string("Optional details"))
                put("location", string("Optional place"))
            },
        ),
        ToolSpec(
            name = "delete_calendar_event",
            description = "Delete a calendar event. Requires the user's approval.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("event_id")) {
                put("event_id", string("Calendar event id"))
            },
        ),
        ToolSpec(
            name = "delete_article",
            description = "Delete one saved article. Requires the user's approval.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("article_id")) {
                put("article_id", string("Article id"))
            },
        ),
        ToolSpec(
            name = "remove_feed",
            description = "Unsubscribe from a feed and delete its articles. Requires the user's approval.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("feed_id")) {
                put("feed_id", string("Feed id from list_feeds"))
            },
        ),
        ToolSpec(
            name = "delete_goal",
            description = "Delete a goal. Requires the user's approval.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("goal_id")) {
                put("goal_id", string("Goal id"))
            },
        ),
        ToolSpec(
            name = "delete_update",
            description = "Delete one card from the user's Today feed. Requires the user's approval.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("update_id")) {
                put("update_id", string("Feed card id from list_updates"))
            },
        ),
        ToolSpec(
            name = "delete_idea",
            description = "Delete one idea from the user's Today ideas tab. Requires the user's approval.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("idea_id")) {
                put("idea_id", string("Idea id from list_ideas"))
            },
        ),
        ToolSpec(
            name = "delete_file",
            description = "Delete a file in the connected folder. Requires the user's approval.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("uri")) {
                put("uri", string("File uri"))
            },
        ),
        ToolSpec(
            name = "create_plan",
            description = "Run several write steps as one approved plan. Use for multi-step changes.",
            kind = ToolKind.Plan,
            parameters = schema(required = listOf("title", "steps")) {
                put("title", string("Short plan title"))
                put("summary", string("One sentence describing the outcome"))
                put(
                    "steps",
                    arrayOf(
                        schema(required = listOf("tool", "arguments", "label")) {
                            put("tool", string("Tool name to run"))
                            put("arguments", string("Tool arguments as a JSON object string"))
                            put("label", string("Short line shown in the timeline"))
                        },
                        "Ordered steps",
                    ),
                )
            },
        ),
        ToolSpec(
            name = "complete_onboarding",
            description = "Finish first-run setup. Only for onboarding.",
            kind = ToolKind.Write,
            parameters = schema {},
        ),
    )

    private val byName = all.associateBy { it.name }

    fun find(name: String): ToolSpec? = byName[name]

    fun onboarding(): List<ToolSpec> = all.filter { it.name in onboardingTools }

    fun chat(): List<ToolSpec> = all.filterNot { it.name == "complete_onboarding" }

    fun redactArguments(name: String, arguments: String): String {
        if (name !in secretArgumentTools) return arguments
        val value = runCatching { Json.parseToJsonElement(arguments) as? JsonObject }.getOrNull()
            ?: return arguments
        if (!value.containsKey("api_key")) return arguments
        val redacted = JsonObject(
            value.mapValues { (key, item) ->
                if (key == "api_key") JsonPrimitive("***") else item
            },
        )
        return redacted.toString()
    }

    private val onboardingTools = setOf(
        "save_profile",
        "save_memory",
        "set_assistant_name",
        "list_providers",
        "save_provider",
        "test_provider",
        "list_models",
        "complete_onboarding",
    )

    private val secretArgumentTools = setOf("save_provider", "test_provider", "list_models")

    private fun objectArray(description: String, fields: List<String>): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
                fields.forEach { put(it, string(it)) }
            }
            putJsonArray("required") { fields.forEach { add(JsonPrimitive(it)) } }
            put("additionalProperties", false)
        }
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

    private fun boolean(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

    private fun arrayOf(item: JsonObject, description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        put("items", item)
    }
}
