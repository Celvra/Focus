package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.R

data class FocusVoiceUi(
    val active: Boolean = false,
    val busy: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val level: () -> Float = { 0f },
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusVoiceHud(
    voice: FocusVoiceUi,
    modifier: Modifier = Modifier,
) {
    val text = voice.error
        ?: voice.status
        ?: if (voice.active) stringResource(R.string.common_voice_listening) else null
        ?: if (voice.busy) stringResource(R.string.common_voice_busy) else null
    if (text == null) return
    val scheme = MaterialTheme.colorScheme
    val error = voice.error != null
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (error) scheme.errorContainer else scheme.secondaryContainer,
        contentColor = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (voice.busy) {
                LoadingIndicator(modifier = Modifier.size(16.dp))
            } else if (voice.active) {
                VoiceWave(color = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer, level = voice.level)
            } else {
                Icon(
                    imageVector = FocusIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VoiceWave(
    color: Color,
    level: () -> Float,
) {
    Canvas(modifier = Modifier.size(width = 22.dp, height = 16.dp)) {
        val value = level().coerceIn(0f, 1f)
        drawBar(color, 0.45f, value, 0f)
        drawBar(color, 1f, value, 1f)
        drawBar(color, 0.62f, value, 2f)
    }
}

private fun DrawScope.drawBar(
    color: Color,
    factor: Float,
    level: Float,
    index: Float,
) {
    val width = 3.dp.toPx()
    val gap = 3.dp.toPx()
    val maxHeight = size.height
    val height = maxHeight * (0.28f + 0.72f * level * factor)
    val x = index * (width + gap)
    val top = (maxHeight - height) / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(x, top),
        size = Size(width, height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f),
    )
}
