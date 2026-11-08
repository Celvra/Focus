package fan.x0.focus.core.modelpicker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.BrandIcon
import fan.x0.focus.core.designsystem.components.BrandIcons
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusPickerCapability
import fan.x0.focus.core.designsystem.components.FocusPickerDetail
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.components.rememberProviderImage
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.core.network.openai.ModelMeta
import fan.x0.focus.data.assistant.ProviderModel
import fan.x0.focus.data.assistant.ProviderModelGroup
import kotlinx.coroutines.launch
import java.util.Locale

data class ModelPickerModel(
    val id: String,
    val label: String,
    val detail: FocusPickerDetail? = null,
)

data class ModelPickerGroup(
    val id: String,
    val title: String,
    val icon: String = "",
    val models: List<ModelPickerModel> = emptyList(),
)

data class ModelPickerLabels(
    val context: String,
    val cutoff: String,
    val vision: String,
    val tools: String,
    val reasoning: String,
)

@Composable
fun rememberModelPickerLabels(): ModelPickerLabels = ModelPickerLabels(
    context = stringResource(R.string.model_picker_context),
    cutoff = stringResource(R.string.model_picker_cutoff),
    vision = stringResource(R.string.model_picker_vision),
    tools = stringResource(R.string.model_picker_tools),
    reasoning = stringResource(R.string.model_picker_reasoning),
)

fun ModelMeta.toModelPickerDetail(labels: ModelPickerLabels): FocusPickerDetail? {
    val meta = buildList {
        contextWindow?.takeIf { it > 0 }?.let { add(labels.context.format(contextLabel(it))) }
        if (inputPrice != null || outputPrice != null) {
            add("${money(inputPrice)} / ${money(outputPrice)} /M")
        }
        knowledgeCutoff?.let { add(labels.cutoff.format(it)) }
    }.joinToString(" · ")

    val capabilities = buildList {
        if (supportsImage == true) add(FocusPickerCapability(FocusIcons.Photo, labels.vision))
        if (supportsTools == true) add(FocusPickerCapability(FocusIcons.Tool, labels.tools))
        if (supportsReasoning == true) add(FocusPickerCapability(FocusIcons.Idea, labels.reasoning))
    }

    val summary = description?.trim()?.ifBlank { null }
    if (meta.isBlank() && capabilities.isEmpty() && summary == null) return null
    return FocusPickerDetail(
        meta = meta.ifBlank { null },
        capabilities = capabilities,
        description = summary,
    )
}

@Composable
fun rememberModelPickerGroups(groups: List<ProviderModelGroup>): List<ModelPickerGroup> {
    val labels = rememberModelPickerLabels()
    return remember(groups, labels) {
        groups.map { group ->
            ModelPickerGroup(
                id = group.providerId,
                title = group.providerName,
                icon = group.icon,
                models = group.models.map { model ->
                    ModelPickerModel(
                        id = model.id,
                        label = model.name.ifBlank { model.id },
                        detail = model.meta.toModelPickerDetail(labels),
                    )
                },
            )
        }
    }
}

@Composable
fun rememberModelPickerGroup(
    providerId: String,
    providerName: String,
    icon: String = "",
    models: List<ProviderModel>,
): ModelPickerGroup {
    val labels = rememberModelPickerLabels()
    return remember(providerId, providerName, icon, models, labels) {
        ModelPickerGroup(
            id = providerId,
            title = providerName,
            icon = icon,
            models = models.map { model ->
                ModelPickerModel(id = model.id, label = model.name.ifBlank { model.id })
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ModelPickerSheet(
    title: String,
    searchHint: String,
    emptyText: String,
    groups: List<ModelPickerGroup>,
    onQueryChange: (String) -> Unit,
    onSelect: (providerId: String, modelId: String) -> Unit,
    onDismiss: () -> Unit,
    query: String = "",
    selectedProviderId: String? = null,
    selectedModelId: String? = null,
    loading: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var collapsed by remember { mutableStateOf(emptySet<String>()) }
    val keyword = query.trim()

    val visibleGroups = remember(groups, keyword, collapsed) {
        groups.mapNotNull { group ->
            val models = if (keyword.isBlank()) {
                group.models
            } else {
                group.models.filter {
                    it.label.contains(keyword, ignoreCase = true) ||
                        it.id.contains(keyword, ignoreCase = true)
                }
            }
            if (models.isEmpty()) null else group to models
        }
    }

    val positions = remember(visibleGroups, keyword, collapsed) {
        var index = 0
        visibleGroups.associate { (group, models) ->
            val position = index
            index += 1
            if (keyword.isNotBlank() || group.id !in collapsed) index += models.size
            group.id to position
        }
    }

    LaunchedEffect(keyword) {
        if (keyword.isNotBlank()) collapsed = emptySet()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .imePadding()
                .padding(bottom = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = LocalFocusTypography.current.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.model_picker_count, groups.sumOf { it.models.size }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    loading -> FocusLoadingBlob(size = 20.dp)
                    onRefresh != null -> FilledTonalIconButton(onClick = hapticTap(onRefresh)) {
                        Icon(
                            imageVector = FocusIcons.Refresh,
                            contentDescription = stringResource(R.string.model_picker_refresh),
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
                FilledTonalIconButton(onClick = hapticTap(onDismiss)) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.model_picker_close),
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(searchHint) },
                leadingIcon = {
                    Icon(
                        imageVector = FocusIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            if (visibleGroups.isEmpty()) {
                if (loading) {
                    FocusLoadingBlob(
                        size = 40.dp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 32.dp),
                    )
                } else {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
                return@Column
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleGroups.map { it.first }, key = { it.id }) { group ->
                    AssistChip(
                        onClick = {
                            val position = positions[group.id] ?: return@AssistChip
                            scope.launch { listState.animateScrollToItem(position) }
                        },
                        label = { Text(group.title, maxLines = 1) },
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleGroups.forEach { (group, models) ->
                    stickyHeader(key = "provider:${group.id}", contentType = "provider") {
                        ProviderHeader(
                            group = group,
                            expanded = keyword.isNotBlank() || group.id !in collapsed,
                            onToggle = {
                                if (keyword.isBlank()) {
                                    collapsed = if (group.id in collapsed) {
                                        collapsed - group.id
                                    } else {
                                        collapsed + group.id
                                    }
                                }
                            },
                        )
                    }
                    if (keyword.isNotBlank() || group.id !in collapsed) {
                        items(models, key = { "${group.id}:${it.id}" }, contentType = { "model" }) { model ->
                            ModelRow(
                                group = group,
                                model = model,
                                selected = group.id == selectedProviderId && model.id == selectedModelId,
                                onClick = { onSelect(group.id, model.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderHeader(
    group: ModelPickerGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "modelProviderChevron",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = hapticTap(onToggle))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = FocusIcons.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(16.dp)
                .rotate(rotation),
        )
        ProviderAvatar(icon = group.icon, size = 22.dp)
        Text(
            text = group.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.model_picker_model_count, group.models.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelRow(
    group: ModelPickerGroup,
    model: ModelPickerModel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = hapticTap(onClick),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainer,
            contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModelAvatar(model = model, selected = selected)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = model.label,
                        style = LocalFocusTypography.current.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = FocusIcons.Check,
                            contentDescription = stringResource(R.string.model_picker_selected),
                            tint = scheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                model.detail?.meta?.let { meta ->
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.72f) else scheme.onSurfaceVariant,
                    )
                }
                val capabilities = model.detail?.capabilities.orEmpty()
                if (capabilities.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        capabilities.forEach { capability ->
                            CapabilityChip(capability = capability)
                        }
                    }
                }
                model.detail?.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) scheme.onPrimaryContainer.copy(alpha = 0.72f) else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityChip(capability: FocusPickerCapability) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = capability.icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = capability.label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}


@Composable
private fun ModelAvatar(
    model: ModelPickerModel,
    selected: Boolean,
) {
    val brand = BrandIcons.forModel(model.id)
    Surface(
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (brand != null) {
                BrandIcon(
                    icon = brand,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = FocusIcons.Assistant,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun ProviderAvatar(
    icon: String,
    size: androidx.compose.ui.unit.Dp,
) {
    val image = rememberProviderImage(icon)
    if (image != null) {
        Surface(
            modifier = Modifier.size(size),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(size * 0.12f),
            )
        }
        return
    }
    FocusIconBadge(
        icon = FocusIcons.Assistant,
        size = size,
        container = MaterialTheme.colorScheme.secondaryContainer,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

internal fun contextLabel(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${trimDecimal(Math.round(tokens / 100_000.0) / 10.0)}M"
    tokens >= 1_000 -> "${Math.round(tokens / 1_000.0)}K"
    else -> tokens.toString()
}

private fun money(value: Double?): String = value?.let { "$${trimDecimal(it)}" } ?: "-"

private fun trimDecimal(value: Double): String {
    val text = String.format(Locale.US, "%.4f", value)
    return text.trimEnd('0').trimEnd('.')
}
