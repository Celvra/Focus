package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.switchItem
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState

@Composable
internal fun SettingsNotificationsPage(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_notifications),
        onBack = onBack,
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_page_notifications_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "push") {
            FocusReveal(index = 1) {
                FocusCardGroup(title = stringResource(R.string.settings_notifications_title)) {
                    switchItem(
                        checked = state.notificationsEnabled,
                        onCheckedChange = { onAction(SettingsAction.NotificationsEnabledChanged(it)) },
                        headlineContent = { Text(stringResource(R.string.settings_notifications_item)) },
                        supportingContent = { Text(stringResource(R.string.settings_notifications_body)) },
                        leadingContent = {
                            FocusCardGroupIcon(
                                icon = FocusIcons.Notification,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        checkedIcon = FocusIcons.Check,
                    )
                }
            }
        }
    }
}
