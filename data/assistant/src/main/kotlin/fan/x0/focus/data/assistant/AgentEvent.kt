package fan.x0.focus.data.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

enum class AgentEventKind {
    SideChatResult,
    BackgroundCommand,
    Reminder,
    DeviceTrigger,
    FeedUpdate,
    Scheduled,
    Webhook,
    ;

    val wire: String get() = name.lowercase()

    companion object {
        fun fromWire(value: String): AgentEventKind =
            entries.firstOrNull { it.wire == value.lowercase() } ?: Reminder
    }
}

enum class AgentEventSource {
    SideChat,
    Background,
    Reminder,
    Device,
    Feed,
    Schedule,
    Webhook,
    ;

    val wire: String get() = name.lowercase()

    val configurable: Boolean get() = this != SideChat

    companion object {
        fun fromWire(value: String): AgentEventSource =
            entries.firstOrNull { it.wire == value.lowercase() } ?: Reminder
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
    fun payloadObject(): JsonObject =
        runCatching { EVENT_JSON.parseToJsonElement(payload) as? JsonObject }.getOrNull()
            ?: JsonObject(emptyMap())

    fun text(): String = payloadObject()["text"].stringValue()

    fun note(): String = payloadObject()["note"].stringValue()

    fun sideChatId(): String = payloadObject()["side_chat_id"].stringValue()

    fun title(): String = payloadObject()["title"].stringValue()
        .ifBlank { payloadObject()["name"].stringValue() }

    fun summary(): String = payloadObject()["summary"].stringValue()

    fun failed(): Boolean = payloadObject()["failed"]?.stringValue()?.toBooleanStrictOrNull() == true

    fun conversationId(): String = payloadObject()["conversation_id"].stringValue()

    fun triggerTarget(): AgentTriggerTarget =
        AgentTriggerTarget.fromWire(payloadObject()["target"].stringValue())

    fun instruction(): String = when (kind) {
        AgentEventKind.SideChatResult -> summary()
        AgentEventKind.BackgroundCommand ->
            "后台命令完成：${text()}\n${payloadObject()["logs"].stringValue().take(2_000)}"
        AgentEventKind.Reminder -> "提醒：${text()}"
        AgentEventKind.DeviceTrigger -> "设备触发器 ${payloadObject()["label"].stringValue()}：${text()}"
        AgentEventKind.FeedUpdate ->
            "订阅源更新：${title()} 新增 ${payloadObject()["count"].stringValue()} 篇"
        AgentEventKind.Scheduled ->
            "定时任务 ${title()}：${text()}"
        AgentEventKind.Webhook -> buildString {
            append("Webhook ${title()}")
            val prompt = payloadObject()["prompt"].stringValue()
            if (prompt.isNotBlank()) append("：").append(prompt)
            val body = text()
            if (body.isNotBlank()) append("\n\nPayload:\n").append(body)
        }
    }

    companion object {
        private val EVENT_JSON = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        fun sideChatResult(
            mainId: String,
            sideChatId: String,
            title: String,
            summary: String,
            failed: Boolean,
            turnId: Long,
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.SideChat,
            kind = AgentEventKind.SideChatResult,
            target = mainId,
            payload = buildJsonObject {
                put("side_chat_id", sideChatId)
                put("title", title)
                put("summary", summary)
                put("failed", failed)
            }.toString(),
            dedupeKey = "side-result:$sideChatId:$turnId",
        )

        fun backgroundCommand(
            conversationId: String,
            jobId: String,
            summary: String,
            logs: String,
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.Background,
            kind = AgentEventKind.BackgroundCommand,
            target = conversationId,
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
            target = AgentTriggerTarget.Main.wire,
            payload = buildJsonObject {
                put("reminder_id", id)
                put("text", text)
            }.toString(),
            dedupeKey = "reminder:$id",
        )

        fun deviceTrigger(label: String, prompt: String, conversationId: String): AgentEvent = AgentEvent(
            source = AgentEventSource.Device,
            kind = AgentEventKind.DeviceTrigger,
            target = conversationId.ifBlank { AgentTriggerTarget.Main.wire },
            payload = buildJsonObject {
                put("label", label)
                put("text", prompt)
                put("conversation_id", conversationId)
            }.toString(),
        )

        fun feedUpdate(feedId: String, title: String, count: Int): AgentEvent = AgentEvent(
            source = AgentEventSource.Feed,
            kind = AgentEventKind.FeedUpdate,
            target = AgentTriggerTarget.Main.wire,
            payload = buildJsonObject {
                put("feed_id", feedId)
                put("title", title)
                put("count", count)
            }.toString(),
        )

        fun scheduled(
            triggerId: String,
            name: String,
            prompt: String,
            target: AgentTriggerTarget,
            runAt: Long,
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.Schedule,
            kind = AgentEventKind.Scheduled,
            target = target.wire,
            payload = buildJsonObject {
                put("trigger_id", triggerId)
                put("name", name)
                put("text", prompt)
                put("target", target.wire)
                put("run_at", runAt)
            }.toString(),
            dedupeKey = "schedule:$triggerId:$runAt",
        )

        fun webhook(
            triggerId: String,
            name: String,
            prompt: String,
            body: String,
            target: AgentTriggerTarget,
            dedupeKey: String = "",
        ): AgentEvent = AgentEvent(
            source = AgentEventSource.Webhook,
            kind = AgentEventKind.Webhook,
            target = target.wire,
            payload = buildJsonObject {
                put("trigger_id", triggerId)
                put("name", name)
                put("text", body)
                put("prompt", prompt)
                put("target", target.wire)
            }.toString(),
            dedupeKey = dedupeKey.takeIf { it.isNotBlank() },
        )
    }
}

internal fun JsonElement?.stringValue(): String = when (this) {
    is JsonPrimitive -> contentOrNull ?: content.toString()
    else -> ""
}
