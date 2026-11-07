package fan.x0.focus.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.data.assistant.TaskStatus
import fan.x0.focus.data.assistant.WorkerProfile
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
) {
    if (!panel.open) return
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.chat_side_panel_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                if (panel.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = hapticTap(onRefresh)) {
                        Text(stringResource(R.string.chat_side_panel_refresh))
                    }
                }
            }
            if (panel.items.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_side_panel_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(panel.items, key = { it.id }, contentType = { "side" }) { row ->
                        SideChatRow(
                            row = row,
                            onOpen = { onOpen(row.id) },
                            onStop = { onStop(row.id) },
                            onRetry = { onRetry(row.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SideChatRow(
    row: SideChatRowUi,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        onClick = hapticTap(onOpen),
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = statusIcon(row.status),
                contentDescription = null,
                tint = statusColor(row.status),
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (row.unseen) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = statusLabel(row.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor(row.status),
                    )
                    Text(
                        text = profileLabel(row.profile),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    modifier = Modifier.size(18.dp),
                )
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
private fun statusColor(status: TaskStatus) = when (status) {
    TaskStatus.Failed, TaskStatus.Interrupted -> MaterialTheme.colorScheme.error
    TaskStatus.Waiting -> MaterialTheme.colorScheme.tertiary
    TaskStatus.Done -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun profileLabel(profile: WorkerProfile) = stringResource(
    when (profile) {
        WorkerProfile.Light -> R.string.chat_side_profile_light
        WorkerProfile.Standard -> R.string.chat_side_profile_standard
        WorkerProfile.Heavy -> R.string.chat_side_profile_heavy
    },
)
