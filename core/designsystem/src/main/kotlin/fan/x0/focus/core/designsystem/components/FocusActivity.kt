package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

@Composable
fun FocusTraceCard(steps: List<FocusTraceStep>, onOpen: ((String) -> Unit)? = null) {
    FocusActivity(
        reasoning = "",
        running = false,
        steps = steps,
        onOpen = onOpen,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusActivity(
    reasoning: String,
    running: Boolean,
    reasoningMs: Long = 0,
    reasoningStartedAt: Long = 0,
    steps: List<FocusTraceStep>,
    onOpen: ((String) -> Unit)? = null,
) {
    if (reasoning.isBlank() && steps.isEmpty()) return
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val active = running || steps.any {
        it.status == FocusStepStatus.Running || it.status == FocusStepStatus.Pending
    }
    var expanded by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        expanded = active
    }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialFast(),
        label = "activityChevron",
    )
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    val totalMs = steps.sumOf { it.durationMs }
    val summary = ActivitySummary(
        active = active,
        reasoningMs = reasoningMs,
        reasoningStartedAt = reasoningStartedAt,
        tools = steps.size,
        files = steps.flatMap { it.diffs }.map { it.path }.distinct().size,
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .stateLayer(interaction, shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = hapticTap { expanded = !expanded },
                )
                .heightIn(min = 44.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                when {
                    active -> ContainedLoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                    )
                    else -> Icon(
                        imageVector = if (steps.isEmpty()) FocusIcons.Idea else FocusIcons.Tool,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .shimmer(active, highlight = scheme.onSurfaceVariant),
            )
            FocusDurationText(
                durationMs = totalMs,
                startedAt = 0,
                running = false,
            )
            Icon(
                imageVector = FocusIcons.ArrowDown,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(chevron),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(motion.spatialDefault()) + fadeIn(motion.effectsDefault()),
            exit = shrinkVertically(motion.spatialDefault()) + fadeOut(motion.effectsFast()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (reasoning.isNotBlank()) {
                    FocusReasoningCard(
                        text = reasoning,
                        running = running,
                        durationMs = reasoningMs,
                        startedAt = reasoningStartedAt,
                    )
                }
                steps.forEachIndexed { index, step ->
                    key(index, step.openKey ?: step.label) {
                        val openKey = step.openKey
                        FocusActivityToolCard(
                            step = step,
                            onOpen = if (openKey != null && onOpen != null) {
                                { onOpen(openKey) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivitySummary(
    active: Boolean,
    reasoningMs: Long,
    reasoningStartedAt: Long,
    tools: Int,
    files: Int,
): String {
    if (tools == 0) {
        val duration = focusDurationLabel(reasoningMs, reasoningStartedAt, active)
        return if (duration != null) {
            stringResource(R.string.common_activity_thought, duration)
        } else {
            stringResource(if (active) R.string.common_reasoning_live else R.string.common_reasoning)
        }
    }
    val toolText = pluralStringResource(R.plurals.common_activity_tools, tools, tools)
    if (files == 0) {
        return stringResource(R.string.common_activity_summary_tools, toolText)
    }
    val fileText = pluralStringResource(R.plurals.common_activity_files, files, files)
    return stringResource(R.string.common_activity_summary, toolText, fileText)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusActivityToolCard(
    step: FocusTraceStep,
    onOpen: (() -> Unit)?,
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val running = step.status == FocusStepStatus.Running || step.status == FocusStepStatus.Pending
    val failed = step.status == FocusStepStatus.Failed
    val container by animateColorAsState(
        targetValue = when {
            failed -> scheme.errorContainer
            running -> scheme.surfaceContainerHigh
            else -> scheme.surfaceContainerLow
        },
        animationSpec = motion.effectsDefault(),
        label = "toolContainer",
    )
    val content by animateColorAsState(
        targetValue = if (failed) scheme.onErrorContainer else scheme.onSurface,
        animationSpec = motion.effectsDefault(),
        label = "toolContent",
    )
    val details = step.input.isNotBlank() || step.output.isNotBlank() ||
        step.media.isNotEmpty() || step.diffs.isNotEmpty()
    var expanded by remember(step.label, step.openKey) { mutableStateOf(false) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialFast(),
        label = "toolChevron",
    )
    val colors = CardDefaults.cardColors(
        containerColor = container,
        contentColor = content,
    )
    val shape = FocusShapeTokens.card
    val modifier = Modifier.fillMaxWidth()
    val onCardClick = when {
        onOpen != null -> hapticTap(onOpen)
        details -> hapticTap { expanded = !expanded }
        else -> null
    }
    val body: @Composable () -> Unit = {
        FocusActivityToolBody(
            step = step,
            contentColor = content,
            running = running,
            details = details,
            expanded = expanded,
            chevron = chevron,
            openable = onOpen != null,
        )
    }
    if (onCardClick != null) {
        Card(
            onClick = onCardClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
        ) {
            body()
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
        ) {
            body()
        }
    }
}

@Composable
private fun FocusActivityToolBody(
    step: FocusTraceStep,
    contentColor: Color,
    running: Boolean,
    details: Boolean,
    expanded: Boolean,
    chevron: Float,
    openable: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FocusActivityBadge(status = step.status)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (step.tool.isBlank()) step.label else friendlyToolName(step.tool),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = contentColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .shimmer(running, highlight = contentColor),
                    )
                    FocusDurationText(
                        durationMs = step.durationMs,
                        startedAt = step.startedAt,
                        running = running,
                        color = contentColor,
                    )
                }
                val summary = step.summary?.trim().orEmpty()
                if (summary.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.82f),
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            when {
                openable -> Icon(
                    imageVector = FocusIcons.Chevron,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
                details -> Icon(
                    imageVector = FocusIcons.ArrowDown,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevron),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(LocalFocusMotion.current.spatialDefault()) +
                fadeIn(LocalFocusMotion.current.effectsDefault()),
            exit = shrinkVertically(LocalFocusMotion.current.spatialDefault()) +
                fadeOut(LocalFocusMotion.current.effectsFast()),
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (step.diffs.isEmpty()) {
                    if (step.input.isNotBlank()) {
                        FocusToolPayload(
                            title = stringResource(R.string.common_tool_input),
                            text = step.input,
                            contentColor = contentColor,
                        )
                    }
                    if (step.output.isNotBlank()) {
                        FocusToolPayload(
                            title = stringResource(R.string.common_tool_output),
                            text = step.output,
                            contentColor = contentColor,
                        )
                    }
                }
                if (step.media.isNotEmpty()) {
                    FocusTraceMedia(media = step.media)
                }
                if (step.diffs.isNotEmpty()) {
                    FocusDiffList(diffs = step.diffs)
                }
            }
        }
    }
}

@Composable
private fun FocusToolPayload(title: String, text: String, contentColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor.copy(alpha = 0.7f),
        )
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.9f),
                maxLines = 14,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusActivityBadge(status: FocusStepStatus) {
    val scheme = MaterialTheme.colorScheme
    when (status) {
        FocusStepStatus.Running, FocusStepStatus.Pending -> ContainedLoadingIndicator(
            modifier = Modifier.size(18.dp),
            containerColor = scheme.primaryContainer,
            indicatorColor = scheme.primary,
        )
        FocusStepStatus.Done -> Icon(
            imageVector = FocusIcons.Check,
            contentDescription = stringResource(R.string.common_tools_done),
            tint = scheme.primary,
            modifier = Modifier.size(18.dp),
        )
        FocusStepStatus.Failed -> Icon(
            imageVector = FocusIcons.Warning,
            contentDescription = stringResource(R.string.common_tools_failed),
            tint = scheme.error,
            modifier = Modifier.size(18.dp),
        )
        FocusStepStatus.Skipped -> Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(scheme.onSurfaceVariant.copy(alpha = 0.5f)),
        )
    }
}
