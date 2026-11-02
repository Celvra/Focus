package fan.x0.focus.feature.crew

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCardGroup
import fan.x0.focus.core.designsystem.components.FocusCardGroupIcon
import fan.x0.focus.core.designsystem.components.FocusCategoryIcons
import fan.x0.focus.core.designsystem.components.FocusChip
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusImageLightbox
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.data.assistant.AskKind
import fan.x0.focus.data.assistant.AskQuestion
import fan.x0.focus.data.assistant.AttachmentKind
import fan.x0.focus.data.assistant.ChatAttachment
import fan.x0.focus.data.assistant.ConversationTodo
import fan.x0.focus.data.assistant.CrewAskPhase
import fan.x0.focus.data.assistant.TodoStatus

@Composable
internal fun CrewAskCard(
    entry: CrewEntryUi.Ask,
    drafts: Map<String, CrewAskDraft>,
    onAction: (CrewAction) -> Unit,
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val waiting = entry.phase == CrewAskPhase.Waiting
    val container by animateColorAsState(
        if (waiting) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        motion.effectsDefault(),
        label = "crewAskContainer",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(motion.spatialDefault()),
        shape = FocusShapeTokens.card,
        color = container,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = scheme.tertiary.copy(alpha = 0.14f),
                    contentColor = scheme.tertiary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            FocusIcons.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = entry.intro.ifBlank { stringResource(R.string.crew_ask_title) },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                )
                when (entry.phase) {
                    CrewAskPhase.Waiting -> FocusStatusPill(
                        text = stringResource(R.string.crew_ask_waiting),
                        tone = StatusTone.Active,
                    )
                    CrewAskPhase.Answered -> FocusStatusPill(
                        text = stringResource(R.string.crew_ask_answered),
                        tone = StatusTone.Success,
                    )
                    CrewAskPhase.Skipped -> FocusStatusPill(
                        text = stringResource(R.string.crew_ask_skipped),
                        tone = StatusTone.Neutral,
                    )
                }
            }
            entry.questions.forEach { question ->
                CrewAskQuestion(
                    entry = entry,
                    question = question,
                    draft = drafts[question.id] ?: CrewAskDraft(),
                    waiting = waiting,
                    onAction = onAction,
                )
            }
            AnimatedVisibility(visible = waiting) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusOutlinedButton(
                        text = stringResource(R.string.crew_ask_skip),
                        onClick = { onAction(CrewAction.AskSkip(entry.id)) },
                        modifier = Modifier.weight(1f),
                    )
                    FocusPrimaryButton(
                        text = stringResource(R.string.crew_ask_submit),
                        onClick = { onAction(CrewAction.AskSubmit(entry.id)) },
                        enabled = entry.isReady(drafts),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CrewAskQuestion(
    entry: CrewEntryUi.Ask,
    question: AskQuestion,
    draft: CrewAskDraft,
    waiting: Boolean,
    onAction: (CrewAction) -> Unit,
) {
    val answer = entry.answers.firstOrNull { it.questionId == question.id }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (question.icon.isNotBlank()) {
                FocusCardGroupIcon(FocusCategoryIcons.resolve(question.icon))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = question.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (question.subtitle.isNotBlank()) {
                    Text(
                        text = question.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!waiting) {
            Text(
                text = answer?.let { answerLine(it.choices, it.text, it.number, it.confirmed) }
                    ?: stringResource(R.string.crew_ask_no_answer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            return@Column
        }
        when (question.kind) {
            AskKind.Single, AskKind.Multi -> {
                if (question.options.isNotEmpty()) {
                    CrewAskOptions(
                        entryId = entry.id,
                        question = question,
                        draft = draft,
                        onAction = onAction,
                    )
                }
                if (question.allowOther) {
                    OutlinedTextField(
                        value = draft.other,
                        onValueChange = {
                            onAction(CrewAction.AskEdit(entry.id, question.id, true, it))
                        },
                        placeholder = { Text(stringResource(R.string.crew_ask_other)) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            AskKind.Confirm -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusChip(
                    label = question.options.getOrNull(0)?.label
                        ?: stringResource(R.string.crew_ask_yes),
                    selected = draft.confirmed == true,
                    onClick = { onAction(CrewAction.AskConfirm(entry.id, question.id, true)) },
                )
                FocusChip(
                    label = question.options.getOrNull(1)?.label
                        ?: stringResource(R.string.crew_ask_no),
                    selected = draft.confirmed == false,
                    onClick = { onAction(CrewAction.AskConfirm(entry.id, question.id, false)) },
                )
            }
            AskKind.Text, AskKind.Number -> OutlinedTextField(
                value = draft.text,
                onValueChange = {
                    onAction(CrewAction.AskEdit(entry.id, question.id, false, it))
                },
                placeholder = {
                    Text(
                        question.placeholder.ifBlank {
                            stringResource(
                                if (question.kind == AskKind.Number) {
                                    R.string.crew_ask_number_hint
                                } else {
                                    R.string.crew_ask_text_hint
                                },
                            )
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (question.kind == AskKind.Number) {
                        KeyboardType.Decimal
                    } else {
                        KeyboardType.Text
                    },
                ),
                singleLine = question.kind == AskKind.Number,
                maxLines = if (question.kind == AskKind.Number) 1 else 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CrewAskOptions(
    entryId: String,
    question: AskQuestion,
    draft: CrewAskDraft,
    onAction: (CrewAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    FocusCardGroup {
        question.options.forEach { option ->
            val selected = option.label in draft.selections
            val multi = question.kind == AskKind.Multi
            item(
                headlineContent = { Text(option.label) },
                onClick = { onAction(CrewAction.AskSelect(entryId, question.id, option.label)) },
                supportingContent = option.detail.takeIf { it.isNotBlank() }?.let { detail ->
                    { Text(detail) }
                },
                leadingContent = option.icon.takeIf { it.isNotBlank() }?.let { icon ->
                    { FocusCardGroupIcon(FocusCategoryIcons.resolve(icon)) }
                },
                trailingContent = when {
                    multi -> {
                        {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = scheme.primary,
                                    uncheckedColor = scheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                    selected -> {
                        {
                            Icon(
                                FocusIcons.Check,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    else -> null
                },
                selected = selected,
                role = if (multi) Role.Checkbox else Role.RadioButton,
            )
        }
    }
}

internal fun CrewEntryUi.Ask.isReady(drafts: Map<String, CrewAskDraft>): Boolean =
    questions.all { question ->
        val draft = drafts[question.id] ?: CrewAskDraft()
        when (question.kind) {
            AskKind.Single -> draft.selections.size == 1 || draft.other.isNotBlank()
            AskKind.Multi -> draft.selections.isNotEmpty() || draft.other.isNotBlank()
            AskKind.Text -> draft.text.isNotBlank()
            AskKind.Number -> draft.text.trim().toDoubleOrNull() != null
            AskKind.Confirm -> draft.confirmed != null
        }
    }

@Composable
private fun answerLine(
    choices: List<String>,
    text: String,
    number: Double?,
    confirmed: Boolean?,
): String {
    if (confirmed != null) {
        return stringResource(
            if (confirmed) R.string.crew_ask_yes else R.string.crew_ask_no,
        )
    }
    if (number != null) {
        return if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
    }
    return (choices.filter { it.isNotBlank() } + text.trim().takeIf { it.isNotBlank() }.orEmpty())
        .joinToString(", ")
        .ifBlank { "-" }
}

@Composable
internal fun CrewTodoCard(todos: List<ConversationTodo>) {
    if (todos.isEmpty()) return
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val done = todos.count { it.status == TodoStatus.Completed }
    val active = todos.any { it.status == TodoStatus.Pending || it.status == TodoStatus.InProgress }
    val progress by animateFloatAsState(
        targetValue = done.toFloat() / todos.size,
        animationSpec = motion.spatialDefault(),
        label = "crewTodoProgress",
    )
    var expanded by rememberSaveable { mutableStateOf(active) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FocusShapeTokens.card,
        color = if (active) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = hapticTap { expanded = !expanded })
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    FocusIcons.Checklist,
                    contentDescription = null,
                    tint = if (active) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.crew_todo_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.crew_todo_progress, done, todos.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Icon(
                    FocusIcons.ArrowDown,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (expanded) 180f else 0f),
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .height(4.dp)
                    .clip(CircleShape),
                strokeCap = StrokeCap.Round,
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.spatialDefault()) + fadeIn(motion.effectsDefault()),
                exit = shrinkVertically(motion.spatialDefault()) + fadeOut(motion.effectsFast()),
            ) {
                Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                    todos.forEachIndexed { index, todo ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 28.dp, end = 4.dp),
                                color = scheme.outlineVariant,
                            )
                        }
                        CrewTodoRow(todo)
                    }
                }
            }
        }
    }
}

@Composable
private fun CrewTodoRow(todo: ConversationTodo) {
    val scheme = MaterialTheme.colorScheme
    val done = todo.status == TodoStatus.Completed
    val cancelled = todo.status == TodoStatus.Cancelled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (todo.status) {
                TodoStatus.Completed -> Icon(
                    FocusIcons.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                TodoStatus.InProgress -> FocusLoadingBlob(size = 16.dp)
                TodoStatus.Cancelled -> Icon(
                    FocusIcons.Close,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                TodoStatus.Pending -> Box(
                    modifier = Modifier
                        .size(12.dp)
                        .border(1.5.dp, scheme.outline, CircleShape),
                )
            }
        }
        Text(
            text = todo.content,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                cancelled -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
                done -> scheme.onSurfaceVariant
                else -> scheme.onSurface
            },
            textDecoration = if (done || cancelled) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        if (todo.status == TodoStatus.InProgress) {
            FocusStatusPill(
                text = stringResource(R.string.crew_todo_in_progress),
                tone = StatusTone.Active,
            )
        }
    }
}

@Composable
internal fun CrewAttachmentStrip(attachments: List<ChatAttachment>) {
    if (attachments.isEmpty()) return
    var preview by remember { mutableStateOf<Pair<ImageBitmap, String>?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        attachments.forEach { attachment ->
            when (attachment.kind) {
                AttachmentKind.Image -> CrewImageAttachment(
                    attachment = attachment,
                    onClick = {
                        attachment.decodeBitmap()?.let { bitmap ->
                            preview = bitmap to attachment.name
                        }
                    },
                )
                AttachmentKind.Text -> Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            FocusIcons.File,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = attachment.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    preview?.let { (bitmap, title) ->
        FocusImageLightbox(
            image = bitmap,
            title = title,
            onDismiss = { preview = null },
        )
    }
}

@Composable
private fun CrewImageAttachment(attachment: ChatAttachment, onClick: () -> Unit) {
    val bitmap = remember(attachment.id) { attachment.decodeBitmap() }
    Surface(
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = hapticTap(onClick),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    FocusIcons.Photo,
                    contentDescription = attachment.name,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun ChatAttachment.decodeBitmap(): ImageBitmap? = runCatching {
    val bytes = android.util.Base64.decode(base64.orEmpty(), android.util.Base64.NO_WRAP)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()
