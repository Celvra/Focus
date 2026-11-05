package fan.x0.focus.feature.assistant.settings

import android.graphics.Bitmap
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCardGroupScope
import fan.x0.focus.core.designsystem.components.FocusGroupedPickerSheet
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPickerGroup
import fan.x0.focus.core.designsystem.components.FocusPickerItem
import fan.x0.focus.core.designsystem.components.FocusPickerSelection
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.components.hapticToggle
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.error.AppError
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.data.im.ImChatKind
import fan.x0.focus.data.im.ImConnectionState
import fan.x0.focus.data.im.ImLog
import fan.x0.focus.data.im.ImLoginChallenge
import fan.x0.focus.feature.assistant.ImAccountUi
import fan.x0.focus.feature.assistant.ImAction
import fan.x0.focus.feature.assistant.ImChannelUi
import fan.x0.focus.feature.assistant.ImChatUi
import fan.x0.focus.feature.assistant.ImDraft
import fan.x0.focus.feature.assistant.ImEffect
import fan.x0.focus.feature.assistant.ImFieldDraft
import fan.x0.focus.feature.assistant.ImLoginUi
import fan.x0.focus.feature.assistant.ImSettingsUiState
import fan.x0.focus.feature.assistant.ImSettingsViewModel
import fan.x0.focus.feature.assistant.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SettingsImRoute(
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    viewModel: ImSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved = stringResource(R.string.settings_im_saved)
    val deleted = stringResource(R.string.settings_im_deleted)
    val bound = stringResource(R.string.settings_im_bound_done)
    CollectEffect(viewModel.effect) { effect ->
        val message = when (effect) {
            ImEffect.Saved -> saved
            ImEffect.Deleted -> deleted
            ImEffect.Bound -> bound
        }
        snackbar.showSnackbar(message)
    }
    SettingsImPage(
        state = state,
        onAction = viewModel::onAction,
        onBack = onBack,
        snackbar = snackbar,
    )
}

@Composable
internal fun SettingsImPage(
    state: ImSettingsUiState,
    onAction: (ImAction) -> Unit,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
) {
    var showLogs by remember { mutableStateOf(false) }
    SettingsPageScaffold(
        title = stringResource(R.string.settings_page_im),
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            IconButton(onClick = hapticTap { showLogs = true }) {
                Icon(
                    imageVector = FocusIcons.Terminal,
                    contentDescription = stringResource(R.string.settings_im_logs),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        item(key = "intro") {
            FocusReveal {
                Text(
                    text = stringResource(R.string.settings_im_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        if (state.loading) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FocusLoadingBlob(size = 28.dp)
                }
            }
        } else {
            item(key = "accounts") {
                FocusReveal(index = 1) {
                    FocusCardGroup(title = stringResource(R.string.settings_im_accounts)) {
                        state.accounts.forEach { account ->
                            AccountRow(
                                account = account,
                                onOpen = { onAction(ImAction.Edit(account.id)) },
                                onEnabled = { onAction(ImAction.SetEnabled(account.id, it)) },
                            )
                        }
                        item(
                            headlineContent = { Text(stringResource(R.string.settings_im_add)) },
                            supportingContent = { Text(stringResource(R.string.settings_im_add_body)) },
                            leadingContent = {
                                FocusCardGroupIcon(
                                    icon = FocusIcons.Add,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            },
                            onClick = { onAction(ImAction.Add) },
                        )
                    }
                }
            }
            if (state.accounts.isEmpty()) {
                item(key = "accounts_empty") {
                    Text(
                        text = stringResource(R.string.settings_im_no_accounts),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            item(key = "chats") {
                FocusReveal(index = 2) {
                    FocusCardGroup(title = stringResource(R.string.settings_im_chats)) {
                        state.chats.forEach { chat ->
                            ChatRow(
                                chat = chat,
                                onOpen = { onAction(ImAction.OpenBinding(chat.id)) },
                                onEnabled = { onAction(ImAction.ToggleChat(chat.id, it)) },
                            )
                        }
                    }
                }
            }
            if (state.chats.isEmpty()) {
                item(key = "chats_empty") {
                    Text(
                        text = stringResource(R.string.settings_im_no_chats),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            state.error?.let { error ->
                item(key = "error") {
                    Text(
                        text = error.message(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
    state.draft?.let { draft ->
        ImEditorSheet(
            draft = draft,
            editing = state.accounts.any { it.id == draft.id },
            error = state.error,
            onAction = onAction,
        )
    }
    state.binding?.let { binding ->
        ImBindingSheet(
            binding = binding,
            state = state,
            onAction = onAction,
        )
    }
    if (state.addChannelOpen) {
        ImAddChannelSheet(
            channels = state.channels,
            onAction = onAction,
        )
    }
    if (showLogs) {
        ImLogSheet(onDismiss = { showLogs = false })
    }
    val login = state.login
    if (login != null && !state.loginHidden) {
        ImLoginSheet(
            login = login,
            accountName = state.accounts.firstOrNull { it.id == login.accountId }?.name
                ?: login.accountName,
            onAction = onAction,
        )
    }
}

@Composable
private fun ImAddChannelSheet(
    channels: List<ImChannelUi>,
    onAction: (ImAction) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val groupTitle = stringResource(R.string.settings_im_channel_group)
    val groups = remember(channels, groupTitle) {
        listOf(
            FocusPickerGroup(
                id = "channels",
                title = groupTitle,
                items = channels.map { FocusPickerItem(it.id, it.name) },
            ),
        )
    }
    FocusGroupedPickerSheet(
        title = stringResource(R.string.settings_im_choose_channel),
        searchHint = stringResource(R.string.settings_im_choose_channel_search),
        emptyText = stringResource(R.string.settings_im_choose_channel_empty),
        groups = groups,
        query = query,
        onQueryChange = { query = it },
        onSelect = { onAction(ImAction.AddChannel(it.itemId)) },
        onDismiss = { onAction(ImAction.CloseAddChannel) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImLoginSheet(
    login: ImLoginUi,
    accountName: String,
    onAction: (ImAction) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onAction(ImAction.HideLogin) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_im_login_title),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAction(ImAction.HideLogin) }) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.settings_im_login_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = accountName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val challenge = login.challenge) {
                is ImLoginChallenge.Qr -> {
                    QrCodeImage(
                        content = challenge.content,
                        modifier = Modifier.size(260.dp),
                    )
                    Text(
                        text = stringResource(R.string.settings_im_login_qr_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FocusOutlinedButton(
                        text = stringResource(R.string.settings_im_login_copy_link),
                        onClick = {
                            clipboard.setText(AnnotatedString(challenge.content))
                            Toast.makeText(
                                context,
                                context.getString(R.string.settings_im_login_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = FocusIcons.Copy,
                    )
                    FocusOutlinedButton(
                        text = stringResource(R.string.settings_im_login_refresh),
                        onClick = { onAction(ImAction.StartLogin(login.accountId)) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = FocusIcons.Refresh,
                    )
                }
                is ImLoginChallenge.Code -> {
                    var code by remember(login.accountId, challenge.prompt) { mutableStateOf("") }
                    Text(
                        text = challenge.prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text(stringResource(R.string.settings_im_login_code)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FocusPrimaryButton(
                        text = stringResource(R.string.settings_im_login_submit),
                        onClick = {
                            onAction(ImAction.SubmitLoginCode(code))
                            code = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ImLoginChallenge.Done -> Text(
                    text = stringResource(R.string.settings_im_login_success),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(content) {
        runCatching {
            val size = 720
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                mapOf(EncodeHintType.MARGIN to 1),
            )
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                val row = y * size
                for (x in 0 until size) {
                    pixels[row + x] = if (matrix[x, y]) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }
                }
            }
            Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.settings_im_login_title),
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImLogSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var lines by remember { mutableStateOf(ImLog.snapshot()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_im_logs),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { lines = ImLog.snapshot() }) {
                    Icon(
                        imageVector = FocusIcons.Refresh,
                        contentDescription = stringResource(R.string.settings_im_logs_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        ImLog.clear()
                        lines = emptyList()
                    },
                ) {
                    Icon(
                        imageVector = FocusIcons.Delete,
                        contentDescription = stringResource(R.string.settings_im_logs_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(lines.joinToString("\n")))
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_im_logs_copied),
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Icon(
                        imageVector = FocusIcons.Copy,
                        contentDescription = stringResource(R.string.settings_im_logs_copy),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.settings_im_logs_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_im_logs_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(lines, key = { index, line -> "$index:$line" }) { _, line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun FocusCardGroupScope.AccountRow(
    account: ImAccountUi,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    formItem(
        verticalPadding = 8.dp,
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = hapticTap(onOpen)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FocusCardGroupIcon(
                    icon = FocusIcons.Link,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FocusStatusPill(
                            text = statusLabel(account.status),
                            tone = statusTone(account.status),
                        )
                        Text(
                            text = account.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (account.statusDetail.isNotBlank()) {
                        Text(
                            text = account.statusDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(
                    checked = account.enabled,
                    onCheckedChange = hapticToggle(onEnabled),
                )
            }
        },
    )
}

private fun FocusCardGroupScope.ChatRow(
    chat: ImChatUi,
    onOpen: () -> Unit,
    onEnabled: (Boolean) -> Unit,
) {
    item(
        headlineContent = { Text(chat.title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "${chat.accountName} · ${kindLabel(chat.kind)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = chat.conversationTitle
                        ?.let { stringResource(R.string.settings_im_bound, it) }
                        ?: stringResource(R.string.settings_im_unbound),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chat.conversationId == null) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (chat.lastMessageAt > 0) {
                    val relative = DateUtils.getRelativeTimeSpanString(
                        chat.lastMessageAt,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                    Text(
                        text = stringResource(R.string.settings_im_last_active, relative),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        leadingContent = {
            FocusCardGroupIcon(
                icon = FocusIcons.Chat,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        trailingContent = {
            Switch(
                checked = chat.enabled,
                onCheckedChange = hapticToggle(onEnabled),
            )
        },
        onClick = onOpen,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImEditorSheet(
    draft: ImDraft,
    editing: Boolean,
    error: AppError?,
    onAction: (ImAction) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { onAction(ImAction.Close) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (editing) R.string.settings_im_edit else R.string.settings_im_add,
                    ),
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = hapticTap { onAction(ImAction.Close) }) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.settings_im_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = draft.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft.channelDescription.isNotBlank()) {
                Text(
                    text = draft.channelDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (draft.qrAuth) {
                Text(
                    text = stringResource(R.string.settings_im_qr_login_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (editing && draft.enabled) {
                    FocusOutlinedButton(
                        text = stringResource(R.string.settings_im_start_login),
                        onClick = { onAction(ImAction.StartLogin(draft.id)) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = FocusIcons.Link,
                    )
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onAction(ImAction.NameChanged(it)) },
                label = { Text(stringResource(R.string.settings_im_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            key(draft.id) {
                draft.fields.forEach { field ->
                    ImFieldRow(
                        field = field,
                        onValueChange = { onAction(ImAction.FieldChanged(field.key, it)) },
                    )
                }
            }
            if (draft.supportsGroup) {
                ToggleRow(
                    title = stringResource(R.string.settings_im_require_mention),
                    body = stringResource(R.string.settings_im_require_mention_body),
                    checked = draft.requireMention,
                    onCheckedChange = { onAction(ImAction.RequireMentionChanged(it)) },
                )
                ToggleRow(
                    title = stringResource(R.string.settings_im_only_owner),
                    body = stringResource(R.string.settings_im_only_owner_body),
                    checked = draft.onlyOwnerInGroups,
                    onCheckedChange = { onAction(ImAction.OnlyOwnerChanged(it)) },
                )
            }
            ToggleRow(
                title = stringResource(R.string.settings_im_enabled),
                body = "",
                checked = draft.enabled,
                onCheckedChange = { onAction(ImAction.EnabledChanged(it)) },
            )
            error?.let {
                Text(
                    text = it.message(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            FocusPrimaryButton(
                text = stringResource(R.string.settings_im_save),
                onClick = { onAction(ImAction.Save) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (editing) {
                FocusOutlinedButton(
                    text = stringResource(R.string.settings_im_delete),
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = FocusIcons.Delete,
                )
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_im_delete_title)) },
            text = { Text(stringResource(R.string.settings_im_delete_body)) },
            confirmButton = {
                FocusTextButton(
                    text = stringResource(R.string.settings_im_delete),
                    onClick = {
                        confirmDelete = false
                        onAction(ImAction.Delete)
                    },
                )
            },
            dismissButton = {
                FocusTextButton(
                    text = stringResource(R.string.settings_im_cancel),
                    onClick = { confirmDelete = false },
                )
            },
        )
    }
}

@Composable
private fun ImFieldRow(
    field: ImFieldDraft,
    onValueChange: (String) -> Unit,
) {
    var visible by remember(field.key) { mutableStateOf(false) }
    OutlinedTextField(
        value = field.value,
        onValueChange = onValueChange,
        label = { Text(if (field.required) "${field.label} *" else field.label) },
        placeholder = if (field.secret && field.existing && field.value.isBlank()) {
            { Text(stringResource(R.string.settings_im_secret_saved)) }
        } else {
            null
        },
        singleLine = true,
        visualTransformation = if (field.secret && !visible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (field.secret) {
            {
                TextButton(onClick = { visible = !visible }) {
                    Text(
                        text = stringResource(
                            if (visible) R.string.settings_im_hide else R.string.settings_im_show,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
            if (body.isNotBlank()) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = hapticToggle(onCheckedChange),
        )
    }
}

@Composable
private fun ImBindingSheet(
    binding: ImChatUi,
    state: ImSettingsUiState,
    onAction: (ImAction) -> Unit,
) {
    val recent = stringResource(R.string.settings_im_recent)
    val newTitle = stringResource(R.string.settings_im_new_conversation)
    val groups = remember(state.conversations, recent, newTitle) {
        listOf(
            FocusPickerGroup(
                id = GROUP,
                title = recent,
                items = buildList {
                    add(FocusPickerItem(ImSettingsViewModel.NEW_CONVERSATION, newTitle))
                    state.conversations.forEach { add(FocusPickerItem(it.id, it.title)) }
                },
            ),
        )
    }
    FocusGroupedPickerSheet(
        title = stringResource(R.string.settings_im_bind_title),
        searchHint = stringResource(R.string.settings_im_bind_search),
        emptyText = stringResource(R.string.settings_im_bind_empty),
        groups = groups,
        query = state.conversationQuery,
        onQueryChange = { onAction(ImAction.QueryChanged(it)) },
        onSelect = { onAction(ImAction.Bind(it.itemId)) },
        onDismiss = { onAction(ImAction.CloseBinding) },
        selected = binding.conversationId?.let { FocusPickerSelection(GROUP, it) },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow(
                    title = stringResource(R.string.settings_im_send_thinking),
                    body = stringResource(R.string.settings_im_send_thinking_body),
                    checked = binding.sendThinking,
                    onCheckedChange = { onAction(ImAction.ToggleChatThinking(binding.id, it)) },
                )
                ToggleRow(
                    title = stringResource(R.string.settings_im_send_tools),
                    body = stringResource(R.string.settings_im_send_tools_body),
                    checked = binding.sendTools,
                    onCheckedChange = { onAction(ImAction.ToggleChatTools(binding.id, it)) },
                )
            }
        },
    )
}

@Composable
private fun statusLabel(state: ImConnectionState): String = when (state) {
    ImConnectionState.Disabled -> stringResource(R.string.settings_im_status_disabled)
    ImConnectionState.Connecting -> stringResource(R.string.settings_im_status_connecting)
    ImConnectionState.Connected -> stringResource(R.string.settings_im_status_connected)
    ImConnectionState.AuthRequired -> stringResource(R.string.settings_im_status_auth)
    ImConnectionState.Error -> stringResource(R.string.settings_im_status_error)
}

private fun statusTone(state: ImConnectionState): StatusTone = when (state) {
    ImConnectionState.Disabled -> StatusTone.Neutral
    ImConnectionState.Connecting -> StatusTone.Active
    ImConnectionState.Connected -> StatusTone.Success
    ImConnectionState.AuthRequired -> StatusTone.Warning
    ImConnectionState.Error -> StatusTone.Danger
}

@Composable
private fun kindLabel(kind: ImChatKind): String = when (kind) {
    ImChatKind.Direct -> stringResource(R.string.settings_im_chat_direct)
    ImChatKind.Group -> stringResource(R.string.settings_im_chat_group)
}

private const val GROUP = "conversations"
