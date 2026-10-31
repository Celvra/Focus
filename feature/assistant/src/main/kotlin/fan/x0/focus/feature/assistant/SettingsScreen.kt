package fan.x0.focus.feature.assistant

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import fan.x0.focus.core.designsystem.components.FocusSearchPickerSheet
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.core.designsystem.theme.FocusMotionDefaults
import fan.x0.focus.feature.assistant.settings.SettingsAccountPage
import fan.x0.focus.feature.assistant.settings.SettingsAppearancePage
import fan.x0.focus.feature.assistant.settings.SettingsChatAgentPage
import fan.x0.focus.feature.assistant.settings.SettingsChatDetailRoute
import fan.x0.focus.feature.assistant.settings.SettingsDataPage
import fan.x0.focus.feature.assistant.settings.SettingsDestination
import fan.x0.focus.feature.assistant.settings.SettingsHomePage
import fan.x0.focus.feature.assistant.settings.SettingsMcpRoute
import fan.x0.focus.feature.assistant.settings.SettingsToolsPage
import fan.x0.focus.feature.assistant.settings.ProviderEditorSheet
import fan.x0.focus.feature.assistant.settings.SettingsWebSearchRoute
import fan.x0.focus.feature.assistant.settings.SettingsWorkspaceIndexRoute
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoute(
    onExit: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    val saved = stringResource(R.string.settings_provider_saved)
    val connected = stringResource(R.string.assistant_connection_ok)
    val deleted = stringResource(R.string.settings_provider_deleted)
    val cleared = stringResource(R.string.settings_data_cleared)
    val reset = stringResource(R.string.settings_reset_done)
    CollectEffect(viewModel.effect) { effect ->
        val message = when (effect) {
            SettingsEffect.ConnectionSaved -> saved
            SettingsEffect.ConnectionOk -> connected
            SettingsEffect.ProviderDeleted -> deleted
            SettingsEffect.DataCleared -> cleared
            SettingsEffect.Reset -> reset
        }
        snackbar.showSnackbar(message)
    }
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbar = snackbar,
        onExit = onExit,
    )
}

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    snackbar: SnackbarHostState,
    onExit: () -> Unit,
) {
    val backStack = rememberSaveable(
        saver = listSaver(
            save = { pages -> pages.map(SettingsDestination::name) },
            restore = { names ->
                mutableStateListOf(
                    *names.mapNotNull { name ->
                        SettingsDestination.entries.firstOrNull { it.name == name }
                    }.ifEmpty { listOf(SettingsDestination.Home) }.toTypedArray(),
                )
            },
        ),
    ) { mutableStateListOf(SettingsDestination.Home) }
    val motion = FocusMotionDefaults
    val latestState = rememberUpdatedState(state)
    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                onExit()
            }
        },
        transitionSpec = {
            (
                slideInHorizontally(motion.spatialDefault()) { it / 5 } +
                    fadeIn(motion.effectsDefault())
                ) togetherWith fadeOut(motion.effectsFast())
        },
        popTransitionSpec = {
            (
                slideInHorizontally(motion.spatialDefault()) { -it / 5 } +
                    fadeIn(motion.effectsDefault())
                ) togetherWith (
                slideOutHorizontally(motion.spatialFast()) { it / 5 } +
                    fadeOut(motion.effectsFast())
                )
        },
        predictivePopTransitionSpec = {
            (
                slideInHorizontally(motion.spatialDefault()) { -it / 5 } +
                    fadeIn(motion.effectsDefault())
                ) togetherWith (
                slideOutHorizontally(motion.spatialFast()) { it / 5 } +
                    fadeOut(motion.effectsFast())
                )
        },
        entryProvider = { destination ->
            NavEntry(key = destination) {
                when (destination) {
                    SettingsDestination.Home -> SettingsHomePage(
                        onOpen = { backStack.add(it) },
                    )
                    SettingsDestination.Account -> SettingsAccountPage(
                        state = latestState.value,
                        onAction = onAction,
                        onBack = { backStack.removeLastOrNull() },
                    )
                    SettingsDestination.ChatAgent -> SettingsChatAgentPage(
                        state = latestState.value,
                        onAction = onAction,
                        onBack = { backStack.removeLastOrNull() },
                    )
                    SettingsDestination.Tools -> SettingsToolsPage(
                        state = latestState.value,
                        onAction = onAction,
                        onOpen = { backStack.add(it) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                    SettingsDestination.WebSearch -> SettingsWebSearchRoute(
                        onBack = { backStack.removeLastOrNull() },
                        snackbar = snackbar,
                    )
                    SettingsDestination.Mcp -> SettingsMcpRoute(
                        onBack = { backStack.removeLastOrNull() },
                        snackbar = snackbar,
                    )
                    SettingsDestination.WorkspaceIndex -> SettingsWorkspaceIndexRoute(
                        onBack = { backStack.removeLastOrNull() },
                        snackbar = snackbar,
                    )
                    SettingsDestination.ChatDetail -> SettingsChatDetailRoute(
                        onBack = { backStack.removeLastOrNull() },
                        snackbar = snackbar,
                    )
                    SettingsDestination.Appearance -> SettingsAppearancePage(
                        state = latestState.value,
                        onAction = onAction,
                        onBack = { backStack.removeLastOrNull() },
                    )
                    SettingsDestination.Data -> SettingsDataPage(
                        state = latestState.value,
                        onAction = onAction,
                        snackbar = snackbar,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            }
        },
    )
    if (state.editorOpen) {
        ProviderEditorSheet(state = state, onAction = onAction)
    }
    if (state.modelPickerOpen) {
        val target = state.modelPickerTarget
        val refresh: (() -> Unit)? = when (target) {
            ModelPickerTarget.Judge -> ({ onAction(SettingsAction.RefreshJudgeModels) })
            ModelPickerTarget.Subagent -> ({ onAction(SettingsAction.RefreshSubagentModels) })
            ModelPickerTarget.Provider -> null
        }
        FocusSearchPickerSheet(
            title = stringResource(
                when (target) {
                    ModelPickerTarget.Judge -> R.string.settings_judge_model_item
                    ModelPickerTarget.Subagent -> R.string.settings_subagent_model_item
                    ModelPickerTarget.Provider -> R.string.settings_model_picker_title
                },
            ),
            searchHint = stringResource(R.string.settings_model_search_hint),
            emptyText = stringResource(R.string.settings_models_empty),
            items = when (target) {
                ModelPickerTarget.Judge -> state.judgeModels
                ModelPickerTarget.Subagent -> state.subagentModels
                ModelPickerTarget.Provider -> state.models
            },
            query = state.modelQuery,
            onQueryChange = { onAction(SettingsAction.ModelQueryChanged(it)) },
            onSelect = { onAction(SettingsAction.SelectModel(it)) },
            onDismiss = { onAction(SettingsAction.CloseModelPicker) },
            selected = when (target) {
                ModelPickerTarget.Judge -> state.judgeModel
                ModelPickerTarget.Subagent -> state.subagentModel
                ModelPickerTarget.Provider -> state.model
            },
            onRefresh = refresh,
        )
    }
}
