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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

data class FocusTraceStep(
    val label: String,
    val status: FocusStepStatus,
    val summary: String? = null,
    val openKey: String? = null,
    val durationMs: Long = 0,
    val startedAt: Long = 0,
    val diffs: List<FocusDiff> = emptyList(),
)

@Composable
fun FocusReasoningCard(
    text: String,
    running: Boolean,
    durationMs: Long = 0,
    startedAt: Long = 0,
) {
    val motion = LocalFocusMotion.current
    var expanded by remember { mutableStateOf(running) }
    LaunchedEffect(running) {
        if (!running) expanded = false
    }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialFast(),
        label = "reasoningChevron",
    )
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    Card(
        shape = FocusShapeTokens.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .stateLayer(interaction, shape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        expanded = !expanded
                    }
                    .heightIn(min = 40.dp)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = FocusIcons.Idea,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(if (running) R.string.common_reasoning_live else R.string.common_reasoning),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .shimmer(running, highlight = MaterialTheme.colorScheme.onSurface),
                )
                FocusDurationText(
                    durationMs = durationMs,
                    startedAt = startedAt,
                    running = running,
                )
                Icon(
                    imageVector = FocusIcons.ArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).rotate(chevron),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.spatialDefault()) + fadeIn(motion.effectsDefault()),
                exit = shrinkVertically(motion.spatialDefault()) + fadeOut(motion.effectsFast()),
            ) {
                ReasoningBody(text = text, running = running)
            }
        }
    }
}

@Composable
private fun ReasoningBody(text: String, running: Boolean) {
    val scroll = rememberScrollState()
    LaunchedEffect(text, running) {
        if (running) scroll.animateScrollTo(scroll.maxValue)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (running) {
                    Modifier
                        .graphicsLayer { alpha = 0.99f }
                        .drawWithCache {
                            val height = size.height.takeIf { it > 0f } ?: 1f
                            val fade = (20.dp.toPx() / height).coerceIn(0f, 0.33f)
                            val brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    fade to Color.Black,
                                ),
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush = brush, blendMode = BlendMode.DstIn)
                            }
                        }
                        .heightIn(max = 120.dp)
                        .verticalScroll(scroll)
                } else {
                    Modifier
                }
            )
    ) {
        FocusMarkdown(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            streaming = running,
            bodyStyle = MaterialTheme.typography.bodySmall,
            preserveLineBreaks = true,
            modifier = Modifier.padding(start = 28.dp, end = 8.dp, bottom = 10.dp),
        )
    }
}

@Composable
fun FocusTraceCard(steps: List<FocusTraceStep>, onOpen: ((String) -> Unit)? = null) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val running = steps.any { it.status == FocusStepStatus.Running || it.status == FocusStepStatus.Pending }
    val failed = steps.any { it.status == FocusStepStatus.Failed }
    var expanded by remember {
        mutableStateOf(running || steps.any { it.diffs.isNotEmpty() })
    }
    LaunchedEffect(running) {
        if (!running) expanded = steps.any { it.diffs.isNotEmpty() }
    }
    val container by animateColorAsState(
        targetValue = if (running) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        animationSpec = motion.effectsDefault(),
        label = "traceContainer",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialFast(),
        label = "traceChevron",
    )
    val headerInteraction = remember { MutableInteractionSource() }
    val headerShape = MaterialTheme.shapes.small

    Card(
        shape = FocusShapeTokens.card,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .stateLayer(headerInteraction, headerShape)
                    .clickable(
                        interactionSource = headerInteraction,
                        indication = null,
                        role = Role.Button,
                    ) { expanded = !expanded }
                    .heightIn(min = 40.dp)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    when {
                        running -> FocusLoadingBlob(size = 16.dp)
                        failed -> Icon(
                            imageVector = FocusIcons.Warning,
                            contentDescription = null,
                            tint = scheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        else -> Icon(
                            imageVector = FocusIcons.Check,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.common_tools_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pluralStringResource(R.plurals.common_tools_count, steps.size, steps.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = FocusIcons.ArrowDown,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevron },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.spatialDefault()) + fadeIn(motion.effectsDefault()),
                exit = shrinkVertically(motion.spatialDefault()) + fadeOut(motion.effectsFast()),
            ) {
                Column {
                    steps.forEachIndexed { index, step ->
                        key(index, step.label) {
                            val openKey = step.openKey
                            FocusTimelineStep(
                                label = step.label,
                                status = step.status,
                                summary = step.summary,
                                durationMs = step.durationMs,
                                startedAt = step.startedAt,
                                diffs = step.diffs,
                                isFirst = index == 0,
                                isLast = index == steps.lastIndex,
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
}
