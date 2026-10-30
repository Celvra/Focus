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

enum class ToolKind { Read, Write, Gated, Plan, Ask }

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
                put("requires_key", boolean("False only when the provider serves models without an API key."))
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
            name = "bridge_list_devices",
            description = "List the computers paired to this phone, with host, port, and which one is active. Use the returned id as device_id to target one computer in other bridge tools. Read-only.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "bridge_run_command",
            description = "Run one shell command on a paired computer and wait for it to finish. Returns stdout, stderr, and the exit code. Requires approval unless Auto Pilot allows it.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("command")) {
                put("command", string("Shell command line, for example git status"))
                put("cwd", string("Working directory on the computer, optional"))
                put("timeout_sec", number("Max seconds to wait, default 120"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_session_open",
            description = "Open a persistent shell session on a paired computer. Returns the session id for bridge_session_send. Requires approval unless Auto Pilot allows it.",
            kind = ToolKind.Gated,
            parameters = schema {
                put("name", string("Short session name, optional"))
                put("shell", string("Shell path, default is the login shell"))
                put("cwd", string("Working directory, optional"))
                put("cols", number("Terminal columns, default 100"))
                put("rows", number("Terminal rows, default 30"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_session_send",
            description = "Send text to a persistent bridge session, usually a command ending with a newline. Requires approval unless Auto Pilot allows it.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("session_id", "input")) {
                put("session_id", string("Session id from bridge_list_sessions"))
                put("input", string("Text to send, end with a newline to run a command"))
                put("signal", string("Instead of input, send one signal: interrupt or terminate"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_list_sessions",
            description = "List the open shell sessions on a paired computer. Read-only.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_read_session",
            description = "Read the recent output of a bridge shell session. Read-only.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("session_id")) {
                put("session_id", string("Session id from bridge_list_sessions"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_close_session",
            description = "Close a shell session on a paired computer. Requires approval unless Auto Pilot allows it.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("session_id")) {
                put("session_id", string("Session id from bridge_list_sessions"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_fs_read",
            description = "Read a text file on a paired computer. Read-only.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("path")) {
                put("path", string("Absolute file path on the computer"))
                put("offset", number("First line to read, 1-based, optional"))
                put("limit", number("How many lines to read, optional"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_fs_list",
            description = "List one directory on a paired computer. Read-only.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("path")) {
                put("path", string("Absolute directory path on the computer"))
                put("depth", number("Recursion depth, default 1"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_fs_glob",
            description = "Find files on a paired computer by glob pattern. Read-only.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("pattern")) {
                put("pattern", string("Glob pattern, for example **/*.kt"))
                put("root", string("Directory to search from, optional"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_fs_grep",
            description = "Search file contents on a paired computer with a regular expression. Read-only.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("pattern")) {
                put("pattern", string("Regular expression"))
                put("root", string("Directory to search from, optional"))
                put("include", string("Only files matching this glob, for example *.rs"))
                put("exclude", string("Skip files matching this glob"))
                put("case_sensitive", boolean("Match case"))
                put("context", number("Lines of context before and after, 0 to 5"))
                put("limit", number("Maximum matches, default 400"))
                put("literal", boolean("true to match plain text instead of a regular expression"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_fs_write",
            description = "Write a whole text file on a paired computer, creating or replacing it. Requires approval unless Auto Pilot allows it.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("path", "content")) {
                put("path", string("Absolute file path on the computer"))
                put("content", string("Full new file content"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_fs_edit",
            description = "Replace an exact snippet inside one file on a paired computer. Use bridge_fs_read first. Requires approval unless Auto Pilot allows it.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("path", "old", "new")) {
                put("path", string("Absolute file path on the computer"))
                put("old", string("Exact text to replace, including indentation"))
                put("new", string("Replacement text"))
                put("replace_all", boolean("true to replace every occurrence"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_fs_delete",
            description = "Delete a file or directory on a paired computer. Requires approval unless Auto Pilot allows it.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("path")) {
                put("path", string("Absolute path on the computer"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_set_workspace",
            description = "Limit which directories the agent may touch on a paired computer. An empty list means no limit. Requires approval.",
            kind = ToolKind.Gated,
            parameters = schema {
                put(
                    "roots",
                    arrayOf(string("Absolute directory path"), "Allowed directories, empty for no limit"),
                )
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "ask",
            description = "Ask the user one or more questions and wait for the answers. Use it when a reply needs a decision, a missing detail, or a preference you cannot infer. " +
                "Fill every question with a title and a subtitle that explains why you ask, so the user knows what the answer is for. " +
                "Kinds: single picks one option, multi picks several, text takes free text, confirm is yes or no, number takes a value. " +
                "Set allow_other when a written answer should sit next to the options. " +
                "Call it alone and wait: the app shows one card and your next message carries the answers.",
            kind = ToolKind.Ask,
            parameters = schema(required = listOf("questions")) {
                put("intro", string("One short line shown above the questions"))
                put("questions", askQuestions())
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
        ToolSpec(
            name = "todowrite",
            description = "Replace the task list for the current conversation. Use at the start of multi-step work and update it as items finish. Keep exactly one item in_progress.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("todos")) {
                put("todos", buildJsonObject {
                    put("type", "array")
                    put("description", "Ordered task list")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            put("content", string("What to do, short and concrete"))
                            put("status", string("pending, in_progress, completed, or cancelled"))
                            put("priority", string("high, medium, or low"))
                        }
                        putJsonArray("required") {
                            add(JsonPrimitive("content"))
                            add(JsonPrimitive("status"))
                            add(JsonPrimitive("priority"))
                        }
                        put("additionalProperties", false)
                    }
                })
            },
        ),
        ToolSpec(
            name = "todoread",
            description = "Read the current task list for this conversation.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = "propose_plan",
            description = "In plan mode, submit a concrete plan for approval. Include steps with the tools and arguments you intend to use, files, risks, and how you will verify.",
            kind = ToolKind.Plan,
            parameters = schema(required = listOf("title", "summary", "steps")) {
                put("title", string("Short plan title"))
                put("summary", string("What the plan will change and why"))
                put("steps", buildJsonObject {
                    put("type", "array")
                    put("description", "One to eight steps in execution order")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            put("label", string("Short step label"))
                            put("tool", string("Tool name to run in build mode"))
                            put("arguments", buildJsonObject { put("type", "object") })
                        }
                        putJsonArray("required") {
                            add(JsonPrimitive("label"))
                            add(JsonPrimitive("tool"))
                            add(JsonPrimitive("arguments"))
                        }
                        put("additionalProperties", false)
                    }
                })
                put("files", buildJsonObject {
                    put("type", "array")
                    put("description", "Files the plan will inspect or change")
                    putJsonObject("items") { put("type", "string") }
                })
                put("risks", buildJsonObject {
                    put("type", "array")
                    put("description", "What could go wrong")
                    putJsonObject("items") { put("type", "string") }
                })
                put("verification", buildJsonObject {
                    put("type", "array")
                    put("description", "How you will check the result")
                    putJsonObject("items") { put("type", "string") }
                })
            },
        ),
        ToolSpec(
            name = "web_search",
            description = "Search the web with the configured provider. Returns titles, links, and snippets. Use it for current facts and cite the links.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("query")) {
                put("query", string("Search query"))
                put("count", number("How many results, 1 to 10"))
                put("provider", string("deepseek, tavily, tinyfish, exa, bing, duckduckgo, or yandex"))
                put("language", string("Language hint, optional"))
                put("time_range", string("day, week, month, or year, optional"))
            },
        ),
        ToolSpec(
            name = "spawn_agent",
            description = "Run a self-contained subtask in a separate read-only agent context. Use it to inspect a large area without flooding this chat. Returns a summary, files, and steps.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("task")) {
                put("task", string("Clear task with the expected result"))
                put("tools", buildJsonObject {
                    put("type", "array")
                    put("description", "Optional read-only tool names for the subagent")
                    putJsonObject("items") { put("type", "string") }
                })
                put("model", string("Optional model id"))
                put("max_rounds", number("Maximum tool rounds, default 6"))
            },
        ),
        ToolSpec(
            name = "mcp_list_tools",
            description = "List the tools exposed by one connected MCP server.",
            kind = ToolKind.Read,
            parameters = schema {
                put("server", string("Server name or id, optional when only one is enabled"))
            },
        ),
        ToolSpec(
            name = "mcp_call_tool",
            description = "Call one tool on a connected MCP server. The arguments object is passed through unchanged.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("tool", "arguments")) {
                put("server", string("Server name or id, optional when only one is enabled"))
                put("tool", string("Tool name from mcp_list_tools"))
                put("arguments", buildJsonObject {
                    put("type", "object")
                    put("description", "Tool arguments")
                })
            },
        ),
        ToolSpec(
            name = "mcp_list_resources",
            description = "List resources exposed by one connected MCP server.",
            kind = ToolKind.Read,
            parameters = schema {
                put("server", string("Server name or id, optional when only one is enabled"))
            },
        ),
        ToolSpec(
            name = "mcp_read_resource",
            description = "Read one resource from a connected MCP server.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("uri")) {
                put("server", string("Server name or id, optional when only one is enabled"))
                put("uri", string("Resource uri from mcp_list_resources"))
            },
        ),
        ToolSpec(
            name = "index_workspace",
            description = "Build or update the search index for the active workspace on the paired computer.",
            kind = ToolKind.Write,
            parameters = schema {
                put("force", boolean("Rebuild every file instead of reusing unchanged entries"))
            },
        ),
        ToolSpec(
            name = "search_files",
            description = "Search indexed workspace files by path and content.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("query")) {
                put("query", string("Text or regular expression"))
                put("regex", boolean("Treat query as a regular expression"))
                put("glob", string("Only paths matching this glob, for example **/*.kt"))
                put("case_sensitive", boolean("Match case"))
                put("limit", number("Maximum matches, default 80"))
            },
        ),
        ToolSpec(
            name = "workspace_files",
            description = "List indexed workspace files, optionally filtered by glob.",
            kind = ToolKind.Read,
            parameters = schema {
                put("glob", string("Glob filter, optional"))
                put("limit", number("Maximum files, default 200"))
            },
        ),
        ToolSpec(
            name = "bridge_bg_start",
            description = "Start a shell command in the background on a paired computer and return immediately. The chat continues working. When it finishes, the app sends you the exit status and log tail in a new turn.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("command")) {
                put("command", string("Shell command line"))
                put("cwd", string("Working directory, optional"))
                put("timeout_sec", number("Maximum seconds before the process group is stopped, default 600"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_bg_logs",
            description = "Read the captured stdout and stderr of a background command.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("id")) {
                put("id", string("Background command id"))
                put("after", number("Only lines after this sequence number"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_bg_kill",
            description = "Stop a background command and its process group.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("id")) {
                put("id", string("Background command id"))
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_bg_list",
            description = "List background commands on a paired computer.",
            kind = ToolKind.Read,
            parameters = schema {
                put("device_id", deviceId())
            },
        ),
        ToolSpec(
            name = "bridge_bg_clear",
            description = "Clear logs of finished background commands.",
            kind = ToolKind.Write,
            parameters = schema {
                put("id", string("One command id, optional"))
                put("device_id", deviceId())
            },
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

    private fun askQuestions(): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", "One to five questions, all answered in one card before you continue")
        put("minItems", 1)
        put("maxItems", 5)
        putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
                put("id", string("Short stable key for the answer, for example horizon"))
                put("title", string("The question itself"))
                put("subtitle", string("One line explaining why you ask"))
                put("kind", string("single, multi, text, confirm, or number. Default single"))
                put("icon", string("Icon key from list_icons"))
                put("allow_other", boolean("Add a written answer next to the options. Default false"))
                put("placeholder", string("Hint inside the text or number field"))
                put("options", buildJsonObject {
                    put("type", "array")
                    put("description", "Answers to pick from, two to six. Needed for single and multi, optional for confirm")
                    put("maxItems", 6)
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            put("label", string("Short option label"))
                            put("detail", string("One line explaining the option"))
                            put("icon", string("Icon key from list_icons"))
                        }
                        putJsonArray("required") { add(JsonPrimitive("label")) }
                        put("additionalProperties", false)
                    }
                })
            }
            putJsonArray("required") {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("subtitle"))
            }
            put("additionalProperties", false)
        }
    }

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

    private fun deviceId(): JsonObject =
        string("Paired computer id from bridge_list_devices, optional, defaults to the active computer")
}
