package fan.x0.focus.data.im

import fan.x0.focus.data.assistant.ChatTurnProgress
import fan.x0.focus.data.assistant.MessageStats
import fan.x0.focus.data.assistant.ToolPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

internal class ImTurnProgress(
    private val sendThinking: Boolean,
    private val sendTools: Boolean,
    private val send: suspend (String) -> Unit,
) {
    private val partialLine = StringBuilder()
    private val block = StringBuilder()
    private var blockLines = 0
    private var stats = MessageStats()
    private var announcedTools = 0
    private var sentThinking = false
    private var lastSentInput = -1
    private var lastSentOutput = -1

    suspend fun onProgress(progress: ChatTurnProgress) {
        when (progress) {
            is ChatTurnProgress.Reasoning -> {
                stats = progress.stats
                if (sendThinking) append(progress.text)
            }
            ChatTurnProgress.ReasoningEnded -> flush()
            is ChatTurnProgress.Stats -> stats = progress.stats
            is ChatTurnProgress.ToolStarted -> {
                stats = progress.stats
                flush()
                if (sendTools) {
                    announcedTools++
                    ImLog.d(TAG, "send tool step=$announcedTools tool=${progress.tool}")
                    send(toolMessage(progress))
                }
            }
        }
    }

    suspend fun finish() {
        flush()
        if (!sendThinking || !sentThinking || !hasUsage()) return
        if (stats.inputTokens == lastSentInput && stats.outputTokens == lastSentOutput) return
        send(statsMessage())
    }

    private suspend fun append(text: String) {
        partialLine.append(text)
        while (true) {
            val index = partialLine.indexOf("\n")
            if (index < 0) break
            block.append(partialLine, 0, index + 1)
            partialLine.delete(0, index + 1)
            blockLines++
            if (blockLines >= LINES_PER_MESSAGE) emit()
        }
    }

    private suspend fun flush() {
        if (partialLine.isNotEmpty()) {
            block.append(partialLine)
            partialLine.clear()
            blockLines++
        }
        if (block.isNotBlank()) emit()
    }

    private suspend fun emit() {
        val text = block.toString().trimEnd()
        val lines = blockLines
        block.clear()
        blockLines = 0
        if (text.isBlank()) return
        ImLog.d(TAG, "send thinking chars=${text.length} lines=$lines")
        sentThinking = true
        if (hasUsage()) {
            lastSentInput = stats.inputTokens
            lastSentOutput = stats.outputTokens
        }
        send(thinkingMessage(text))
    }

    private fun thinkingMessage(text: String): String {
        val round = maxOf(stats.round, 1)
        val steps = maxOf(stats.step, announcedTools)
        return buildString {
            append(header(round, steps))
            append("\n```text\n")
            append(text)
            append("\n```")
        }
    }

    private fun statsMessage(): String {
        val round = maxOf(stats.round, 1)
        val steps = maxOf(stats.step, announcedTools)
        return "本轮统计 | ${header(round, steps)}"
    }

    private fun header(round: Int, steps: Int): String = buildString {
        append("共 ")
        append(round)
        append(" 轮，")
        append(steps)
        append(" 步")
        if (hasUsage()) {
            append(" | 输入 ")
            append(formatTokens(stats.inputTokens))
            append(" · 输出 ")
            append(formatTokens(stats.outputTokens))
        }
    }

    private fun hasUsage(): Boolean = stats.inputTokens > 0 || stats.outputTokens > 0

    private fun toolMessage(progress: ChatTurnProgress.ToolStarted): String {
        val step = maxOf(stats.step + 1, announcedTools)
        val preview = toolPreview(progress.arguments)
            .ifBlank { progress.label.ifBlank { progress.tool } }
        return buildString {
            append("正在调用工具 · **第 ")
            append(step)
            append(" 步**")
            if (preview.isNotBlank()) {
                preview.lineSequence().forEach { line ->
                    append("\n> ")
                    append(line)
                }
            }
        }
    }

    private fun toolPreview(arguments: String): String {
        val command = runCatching {
            val root = Json.parseToJsonElement(arguments) as? JsonObject
            root?.get("command")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val rendered = command?.takeIf { it.isNotBlank() }
            ?: ToolPayload.format(arguments, MAX_TOOL_PREVIEW)
        return rendered
            .replace("\r", "")
            .take(MAX_TOOL_PREVIEW)
            .trim()
    }

    companion object {
        private const val TAG = "FocusIM/Progress"
        private const val LINES_PER_MESSAGE = 5
        private const val MAX_TOOL_PREVIEW = 360
    }
}

internal fun formatTokens(value: Int): String = when {
    value >= 1_000_000 -> decimal(value / 1_000_000.0) + "M"
    value >= 1_000 -> decimal(value / 1_000.0) + "K"
    else -> value.toString()
}

private fun decimal(value: Double): String {
    val rounded = (value * 10).toInt() / 10.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", rounded)
    }
}
