package fan.x0.focus.feature.assistant.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.network.ProviderAuthType
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.feature.assistant.R

@Composable
internal fun ChatProtocol.displayName(): String = stringResource(
    when (this) {
        ChatProtocol.ChatCompletions -> R.string.settings_protocol_chat
        ChatProtocol.Responses -> R.string.settings_protocol_responses
        ChatProtocol.AnthropicMessages -> R.string.settings_protocol_anthropic
    },
)

internal fun ProviderAuthType.labelRes(): Int = when (this) {
    ProviderAuthType.Default -> R.string.settings_provider_auth_default
    ProviderAuthType.Bearer -> R.string.settings_provider_auth_bearer
    ProviderAuthType.ApiKeyHeader -> R.string.settings_provider_auth_header
    ProviderAuthType.Query -> R.string.settings_provider_auth_query
    ProviderAuthType.Basic -> R.string.settings_provider_auth_basic
    ProviderAuthType.None -> R.string.settings_provider_auth_none
}

internal fun ProviderAuthType.bodyRes(): Int = when (this) {
    ProviderAuthType.Default -> R.string.settings_provider_auth_default_body
    ProviderAuthType.Bearer -> R.string.settings_provider_auth_bearer_body
    ProviderAuthType.ApiKeyHeader -> R.string.settings_provider_auth_header_body
    ProviderAuthType.Query -> R.string.settings_provider_auth_query_body
    ProviderAuthType.Basic -> R.string.settings_provider_auth_basic_body
    ProviderAuthType.None -> R.string.settings_provider_auth_none_body
}
