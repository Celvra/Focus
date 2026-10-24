package fan.x0.focus.feature.chat

internal fun List<ChatEntry>.approvalCard(): ApprovalUi? =
    filterIsInstance<ChatEntry.Approval>().lastOrNull()?.card

internal fun List<ChatEntry>.updateApproval(card: ApprovalUi): List<ChatEntry> = map { entry ->
    if (entry is ChatEntry.Approval && entry.card.id == card.id) entry.copy(card = card) else entry
}

internal fun List<ChatEntry>.sealLive(): List<ChatEntry> {
    val last = lastOrNull()
    return if (last is ChatEntry.Assistant && last.live) {
        dropLast(1) + last.copy(live = false)
    } else {
        this
    }
}

internal fun List<ChatEntry>.appendDelta(turn: Int, text: String): List<ChatEntry> {
    val last = lastOrNull()
    return if (last is ChatEntry.Assistant && last.live) {
        dropLast(1) + last.copy(text = last.text + text)
    } else {
        this + ChatEntry.Assistant("live-answer-$turn-$size", text, live = true)
    }
}

internal fun List<ChatEntry>.appendReasoning(turn: Int, text: String): List<ChatEntry> {
    val last = lastOrNull()
    return if (last is ChatEntry.Assistant && last.live) {
        dropLast(1) + last.copy(reasoning = last.reasoning + text)
    } else {
        this + ChatEntry.Assistant("live-answer-$turn-$size", "", live = true, reasoning = text)
    }
}

internal fun List<ChatEntry>.startStep(turn: Int, label: String): List<ChatEntry> {
    val sealed = sealLive()
    val last = sealed.lastOrNull()
    return when (last) {
        is ChatEntry.Trace -> sealed.dropLast(1) + last.copy(
            steps = last.steps.upsert(label, TraceStatus.Running, null),
        )
        is ChatEntry.Approval -> sealed.dropLast(1) + last.copy(
            card = last.card.copy(steps = last.card.steps.upsert(label, TraceStatus.Running, null)),
        )
        else -> sealed + ChatEntry.Trace(
            "live-trace-$turn-${sealed.size}",
            listOf(TraceStep(label, TraceStatus.Running)),
        )
    }
}

internal fun List<ChatEntry>.finishStep(label: String, ok: Boolean, summary: String): List<ChatEntry> {
    val status = if (ok) TraceStatus.Done else TraceStatus.Failed
    val last = lastOrNull()
    return when (last) {
        is ChatEntry.Trace -> dropLast(1) + last.copy(
            steps = last.steps.upsert(label, status, summary),
        )
        is ChatEntry.Approval -> dropLast(1) + last.copy(
            card = last.card.copy(steps = last.card.steps.upsert(label, status, summary)),
        )
        else -> this
    }
}

internal fun List<TraceStep>.upsert(
    label: String,
    status: TraceStatus,
    summary: String?,
): List<TraceStep> {
    val index = indexOfLast {
        it.label == label && (it.status == TraceStatus.Pending || it.status == TraceStatus.Running)
    }
    if (index < 0) {
        return if (status == TraceStatus.Running) this + TraceStep(label, status, summary) else this
    }
    return toMutableList().also {
        it[index] = it[index].copy(status = status, summary = summary ?: it[index].summary)
    }
}

internal fun ChatEntry.sealed(): ChatEntry = when (this) {
    is ChatEntry.Assistant -> copy(live = false)
    is ChatEntry.Approval -> copy(
        card = card.copy(
            phase = if (card.phase == ApprovalPhase.Declined) {
                ApprovalPhase.Declined
            } else {
                ApprovalPhase.Finished
            },
        ),
    )
    else -> this
}