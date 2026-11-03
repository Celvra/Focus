package fan.x0.focus.feature.chat

internal fun List<ChatEntry>.toActivities(): List<ChatEntry> {
    if (none { it is ChatEntry.Trace || it is ChatEntry.Assistant && it.reasoning.isNotBlank() }) {
        return this
    }
    val result = mutableListOf<ChatEntry>()
    var anchor: String? = null
    var reasoning = ""
    var running = false
    var reasoningMs = 0L
    var reasoningStartedAt = 0L
    val steps = mutableListOf<TraceStep>()
    val answers = mutableListOf<ChatEntry.Assistant>()

    fun flush() {
        if (reasoning.isNotBlank() || steps.isNotEmpty()) {
            result += ChatEntry.Activity(
                key = "activity-$anchor",
                reasoning = reasoning,
                running = running,
                reasoningMs = reasoningMs,
                reasoningStartedAt = reasoningStartedAt,
                steps = steps.toList(),
            )
        }
        result += answers
        anchor = null
        reasoning = ""
        running = false
        reasoningMs = 0
        reasoningStartedAt = 0
        steps.clear()
        answers.clear()
    }

    forEach { entry ->
        when (entry) {
            is ChatEntry.Assistant -> {
                if (anchor == null) anchor = entry.key
                if (entry.reasoning.isNotBlank()) {
                    if (reasoning.isNotEmpty()) reasoning += "\n\n"
                    reasoning += entry.reasoning
                }
                running = running || entry.live && entry.reasoningStartedAt > 0
                reasoningMs += entry.reasoningMs
                if (entry.reasoningStartedAt > 0) reasoningStartedAt = entry.reasoningStartedAt
                if (entry.text.isNotBlank()) answers += entry
            }
            is ChatEntry.Trace -> {
                if (anchor == null) anchor = entry.key
                steps += entry.steps
            }
            else -> {
                flush()
                result += entry
            }
        }
    }
    flush()
    return result
}
