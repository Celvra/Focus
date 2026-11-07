package fan.x0.focus.feature.overlay

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.data.device.display.VirtualDisplayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class DisplayWindowMode { Normal, Hover, Minimized }

internal const val DISPLAY_WINDOW_WIDTH_DP = 200
internal const val DISPLAY_WINDOW_PADDING_DP = 8
internal const val DISPLAY_WINDOW_CHROME_DP = 112
internal const val DISPLAY_WINDOW_HOVER_DP = 96
internal const val DISPLAY_WINDOW_BUBBLE_DP = 48
private const val LONG_PRESS_MS = 450L

@Composable
internal fun DisplayWindow(
    mode: DisplayWindowMode,
    state: VirtualDisplayState,
    preview: State<String?>,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResize: (Offset) -> Unit,
    onTap: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
    onSwipe: (Int, Int, Int, Int) -> Unit,
    onHover: () -> Unit,
    onMinimize: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) {
    when (mode) {
        DisplayWindowMode.Normal -> DisplayWindowNormal(
            state = state,
            preview = preview,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onResize = onResize,
            onTap = onTap,
            onLongPress = onLongPress,
            onSwipe = onSwipe,
            onHover = onHover,
            onMinimize = onMinimize,
            onClose = onClose,
        )

        DisplayWindowMode.Hover -> DisplayWindowHover(
            preview = preview,
            onRestore = onRestore,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )

        DisplayWindowMode.Minimized -> DisplayWindowBubble(onRestore = onRestore)
    }
}

@Composable
private fun DisplayWindowNormal(
    state: VirtualDisplayState,
    preview: State<String?>,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResize: (Offset) -> Unit,
    onTap: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
    onSwipe: (Int, Int, Int, Int) -> Unit,
    onHover: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = FocusShapeTokens.cardLarge
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), shape),
            shape = shape,
            color = scheme.surfaceContainerHigh.copy(alpha = 0.96f),
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(DISPLAY_WINDOW_PADDING_DP.dp)) {
                DisplayWindowHeader(
                    state = state,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                    onMenuClick = { menuOpen = !menuOpen },
                )
                DisplayWindowScreen(
                    state = state,
                    preview = preview,
                    onTap = onTap,
                    onLongPress = onLongPress,
                    onSwipe = onSwipe,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(scheme.surfaceContainerLowest),
                )
                DisplayWindowFooter(onResize = onResize)
            }
        }
        if (menuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { menuOpen = false },
            )
            DisplayWindowMenu(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 8.dp)
                    .width(160.dp),
                onHover = {
                    menuOpen = false
                    onHover()
                },
                onMinimize = {
                    menuOpen = false
                    onMinimize()
                },
                onClose = {
                    menuOpen = false
                    onClose()
                },
            )
        }
    }
}

@Composable
private fun DisplayWindowHeader(
    state: VirtualDisplayState,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 24.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.45f)),
            )
        }
        Icon(
            imageVector = FocusIcons.Device,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.overlay_display_window_title),
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.overlay_display_window_id, state.displayId),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onMenuClick) {
            Icon(
                imageVector = FocusIcons.MoreHoriz,
                contentDescription = stringResource(R.string.overlay_display_window_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DisplayWindowMenu(
    modifier: Modifier,
    onHover: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), shape),
        shape = shape,
        color = scheme.surfaceContainerHighest,
        shadowElevation = 8.dp,
    ) {
        Column {
            DisplayWindowMenuItem(
                text = stringResource(R.string.overlay_display_window_hover),
                onClick = onHover,
            )
            DisplayWindowMenuItem(
                text = stringResource(R.string.overlay_display_window_minimize),
                onClick = onMinimize,
            )
            DisplayWindowMenuItem(
                text = stringResource(R.string.overlay_display_window_close),
                color = scheme.error,
                onClick = onClose,
            )
        }
    }
}

@Composable
private fun DisplayWindowMenuItem(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun DisplayWindowScreen(
    state: VirtualDisplayState,
    preview: State<String?>,
    onTap: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
    onSwipe: (Int, Int, Int, Int) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.displayTakeover(
            displayWidth = state.width,
            displayHeight = state.height,
            onTap = onTap,
            onLongPress = onLongPress,
            onSwipe = onSwipe,
        ),
        contentAlignment = Alignment.Center,
    ) {
        DisplayWindowPreview(
            preview = preview,
            waiting = stringResource(R.string.overlay_display_window_waiting),
            description = stringResource(R.string.overlay_display_window_screen),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DisplayWindowFooter(onResize: (Offset) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.overlay_display_window_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        DisplayResizeHandle(onResize = onResize)
    }
}

@Composable
private fun DisplayResizeHandle(onResize: (Offset) -> Unit) {
    val description = stringResource(R.string.overlay_display_window_resize)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onResize(dragAmount)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val stroke = 1.5.dp.toPx()
            drawLine(
                color = color,
                start = Offset(size.width * 0.35f, size.height),
                end = Offset(size.width, size.height * 0.35f),
                strokeWidth = stroke,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.05f, size.height),
                end = Offset(size.width, size.height * 0.05f),
                strokeWidth = stroke,
            )
        }
    }
}

@Composable
private fun DisplayWindowHover(
    preview: State<String?>,
    onRestore: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val description = stringResource(R.string.overlay_display_window_restore)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), shape)
            .semantics {
                contentDescription = description
                onClick(label = description) {
                    onRestore()
                    true
                }
            }
            .hoverGesture(onTap = onRestore, onDrag = onDrag, onDragEnd = onDragEnd),
        shape = shape,
        color = scheme.surfaceContainerLowest,
        shadowElevation = 12.dp,
    ) {
        DisplayWindowPreview(
            preview = preview,
            waiting = stringResource(R.string.overlay_display_window_waiting),
            description = stringResource(R.string.overlay_display_window_screen),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DisplayWindowBubble(onRestore: () -> Unit) {
    val description = stringResource(R.string.overlay_display_window_restore)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .clickable(onClick = onRestore)
            .semantics { contentDescription = description },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 12.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = FocusIcons.Device,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun DisplayWindowPreview(
    preview: State<String?>,
    waiting: String,
    description: String,
    contentScale: ContentScale,
    modifier: Modifier,
) {
    val raw by preview
    val frame = remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(raw) {
        if (raw == null) {
            frame.value = null
            return@LaunchedEffect
        }
        frame.value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Base64.decode(raw, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val image = frame.value
    if (image == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = waiting,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Image(
            bitmap = image,
            contentDescription = description,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}

private fun Modifier.hoverGesture(
    onTap: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragged = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.position - change.previousPosition
            if (!dragged && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                dragged = true
            }
            if (dragged) {
                change.consume()
                onDrag(delta)
            }
            if (!change.pressed) {
                if (dragged) onDragEnd() else onTap()
                break
            }
        }
    }
}

private fun Modifier.displayTakeover(
    displayWidth: Int,
    displayHeight: Int,
    onTap: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
    onSwipe: (Int, Int, Int, Int) -> Unit,
): Modifier = pointerInput(displayWidth, displayHeight) {
    if (displayWidth <= 0 || displayHeight <= 0) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        var last = start
        var dragged = false
        val startedAt = System.currentTimeMillis()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!dragged && (change.position - start).getDistance() > viewConfiguration.touchSlop) {
                dragged = true
            }
            if (dragged) {
                change.consume()
                last = change.position
            }
            if (!change.pressed) {
                val startPoint = mapToDisplay(start, size, displayWidth, displayHeight)
                val endPoint = mapToDisplay(last, size, displayWidth, displayHeight)
                if (dragged) {
                    if (startPoint != null && endPoint != null) {
                        onSwipe(startPoint.first, startPoint.second, endPoint.first, endPoint.second)
                    }
                } else if (startPoint != null) {
                    if (System.currentTimeMillis() - startedAt >= LONG_PRESS_MS) {
                        onLongPress(startPoint.first, startPoint.second)
                    } else {
                        onTap(startPoint.first, startPoint.second)
                    }
                }
                break
            }
        }
    }
}

private fun mapToDisplay(
    position: Offset,
    size: IntSize,
    displayWidth: Int,
    displayHeight: Int,
): Pair<Int, Int>? {
    val scale = min(size.width.toFloat() / displayWidth, size.height.toFloat() / displayHeight)
    if (scale <= 0f) return null
    val drawWidth = displayWidth * scale
    val drawHeight = displayHeight * scale
    val left = (size.width - drawWidth) / 2f
    val top = (size.height - drawHeight) / 2f
    val x = position.x - left
    val y = position.y - top
    if (x < 0f || y < 0f || x > drawWidth || y > drawHeight) return null
    return (x / scale).roundToInt().coerceIn(0, displayWidth - 1) to
        (y / scale).roundToInt().coerceIn(0, displayHeight - 1)
}
