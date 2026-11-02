package fan.x0.focus.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusMenuAction
import fan.x0.focus.core.designsystem.components.FocusMessageActionPopup
import fan.x0.focus.core.designsystem.components.hapticSelect
import fan.x0.focus.core.designsystem.theme.FocusRadii

@Composable
internal fun MessageActionPopup(
    menu: ChatMenuUi,
    onAction: (ChatAction) -> Unit,
    onCopy: (String) -> Unit,
) {
    val actions = listOf(
        FocusMenuAction(
            icon = FocusIcons.Reply,
            label = stringResource(R.string.chat_menu_reply),
            onClick = { onAction(ChatAction.ReplyTo(menu.key)) },
        ),
        FocusMenuAction(
            icon = FocusIcons.Copy,
            label = stringResource(R.string.chat_menu_copy),
            enabled = menu.text.isNotBlank(),
            onClick = { onCopy(menu.text) },
        ),
        FocusMenuAction(
            icon = FocusIcons.SelectAll,
            label = stringResource(R.string.chat_menu_select),
            enabled = menu.text.isNotBlank(),
            onClick = { onAction(ChatAction.SelectText(menu.key)) },
        ),
        FocusMenuAction(
            icon = FocusIcons.Edit,
            label = stringResource(R.string.chat_menu_edit),
            enabled = menu.canEdit,
            dividerAfter = true,
            onClick = { onAction(ChatAction.StartEdit(menu.key)) },
        ),
        FocusMenuAction(
            icon = FocusIcons.Delete,
            label = stringResource(R.string.chat_menu_delete),
            enabled = menu.canDelete,
            destructive = true,
            onClick = { onAction(ChatAction.AskDelete(menu.key)) },
        ),
    )
    FocusMessageActionPopup(
        actions = actions,
        onDismiss = { onAction(ChatAction.CloseMessageMenu) },
    )
}

@Composable
internal fun MessageReactionChip(
    reaction: String,
    count: Int,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        FilterChip(
            selected = true,
            onClick = hapticSelect {},
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = reaction,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            },
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$reaction, $count" },
            shape = RoundedCornerShape(FocusRadii.extraLarge),
        )
    }
}

