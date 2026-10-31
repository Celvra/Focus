package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.assistant.ChatDetailConfig
import fan.x0.focus.data.assistant.ChatStatField
import fan.x0.focus.feature.assistant.ChatDetailAction
import fan.x0.focus.feature.assistant.ChatDetailEffect
import fan.x0.focus.feature.assistant.ChatDetailSettingsViewModel
import fan.x0.focus.feature.assistant.ChatDetailUiState
import fan.x0.focus.feature.assistant.R
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsChatDetailRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: ChatDetailSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved = stringResource(R.string.settings_chat_detail_saved)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            ChatDetailEffect.Saved -> snackbar.showSnackbar(saved)
        }
    }
    SettingsChatDetailPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsChatDetailPage(
    state: ChatDetailUiState,
    onAction: (ChatDetailAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_chat_detail),
        onBack = onBack,
        snackbar = snackbar,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_chat_detail_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "fields") {
            FocusReveal(index = 1) {
                FocusCardGroup(title = stringResource(R.string.settings_chat_detail_fields)) {
                    state.config.order.forEachIndexed { index, field ->
                        FieldRow(
                            field = field,
                            enabled = field in state.config.enabled,
                            first = index == 0,
                            last = index == state.config.order.lastIndex,
                            onToggle = { onAction(ChatDetailAction.Toggle(field, it)) },
                            onUp = { onAction(ChatDetailAction.Move(field, -1)) },
                            onDown = { onAction(ChatDetailAction.Move(field, 1)) },
                        )
                    }
                }
            }
        }
        item(key = "preview") {
            FocusReveal(index = 2) {
                FocusCardGroup(title = stringResource(R.string.settings_chat_detail_preview)) {
                    item(
                        headlineContent = { PreviewLine(config = state.config) },
                        leadingContent = {
                            FocusCardGroupIcon(
                                icon = FocusIcons.Chat,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                    )
                }
            }
        }
        item(key = "reset") {
            FocusReveal(index = 3) {
                FocusTextButton(
                    text = stringResource(R.string.settings_chat_detail_reset),
                    onClick = { onAction(ChatDetailAction.Reset) },
                )
            }
        }
    }
}

private fun FocusCardGroupScope.FieldRow(
    field: ChatStatField,
    enabled: Boolean,
    first: Boolean,
    last: Boolean,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    item(
        headlineContent = { Text(stringResource(field.labelRes())) },
        supportingContent = {
            Text(
                text = if (enabled) {
                    stringResource(R.string.settings_chat_detail_shown)
                } else {
                    stringResource(R.string.settings_chat_detail_hidden)
                },
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onUp, enabled = !first) {
                    Icon(
                        imageVector = FocusIcons.ArrowDown,
                        contentDescription = stringResource(R.string.settings_chat_detail_move_up),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(180f),
                    )
                }
                IconButton(onClick = onDown, enabled = !last) {
                    Icon(
                        imageVector = FocusIcons.ArrowDown,
                        contentDescription = stringResource(R.string.settings_chat_detail_move_down),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                )
            }
        },
    )
}

@Composable
private fun PreviewLine(config: ChatDetailConfig) {
    val labels = config.order
        .filter { it in config.enabled }
        .map { stringResource(it.labelRes()) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = labels.joinToString(" · ").ifBlank {
                    stringResource(R.string.settings_chat_detail_hidden)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun ChatStatField.labelRes(): Int = when (this) {
    ChatStatField.Provider -> R.string.settings_chat_field_provider
    ChatStatField.Model -> R.string.settings_chat_field_model
    ChatStatField.Input -> R.string.settings_chat_field_input
    ChatStatField.Output -> R.string.settings_chat_field_output
    ChatStatField.Total -> R.string.settings_chat_field_total
    ChatStatField.Cached -> R.string.settings_chat_field_cached
    ChatStatField.Round -> R.string.settings_chat_field_round
    ChatStatField.Step -> R.string.settings_chat_field_step
    ChatStatField.Duration -> R.string.settings_chat_field_duration
}
