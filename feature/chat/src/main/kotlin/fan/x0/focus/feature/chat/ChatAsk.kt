package fan.x0.focus.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusCategoryIcons
import fan.x0.focus.core.designsystem.components.FocusIconBadge
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.data.assistant.AskKind
import fan.x0.focus.data.assistant.trimNumber

@Composable
fun FocusAskCard(
    card: AskCardUi,
    drafts: Map<String, AskDraft>,
    onAction: (ChatAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val waiting = card.phase == AskPhase.Waiting
    val container by animateColorAsState(
        if (waiting) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        motion.effectsDefault(),
        label = "askContainer",
    )
    val badgeTint = if (waiting) scheme.tertiary else scheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(motion.spatialDefault()),
        shape = FocusShapeTokens.card,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(badgeTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = FocusIcons.Checklist,
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = card.intro.ifBlank { stringResource(R.string.chat_ask_title) },
                    style = LocalFocusTypography.current.titleMedium,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                AskStatusPill(card.phase)
            }

            card.questions.forEach { question ->
                AskQuestionBlock(
                    askId = card.id,
                    question = question,
                    draft = drafts[question.id] ?: AskDraft(),
                    answer = card.answers.firstOrNull { it.questionId == question.id },
                    waiting = waiting,
                    onAction = onAction,
                )
            }

            AnimatedVisibility(
                visible = waiting,
                enter = fadeIn(motion.effectsDefault()),
                exit = fadeOut(motion.effectsFast()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusOutlinedButton(
                        text = stringResource(R.string.chat_ask_skip),
                        onClick = { onAction(ChatAction.AskSkip(card.id)) },
                        modifier = Modifier.weight(1f),
                    )
                    FocusPrimaryButton(
                        text = stringResource(R.string.chat_ask_submit),
                        onClick = { onAction(ChatAction.AskSubmit(card.id)) },
                        icon = FocusIcons.Send,
                        enabled = card.isReady(drafts),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AskStatusPill(phase: AskPhase) {
    when (phase) {
        AskPhase.Waiting -> FocusStatusPill(
            text = stringResource(R.string.chat_ask_waiting),
            tone = StatusTone.Active,
        )
        AskPhase.Answered -> FocusStatusPill(
            text = stringResource(R.string.chat_ask_answered),
            tone = StatusTone.Success,
        )
        AskPhase.Skipped -> FocusStatusPill(
            text = stringResource(R.string.chat_ask_skipped),
            tone = StatusTone.Neutral,
        )
    }
}

@Composable
private fun AskQuestionBlock(
    askId: String,
    question: AskQuestionUi,
    draft: AskDraft,
    answer: AskAnswerUi?,
    waiting: Boolean,
    onAction: (ChatAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (question.icon.isNotBlank()) {
                FocusIconBadge(
                    icon = FocusCategoryIcons.resolve(question.icon),
                    size = 32.dp,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = question.title,
                    style = LocalFocusTypography.current.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
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
        if (waiting) {
            AskQuestionInput(
                askId = askId,
                question = question,
                draft = draft,
                onAction = onAction,
            )
        } else {
            AskAnswerLine(question = question, answer = answer)
        }
    }
}

@Composable
private fun AskAnswerLine(question: AskQuestionUi, answer: AskAnswerUi?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = FocusIcons.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = answer.label(question.kind),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun AskAnswerUi?.label(kind: AskKind): String {
    if (this == null) return stringResource(R.string.chat_ask_no_answer)
    if (kind == AskKind.Confirm) {
        return when (confirmed) {
            true -> stringResource(R.string.chat_ask_yes)
            false -> stringResource(R.string.chat_ask_no)
            null -> stringResource(R.string.chat_ask_no_answer)
        }
    }
    if (kind == AskKind.Number) {
        number?.let { return trimNumber(it) }
    }
    val parts = choices.filter { it.isNotBlank() } +
        text.trim().takeIf { it.isNotBlank() }.orEmpty()
    return parts.joinToString(", ").ifBlank { stringResource(R.string.chat_ask_no_answer) }
}
