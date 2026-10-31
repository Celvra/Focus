package fan.x0.focus.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusChatBubble
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusReasoningCard
import fan.x0.focus.core.designsystem.components.FocusTraceCard
import fan.x0.focus.core.designsystem.components.FocusTraceStep
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubAgentSheet(
    run: SubAgentRunUi,
    onClose: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scroll = rememberScrollState()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(scroll) {
        scroll.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> follow = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    snapshotFlow { scroll.isScrollInProgress }.first { !it }
                    follow = scroll.value >= scroll.maxValue - 24
                }
            }
        }
    }
    LaunchedEffect(run.entries, run.running) {
        if (follow && run.entries.isNotEmpty()) scroll.scrollTo(scroll.maxValue)
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FocusIconBadge(icon = FocusIcons.Assistant)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_subagent_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = listOf(run.providerName, run.model)
                            .filter { it.isNotBlank() }
                            .joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.chat_subagent_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = FocusShapeTokens.card,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = FocusIcons.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.chat_subagent_task),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(
                                when {
                                    run.running -> R.string.chat_subagent_running
                                    run.failed -> R.string.chat_subagent_failed
                                    else -> R.string.chat_subagent_done
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                run.failed -> MaterialTheme.colorScheme.error
                                run.running -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = run.task,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                run.entries.forEach { entry ->
                    when (entry) {
                        is ChatEntry.Assistant -> Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (entry.reasoning.isNotBlank()) {
                                FocusReasoningCard(
                                    text = entry.reasoning,
                                    running = entry.live && run.running,
                                    durationMs = entry.reasoningMs,
                                    startedAt = entry.reasoningStartedAt,
                                )
                            }
                            if (entry.text.isNotBlank()) {
                                FocusChatBubble(
                                    text = entry.text,
                                    fromUser = false,
                                    streaming = entry.live && run.running,
                                )
                            }
                        }
                        is ChatEntry.Trace -> FocusTraceCard(
                            steps = entry.steps.map {
                                FocusTraceStep(
                                    label = it.label,
                                    status = it.status.toStepStatus(),
                                    summary = it.summary,
                                    durationMs = it.durationMs,
                                    startedAt = it.startedAt,
                                    diffs = it.diffs.map { diff -> diff.toFocusDiff() },
                                )
                            },
                        )
                        else -> Unit
                    }
                }
                if (run.running && run.entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        FocusLoadingBlock()
                    }
                }
                val lastAnswer = run.entries
                    .filterIsInstance<ChatEntry.Assistant>()
                    .lastOrNull()
                    ?.text
                    .orEmpty()
                if (!run.running && run.summary.isNotBlank() && run.summary != lastAnswer) {
                    FocusChatBubble(
                        text = run.summary,
                        fromUser = false,
                    )
                }
                val error = run.error
                if (!error.isNullOrBlank()) {
                    ChatFailureCard(
                        message = error,
                        retryable = false,
                        onRetry = {},
                    )
                }
            }
        }
    }
}
