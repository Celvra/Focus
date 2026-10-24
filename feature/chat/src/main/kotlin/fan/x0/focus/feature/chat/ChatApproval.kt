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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusStepStatus
import fan.x0.focus.core.designsystem.components.FocusTimelineStep
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

@Composable
fun FocusApprovalCard(
    card: ApprovalUi,
    onDecide: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when (card.phase) {
            ApprovalPhase.Waiting -> scheme.surfaceContainerHighest
            ApprovalPhase.Declined -> scheme.surfaceContainerHigh
            else -> scheme.surfaceContainerLow
        },
        motion.effectsDefault(),
        label = "approvalContainer",
    )
    val badgeTint = when (card.phase) {
        ApprovalPhase.Waiting -> scheme.tertiary
        ApprovalPhase.Declined -> scheme.error
        else -> scheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(motion.spatialDefault()),
        shape = FocusShapeTokens.card,
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(badgeTint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (card.phase == ApprovalPhase.Waiting) FocusIcons.Warning else FocusIcons.Bolt,
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.title,
                        style = LocalFocusTypography.current.titleMedium,
                        color = scheme.onSurface,
                    )
                    if (card.summary.isNotBlank()) {
                        Text(
                            text = card.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                ApprovalStatusPill(card.phase)
            }

            if (card.steps.isNotEmpty()) {
                Column {
                    card.steps.forEachIndexed { index, step ->
                        key("${step.label}-$index") {
                            FocusTimelineStep(
                                label = step.label,
                                status = step.status.toStepStatus(),
                                summary = step.summary,
                                isFirst = index == 0,
                                isLast = index == card.steps.lastIndex,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = card.phase == ApprovalPhase.Waiting,
                enter = fadeIn(motion.effectsDefault()),
                exit = fadeOut(motion.effectsFast()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusOutlinedButton(
                        text = stringResource(R.string.chat_approval_decline),
                        onClick = { onDecide(false) },
                        modifier = Modifier.weight(1f),
                    )
                    FocusPrimaryButton(
                        text = stringResource(R.string.chat_approval_approve),
                        onClick = { onDecide(true) },
                        icon = FocusIcons.Check,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalStatusPill(phase: ApprovalPhase) {
    when (phase) {
        ApprovalPhase.Waiting -> FocusStatusPill(
            text = stringResource(R.string.chat_approval_waiting),
            tone = StatusTone.Warning,
        )
        ApprovalPhase.Running -> FocusStatusPill(
            text = stringResource(R.string.chat_approval_running),
            tone = StatusTone.Active,
        )
        ApprovalPhase.Finished -> FocusStatusPill(
            text = stringResource(R.string.chat_approval_done),
            tone = StatusTone.Success,
        )
        ApprovalPhase.Declined -> FocusStatusPill(
            text = stringResource(R.string.chat_approval_declined),
            tone = StatusTone.Neutral,
        )
    }
}

internal fun TraceStatus.toStepStatus(): FocusStepStatus = when (this) {
    TraceStatus.Pending -> FocusStepStatus.Pending
    TraceStatus.Running -> FocusStepStatus.Running
    TraceStatus.Done -> FocusStepStatus.Done
    TraceStatus.Failed -> FocusStepStatus.Failed
}
