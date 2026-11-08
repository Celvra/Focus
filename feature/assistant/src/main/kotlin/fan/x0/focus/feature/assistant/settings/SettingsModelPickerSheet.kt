package fan.x0.focus.feature.assistant.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.modelpicker.ModelPickerGroup
import fan.x0.focus.core.modelpicker.ModelPickerSheet
import fan.x0.focus.core.modelpicker.rememberModelPickerGroups
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
    val groups = rememberModelPickerGroups(state.providerModelGroups)
    when (state.modelPickerTarget) {
        ModelPickerTarget.Active -> ModelPicker(
            title = stringResource(R.string.settings_model_picker_title),
            groups = groups,
            state = state,
            selectedProviderId = state.activeProviderId,
            selectedModelId = state.activeModel,
            onSelect = { providerId, modelId ->
                onAction(SettingsAction.SelectActiveModel(providerId, modelId))
            },
            onRefresh = { onAction(SettingsAction.RefreshActiveModels) },
            onAction = onAction,
        )
        ModelPickerTarget.Judge -> ModelPicker(
            title = stringResource(R.string.settings_judge_model_item),
            groups = groups,
            state = state,
            selectedProviderId = state.judgeProviderId,
            selectedModelId = state.judgeModel,
            onSelect = { providerId, modelId ->
                onAction(SettingsAction.SelectModel(providerId, modelId))
            },
            onRefresh = { onAction(SettingsAction.RefreshJudgeModels) },
            onAction = onAction,
        )
    }
}

@Composable
private fun ModelPicker(
    title: String,
    groups: List<ModelPickerGroup>,
    state: SettingsUiState,
    selectedProviderId: String,
    selectedModelId: String,
    onSelect: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    ModelPickerSheet(
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
        selectedProviderId = selectedProviderId,
        selectedModelId = selectedModelId,
        loading = state.providerModelsLoading,
        onRefresh = onRefresh,
    )
}
