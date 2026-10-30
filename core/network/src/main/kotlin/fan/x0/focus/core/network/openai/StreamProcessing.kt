package fan.x0.focus.core.network.openai

// Incremental SSE decoder: returns a data payload for each complete event.
class SseDecoder {
    private val buffer = StringBuilder()

    fun onLine(line: String): String? {
        if (line.isEmpty()) {
            val payload = buffer.toString().trimEnd('\n')
            buffer.clear()
            return payload.ifBlank { null }
        }
        if (line.startsWith(":")) return null
        if (line.startsWith("data:")) {
            val value = line.removePrefix("data:")
            buffer.append(value.removePrefix(" "))
            buffer.append('\n')
        }
        return null
    }
}

// Accumulates streaming deltas into a final message. Tool calls arrive by index.
class StreamAccumulator {
    private val text = StringBuilder()
    private val reasoning = StringBuilder()
    private val toolCalls = LinkedHashMap<Int, MutableToolCall>()
    private var finishReason: String? = null
    private var usage: UsageDto? = null

    fun apply(event: ChatStreamEvent) {
        when (event) {
            is ChatStreamEvent.Text -> text.append(event.delta)
            is ChatStreamEvent.Reasoning -> reasoning.append(event.delta)
            is ChatStreamEvent.ToolDelta -> {
                val call = toolCalls.getOrPut(event.index) { MutableToolCall() }
                if (!event.id.isNullOrBlank()) call.id = event.id
                if (!event.name.isNullOrBlank()) call.name = event.name
                if (event.arguments != null) call.arguments.append(event.arguments)
            }
            is ChatStreamEvent.Finished -> finishReason = event.reason
            is ChatStreamEvent.Usage -> usage = UsageDto(
                promptTokens = event.prompt,
                completionTokens = event.completion,
                totalTokens = event.total,
                promptDetails = PromptTokensDetailsDto(event.cached),
            )
            is ChatStreamEvent.ProtocolError -> Unit
        }
    }

    fun result(): CompletedMessage = CompletedMessage(
        text = text.toString(),
        reasoning = reasoning.toString(),
        toolCalls = toolCalls.entries.map { (index, call) ->
            CompletedToolCall(
                id = call.id ?: "call_$index",
                name = call.name.orEmpty(),
                arguments = call.arguments.toString().ifBlank { "{}" },
            )
        }.filter { it.name.isNotBlank() },
        finishReason = finishReason,
        usage = usage,
    )

    private class MutableToolCall {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }
}
