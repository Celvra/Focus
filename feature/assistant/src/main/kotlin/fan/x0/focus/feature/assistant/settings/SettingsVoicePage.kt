package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
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
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.data.voice.LocalModelState
import fan.x0.focus.data.voice.VoiceEngine
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.VoiceSettingsAction
import fan.x0.focus.feature.assistant.VoiceSettingsUiState
import fan.x0.focus.feature.assistant.VoiceSettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsVoiceRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: VoiceSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsVoicePage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsVoicePage(
    state: VoiceSettingsUiState,
    onAction: (VoiceSettingsAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_voice_title),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_voice_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "engine") {
            FocusReveal(index = 1) {
                FocusCardGroup(title = stringResource(R.string.settings_voice_engine)) {
                    formItem(
                        headlineContent = { Text(stringResource(R.string.settings_voice_engine)) },
                        leadingContent = {
                            FocusCardGroupIcon(
                                icon = FocusIcons.Assistant,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VoiceEngine.entries.forEach { engine ->
                                FocusChip(
                                    label = stringResource(engine.labelRes()),
                                    selected = state.config.engine == engine,
                                    onClick = { onAction(VoiceSettingsAction.SetEngine(engine)) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.config.engine == VoiceEngine.Local) {
            item(key = "local") {
                FocusReveal(index = 2) {
                    FocusCardGroup(title = stringResource(R.string.settings_voice_local)) {
                        formItem(
                            headlineContent = { Text(stringResource(R.string.settings_voice_local_model)) },
                            supportingContent = {
                                Text(stringResource(R.string.settings_voice_local_size))
                            },
                        ) {
                            LocalModelContent(
                                state = state.model,
                                onDownload = { onAction(VoiceSettingsAction.DownloadModel) },
                                onDelete = { onAction(VoiceSettingsAction.DeleteModel) },
                                onRefresh = { onAction(VoiceSettingsAction.RefreshModel) },
                            )
                        }
                    }
                }
            }
        }
        if (state.config.engine == VoiceEngine.Cloud) {
            item(key = "cloud") {
                FocusReveal(index = 2) {
                    FocusCardGroup(title = stringResource(R.string.settings_voice_cloud)) {
                        formItem(
                            headlineContent = { Text(stringResource(R.string.settings_voice_cloud_provider)) },
                        ) {
                            if (state.providers.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.settings_voice_cloud_no_provider),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    state.providers.forEach { provider ->
                                        FocusChip(
                                            label = provider.name,
                                            selected = state.config.cloudProviderId == provider.id,
                                            onClick = {
                                                onAction(VoiceSettingsAction.SetCloudProvider(provider.id))
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        formItem(
                            headlineContent = { Text(stringResource(R.string.settings_voice_cloud_model)) },
                        ) {
                            OutlinedTextField(
                                value = state.config.cloudModel,
                                onValueChange = {
                                    onAction(VoiceSettingsAction.SetCloudModel(it))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = stringResource(R.string.settings_voice_cloud_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalModelContent(
    state: LocalModelState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
) {
    when (state) {
        LocalModelState.Missing -> {
            FocusPrimaryButton(
                text = stringResource(R.string.settings_voice_local_download),
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is LocalModelState.Downloading -> {
            val progress = if (state.total > 0) {
                state.downloaded.toFloat() / state.total
            } else {
                0f
            }
            LinearWavyProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.settings_voice_local_progress,
                    (progress * 100).toInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            FocusOutlinedButton(
                text = stringResource(R.string.settings_voice_local_cancel),
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        LocalModelState.Extracting -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.settings_voice_local_extracting),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LocalModelState.Ready -> {
            Text(
                text = stringResource(R.string.settings_voice_local_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FocusOutlinedButton(
                text = stringResource(R.string.settings_voice_local_delete),
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        is LocalModelState.Failed -> {
            Text(
                text = state.error.message(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            FocusPrimaryButton(
                text = stringResource(R.string.settings_voice_local_retry),
                onClick = {
                    onRefresh()
                    onDownload()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

private fun VoiceEngine.labelRes(): Int = when (this) {
    VoiceEngine.Local -> R.string.settings_voice_engine_local
    VoiceEngine.Cloud -> R.string.settings_voice_engine_cloud
}
