package fan.x0.focus.feature.chat

internal fun List<ChatEntry>.updateApproval(card: ApprovalUi): List<ChatEntry> = map { entry ->
    if (entry is ChatEntry.Approval && entry.card.id == card.id) entry.copy(card = card) else entry
}
