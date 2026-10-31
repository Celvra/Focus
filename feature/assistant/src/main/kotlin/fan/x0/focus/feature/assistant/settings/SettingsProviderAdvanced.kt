package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.network.ProviderAuthType
import fan.x0.focus.core.network.ProviderHeader
import fan.x0.focus.core.network.openai.ChatProtocol
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
        ProtocolSection(state = state, onAction = onAction)
        RequiresKeyRow(state = state, onAction = onAction)
        AuthSection(state = state, onAction = onAction)
        UserAgentSection(state = state, onAction = onAction)
        HeaderSection(state = state, onAction = onAction)
        if (state.balanceSupported) {
            BalanceSection(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun ProtocolSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.settings_protocol_title))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusChip(
                label = stringResource(R.string.settings_protocol_chat),
                selected = state.protocol == ChatProtocol.ChatCompletions,
                onClick = { onAction(SettingsAction.ProtocolChanged(ChatProtocol.ChatCompletions)) },
            )
            FocusChip(
                label = stringResource(R.string.settings_protocol_responses),
                selected = state.protocol == ChatProtocol.Responses,
                onClick = { onAction(SettingsAction.ProtocolChanged(ChatProtocol.Responses)) },
            )
            FocusChip(
                label = stringResource(R.string.settings_protocol_anthropic),
                selected = state.protocol == ChatProtocol.AnthropicMessages,
                onClick = { onAction(SettingsAction.ProtocolChanged(ChatProtocol.AnthropicMessages)) },
            )
        }
    }
}

@Composable
private fun RequiresKeyRow(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAction(SettingsAction.RequiresKeyChanged(!state.requiresKey)) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_provider_requires_key),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_provider_requires_key_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = state.requiresKey,
            onCheckedChange = { onAction(SettingsAction.RequiresKeyChanged(it)) },
        )
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
private fun AuthSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.settings_provider_auth_title))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProviderAuthType.entries.forEach { type ->
                FocusChip(
                    label = stringResource(type.labelRes()),
                    selected = state.authType == type,
                    onClick = { onAction(SettingsAction.AuthTypeChanged(type)) },
                )
            }
        }
        Text(
            text = stringResource(state.authType.bodyRes()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (state.authType) {
            ProviderAuthType.ApiKeyHeader -> OutlinedTextField(
                value = state.authName,
                onValueChange = { onAction(SettingsAction.AuthNameChanged(it)) },
                label = { Text(stringResource(R.string.settings_provider_auth_header_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ProviderAuthType.Query -> OutlinedTextField(
                value = state.authName,
                onValueChange = { onAction(SettingsAction.AuthNameChanged(it)) },
                label = { Text(stringResource(R.string.settings_provider_auth_query_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            ProviderAuthType.Basic -> OutlinedTextField(
                value = state.authUsername,
                onValueChange = { onAction(SettingsAction.AuthUsernameChanged(it)) },
                label = { Text(stringResource(R.string.settings_provider_auth_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Unit
        }
    }
}

@Composable
private fun HeaderSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.settings_provider_headers_title))
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
            IconButton(onClick = onRemove) {
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

@Composable
private fun BalanceSection(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(stringResource(R.string.settings_provider_balance))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.balance?.let(::formatBalance) ?: "—",
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (state.fetchingBalance) {
                FocusLoadingBlob(size = 16.dp)
            }
            IconButton(onClick = { onAction(SettingsAction.FetchBalance) }) {
                Icon(
                    imageVector = FocusIcons.Refresh,
                    contentDescription = stringResource(R.string.settings_provider_balance_refresh),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        state.balanceError?.let { error ->
            Text(
                text = error.message(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun ProviderAuthType.labelRes(): Int = when (this) {
    ProviderAuthType.Default -> R.string.settings_provider_auth_default
    ProviderAuthType.Bearer -> R.string.settings_provider_auth_bearer
    ProviderAuthType.ApiKeyHeader -> R.string.settings_provider_auth_header
    ProviderAuthType.Query -> R.string.settings_provider_auth_query
    ProviderAuthType.Basic -> R.string.settings_provider_auth_basic
    ProviderAuthType.None -> R.string.settings_provider_auth_none
}

private fun ProviderAuthType.bodyRes(): Int = when (this) {
    ProviderAuthType.Default -> R.string.settings_provider_auth_default_body
    ProviderAuthType.Bearer -> R.string.settings_provider_auth_bearer_body
    ProviderAuthType.ApiKeyHeader -> R.string.settings_provider_auth_header_body
    ProviderAuthType.Query -> R.string.settings_provider_auth_query_body
    ProviderAuthType.Basic -> R.string.settings_provider_auth_basic_body
    ProviderAuthType.None -> R.string.settings_provider_auth_none_body
}
