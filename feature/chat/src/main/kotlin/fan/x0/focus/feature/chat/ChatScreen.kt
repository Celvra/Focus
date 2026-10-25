package fan.x0.focus.feature.chat

import android.net.Uri
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import fan.x0.focus.core.designsystem.components.FocusChatBubble
import fan.x0.focus.core.designsystem.components.FocusCompactTopBar
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusLoadingBlock
import fan.x0.focus.core.designsystem.components.FocusMarkdown
import fan.x0.focus.core.designsystem.components.FocusReveal
import fan.x0.focus.core.designsystem.components.FocusTimelineStep
import fan.x0.focus.core.designsystem.components.LocalFocusDrawerOpener
import fan.x0.focus.core.designsystem.components.pressScale
import fan.x0.focus.core.designsystem.components.shimmer
import fan.x0.focus.core.designsystem.components.stateLayer
import fan.x0.focus.core.designsystem.error.HandleAppErrors
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.io.File
import java.time.LocalTime

@Composable
fun ChatRoute(viewModel: ChatViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    HandleAppErrors(viewModel.errors, snackbar)
    val context = LocalContext.current
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
    var mascotTop by remember { mutableStateOf<Offset?>(null) }
    var mascotBottom by remember { mutableStateOf<Offset?>(null) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(state.conversationId) { mascotBottom = null }
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
                    title = state.title.ifBlank { stringResource(R.string.chat_title) },
                    subtitle = when {
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
                                    bottomPadding = composerPadding,
                                    working = working,
                                    onMascotDock = { mascotBottom = it },
                                )
                            }
                        }
                    }
                }
                Composer(
                    state = state,
                    hazeState = hazeState,
                    onAction = onAction,
                    onPickFiles = onPickFiles,
                    onPickPhotos = onPickPhotos,
                    onTakePhoto = onTakePhoto,
                    onHeightChange = { composerHeight = it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        if (!state.isEmpty) {
            ChatMascot(
                state = avatarState,
                working = working,
                topDock = mascotTop,
                bottomDock = mascotBottom,
                overlayOrigin = overlayOrigin,
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
            listOf(
                Suggestion(
                    icon = FocusIcons.Calendar,
                    title = R.string.chat_suggest_schedule,
                    prompt = R.string.chat_suggest_schedule_prompt,
                ),
                Suggestion(
                    icon = FocusIcons.Feed,
                    title = R.string.chat_suggest_reading,
                    prompt = R.string.chat_suggest_reading_prompt,
                ),
                Suggestion(
                    icon = FocusIcons.Goals,
                    title = R.string.chat_suggest_goals,
                    prompt = R.string.chat_suggest_goals_prompt,
                ),
            ),
            key = { it.title },
        ) { suggestion ->
            val prompt = stringResource(suggestion.prompt)
            FocusReveal(index = 2) {
                SuggestionCard(
                    suggestion = suggestion,
                    onClick = { onAction(ChatAction.Suggest(prompt)) },
                )
            }
        }
    }
}

private data class Suggestion(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: Int,
    val prompt: Int,
)

@Composable
private fun SuggestionCard(suggestion: Suggestion, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val prompt = stringResource(suggestion.prompt)
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
                    text = stringResource(suggestion.title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(suggestion.prompt),
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
    bottomPadding: Dp = 0.dp,
    working: Boolean = false,
    onMascotDock: (Offset?) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val entries = state.entries
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

    LaunchedEffect(entries.size, state.live, bottomPadding, working) {
        if (follow && entries.isNotEmpty()) listState.requestScrollToItem(entries.size)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(ChatThreadTag),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 8.dp,
                end = 16.dp,
                bottom = bottomPadding + 8.dp,
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
                        is ChatEntry.User -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (entry.attachments.isNotEmpty()) {
                                UserAttachmentStrip(attachments = entry.attachments)
                            }
                            if (entry.text.isNotBlank()) {
                                FocusChatBubble(text = entry.text, fromUser = true)
                            }
                        }
                        is ChatEntry.Assistant -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (entry.reasoning.isNotBlank()) {
                                key(entry.key) {
                                    ReasoningCard(text = entry.reasoning, running = entry.live)
                                }
                            }
                            if (entry.text.isNotBlank()) {
                                FocusChatBubble(
                                    text = entry.text,
                                    fromUser = false,
                                    streaming = entry.live,
                                )
                            }
                        }
                        is ChatEntry.Trace -> TraceCard(steps = entry.steps)
                        is ChatEntry.Approval -> FocusApprovalCard(
                            card = entry.card,
                            onDecide = { onAction(ChatAction.Decide(entry.card.id, it)) },
                        )
                    }
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
) {
    val motion = LocalFocusMotion.current
    val density = LocalDensity.current
    val alpha = remember { Animatable(if (play) 0f else 1f) }
    val progress = remember { Animatable(if (play) 0f else 1f) }
    var settled by remember { mutableStateOf(!play) }
    LaunchedEffect(Unit) {
        if (play) {
            onPlayed()
            coroutineScope {
                launch { alpha.animateTo(1f, motion.effectsDefault()) }
                launch { progress.animateTo(1f, motion.spatialDefault()) }
            }
            settled = true
        }
    }
    val startScale = if (fromUser) 0.88f else 0.94f
    val startLift = with(density) { (if (fromUser) 18.dp else 12.dp).toPx() }
    val origin = if (fromUser) TransformOrigin(1f, 1f) else TransformOrigin(0f, 1f)
    Box(
        modifier = if (settled) {
            Modifier
        } else {
            Modifier.graphicsLayer {
                this.alpha = alpha.value
                scaleX = startScale + (1f - startScale) * progress.value
                scaleY = scaleX
                translationY = startLift * (1f - progress.value)
                transformOrigin = origin
            }
        },
    ) {
        content()
    }
}

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

@Composable
private fun ReasoningCard(text: String, running: Boolean) {
    val motion = LocalFocusMotion.current
    var expanded by remember { mutableStateOf(running) }
    LaunchedEffect(running) {
        if (!running) expanded = false
    }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialFast(),
        label = "reasoningChevron",
    )
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    Card(
        shape = FocusShapeTokens.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .stateLayer(interaction, shape)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                        expanded = !expanded
                    }
                    .heightIn(min = 40.dp)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = FocusIcons.Idea,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(if (running) R.string.chat_reasoning_live else R.string.chat_reasoning),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .shimmer(running, highlight = MaterialTheme.colorScheme.onSurface),
                )
                Icon(
                    imageVector = FocusIcons.ArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).rotate(chevron),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.spatialDefault()) + fadeIn(motion.effectsDefault()),
                exit = shrinkVertically(motion.spatialDefault()) + fadeOut(motion.effectsFast()),
            ) {
                ReasoningBody(text = text, running = running)
            }
        }
    }
}

@Composable
private fun ReasoningBody(text: String, running: Boolean) {
    val scroll = rememberScrollState()
    LaunchedEffect(text, running) {
        if (running) scroll.animateScrollTo(scroll.maxValue)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (running) {
                    Modifier
                        .graphicsLayer { alpha = 0.99f }
                        .drawWithCache {
                            val height = size.height.takeIf { it > 0f } ?: 1f
                            val fade = (20.dp.toPx() / height).coerceIn(0f, 0.33f)
                            val brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    fade to Color.Black,
                                ),
                            )
                            onDrawWithContent {
                                drawContent()
                                drawRect(brush = brush, blendMode = BlendMode.DstIn)
                            }
                        }
                        .heightIn(max = 120.dp)
                        .verticalScroll(scroll)
                } else {
                    Modifier
                }
            )
    ) {
        FocusMarkdown(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            streaming = running,
            bodyStyle = MaterialTheme.typography.bodySmall,
            preserveLineBreaks = true,
            modifier = Modifier.padding(start = 28.dp, end = 8.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun TraceCard(steps: List<TraceStep>) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val running = steps.any { it.status == TraceStatus.Running || it.status == TraceStatus.Pending }
    val failed = steps.any { it.status == TraceStatus.Failed }
    var expanded by remember { mutableStateOf(running) }
    // Collapse once the turn settles; a later tap still reopens it.
    LaunchedEffect(running) {
        if (!running) expanded = false
    }
    val container by animateColorAsState(
        targetValue = if (running) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        animationSpec = motion.effectsDefault(),
        label = "traceContainer",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialFast(),
        label = "traceChevron",
    )
    val headerInteraction = remember { MutableInteractionSource() }
    val headerShape = MaterialTheme.shapes.small

    Card(
        shape = FocusShapeTokens.card,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .stateLayer(headerInteraction, headerShape)
                    .clickable(
                        interactionSource = headerInteraction,
                        indication = null,
                        role = Role.Button,
                    ) { expanded = !expanded }
                    .heightIn(min = 40.dp)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    when {
                        running -> FocusLoadingBlob(size = 16.dp)
                        failed -> Icon(
                            imageVector = FocusIcons.Warning,
                            contentDescription = null,
                            tint = scheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        else -> Icon(
                            imageVector = FocusIcons.Check,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.chat_tools_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pluralStringResource(R.plurals.chat_tools_count, steps.size, steps.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = FocusIcons.ArrowDown,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(chevron),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.spatialDefault()) + fadeIn(motion.effectsDefault()),
                exit = shrinkVertically(motion.spatialDefault()) + fadeOut(motion.effectsFast()),
            ) {
                Column {
                    steps.forEachIndexed { index, step ->
                        key(index, step.label) {
                            FocusTimelineStep(
                                label = step.label,
                                status = step.status.toStepStatus(),
                                summary = step.summary,
                                isFirst = index == 0,
                                isLast = index == steps.lastIndex,
                            )
                        }
                    }
                }
            }
        }
    }
}
