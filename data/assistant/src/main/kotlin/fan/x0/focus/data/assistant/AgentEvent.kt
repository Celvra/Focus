package fan.x0.focus.data.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.builtins.ListSerializer

enum class AgentEventKind {
    UserMessage,
    SideChatResult,
    SideChatApproval,
    BackgroundCommand,
    Reminder,
    DeviceTrigger,
    FeedUpdate,
    WatchdogAlarm,
    Scheduled,
    Webhook,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String): AgentEventKind =
            entries.firstOrNull { it.wire == value.lowercase() } ?: UserMessage
    }
}

enum class AgentEventSource {
    User,
    SideChat,
    Background,
    Reminder,
    Device,
    Feed,
    Watchdog,
    Schedule,
    Webhook,
    System,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String): AgentEventSource =
            entries.firstOrNull { it.wire == value.lowercase() } ?: System
    }
}

enum class AgentEventStatus {
    Pending,
    Running,
    Done,
    Failed,
    Filtered,
    ;

    val wire: String get() = name.lowercase()

    val settled: Boolean get() = this == Done || this == Filtered

    companion object {
        fun fromWire(value: String): AgentEventStatus =
            entries.firstOrNull { it.wire == value.lowercase() } ?: Pending
    }
}

sealed interface AgentEventRoute {
    data object Router : AgentEventRoute
    data class Side(val conversationId: String) : AgentEventRoute
    data object Thread : AgentEventRoute

    val target: String
        get() = when (this) {
            Router -> ROUTER_TARGET
            is Side -> "side:$conversationId"
            Thread -> THREAD_TARGET
        }

    companion object {
        const val ROUTER_TARGET = "router"
        const val THREAD_TARGET = "thread"

        fun fromTarget(target: String): AgentEventRoute = when {
            target == ROUTER_TARGET -> Router
            target == THREAD_TARGET -> Thread
            target.startsWith("side:") -> Side(target.removePrefix("side:"))
            else -> Router
        }
    }
}

data class AgentEvent(
    val id: Long = 0,
    val source: AgentEventSource,
    val kind: AgentEventKind,
    val target: String,
    val threadKey: String = "",
    val payload: String = "{}",
    val dedupeKey: String? = null,
    val status: AgentEventStatus = AgentEventStatus.Pending,
    val attempts: Int = 0,
    val dueAt: Long = 0,
    val createdAt: Long = 0,
    val processedAt: Long? = null,
    val error: String? = null,
) {
    val route: AgentEventRoute get() = AgentEventRoute.fromTarget(target)
    val isUserMessage: Boolean get() = kind == AgentEventKind.UserMessage

    fun payloadObject(): JsonObject =
        runCatching { EVENT_JSON.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: JsonObject(emptyMap())

    fun text(): String = payloadObject()["text"].stringValue()

    fun note(): String = payloadObject()["note"].stringValue()

    fun sideChatId(): String = payloadObject()["side_chat_id"].stringValue()

    fun summary(): String = payloadObject()["summary"].stringValue()

    fun failed(): Boolean = payloadObject()["failed"]?.stringValue()?.toBooleanStrictOrNull() == true

    fun attachments(): List<ChatAttachment> {
        val raw = payloadObject()["attachments"].stringValue()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            EVENT_JSON.decodeFromString(ListSerializer(ChatAttachment.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    companion object {
        private val EVENT_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun userMessage(
            text: String,
            threadKey: String,
            messageId: Long,
            attachments: String = "",
            dedupeKey: String? = null,
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.User,
            kind = AgentEventKind.UserMessage,
            target = AgentEventRoute.ROUTER_TARGET,
            threadKey = threadKey,
            payload = buildJsonObject {
                put("text", text)
                put("message_id", messageId)
                if (attachments.isNotBlank()) put("attachments", attachments)
            }.toString(),
            dedupeKey = dedupeKey,
        )

        fun sideChatResult(
            sideChatId: String,
            title: String,
            summary: String,
            failed: Boolean,
            threadKey: String = "side:$sideChatId",
            attachments: List<ChatAttachment> = emptyList(),
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.SideChat,
            kind = AgentEventKind.SideChatResult,
            target = AgentEventRoute.ROUTER_TARGET,
            threadKey = threadKey,
            payload = buildJsonObject {
                put("side_chat_id", sideChatId)
                put("title", title)
                put("summary", summary)
                put("failed", failed)
                if (attachments.isNotEmpty()) {
                    put(
                        "attachments",
                        EVENT_JSON.encodeToString(ListSerializer(ChatAttachment.serializer()), attachments),
                    )
                }
            }.toString(),
        )

        fun sideChatApproval(
            sideChatId: String,
            title: String,
            note: String,
            threadKey: String = "side:$sideChatId",
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.SideChat,
            kind = AgentEventKind.SideChatApproval,
            target = AgentEventRoute.ROUTER_TARGET,
            threadKey = threadKey,
            payload = buildJsonObject {
                put("side_chat_id", sideChatId)
                put("title", title)
                put("note", note)
            }.toString(),
        )

        fun backgroundCommand(
            conversationId: String,
            jobId: String,
            summary: String,
            logs: String,
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.Background,
            kind = AgentEventKind.BackgroundCommand,
            target = AgentEventRoute.THREAD_TARGET,
            threadKey = "side:$conversationId",
            payload = buildJsonObject {
                put("conversation_id", conversationId)
                put("job_id", jobId)
                put("text", summary)
                put("logs", logs)
            }.toString(),
        )

        fun reminder(id: String, text: String): AgentEvent = AgentEvent(
            source = AgentEventSource.Reminder,
            kind = AgentEventKind.Reminder,
            target = AgentEventRoute.THREAD_TARGET,
            threadKey = "reminder:$id",
            payload = buildJsonObject {
                put("reminder_id", id)
                put("text", text)
            }.toString(),
            dedupeKey = "reminder:$id",
        )

        fun deviceTrigger(label: String, prompt: String, conversationId: String): AgentEvent = AgentEvent(
            source = AgentEventSource.Device,
            kind = AgentEventKind.DeviceTrigger,
            target = if (conversationId.isBlank()) {
                AgentEventRoute.ROUTER_TARGET
            } else {
                AgentEventRoute.Side(conversationId).target
            },
            threadKey = "device:$label",
            payload = buildJsonObject {
                put("label", label)
                put("text", prompt)
                put("conversation_id", conversationId)
            }.toString(),
        )

        fun feedUpdate(feedId: String, title: String, count: Int): AgentEvent = AgentEvent(
            source = AgentEventSource.Feed,
            kind = AgentEventKind.FeedUpdate,
            target = AgentEventRoute.THREAD_TARGET,
            threadKey = "feed:$feedId",
            payload = buildJsonObject {
                put("feed_id", feedId)
                put("title", title)
                put("count", count)
            }.toString(),
        )

        fun watchdogAlarm(
            conversationId: String,
            code: String,
            text: String,
            dedupeKey: String = "watchdog:$conversationId:$code",
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.Watchdog,
            kind = AgentEventKind.WatchdogAlarm,
            target = AgentEventRoute.ROUTER_TARGET,
            threadKey = "side:$conversationId",
            payload = buildJsonObject {
                put("conversation_id", conversationId)
                put("code", code)
                put("text", text)
            }.toString(),
            dedupeKey = dedupeKey,
        )

        fun scheduled(
            triggerId: String,
            name: String,
            prompt: String,
            profile: WorkerProfile,
            runAt: Long,
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.Schedule,
            kind = AgentEventKind.Scheduled,
            target = AgentEventRoute.ROUTER_TARGET,
            threadKey = "trigger:$triggerId",
            payload = buildJsonObject {
                put("trigger_id", triggerId)
                put("name", name)
                put("text", prompt)
                put("profile", profile.wire)
                put("run_at", runAt)
            }.toString(),
            dedupeKey = "schedule:$triggerId:$runAt",
        )

        fun webhook(
            triggerId: String,
            name: String,
            prompt: String,
            body: String,
            profile: WorkerProfile,
            dedupeKey: String = "",
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.Webhook,
            kind = AgentEventKind.Webhook,
            target = AgentEventRoute.ROUTER_TARGET,
            threadKey = "trigger:$triggerId",
            payload = buildJsonObject {
                put("trigger_id", triggerId)
                put("name", name)
                put("text", body)
                put("prompt", prompt)
                put("profile", profile.wire)
            }.toString(),
            dedupeKey = dedupeKey.takeIf { it.isNotBlank() },
        )
    }
}

internal fun JsonElement?.stringValue(): String = when (this) {
    is JsonPrimitive -> contentOrNull ?: content.toString()
    else -> ""
}
