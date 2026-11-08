package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCard
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.feature.assistant.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsPromptsRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: PromptsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved = stringResource(R.string.settings_prompts_saved)
    CollectEffect(viewModel.effect) { snackbar.showSnackbar(saved) }
    SettingsPromptsPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
private fun SettingsPromptsPage(
    state: PromptsUiState,
    onAction: (PromptsAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_prompts),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_page_prompts_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "main") {
            FocusReveal(index = 1) {
                PromptCard(
                    title = stringResource(R.string.settings_prompts_main_title),
                    body = stringResource(R.string.settings_prompts_main_body),
                    hint = stringResource(R.string.settings_prompts_main_hint),
                    value = state.main,
                    onValueChange = { onAction(PromptsAction.MainChanged(it)) },
                )
            }
        }
        item(key = "side") {
            FocusReveal(index = 2) {
                PromptCard(
                    title = stringResource(R.string.settings_prompts_side_title),
                    body = stringResource(R.string.settings_prompts_side_body),
                    hint = stringResource(R.string.settings_prompts_side_hint),
                    value = state.side,
                    onValueChange = { onAction(PromptsAction.SideChanged(it)) },
                )
            }
        }
        item(key = "save") {
            FocusReveal(index = 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    FocusPrimaryButton(
                        text = stringResource(R.string.settings_prompts_save),
                        onClick = { onAction(PromptsAction.Save) },
                        loading = state.saving,
                        enabled = !state.saving,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptCard(
    title: String,
    body: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    FocusCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(hint) },
            minLines = 6,
            maxLines = 12,
            supportingText = {
                Text(stringResource(R.string.settings_prompts_empty))
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
