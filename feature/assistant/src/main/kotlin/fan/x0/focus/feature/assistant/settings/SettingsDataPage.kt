package fan.x0.focus.feature.assistant.settings

import android.graphics.BitmapFactory
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupValue
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.data.assistant.Contributor
import fan.x0.focus.data.assistant.DataUsage
import fan.x0.focus.feature.assistant.DataClearTarget
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SettingsAction
import fan.x0.focus.feature.assistant.SettingsUiState
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsDataPage(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onAction(SettingsAction.RefreshData)
    }
    val busy = state.dataUsageLoading || state.contributorsLoading
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_data),
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            if (busy) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = hapticTap { onAction(SettingsAction.RefreshData) }) {
                    Icon(
                        imageVector = FocusIcons.Refresh,
                        contentDescription = stringResource(R.string.settings_data_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_page_data_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        item(key = "stats") {
            FocusReveal(index = 1) {
                DataStatsGroup(state = state)
            }
        }
        item(key = "manage") {
            FocusReveal(index = 2) {
                DataManagementGroup(state = state, onAction = onAction)
            }
        }
        item(key = "about") {
            FocusReveal(index = 3) {
                AboutGroup(state = state, onAction = onAction)
            }
        }
    }
    state.pendingClear?.let { target ->
        ClearConfirmDialog(
            target = target,
            onDismiss = { onAction(SettingsAction.CancelClearData) },
            onConfirm = { onAction(SettingsAction.ConfirmClearData) },
        )
    }
    if (state.resetConfirm) {
        AlertDialog(
            onDismissRequest = { onAction(SettingsAction.CancelReset) },
            title = { Text(stringResource(R.string.settings_reset_title)) },
            text = { Text(stringResource(R.string.settings_reset_body)) },
            confirmButton = {
                TextButton(onClick = hapticTap { onAction(SettingsAction.ConfirmReset) }) {
                    Text(stringResource(R.string.settings_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = hapticTap { onAction(SettingsAction.CancelReset) }) {
                    Text(stringResource(R.string.assistant_cancel))
                }
            },
        )
    }
}

@Composable
private fun DataStatsGroup(state: SettingsUiState) {
    val context = LocalContext.current
    val usage = state.dataUsage
    val loading = state.dataUsageLoading && usage == DataUsage()
    val placeholder = stringResource(R.string.settings_data_stat_loading)
    FocusCardGroup(title = stringResource(R.string.settings_data_stats_title)) {
        formItem(
            headlineContent = { Text(stringResource(R.string.settings_data_stats_item)) },
            supportingContent = {
                Text(
                    stringResource(
                        if (loading) {
                            R.string.settings_data_stats_loading
                        } else {
                            R.string.settings_data_stats_body
                        },
                    ),
                )
            },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.History,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                StatTile(
                    label = stringResource(R.string.settings_data_stat_conversations),
                    value = if (loading) placeholder else formatCount(usage.conversations),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.settings_data_stat_messages),
                    value = if (loading) placeholder else formatCount(usage.messages),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.settings_data_stat_input),
                    value = if (loading) placeholder else formatTokens(usage.inputTokens),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.settings_data_stat_output),
                    value = if (loading) placeholder else formatTokens(usage.outputTokens),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.settings_data_stat_index),
                    value = if (loading) placeholder else formatCount(usage.indexedFiles),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.settings_data_stat_storage),
                    value = if (loading) placeholder else Formatter.formatShortFileSize(context, usage.databaseBytes),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DataManagementGroup(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    FocusCardGroup(title = stringResource(R.string.settings_data_manage_title)) {
        item(
            headlineContent = { Text(stringResource(R.string.settings_data_clear_chat_item)) },
            supportingContent = { Text(stringResource(R.string.settings_data_clear_chat_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Delete,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            trailingContent = {
                FocusCardGroupValue(formatCount(state.dataUsage.conversations))
            },
            onClick = { onAction(SettingsAction.AskClearData(DataClearTarget.Conversations)) },
        )
        item(
            headlineContent = { Text(stringResource(R.string.settings_data_clear_index_item)) },
            supportingContent = { Text(stringResource(R.string.settings_data_clear_index_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Folder,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            },
            trailingContent = {
                FocusCardGroupValue(formatCount(state.dataUsage.indexedFiles))
            },
            onClick = { onAction(SettingsAction.AskClearData(DataClearTarget.FileIndex)) },
        )
        item(
            headlineContent = { Text(stringResource(R.string.settings_reset_title)) },
            supportingContent = { Text(stringResource(R.string.settings_reset_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Refresh,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            onClick = { onAction(SettingsAction.AskReset) },
        )
    }
}

@Composable
private fun AboutGroup(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val rotation by animateFloatAsState(
        targetValue = if (state.contributorsExpanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "contributorsChevron",
    )
    FocusCardGroup(title = stringResource(R.string.settings_about_title)) {
        item(
            headlineContent = { Text(stringResource(R.string.settings_about_app)) },
            supportingContent = { Text(stringResource(R.string.settings_about_tagline)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Assistant,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            trailingContent = {
                if (version.isNotBlank()) {
                    FocusCardGroupValue(stringResource(R.string.settings_about_version, version))
                }
            },
        )
        item(
            headlineContent = { Text(stringResource(R.string.settings_about_contributors_item)) },
            supportingContent = { Text(stringResource(R.string.settings_about_contributors_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Person,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (state.contributors.isNotEmpty()) {
                        FocusCardGroupValue(formatCount(state.contributors.size))
                    }
                    Icon(
                        imageVector = FocusIcons.ArrowDown,
                        contentDescription = stringResource(
                            if (state.contributorsExpanded) {
                                R.string.settings_about_contributors_collapse
                            } else {
                                R.string.settings_about_contributors_expand
                            },
                        ),
                        modifier = Modifier.rotate(rotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            onClick = { onAction(SettingsAction.ToggleContributors) },
        )
        collapsibleFormItem(expanded = state.contributorsExpanded) {
            ContributorList(
                contributors = state.contributors,
                avatars = state.contributorAvatars,
                loading = state.contributorsLoading,
                error = state.contributorsError,
                onRetry = { onAction(SettingsAction.RefreshData) },
                onOpen = { openUrl(uriHandler, it) },
            )
        }
        item(
            headlineContent = { Text(stringResource(R.string.settings_about_source_item)) },
            supportingContent = { Text(stringResource(R.string.settings_about_source_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Link,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = FocusIcons.Chevron,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = { openUrl(uriHandler, REPO_URL) },
        )
        item(
            headlineContent = { Text(stringResource(R.string.settings_about_license_item)) },
            supportingContent = { Text(stringResource(R.string.settings_about_license_body)) },
            leadingContent = {
                FocusCardGroupIcon(
                    icon = FocusIcons.Library,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            },
            trailingContent = {
                Icon(
                    imageVector = FocusIcons.Chevron,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = { openUrl(uriHandler, LICENSE_URL) },
        )
    }
}

@Composable
private fun ContributorList(
    contributors: List<Contributor>,
    avatars: Map<String, ByteArray>,
    loading: Boolean,
    error: Boolean,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
) {
    when {
        loading && contributors.isEmpty() -> LoadingRow()
        error && contributors.isEmpty() -> ErrorRow(onRetry = onRetry)
        contributors.isEmpty() -> Text(
            text = stringResource(R.string.settings_about_contributors_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column {
            contributors.forEachIndexed { index, contributor ->
                ContributorRow(
                    contributor = contributor,
                    avatar = avatars[contributor.login],
                    onOpen = onOpen,
                )
                if (index != contributors.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 44.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            if (error) {
                Text(
                    text = stringResource(R.string.settings_about_contributors_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ContributorRow(
    contributor: Contributor,
    avatar: ByteArray?,
    onOpen: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = hapticTap { onOpen(contributor.url) })
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ContributorAvatar(login = contributor.login, avatar = avatar)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = contributor.login,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.settings_about_contributor_commits, contributor.contributions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = FocusIcons.Chevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContributorAvatar(login: String, avatar: ByteArray?) {
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = avatar) {
        value = avatar?.let { bytes ->
            withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val bitmap = image
    Surface(
        modifier = Modifier.size(32.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = login.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.settings_about_contributors_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorRow(onRetry: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_about_contributors_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = hapticTap(onRetry)) {
            Text(stringResource(R.string.settings_about_contributors_retry))
        }
    }
}

@Composable
private fun ClearConfirmDialog(
    target: DataClearTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val title = when (target) {
        DataClearTarget.Conversations -> R.string.settings_data_clear_chat_confirm_title
        DataClearTarget.FileIndex -> R.string.settings_data_clear_index_confirm_title
    }
    val body = when (target) {
        DataClearTarget.Conversations -> R.string.settings_data_clear_chat_confirm_body
        DataClearTarget.FileIndex -> R.string.settings_data_clear_index_confirm_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = hapticTap(onConfirm)) {
                Text(stringResource(R.string.settings_data_clear_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = hapticTap(onDismiss)) {
                Text(stringResource(R.string.assistant_cancel))
            }
        },
    )
}

private fun formatCount(value: Int): String = NumberFormat.getIntegerInstance().format(value)

private fun formatTokens(value: Long): String {
    if (value < 1_000) return value.toString()
    val divisor = when {
        value >= 1_000_000_000 -> 1_000_000_000.0
        value >= 1_000_000 -> 1_000_000.0
        else -> 1_000.0
    }
    val suffix = when {
        value >= 1_000_000_000 -> "B"
        value >= 1_000_000 -> "M"
        else -> "K"
    }
    return String.format(Locale.getDefault(), "%.1f%s", value / divisor, suffix)
}

private fun openUrl(uriHandler: UriHandler, url: String) {
    runCatching { uriHandler.openUri(url) }
}

private const val REPO_URL = "https://github.com/Celvra/Focus"
private const val LICENSE_URL = "https://github.com/Celvra/Focus/blob/main/LICENSE"
