package fan.x0.focus.feature.assistant.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.designsystem.components.FocusGroupedPickerSheet
import fan.x0.focus.core.designsystem.components.FocusPickerGroup
import fan.x0.focus.core.designsystem.components.FocusPickerItem
import fan.x0.focus.core.designsystem.components.FocusPickerSelection
import fan.x0.focus.feature.assistant.ModelPickerTarget
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState

@Composable
internal fun SettingsModelPickerSheet(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    if (!state.modelPickerOpen) return
    val groups = remember(state.providerModelGroups) {
        state.providerModelGroups.map { group ->
            FocusPickerGroup(
                id = group.providerId,
                title = group.providerName,
                icon = group.icon,
                items = group.models.map { model ->
                    FocusPickerItem(
                        id = model.id,
                        label = model.name.ifBlank { model.id },
                    )
                },
            )
        }
    }
    when (state.modelPickerTarget) {
        ModelPickerTarget.Active -> ModelPicker(
            title = stringResource(R.string.settings_model_picker_title),
            groups = groups,
            state = state,
            selected = FocusPickerSelection(state.activeProviderId, state.activeModel),
            onSelect = { onAction(SettingsAction.SelectActiveModel(it.groupId, it.itemId)) },
            onRefresh = { onAction(SettingsAction.RefreshActiveModels) },
            onAction = onAction,
        )
        ModelPickerTarget.Judge -> ModelPicker(
            title = stringResource(R.string.settings_judge_model_item),
            groups = groups,
            state = state,
            selected = FocusPickerSelection(state.judgeProviderId, state.judgeModel),
            onSelect = { onAction(SettingsAction.SelectModel(it.groupId, it.itemId)) },
            onRefresh = { onAction(SettingsAction.RefreshJudgeModels) },
            onAction = onAction,
        )
    }
}

@Composable
private fun ModelPicker(
    title: String,
    groups: List<FocusPickerGroup>,
    state: SettingsUiState,
    selected: FocusPickerSelection,
    onSelect: (FocusPickerSelection) -> Unit,
    onRefresh: () -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    FocusGroupedPickerSheet(
        title = title,
        searchHint = stringResource(R.string.settings_model_search_hint),
        emptyText = stringResource(
            if (state.providerModelsLoading) {
                R.string.settings_models_loading
            } else {
                R.string.settings_models_empty
            },
        ),
        groups = groups,
        query = state.modelQuery,
        onQueryChange = { onAction(SettingsAction.ModelQueryChanged(it)) },
        onSelect = onSelect,
        onDismiss = { onAction(SettingsAction.CloseModelPicker) },
        selected = selected,
        loading = state.providerModelsLoading,
        onRefresh = onRefresh,
    )
}
