package fan.x0.focus

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusTabContent
import fan.x0.focus.core.designsystem.components.FocusTabSwitcher
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.data.assistant.Conversation

@Composable
fun HistoryRoute(
    conversations: List<Conversation>,
    archived: List<Conversation>,
    onOpen: (String) -> Unit,
    onOpenArchived: (String) -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<Conversation?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FocusCompactTopBar(title = stringResource(R.string.history_title))
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            FocusTabSwitcher(
                labels = listOf(
                    stringResource(R.string.history_tab_active),
                    stringResource(R.string.history_tab_archived),
                ),
                selectedIndex = section,
                onSelect = { section = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FocusTabContent(selectedIndex = section) { tab ->
                if (tab == 0) {
                    ConversationList(
                        conversations = conversations,
                        archived = false,
                        emptyIcon = FocusIcons.History,
                        emptyTitle = stringResource(R.string.history_empty),
                        emptyBody = stringResource(R.string.history_empty_body),
                        onOpen = { onOpen(it.id) },
                        onArchive = { onArchive(it.id) },
                        onDelete = { deleteTarget = it },
                    )
                } else {
                    ConversationList(
                        conversations = archived,
                        archived = true,
                        emptyIcon = FocusIcons.Archive,
                        emptyTitle = stringResource(R.string.history_archived_empty),
                        emptyBody = stringResource(R.string.history_archived_empty_body),
                        onOpen = { onOpenArchived(it.id) },
                        onArchive = {},
                        onDelete = { deleteTarget = it },
                    )
                }
            }
        }
    }

    deleteTarget?.let { target ->
        val title = target.title.ifBlank { stringResource(R.string.drawer_untitled) }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_body, title)) },
            confirmButton = {
                TextButton(
                    onClick = hapticTap {
                        onDelete(target.id)
                        deleteTarget = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.history_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = hapticTap { deleteTarget = null }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }
}

@Composable
private fun ConversationList(
    conversations: List<Conversation>,
    archived: Boolean,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyTitle: String,
    emptyBody: String,
    onOpen: (Conversation) -> Unit,
    onArchive: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
) {
    if (conversations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            FocusEmptyState(
                icon = emptyIcon,
                title = emptyTitle,
                body = emptyBody,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(conversations, key = { it.id }) { conversation ->
            ConversationCard(
                conversation = conversation,
                archived = archived,
                onOpen = { onOpen(conversation) },
                onArchive = { onArchive(conversation) },
                onDelete = { onDelete(conversation) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    archived: Boolean,
    onOpen: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = hapticTap(onOpen),
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = conversation.title.ifBlank { stringResource(R.string.drawer_untitled) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
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
            },
            trailingContent = {
                if (archived) {
                    IconButton(onClick = hapticTap(onDelete)) {
                        Icon(
                            imageVector = FocusIcons.Delete,
                            contentDescription = stringResource(R.string.history_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                } else {
                    IconButton(onClick = hapticTap(onArchive)) {
                        Icon(
                            imageVector = FocusIcons.Archive,
                            contentDescription = stringResource(R.string.history_archive),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
