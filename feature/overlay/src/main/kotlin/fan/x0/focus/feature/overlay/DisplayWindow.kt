package fan.x0.focus.feature.overlay

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.data.device.display.VirtualDisplayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

internal const val DISPLAY_WINDOW_WIDTH_DP = 240
internal const val DISPLAY_WINDOW_PADDING_DP = 10
private const val DISPLAY_WINDOW_SCREEN_WIDTH_DP = DISPLAY_WINDOW_WIDTH_DP - DISPLAY_WINDOW_PADDING_DP * 2
private const val LONG_PRESS_MS = 450L

@Composable
internal fun DisplayWindow(
    state: VirtualDisplayState,
    preview: State<String?>,
    onDrag: (Offset) -> Unit,
    onClose: () -> Unit,
    onTap: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
    onSwipe: (Int, Int, Int, Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = FocusShapeTokens.cardLarge
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), shape),
        shape = shape,
        color = scheme.surfaceContainerHigh.copy(alpha = 0.96f),
        shadowElevation = 16.dp,
    ) {
        Column(modifier = Modifier.padding(DISPLAY_WINDOW_PADDING_DP.dp)) {
            DisplayWindowHeader(state = state, onDrag = onDrag, onClose = onClose)
            Spacer(modifier = Modifier.height(8.dp))
            DisplayWindowScreen(
                state = state,
                preview = preview,
                onTap = onTap,
                onLongPress = onLongPress,
                onSwipe = onSwipe,
            )
            Text(
                text = stringResource(R.string.overlay_display_window_hint),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

@Composable
private fun DisplayWindowHeader(
    state: VirtualDisplayState,
    onDrag: (Offset) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    }
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
        IconButton(onClick = onClose) {
            Icon(
                imageVector = FocusIcons.Stop,
                contentDescription = stringResource(R.string.overlay_display_window_close),
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DisplayWindowScreen(
    state: VirtualDisplayState,
    preview: State<String?>,
    onTap: (Int, Int) -> Unit,
    onLongPress: (Int, Int) -> Unit,
    onSwipe: (Int, Int, Int, Int) -> Unit,
) {
    val ratio = if (state.width > 0 && state.height > 0) {
        state.width.toFloat() / state.height
    } else {
        9f / 16f
    }
    val height = (DISPLAY_WINDOW_SCREEN_WIDTH_DP / ratio).dp.coerceIn(140.dp, 320.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .displayTakeover(
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
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DisplayWindowPreview(
    preview: State<String?>,
    waiting: String,
    description: String,
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
        Text(
            text = waiting,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Image(
            bitmap = image,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
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
