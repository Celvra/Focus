package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens

@Composable
fun FocusImageLightbox(
    image: ImageBitmap,
    title: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val scheme = MaterialTheme.colorScheme
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.scrim.copy(alpha = 0.96f)),
        ) {
            LightboxImage(image = image, title = title)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = FocusShapeTokens.pill,
                    color = scheme.surfaceContainer.copy(alpha = 0.86f),
                    contentColor = scheme.onSurface,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
                Surface(
                    onClick = hapticTap(onDismiss),
                    shape = CircleShape,
                    color = scheme.surfaceContainer.copy(alpha = 0.9f),
                    contentColor = scheme.onSurface,
                ) {
                    Box(
                        modifier = Modifier.size(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = FocusIcons.Close,
                            contentDescription = stringResource(R.string.common_image_close),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LightboxImage(image: ImageBitmap, title: String) {
    var container by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(image) { mutableFloatStateOf(MIN_SCALE) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    val haptics = LocalFocusHaptics.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { container = it }
            .pointerInput(image, container) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val center = Offset(container.width / 2f, container.height / 2f)
                    val focus = centroid - center - offset
                    val next = offset + pan + focus * (1f - newScale / oldScale)
                    scale = newScale
                    offset = clampOffset(next, newScale, container, image)
                }
            }
            .pointerInput(image, container) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        haptics.select()
                        if (scale > MIN_SCALE + 0.01f) {
                            scale = MIN_SCALE
                            offset = Offset.Zero
                        } else {
                            val center = Offset(container.width / 2f, container.height / 2f)
                            val focus = tap - center
                            scale = DOUBLE_TAP_SCALE
                            offset = clampOffset(
                                -focus * (DOUBLE_TAP_SCALE - 1f),
                                DOUBLE_TAP_SCALE,
                                container,
                                image,
                            )
                        }
                    },
                )
            },
    ) {
        Image(
            bitmap = image,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private fun clampOffset(
    value: Offset,
    scale: Float,
    container: IntSize,
    image: ImageBitmap,
): Offset {
    if (container.width <= 0 || container.height <= 0 || image.width <= 0 || image.height <= 0) {
        return Offset.Zero
    }
    val fit = minOf(
        container.width.toFloat() / image.width,
        container.height.toFloat() / image.height,
    )
    val width = image.width * fit * scale
    val height = image.height * fit * scale
    val maxX = ((width - container.width) / 2f).coerceAtLeast(0f)
    val maxY = ((height - container.height) / 2f).coerceAtLeast(0f)
    return Offset(
        value.x.coerceIn(-maxX, maxX),
        value.y.coerceIn(-maxY, maxY),
    )
}

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f
