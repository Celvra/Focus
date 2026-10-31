package fan.x0.focus.data.assistant

internal fun List<ChatTurnEntry>.resetAnswer(): List<ChatTurnEntry> =
    dropLastWhile { it is ChatTurnEntry.Answer }

internal fun List<ChatTurnEntry>.appendAnswer(
    text: String,
    reasoning: Boolean,
    now: Long = 0,
): List<ChatTurnEntry> {
    val last = lastOrNull()
    if (last is ChatTurnEntry.Answer) {
        val updated = if (reasoning) {
            last.copy(
                reasoning = last.reasoning + text,
                reasoningStartedAt = if (last.reasoningStartedAt == 0L) now else last.reasoningStartedAt,
            )
        } else {
            last.copy(
                text = last.text + text,
                reasoningMs = last.reasoningElapsed(now),
                reasoningStartedAt = 0,
            )
        }
        return dropLast(1) + updated
    }
    return this + if (reasoning) {
        ChatTurnEntry.Answer("", text, reasoningStartedAt = now)
    } else {
        ChatTurnEntry.Answer(text, "")
    }
}

private fun ChatTurnEntry.Answer.reasoningElapsed(now: Long): Long =
    if (reasoningStartedAt > 0 && now > reasoningStartedAt) now - reasoningStartedAt else reasoningMs

internal fun List<ChatTurnEntry>.finishReasoning(durationMs: Long): List<ChatTurnEntry> {
    if (durationMs <= 0) return this
    val last = lastOrNull() as? ChatTurnEntry.Answer ?: return this
    return dropLast(1) + last.copy(reasoningMs = durationMs, reasoningStartedAt = 0)
}

internal fun List<ChatTurnEntry>.stopReasoning(now: Long): List<ChatTurnEntry> {
    val last = lastOrNull() as? ChatTurnEntry.Answer ?: return this
    if (last.reasoningStartedAt <= 0) return this
    val duration = if (now > last.reasoningStartedAt) now - last.reasoningStartedAt else 0
    return dropLast(1) + last.copy(reasoningMs = duration, reasoningStartedAt = 0)
}

internal fun List<ChatTurnEntry>.startStep(
    id: String,
    label: String,
    tool: String,
    arguments: String,
    startedAt: Long = 0,
): List<ChatTurnEntry> {
    val step = ChatTurnStep(
        label = label,
        status = ChatTurnStepStatus.Running,
        id = id,
        tool = tool,
        arguments = arguments,
        startedAt = startedAt,
    )
    val stopped = stopReasoning(if (startedAt > 0) startedAt else System.currentTimeMillis())
    val last = stopped.lastOrNull()
    return when (last) {
        is ChatTurnEntry.Approval -> stopped.dropLast(1) + last.copy(
            card = last.card.copy(
                steps = last.card.steps.upsert(step, ChatTurnStepStatus.Running, null),
            ),
        )
        is ChatTurnEntry.Trace -> stopped.dropLast(1) + last.copy(
            steps = last.steps.upsert(step, ChatTurnStepStatus.Running, null),
        )
        else -> stopped + ChatTurnEntry.Trace(listOf(step))
    }
}

internal fun List<ChatTurnEntry>.finishStep(
    id: String,
    label: String,
    ok: Boolean,
    summary: String,
    output: String?,
    durationMs: Long = 0,
    diffs: List<FileDiff> = emptyList(),
): List<ChatTurnEntry> {
    val status = if (ok) ChatTurnStepStatus.Done else ChatTurnStepStatus.Failed
    val step = ChatTurnStep(
        label = label,
        status = status,
        id = id,
        summary = summary,
        output = output?.take(4000),
        durationMs = durationMs,
        diffs = diffs,
    )
    return map { entry ->
        when {
            entry is ChatTurnEntry.Trace && entry.steps.any {
                (id.isNotBlank() && it.id == id) ||
                    (id.isBlank() && it.label == label && it.status == ChatTurnStepStatus.Running)
            } -> entry.copy(steps = entry.steps.upsert(step, status, summary))
            entry is ChatTurnEntry.Approval && entry.card.steps.any {
                (id.isNotBlank() && it.id == id) ||
                    (id.isBlank() && it.label == label && it.status == ChatTurnStepStatus.Running)
            } -> entry.copy(
                card = entry.card.copy(steps = entry.card.steps.upsert(step, status, summary)),
            )
            else -> entry
        }
    }
}

internal fun List<ChatTurnStep>.upsert(
    step: ChatTurnStep,
    status: ChatTurnStepStatus,
    summary: String?,
): List<ChatTurnStep> {
    val index = indexOfLast {
        (step.id.isNotBlank() && it.id == step.id) ||
            (step.id.isBlank() && it.label == step.label &&
                (it.status == ChatTurnStepStatus.Pending || it.status == ChatTurnStepStatus.Running))
    }
    if (index < 0) {
        return if (status == ChatTurnStepStatus.Running) this + step else this
    }
    return toMutableList().also {
        it[index] = it[index].copy(
            status = status,
            summary = summary ?: it[index].summary,
            tool = step.tool.ifBlank { it[index].tool },
            arguments = step.arguments.ifBlank { it[index].arguments },
            output = step.output ?: it[index].output,
            startedAt = when {
                status == ChatTurnStepStatus.Running ->
                    step.startedAt.takeIf { value -> value > 0 } ?: it[index].startedAt
                else -> 0
            },
            durationMs = step.durationMs.takeIf { value -> value > 0 } ?: it[index].durationMs,
            diffs = if (step.diffs.isNotEmpty()) step.diffs else it[index].diffs,
        )
    }
}
