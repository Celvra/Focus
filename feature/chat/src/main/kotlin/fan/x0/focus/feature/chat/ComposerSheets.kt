package fan.x0.focus.feature.chat

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.BrandIcon
import fan.x0.focus.core.designsystem.components.BrandIcons
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.rememberProviderImage
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.data.assistant.ReasoningLevel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val MAX_HINT_DURATION_MS = 3000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderPickerSheet(
    providers: List<ProviderOption>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_picker_provider_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (providers.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_picker_provider_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                providers.forEach { provider ->
                    val active = provider.id == selected
                    val image = rememberProviderImage(provider.icon)
                    val brand = BrandIcons.forKey(provider.icon)
                        ?: BrandIcons.forProvider(provider.id, provider.name)
                    Surface(
                        onClick = { onSelect(provider.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.largeIncreased,
                        color = if (active) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        contentColor = if (active) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (image != null) {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                ) {
                                    Image(
                                        bitmap = image,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            } else if (brand != null) {
                                BrandIcon(
                                    icon = brand,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(28.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = provider.name.trim().take(1).uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                        )
                                    }
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = provider.model.ifBlank {
                                        stringResource(R.string.chat_picker_model_empty)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (active) {
                                Icon(
                                    imageVector = FocusIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ReasoningPreviewContent(
    val level: ReasoningLevel,
    val boosted: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReasoningPickerSheet(
    level: ReasoningLevel,
    model: String,
    onSelect: (ReasoningLevel) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val levels = ReasoningLevel.entries
    val sliderState = rememberSliderState(
        value = levels.indexOf(level).coerceAtLeast(0).toFloat(),
        steps = (levels.size - 2).coerceAtLeast(0),
        trackRange = 0f..levels.lastIndex.toFloat(),
    )
    val preview = levels[sliderState.value.roundToInt().coerceIn(0, levels.lastIndex)]
    val motion = LocalFocusMotion.current
    val boostTitle = stringResource(
        if (model.contains("deepseek", ignoreCase = true)) {
            R.string.chat_reasoning_max_deepseek_title
        } else {
            R.string.chat_reasoning_max_boost_title
        },
    )
    val maxEntryAt = remember(preview) {
        if (preview == ReasoningLevel.Max) SystemClock.uptimeMillis() else 0L
    }
    var tick by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(maxEntryAt) {
        if (maxEntryAt == 0L) return@LaunchedEffect
        val elapsed = SystemClock.uptimeMillis() - maxEntryAt
        if (elapsed < MAX_HINT_DURATION_MS) delay(MAX_HINT_DURATION_MS - elapsed)
        tick = SystemClock.uptimeMillis()
    }
    val boosted = preview == ReasoningLevel.Max &&
        maxEntryAt > 0L &&
        tick - maxEntryAt < MAX_HINT_DURATION_MS
    val content = ReasoningPreviewContent(preview, boosted)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_picker_reasoning_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AnimatedContent(
                targetState = content,
                transitionSpec = {
                    if (targetState.level == initialState.level) {
                        fadeIn(motion.effectsDefault()) togetherWith
                            fadeOut(motion.effectsDefault())
                    } else {
                        val up = targetState.level.ordinal > initialState.level.ordinal
                        val enter = slideInVertically(motion.spatialDefault()) { if (up) it else -it } +
                            fadeIn(motion.effectsFast())
                        val exit = slideOutVertically(motion.spatialDefault()) { if (up) -it else it } +
                            fadeOut(motion.effectsFast())
                        enter togetherWith exit
                    }
                },
                label = "reasoningPreview",
            ) { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (item.boosted) {
                            boostTitle
                        } else {
                            stringResource(item.level.labelRes())
                        },
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(
                            if (item.boosted) R.string.chat_reasoning_max_boost_desc else item.level.descriptionRes(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Slider(
                state = sliderState,
                onValueChange = { sliderState.value = it },
                onValueChangeFinished = {
                    val index = sliderState.value.roundToInt().coerceIn(0, levels.lastIndex)
                    sliderState.value = index.toFloat()
                    onSelect(levels[index])
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
    FullScreenChargingRipple(
        active = preview == ReasoningLevel.Max,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentSheet(
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onPickFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_attach_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AttachmentTile(
                    icon = FocusIcons.Camera,
                    label = stringResource(R.string.chat_attach_camera),
                    onClick = onTakePhoto,
                    modifier = Modifier.weight(1f),
                )
                AttachmentTile(
                    icon = FocusIcons.Photo,
                    label = stringResource(R.string.chat_attach_photos),
                    onClick = onPickPhotos,
                    modifier = Modifier.weight(1f),
                )
                AttachmentTile(
                    icon = FocusIcons.UploadFile,
                    label = stringResource(R.string.chat_attach_files),
                    onClick = onPickFiles,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AttachmentTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun ReasoningLevel.labelRes(): Int = when (this) {
    ReasoningLevel.Off -> R.string.chat_reasoning_off
    ReasoningLevel.Auto -> R.string.chat_reasoning_auto
    ReasoningLevel.Low -> R.string.chat_reasoning_low
    ReasoningLevel.Medium -> R.string.chat_reasoning_medium
    ReasoningLevel.High -> R.string.chat_reasoning_high
    ReasoningLevel.XHigh -> R.string.chat_reasoning_xhigh
    ReasoningLevel.Max -> R.string.chat_reasoning_max
}

internal fun ReasoningLevel.descriptionRes(): Int = when (this) {
    ReasoningLevel.Off -> R.string.chat_reasoning_off_desc
    ReasoningLevel.Auto -> R.string.chat_reasoning_auto_desc
    ReasoningLevel.Low -> R.string.chat_reasoning_low_desc
    ReasoningLevel.Medium -> R.string.chat_reasoning_medium_desc
    ReasoningLevel.High -> R.string.chat_reasoning_high_desc
    ReasoningLevel.XHigh -> R.string.chat_reasoning_xhigh_desc
    ReasoningLevel.Max -> R.string.chat_reasoning_max_desc
}
