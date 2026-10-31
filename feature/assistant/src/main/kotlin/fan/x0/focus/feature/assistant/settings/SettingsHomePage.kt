package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.feature.assistant.R

@Composable
internal fun SettingsHomePage(onOpen: (SettingsDestination) -> Unit) {
    SettingsPageScaffold(title = stringResource(R.string.settings_home_title)) {
        item(key = "intro") {
            FocusReveal {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_home_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "pages") {
            FocusReveal(index = 1) {
                val accountTitle = stringResource(R.string.settings_page_account)
                val accountBody = stringResource(R.string.settings_page_account_body)
                val chatTitle = stringResource(R.string.settings_page_chat_agent)
                val chatBody = stringResource(R.string.settings_page_chat_agent_body)
                val toolsTitle = stringResource(R.string.settings_page_tools)
                val toolsBody = stringResource(R.string.settings_page_tools_body)
                val detailTitle = stringResource(R.string.settings_page_chat_detail)
                val detailBody = stringResource(R.string.settings_page_chat_detail_body)
                val appearanceTitle = stringResource(R.string.settings_page_appearance)
                val appearanceBody = stringResource(R.string.settings_page_appearance_body)
                val notificationsTitle = stringResource(R.string.settings_page_notifications)
                val notificationsBody = stringResource(R.string.settings_page_notifications_body)
                val dataTitle = stringResource(R.string.settings_page_data)
                val dataBody = stringResource(R.string.settings_page_data_body)
                FocusCardGroup {
                    pageItem(
                        title = accountTitle,
                        body = accountBody,
                        icon = FocusIcons.Provider,
                        onClick = { onOpen(SettingsDestination.Account) },
                    )
                    pageItem(
                        title = chatTitle,
                        body = chatBody,
                        icon = FocusIcons.Bolt,
                        onClick = { onOpen(SettingsDestination.ChatAgent) },
                    )
                    pageItem(
                        title = toolsTitle,
                        body = toolsBody,
                        icon = FocusIcons.Tool,
                        onClick = { onOpen(SettingsDestination.Tools) },
                    )
                    pageItem(
                        title = detailTitle,
                        body = detailBody,
                        icon = FocusIcons.Chat,
                        onClick = { onOpen(SettingsDestination.ChatDetail) },
                    )
                    pageItem(
                        title = appearanceTitle,
                        body = appearanceBody,
                        icon = FocusIcons.Language,
                        onClick = { onOpen(SettingsDestination.Appearance) },
                    )
                    pageItem(
                        title = notificationsTitle,
                        body = notificationsBody,
                        icon = FocusIcons.Notification,
                        onClick = { onOpen(SettingsDestination.Notifications) },
                    )
                    pageItem(
                        title = dataTitle,
                        body = dataBody,
                        icon = FocusIcons.Folder,
                        onClick = { onOpen(SettingsDestination.Data) },
                    )
                }
            }
        }
    }
}

private fun FocusCardGroupScope.pageItem(
    title: String,
    body: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    item(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        leadingContent = {
            FocusCardGroupIcon(icon = icon)
        },
        trailingContent = {
            Icon(
                imageVector = FocusIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}
