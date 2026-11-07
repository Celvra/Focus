package fan.x0.focus.data.assistant

import org.koin.core.annotation.Single
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Single
class ContextAssembler(
    private val chats: ChatStore,
    private val sideChats: SideChatManager,
    private val events: AgentEventStore,
    private val eventSettings: AgentEventSettings,
    private val memory: RouterMemoryStore,
    private val workspace: WorkspaceStore,
) {
    suspend fun build(batch: List<AgentEvent>): String {
        val config = eventSettings.configOnce()
        val name = workspace.assistantName()
        val userName = workspace.userName().orEmpty()
        val mainMessages = chats.messages(ChatStore.MAIN_CONVERSATION_ID, limit = 12)
        val sides = sideChats.list(limit = 12)
        val recent = events.recent(limit = 12)
        val note = memory.read()
        val builder = StringBuilder()
        builder.appendLine("You are $name, the router of a personal agent system on the user's Android phone.")
        builder.appendLine("The main conversation is your only human channel. Side chats do the work. You never use shell, files, devices, or apps directly.")
        builder.appendLine("Rules:")
        builder.appendLine("- Reply directly only for simple conversation, questions you can answer without tools, or decisions about routing.")
        builder.appendLine("- For any work that needs tools, files, devices, apps, long steps, or parallel effort, call start_side_chat with a complete, self-contained task.")
        builder.appendLine("- Attachments in the current event are forwarded to a new side chat automatically. " +
            "If an event carries an image or file and the user asks about it, start a side chat instead of guessing.")
        builder.appendLine("- Files and images produced by a side chat are returned with its result. " +
            "Call show_side_chat_result so the user can open them.")
        builder.appendLine("- Start one side chat per independent task. Several can run in parallel. Do not start a side chat for simple chat.")
        builder.appendLine("- When you start work, tell the user in one short line what is running. Do not claim the work is done.")
        builder.appendLine("- Side chat results arrive as events. If the user can use or act on a result, call show_side_chat_result. Otherwise call stay_silent. Never repeat a full side chat trajectory in the main conversation.")
        builder.appendLine("- Failures already show a card in the main conversation. Add at most one short sentence when it helps.")
        builder.appendLine("- If you need a decision or a missing detail, call ask with one concrete question. Do not guess.")
        builder.appendLine("- Before slow work call set_working_text with a short line in the user's language.")
        builder.appendLine("- Never expose tool names, ids, JSON, or internal status codes to the user.")
        builder.appendLine("- Use recall or read_side_chat when a result needs detail. Do not ask the user to read a side chat for you.")
        builder.appendLine("- Memory is yours. Use read_memory and write_memory for durable routing preferences and standing rules.")
        builder.appendLine("- Change role models only when the user explicitly asks. Use list_role_models first.")
        builder.appendLine("- Pass a thread_key to start_side_chat when the event carries one and the work continues that thread.")
        builder.appendLine("- User messages always go through you. Context events may go straight to a subscribed side chat.")
        builder.appendLine("- Sources marked speculative must not produce a message just because they fired. " +
            "Check whether anything useful or actionable exists. If not, stay silent. " +
            "If it needs checking, start a light side chat and stay silent until its result.")
        builder.appendLine("- Stay silent when nothing needs the user. Quiet is the default.")
        builder.appendLine("- ${language()}")
        if (userName.isNotBlank()) builder.appendLine("- The user's name is $userName.")
        builder.appendLine("- Current time: ${time()}.")
        builder.appendLine()
        builder.appendLine("Sources:")
        AgentEventSource.entries.forEach { source ->
            val speculative = if (config.speculative(source)) "speculative" else "normal"
            builder.appendLine("- ${source.wire}: ${if (config.enabled(source)) "on" else "off"}, $speculative")
        }
        builder.appendLine()
        builder.appendLine("Recent main conversation:")
        if (mainMessages.isEmpty()) {
            builder.appendLine("(empty)")
        } else {
            mainMessages.forEach { message ->
                val who = when (message.role) {
                    ChatRole.User -> "User"
                    ChatRole.Assistant -> "Assistant"
                    ChatRole.System -> "App"
                    ChatRole.Tool -> "Tool"
                }
                val body = if (message.label == MainChannelSession.SIDE_RESULT_LABEL) {
                    val card = runCatching {
                        kotlinx.serialization.json.Json
                            .decodeFromString(SideResultCard.serializer(), message.content)
                    }.getOrNull()
                    if (card == null) {
                        ""
                    } else {
                        "Side chat result: ${card.title} (${if (card.failed) "failed" else "done"}): ${card.summary}"
                    }
                } else {
                    message.content.trim().replace('\n', ' ')
                }.take(500)
                if (body.isNotBlank()) builder.appendLine("$who: $body")
            }
        }
        builder.appendLine()
        builder.appendLine("Side chats:")
        if (sides.isEmpty()) {
            builder.appendLine("(none)")
        } else {
            sides.forEach { side ->
                builder.appendLine(
                    "- ${side.id} | ${side.status.wire} | ${side.profile.wire} | ${side.title} | " +
                        side.latest.replace('\n', ' ').take(200),
                )
            }
        }
        builder.appendLine()
        builder.appendLine("Recent events:")
        if (recent.isEmpty()) {
            builder.appendLine("(none)")
        } else {
            recent.forEach { event ->
                builder.appendLine("- ${event.kind.wire} / ${event.source.wire}: ${describe(event).replace('\n', ' ').take(300)}")
            }
        }
        builder.appendLine()
        builder.appendLine("Router memory:")
        builder.appendLine(note.trim().ifBlank { "(empty)" })
        builder.appendLine()
        builder.appendLine("Events in this turn:")
        batch.forEach { event ->
            builder.appendLine("--- ${event.kind.wire} / ${event.source.wire} / thread ${event.threadKey.ifBlank { "-" }}")
            builder.appendLine(describe(event))
        }
        return builder.toString()
    }

    fun describe(event: AgentEvent): String = when (event.kind) {
        AgentEventKind.UserMessage -> buildString {
            append("User: ")
            append(event.text().ifBlank { "(empty message)" })
            val names = event.attachments().joinToString(", ") { it.name }
            if (names.isNotBlank()) append("\nAttachments: ").append(names)
        }
        AgentEventKind.SideChatResult -> buildString {
            append("Side chat ${event.sideChatId()} (${event.payloadObject()["title"].stringValue()}) ")
            if (event.failed()) append("failed: ${event.summary()}") else append("finished: ${event.summary()}")
            val names = event.attachments().joinToString(", ") { it.name }
            if (names.isNotBlank()) append("\nAttachments: ").append(names)
        }
        AgentEventKind.SideChatApproval -> "Side chat ${event.sideChatId()} " +
            "(${event.payloadObject()["title"].stringValue()}) waits for approval: ${event.note()}"
        AgentEventKind.BackgroundCommand ->
            "Background command finished: ${event.text()}\n${event.payloadObject()["logs"].stringValue().take(1_200)}"
        AgentEventKind.Reminder -> "Reminder: ${event.text()}"
        AgentEventKind.DeviceTrigger -> "Device trigger ${event.payloadObject()["label"].stringValue()}: ${event.text()}"
        AgentEventKind.FeedUpdate -> "Feed update: ${event.payloadObject()["title"].stringValue()} " +
            "(${event.payloadObject()["count"].stringValue()} new)"
        AgentEventKind.WatchdogAlarm -> "Watchdog: ${event.text()}"
    }

    private suspend fun language(): String {
        val tag = workspace.userLanguage() ?: Locale.getDefault().language
        return when {
            tag.startsWith("zh") -> "Reply in Simplified Chinese."
            tag.startsWith("en") -> "Reply in English."
            else -> "Reply in the user's language."
        }
    }

    private fun time(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
}
