package fan.x0.focus.feature.overlay

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusMarkdown
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusTheme
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.data.assistant.AskAnswer
import fan.x0.focus.data.assistant.AskKind
import kotlinx.coroutines.delay

private val InputShape = RoundedCornerShape(50)
private val ResultShape = RoundedCornerShape(24.dp)

private data class OverlaySuggestion(
    val label: String,
    val prompt: String,
    val icon: ImageVector,
)

@Composable
internal fun OverlayBarContent(
    state: OverlayUiState,
    selectionActive: Boolean,
    autoFocus: Boolean,
    onToggleSelection: () -> Unit,
    onAction: (OverlayAction) -> Unit,
    onCaptureScreen: () -> Unit,
    onClose: () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    val motion = LocalFocusMotion.current
    val dispatch: (OverlayAction) -> Unit = { action ->
        if (action is OverlayAction.Submit && selectionActive) onToggleSelection()
        onAction(action)
    }
    LaunchedEffect(Unit) { appeared = true }
    FocusTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Back && event.type == KeyEventType.KeyUp) {
                        onClose()
                        true
                    } else {
                        false
                    }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = appeared,
                enter = fadeIn(motion.effectsDefault()) +
                    slideInVertically(motion.spatialDefault()) { it / 3 } +
                    scaleIn(motion.spatialDefault(), initialScale = 0.94f),
                exit = fadeOut(motion.effectsFast()),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth(),
                ) {
                    if (state.ask != null) {
                        AskInline(
                            ask = state.ask,
                            onAction = dispatch,
                        )
                    } else {
                        if (state.attachment != null && !state.sending) {
                            SuggestionRow(
                                selection = state.attachment.selection,
                                onAction = dispatch,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        InputBar(
                            state = state,
                            selectionActive = selectionActive,
                            autoFocus = autoFocus,
                            onToggleSelection = onToggleSelection,
                            onAction = dispatch,
                            onCaptureScreen = onCaptureScreen,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun OverlayResultContent(
    state: OverlayUiState,
    onAction: (OverlayAction) -> Unit,
    onClose: () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    val motion = LocalFocusMotion.current
    val context = LocalContext.current
    val uriHandler = remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxCardHeight = (screenHeight * 0.45f).coerceIn(200.dp, 420.dp)
    LaunchedEffect(Unit) { appeared = true }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        FocusTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(
                        visible = appeared,
                        enter = fadeIn(motion.effectsDefault()) +
                            slideInVertically(motion.spatialDefault()) { it / 4 } +
                            scaleIn(motion.spatialDefault(), initialScale = 0.96f),
                        exit = fadeOut(motion.effectsFast()),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            AnimatedContent(
                                targetState = state,
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.TopCenter,
                                transitionSpec = {
                                    (
                                        fadeIn(tween(durationMillis = 220)) +
                                            slideInVertically(tween(durationMillis = 260)) { it / 8 } +
                                            scaleIn(tween(durationMillis = 240), initialScale = 0.97f)
                                        ) togetherWith fadeOut(tween(durationMillis = 160))
                                },
                                contentKey = { current ->
                                    current.messages.lastOrNull { it.role == OverlayRole.Assistant }?.id ?: 0L
                                },
                                label = "overlayTurn",
                            ) { animatedState ->
                                val question = animatedState.messages.lastOrNull { it.role == OverlayRole.User }
                                val answer = animatedState.messages.lastOrNull { it.role == OverlayRole.Assistant }
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 640.dp)
                                        .fillMaxWidth(),
                                ) {
                                    ResultCard(
                                        state = animatedState,
                                        question = question,
                                        answer = answer,
                                        maxHeight = maxCardHeight,
                                        onAction = onAction,
                                    )
                                }
                            }
                        }
                    }
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = onClose,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    state: OverlayUiState,
    question: OverlayMessage?,
    answer: OverlayMessage?,
    maxHeight: Dp,
    onAction: (OverlayAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val errorText = state.errorDetail ?: when (state.error) {
        OverlayError.CaptureFailed -> stringResource(R.string.overlay_capture_failed)
        OverlayError.SendFailed -> stringResource(R.string.overlay_send_failed)
        null -> null
    }
    val scroll = rememberScrollState()
    LaunchedEffect(answer?.id) {
        scroll.scrollTo(0)
    }
    LaunchedEffect(state.approval?.id, state.trust?.id, state.ask?.id) {
        if (state.approval != null || state.trust != null || state.ask != null) {
            scroll.scrollTo(0)
        }
    }
    LaunchedEffect(answer?.text, answer?.streaming) {
        if (answer?.streaming == true) scroll.scrollTo(scroll.maxValue)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .animateContentSize(tween(durationMillis = 160))
            .clip(ResultShape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.95f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), ResultShape)
            .verticalScroll(scroll)
            .padding(16.dp),
    ) {
        when {
            state.approval != null -> ApprovalBlock(state.approval, onAction)
            state.trust != null -> TrustBlock(state.trust, onAction)
            else -> AnswerBlock(
                state = state,
                question = question,
                answer = answer,
                errorText = errorText,
            )
        }
    }
}

@Composable
private fun AnswerBlock(
    state: OverlayUiState,
    question: OverlayMessage?,
    answer: OverlayMessage?,
    errorText: String?,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        question?.text?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        if (state.toolSteps.isNotEmpty()) {
            ToolSteps(state.toolSteps)
            Spacer(modifier = Modifier.height(10.dp))
        }
        when {
            errorText != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FocusIcons.Warning,
                    contentDescription = null,
                    tint = scheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                )
            }
            state.sending && (answer == null || answer.text.isBlank()) -> Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FocusLoadingBlob(size = 20.dp, color = scheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.overlay_thinking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            answer != null && answer.text.isNotBlank() -> {
                FocusMarkdown(
                    text = answer.text,
                    modifier = Modifier.fillMaxWidth(),
                    color = scheme.onSurface,
                    streaming = answer.streaming,
                    bodyStyle = MaterialTheme.typography.bodyMedium,
                )
                if (answer.streaming) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .height(2.dp),
                        color = scheme.primary,
                        trackColor = scheme.surfaceVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolSteps(steps: List<OverlayToolStep>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            key(step.id.ifBlank { "$index-${step.label}" }) {
                ToolStepRow(step)
            }
        }
    }
}

@Composable
private fun ToolStepRow(step: OverlayToolStep) {
    val scheme = MaterialTheme.colorScheme
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 180)) +
            slideInVertically(tween(durationMillis = 200)) { it / 4 } +
            scaleIn(tween(durationMillis = 200), initialScale = 0.96f),
        exit = fadeOut(tween(durationMillis = 120)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = step.status,
                    animationSpec = tween(durationMillis = 160),
                    label = "toolStatus",
                ) { status ->
                    when (status) {
                        OverlayStepStatus.Running -> CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = scheme.primary,
                            strokeWidth = 1.5.dp,
                        )
                        OverlayStepStatus.Pending -> Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(scheme.outlineVariant),
                        )
                        OverlayStepStatus.Done -> Icon(
                            imageVector = FocusIcons.Check,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        OverlayStepStatus.Failed -> Icon(
                            imageVector = FocusIcons.Warning,
                            contentDescription = null,
                            tint = scheme.error,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface,
                )
                step.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalBlock(
    approval: OverlayApproval,
    onAction: (OverlayAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val allow = hapticTap { onAction(OverlayAction.DecideApproval(approval.id, true)) }
    val deny = hapticTap { onAction(OverlayAction.DecideApproval(approval.id, false)) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                if (approval.plan) R.string.overlay_plan else R.string.overlay_approval,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = approval.title,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )
        if (approval.summary.isNotBlank()) {
            Text(
                text = approval.summary,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (approval.steps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            approval.steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = FocusIcons.Tool,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = deny) {
                Text(stringResource(R.string.overlay_deny))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = allow) {
                Text(stringResource(R.string.overlay_allow))
            }
        }
    }
}

@Composable
private fun TrustBlock(
    trust: OverlayTrust,
    onAction: (OverlayAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val allow = hapticTap { onAction(OverlayAction.DecideTrust(trust.id, true)) }
    val deny = hapticTap { onAction(OverlayAction.DecideTrust(trust.id, false)) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.overlay_workspace),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = trust.label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = deny) {
                Text(stringResource(R.string.overlay_deny))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = allow) {
                Text(stringResource(R.string.overlay_trust))
            }
        }
    }
}

@Composable
private fun AskInline(
    ask: OverlayAsk,
    onAction: (OverlayAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val firstFocus = remember(ask.id) { FocusRequester() }
    var selections by remember(ask.id) {
        mutableStateOf<Map<String, Set<String>>>(emptyMap())
    }
    var confirmed by remember(ask.id) {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }
    var texts by remember(ask.id) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    val firstTextId = remember(ask.id) {
        ask.questions.firstOrNull {
            it.kind == AskKind.Text || it.kind == AskKind.Number
        }?.id
    }
    LaunchedEffect(ask.id) {
        if (firstTextId == null) return@LaunchedEffect
        delay(160)
        runCatching { firstFocus.requestFocus() }
    }
    val skip = hapticTap { onAction(OverlayAction.SkipAsk(ask.id)) }

    fun otherKey(question: OverlayQuestion): String = "${question.id}#other"

    fun otherText(question: OverlayQuestion): String =
        texts[otherKey(question)].orEmpty().trim()

    fun answerOf(question: OverlayQuestion): AskAnswer {
        val selected = selections[question.id].orEmpty()
        return when (question.kind) {
            AskKind.Confirm -> AskAnswer(
                questionId = question.id,
                confirmed = confirmed[question.id],
            )
            AskKind.Number -> AskAnswer(
                questionId = question.id,
                number = texts[question.id].orEmpty().trim().toDoubleOrNull(),
            )
            AskKind.Single, AskKind.Multi -> AskAnswer(
                questionId = question.id,
                choices = selected.toList(),
                text = otherText(question),
            )
            AskKind.Text -> AskAnswer(
                questionId = question.id,
                text = texts[question.id].orEmpty().trim(),
            )
        }
    }

    fun ready(question: OverlayQuestion): Boolean {
        val selected = selections[question.id].orEmpty()
        val text = texts[question.id].orEmpty().trim()
        return when (question.kind) {
            AskKind.Confirm -> confirmed[question.id] != null
            AskKind.Number -> text.toDoubleOrNull() != null
            AskKind.Single -> selected.size == 1 || text.isNotBlank() || otherText(question).isNotBlank()
            AskKind.Multi -> selected.isNotEmpty() || text.isNotBlank() || otherText(question).isNotBlank()
            AskKind.Text -> text.isNotBlank()
        }
    }

    val canSend = ask.questions.all(::ready)
    val answer = hapticTap {
        if (canSend) {
            onAction(OverlayAction.AnswerAsk(ask.id, ask.questions.map(::answerOf)))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .clip(ResultShape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.5f), ResultShape)
            .verticalScroll(scroll)
            .padding(14.dp),
    ) {
        Text(
            text = ask.intro.ifBlank { stringResource(R.string.overlay_ask) },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
        )
        ask.questions.forEach { question ->
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = question.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurface,
                )
                if (question.subtitle.isNotBlank()) {
                    Text(
                        text = question.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                when {
                    question.kind == AskKind.Confirm -> {
                        val yesLabel = question.options.getOrNull(0)
                            ?: stringResource(R.string.overlay_yes)
                        val noLabel = question.options.getOrNull(1)
                            ?: stringResource(R.string.overlay_no)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusChip(
                                label = yesLabel,
                                selected = confirmed[question.id] == true,
                                onClick = { confirmed = confirmed + (question.id to true) },
                            )
                            FocusChip(
                                label = noLabel,
                                selected = confirmed[question.id] == false,
                                onClick = { confirmed = confirmed + (question.id to false) },
                            )
                        }
                    }
                    question.kind == AskKind.Single || question.kind == AskKind.Multi -> {
                        if (question.options.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                question.options.forEach { option ->
                                    val selected = option in selections[question.id].orEmpty()
                                    FocusChip(
                                        label = option,
                                        selected = selected,
                                        onClick = {
                                            val current = selections[question.id].orEmpty()
                                            val next = when {
                                                question.kind == AskKind.Multi -> {
                                                    if (selected) current - option else current + option
                                                }
                                                selected -> emptySet()
                                                else -> setOf(option)
                                            }
                                            selections = selections + (question.id to next)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                if (question.allowOther &&
                    (question.kind == AskKind.Single || question.kind == AskKind.Multi)
                ) {
                    OutlinedTextField(
                        value = texts[otherKey(question)].orEmpty(),
                        onValueChange = { texts = texts + (otherKey(question) to it) },
                        placeholder = { Text(stringResource(R.string.overlay_answer_other)) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )
                }
                if (question.kind == AskKind.Text ||
                    question.kind == AskKind.Number ||
                    (question.kind != AskKind.Confirm && question.options.isEmpty())
                ) {
                    OutlinedTextField(
                        value = texts[question.id].orEmpty(),
                        onValueChange = { texts = texts + (question.id to it) },
                        placeholder = {
                            Text(
                                question.placeholder.ifBlank {
                                    stringResource(R.string.overlay_answer_input_hint)
                                },
                            )
                        },
                        keyboardOptions = if (question.kind == AskKind.Number) {
                            KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        } else {
                            KeyboardOptions.Default
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (question.id == firstTextId) {
                                    Modifier.focusRequester(firstFocus)
                                } else {
                                    Modifier
                                },
                            ),
                        singleLine = question.kind == AskKind.Number,
                        maxLines = if (question.kind == AskKind.Number) 1 else 4,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = skip) {
                Text(stringResource(R.string.overlay_skip))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = answer, enabled = canSend) {
                Text(stringResource(R.string.overlay_answer_send))
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    selection: Boolean,
    onAction: (OverlayAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val describe = stringResource(
        if (selection) R.string.overlay_suggestion_describe else R.string.overlay_suggestion_screen,
    )
    val suggestions = listOf(
        OverlaySuggestion(
            label = describe,
            prompt = describe,
            icon = if (selection) FocusIcons.Assistant else FocusIcons.Photo,
        ),
        OverlaySuggestion(
            label = stringResource(R.string.overlay_suggestion_translate),
            prompt = stringResource(R.string.overlay_suggestion_translate),
            icon = FocusIcons.Language,
        ),
        OverlaySuggestion(
            label = stringResource(R.string.overlay_suggestion_extract),
            prompt = stringResource(R.string.overlay_suggestion_extract),
            icon = FocusIcons.File,
        ),
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(suggestions) { suggestion ->
            val click = hapticTap { onAction(OverlayAction.Submit(suggestion.prompt)) }
            SuggestionChip(
                onClick = click,
                label = { Text(suggestion.label) },
                icon = {
                    Icon(
                        imageVector = suggestion.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = scheme.secondaryContainer,
                    labelColor = scheme.onSecondaryContainer,
                    iconContentColor = scheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun InputBar(
    state: OverlayUiState,
    selectionActive: Boolean,
    autoFocus: Boolean,
    onToggleSelection: () -> Unit,
    onAction: (OverlayAction) -> Unit,
    onCaptureScreen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    val defaultQuestion = stringResource(
        if (state.attachment?.selection == true) {
            R.string.overlay_selection_question
        } else {
            R.string.overlay_screen_question
        },
    )
    val hint = stringResource(
        if (state.ask != null) R.string.overlay_answer_input_hint else R.string.overlay_hint,
    )
    val canSend = !state.sending && !state.capturing &&
        (state.input.isNotBlank() || state.attachment != null)
    val send: () -> Unit = {
        if (canSend) {
            val text = state.input.ifBlank {
                if (state.attachment != null) defaultQuestion else ""
            }
            if (text.isNotBlank()) onAction(OverlayAction.Submit(text))
        }
    }
    val capture = hapticTap { onCaptureScreen() }
    val toggleSelection = hapticTap { onToggleSelection() }
    val sendClick = hapticTap {
        if (state.sending) {
            onAction(OverlayAction.Stop)
        } else {
            send()
        }
    }
    val removeAttachment = hapticTap { onAction(OverlayAction.RemoveAttachment) }
    LaunchedEffect(Unit) {
        if (!autoFocus) return@LaunchedEffect
        delay(180)
        runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(InputShape)
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), InputShape)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.ask == null) {
            IconButton(
                onClick = capture,
                enabled = !state.sending && !state.capturing,
            ) {
                if (state.capturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = scheme.primary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = FocusIcons.Camera,
                        contentDescription = stringResource(
                            if (state.attachment != null) {
                                R.string.overlay_screen_attached
                            } else {
                                R.string.overlay_screen
                            },
                        ),
                        tint = when {
                            state.sending -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
                            state.attachment != null -> scheme.primary
                            else -> scheme.onSurfaceVariant
                        },
                    )
                }
            }
            IconButton(
                onClick = toggleSelection,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = FocusIcons.SelectAll,
                    contentDescription = stringResource(
                        if (selectionActive) R.string.overlay_select_exit else R.string.overlay_select,
                    ),
                    tint = if (selectionActive) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }
        if (state.attachment != null) {
            Row(
                modifier = Modifier
                    .clip(InputShape)
                    .background(scheme.secondaryContainer)
                    .padding(start = 8.dp, end = 0.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (state.attachment.selection) {
                        FocusIcons.SelectAll
                    } else {
                        FocusIcons.Photo
                    },
                    contentDescription = null,
                    tint = scheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp),
                )
                IconButton(
                    onClick = removeAttachment,
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = FocusIcons.Close,
                        contentDescription = stringResource(R.string.overlay_screen_remove),
                        tint = scheme.onSecondaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        BasicTextField(
            value = state.input,
            onValueChange = { onAction(OverlayAction.InputChanged(it)) },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
            cursorBrush = SolidColor(scheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { sendClick() }),
            decorationBox = { inner ->
                Box {
                    if (state.input.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                        )
                    }
                    inner()
                }
            },
        )
        FilledIconButton(
            onClick = sendClick,
            enabled = state.sending || canSend,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (state.sending) scheme.error else scheme.primary,
                contentColor = if (state.sending) scheme.onError else scheme.onPrimary,
                disabledContainerColor = scheme.surfaceContainerHigh,
                disabledContentColor = scheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
        ) {
            Icon(
                imageVector = if (state.sending) FocusIcons.Stop else FocusIcons.Send,
                contentDescription = stringResource(
                    if (state.sending) R.string.overlay_stop else R.string.overlay_send,
                ),
            )
        }
    }
}
