package fan.x0.focus.feature.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.hapticTap

@Composable
internal fun OverlaySelection(
    onSelected: (Rect) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val minSize = with(density) { 24.dp.toPx() }
    val framePadding = with(density) { 8.dp.toPx() }
    var points by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var frame by remember { mutableStateOf<Rect?>(null) }
    val hint = stringResource(R.string.overlay_select_hint)
    val clearText = stringResource(R.string.overlay_select_clear)
    val closeText = stringResource(R.string.overlay_close)
    val topInteraction = remember { MutableInteractionSource() }
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(minSize, framePadding) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            points = listOf(offset)
                            frame = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val last = points.lastOrNull()
                            if (last == null || (last - change.position).getDistance() > 2f) {
                                points = points + change.position
                            }
                        },
                        onDragEnd = {
                            val next = selectionFrame(
                                points = points,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                padding = framePadding,
                            )
                            if (next == null || next.width < minSize || next.height < minSize) {
                                points = emptyList()
                                frame = null
                                return@detectDragGestures
                            }
                            frame = next
                            onSelected(next)
                        },
                        onDragCancel = {
                            points = emptyList()
                            frame = null
                        },
                    )
                },
        ) {
            val scrim = scheme.scrim.copy(alpha = 0.44f)
            val current = frame
            if (current != null) {
                val shape = Path().apply {
                    addRoundRect(RoundRect(current, CornerRadius(20.dp.toPx())))
                }
                val outside = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(Offset.Zero, size))
                    addPath(shape)
                }
                drawPath(path = outside, color = scrim)
                drawPath(
                    path = shape,
                    color = Color.White.copy(alpha = 0.14f),
                    blendMode = BlendMode.Plus,
                )
                drawPath(
                    path = shape,
                    color = scheme.primary,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            if (current == null && points.isNotEmpty()) {
                val ink = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (index in 1 until points.size) {
                        lineTo(points[index].x, points[index].y)
                    }
                }
                drawPath(
                    path = ink,
                    color = scheme.primary.copy(alpha = 0.28f),
                    style = Stroke(
                        width = 14.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawPath(
                    path = ink,
                    color = scheme.primary,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            val edge = 3.5.dp.toPx()
            drawRect(
                color = scheme.onSurface.copy(alpha = 0.5f),
                topLeft = Offset(edge, edge),
                size = Size(
                    (size.width - edge * 2f).coerceAtLeast(0f),
                    (size.height - edge * 2f).coerceAtLeast(0f),
                ),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = topInteraction,
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(50),
            color = scheme.surfaceContainerHigh.copy(alpha = 0.92f),
            contentColor = scheme.onSurface,
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = hapticTap { onClose() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = closeText,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = hint,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (points.isNotEmpty()) {
                    TextButton(
                        onClick = hapticTap {
                            points = emptyList()
                            frame = null
                            onClear()
                        },
                    ) {
                        Icon(
                            imageVector = FocusIcons.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = clearText,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun selectionFrame(
    points: List<Offset>,
    width: Float,
    height: Float,
    padding: Float,
): Rect? {
    if (points.size < 3 || width <= 0f || height <= 0f) return null
    val left = (points.minOf { it.x } - padding).coerceIn(0f, width)
    val top = (points.minOf { it.y } - padding).coerceIn(0f, height)
    val right = (points.maxOf { it.x } + padding).coerceIn(0f, width)
    val bottom = (points.maxOf { it.y } + padding).coerceIn(0f, height)
    if (right - left < 1f || bottom - top < 1f) return null
    return Rect(left, top, right, bottom)
}
