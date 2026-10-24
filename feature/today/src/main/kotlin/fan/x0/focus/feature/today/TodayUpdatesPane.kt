package fan.x0.focus.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCategoryIcons
import fan.x0.focus.core.designsystem.components.FocusDivider
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

@Composable
fun UpdatesPane(
    state: TodayUiState,
    onAction: (TodayAction) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "prompt") {
            FocusReveal {
                UpdatesPrompt(onClick = { onAction(TodayAction.AskForUpdates) })
            }
        }
        if (state.updates.isEmpty()) {
            item(key = "empty") {
                FocusEmptyState(
                    icon = FocusIcons.Today,
                    title = stringResource(R.string.today_updates_empty_title),
                    body = stringResource(R.string.today_updates_empty_body),
                    actionLabel = stringResource(R.string.today_updates_generate),
                    onAction = { onAction(TodayAction.AskForUpdates) },
                )
            }
        } else {
            itemsIndexed(state.updates, key = { _, update -> update.id }) { index, update ->
                UpdateCard(
                    update = update,
                    onAction = onAction,
                    modifier = Modifier.animateItem(),
                )
                if (index < state.updates.lastIndex) {
                    FocusDivider()
                }
            }
        }
    }
}

@Composable
private fun UpdatesPrompt(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = FocusShapeTokens.cardLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = FocusIcons.Assistant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.today_updates_prompt),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UpdateCard(
    update: UpdateUi,
    onAction: (TodayAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FocusIconBadge(
                icon = FocusCategoryIcons.resolve(update.icon),
                size = 44.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (update.category.isNotBlank()) {
                    Text(
                        text = update.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = update.headline,
                    style = LocalFocusTypography.current.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = update.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconToggleButton(
                checked = update.feedback == 1,
                onCheckedChange = { onAction(TodayAction.Feedback(update.id, 1)) },
            ) {
                Icon(
                    imageVector = FocusIcons.ThumbUp,
                    contentDescription = stringResource(R.string.today_update_like),
                    tint = if (update.feedback == 1) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            IconToggleButton(
                checked = update.feedback == -1,
                onCheckedChange = { onAction(TodayAction.Feedback(update.id, -1)) },
            ) {
                Icon(
                    imageVector = FocusIcons.ThumbDown,
                    contentDescription = stringResource(R.string.today_update_dislike),
                    tint = if (update.feedback == -1) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            TextButton(
                onClick = { onAction(TodayAction.Discuss(update.id)) },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = FocusIcons.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.today_update_discuss),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = update.time,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            IconButton(onClick = { onAction(TodayAction.OpenReason(update.id)) }) {
                Icon(
                    imageVector = FocusIcons.Info,
                    contentDescription = stringResource(R.string.today_update_info),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
