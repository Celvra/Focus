package fan.x0.focus.core.designsystem.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

enum class FocusStepStatus { Pending, Running, Done, Failed, Skipped }

private val NodeColumnWidth = 20.dp
private val NodeSize = 16.dp
private val LineGap = 12.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusTimelineStep(
    label: String,
    status: FocusStepStatus,
    modifier: Modifier = Modifier,
    summary: String? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onOpen: (() -> Unit)? = null,
    durationMs: Long = 0,
    startedAt: Long = 0,
    diffs: List<FocusDiff> = emptyList(),
    media: List<FocusTraceImage> = emptyList(),
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val contentColor = LocalContentColor.current
    val hasSummary = !summary.isNullOrBlank()
    val interaction = remember { MutableInteractionSource() }
    val rowShape = MaterialTheme.shapes.small
    var expanded by remember(label) { mutableStateOf(false) }

    val targetTint = when (status) {
        FocusStepStatus.Pending, FocusStepStatus.Skipped -> contentColor.copy(alpha = 0.35f)
        FocusStepStatus.Running -> scheme.tertiary
        FocusStepStatus.Done -> scheme.primary
        FocusStepStatus.Failed -> scheme.error
    }
    val tint by animateColorAsState(targetTint, motion.effectsDefault(), label = "stepTint")
    val labelColor by animateColorAsState(
        when (status) {
            FocusStepStatus.Pending, FocusStepStatus.Skipped -> contentColor.copy(alpha = 0.7f)
            FocusStepStatus.Failed -> scheme.error
            else -> contentColor
        },
        motion.effectsDefault(),
        label = "stepLabel",
    )
    val running = status == FocusStepStatus.Running
    val lineColor = contentColor.copy(alpha = 0.16f)
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialFast(),
        label = "stepChevron",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .stateLayer(interaction, rowShape)
                .drawBehind {
                    val x = NodeColumnWidth.toPx() / 2f
                    val centerY = size.height / 2f
                    val gap = LineGap.toPx()
                    val stroke = 1.dp.toPx()
                    if (!isFirst) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, centerY - gap), stroke)
                    }
                    if (!isLast) {
                        drawLine(lineColor, Offset(x, centerY + gap), Offset(x, size.height), stroke)
                    }
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = hasSummary || onOpen != null,
                    role = Role.Button,
                    onClick = hapticTap {
                        if (onOpen != null) onOpen() else expanded = !expanded
                    },
                )
                .heightIn(min = 40.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.width(NodeColumnWidth), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(NodeSize), contentAlignment = Alignment.Center) {
                    when (status) {
                        FocusStepStatus.Running -> ContainedLoadingIndicator(
                            modifier = Modifier.size(NodeSize),
                        )
                        FocusStepStatus.Done -> Icon(
                            imageVector = FocusIcons.Check,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(14.dp),
                        )
                        FocusStepStatus.Failed -> Icon(
                            imageVector = FocusIcons.Warning,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(14.dp),
                        )
                        else -> Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tint),
                        )
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (running) labelColor.copy(alpha = 0.45f) else labelColor,
                modifier = Modifier
                    .weight(1f)
                    .shimmer(running, highlight = labelColor),
            )
            FocusDurationText(
                durationMs = durationMs,
                startedAt = startedAt,
                running = running,
            )
            if (onOpen != null) {
                Icon(
                    imageVector = FocusIcons.Chevron,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp),
                )
            } else if (hasSummary) {
                Icon(
                    imageVector = FocusIcons.ArrowDown,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevron },
                )
            }
        }
        if (media.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (!isLast) {
                            val x = NodeColumnWidth.toPx() / 2f
                            drawLine(
                                lineColor,
                                Offset(x, 0f),
                                Offset(x, size.height),
                                1.dp.toPx(),
                            )
                        }
                    }
                    .padding(start = NodeColumnWidth + 8.dp, top = 2.dp, bottom = 8.dp, end = 4.dp),
            ) {
                FocusTraceMedia(media = media)
            }
        }
        AnimatedVisibility(
            visible = expanded && hasSummary,
            enter = expandVertically(motion.spatialDefault()) + fadeIn(motion.effectsDefault()),
            exit = shrinkVertically(motion.spatialDefault()) + fadeOut(motion.effectsFast()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (!isLast) {
                            val x = NodeColumnWidth.toPx() / 2f
                            drawLine(
                                lineColor,
                                Offset(x, 0f),
                                Offset(x, size.height),
                                1.dp.toPx(),
                            )
                        }
                    }
                    .padding(start = NodeColumnWidth + 8.dp, top = 0.dp, bottom = 6.dp, end = 4.dp),
            ) {
                Text(
                    text = summary.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.75f),
                )
            }
        }
        if (diffs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        if (!isLast) {
                            val x = NodeColumnWidth.toPx() / 2f
                            drawLine(
                                lineColor,
                                Offset(x, 0f),
                                Offset(x, size.height),
                                1.dp.toPx(),
                            )
                        }
                    }
                    .padding(start = NodeColumnWidth + 8.dp, top = 2.dp, bottom = 8.dp, end = 4.dp),
            ) {
                FocusDiffList(diffs = diffs)
            }
        }
    }
}

@Composable
private fun FocusTraceMedia(media: List<FocusTraceImage>) {
    var preview by remember { mutableStateOf<FocusTraceImage?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        media.forEach { image ->
            val bitmap = rememberTraceImage(image, TRACE_THUMB_EDGE)
            Surface(
                onClick = hapticTap { preview = image },
                enabled = bitmap != null,
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = stringResource(R.string.common_image_preview, image.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = FocusIcons.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
    preview?.let { image ->
        val bitmap = rememberTraceImage(image)
        if (bitmap != null) {
            FocusImageLightbox(
                image = bitmap,
                title = image.name,
                onDismiss = { preview = null },
            )
        }
    }
}

@Composable
private fun rememberTraceImage(image: FocusTraceImage, maxEdge: Int = 0): ImageBitmap? =
    remember(image.id, image.base64, maxEdge) {
        val bytes = runCatching { Base64.decode(image.base64, Base64.DEFAULT) }.getOrNull()
            ?: return@remember null
        runCatching {
            val options = BitmapFactory.Options()
            if (maxEdge > 0) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxEdge && bounds.outHeight / (sample * 2) >= maxEdge) {
                    sample *= 2
                }
                options.inSampleSize = sample
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        }.getOrNull()
    }

private const val TRACE_THUMB_EDGE = 192

@Composable
fun Modifier.shimmer(
    active: Boolean,
    highlight: Color = MaterialTheme.colorScheme.onSurface,
): Modifier {
    if (!active) return this
    return composed {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "shimmerProgress",
        )
        graphicsLayer { alpha = 0.99f }
            .drawWithContent {
                drawContent()
                val sweep = size.width * 0.55f
                val start = -sweep + progress * (size.width + sweep * 2f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        startX = start,
                        endX = start + sweep,
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            }
    }
}
