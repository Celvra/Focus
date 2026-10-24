package fan.x0.focus

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.app.ShellState
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.data.assistant.Conversation

@Composable
fun ShellDrawer(
    state: ShellState,
    current: FocusRoute,
    onNavigate: (FocusRoute) -> Unit,
    onNewChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DrawerHeader(state, onOpenSettings = { onNavigate(FocusRoute.Settings) })
        Spacer(Modifier.height(8.dp))
        NewChatButton(onClick = onNewChat)
        Spacer(Modifier.height(8.dp))
        DrawerEntry(FocusIcons.Today, stringResource(R.string.tab_today), current == FocusRoute.Today) {
            onNavigate(FocusRoute.Today)
        }
        DrawerEntry(FocusIcons.Goals, stringResource(R.string.tab_goals), current == FocusRoute.Goals) {
            onNavigate(FocusRoute.Goals)
        }
        DrawerEntry(FocusIcons.Library, stringResource(R.string.tab_library), current == FocusRoute.Library) {
            onNavigate(FocusRoute.Library)
        }
        DrawerEntry(FocusIcons.Assistant, stringResource(R.string.tab_assistant), current == FocusRoute.Assistant) {
            onNavigate(FocusRoute.Assistant)
        }
        DrawerEntry(FocusIcons.Settings, stringResource(R.string.settings_title), current == FocusRoute.Settings) {
            onNavigate(FocusRoute.Settings)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(6.dp))
        DrawerSectionLabel(stringResource(R.string.drawer_recent))
        if (state.recent.isEmpty()) {
            Text(
                text = stringResource(R.string.drawer_no_conversations),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else {
            state.recent.forEach { conversation ->
                ConversationRow(conversation = conversation, onClick = { onOpenConversation(conversation.id) })
            }
        }
        DrawerEntry(
            icon = FocusIcons.History,
            label = stringResource(R.string.drawer_history),
            selected = current == FocusRoute.History,
            onClick = { onNavigate(FocusRoute.History) },
        )
    }
}

@Composable
private fun DrawerHeader(state: ShellState, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FocusAvatar(style = AvatarStyle(preset = 0, palette = 0), size = 52.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = state.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.drawer_default_name),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Running on Focus",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF9E9E9E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = FocusIcons.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NewChatButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Icon(
            imageVector = FocusIcons.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.drawer_new_chat),
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
    }
}

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ConversationRow(conversation: Conversation, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = conversation.title.ifBlank { stringResource(R.string.drawer_untitled) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(
                        conversation.updatedAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                        DateUtils.FORMAT_ABBREV_RELATIVE,
                    ).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        selected = false,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun DrawerEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                style = if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}
