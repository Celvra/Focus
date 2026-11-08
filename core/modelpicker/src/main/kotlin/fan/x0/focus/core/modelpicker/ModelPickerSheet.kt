package fan.x0.focus.core.modelpicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.designsystem.components.FocusGroupedPickerSheet
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusPickerCapability
import fan.x0.focus.core.designsystem.components.FocusPickerDetail
import fan.x0.focus.core.designsystem.components.FocusPickerGroup
import fan.x0.focus.core.designsystem.components.FocusPickerItem
import fan.x0.focus.core.designsystem.components.FocusPickerSelection
import fan.x0.focus.core.network.openai.ModelMeta
import fan.x0.focus.data.assistant.ProviderModel
import fan.x0.focus.data.assistant.ProviderModelGroup
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
    val pickerGroups = remember(groups) {
        groups.map { group ->
            FocusPickerGroup(
                id = group.id,
                title = group.title,
                icon = group.icon,
                items = group.models.map { model ->
                    FocusPickerItem(
                        id = model.id,
                        label = model.label,
                        detail = model.detail,
                    )
                },
            )
        }
    }
    val selected = remember(selectedProviderId, selectedModelId) {
        if (selectedProviderId == null || selectedModelId == null) {
            null
        } else {
            FocusPickerSelection(selectedProviderId, selectedModelId)
        }
    }
    FocusGroupedPickerSheet(
        title = title,
        searchHint = searchHint,
        emptyText = emptyText,
        groups = pickerGroups,
        query = query,
        onQueryChange = onQueryChange,
        onSelect = { onSelect(it.groupId, it.itemId) },
        onDismiss = onDismiss,
        selected = selected,
        loading = loading,
        onRefresh = onRefresh,
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
