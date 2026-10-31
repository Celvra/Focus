package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.WebSearchProvider
import fan.x0.focus.data.assistant.WebSearchResult
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.WebSearchAction
import fan.x0.focus.feature.assistant.WebSearchEffect
import fan.x0.focus.feature.assistant.WebSearchProviderStatus
import fan.x0.focus.feature.assistant.WebSearchSettingsViewModel
import fan.x0.focus.feature.assistant.WebSearchUiState
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsWebSearchRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: WebSearchSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved = stringResource(R.string.settings_web_saved)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            WebSearchEffect.Saved -> snackbar.showSnackbar(saved)
        }
    }
    SettingsWebSearchPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsWebSearchPage(
    state: WebSearchUiState,
    onAction: (WebSearchAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_web_title),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_web_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "default") {
            FocusReveal(index = 1) {
                FocusCardGroup(title = stringResource(R.string.settings_web_default)) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.settings_web_provider)) },
                        leadingContent = {
                            FocusCardGroupIcon(
                                icon = FocusIcons.Search,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WebSearchProvider.entries.forEach { provider ->
                                FocusChip(
                                    label = provider.name,
                                    selected = state.defaultProvider == provider,
                                    onClick = { onAction(WebSearchAction.SetDefault(provider)) },
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "providers") {
            FocusReveal(index = 2) {
                FocusCardGroup(title = stringResource(R.string.settings_web_title)) {
                    state.providers.forEach { status ->
                        ProviderRow(
                            status = status,
                            onOpen = { onAction(WebSearchAction.Open(status.provider)) },
                            onEnabled = { onAction(WebSearchAction.SetEnabled(status.provider, it)) },
                        )
                    }
                }
            }
        }
    }
    state.editing?.let { provider ->
        ProviderEditorSheet(
            provider = provider,
            state = state,
            onAction = onAction,
        )
    }
}

private fun FocusCardGroupScope.ProviderRow(
    status: WebSearchProviderStatus,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    val configured = if (status.provider.requiresKey) status.configured else true
    item(
        headlineContent = { Text(status.provider.name) },
        supportingContent = {
            Text(
                text = when {
                    !status.provider.requiresKey -> stringResource(R.string.settings_web_no_key)
                    status.configured -> stringResource(R.string.settings_web_configured)
                    else -> stringResource(R.string.settings_web_not_configured)
                },
            )
        },
        leadingContent = {
            FocusCardGroupIcon(
                icon = FocusIcons.Search,
                containerColor = if (configured) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (configured) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
        },
        trailingContent = {
            Switch(
                checked = status.enabled,
                onCheckedChange = onEnabled,
            )
        },
        onClick = onOpen,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditorSheet(
    provider: WebSearchProvider,
    state: WebSearchUiState,
    onAction: (WebSearchAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onAction(WebSearchAction.Close) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Text(
                        text = stringResource(R.string.settings_web_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onAction(WebSearchAction.Close) }) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.settings_provider_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (provider.requiresKey) {
                OutlinedTextField(
                    value = state.keyInput,
                    onValueChange = { onAction(WebSearchAction.KeyChanged(it)) },
                    label = { Text(stringResource(R.string.settings_web_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.keySaved) {
                    FocusStatusPill(
                        text = stringResource(R.string.settings_key_saved),
                        tone = StatusTone.Success,
                    )
                }
            } else {
                FocusStatusPill(
                    text = stringResource(R.string.settings_web_no_key),
                    tone = StatusTone.Neutral,
                )
            }
            state.error?.let { error ->
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusOutlinedButton(
                    text = stringResource(R.string.settings_web_test),
                    onClick = { onAction(WebSearchAction.Test) },
                    enabled = !state.testing,
                    loading = state.testing,
                    icon = FocusIcons.Bolt,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
                FocusPrimaryButton(
                    text = stringResource(R.string.settings_web_save),
                    onClick = { onAction(WebSearchAction.Save) },
                    enabled = !state.testing,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
            }
            if (state.results.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_web_results),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                state.results.forEach { result ->
                    WebResultCard(result)
                }
            }
        }
    }
}

@Composable
private fun WebResultCard(result: WebSearchResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.snippet.isNotBlank()) {
                Text(
                    text = result.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
