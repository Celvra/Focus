package fan.x0.focus.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import fan.x0.focus.data.assistant.WorkspaceKind

@Composable
fun FocusTrustCard(
    card: TrustCardUi,
    onDecide: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(motion.spatialDefault()),
        shape = FocusShapeTokens.card,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerHighest),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = scheme.tertiary.copy(alpha = 0.14f),
                    contentColor = scheme.tertiary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (card.kind == WorkspaceKind.Bridge) {
                                FocusIcons.Terminal
                            } else {
                                FocusIcons.Folder
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.chat_trust_title),
                        style = LocalFocusTypography.current.titleMedium,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = card.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                TrustStatusPill(card.phase)
            }
            Text(
                text = stringResource(R.string.chat_trust_body),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            if (card.files.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.chat_trust_files, card.files.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = card.phase == TrustPhase.Waiting,
                enter = fadeIn(motion.effectsDefault()),
                exit = fadeOut(motion.effectsFast()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FocusOutlinedButton(
                        text = stringResource(R.string.chat_trust_decline),
                        onClick = { onDecide(false) },
                        modifier = Modifier.weight(1f),
                    )
                    FocusPrimaryButton(
                        text = stringResource(R.string.chat_trust_accept),
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
private fun TrustStatusPill(phase: TrustPhase) {
    when (phase) {
        TrustPhase.Waiting -> FocusStatusPill(
            text = stringResource(R.string.chat_trust_waiting),
            tone = StatusTone.Warning,
        )
        TrustPhase.Trusted -> FocusStatusPill(
            text = stringResource(R.string.chat_trust_trusted),
            tone = StatusTone.Success,
        )
        TrustPhase.Declined -> FocusStatusPill(
            text = stringResource(R.string.chat_trust_declined),
            tone = StatusTone.Neutral,
        )
    }
}
