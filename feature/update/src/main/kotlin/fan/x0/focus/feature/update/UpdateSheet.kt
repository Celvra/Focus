package fan.x0.focus.feature.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusMarkdown
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.data.assistant.DownloadUiState
import fan.x0.focus.data.assistant.ReleaseAsset
import fan.x0.focus.data.assistant.ReleaseInfo
import fan.x0.focus.data.assistant.UpdateUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdateSheet(
    state: UpdateUiState,
    downloadState: State<DownloadUiState>,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDownload: (ReleaseAsset) -> Unit,
    onCancelDownload: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val uriHandler = LocalUriHandler.current
    val installer = rememberApkInstaller()
    var installOutcome by remember { mutableStateOf<InstallOutcome?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UpdateHeader(state = state, onDismiss = onDismiss)
            if (state.checking) {
                CheckingRow()
            }
            state.error?.let { error ->
                ErrorPanel(text = error.message(), onRetry = onRetry)
            }
            val release = state.release
            when {
                release == null && !state.checking && state.error == null -> {
                    EmptyPanel(text = stringResource(R.string.update_no_release), onRetry = onRetry)
                }
                release != null && !state.updateAvailable && !state.checking -> {
                    UpToDatePanel(release = release)
                }
                release != null -> {
                    ReleaseContent(
                        release = release,
                        downloadState = downloadState,
                        installOutcome = installOutcome,
                        onDownload = { asset ->
                            installOutcome = null
                            onDownload(asset)
                        },
                        onCancelDownload = onCancelDownload,
                        onInstall = { path -> installOutcome = installer(File(path)) },
                    )
                }
            }
            if (release != null) {
                FocusOutlinedButton(
                    text = stringResource(R.string.update_open_release),
                    onClick = { openUrl(uriHandler, release.url) },
                    icon = FocusIcons.Link,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun UpdateHeader(state: UpdateUiState, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.update_title),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            if (state.currentVersion.isNotBlank()) {
                Text(
                    text = stringResource(R.string.update_current, state.currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = hapticTap(onDismiss)) {
            Icon(
                imageVector = FocusIcons.Close,
                contentDescription = stringResource(R.string.update_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CheckingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
        )
        Text(
            text = stringResource(R.string.update_checking),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorPanel(text: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.update_check_failed),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
            )
            FocusTextButton(text = stringResource(R.string.update_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun EmptyPanel(text: String, onRetry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            FocusTextButton(text = stringResource(R.string.update_retry), onClick = onRetry)
        }
    }
}

@Composable
private fun UpToDatePanel(release: ReleaseInfo) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = FocusIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.update_up_to_date),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.update_latest, release.tagName),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ReleaseContent(
    release: ReleaseInfo,
    downloadState: State<DownloadUiState>,
    installOutcome: InstallOutcome?,
    onDownload: (ReleaseAsset) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = FocusIcons.Bolt,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.update_latest, release.tagName),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (release.name != release.tagName) {
                    Text(
                        text = release.name,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    Text(
        text = stringResource(R.string.update_notes_title),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
    if (release.notes.isBlank()) {
        Text(
            text = stringResource(R.string.update_notes_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FocusMarkdown(
            text = release.notes,
            bodyStyle = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        text = stringResource(R.string.update_assets_title),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
    if (release.assets.isEmpty()) {
        Text(
            text = stringResource(R.string.update_no_apk),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        release.assets.forEach { asset ->
            AssetRow(
                asset = asset,
                downloadState = downloadState,
                installOutcome = installOutcome,
                onDownload = { onDownload(asset) },
                onCancelDownload = onCancelDownload,
                onInstall = onInstall,
            )
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

@Composable
private fun AssetRow(
    asset: ReleaseAsset,
    downloadState: State<DownloadUiState>,
    installOutcome: InstallOutcome?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: (String) -> Unit,
) {
    val context = LocalContext.current
    val download by downloadState
    val running = (download as? DownloadUiState.Running)?.takeIf { it.assetName == asset.name }
    val completed = (download as? DownloadUiState.Completed)?.takeIf { it.assetName == asset.name }
    val failed = (download as? DownloadUiState.Failed)?.takeIf { it.assetName == asset.name }
    val anotherRunning = (download as? DownloadUiState.Running)?.assetName?.let { it != asset.name } == true
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = FocusIcons.UploadFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (asset.size > 0) {
                            Formatter.formatShortFileSize(context, asset.size)
                        } else {
                            stringResource(R.string.update_size_unknown)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (running == null && completed == null && failed == null) {
                    FocusTextButton(
                        text = stringResource(R.string.update_download),
                        onClick = onDownload,
                        enabled = !anotherRunning,
                    )
                }
            }
            when {
                running != null -> {
                    LinearProgressIndicator(
                        progress = { running.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = progressText(running),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        FocusTextButton(
                            text = stringResource(R.string.update_cancel),
                            onClick = onCancelDownload,
                        )
                    }
                }
                completed != null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.update_download_done),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FocusPrimaryButton(
                            text = stringResource(R.string.update_install),
                            onClick = { onInstall(completed.filePath) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                failed != null -> {
                    Text(
                        text = failed.error.message(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        FocusTextButton(
                            text = stringResource(R.string.update_retry),
                            onClick = onDownload,
                            enabled = !anotherRunning,
                        )
                    }
                }
            }
            if (completed != null && installOutcome != null && installOutcome != InstallOutcome.Started) {
                Text(
                    text = stringResource(
                        when (installOutcome) {
                            InstallOutcome.Failed -> R.string.update_install_failed
                            InstallOutcome.Permission -> R.string.update_install_permission
                            InstallOutcome.Started -> R.string.update_download_done
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (installOutcome == InstallOutcome.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun progressText(running: DownloadUiState.Running): String {
    val context = LocalContext.current
    val downloaded = Formatter.formatShortFileSize(context, running.downloaded)
    return if (running.total > 0) {
        stringResource(
            R.string.update_progress,
            downloaded,
            Formatter.formatShortFileSize(context, running.total),
        )
    } else {
        downloaded
    }
}

@Composable
private fun rememberApkInstaller(): (File) -> InstallOutcome {
    val context = LocalContext.current
    return remember(context) {
        { file -> installApk(context, file) }
    }
}

private fun installApk(context: Context, file: File): InstallOutcome {
    if (!file.exists() || file.length() == 0L) return InstallOutcome.Failed
    if (!context.packageManager.canRequestPackageInstalls()) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (runCatching { context.startActivity(intent) }.isSuccess) {
            InstallOutcome.Permission
        } else {
            InstallOutcome.Failed
        }
    }
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return InstallOutcome.Failed
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, APK_MIME)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return if (runCatching { context.startActivity(intent) }.isSuccess) {
        InstallOutcome.Started
    } else {
        InstallOutcome.Failed
    }
}

private fun openUrl(uriHandler: UriHandler, url: String) {
    runCatching { uriHandler.openUri(url) }
}

private enum class InstallOutcome { Started, Permission, Failed }

private const val APK_MIME = "application/vnd.android.package-archive"
