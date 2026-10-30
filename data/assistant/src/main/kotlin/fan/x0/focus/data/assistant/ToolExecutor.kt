package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.CompletedToolCall
import fan.x0.focus.core.network.openai.ConnectionTestException
import fan.x0.focus.core.network.openai.ModelConnectionTester
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.data.bridge.BridgeEndpoint
import fan.x0.focus.data.bridge.BridgeExecRequest
import fan.x0.focus.data.bridge.BridgeRepository
import fan.x0.focus.data.sources.Article
import fan.x0.focus.data.sources.CalendarRepository
import fan.x0.focus.data.sources.Feed
import fan.x0.focus.data.sources.FeedRepository
import fan.x0.focus.data.sources.FileRepository
import fan.x0.focus.data.sources.SourceException
import fan.x0.focus.data.sources.WebArticleExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.koin.core.annotation.Single
import java.util.UUID

data class ToolResult(
    val ok: Boolean,
    val content: String,
    val summary: String,
    val approval: ApprovalRequest? = null,
    val ask: AskRequest? = null,
    val workingText: String? = null,
    val completesOnboarding: Boolean = false,
    val todos: List<ConversationTodo>? = null,
    val plan: ProposedPlan? = null,
)

data class StepOutcome(
    val label: String,
    val ok: Boolean,
    val summary: String,
)

@Single
class ToolExecutor(
    private val calendar: CalendarRepository,
    private val feeds: FeedRepository,
    private val files: FileRepository,
    private val web: WebArticleExtractor,
    private val goals: GoalStore,
    private val approvals: ApprovalStore,
    private val today: TodayStore,
    private val reminders: ReminderStore,
    private val workspace: WorkspaceStore,
    private val settings: AssistantSettings,
    private val reminderScheduler: ReminderScheduler,
    private val chats: ChatStore,
    private val secrets: SecretStore,
    private val tester: ModelConnectionTester,
    private val catalog: ModelCatalogRepository,
    private val bridge: BridgeRepository,
    private val gate: AutoPilotGate,
    private val webSearch: WebSearchRepository,
    private val mcp: McpManager,
    private val indexer: WorkspaceIndexer,
    private val backgroundWatch: BackgroundCommandMonitor,
) {
    val specs: List<ToolSpec> = ToolCatalog.chat()

    fun specsFor(mode: AgentMode): List<ToolSpec> =
        if (mode == AgentMode.Plan) specs.filter { it.kind == ToolKind.Read || it.kind == ToolKind.Ask || it.kind == ToolKind.Plan } else specs

    suspend fun execute(
        call: CompletedToolCall,
        conversationId: String = "",
        mode: AgentMode = AgentMode.Build,
    ): ToolResult = dispatch(call.name, call.arguments, allowApproval = true, conversationId = conversationId, mode = mode)

    suspend fun executeDirect(name: String, arguments: String): ToolResult =
        dispatch(name, arguments, allowApproval = false, conversationId = "", mode = AgentMode.Build)

    suspend fun runStep(step: ApprovalStep, conversationId: String = "", mode: AgentMode = AgentMode.Build): StepOutcome {
        val spec = ToolCatalog.find(step.tool)
        val args = parseObject(step.arguments)
        return when {
            spec == null || spec.kind == ToolKind.Plan || spec.kind == ToolKind.Ask ->
                StepOutcome(step.label, false, "Unknown step")
            args == null -> StepOutcome(step.label, false, "Invalid arguments")
            else -> {
                val result = runTool(spec, args, conversationId)
                StepOutcome(step.label, result.ok, result.summary)
            }
        }
    }

    private suspend fun dispatch(
        name: String,
        rawArguments: String,
        allowApproval: Boolean,
        conversationId: String,
        mode: AgentMode = AgentMode.Build,
    ): ToolResult {
        val spec = ToolCatalog.find(name) ?: return fail("Unknown tool: $name")
        val args = parseObject(rawArguments) ?: return fail("Invalid arguments for $name")
        if (mode == AgentMode.Plan && spec.kind !in setOf(ToolKind.Read, ToolKind.Ask, ToolKind.Plan)) {
            return fail("Plan mode only allows read-only tools. $name would change something. Ask the user to approve the plan first.")
        }
        when (spec.kind) {
            ToolKind.Read, ToolKind.Write -> return runTool(spec, args, conversationId)
            ToolKind.Gated -> {
                if (!allowApproval) return fail("$name needs approval and is not allowed here")
                val draft = gatedDraft(spec, args) ?: return fail("Missing or invalid fields for $name")
                val decision = if (spec.name.startsWith("bridge_") && spec.name != "bridge_set_workspace") {
                    gate.evaluate(
                        JudgeAction(kind = spec.name, title = draft.title, detail = draft.summary),
                        conversationId,
                    )
                } else {
                    GateDecision.NeedsApproval("approval required")
                }
                when (decision) {
                    is GateDecision.Allowed -> return runTool(spec, args, conversationId)
                    is GateDecision.Refused -> return fail("Refused: ${decision.reason}")
                    is GateDecision.NeedsApproval -> Unit
                }
                val step = ApprovalStep(spec.name, args.toString(), draft.label)
                val request = approvals.create(draft.title, draft.summary, listOf(step))
                return ToolResult(
                    ok = true,
                    content = "Waiting for the user to approve: ${draft.title}",
                    summary = draft.label,
                    approval = request,
                )
            }
            ToolKind.Ask -> {
                if (!allowApproval) return fail("$name needs a conversation")
                val request = askRequest(args) ?: return fail("$name needs one to five questions with a title")
                return ToolResult(
                    ok = true,
                    content = "Waiting for the user to answer",
                    summary = "${request.questions.size} questions",
                    ask = request,
                )
            }
            ToolKind.Plan -> {
                if (!allowApproval) return fail("Plans are not allowed here")
                val steps = planSteps(args) ?: return fail("Plan needs 1 to 8 valid steps")
                val title = args.text("title")?.take(80) ?: "Plan"
                val summary = args.text("summary").orEmpty()
                val request = approvals.create(title, summary, steps)
                val plan = ProposedPlan(
                    title = title,
                    summary = summary,
                    steps = steps.map {
                        PlanStep(
                            title = it.label,
                            detail = "",
                            tool = it.tool,
                            arguments = it.arguments,
                        )
                    },
                    files = args.stringList("files"),
                    risks = args.stringList("risks"),
                    verification = args.stringList("verification"),
                )
                return ToolResult(
                    ok = true,
                    content = "Waiting for the user to approve the plan: $title",
                    summary = title,
                    approval = request,
                    plan = plan,
                )
            }
        }
    }

    private suspend fun runTool(spec: ToolSpec, args: JsonObject, conversationId: String): ToolResult = try {
        when (spec.name) {
            "list_calendar_events" -> {
                val days = (args.number("days") ?: 7).coerceIn(1, 60)
                val limit = (args.number("limit") ?: 40).coerceIn(1, 100)
                val events = calendar.eventsBetween(ToolTime.now(), ToolTime.daysFromNow(days), limit)
                ok("${events.size} events in $days days", eventsJson(events))
            }
            "search_calendar" -> {
                val query = args.text("query") ?: return fail("query is required")
                val days = (args.number("days") ?: 60).coerceIn(1, 365)
                val events = calendar.search(query, days)
                ok("${events.size} matches", eventsJson(events))
            }
            "create_calendar_event" -> {
                val title = args.text("title") ?: return fail("title is required")
                val start = ToolTime.parse(args.text("start")) ?: return fail("start must be ISO-8601")
                val end = ToolTime.parse(args.text("end")) ?: return fail("end must be ISO-8601")
                if (end <= start) return fail("end must be after start")
                val id = calendar.createEvent(
                    title = title,
                    start = start,
                    end = end,
                    description = args.text("description"),
                    location = args.text("location"),
                )
                ok("Created $title", "Event created: $title ($id).")
            }
            "delete_calendar_event" -> {
                val id = args.text("event_id")?.toLongOrNull() ?: return fail("event_id is required")
                if (!calendar.deleteEvent(id)) return fail("Event not found")
                ok("Deleted event", "Calendar event deleted.")
            }
            "list_feeds" -> {
                val feeds = feeds.listFeeds()
                ok("${feeds.size} feeds", feedsJson(feeds))
            }
            "list_articles" -> {
                val limit = (args.number("limit") ?: 15).coerceIn(1, 50)
                val feedId = args.text("feed_id")
                val unreadOnly = args.flag("unread_only") ?: false
                val articles = feeds.listArticles(limit = if (unreadOnly) 100 else limit, feedId = feedId)
                    .filter { !unreadOnly || !it.read }
                    .take(limit)
                ok("${articles.size} articles", articlesJson(articles))
            }
            "search_articles" -> {
                val query = args.text("query") ?: return fail("query is required")
                val articles = feeds.searchArticles(query, 15)
                ok("${articles.size} matches", articlesJson(articles))
            }
            "read_article" -> {
                val id = args.text("article_id") ?: return fail("article_id is required")
                val article = feeds.getArticle(id) ?: return fail("Article not found")
                ok(
                    article.title,
                    buildJsonObject {
                        put("id", article.id)
                        put("title", article.title)
                        put("feed", article.feedTitle)
                        put("url", article.url)
                        put("published", article.publishedAt?.let { ToolTime.full(it) } ?: "")
                        put("content", article.content.take(8000))
                    }.toString(),
                )
            }
            "list_files" -> {
                val list = files.list()
                ok(
                    "${list.size} items",
                    buildJsonObject {
                        putJsonArray("files") {
                            list.forEach { file ->
                                add(
                                    buildJsonObject {
                                        put("name", file.name)
                                        put("uri", file.uri)
                                        put("is_directory", file.isDirectory)
                                        put("size", file.size)
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "read_text_file" -> {
                val uri = args.text("uri") ?: return fail("uri is required")
                val text = files.readText(uri, 12_000)
                ok("Read ${text.length} characters", text)
            }
            "read_web_article" -> {
                val url = args.text("url") ?: return fail("url is required")
                val article = web.extract(url)
                ok(
                    article.title,
                    buildJsonObject {
                        put("title", article.title)
                        put("site", article.siteName.orEmpty())
                        put("byline", article.byline.orEmpty())
                        put("url", article.url)
                        put("text", article.text)
                    }.toString(),
                )
            }
            "list_goals" -> {
                val list = goals.list()
                ok("${list.size} goals", goalsJson(list))
            }
            "list_workspace_files" -> {
                val documents = workspace.readAll()
                ok(
                    "${documents.size} context files",
                    buildJsonObject {
                        putJsonArray("files") {
                            documents.forEach { document ->
                                add(
                                    buildJsonObject {
                                        put("file", document.file.name.lowercase())
                                        put("name", document.file.fileName)
                                        put("characters", document.content.length)
                                        put("preview", document.content.take(200))
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "read_workspace_file" -> {
                val file = args.workspaceFile() ?: return fail("file must be one of ${WorkspaceFile.keys()}")
                val content = workspace.read(file)
                ok(
                    "Read ${file.fileName}",
                    buildJsonObject {
                        put("file", file.name.lowercase())
                        put("name", file.fileName)
                        put("content", content)
                    }.toString(),
                )
            }
            "write_workspace_file" -> {
                val file = args.workspaceFile() ?: return fail("file must be one of ${WorkspaceFile.keys()}")
                val content = args.text("content") ?: return fail("content is required")
                workspace.write(file, content)
                ok("Saved ${file.fileName}", "${file.fileName} updated.")
            }
            "append_workspace_file" -> {
                val file = args.workspaceFile() ?: return fail("file must be one of ${WorkspaceFile.keys()}")
                val content = args.text("content") ?: return fail("content is required")
                workspace.append(file, content)
                ok("Updated ${file.fileName}", "${file.fileName} updated.")
            }
            "list_reminders" -> {
                val list = reminders.upcoming(ToolTime.now())
                ok(
                    "${list.size} reminders",
                    buildJsonObject {
                        putJsonArray("reminders") {
                            list.forEach { reminder ->
                                add(
                                    buildJsonObject {
                                        put("id", reminder.id)
                                        put("text", reminder.text)
                                        put("at", ToolTime.iso(reminder.at))
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "list_updates" -> {
                val list = today.updates()
                ok(
                    "${list.size} feed cards",
                    buildJsonObject {
                        putJsonArray("updates") {
                            list.forEach { update ->
                                add(
                                    buildJsonObject {
                                        put("id", update.id)
                                        put("headline", update.headline)
                                        put("category", update.category)
                                        put("icon", update.icon)
                                        put("created_at", ToolTime.iso(update.createdAt))
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "list_ideas" -> {
                val list = today.ideas()
                ok(
                    "${list.size} ideas",
                    buildJsonObject {
                        putJsonArray("ideas") {
                            list.forEach { idea ->
                                add(
                                    buildJsonObject {
                                        put("id", idea.id)
                                        put("title", idea.title)
                                        put("category", idea.category)
                                        put("icon", idea.icon)
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "list_icons" -> {
                ok(
                    "${TodayIcons.all.size} icon keys",
                    buildJsonObject {
                        putJsonArray("icons") {
                            TodayIcons.all.forEach { (key, use) ->
                                add(buildJsonObject { put("key", key); put("use", use) })
                            }
                        }
                    }.toString(),
                )
            }
            "mark_article_read" -> {
                val id = args.text("article_id") ?: return fail("article_id is required")
                feeds.markRead(id)
                ok("Marked as read", "Article marked as read.")
            }
            "add_feed" -> {
                val url = args.text("url") ?: return fail("url is required")
                val feed = feeds.addFeed(url)
                ok("Added ${feed.title}", "Subscribed to ${feed.title}.")
            }
            "delete_article" -> {
                val id = args.text("article_id") ?: return fail("article_id is required")
                if (feeds.getArticle(id) == null) return fail("Article not found")
                feeds.deleteArticle(id)
                ok("Deleted article", "Article deleted.")
            }
            "remove_feed" -> {
                val id = args.text("feed_id") ?: return fail("feed_id is required")
                if (feeds.getFeed(id) == null) return fail("Feed not found")
                feeds.removeFeed(id)
                ok("Removed feed", "Feed removed.")
            }
            "create_goal" -> {
                val title = args.text("title") ?: return fail("title is required")
                val goal = goals.add(
                    title = title,
                    detail = args.text("detail"),
                    dueAt = ToolTime.parse(args.text("due_at")),
                    category = args.text("category").orEmpty(),
                    icon = args.text("icon").orEmpty(),
                )
                ok("Created ${goal.title}", "Goal created: ${goal.title} (${goal.id}).")
            }
            "update_goal" -> {
                val id = args.text("goal_id") ?: return fail("goal_id is required")
                val status = args.text("status")?.lowercase()?.let {
                    runCatching { GoalStatus.valueOf(it.replaceFirstChar { c -> c.uppercase() }) }.getOrNull()
                }
                val updated = goals.update(
                    id = id,
                    title = args.text("title"),
                    detail = args.text("detail"),
                    status = status,
                    progress = args.number("progress"),
                    note = args.text("note"),
                    dueAt = ToolTime.parse(args.text("due_at")),
                    category = args.text("category"),
                    icon = args.text("icon"),
                ) ?: return fail("Goal not found")
                ok("Updated ${updated.title}", "Goal updated: ${updated.title}.")
            }
            "delete_goal" -> {
                val id = args.text("goal_id") ?: return fail("goal_id is required")
                if (!goals.delete(id)) return fail("Goal not found")
                ok("Deleted goal", "Goal deleted.")
            }
            "save_memory" -> {
                val content = args.text("content") ?: return fail("content is required")
                workspace.appendMemory(content)
                ok("Saved memory", "Saved to MEMORY.md: $content")
            }
            "create_reminder" -> {
                val text = args.text("text") ?: return fail("text is required")
                val at = ToolTime.parse(args.text("at")) ?: return fail("at must be ISO-8601")
                if (at <= ToolTime.now()) return fail("Reminder time is in the past")
                val reminder = reminders.add(text, at)
                reminderScheduler.schedule(reminder.id, text, at)
                ok("Reminder at ${ToolTime.full(at)}", "Reminder set for ${ToolTime.full(at)}.")
            }
            "write_text_file" -> {
                val uri = args.text("uri") ?: return fail("uri is required")
                val content = args.text("content") ?: return fail("content is required")
                files.writeText(uri, content.take(100_000))
                ok("File saved", "File updated.")
            }
            "create_text_file" -> {
                val name = args.text("name") ?: return fail("name is required")
                val content = args.text("content") ?: return fail("content is required")
                val file = files.createText(name, content.take(100_000))
                ok("Created ${file.name}", "Created file ${file.name}.")
            }
            "delete_file" -> {
                val uri = args.text("uri") ?: return fail("uri is required")
                files.delete(uri)
                ok("Deleted file", "File deleted.")
            }
            "save_profile" -> {
                workspace.updateUserProfile(
                    name = args.text("name"),
                    tone = args.text("tone"),
                    focus = args.text("focus"),
                    language = args.text("language"),
                )
                ok("Profile saved", "USER.md updated.")
            }
            "set_assistant_name" -> {
                val name = args.text("name") ?: return fail("name is required")
                workspace.updateAssistantName(name)
                ok("Name set to ${name.trim().take(40)}", "Assistant name updated.")
            }
            "list_providers" -> {
                val config = settings.configOnce()
                val entries = config.providers.map { provider ->
                    buildJsonObject {
                        put("id", provider.id)
                        put("name", provider.name)
                        put("base_url", provider.baseUrl)
                        put("model", provider.model)
                        put("protocol", provider.protocol.name)
                        put("active", provider.id == config.activeProviderId)
                        put("key_saved", !secrets.apiKey(provider.id).isNullOrBlank())
                    }
                }
                ok(
                    "${entries.size} providers",
                    buildJsonObject {
                        putJsonArray("providers") { entries.forEach { add(it) } }
                    }.toString(),
                )
            }
            "save_provider" -> {
                val config = settings.configOnce()
                val requestedName = args.text("name")
                val requestedBaseUrl = args.text("base_url")?.trim()?.trimEnd('/')
                val existing = args.text("provider_id")?.let { id ->
                    config.providers.firstOrNull { it.id == id }
                } ?: requestedName?.let { name ->
                    config.providers.firstOrNull { it.name.equals(name, ignoreCase = true) }
                } ?: requestedBaseUrl?.let { url ->
                    config.providers.firstOrNull { it.baseUrl == url }
                }
                val baseUrl = requestedBaseUrl
                    ?: existing?.baseUrl
                    ?: return fail("base_url or provider_id is required")
                if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                    return fail("base_url must start with http:// or https://")
                }
                val model = args.text("model")
                    ?: existing?.model?.takeIf { it.isNotBlank() }
                    ?: return fail("model or provider_id is required")
                val protocolText = args.text("protocol")
                val protocol = if (protocolText == null) {
                    existing?.protocol ?: ChatProtocol.ChatCompletions
                } else {
                    protocolOf(protocolText) ?: return fail("protocol must be chat, responses, or anthropic")
                }
                val name = requestedName ?: existing?.name ?: providerName(baseUrl)
                val id = existing?.id ?: UUID.randomUUID().toString()
                val makeActive = args.flag("make_active") ?: true
                val requiresKey = args.flag("requires_key") ?: existing?.requiresKey ?: true
                settings.updateProvider(
                    ProviderConfig(
                        id = id,
                        name = name,
                        baseUrl = baseUrl,
                        model = model,
                        protocol = protocol,
                        requiresKey = requiresKey,
                    ),
                    makeActive = makeActive,
                )
                args.text("api_key")?.let { secrets.setApiKey(id, it) }
                val keySaved = !secrets.apiKey(id).isNullOrBlank()
                ok(
                    "Saved $name",
                    buildJsonObject {
                        put("id", id)
                        put("name", name)
                        put("base_url", baseUrl)
                        put("model", model)
                        put("protocol", protocol.name)
                        put("active", makeActive)
                        put("requires_key", requiresKey)
                        put("key_saved", keySaved)
                    }.toString(),
                )
            }
            "test_provider" -> {
                val config = settings.configOnce()
                val saved = args.text("provider_id")?.let { id ->
                    config.providers.firstOrNull { it.id == id }
                }
                val baseUrl = args.text("base_url")?.trim()?.trimEnd('/')
                    ?: saved?.baseUrl
                    ?: return fail("base_url or provider_id is required")
                if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                    return fail("base_url must start with http:// or https://")
                }
                val model = args.text("model") ?: saved?.model ?: return fail("model or provider_id is required")
                val protocol = protocolOf(args.text("protocol"))
                    ?: saved?.protocol
                    ?: ChatProtocol.ChatCompletions
                val key = args.text("api_key")
                    ?: saved?.let { secrets.apiKey(it.id) }
                    ?: return fail("api_key or provider_id is required")
                try {
                    val report = tester.test(ModelEndpoint(baseUrl, model), key, protocol)
                    ok(
                        "Connection works",
                        buildJsonObject {
                            put("ok", true)
                            put("model", report.echoedModel ?: model)
                            put("latency_ms", report.latencyMs)
                        }.toString(),
                    )
                } catch (e: ConnectionTestException) {
                    fail("Connection test failed: ${e.detail ?: e.stage.name}")
                }
            }
            "list_models" -> {
                val config = settings.configOnce()
                val saved = args.text("provider_id")?.let { id ->
                    config.providers.firstOrNull { it.id == id }
                }
                val baseUrl = args.text("base_url")?.trim()?.trimEnd('/')
                    ?: saved?.baseUrl
                    ?: return fail("base_url or provider_id is required")
                if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
                    return fail("base_url must start with http:// or https://")
                }
                val protocol = protocolOf(args.text("protocol"))
                    ?: saved?.protocol
                    ?: ChatProtocol.ChatCompletions
                val key = args.text("api_key")
                    ?: saved?.let { secrets.apiKey(it.id) }
                    ?: return fail("api_key or provider_id is required")
                val entries = catalog.models(
                    providerId = saved?.id ?: baseUrl,
                    baseUrl = baseUrl,
                    model = saved?.model.orEmpty(),
                    apiKey = key,
                    protocol = protocol,
                ).take(200)
                ok(
                    "${entries.size} models",
                    buildJsonObject {
                        putJsonArray("models") {
                            entries.forEach { entry ->
                                add(
                                    buildJsonObject {
                                        put("id", entry.id)
                                        val meta = entry.meta
                                        if (!meta.isEmpty) {
                                            meta.contextWindow?.let { put("context_window", it) }
                                            meta.inputPrice?.let { put("input_price_per_m", it) }
                                            meta.outputPrice?.let { put("output_price_per_m", it) }
                                            meta.supportsTools?.let { put("supports_tools", it) }
                                            meta.supportsReasoning?.let { put("supports_reasoning", it) }
                                            meta.supportsImage?.let { put("supports_image", it) }
                                            meta.knowledgeCutoff?.let { put("knowledge_cutoff", it) }
                                            meta.releaseDate?.let { put("released", it) }
                                            meta.description?.take(200)?.let { put("description", it) }
                                        }
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "switch_model" -> {
                val model = args.text("model") ?: return fail("model is required")
                val asDefault = args.flag("default") ?: false
                val config = settings.configOnce()
                val provider = args.text("provider_id")?.let { id ->
                    config.providers.firstOrNull { it.id == id }
                } ?: config.activeProvider ?: return fail("No provider")
                if (asDefault) {
                    settings.setActiveProvider(provider.id)
                    settings.setModel(provider.baseUrl, model, provider.protocol)
                }
                if (conversationId.isNotBlank()) {
                    val reasoning = chats.conversation(conversationId)?.reasoning.orEmpty()
                    chats.setProvider(conversationId, provider.id, model, reasoning)
                }
                ok(
                    if (asDefault) "Default model set to $model" else "Model set to $model",
                    buildJsonObject {
                        put("provider_id", provider.id)
                        put("model", model)
                        put("default", asDefault)
                    }.toString(),
                )
            }
            "rename_conversation" -> {
                val title = args.text("title") ?: return fail("title is required")
                if (conversationId.isBlank()) return fail("No active conversation")
                chats.rename(conversationId, title)
                ok("Renamed to $title", "Conversation renamed: $title.")
            }
            ToolCatalog.SET_WORKING_TEXT -> {
                val text = args.text("text") ?: return fail("text is required")
                ToolResult(
                    ok = true,
                    content = "Working line set: ${text.take(40)}",
                    summary = text.take(40),
                    workingText = text.take(40),
                )
            }
            "post_update" -> {
                val headline = args.text("headline") ?: return fail("headline is required")
                val body = args.text("body") ?: return fail("body is required")
                val reason = args.text("reason") ?: return fail("reason is required")
                val update = today.postUpdate(
                    headline = headline,
                    body = body,
                    reason = reason,
                    category = args.text("category").orEmpty(),
                    icon = args.text("icon").orEmpty(),
                    articleId = args.text("article_id"),
                )
                ok("Posted ${update.headline}", "Feed card posted: ${update.headline}.")
            }
            "save_idea" -> {
                val title = args.text("title") ?: return fail("title is required")
                val detail = args.text("detail") ?: return fail("detail is required")
                val idea = today.saveIdea(
                    title = title,
                    detail = detail,
                    steps = args.steps("steps"),
                    category = args.text("category").orEmpty(),
                    icon = args.text("icon").orEmpty(),
                )
                ok("Saved ${idea.title}", "Idea saved: ${idea.title}.")
            }
            "delete_update" -> {
                val id = args.text("update_id") ?: return fail("update_id is required")
                if (!today.deleteUpdate(id)) return fail("Card not found")
                ok("Deleted card", "Feed card deleted.")
            }
            "delete_idea" -> {
                val id = args.text("idea_id") ?: return fail("idea_id is required")
                if (!today.deleteIdea(id)) return fail("Idea not found")
                ok("Deleted idea", "Idea deleted.")
            }
"complete_onboarding" -> ToolResult(
                ok = true,
                content = "Setup will finish after your goodbye.",
                summary = "Onboarding complete",
                completesOnboarding = true,
            )
            "bridge_list_devices" -> {
                val devices = bridge.endpointsOnce()
                val activeId = bridge.activeEndpoint()?.id
                ok(
                    "${devices.size} devices",
                    buildJsonObject {
                        putJsonArray("devices") {
                            devices.forEach { endpoint ->
                                add(
                                    buildJsonObject {
                                        put("id", endpoint.id)
                                        put("name", endpoint.name)
                                        put("host", endpoint.host)
                                        put("port", endpoint.port)
                                        put("active", endpoint.id == activeId)
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "bridge_run_command" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val command = args.text("command") ?: return fail("command is required")
                val timeout = (args.number("timeout_sec") ?: 120).coerceIn(1, 600)
                val stdout = StringBuilder()
                val stderr = StringBuilder()
                var exitCode = 0
                var timedOut = false
                var error: String? = null
                bridge.exec(
                    endpoint,
                    BridgeExecRequest(command = command, cwd = args.text("cwd"), timeoutSec = timeout.toLong()),
                ) { event ->
                    when (event.type) {
                        "stdout" -> stdout.append(event.data.orEmpty())
                        "stderr" -> stderr.append(event.data.orEmpty())
                        "exit" -> {
                            exitCode = event.code
                            timedOut = event.timedOut
                        }
                        "error" -> error = event.message
                    }
                }
                if (error != null) return fail("Command failed: $error")
                ok(
                    "exit $exitCode${if (timedOut) " (timed out)" else ""} on ${endpoint.name}",
                    buildJsonObject {
                        put("device", endpoint.name)
                        put("device_id", endpoint.id)
                        put("exit_code", exitCode)
                        put("timed_out", timedOut)
                        put("stdout", stdout.toString().take(20_000))
                        put("stderr", stderr.toString().take(5_000))
                    }.toString(),
                )
            }
            "bridge_session_open" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val session = bridge.openSession(
                    endpoint,
                    cwd = args.text("cwd"),
                    shell = args.text("shell"),
                    cols = (args.number("cols") ?: 100).coerceIn(20, 300),
                    rows = (args.number("rows") ?: 30).coerceIn(5, 100),
                )
                ok("Session ${session.id}", "Opened session ${session.id} on ${endpoint.name} (${endpoint.id}).")
            }
            "bridge_session_send" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val sessionId = args.text("session_id") ?: return fail("session_id is required")
                val signal = args.text("signal")
                if (signal.isNullOrBlank()) {
                    val input = args.text("input") ?: return fail("input or signal is required")
                    bridge.sendSessionInput(endpoint, sessionId, input)
                    ok("Sent to session $sessionId", "Input delivered to session $sessionId on ${endpoint.name}.")
                } else {
                    bridge.signalSession(endpoint, sessionId, signal)
                    ok(
                        "Sent $signal to session $sessionId",
                        "Signal $signal delivered to session $sessionId on ${endpoint.name}.",
                    )
                }
            }
            "bridge_list_sessions" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val sessions = bridge.sessions(endpoint)
                ok(
                    "${sessions.size} sessions on ${endpoint.name}",
                    buildJsonObject {
                        put("device", endpoint.name)
                        put("device_id", endpoint.id)
                        putJsonArray("sessions") {
                            sessions.forEach { session ->
                                add(
                                    buildJsonObject {
                                        put("id", session.id)
                                        put("name", session.name)
                                        put("shell", session.shell)
                                        put("cols", session.cols)
                                        put("rows", session.rows)
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "bridge_read_session" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val sessionId = args.text("session_id") ?: return fail("session_id is required")
                val tail = bridge.sessionTail(endpoint, sessionId)
                ok("Read session $sessionId on ${endpoint.name}", tail.take(20_000))
            }
            "bridge_close_session" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val sessionId = args.text("session_id") ?: return fail("session_id is required")
                bridge.closeSession(endpoint, sessionId)
                ok("Closed session $sessionId", "Session $sessionId closed on ${endpoint.name}.")
            }
            "bridge_fs_read" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val path = args.text("path") ?: return fail("path is required")
                val result = bridge.read(endpoint, path, args.number("offset"), args.number("limit"))
                ok(
                    "Read $path (${result.totalLines} lines) on ${endpoint.name}" +
                        if (result.truncated) " (truncated)" else "",
                    buildJsonObject {
                        put("device", endpoint.name)
                        put("device_id", endpoint.id)
                        put("path", path)
                        put("content", result.content.ifBlank { result.lines })
                        put("total_lines", result.totalLines)
                        put("start_line", result.startLine)
                        put("end_line", result.endLine)
                        put("truncated", result.truncated)
                        put("notice", result.notice)
                        result.nextOffset?.let { put("next_offset", it) }
                    }.toString(),
                )
            }
            "bridge_fs_list" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val path = args.text("path") ?: return fail("path is required")
                val entries = bridge.list(endpoint, path, args.number("depth"))
                ok(
                    "${entries.size} entries under $path on ${endpoint.name}",
                    buildJsonObject {
                        put("device", endpoint.name)
                        put("device_id", endpoint.id)
                        put("path", path)
                        putJsonArray("entries") {
                            entries.forEach { entry ->
                                add(
                                    buildJsonObject {
                                        put("path", entry.path)
                                        put("name", entry.name)
                                        put("is_dir", entry.isDir)
                                        put("size", entry.size)
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "bridge_fs_glob" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val pattern = args.text("pattern") ?: return fail("pattern is required")
                val result = bridge.glob(endpoint, pattern, args.text("root"))
                ok(
                    "${result.matches.size} matches on ${endpoint.name}" +
                        if (result.truncated) " (truncated)" else "",
                    buildJsonObject {
                        put("device", endpoint.name)
                        put("device_id", endpoint.id)
                        put("pattern", pattern)
                        put("root", result.root)
                        put("truncated", result.truncated)
                        putJsonArray("matches") { result.matches.take(800).forEach { add(JsonPrimitive(it)) } }
                    }.toString(),
                )
            }
            "bridge_fs_grep" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val pattern = args.text("pattern") ?: return fail("pattern is required")
                val result = bridge.grep(
                    endpoint,
                    pattern,
                    root = args.text("root"),
                    include = args.text("include"),
                    exclude = args.text("exclude"),
                    literal = args.flag("literal") ?: false,
                    caseSensitive = args.flag("case_sensitive") ?: false,
                    context = args.number("context"),
                    limit = args.number("limit"),
                )
                ok(
                    "${result.matches.size} matches on ${endpoint.name}" +
                        if (result.truncated) " (truncated)" else "",
                    buildJsonObject {
                        put("device", endpoint.name)
                        put("device_id", endpoint.id)
                        put("pattern", pattern)
                        put("root", result.root)
                        put("truncated", result.truncated)
                        putJsonArray("matches") {
                            result.matches.take(300).forEach { match ->
                                add(
                                    buildJsonObject {
                                        put("path", match.path)
                                        put("line", match.line)
                                        put("column", match.column)
                                        put("text", match.text)
                                        if (match.before.isNotEmpty()) {
                                            putJsonArray("before") { match.before.forEach { add(JsonPrimitive(it)) } }
                                        }
                                        if (match.after.isNotEmpty()) {
                                            putJsonArray("after") { match.after.forEach { add(JsonPrimitive(it)) } }
                                        }
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "bridge_fs_write" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val path = args.text("path") ?: return fail("path is required")
                val content = args.text("content") ?: return fail("content is required")
                bridge.write(endpoint, path, content)
                ok(
                    "Wrote $path on ${endpoint.name}",
                    "File $path written on ${endpoint.name} (${content.length} characters).",
                )
            }
            "bridge_fs_edit" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val path = args.text("path") ?: return fail("path is required")
                val old = args.text("old") ?: return fail("old is required")
                val new = args.text("new") ?: return fail("new is required")
                bridge.edit(endpoint, path, old, new, args.flag("replace_all") ?: false)
                ok("Edited $path", "Replaced snippet in $path on ${endpoint.name}.")
            }
            "bridge_fs_delete" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val path = args.text("path") ?: return fail("path is required")
                bridge.remove(endpoint, path)
                ok("Deleted $path", "Path $path deleted on ${endpoint.name}.")
            }
            "bridge_set_workspace" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val roots = (args["roots"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
                val applied = bridge.setWorkspace(endpoint, roots)
                ok(
                    if (applied.isEmpty()) "Workspace unlimited" else "Workspace: ${applied.joinToString(", ")}",
                    "Workspace roots updated on ${endpoint.name}.",
                )
            }
            "todowrite" -> {
                if (conversationId.isBlank()) return fail("No active conversation")
                val array = args["todos"] as? JsonArray ?: return fail("todos must be an array")
                val parsed = array.mapIndexedNotNull { index, element ->
                    val obj = element as? JsonObject ?: return@mapIndexedNotNull null
                    val content = obj.text("content")?.trim().orEmpty()
                    if (content.isBlank()) return@mapIndexedNotNull null
                    ConversationTodo(
                        conversationId = conversationId,
                        content = content.take(300),
                        status = TodoStatus.fromWire(obj.text("status").orEmpty()),
                        priority = TodoPriority.fromWire(obj.text("priority").orEmpty()),
                        position = index,
                    )
                }
                if (parsed.isEmpty()) return fail("todos needs at least one item with content")
                if (parsed.count { it.status == TodoStatus.InProgress } > 1) {
                    return fail("Only one todo may be in_progress at a time")
                }
                chats.replaceTodos(conversationId, parsed)
                val stored = chats.todos(conversationId)
                val done = stored.count { it.status == TodoStatus.Completed }
                ok(
                    "$done/${stored.size} todos done",
                    buildJsonObject {
                        putJsonArray("todos") {
                            stored.forEach { todo ->
                                add(
                                    buildJsonObject {
                                        put("id", todo.id)
                                        put("content", todo.content)
                                        put("status", todo.status.wire)
                                        put("priority", todo.priority.wire)
                                    },
                                )
                            }
                        }
                    }.toString(),
                ).copy(todos = stored)
            }
            "todoread" -> {
                if (conversationId.isBlank()) return fail("No active conversation")
                val stored = chats.todos(conversationId)
                ok(
                    "${stored.size} todos",
                    buildJsonObject {
                        putJsonArray("todos") {
                            stored.forEach { todo ->
                                add(
                                    buildJsonObject {
                                        put("id", todo.id)
                                        put("content", todo.content)
                                        put("status", todo.status.wire)
                                        put("priority", todo.priority.wire)
                                    },
                                )
                            }
                        }
                    }.toString(),
                ).copy(todos = stored)
            }
            "web_search" -> {
                val query = args.text("query") ?: return fail("query is required")
                val count = (args.number("count") ?: 5).coerceIn(1, 10)
                val provider = args.text("provider")?.let {
                    WebSearchProvider.fromId(it) ?: return fail("provider must be one of ${WebSearchProvider.entries.joinToString { p -> p.id }}")
                }
                val results = webSearch.search(
                    query = query,
                    count = count,
                    provider = provider,
                    language = args.text("language").orEmpty(),
                    timeRange = args.text("time_range").orEmpty(),
                )
                ok(
                    "${results.size} results",
                    buildJsonObject {
                        put("query", query)
                        put("provider", (provider ?: webSearchProvider()).id)
                        putJsonArray("results") {
                            results.forEach { result ->
                                add(
                                    buildJsonObject {
                                        put("title", result.title)
                                        put("url", result.url)
                                        put("snippet", result.snippet.take(1000))
                                        put("source", result.source)
                                        if (result.published.isNotBlank()) put("published", result.published)
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "mcp_list_tools" -> {
                val server = mcpServer(args) ?: return fail("server is required")
                val tools = mcp.listTools(server)
                ok(
                    "${tools.size} tools on ${server.name}",
                    buildJsonObject {
                        put("server", server.name)
                        putJsonArray("tools") {
                            tools.forEach { tool ->
                                add(
                                    buildJsonObject {
                                        put("name", tool.name)
                                        put("title", tool.title)
                                        put("description", tool.description)
                                        put("input_schema", tool.inputSchema.toString())
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "mcp_call_tool" -> {
                val server = mcpServer(args) ?: return fail("server is required")
                val tool = args.text("tool") ?: return fail("tool is required")
                val arguments = args.jsonObject("arguments") ?: JsonObject(emptyMap())
                val result = mcp.callTool(server, tool, arguments)
                if (result.isError) {
                    fail("MCP ${server.name} $tool: ${result.readable()}")
                } else {
                    ok("${server.name}: $tool", result.readable())
                }
            }
            "mcp_list_resources" -> {
                val server = mcpServer(args) ?: return fail("server is required")
                val resources = mcp.listResources(server)
                ok(
                    "${resources.size} resources on ${server.name}",
                    buildJsonObject {
                        put("server", server.name)
                        putJsonArray("resources") {
                            resources.forEach { resource ->
                                add(
                                    buildJsonObject {
                                        put("uri", resource.uri)
                                        put("name", resource.name)
                                        put("description", resource.description)
                                        put("mime_type", resource.mimeType)
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "mcp_read_resource" -> {
                val server = mcpServer(args) ?: return fail("server is required")
                val uri = args.text("uri") ?: return fail("uri is required")
                val content = mcp.readResource(server, uri)
                ok("Read $uri on ${server.name}", content.take(20_000))
            }
            "index_workspace" -> {
                val result = indexer.index(force = args.flag("force") ?: false)
                ok(
                    "Indexed ${result.indexed} files under ${result.root}" +
                        if (result.truncated) " (truncated)" else "",
                    buildJsonObject {
                        put("root", result.root)
                        put("indexed", result.indexed)
                        put("skipped", result.skipped)
                        put("truncated", result.truncated)
                    }.toString(),
                )
            }
            "search_files" -> {
                val query = args.text("query") ?: return fail("query is required")
                val hits = indexer.search(
                    query = query,
                    regex = args.flag("regex") ?: false,
                    glob = args.text("glob").orEmpty(),
                    caseSensitive = args.flag("case_sensitive") ?: false,
                    limit = (args.number("limit") ?: 80).coerceIn(1, 200),
                )
                ok(
                    "${hits.size} matches",
                    buildJsonObject {
                        putJsonArray("matches") {
                            hits.forEach { hit ->
                                add(
                                    buildJsonObject {
                                        put("path", hit.path)
                                        put("line", hit.line)
                                        put("text", hit.text)
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "workspace_files" -> {
                val files = indexer.files(
                    glob = args.text("glob").orEmpty(),
                    limit = (args.number("limit") ?: 200).coerceIn(1, 1000),
                )
                ok(
                    "${files.size} files",
                    buildJsonObject {
                        putJsonArray("files") { files.forEach { add(JsonPrimitive(it)) } }
                    }.toString(),
                )
            }
            "bridge_bg_start" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val command = args.text("command") ?: return fail("command is required")
                val job = bridge.backgroundStart(
                    endpoint = endpoint,
                    command = command,
                    cwd = args.text("cwd"),
                    timeoutSec = (args.number("timeout_sec") ?: 600).toLong().coerceIn(1, 86_400),
                )
                backgroundWatch?.register(conversationId, endpoint, job.id)
                ok(
                    "Started ${job.id} on ${endpoint.name}",
                    buildJsonObject {
                        put("id", job.id)
                        put("command", job.command)
                        put("pid", job.pid)
                        put("status", job.status)
                        put("device", endpoint.name)
                    }.toString(),
                )
            }
            "bridge_bg_logs" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val id = args.text("id") ?: return fail("id is required")
                var lines = emptyList<fan.x0.focus.data.bridge.BridgeBgLogLine>()
                var done = false
                bridge.backgroundLogs(
                    endpoint = endpoint,
                    id = id,
                    after = (args.number("after") ?: 0).toLong(),
                    follow = false,
                ) { frame ->
                    lines = frame.lines
                    done = frame.done
                }
                ok(
                    "${lines.size} log lines${if (done) ", finished" else ""}",
                    buildJsonObject {
                        put("id", id)
                        put("done", done)
                        putJsonArray("lines") {
                            lines.forEach { line ->
                                add(
                                    buildJsonObject {
                                        put("seq", line.seq)
                                        put("stream", line.stream)
                                        put("data", line.data)
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "bridge_bg_kill" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val id = args.text("id") ?: return fail("id is required")
                val killed = bridge.backgroundKill(endpoint, id)
                if (killed) ok("Stopped $id", "Background command $id stopped.") else fail("Command not found")
            }
            "bridge_bg_list" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                val jobs = bridge.backgroundList(endpoint)
                ok(
                    "${jobs.size} background commands on ${endpoint.name}",
                    buildJsonObject {
                        putJsonArray("jobs") {
                            jobs.forEach { job ->
                                add(
                                    buildJsonObject {
                                        put("id", job.id)
                                        put("command", job.command)
                                        put("status", job.status)
                                        put("exit_code", job.exitCode)
                                        put("timed_out", job.timedOut)
                                    },
                                )
                            }
                        }
                    }.toString(),
                )
            }
            "bridge_bg_clear" -> {
                val endpoint = bridgeTarget(args) ?: return fail(bridgeTargetError(args))
                bridge.backgroundClear(endpoint, args.text("id"))
                ok("Cleared background commands", "Background command logs cleared.")
            }
            "spawn_agent" -> fail("The subagent runner is only available from a chat turn")
            else -> fail("Unsupported tool: ${spec.name}")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SourceException) {
        fail("${e.kind.name}: ${e.message}", "Tool failed")
    } catch (e: Exception) {
        fail("Tool failed: ${e.message ?: e.javaClass.simpleName}", "Tool failed")
    }

    private sealed interface Draft {
        val title: String
        val summary: String
        val label: String
    }

    private data class SimpleDraft(
        override val title: String,
        override val summary: String,
        override val label: String,
    ) : Draft

    private suspend fun gatedDraft(spec: ToolSpec, args: JsonObject): Draft? = when (spec.name) {
        "create_calendar_event" -> {
            val title = args.text("title") ?: return null
            val start = ToolTime.parse(args.text("start")) ?: return null
            val end = ToolTime.parse(args.text("end")) ?: return null
            if (end <= start) return null
            SimpleDraft(
                title = "Create calendar event",
                summary = "$title, ${ToolTime.full(start)} to ${ToolTime.full(end)}",
                label = "Create event: $title",
            )
        }
        "delete_calendar_event" -> {
            val id = args.text("event_id")?.toLongOrNull() ?: return null
            val title = calendar.eventTitle(id)
            SimpleDraft("Delete calendar event", title ?: "Event $id", "Delete event: ${title ?: id}")
        }
        "delete_article" -> {
            val id = args.text("article_id") ?: return null
            val article = feeds.getArticle(id)
            SimpleDraft("Delete article", article?.title ?: id, "Delete article: ${article?.title ?: id}")
        }
        "remove_feed" -> {
            val id = args.text("feed_id") ?: return null
            val feed = feeds.getFeed(id)
            SimpleDraft("Unsubscribe feed", feed?.title ?: id, "Remove feed: ${feed?.title ?: id}")
        }
        "delete_goal" -> {
            val id = args.text("goal_id") ?: return null
            val goal = goals.get(id)
            SimpleDraft("Delete goal", goal?.title ?: id, "Delete goal: ${goal?.title ?: id}")
        }
        "delete_update" -> {
            val id = args.text("update_id") ?: return null
            val update = today.update(id)
            SimpleDraft("Delete card", update?.headline ?: id, "Delete card: ${update?.headline ?: id}")
        }
        "delete_idea" -> {
            val id = args.text("idea_id") ?: return null
            val idea = today.idea(id)
            SimpleDraft("Delete idea", idea?.title ?: id, "Delete idea: ${idea?.title ?: id}")
        }
        "delete_file" -> {
            val uri = args.text("uri") ?: return null
            val name = runCatching { files.list().firstOrNull { it.uri == uri }?.name }.getOrNull()
            SimpleDraft("Delete file", name ?: uri, "Delete file: ${name ?: uri}")
        }
        "bridge_run_command" -> {
            val command = args.text("command") ?: return null
            val cwd = args.text("cwd")
            val device = bridgeTarget(args)?.name
            SimpleDraft(
                title = "Run command on computer",
                summary = buildString {
                    append(command.take(400))
                    if (!cwd.isNullOrBlank()) append("\nin ").append(cwd)
                    if (!device.isNullOrBlank()) append("\non ").append(device)
                },
                label = "Run: ${command.take(80)}",
            )
        }
        "bridge_session_open" -> {
            val endpoint = bridgeTarget(args)
            val shell = args.text("shell") ?: endpoint?.let { "default shell" } ?: "shell"
            SimpleDraft(
                title = "Open shell session",
                summary = "Open $shell on ${endpoint?.name ?: "the paired computer"}",
                label = "Open session: $shell",
            )
        }
        "bridge_session_send" -> {
            val sessionId = args.text("session_id") ?: return null
            val input = args.text("input")
            val signal = args.text("signal")
            if (input.isNullOrBlank() && signal.isNullOrBlank()) return null
            val detail = if (signal != null) "signal $signal" else input.orEmpty().take(400)
            val device = bridgeTarget(args)?.name
            SimpleDraft(
                title = "Send input to session",
                summary = "Session $sessionId${device?.let { " on $it" }.orEmpty()}\n$detail",
                label = "Session $sessionId: ${if (signal != null) signal else input.orEmpty().take(60)}",
            )
        }
        "bridge_close_session" -> {
            val sessionId = args.text("session_id") ?: return null
            val device = bridgeTarget(args)?.name
            SimpleDraft(
                "Close session",
                "Close session $sessionId${device?.let { " on $it" }.orEmpty()}",
                "Close session $sessionId",
            )
        }
        "bridge_fs_write" -> {
            val path = args.text("path") ?: return null
            val content = args.text("content")
            val device = bridgeTarget(args)?.name
            SimpleDraft(
                title = "Write file on computer",
                summary = "$path\n${content?.length ?: 0} characters" +
                    device?.let { "\non $it" }.orEmpty(),
                label = "Write: $path",
            )
        }
        "bridge_fs_edit" -> {
            val path = args.text("path") ?: return null
            val device = bridgeTarget(args)?.name
            SimpleDraft(
                title = "Edit file on computer",
                summary = "$path\nreplacing ${args.text("old")?.length ?: 0} characters" +
                    device?.let { "\non $it" }.orEmpty(),
                label = "Edit: $path",
            )
        }
        "bridge_fs_delete" -> {
            val path = args.text("path") ?: return null
            val device = bridgeTarget(args)?.name
            SimpleDraft(
                "Delete on computer",
                "$path${device?.let { " on $it" }.orEmpty()}",
                "Delete: $path",
            )
        }
        "bridge_set_workspace" -> {
            val roots = (args["roots"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val device = bridgeTarget(args)?.name
            SimpleDraft(
                title = "Set workspace folders",
                summary = (if (roots.isEmpty()) "No limit" else roots.joinToString("\n")) +
                    device?.let { "\non $it" }.orEmpty(),
                label = if (roots.isEmpty()) "Workspace: no limit" else "Workspace: ${roots.size} folders",
            )
        }
        else -> null
    }

    private fun askRequest(args: JsonObject): AskRequest? {
        val array = args["questions"] as? JsonArray ?: return null
        val used = mutableSetOf<String>()
        val questions = array.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val title = obj.text("title")?.oneLine()?.take(120).orEmpty()
            if (title.isBlank()) return@mapIndexedNotNull null
            val declared = askKindOf(obj.text("kind"))
            val options = askOptions(obj["options"], declared)
            val kind = if (options.isEmpty() && declared in choiceKinds) AskKind.Text else declared
            val requested = obj.text("id")?.oneLine()?.take(40).orEmpty().ifBlank { "q${index + 1}" }
            var id = requested
            var suffix = 2
            while (!used.add(id)) {
                id = "$requested-$suffix"
                suffix++
            }
            AskQuestion(
                id = id,
                title = title,
                subtitle = obj.text("subtitle")?.oneLine()?.take(160).orEmpty(),
                kind = kind,
                icon = obj.text("icon")?.trim()?.take(24).orEmpty(),
                options = options,
                allowOther = kind in choiceKinds && (obj.flag("allow_other") ?: false),
                placeholder = obj.text("placeholder")?.oneLine()?.take(60).orEmpty(),
            )
        }.take(5)
        if (questions.isEmpty()) return null
        return AskRequest(
            id = UUID.randomUUID().toString(),
            intro = args.text("intro")?.oneLine()?.take(120).orEmpty(),
            questions = questions,
        )
    }

    private fun askOptions(element: JsonElement?, kind: AskKind): List<AskOption> {
        val array = element as? JsonArray ?: return emptyList()
        val options = array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val label = obj.text("label")?.oneLine()?.take(60).orEmpty()
            if (label.isBlank()) return@mapNotNull null
            AskOption(
                label = label,
                detail = obj.text("detail")?.oneLine()?.take(120).orEmpty(),
                icon = obj.text("icon")?.trim()?.take(24).orEmpty(),
            )
        }
        return if (kind == AskKind.Confirm) options.take(2) else options.take(6)
    }

    private fun String.oneLine(): String = replace('\n', ' ').replace('\r', ' ').trim()

    private val choiceKinds = setOf(AskKind.Single, AskKind.Multi)

    private fun planSteps(args: JsonObject): List<ApprovalStep>? {
        val array = args["steps"] as? JsonArray ?: return null
        val steps = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val tool = obj.text("tool") ?: return@mapNotNull null
            val label = obj.text("label") ?: tool
            val arguments = when (val raw = obj["arguments"]) {
                is JsonPrimitive -> raw.content
                is JsonObject -> raw.toString()
                else -> null
            } ?: return@mapNotNull null
            val spec = ToolCatalog.find(tool) ?: return@mapNotNull null
            if (spec.kind == ToolKind.Plan || spec.kind == ToolKind.Ask ||
                tool == ToolCatalog.SET_WORKING_TEXT
            ) {
                return@mapNotNull null
            }
            if (parseObject(arguments) == null) return@mapNotNull null
            ApprovalStep(tool, arguments, label.take(80))
        }
        return steps.take(8).ifEmpty { null }
    }

    private fun parseObject(raw: String): JsonObject? {
        if (raw.isBlank()) return JsonObject(emptyMap())
        return runCatching { Json.parseToJsonElement(raw) as JsonObject }.getOrNull()
    }

    private fun ok(summary: String, content: String): ToolResult =
        ToolResult(ok = true, content = content, summary = summary)

    private fun fail(message: String, summary: String = message): ToolResult =
        ToolResult(ok = false, content = "Error: $message", summary = summary)

    private fun eventsJson(events: List<fan.x0.focus.data.sources.CalendarEvent>): String = buildJsonObject {
        put("count", events.size)
        putJsonArray("events") {
            events.forEach { event ->
                add(
                    buildJsonObject {
                        put("id", event.id)
                        put("title", event.title)
                        put("start", ToolTime.iso(event.start))
                        put("end", ToolTime.iso(event.end))
                        put("all_day", event.allDay)
                        if (!event.location.isNullOrBlank()) put("location", event.location)
                    },
                )
            }
        }
    }.toString()

    private fun feedsJson(list: List<Feed>): String = buildJsonObject {
        putJsonArray("feeds") {
            list.forEach { feed ->
                add(
                    buildJsonObject {
                        put("id", feed.id)
                        put("title", feed.title)
                        put("url", feed.url)
                        put("last_error", feed.error.orEmpty())
                    },
                )
            }
        }
    }.toString()

    private fun articlesJson(list: List<Article>): String = buildJsonObject {
        putJsonArray("articles") {
            list.forEach { article ->
                add(
                    buildJsonObject {
                        put("id", article.id)
                        put("title", article.title)
                        put("feed", article.feedTitle)
                        put("published", article.publishedAt?.let { ToolTime.full(it) } ?: "")
                        put("read", article.read)
                        put("summary", article.summary.orEmpty().take(300))
                    },
                )
            }
        }
    }.toString()

    private fun goalsJson(list: List<Goal>): String = buildJsonObject {
        putJsonArray("goals") {
            list.forEach { goal ->
                add(
                    buildJsonObject {
                        put("id", goal.id)
                        put("title", goal.title)
                        put("status", goal.status.name.lowercase())
                        put("progress", goal.progress)
                        put("due", goal.dueAt?.let { ToolTime.full(it) } ?: "")
                        put("note", goal.note.orEmpty())
                        put("category", goal.category)
                        put("icon", goal.icon)
                    },
                )
            }
        }
    }.toString()

    private suspend fun bridgeTarget(args: JsonObject): BridgeEndpoint? {
        val deviceId = args.text("device_id")
        if (deviceId.isNullOrBlank()) return bridge.activeEndpoint()
        return bridge.endpointsOnce().firstOrNull { it.id == deviceId }
    }

    private suspend fun webSearchProvider(): WebSearchProvider = webSearch.defaultProvider()

    private suspend fun mcpServer(args: JsonObject): McpServerConfig? {
        val requested = args.text("server")
        val servers = mcp.enabledServers()
        if (servers.isEmpty()) return null
        if (requested.isNullOrBlank()) return servers.singleOrNull()
        return servers.firstOrNull {
            it.id == requested || it.name.equals(requested, ignoreCase = true)
        }
    }

    private fun JsonObject.jsonObject(key: String): JsonObject? = when (val value = this[key]) {
        is JsonObject -> value
        is JsonPrimitive -> parseObject(value.content)
        else -> null
    }

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { value -> value.isNotBlank() } }
            .orEmpty()

    private fun bridgeTargetError(args: JsonObject): String {
        val deviceId = args.text("device_id")
        return if (deviceId.isNullOrBlank()) {
            "No paired computer is active"
        } else {
            "No paired computer with id $deviceId, run bridge_list_devices"
        }
    }

    private fun JsonObject.text(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.content.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.workspaceFile(): WorkspaceFile? =
        text("file")?.let { WorkspaceFile.fromKey(it) }

    private fun JsonObject.number(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonObject.flag(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    private fun protocolOf(value: String?): ChatProtocol? = when (value?.trim()?.lowercase()) {
        "chat", "chat_completions", "chatcompletions", "openai" -> ChatProtocol.ChatCompletions
        "responses", "response" -> ChatProtocol.Responses
        "anthropic", "messages", "anthropic_messages" -> ChatProtocol.AnthropicMessages
        else -> null
    }

    private fun providerName(baseUrl: String): String =
        runCatching { java.net.URI(baseUrl).host }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "Provider"

    private fun JsonObject.steps(key: String): List<IdeaStep> {
        val array = this[key] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val title = item.text("title") ?: return@mapNotNull null
            IdeaStep(title = title, detail = item.text("detail").orEmpty())
        }
    }
}
