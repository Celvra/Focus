package fan.x0.focus.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

@Composable
fun ArticleList(state: LibraryUiState, onAction: (LibraryAction) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FocusChip(
                label = stringResource(R.string.library_unread_only),
                selected = state.unreadOnly,
                onClick = { onAction(LibraryAction.ToggleUnread) },
            )
        }
        val visible = if (state.unreadOnly) state.articles.filter { !it.read } else state.articles
        if (visible.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                FocusEmptyState(
                    icon = FocusIcons.Feed,
                    title = stringResource(R.string.library_empty_articles_title),
                    body = stringResource(R.string.library_empty_articles_body),
                    actionLabel = stringResource(R.string.library_add_feed),
                    onAction = { onAction(LibraryAction.OpenAddFeed) },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.id }) { article ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        shape = FocusShapeTokens.card,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        onClick = { onAction(LibraryAction.OpenArticle(article.id)) },
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!article.read) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = article.feed,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (article.time.isNotBlank()) {
                                    Text(
                                        text = article.time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = article.title,
                                style = LocalFocusTypography.current.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            article.summary?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeedList(state: LibraryUiState, onAction: (LibraryAction) -> Unit) {
    if (state.feeds.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FocusEmptyState(
                icon = FocusIcons.Feed,
                title = stringResource(R.string.library_empty_feeds_title),
                body = stringResource(R.string.library_empty_feeds_body),
                actionLabel = stringResource(R.string.library_add_feed),
                onAction = { onAction(LibraryAction.OpenAddFeed) },
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.feeds, key = { it.id }) { feed ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                shape = FocusShapeTokens.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = feed.title,
                            style = LocalFocusTypography.current.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = feed.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        feed.error?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(4.dp))
                            FocusStatusPill(
                                text = stringResource(R.string.library_feed_error),
                                tone = StatusTone.Danger,
                            )
                        }
                    }
                    IconButton(onClick = { onAction(LibraryAction.AskRemoveFeed(feed.id)) }) {
                        Icon(
                            FocusIcons.Delete,
                            contentDescription = stringResource(R.string.library_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileList(
    state: LibraryUiState,
    onAction: (LibraryAction) -> Unit,
    onPickFolder: () -> Unit,
) {
    if (state.folderName == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FocusEmptyState(
                icon = FocusIcons.Folder,
                title = stringResource(R.string.library_empty_files_title),
                body = stringResource(R.string.library_empty_files_body),
                actionLabel = stringResource(R.string.library_pick_folder),
                onAction = onPickFolder,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "folder-actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusOutlinedButton(
                    text = stringResource(R.string.library_change_folder),
                    onClick = onPickFolder,
                    icon = FocusIcons.Folder,
                )
                FocusPrimaryButton(
                    text = stringResource(R.string.library_note_title),
                    onClick = { onAction(LibraryAction.OpenNote) },
                    icon = FocusIcons.Add,
                )
            }
        }
        if (state.files.isEmpty()) {
            item(key = "files-empty") {
                Text(
                    text = stringResource(R.string.library_files_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(state.files, key = { it.uri }) { file ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
                shape = FocusShapeTokens.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                onClick = { if (!file.isDirectory) onAction(LibraryAction.OpenFile(file.uri)) },
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FocusIconBadge(
                        icon = if (file.isDirectory) FocusIcons.Folder else FocusIcons.Library,
                        size = 34.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (file.sizeLabel.isNotBlank()) {
                            Text(
                                text = file.sizeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { onAction(LibraryAction.AskDeleteFile(file.uri)) }) {
                        Icon(
                            FocusIcons.Delete,
                            contentDescription = stringResource(R.string.library_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArticleReader(reader: ArticleReaderUi) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = reader.title,
                style = LocalFocusTypography.current.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = reader.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reader.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun FileReader(reader: FileReaderUi) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = reader.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        )
    }
}
