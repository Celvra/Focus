package fan.x0.focus.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.data.assistant.TaskStatus
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SideChatSheet(
    panel: SidePanelUi,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
    onStop: (String) -> Unit,
    onRetry: (String) -> Unit,
    onPromote: (String) -> Unit,
) {
    if (!panel.open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SideChatHeader(
                panel = panel,
                onClose = onClose,
                onRefresh = onRefresh,
            )
            val active = panel.items.filter { it.status.active }
            val recent = panel.items.filterNot { it.status.active }
            when {
                panel.loading && panel.items.isEmpty() -> SideChatLoading()
                panel.items.isEmpty() -> FocusEmptyState(
                    icon = FocusIcons.Groups,
                    title = stringResource(R.string.chat_side_panel_empty_title),
                    body = stringResource(R.string.chat_side_panel_empty),
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (active.isNotEmpty()) {
                        item(key = "active-header", contentType = "header") {
                            SideChatSectionLabel(stringResource(R.string.chat_side_panel_section_active))
                        }
                        items(active, key = { it.id }, contentType = { "side" }) { row ->
                            SideChatRow(
                                row = row,
                                onOpen = { onOpen(row.id) },
                                onStop = { onStop(row.id) },
                                onRetry = { onRetry(row.id) },
                                onPromote = { onPromote(row.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    if (recent.isNotEmpty()) {
                        item(key = "recent-header", contentType = "header") {
                            SideChatSectionLabel(stringResource(R.string.chat_side_panel_section_recent))
                        }
                        items(recent, key = { it.id }, contentType = { "side" }) { row ->
                            SideChatRow(
                                row = row,
                                onOpen = { onOpen(row.id) },
                                onStop = { onStop(row.id) },
                                onRetry = { onRetry(row.id) },
                                onPromote = { onPromote(row.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideChatHeader(
    panel: SidePanelUi,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
) {
    val active = panel.items.count { it.status.active }
    val summary = if (active > 0) {
        stringResource(R.string.chat_side_panel_summary, panel.items.size, active)
    } else {
        stringResource(R.string.chat_side_panel_summary_idle, panel.items.size)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.chat_side_panel_title),
                style = LocalFocusTypography.current.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (panel.loading) {
            FocusLoadingBlob(size = 20.dp)
        } else {
            FilledTonalIconButton(onClick = hapticTap(onRefresh)) {
                Icon(
                    imageVector = FocusIcons.Refresh,
                    contentDescription = stringResource(R.string.chat_side_panel_refresh),
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        FilledTonalIconButton(onClick = hapticTap(onClose)) {
            Icon(
                imageVector = FocusIcons.Close,
                contentDescription = stringResource(R.string.chat_side_panel_close),
            )
        }
    }
}

@Composable
private fun SideChatSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SideChatLoading() {
    FocusLoadingBlock(modifier = Modifier.padding(bottom = 16.dp))
}

@Composable
private fun SideChatRow(
    row: SideChatRowUi,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onPromote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (row.unseen) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    Surface(
        onClick = hapticTap(onOpen),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FocusIconBadge(
                icon = statusIcon(row.status),
                tint = statusContent(row.status),
                container = statusContainer(row.status),
                size = 40.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.title,
                        style = LocalFocusTypography.current.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (row.unseen) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            modifier = Modifier.size(8.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                        ) {}
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusStatusPill(
                        text = statusLabel(row.status),
                        tone = statusTone(row.status),
                    )
                    Text(
                        text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(row.updatedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (row.summary.isNotBlank()) {
                    Text(
                        text = row.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                FilledTonalIconButton(
                    onClick = hapticTap(onPromote),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = FocusIcons.Assistant,
                        contentDescription = stringResource(R.string.chat_promote_main),
                        modifier = Modifier.size(18.dp),
                    )
                }
                when {
                    row.status.active -> TextButton(onClick = hapticTap(onStop)) {
                        Text(stringResource(R.string.chat_side_panel_stop))
                    }
                    row.status == TaskStatus.Failed || row.status == TaskStatus.Interrupted -> {
                        TextButton(onClick = hapticTap(onRetry)) {
                            Text(stringResource(R.string.chat_side_result_retry))
                        }
                    }
                    else -> Icon(
                        imageVector = FocusIcons.Chevron,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(18.dp),
                    )
                }
            }
        }
    }
}

private fun statusIcon(status: TaskStatus) = when (status) {
    TaskStatus.Queued, TaskStatus.Running -> FocusIcons.Bolt
    TaskStatus.Waiting -> FocusIcons.Notification
    TaskStatus.Done -> FocusIcons.Check
    TaskStatus.Failed, TaskStatus.Interrupted -> FocusIcons.Warning
    TaskStatus.NoResult, TaskStatus.None -> FocusIcons.Chat
}

@Composable
private fun statusContainer(status: TaskStatus) = when (status) {
    TaskStatus.Queued, TaskStatus.Running -> MaterialTheme.colorScheme.secondaryContainer
    TaskStatus.Waiting -> MaterialTheme.colorScheme.tertiaryContainer
    TaskStatus.Done -> MaterialTheme.colorScheme.primaryContainer
    TaskStatus.Failed, TaskStatus.Interrupted -> MaterialTheme.colorScheme.errorContainer
    TaskStatus.NoResult, TaskStatus.None -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun statusContent(status: TaskStatus) = when (status) {
    TaskStatus.Queued, TaskStatus.Running -> MaterialTheme.colorScheme.onSecondaryContainer
    TaskStatus.Waiting -> MaterialTheme.colorScheme.onTertiaryContainer
    TaskStatus.Done -> MaterialTheme.colorScheme.onPrimaryContainer
    TaskStatus.Failed, TaskStatus.Interrupted -> MaterialTheme.colorScheme.onErrorContainer
    TaskStatus.NoResult, TaskStatus.None -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusTone(status: TaskStatus) = when (status) {
    TaskStatus.Queued, TaskStatus.Running -> StatusTone.Active
    TaskStatus.Waiting -> StatusTone.Warning
    TaskStatus.Done -> StatusTone.Success
    TaskStatus.Failed, TaskStatus.Interrupted -> StatusTone.Danger
    TaskStatus.NoResult, TaskStatus.None -> StatusTone.Neutral
}

@Composable
private fun statusLabel(status: TaskStatus) = stringResource(
    when (status) {
        TaskStatus.Queued -> R.string.chat_side_status_queued
        TaskStatus.Running -> R.string.chat_side_status_running
        TaskStatus.Waiting -> R.string.chat_side_status_waiting
        TaskStatus.Done -> R.string.chat_side_status_done
        TaskStatus.Failed -> R.string.chat_side_status_failed
        TaskStatus.Interrupted -> R.string.chat_side_status_interrupted
        TaskStatus.NoResult -> R.string.chat_side_status_no_result
        TaskStatus.None -> R.string.chat_side_status_none
    },
)
