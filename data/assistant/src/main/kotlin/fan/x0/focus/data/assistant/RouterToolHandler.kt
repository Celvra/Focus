package fan.x0.focus.data.assistant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import org.koin.core.annotation.Single

data class RouterTurnFlags(
    var silent: Boolean = false,
    var started: Int = 0,
    val attachments: List<ChatAttachment> = emptyList(),
    val preferredProfile: WorkerProfile? = null,
)

@Single
class RouterToolHandler(
    private val chats: ChatStore,
    private val sideChats: SideChatManager,
    private val roles: RoleModels,
    private val settings: AssistantSettings,
    private val main: MainChannelSession,
    private val memory: RouterMemoryStore,
    private val prompts: PromptSettings,
    private val triggers: AgentTriggerStore,
    private val triggerScheduler: AgentTriggerScheduler,
    private val webhook: AgentWebhookServer,
    private val shortcuts: AgentShortcutManager,
) {
    suspend fun handle(call: fan.x0.focus.core.network.openai.CompletedToolCall, flags: RouterTurnFlags): ToolResult? {
        val args = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(call.arguments) as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
        return when (call.name) {
            RouterToolCatalog.START_SIDE_CHAT -> start(args, flags)
            RouterToolCatalog.SET_PROMPT -> setPrompt(args)
            RouterToolCatalog.LIST_SIDE_CHATS -> list(args)
            RouterToolCatalog.READ_SIDE_CHAT -> read(args)
            RouterToolCatalog.STOP_SIDE_CHAT -> stop(args)
            RouterToolCatalog.RECALL -> recall(args)
            RouterToolCatalog.ASK -> ask(args, flags)
            RouterToolCatalog.STAY_SILENT -> {
                flags.silent = true
                ok("Silent turn.")
            }
            RouterToolCatalog.SET_WORKING_TEXT -> {
                val text = args.string("text").take(40)
                main.setWorkingText(text)
                ok(text)
            }
            RouterToolCatalog.READ_MEMORY -> ok(memory.read())
            RouterToolCatalog.WRITE_MEMORY -> {
                memory.write(args.string("content"))
                ok("Memory saved.")
            }
            RouterToolCatalog.LIST_ROLE_MODELS -> listRoleModels()
            RouterToolCatalog.SET_ROLE_MODEL -> setRoleModel(args)
            RouterToolCatalog.CLEAR_ROLE_MODEL -> clearRoleModel(args)
            RouterToolCatalog.SHOW_SIDE_CHAT_RESULT -> showResult(args)
            RouterToolCatalog.LIST_TRIGGERS -> listTriggers()
            RouterToolCatalog.CREATE_CRON_TRIGGER -> createCron(args)
            RouterToolCatalog.CREATE_WEBHOOK_TRIGGER -> createWebhook(args)
            RouterToolCatalog.SET_TRIGGER_ENABLED -> setTriggerEnabled(args)
            RouterToolCatalog.DELETE_TRIGGER -> deleteTrigger(args)
            RouterToolCatalog.CREATE_DESKTOP_SHORTCUT -> createShortcut(args)
            else -> null
        }
    }

    private suspend fun start(args: JsonObject, flags: RouterTurnFlags): ToolResult {
        val title = args.string("title").trim()
        val task = args.string("task").trim()
        if (title.isBlank() || task.isBlank()) return fail("title and task are required")
        val profile = args.string("profile").takeIf { it.isNotBlank() }
            ?.let { WorkerProfile.fromWire(it) }
            ?: flags.preferredProfile
            ?: WorkerProfile.Standard
        val conversation = sideChats.create(
            title = title,
            task = task,
            profile = profile,
            note = args.string("note"),
            threadKey = args.string("thread_key"),
            attachments = flags.attachments,
        )
        flags.started += 1
        return ok(
            "Side chat started: ${conversation.id} (${conversation.title}, ${profile.wire}). " +
                "It reports back when it finishes.",
        )
    }

    private suspend fun setPrompt(args: JsonObject): ToolResult {
        val target = args.string("target").trim().lowercase()
        val content = args.string("content").trim()
        when (target) {
            "main" -> prompts.setMain(content)
            "side" -> prompts.setSide(content)
            else -> return fail("target must be main or side")
        }
        return ok("Prompt updated.")
    }

    private suspend fun list(args: JsonObject): ToolResult {
        val filter = args.string("status").trim().lowercase()
        val limit = args.int("limit", 20).coerceIn(1, 50)
        val items = sideChats.list(limit = 50).filter { item ->
            when (filter) {
                "", "all" -> true
                "active" -> item.status.active
                "waiting" -> item.status == TaskStatus.Waiting
                "done" -> item.status == TaskStatus.Done
                "failed" -> item.status == TaskStatus.Failed || item.status == TaskStatus.Interrupted
                else -> item.status.wire == filter
            }
        }.take(limit)
        if (items.isEmpty()) return ok("No side chats.")
        return ok(
            items.joinToString("\n") { item ->
                "- ${item.id} | ${item.status.wire} | ${item.profile.wire} | ${item.title} | " +
                    item.latest.replace('\n', ' ').take(160)
            },
        )
    }

    private suspend fun read(args: JsonObject): ToolResult {
        val id = args.string("side_chat_id")
        if (id.isBlank()) return fail("side_chat_id is required")
        val limit = args.int("limit", 20).coerceIn(1, 60)
        return transcript(id, limit)
    }

    private suspend fun recall(args: JsonObject): ToolResult {
        val id = args.string("id")
        if (id.isBlank()) return fail("id is required")
        val conversationId = id.toLongOrNull()?.let { messageId ->
            chats.message(messageId)?.conversationId
        } ?: id
        val conversation = chats.conversation(conversationId)
            ?: return fail("No side chat or message found for $id")
        if (conversation.kind != ConversationKind.Side) return fail("$id is not a side chat")
        return transcript(conversationId, args.int("limit", 40).coerceIn(1, 80))
    }

    private suspend fun transcript(conversationId: String, limit: Int): ToolResult {
        val messages = chats.messages(conversationId, limit = limit)
        if (messages.isEmpty()) return ok("No messages.")
        val lines = messages.mapNotNull { message ->
            val body = message.content.trim().replace('\n', ' ').take(1_000)
            val tools = message.toolCalls.joinToString(", ") { it.name }.take(200)
            when {
                body.isBlank() && tools.isBlank() -> null
                tools.isBlank() -> "${message.role.name.lowercase()}: $body"
                body.isBlank() -> "${message.role.name.lowercase()} tools: $tools"
                else -> "${message.role.name.lowercase()}: $body [tools: $tools]"
            }
        }
        return ok(lines.joinToString("\n").take(12_000))
    }

    private suspend fun stop(args: JsonObject): ToolResult {
        val id = args.string("side_chat_id")
        if (id.isBlank()) return fail("side_chat_id is required")
        sideChats.stop(id, args.string("reason"))
        return ok("Stop requested for $id.")
    }

    private suspend fun ask(args: JsonObject, flags: RouterTurnFlags): ToolResult {
        val question = args.string("question").trim()
        if (question.isBlank()) return fail("question is required")
        val context = args.string("context").trim()
        main.appendQuestion(
            buildString {
                append(question)
                if (context.isNotBlank()) append("\n\n").append(context)
            },
        )
        flags.silent = true
        return ok("Question sent to the main conversation.")
    }

    private suspend fun listRoleModels(): ToolResult {
        val config = settings.configOnce()
        val assigned = roles.configsOnce()
        val lines = AgentRole.entries.map { role ->
            val entry = assigned[role]
            val provider = entry?.let { item -> config.providers.firstOrNull { it.id == item.providerId } }
            val value = if (entry == null || provider == null) {
                "unset"
            } else {
                "${provider.name} · ${entry.model}"
            }
            "- ${role.key}: $value"
        }
        return ok(lines.joinToString("\n"))
    }

    private suspend fun setRoleModel(args: JsonObject): ToolResult {
        val role = AgentRole.fromKey(args.string("role"))
            ?: return fail("Unknown role. Use list_role_models.")
        val providerId = args.string("provider_id")
        val model = args.string("model")
        val config = settings.configOnce()
        if (config.providers.none { it.id == providerId }) return fail("Unknown provider: $providerId")
        if (model.isBlank()) return fail("model is required")
        roles.set(role, providerId, model, args.string("reasoning").ifBlank { ReasoningLevel.Auto.name })
        return ok("${role.key} now uses $providerId · $model.")
    }

    private suspend fun clearRoleModel(args: JsonObject): ToolResult {
        val role = AgentRole.fromKey(args.string("role"))
            ?: return fail("Unknown role. Use list_role_models.")
        roles.clear(role)
        return ok("${role.key} follows its fallback chain again.")
    }

    private suspend fun showResult(args: JsonObject): ToolResult {
        val id = args.string("side_chat_id")
        if (id.isBlank()) return fail("side_chat_id is required")
        val conversation = chats.conversation(id) ?: return fail("No side chat: $id")
        if (conversation.kind != ConversationKind.Side) return fail("$id is not a side chat")
        sideChats.showResult(id, args.string("message"))
        return ok("Result card shown in the main conversation.")
    }

    private suspend fun listTriggers(): ToolResult {
        val items = triggers.list()
        if (items.isEmpty()) return ok("No automatic triggers.")
        return ok(
            items.joinToString("\n") { trigger ->
                val target = when (trigger.kind) {
                    AgentTriggerKind.Cron -> trigger.schedule
                    AgentTriggerKind.Webhook -> "${webhook.baseUrl()}${trigger.webhookPath}"
                }
                "- ${trigger.id} | ${if (trigger.enabled) "on" else "off"} | ${trigger.kind.wire} | " +
                    "${trigger.name} | $target"
            },
        )
    }

    private suspend fun createCron(args: JsonObject): ToolResult {
        val name = args.string("name").trim()
        val prompt = args.string("prompt").trim()
        val schedule = args.string("schedule").trim()
        if (name.isBlank() || prompt.isBlank() || schedule.isBlank()) {
            return fail("name, prompt, and schedule are required")
        }
        CronExpression.validate(schedule)?.let { return fail(it) }
        val trigger = triggers.create(
            name = name,
            kind = AgentTriggerKind.Cron,
            prompt = prompt,
            schedule = schedule,
            profile = WorkerProfile.fromWire(args.string("profile")),
        )
        triggerScheduler.sync(trigger)
        return ok("Cron trigger created: ${trigger.id}. Next run: ${trigger.nextRun}.")
    }

    private suspend fun createWebhook(args: JsonObject): ToolResult {
        val name = args.string("name").trim()
        val prompt = args.string("prompt").trim()
        if (name.isBlank() || prompt.isBlank()) return fail("name and prompt are required")
        val trigger = triggers.create(
            name = name,
            kind = AgentTriggerKind.Webhook,
            prompt = prompt,
            schedule = "",
            profile = WorkerProfile.fromWire(args.string("profile")),
        )
        return ok(
            "Webhook trigger created: ${trigger.id}. URL: ${webhook.baseUrl()}${trigger.webhookPath}",
        )
    }

    private suspend fun setTriggerEnabled(args: JsonObject): ToolResult {
        val id = args.string("trigger_id")
        val enabled = args.boolean("enabled") ?: return fail("enabled is required")
        val trigger = triggers.setEnabled(id, enabled) ?: return fail("Trigger not found: $id")
        if (enabled) triggerScheduler.sync(trigger) else triggerScheduler.cancel(trigger.id)
        return ok("${trigger.name} is now ${if (enabled) "on" else "off"}.")
    }

    private suspend fun deleteTrigger(args: JsonObject): ToolResult {
        val id = args.string("trigger_id")
        if (id.isBlank()) return fail("trigger_id is required")
        triggers.delete(id)
        triggerScheduler.cancel(id)
        return ok("Trigger deleted: $id.")
    }

    private fun createShortcut(args: JsonObject): ToolResult {
        val target = args.string("target").ifBlank { "overlay" }
        val label = args.string("label").ifBlank { "Focus" }
        val requested = shortcuts.pin(target, label)
        return if (requested) {
            ok("Shortcut requested. Confirm the pin in the system dialog.")
        } else {
            fail("This launcher does not allow pinning shortcuts.")
        }
    }

    private fun ok(content: String): ToolResult =
        ToolResult(ok = true, content = content, summary = content.lineSequence().firstOrNull().orEmpty().take(120))

    private fun fail(content: String): ToolResult =
        ToolResult(ok = false, content = content, summary = content.take(120))

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.content.orEmpty()

    private fun JsonObject.int(key: String, default: Int): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: default

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}
