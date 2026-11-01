package fan.x0.focus.feature.crew

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import fan.x0.focus.core.designsystem.components.FocusChatBubble
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusComposer
import fan.x0.focus.core.designsystem.components.FocusComposerPill
import fan.x0.focus.core.designsystem.components.FocusEmptyState
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.data.assistant.CrewApprovalPhase
import fan.x0.focus.data.assistant.CrewToolStatus

@Composable
fun CrewChatScreen(
    state: CrewUiState,
    onAction: (CrewAction) -> Unit,
    snackbar: SnackbarHostState,
    onPickMemberPhoto: () -> Unit,
) {
    val group = state.group ?: return
    val entries = state.entries
    val listState = rememberLazyListState()
    val hazeState = rememberHazeState()
    var composerHeight by remember { mutableIntStateOf(0) }
    var follow by remember { mutableStateOf(true) }
    val lastIndex by rememberUpdatedState(entries.lastIndex)
    val bottomPadding = with(LocalDensity.current) { composerHeight.toDp() } + 16.dp
    val panelOffset = with(LocalDensity.current) { composerHeight.toDp() } + 8.dp

    LaunchedEffect(group.id) { follow = true }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> follow = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    follow = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= lastIndex - 1
                }
            }
        }
    }
    val liveTextLength = (entries.lastOrNull() as? CrewEntryUi.Message)?.takeIf { it.streaming }?.text?.length ?: 0
    LaunchedEffect(entries.size, liveTextLength) {
        if (follow && entries.isNotEmpty()) listState.scrollToItem(lastIndex)
    }
    LaunchedEffect(state.focusMessageId) {
        val id = state.focusMessageId ?: return@LaunchedEffect
        val index = entries.indexOfFirst {
            it is CrewEntryUi.Message && it.messageId == id
        }
        if (index >= 0) {
            follow = false
            listState.animateScrollToItem(index)
        }
        kotlinx.coroutines.delay(2000)
        onAction(CrewAction.ClearFocus)
    }

    BackHandler(enabled = state.mention.visible) { onAction(CrewAction.MentionDismiss) }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CrewChatTopBar(group = group, state = state, onAction = onAction)
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                when {
                    state.groupLoading -> FocusLoadingBlock()
                    entries.isEmpty() -> CrewEmptyThread()
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 10.dp,
                            end = 12.dp,
                            bottom = bottomPadding,
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(
                            items = entries,
                            key = { it.key },
                            contentType = { entry ->
                                when (entry) {
                                    is CrewEntryUi.Message -> if (entry.fromUser) "user" else "agent"
                                    is CrewEntryUi.Notice -> "notice"
                                    is CrewEntryUi.Approval -> "approval"
                                }
                            },
                        ) { entry ->
                            when (entry) {
                                is CrewEntryUi.Message -> CrewMessageRow(
                                    entry = entry,
                                    highlighted = entry.messageId != null &&
                                        entry.messageId == state.focusMessageId,
                                    onOpenMenu = { onAction(CrewAction.OpenMessageMenu(entry.key)) },
                                    onJump = { id -> onAction(CrewAction.JumpToMessage(id)) },
                                )
                                is CrewEntryUi.Notice -> CrewNoticeRow(entry.text)
                                is CrewEntryUi.Approval -> CrewApprovalCard(
                                    entry = entry,
                                    onDecide = { approved -> onAction(CrewAction.Approve(entry.id, approved)) },
                                )
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = state.mention.visible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = panelOffset),
                enter = slideInVertically { it / 2 } + fadeIn(),
                exit = slideOutVertically { it / 2 } + fadeOut(),
            ) {
                CrewMentionPanel(
                    mention = state.mention,
                    onPick = { onAction(CrewAction.MentionPick(it)) },
                )
            }
            FocusComposer(
                input = state.input,
                onInputChange = { onAction(CrewAction.InputChanged(it)) },
                placeholder = stringResource(R.string.crew_input_hint),
                canSend = state.input.isNotBlank() && !state.sending,
                sending = false,
                onSend = {
                    follow = true
                    onAction(CrewAction.Send)
                },
                onStop = {},
                hazeState = hazeState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 0.dp),
                onHeightChange = { composerHeight = it },
                attachments = {
                    state.reply?.let { reply ->
                        CrewReplyPreview(reply = reply, onCancel = { onAction(CrewAction.CancelReply) })
                    }
                },
                pills = {
                    FocusComposerPill(
                        onClick = {
                            val prefix = if (state.input.isEmpty() || state.input.last().isWhitespace()) "" else " "
                            onAction(CrewAction.InputChanged(state.input + prefix + "@"))
                        },
                        label = "@",
                    )
                    if (state.running) {
                        FocusComposerPill(
                            onClick = { onAction(CrewAction.Stop) },
                            icon = FocusIcons.Stop,
                            label = stringResource(R.string.crew_stop),
                            selected = true,
                        )
                    }
                },
            )
        }
    }
    if (state.memberSheetOpen) {
        CrewMemberSheet(
            state = state,
            onAction = onAction,
            onPickPhoto = onPickMemberPhoto,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrewChatTopBar(
    group: CrewGroupUi,
    state: CrewUiState,
    onAction: (CrewAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val subtitle = when {
        state.running && state.working.isNotBlank() -> stringResource(R.string.crew_speaking, state.working)
        state.running -> stringResource(R.string.crew_working_line)
        else -> pluralStringResource(
            R.plurals.crew_member_count,
            group.memberCount,
            group.memberCount,
        )
    }
    FocusCompactTopBar(
        title = group.name,
        subtitle = subtitle,
        onBack = { onAction(CrewAction.CloseGroup) },
        actions = {
            IconButton(onClick = hapticTap { onAction(CrewAction.OpenMembers) }) {
                Icon(
                    FocusIcons.Groups,
                    contentDescription = stringResource(R.string.crew_members),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = hapticTap { menuOpen = true }) {
                    Icon(
                        FocusIcons.More,
                        contentDescription = stringResource(R.string.crew_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.crew_edit_group)) },
                        onClick = {
                            menuOpen = false
                            onAction(CrewAction.EditGroup)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (group.archived) R.string.crew_unarchive else R.string.crew_archive,
                                ),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onAction(CrewAction.SetArchived(group.id, !group.archived))
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.crew_delete_group),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onAction(CrewAction.AskDeleteGroup)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun CrewEmptyThread() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FocusEmptyState(
            icon = FocusIcons.Groups,
            title = stringResource(R.string.crew_start_title),
            body = stringResource(R.string.crew_start_body),
        )
    }
}

@Composable
private fun CrewMessageRow(
    entry: CrewEntryUi.Message,
    highlighted: Boolean,
    onOpenMenu: () -> Unit,
    onJump: (Long) -> Unit,
) {
    val alignment = if (entry.fromUser) Alignment.End else Alignment.Start
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = if (entry.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        if (!entry.fromUser) {
            CrewAvatar(
                preset = entry.avatarPreset,
                palette = entry.avatarPalette,
                path = entry.avatarPath,
                size = 34.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = alignment,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!entry.fromUser) {
                Text(
                    text = entry.author,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.duty.isNotBlank()) {
                    Text(
                        text = entry.duty,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.mentions.isNotEmpty()) {
                    CrewMentionChips(entry.mentions)
                }
            }
            if (entry.streaming && entry.text.isBlank()) {
                CrewTypingBubble(activity = entry.activity)
            } else if (entry.text.isNotBlank()) {
                FocusChatBubble(
                    text = entry.text,
                    fromUser = entry.fromUser,
                    highlighted = highlighted,
                    modifier = Modifier.fillMaxWidth(),
                    streaming = entry.streaming,
                    reactions = entry.reactions,
                    onLongPress = entry.messageId?.let { { onOpenMenu() } },
                    header = entry.replyTo?.let { quote ->
                        { CrewQuoteHeader(quote = quote, onJump = onJump) }
                    },
                )
            }
            if (entry.fromUser && entry.mentions.isNotEmpty()) {
                CrewMentionChips(entry.mentions)
            }
            if (!entry.fromUser && entry.activity.isNotBlank() && entry.streaming && entry.text.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FocusLoadingBlob(size = 12.dp)
                    Text(
                        text = entry.activity,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (entry.tools.isNotEmpty()) {
                CrewToolTraces(entry.tools)
            }
            entry.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CrewTypingBubble(activity: String) {
    Surface(
        shape = FocusShapeTokens.bubbleIncoming,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FocusLoadingBlob(size = 16.dp)
            Text(
                text = activity.ifBlank { stringResource(R.string.crew_typing) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CrewQuoteHeader(quote: CrewQuoteUi, onJump: (Long) -> Unit) {
    Surface(
        modifier = if (quote.deleted) {
            Modifier
        } else {
            Modifier.clickable(onClick = hapticTap { onJump(quote.messageId) })
        },
        shape = MaterialTheme.shapes.small,
        color = if (quote.deleted) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.crew_replying_to,
                    quote.author.ifBlank { stringResource(R.string.crew_you) },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (quote.deleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = if (quote.deleted) stringResource(R.string.crew_message_deleted) else quote.text,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = if (quote.deleted) FontStyle.Italic else FontStyle.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CrewToolTraces(tools: List<CrewToolUi>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tools.forEach { tool ->
            key(tool.id) {
                CrewToolCard(tool)
            }
        }
    }
}

@Composable
private fun CrewToolCard(tool: CrewToolUi) {
    var expanded by rememberSaveable(tool.id) { mutableStateOf(false) }
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = when (tool.status) {
            CrewToolStatus.Failed -> scheme.errorContainer
            CrewToolStatus.Running -> scheme.secondaryContainer
            CrewToolStatus.Done -> scheme.surfaceContainer
        },
        animationSpec = motion.effectsDefault(),
        label = "crewToolContainer",
    )
    val content = when (tool.status) {
        CrewToolStatus.Failed -> scheme.onErrorContainer
        CrewToolStatus.Running -> scheme.onSecondaryContainer
        CrewToolStatus.Done -> scheme.onSurfaceVariant
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialDefault(),
        label = "crewToolChevron",
    )
    val expandable = tool.arguments.isNotBlank() || tool.detail.isNotBlank()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(motion.spatialDefault()),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        onClick = hapticTap { if (expandable) expanded = !expanded },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tool.status == CrewToolStatus.Running) {
                    FocusLoadingBlob(size = 12.dp)
                } else {
                    Icon(
                        imageVector = if (tool.status == CrewToolStatus.Done) {
                            FocusIcons.Check
                        } else {
                            FocusIcons.Warning
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tool.summary.isNotBlank()) {
                    Text(
                        text = tool.summary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (expandable) {
                    Icon(
                        imageVector = FocusIcons.ArrowDown,
                        contentDescription = stringResource(
                            if (expanded) R.string.crew_tool_collapse else R.string.crew_tool_expand,
                        ),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = rotation },
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (tool.arguments.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.crew_tool_input),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tool.status == CrewToolStatus.Failed) {
                                scheme.onErrorContainer
                            } else {
                                scheme.onSurfaceVariant
                            },
                        )
                        SelectionContainer {
                            Text(
                                text = tool.arguments,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                    if (tool.detail.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.crew_tool_output),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tool.status == CrewToolStatus.Failed) {
                                scheme.onErrorContainer
                            } else {
                                scheme.onSurfaceVariant
                            },
                        )
                        SelectionContainer {
                            Text(
                                text = tool.detail,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 240.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrewNoticeRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun CrewApprovalCard(entry: CrewEntryUi.Approval, onDecide: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FocusShapeTokens.card,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    FocusIcons.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = approvalPhaseLabel(entry.phase),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (entry.summary.isNotBlank()) {
                Text(
                    text = entry.summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            entry.steps.forEach { step ->
                Text(
                    text = "• $step",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.phase == CrewApprovalPhase.Waiting) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusPrimaryButton(
                        text = stringResource(R.string.crew_approve),
                        onClick = { onDecide(true) },
                        modifier = Modifier.weight(1f),
                    )
                    FocusOutlinedButton(
                        text = stringResource(R.string.crew_decline),
                        onClick = { onDecide(false) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun approvalPhaseLabel(phase: CrewApprovalPhase): String = when (phase) {
    CrewApprovalPhase.Waiting -> stringResource(R.string.crew_approval_waiting)
    CrewApprovalPhase.Running -> stringResource(R.string.crew_approval_running)
    CrewApprovalPhase.Finished -> stringResource(R.string.crew_approval_done)
    CrewApprovalPhase.Declined -> stringResource(R.string.crew_approval_declined)
}

@Composable
private fun CrewReplyPreview(reply: CrewQuoteUi, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.crew_replying_to,
                    reply.author.ifBlank { stringResource(R.string.crew_you) },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = reply.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = hapticTap(onCancel), modifier = Modifier.size(32.dp)) {
            Icon(
                FocusIcons.Close,
                contentDescription = stringResource(R.string.crew_cancel_reply),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CrewMentionPanel(mention: CrewMentionUi, onPick: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        shape = FocusShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.crew_call_agent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            LazyColumn {
                items(
                    items = mention.members,
                    key = { it.id },
                    contentType = { "member" },
                ) { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = hapticTap { onPick(member.id) })
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CrewAvatar(
                            preset = member.avatarPreset,
                            palette = member.avatarPalette,
                            path = member.avatarPath,
                            size = 34.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleSmallEmphasized,
                            )
                            if (member.duty.isNotBlank()) {
                                Text(
                                    text = member.duty,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
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
