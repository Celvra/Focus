package fan.x0.focus.feature.assistant.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.BrandIcon
import fan.x0.focus.core.designsystem.components.BrandIcons
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.rememberProviderImage
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ProviderBalance
import fan.x0.focus.data.assistant.ProviderConfig
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState

@Composable
internal fun SettingsAccountPage(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_account),
        onBack = onBack,
        actions = {
            IconButton(onClick = { onAction(SettingsAction.AddProvider) }) {
                Icon(
                    imageVector = FocusIcons.Add,
                    contentDescription = stringResource(R.string.settings_provider_add),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_page_account_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "providers") {
            FocusReveal(index = 1) {
                ProvidersGroup(state = state, onAction = onAction)
            }
        }
    }
    if (state.deleteConfirm) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.CancelDeleteProvider) },
            title = { Text(stringResource(R.string.settings_provider_delete_title)) },
            text = { Text(stringResource(R.string.settings_provider_delete_body, state.draftName)) },
            confirmButton = {
                TextButton(onClick = { onAction(SettingsAction.DeleteProvider) }) {
                    Text(stringResource(R.string.assistant_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(SettingsAction.CancelDeleteProvider) }) {
                    Text(stringResource(R.string.assistant_cancel))
                }
            },
        )
    }
}

@Composable
private fun ProvidersGroup(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    FocusCardGroup(title = stringResource(R.string.settings_providers_title)) {
        state.providers.forEach { provider ->
            val active = provider.id == state.activeProviderId
            item(
                selected = active,
                headlineContent = {
                    Text(
                        text = provider.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Column {
                        Text(
                            text = provider.baseUrl,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = provider.model.ifBlank {
                                stringResource(R.string.settings_provider_no_model)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = protocolLabel(provider.protocol),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.providerBalances[provider.id]?.let { balance ->
                            Text(
                                text = formatBalance(balance),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                leadingContent = {
                    ProviderBadge(provider = provider, active = active)
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProviderCheck(active = active)
                        IconButton(onClick = { onAction(SettingsAction.OpenProvider(provider.id)) }) {
                            Icon(
                                imageVector = FocusIcons.Edit,
                                contentDescription = stringResource(R.string.settings_provider_edit),
                            )
                        }
                    }
                },
                onClick = { onAction(SettingsAction.SelectProvider(provider.id)) },
            )
        }
        item(
            headlineContent = { Text(stringResource(R.string.settings_provider_add)) },
            supportingContent = { Text(stringResource(R.string.settings_provider_add_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Add,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            onClick = { onAction(SettingsAction.AddProvider) },
        )
    }
}

@Composable
private fun protocolLabel(protocol: ChatProtocol): String = stringResource(
    when (protocol) {
        ChatProtocol.ChatCompletions -> R.string.settings_protocol_chat
        ChatProtocol.Responses -> R.string.settings_protocol_responses
        ChatProtocol.AnthropicMessages -> R.string.settings_protocol_anthropic
    },
)

internal fun formatBalance(balance: ProviderBalance): String {
    val symbol = when (balance.currency.uppercase()) {
        "USD" -> "$"
        "CNY" -> "¥"
        else -> balance.currency.uppercase() + " "
    }
    val sign = if (balance.amount < 0) "-" else ""
    return sign + symbol + String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(balance.amount))
}

@Composable
private fun ProviderBadge(provider: ProviderConfig, active: Boolean) {
    val container by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "providerBadgeContainer",
    )
    val content by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "providerBadgeContent",
    )
    val image = rememberProviderImage(provider.icon)
    val painter = BrandIcons.forKey(provider.icon)
        ?: BrandIcons.forProvider(provider.id, provider.name)
    Surface(
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                image != null -> Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                painter != null -> BrandIcon(
                    icon = painter,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                else -> Text(
                    text = provider.name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ProviderCheck(active: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "providerCheckAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.6f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "providerCheckScale",
    )
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = FocusIcons.Check,
            contentDescription = if (active) stringResource(R.string.settings_provider_active) else null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

