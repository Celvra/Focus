package fan.x0.focus.feature.assistant.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusTabContent
import fan.x0.focus.core.designsystem.components.FocusTabSwitcher
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState

private enum class ProviderEditorTab {
    Connection,
    Models,
    Advanced,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderEditorSheet(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by rememberSaveable { mutableIntStateOf(ProviderEditorTab.Connection.ordinal) }
    ModalBottomSheet(
        onDismissRequest = { onAction(SettingsAction.CloseEditor) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding(),
        ) {
            ProviderEditorHeader(state = state, onAction = onAction)
            FocusTabSwitcher(
                labels = listOf(
                    stringResource(R.string.settings_provider_tab_connection),
                    stringResource(R.string.settings_provider_tab_models),
                    stringResource(R.string.settings_provider_tab_advanced),
                ),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            FocusTabContent(
                selectedIndex = tab,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { index ->
                when (ProviderEditorTab.entries[index]) {
                    ProviderEditorTab.Connection -> ProviderConnectionTab(
                        state = state,
                        onAction = onAction,
                    )
                    ProviderEditorTab.Models -> ProviderModelsTab(
                        state = state,
                        onAction = onAction,
                    )
                    ProviderEditorTab.Advanced -> ProviderAdvancedTab(
                        state = state,
                        onAction = onAction,
                    )
                }
            }
            ProviderEditorActions(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun ProviderEditorHeader(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                if (state.isNewProvider) {
                    R.string.settings_provider_editor_new
                } else {
                    R.string.settings_provider_editor_edit
                },
            ),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.weight(1f),
        )
        if (state.canDelete) {
            IconButton(onClick = hapticTap { onAction(SettingsAction.AskDeleteProvider) }) {
                Icon(
                    imageVector = FocusIcons.Delete,
                    contentDescription = stringResource(R.string.settings_provider_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = hapticTap { onAction(SettingsAction.CloseEditor) }) {
            Icon(
                imageVector = FocusIcons.Close,
                contentDescription = stringResource(R.string.settings_provider_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderEditorActions(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = state.connectionError != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            state.connectionError?.let { error ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error.message(),
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusOutlinedButton(
                text = stringResource(
                    when {
                        state.testing -> R.string.assistant_testing
                        state.connectionOk -> R.string.assistant_connected
                        else -> R.string.assistant_test
                    },
                ),
                onClick = { onAction(SettingsAction.TestProvider) },
                enabled = !state.testing,
                loading = state.testing,
                icon = if (state.connectionOk) FocusIcons.Check else FocusIcons.Bolt,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            )
            FocusPrimaryButton(
                text = stringResource(
                    if (state.isNewProvider) R.string.settings_provider_add else R.string.assistant_save,
                ),
                onClick = { onAction(SettingsAction.SaveProvider) },
                enabled = !state.savingConnection,
                loading = state.savingConnection,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
            )
        }
    }
}
