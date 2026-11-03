package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.network.ProviderHeader
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState

@Composable
internal fun ProviderAdvancedTab(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        UserAgentSection(state = state, onAction = onAction)
        HeaderSection(state = state, onAction = onAction)
    }
}

@Composable
private fun UserAgentSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.userAgent,
            onValueChange = { onAction(SettingsAction.UserAgentChanged(it)) },
            label = { Text(stringResource(R.string.settings_provider_user_agent)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.settings_provider_user_agent_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeaderSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_provider_headers_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_provider_headers_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.headers.forEachIndexed { index, header ->
            ProviderHeaderEditor(
                header = header,
                onNameChange = { onAction(SettingsAction.HeaderNameChanged(index, it)) },
                onValueChange = { onAction(SettingsAction.HeaderValueChanged(index, it)) },
                onRemove = { onAction(SettingsAction.RemoveHeader(index)) },
            )
        }
        FocusTextButton(
            text = stringResource(R.string.settings_provider_header_add),
            onClick = { onAction(SettingsAction.AddHeader) },
        )
    }
}

@Composable
private fun ProviderHeaderEditor(
    header: ProviderHeader,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = header.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.settings_provider_header_name)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = hapticTap(onRemove)) {
                Icon(
                    imageVector = FocusIcons.Delete,
                    contentDescription = stringResource(R.string.settings_provider_header_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = header.value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.settings_provider_header_value)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
