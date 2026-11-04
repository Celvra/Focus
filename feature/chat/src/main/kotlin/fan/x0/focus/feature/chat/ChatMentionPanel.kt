package fan.x0.focus.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.designsystem.components.FocusMentionGroup
import fan.x0.focus.core.designsystem.components.FocusMentionItem
import fan.x0.focus.core.designsystem.components.FocusMentionPanel
import fan.x0.focus.data.assistant.MentionKind
import fan.x0.focus.data.assistant.MentionUi

@Composable
internal fun ChatMentionPanel(
    mention: MentionUi,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = mention.items
        .groupBy { it.kind }
        .map { (kind, items) ->
            FocusMentionGroup(
                id = kind.name,
                title = if (kind == MentionKind.Workspace) {
                    stringResource(R.string.chat_mention_workspace)
                } else {
                    null
                },
                items = items.map { item ->
                    FocusMentionItem(
                        id = item.id,
                        label = item.label,
                        description = item.description,
                        kind = item.kind.name,
                        action = item.action,
                    )
                },
            )
        }
    FocusMentionPanel(
        groups = groups,
        selectedId = mention.items.getOrNull(mention.selected)?.id,
        onPick = { onPick(it.id) },
        modifier = modifier,
    )
}
