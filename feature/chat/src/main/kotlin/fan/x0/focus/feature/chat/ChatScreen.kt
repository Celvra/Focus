package fan.x0.focus.feature.chat

import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fan.x0.focus.core.designsystem.components.AvatarState
import fan.x0.focus.core.designsystem.components.AvatarStyle
import fan.x0.focus.core.designsystem.components.FocusAvatar
import fan.x0.focus.core.designsystem.components.FocusBubbleEnter
import fan.x0.focus.core.designsystem.components.FocusReasoningCard
import fan.x0.focus.core.designsystem.components.FocusTraceCard
import fan.x0.focus.core.designsystem.components.FocusTraceStep
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusMarkdown
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusTimelineStep
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerOpener
import fan.x0.focus.core.designsystem.components.LocalFocusSettingsOpener
import fan.x0.focus.core.designsystem.components.pressScale
import fan.x0.focus.core.designsystem.components.shimmer
import fan.x0.focus.core.designsystem.components.stateLayer
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.core.mvi.CollectEffect
import fan.x0.focus.core.notify.AppPresence
import fan.x0.focus.core.notify.MessageNotifier
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.io.File
import java.time.LocalTime

@Composable
fun ChatRoute(viewModel: ChatViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    val context = LocalContext.current
    val presence = koinInject<AppPresence>()
    val notifier = koinInject<MessageNotifier>()
    val openSettings = LocalFocusSettingsOpener.current
    val openDrawer = LocalFocusDrawerOpener.current
    val copied = stringResource(R.string.chat_copied)
    val deleted = stringResource(R.string.chat_deleted)
    val editSaved = stringResource(R.string.chat_edit_saved)
    CollectEffect(viewModel.effect) { effect ->
        when (effect) {
            ChatEffect.Copied -> snackbar.showSnackbar(copied)
            ChatEffect.Deleted -> snackbar.showSnackbar(deleted)
            ChatEffect.EditSaved -> snackbar.showSnackbar(editSaved)
            ChatEffect.ReplySent -> Unit
            ChatEffect.OpenSettings -> openSettings()
            ChatEffect.OpenSessions -> openDrawer()
        }
    }
    LaunchedEffect(state.conversationId) {
        val id = state.conversationId
        if (id.isNotBlank()) notifier.cancel(id)
    }
    DisposableEffect(state.conversationId) {
        val id = state.conversationId
        if (id.isNotBlank()) presence.setVisibleConversation(id)
        onDispose {
            if (id.isNotBlank() && presence.visibleConversationId.value == id) {
                presence.setVisibleConversation(null)
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onAction(ChatAction.AttachPicked(uris.map { it.toString() }))
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onAction(ChatAction.AttachPicked(uris.map { it.toString() }))
        }
    }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = cameraUri
        cameraUri = null
        if (saved && uri != null) {
            viewModel.onAction(ChatAction.AttachPicked(listOf(uri.toString())))
        }
    }
    ChatScreen(
        state = state,
        onAction = viewModel::onAction,
        snackbar = snackbar,
        onPickFiles = { filePicker.launch(arrayOf("image/*", "text/*", "application/json", "application/xml")) },
        onPickPhotos = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onTakePhoto = {
            val file = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraUri = uri
            camera.launch(uri)
        },
    )
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    onAction: (ChatAction) -> Unit,
    snackbar: SnackbarHostState,
    onPickFiles: () -> Unit = {},
    onPickPhotos: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.chat_copied)
    val copyText: (String) -> Unit = { text ->
        onAction(ChatAction.CloseMessageMenu)
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text)))
            snackbar.showSnackbar(copied)
        }
    }
    BackHandler(enabled = state.selectionKey != null) {
        onAction(ChatAction.ClearSelection)
    }
    LaunchedEffect(state.message?.nonce) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(
            context.getString(message.resId, *message.args.toTypedArray()),
        )
        onAction(ChatAction.ConsumeMessage)
    }
    var celebrate by remember { mutableStateOf(false) }
    var wasSending by remember { mutableStateOf(false) }
    LaunchedEffect(state.sending) {
        if (wasSending && !state.sending) {
            celebrate = true
            delay(1600)
            celebrate = false
        }
        wasSending = state.sending
    }
    val avatarState = state.avatarState(celebrate)
    val working = state.sending
    val retry = state.retry
    var mascotTop by remember { mutableStateOf<Offset?>(null) }
    var mascotBottom by remember { mutableStateOf<Offset?>(null) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var composerTop by remember { mutableStateOf(Float.POSITIVE_INFINITY) }
    LaunchedEffect(state.conversationId) { mascotBottom = null }
    LaunchedEffect(working) {
        if (!working) mascotBottom = null
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                FocusCompactTopBar(
                    title = state.title.ifBlank {
                        state.assistantName.ifBlank { stringResource(R.string.chat_title) }
                    },
                    subtitle = when {
                        retry != null -> stringResource(R.string.chat_retrying, retry.attempt, retry.max)
                        state.compacting -> stringResource(R.string.chat_compacting)
                        state.sending -> state.workingText.ifBlank {
                            stringResource(R.string.chat_working)
                        }
                        state.title.isBlank() -> stringResource(R.string.chat_subtitle)
                        else -> null
                    },
                    onMenu = LocalFocusDrawerOpener.current,
                    actions = {
                        if (!state.isEmpty) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(ChatMascotSize)
                                    .onGloballyPositioned { mascotTop = it.positionInRoot() },
                            )
                        }
                        IconButton(onClick = { onAction(ChatAction.NewConversation) }) {
                            Icon(
                                FocusIcons.Add,
                                contentDescription = stringResource(R.string.chat_new),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
        ) { padding ->
            var composerHeight by remember { mutableStateOf(0) }
            val hazeState = rememberHazeState()
            val composerPadding = with(LocalDensity.current) { composerHeight.toDp() } + 12.dp
            val composerOffset = with(LocalDensity.current) { composerHeight.toDp() } + 8.dp
            val motion = LocalFocusMotion.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
                    AnimatedContent(
                        targetState = state,
                        contentKey = {
                            it.conversationId to when {
                                it.loading -> ChatBody.Loading
                                it.isEmpty -> ChatBody.Home
                                else -> ChatBody.Thread
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        label = "chatConversation",
                    ) { conversation ->
                        key(conversation.conversationId) {
                            when {
                                conversation.loading -> FocusLoadingBlock()
                                conversation.isEmpty -> ChatHome(
                                    state = conversation,
                                    onAction = onAction,
                                    bottomPadding = composerPadding,
                                )
                                else -> ChatThread(
                                    state = conversation,
                                    onAction = onAction,
                                    onCopy = copyText,
                                    bottomPadding = composerPadding,
                                    working = working,
                                    onMascotDock = { mascotBottom = it },
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = state.slash.visible,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = composerOffset),
                    enter = slideInVertically(motion.spatialDefault()) { it } +
                        fadeIn(motion.effectsDefault()),
                    exit = slideOutVertically(motion.spatialDefault()) { it } +
                        fadeOut(motion.effectsFast()),
                ) {
                    SlashCommandPanel(slash = state.slash, onAction = onAction)
                }
                AnimatedVisibility(
                    visible = state.selectionKey != null && !state.slash.visible,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = composerOffset),
                    enter = slideInVertically(motion.spatialDefault()) { it } +
                        fadeIn(motion.effectsDefault()),
                    exit = slideOutVertically(motion.spatialDefault()) { it } +
                        fadeOut(motion.effectsFast()),
                ) {
                    SelectionModeBar(onDone = { onAction(ChatAction.ClearSelection) })
                }
                Composer(
                    state = state,
                    hazeState = hazeState,
                    onAction = onAction,
                    onPickFiles = onPickFiles,
                    onPickPhotos = onPickPhotos,
                    onTakePhoto = onTakePhoto,
                    onHeightChange = { composerHeight = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { composerTop = it.positionInRoot().y },
                )
            }
        }
        if (!state.isEmpty && !state.slash.visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(bottom = (composerTop - overlayOrigin.y).coerceIn(0f, size.height)) {
                            this@drawWithContent.drawContent()
                        }
                    },
            ) {
                ChatMascot(
                    state = avatarState,
                    working = working,
                    topDock = mascotTop,
                    bottomDock = mascotBottom,
                    overlayOrigin = overlayOrigin,
                )
            }
        }
        if (state.btw.open) {
            BtwSheet(
                btw = state.btw,
                onClose = { onAction(ChatAction.CloseBtw) },
                onRetry = { onAction(ChatAction.RetryBtw) },
            )
        }
        state.subAgent?.let { run ->
            SubAgentSheet(
                run = run,
                onClose = { onAction(ChatAction.CloseSubAgent) },
            )
        }
        state.edit?.let { edit ->
            MessageEditSheet(
                edit = edit,
                onDraftChange = { onAction(ChatAction.EditDraftChanged(it)) },
                onSave = { onAction(ChatAction.SaveEdit) },
                onDismiss = { onAction(ChatAction.CancelEdit) },
            )
        }
        if (state.pendingDelete != null) {
            MessageDeleteDialog(
                onConfirm = { onAction(ChatAction.ConfirmDelete) },
                onDismiss = { onAction(ChatAction.CancelDelete) },
            )
        }
    }
}

@Composable
private fun ChatHome(
    state: ChatUiState,
    onAction: (ChatAction) -> Unit,
    bottomPadding: Dp = 0.dp,
) {
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 5 -> R.string.chat_greeting_night
        hour < 12 -> R.string.chat_greeting_morning
        hour < 18 -> R.string.chat_greeting_afternoon
        else -> R.string.chat_greeting_evening
    }
    val title = buildString {
        append(stringResource(greeting))
        state.name?.takeIf { it.isNotBlank() }?.let { append(", ").append(it) }
    }
    val generated = state.suggestions
    val suggestions = if (generated.isEmpty()) {
        defaultSuggestions()
    } else {
        generated.map { Suggestion(suggestionIcon(it.icon), it.title, it.prompt) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 12.dp,
            end = 20.dp,
            bottom = bottomPadding + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "hero") {
            FocusReveal {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FocusAvatar(style = AvatarStyle(preset = 0, palette = 0), size = 96.dp)
                    Text(
                        text = title,
                        style = LocalFocusTypography.current.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.chat_home_tagline),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item(key = "suggestions-label") {
            FocusReveal(index = 1) {
                Text(
                    text = stringResource(R.string.chat_home_suggestions),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            }
        }
        items(
            suggestions,
            key = { it.prompt },
        ) { suggestion ->
            FocusReveal(index = 2) {
                SuggestionCard(
                    suggestion = suggestion,
                    onClick = { onAction(ChatAction.Suggest(suggestion.prompt)) },
                )
            }
        }
    }
}

private data class Suggestion(
    val icon: ImageVector,
    val title: String,
    val prompt: String,
)

@Composable
private fun defaultSuggestions(): List<Suggestion> = listOf(
    Suggestion(
        icon = FocusIcons.Calendar,
        title = stringResource(R.string.chat_suggest_schedule),
        prompt = stringResource(R.string.chat_suggest_schedule_prompt),
    ),
    Suggestion(
        icon = FocusIcons.Feed,
        title = stringResource(R.string.chat_suggest_reading),
        prompt = stringResource(R.string.chat_suggest_reading_prompt),
    ),
    Suggestion(
        icon = FocusIcons.Goals,
        title = stringResource(R.string.chat_suggest_goals),
        prompt = stringResource(R.string.chat_suggest_goals_prompt),
    ),
)

private fun suggestionIcon(key: String): ImageVector = when (key) {
    "calendar" -> FocusIcons.Calendar
    "feed" -> FocusIcons.Feed
    "goals" -> FocusIcons.Goals
    "task" -> FocusIcons.Check
    "idea" -> FocusIcons.Idea
    "folder" -> FocusIcons.Folder
    "today" -> FocusIcons.Today
    else -> FocusIcons.Assistant
}

@Composable
private fun SuggestionCard(suggestion: Suggestion, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FocusIconBadge(icon = suggestion.icon, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = suggestion.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = FocusIcons.Chevron,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal const val ChatThreadTag = "chat-thread"

@Composable
private fun ChatThread(
    state: ChatUiState,
    onAction: (ChatAction) -> Unit,
    onCopy: (String) -> Unit,
    bottomPadding: Dp = 0.dp,
    working: Boolean = false,
    onMascotDock: (Offset?) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val entries = state.entries
    val retry = state.retry
    val todos = state.todos
    val stats = state.stats?.takeIf { it.hasStats(state.statsConfig) }
    var follow by remember { mutableStateOf(true) }
    val played = remember(state.conversationId) { mutableSetOf<String>() }

    LaunchedEffect(state.conversationId) { follow = true }

    LaunchedEffect(state.turn) {
        follow = true
    }

    // Follow streaming only while the reader has not dragged away from the end.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> follow = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    snapshotFlow { listState.isScrollInProgress }.first { !it }
                    follow = listState.atBottom()
                }
            }
        }
    }

    LaunchedEffect(entries.size, state.live, bottomPadding, working, retry, stats, todos) {
        if (follow && entries.isNotEmpty()) {
            listState.requestScrollToItem(
                entries.size +
                    (if (retry != null) 1 else 0) +
                    (if (todos.isNotEmpty()) 1 else 0) +
                    (if (stats != null) 1 else 0) +
                    (if (working) 1 else 0),
            )
        }
    }

    LaunchedEffect(state.highlightKey) {
        val key = state.highlightKey ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.key == key }
        if (index < 0) {
            onAction(ChatAction.ConsumeHighlight)
            return@LaunchedEffect
        }
        listState.animateScrollToItem(index)
        delay(1600)
        onAction(ChatAction.ConsumeHighlight)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(ChatThreadTag),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = bottomPadding + if (state.selectionKey != null) 64.dp else 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = entries,
                key = { it.key },
                contentType = {
                    when (it) {
                        is ChatEntry.User -> "user"
                        is ChatEntry.Assistant -> "assistant"
                        is ChatEntry.Trace -> "trace"
                        is ChatEntry.Approval -> "approval"
                        is ChatEntry.Plan -> "plan"
                        is ChatEntry.Ask -> "ask"
                        is ChatEntry.Failure -> "failure"
                        is ChatEntry.Notice -> "notice"
                        is ChatEntry.Compaction -> "compaction"
                    }
                },
            ) { entry ->
                val play = entry.isFresh() && entry.key !in played
                ChatBubbleEnter(
                    play = play,
                    fromUser = entry is ChatEntry.User,
                    onPlayed = { played += entry.key },
                ) {
                    when (entry) {
                        is ChatEntry.User -> Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (entry.attachments.isNotEmpty()) {
                                UserAttachmentStrip(attachments = entry.attachments)
                            }
                            if (entry.text.isNotBlank()) {
                                ChatMessageBubble(
                                    entry = entry,
                                    fromUser = true,
                                    state = state,
                                    onAction = onAction,
                                    onCopy = onCopy,
                                )
                            } else if (entry.reactions.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    entry.reactions.distinct().forEach { reaction ->
                                        MessageReactionChip(
                                            reaction = reaction,
                                            count = entry.reactions.count { it == reaction },
                                        )
                                    }
                                }
                            }
                        }
                        is ChatEntry.Assistant -> Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (entry.reasoning.isNotBlank()) {
                                key(entry.key) {
                                    FocusReasoningCard(
                                        text = entry.reasoning,
                                        running = entry.live,
                                        durationMs = entry.reasoningMs,
                                        startedAt = entry.reasoningStartedAt,
                                    )
                                }
                            }
                            if (entry.text.isNotBlank()) {
                                ChatMessageBubble(
                                    entry = entry,
                                    fromUser = false,
                                    state = state,
                                    onAction = onAction,
                                    onCopy = onCopy,
                                    streaming = entry.live,
                                )
                            }
                        }
                        is ChatEntry.Trace -> FocusTraceCard(
                            steps = entry.steps.map {
                                FocusTraceStep(
                                    label = it.label,
                                    status = it.status.toStepStatus(),
                                    summary = it.summary,
                                    openKey = if (it.tool == "spawn_agent" && it.id.isNotBlank()) {
                                        it.id
                                    } else {
                                        null
                                    },
                                    durationMs = it.durationMs,
                                    startedAt = it.startedAt,
                                    diffs = it.diffs.map { diff -> diff.toFocusDiff() },
                                    media = it.media.mapNotNull { image -> image.toFocusTraceImage() },
                                )
                            },
                            onOpen = { onAction(ChatAction.OpenSubAgent(it)) },
                        )
                        is ChatEntry.Approval -> FocusApprovalCard(
                            card = entry.card,
                            onDecide = { onAction(ChatAction.Decide(entry.card.id, it)) },
                        )
                        is ChatEntry.Plan -> FocusApprovalCard(
                            card = entry.card,
                            onDecide = { approve ->
                                onAction(
                                    ChatAction.DecidePlan(
                                        approvalId = entry.card.id,
                                        approve = approve,
                                        policy = state.policy,
                                    ),
                                )
                            },
                        )
                        is ChatEntry.Ask -> FocusAskCard(
                            card = entry.card,
                            drafts = state.askDrafts,
                            onAction = onAction,
                        )
                        is ChatEntry.Failure -> ChatFailureCard(
                            message = entry.message,
                            retryable = entry.retryable,
                            onRetry = { onAction(ChatAction.RetryLast) },
                        )
                        is ChatEntry.Notice -> ChatNoticeCard(text = entry.text)
                        is ChatEntry.Compaction -> ChatCompactionCard(
                            text = entry.summary,
                            streaming = entry.streaming,
                            tokensBefore = entry.tokensBefore,
                            tokensAfter = entry.tokensAfter,
                        )
                    }
                }
            }
            if (retry != null) {
                item(key = "retry") {
                    ChatRetryCard(
                        retry = retry,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (todos.isNotEmpty()) {
                item(key = "todos", contentType = "todos") {
                    ChatTodoCard(
                        conversationId = state.conversationId,
                        todos = todos,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (stats != null) {
                item(key = "stats") {
                    ChatStatsLine(
                        stats = stats,
                        config = state.statsConfig,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            item(key = "tail") { Spacer(Modifier.height(1.dp)) }
            if (working) {
                item(key = "mascot") {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(ChatMascotSize)
                            .onGloballyPositioned { onMascotDock(it.positionInRoot()) },
                    )
                }
            }
        }

        JumpToLatest(
            visible = !follow && entries.isNotEmpty(),
            onClick = {
                scope.launch {
                    val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                    listState.animateScrollToItem(target)
                    follow = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomPadding + 16.dp),
        )
    }
}

private fun ChatUiState.avatarState(celebrate: Boolean): AvatarState = when {
    approval?.phase == ApprovalPhase.Waiting -> AvatarState.Notify
    ask?.phase == AskPhase.Waiting -> AvatarState.Notify
    approval?.phase == ApprovalPhase.Declined -> AvatarState.Alert
    approval?.phase == ApprovalPhase.Running -> AvatarState.Orbit
    sending || streaming.isNotBlank() -> AvatarState.Orbit
    approval?.phase == ApprovalPhase.Finished -> AvatarState.Wink
    celebrate -> AvatarState.Wink
    else -> AvatarState.Idle
}

private enum class ChatBody { Loading, Home, Thread }

// Entries created during this session; entries restored from the database skip the pop-in.
private fun ChatEntry.isFresh(): Boolean = when (this) {
    is ChatEntry.Approval -> true
    is ChatEntry.Ask -> true
    else -> key.startsWith("local-") ||
        key.startsWith("live-answer-") ||
        key.startsWith("live-trace-")
}

// New bubbles pop in from their own corner; history entries render without motion.
@Composable
private fun ChatBubbleEnter(
    play: Boolean,
    fromUser: Boolean,
    onPlayed: () -> Unit,
    content: @Composable () -> Unit,
) = FocusBubbleEnter(play, fromUser, onPlayed, content)

private fun LazyListState.atBottom(): Boolean {
    val layout = layoutInfo
    val last = layout.visibleItemsInfo.lastOrNull() ?: return true
    return last.index >= layout.totalItemsCount - 1
}

@Composable
private fun JumpToLatest(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(motion.effectsDefault()) +
            scaleIn(motion.spatialFast(), initialScale = 0.7f) +
            slideInVertically(motion.spatialFast()) { height -> height / 4 },
        exit = fadeOut(motion.effectsFast()) +
            scaleOut(motion.spatialFast(), targetScale = 0.7f),
    ) {
        SmallFloatingActionButton(
            onClick = onClick,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Icon(
                imageVector = FocusIcons.ArrowDown,
                contentDescription = stringResource(R.string.chat_jump_latest),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
