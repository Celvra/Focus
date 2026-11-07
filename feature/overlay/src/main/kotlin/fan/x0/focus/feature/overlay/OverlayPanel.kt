package fan.x0.focus.feature.overlay

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import fan.x0.focus.core.designsystem.error.message
import fan.x0.focus.core.designsystem.theme.FocusTheme
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.data.assistant.AskAnswer
import fan.x0.focus.data.assistant.AskKind
import fan.x0.focus.data.voice.VoiceInputHandle
import fan.x0.focus.data.voice.rememberVoiceInput
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

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
    onAction: (OverlayAction) -> Unit,
    onCaptureScreen: () -> Unit,
    onClose: () -> Unit,
) {
    var appeared by remember { mutableStateOf(false) }
    val motion = LocalFocusMotion.current
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
                            onAction = onAction,
                        )
                    } else {
                        if (state.attachment != null && !state.sending) {
                            SuggestionRow(
                                selection = state.attachment.selection,
                                onAction = onAction,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        InputBar(
                            state = state,
                            onAction = onAction,
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
                                    fadeIn(tween(durationMillis = 180)) togetherWith
                                        fadeOut(tween(durationMillis = 120)) using
                                        SizeTransform(clip = false) { _, _ -> snap() }
                                },
                                contentKey = { current ->
                                    current.messages.lastOrNull { it.role == OverlayRole.Assistant }?.id ?: 0L
                                },
                                label = "overlayTurn",
                            ) { animatedState ->
                                val questionIndex = animatedState.messages.indexOfLast { it.role == OverlayRole.User }
                                val question = animatedState.messages.getOrNull(questionIndex)
                                val answer = animatedState.messages
                                    .drop(questionIndex + 1)
                                    .lastOrNull { it.role == OverlayRole.Assistant }
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
            question != null && (answer == null || answer.text.isBlank()) -> Text(
                text = stringResource(R.string.overlay_no_reply),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
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
        item(key = "attachment") {
            AttachmentSuggestionChip(
                selection = selection,
                onRemove = hapticTap { onAction(OverlayAction.RemoveAttachment) },
            )
        }
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InputBar(
    state: OverlayUiState,
    onAction: (OverlayAction) -> Unit,
    onCaptureScreen: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var typing by remember { mutableStateOf(false) }
    val voice = rememberVoiceInput(
        input = state.input,
        onInput = { onAction(OverlayAction.InputChanged(it)) },
    )
    val voiceError = voice.error?.message()
    LaunchedEffect(voiceError) {
        if (voiceError != null) {
            Toast.makeText(context, voiceError, Toast.LENGTH_SHORT).show()
        }
    }
    val focusRequester = remember { FocusRequester() }
    val defaultQuestion = stringResource(
        if (state.attachment?.selection == true) {
            R.string.overlay_selection_question
        } else {
            R.string.overlay_screen_question
        },
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
    val sendClick = hapticTap {
        if (state.sending) onAction(OverlayAction.Stop) else send()
    }
    val enterTyping = {
        voice.cancel()
        typing = true
    }
    val enterVoice = {
        typing = false
        keyboard?.hide()
        voice.start()
    }
    LaunchedEffect(typing) {
        if (!typing) return@LaunchedEffect
        delay(260)
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(voice.autoVoice, typing) {
        if (typing) return@LaunchedEffect
        if (!voice.autoVoice) return@LaunchedEffect
        if (state.input.isNotBlank()) return@LaunchedEffect
        delay(220)
        if (!voice.listening && !voice.busy) voice.start()
    }
    if (typing) {
        TypingDock(
            state = state,
            canSend = canSend,
            focusRequester = focusRequester,
            sendClick = sendClick,
            onCaptureScreen = onCaptureScreen,
            onVoice = enterVoice,
            onAction = onAction,
        )
    } else {
        VoiceDock(
            state = state,
            canSend = canSend,
            voice = voice,
            onTyping = enterTyping,
            onCaptureScreen = onCaptureScreen,
            send = send,
            onAction = onAction,
        )
    }
}

private sealed interface VoiceCenter {
    data class Transcript(val text: String) : VoiceCenter
    data class Status(val text: String) : VoiceCenter
    data object Listening : VoiceCenter
    data object Idle : VoiceCenter
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VoiceDock(
    state: OverlayUiState,
    canSend: Boolean,
    voice: VoiceInputHandle,
    onTyping: () -> Unit,
    onCaptureScreen: () -> Unit,
    send: () -> Unit,
    onAction: (OverlayAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalFocusMotion.current
    val status = voice.status
    val loading = voice.listening && status != null
    val working = loading || voice.busy || state.sending
    val listening = voice.listening && !loading && !voice.busy
    val statusText = when {
        status != null -> status
        voice.busy || state.sending -> stringResource(R.string.overlay_voice_working)
        else -> null
    }
    val center = when {
        listening && state.input.isBlank() -> VoiceCenter.Listening
        state.input.isNotBlank() -> VoiceCenter.Transcript(state.input)
        statusText != null -> VoiceCenter.Status(statusText)
        else -> VoiceCenter.Idle
    }
    val placeholder = stringResource(R.string.overlay_shortcut_short)
    val startClick = hapticTap { voice.start() }
    val typingClick = hapticTap(onTyping)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = InputShape,
        color = scheme.surfaceContainerHigh.copy(alpha = 0.96f),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(start = 8.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CameraButton(
                    capturing = state.capturing,
                    enabled = !state.sending,
                    onCaptureScreen = onCaptureScreen,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = typingClick)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedVisibility(
                            visible = listening && state.input.isNotBlank(),
                            enter = fadeIn(motion.effectsFast()) +
                                expandHorizontally(
                                    animationSpec = motion.spatialDefault(),
                                    expandFrom = Alignment.Start,
                                ),
                            exit = fadeOut(motion.effectsFast()) +
                                shrinkHorizontally(
                                    animationSpec = motion.spatialDefault(),
                                    shrinkTowards = Alignment.Start,
                                ),
                        ) {
                            Row(modifier = Modifier.padding(end = 10.dp)) {
                                ListeningBars(
                                    color = scheme.onSurface,
                                    level = voice.level,
                                    compact = true,
                                )
                            }
                        }
                        AnimatedContent(
                            targetState = center,
                            modifier = Modifier.weight(1f),
                            contentKey = { it::class },
                            transitionSpec = {
                                (
                                    fadeIn(motion.effectsFast()) +
                                        scaleIn(motion.spatialDefault(), initialScale = 0.88f)
                                    ) togetherWith (
                                    fadeOut(motion.effectsFast()) +
                                        scaleOut(motion.spatialDefault(), targetScale = 0.88f)
                                    )
                            },
                            contentAlignment = Alignment.CenterStart,
                            label = "voiceCenter",
                        ) { current ->
                            when (current) {
                                is VoiceCenter.Transcript -> Text(
                                    text = current.text,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = scheme.onSurface,
                                )
                                is VoiceCenter.Status -> Text(
                                    text = current.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                VoiceCenter.Listening -> ListeningBars(
                                    color = scheme.onSurface,
                                    level = voice.level,
                                )
                                VoiceCenter.Idle -> Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                when {
                    state.sending -> DockCircle(
                        container = scheme.error,
                        contentColor = scheme.onError,
                        onClick = { onAction(OverlayAction.Stop) },
                    ) {
                        Icon(
                            imageVector = FocusIcons.Stop,
                            contentDescription = stringResource(R.string.overlay_stop),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    working -> DockCircle(
                        container = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                        onClick = { voice.cancel() },
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(22.dp),
                            color = scheme.onPrimaryContainer,
                        )
                    }
                    listening -> DockCircle(
                        container = scheme.primaryContainer,
                        contentColor = scheme.onPrimaryContainer,
                        onClick = { voice.stop() },
                    ) {
                        Icon(
                            imageVector = FocusIcons.Mic,
                            contentDescription = stringResource(R.string.overlay_voice_stop),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    else -> {
                        IconButton(
                            onClick = startClick,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Icon(
                                imageVector = FocusIcons.Mic,
                                contentDescription = stringResource(R.string.overlay_voice_start),
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        if (canSend) {
                            DockCircle(
                                container = scheme.primary,
                                contentColor = scheme.onPrimary,
                                onClick = send,
                            ) {
                                Icon(
                                    imageVector = FocusIcons.Send,
                                    contentDescription = stringResource(R.string.overlay_send),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        } else {
                            IconButton(
                                onClick = typingClick,
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = FocusIcons.Keyboard,
                                    contentDescription = stringResource(R.string.overlay_typing_mode),
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TypingDock(
    state: OverlayUiState,
    canSend: Boolean,
    focusRequester: FocusRequester,
    sendClick: () -> Unit,
    onCaptureScreen: () -> Unit,
    onVoice: () -> Unit,
    onAction: (OverlayAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hint = stringResource(R.string.overlay_hint)
    val voiceClick = hapticTap(onVoice)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh.copy(alpha = 0.96f),
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            BasicTextField(
                value = state.input,
                onValueChange = { onAction(OverlayAction.InputChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .padding(vertical = 8.dp)
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.titleMedium.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendClick() }),
                decorationBox = { inner ->
                    Box {
                        if (state.input.isEmpty()) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        inner()
                    }
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CameraButton(
                    capturing = state.capturing,
                    enabled = !state.sending,
                    onCaptureScreen = onCaptureScreen,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = voiceClick,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = FocusIcons.Mic,
                        contentDescription = stringResource(R.string.overlay_voice_mode),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                DockCircle(
                    container = if (state.sending) scheme.error else scheme.primary,
                    contentColor = if (state.sending) scheme.onError else scheme.onPrimary,
                    onClick = sendClick,
                    enabled = state.sending || canSend,
                ) {
                    Icon(
                        imageVector = if (state.sending) FocusIcons.Stop else FocusIcons.Send,
                        contentDescription = stringResource(
                            if (state.sending) R.string.overlay_stop else R.string.overlay_send,
                        ),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DockCircle(
    container: Color,
    contentColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    FilledIconButton(
        onClick = hapticTap(onClick),
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        ),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CameraButton(
    capturing: Boolean,
    enabled: Boolean,
    onCaptureScreen: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    IconButton(
        onClick = hapticTap(onCaptureScreen),
        enabled = enabled && !capturing,
        modifier = Modifier.size(48.dp),
    ) {
        if (capturing) {
            LoadingIndicator(
                modifier = Modifier.size(22.dp),
                color = scheme.primary,
            )
        } else {
            Icon(
                imageVector = FocusIcons.Camera,
                contentDescription = stringResource(R.string.overlay_screen),
                tint = if (enabled) {
                    scheme.onSurfaceVariant
                } else {
                    scheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun AttachmentSuggestionChip(
    selection: Boolean,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    SuggestionChip(
        onClick = onRemove,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(
                        if (selection) R.string.overlay_select else R.string.overlay_screen_attached,
                    ),
                )
                Icon(
                    imageVector = FocusIcons.Close,
                    contentDescription = stringResource(R.string.overlay_screen_remove),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(14.dp),
                )
            }
        },
        icon = {
            Icon(
                imageVector = if (selection) FocusIcons.SelectAll else FocusIcons.Photo,
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

@Composable
private fun ListeningBars(
    color: Color,
    level: () -> Float,
    compact: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "listening")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val smoothed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        snapshotFlow { level().coerceIn(0f, 1f) }.collect { next ->
            smoothed.floatValue = if (next > smoothed.floatValue) {
                smoothed.floatValue + (next - smoothed.floatValue) * 0.65f
            } else {
                smoothed.floatValue * 0.8f + next * 0.2f
            }
        }
    }
    Canvas(
        modifier = if (compact) {
            Modifier.size(width = 42.dp, height = 20.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .height(24.dp)
        },
    ) {
        val count = if (compact) 5 else 11
        val barWidth = 3.dp.toPx()
        val gap = 4.dp.toPx()
        val total = count * barWidth + (count - 1) * gap
        val startX = (size.width - total) / 2f
        val minHeight = 3.dp.toPx()
        val maxHeight = if (compact) 16.dp.toPx() else 20.dp.toPx()
        val energy = smoothed.floatValue
        val middle = (count - 1) / 2f
        repeat(count) { index ->
            val wave = sin(phase * 2f * PI.toFloat() - index * 0.6f)
            val swell = (wave + 1f) / 2f
            val focus = 1f - 0.4f * (abs(index - middle) / middle)
            val height = minHeight +
                (maxHeight - minHeight) * energy * (0.45f + 0.55f * swell) * focus
            val x = startX + index * (barWidth + gap)
            drawRoundRect(
                color = color.copy(alpha = 0.35f + 0.65f * energy),
                topLeft = Offset(x, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
