package fan.x0.focus.feature.crew

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusMenuAction
import fan.x0.focus.core.designsystem.components.FocusMessageActionPopup
import fan.x0.focus.core.designsystem.components.hapticSelect
import fan.x0.focus.core.designsystem.theme.FocusRadii

@Composable
internal fun CrewMessageActionPopup(
    menu: CrewMessageMenuUi,
    onAction: (CrewAction) -> Unit,
    onCopy: (String) -> Unit,
) {
    val actions = listOf(
        FocusMenuAction(
            icon = FocusIcons.Reply,
            label = stringResource(R.string.crew_reply),
            onClick = { onAction(CrewAction.ReplyTo(menu.key)) },
        ),
        FocusMenuAction(
            icon = FocusIcons.Copy,
            label = stringResource(R.string.crew_copy),
            enabled = menu.text.isNotBlank(),
            onClick = { onCopy(menu.text) },
        ),
        FocusMenuAction(
            icon = FocusIcons.SelectAll,
            label = stringResource(R.string.crew_select_text),
            enabled = menu.text.isNotBlank(),
            onClick = { onAction(CrewAction.SelectText(menu.key)) },
        ),
        FocusMenuAction(
            icon = FocusIcons.Edit,
            label = stringResource(R.string.crew_edit_message),
            enabled = menu.canEdit,
            dividerAfter = true,
            onClick = { onAction(CrewAction.StartEdit(menu.key)) },
        ),
        FocusMenuAction(
            icon = FocusIcons.Delete,
            label = stringResource(R.string.crew_delete_message),
            enabled = menu.canDelete,
            destructive = true,
            onClick = { onAction(CrewAction.AskDeleteMessage(menu.key)) },
        ),
    )
    FocusMessageActionPopup(
        actions = actions,
        onDismiss = { onAction(CrewAction.CloseMessageMenu) },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = menu.author.ifBlank { stringResource(R.string.crew_you) },
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (menu.text.isNotBlank()) {
                Text(
                    text = menu.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val counts = remember(menu.reactions) {
                menu.reactions.groupingBy { it }.eachCount()
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CrewReactionChoices.forEach { emoji ->
                    val selected = counts[emoji] != null
                    FilterChip(
                        selected = selected,
                        onClick = hapticSelect {
                            menu.messageId?.let { id -> onAction(CrewAction.React(id, emoji)) }
                        },
                        label = {
                            Text(
                                text = if (selected) {
                                    "$emoji ${counts[emoji]}"
                                } else {
                                    emoji
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        shape = RoundedCornerShape(FocusRadii.extraLarge),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}
