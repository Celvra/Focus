package fan.x0.focus.feature.crew

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import fan.x0.focus.core.designsystem.components.AvatarState
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.hapticSelect
import fan.x0.focus.data.assistant.CrewAvatars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val avatarCache = LruCache<String, ImageBitmap>(32)

@Composable
fun CrewAvatar(
    preset: Int,
    palette: Int,
    path: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    state: AvatarState = AvatarState.Idle,
) {
    val image by produceState<ImageBitmap?>(initialValue = path?.let(avatarCache::get), path) {
        if (path == null) {
            value = null
            return@produceState
        }
        val cached = avatarCache.get(path)
        if (cached != null) {
            value = cached
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }?.also { avatarCache.put(path, it) }
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        FocusAvatar(
            style = AvatarStyle(preset = preset, palette = palette),
            size = size,
            state = state,
            modifier = modifier,
        )
    }
}

@Composable
fun CrewAvatarPicker(
    preset: Int,
    palette: Int,
    path: String?,
    onPreset: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    onPickPhoto: (() -> Unit)? = null,
    onClearPhoto: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CrewAvatar(preset = preset, palette = palette, path = path, size = 64.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (onPickPhoto != null) {
                    FocusTextButton(
                        text = stringResource(R.string.crew_pick_photo),
                        onClick = { if (enabled) onPickPhoto() },
                        enabled = enabled,
                    )
                }
                if (path != null && onClearPhoto != null) {
                    FocusTextButton(
                        text = stringResource(R.string.crew_remove_photo),
                        onClick = { if (enabled) onClearPhoto() },
                        enabled = enabled,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(CrewAvatars.PRESET_COUNT) { option ->
                val selected = path == null && option == preset
                val label = stringResource(R.string.crew_avatar_style, option + 1)
                FocusAvatar(
                    style = AvatarStyle(option, palette),
                    size = 38.dp,
                    modifier = Modifier
                        .border(
                            width = 2.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape,
                        )
                        .padding(2.dp)
                        .semantics { contentDescription = label }
                        .clickable(
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = hapticSelect { onPreset(option, palette) },
                        ),
                )
            }
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(CrewAvatars.PALETTE_COUNT) { option ->
                val selected = path == null && option == palette
                val label = stringResource(R.string.crew_avatar_color, option + 1)
                FocusAvatar(
                    style = AvatarStyle(preset, option),
                    size = 34.dp,
                    modifier = Modifier
                        .border(
                            width = 2.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape,
                        )
                        .padding(2.dp)
                        .semantics { contentDescription = label }
                        .clickable(
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = hapticSelect { onPreset(preset, option) },
                        ),
                )
            }
        }
    }
}

@Composable
fun CrewMentionChips(
    mentions: List<CrewMentionChipUi>,
    modifier: Modifier = Modifier,
) {
    if (mentions.isEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mentions.forEachIndexed { index, mention ->
            key(mention.name, index) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = "@${mention.name}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
