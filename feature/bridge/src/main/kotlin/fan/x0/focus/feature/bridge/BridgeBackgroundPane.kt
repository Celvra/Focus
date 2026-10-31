package fan.x0.focus.feature.bridge

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens

@Composable
internal fun BackgroundPane(
    state: BridgeUiState,
    onAction: (BridgeAction) -> Unit,
) {
    LaunchedEffect(Unit) { onAction(BridgeAction.LoadBackground) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.bridge_bg_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (state.backgroundJobs.isNotEmpty()) {
                FocusTextButton(
                    text = stringResource(R.string.bridge_bg_clear),
                    onClick = { onAction(BridgeAction.ClearBackgroundJobs) },
                )
            }
            IconButton(onClick = { onAction(BridgeAction.LoadBackground) }) {
                Icon(
                    imageVector = FocusIcons.Refresh,
                    contentDescription = stringResource(R.string.bridge_refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            state.backgroundLoading && state.backgroundJobs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize()) { FocusLoadingBlock() }
            }
            state.backgroundJobs.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FocusEmptyState(
                        icon = FocusIcons.Terminal,
                        title = stringResource(R.string.bridge_bg_empty_title),
                        body = stringResource(R.string.bridge_bg_empty_body),
                        actionLabel = stringResource(R.string.bridge_refresh),
                        onAction = { onAction(BridgeAction.LoadBackground) },
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.backgroundJobs, key = { it.id }) { job ->
                        BackgroundJobCard(
                            job = job,
                            onClick = { onAction(BridgeAction.OpenBackground(job.id)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
    state.backgroundSelected?.let { id ->
        BackgroundDetailSheet(
            job = state.backgroundJobs.firstOrNull { it.id == id },
            state = state,
            onAction = onAction,
        )
    }
}

@Composable
private fun BackgroundJobCard(
    job: BridgeBgJobUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = job.statusLabel()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FocusShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = job.command,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FocusStatusPill(text = status.first, tone = status.second)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = JobTime.relative(job.startedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (job.status != "running") {
                    Text(
                        text = stringResource(R.string.bridge_bg_exit, job.exitCode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundDetailSheet(
    job: BridgeBgJobUi?,
    state: BridgeUiState,
    onAction: (BridgeAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onAction(BridgeAction.CloseBackground) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job?.command ?: stringResource(R.string.bridge_bg_title),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (state.backgroundFollowing) {
                            stringResource(R.string.bridge_bg_following)
                        } else {
                            stringResource(R.string.bridge_bg_finished)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onAction(BridgeAction.CloseBackground) }) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.bridge_bg_back),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                job?.let {
                    val status = it.statusLabel()
                    FocusStatusPill(text = status.first, tone = status.second)
                }
                if (state.backgroundLogs.isNotEmpty()) {
                    FocusTextButton(
                        text = stringResource(R.string.bridge_bg_clear_logs),
                        onClick = { onAction(BridgeAction.ClearBackgroundLogs) },
                    )
                }
                if (job != null && job.status == "running") {
                    FocusOutlinedButton(
                        text = stringResource(R.string.bridge_bg_kill),
                        onClick = { onAction(BridgeAction.KillBackground(job.id)) },
                    )
                }
            }
            if (state.backgroundLogs.isEmpty()) {
                Text(
                    text = stringResource(R.string.bridge_bg_no_logs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(state.backgroundLogs, key = { it.seq }) { line ->
                            Text(
                                text = line.data,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                color = if (line.stream == "stderr") {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BridgeBgJobUi.statusLabel(): Pair<String, StatusTone> = when (status) {
    "running" -> stringResource(R.string.bridge_bg_running) to StatusTone.Active
    "completed" -> stringResource(R.string.bridge_bg_completed) to StatusTone.Success
    "failed" -> stringResource(R.string.bridge_bg_failed) to StatusTone.Danger
    "timed_out" -> stringResource(R.string.bridge_bg_timed_out) to StatusTone.Warning
    "killed" -> stringResource(R.string.bridge_bg_killed) to StatusTone.Neutral
    else -> status to StatusTone.Neutral
}

private object JobTime {
    fun relative(time: Long): String {
        if (time <= 0) return ""
        return DateUtils.getRelativeTimeSpanString(
            time,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }
}
