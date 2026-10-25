package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.CompletedToolCall
import fan.x0.focus.core.network.openai.ConnectionTestException
import fan.x0.focus.core.network.openai.ModelConnectionTester
import fan.x0.focus.core.network.openai.ModelEndpoint
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
    val workingText: String? = null,
    val completesOnboarding: Boolean = false,
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
) {
    val specs: List<ToolSpec> = ToolCatalog.chat()

    suspend fun execute(call: CompletedToolCall, conversationId: String = ""): ToolResult =
        dispatch(call.name, call.arguments, allowApproval = true, conversationId = conversationId)

    suspend fun executeDirect(name: String, arguments: String): ToolResult =
        dispatch(name, arguments, allowApproval = false, conversationId = "")

    suspend fun runStep(step: ApprovalStep, conversationId: String = ""): StepOutcome {
        val spec = ToolCatalog.find(step.tool)
        val args = parseObject(step.arguments)
        return when {
            spec == null || spec.kind == ToolKind.Plan -> StepOutcome(step.label, false, "Unknown step")
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
    ): ToolResult {
        val spec = ToolCatalog.find(name) ?: return fail("Unknown tool: $name")
        val args = parseObject(rawArguments) ?: return fail("Invalid arguments for $name")
        when (spec.kind) {
            ToolKind.Read, ToolKind.Write -> return runTool(spec, args, conversationId)
            ToolKind.Gated -> {
                if (!allowApproval) return fail("$name needs approval and is not allowed here")
                val draft = gatedDraft(spec, args) ?: return fail("Missing or invalid fields for $name")
                val step = ApprovalStep(spec.name, args.toString(), draft.label)
                val request = approvals.create(draft.title, draft.summary, listOf(step))
                return ToolResult(
                    ok = true,
                    content = "Waiting for the user to approve: ${draft.title}",
                    summary = draft.label,
                    approval = request,
                )
            }
            ToolKind.Plan -> {
                if (!allowApproval) return fail("Plans are not allowed here")
                val steps = planSteps(args) ?: return fail("Plan needs 1 to 8 valid steps")
                val title = args.text("title")?.take(80) ?: "Plan"
                val summary = args.text("summary").orEmpty()
                val request = approvals.create(title, summary, steps)
                return ToolResult(
                    ok = true,
                    content = "Waiting for the user to approve the plan: $title",
                    summary = title,
                    approval = request,
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
                settings.updateProvider(
                    ProviderConfig(
                        id = id,
                        name = name,
                        baseUrl = baseUrl,
                        model = model,
                        protocol = protocol,
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
        else -> null
    }

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
            if (spec.kind == ToolKind.Plan || tool == ToolCatalog.SET_WORKING_TEXT) return@mapNotNull null
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
