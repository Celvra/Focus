package fan.x0.focus.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

@Composable
internal fun SlashCommandPanel(
    slash: SlashUi,
    onAction: (ChatAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val specs = remember(slash.commandIds) {
        slash.commandIds.mapNotNull { id ->
            SlashCommands.all.firstOrNull { it.id == id }
        }
    }
    if (specs.isEmpty()) return
    val motion = LocalFocusMotion.current
    val selectedIndex = slash.selected.coerceIn(0, specs.lastIndex)
    val selected = specs[selectedIndex]
    val visibleIds = slash.commandIds.toSet()
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected.id) {
        bringIntoView.bringIntoView()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column {
            Column(
                modifier = Modifier
                    .heightIn(max = 252.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                SlashCommands.all.forEach { spec ->
                    AnimatedVisibility(
                        visible = spec.id in visibleIds,
                        enter = expandVertically(motion.spatialFast()) +
                            fadeIn(motion.effectsFast()),
                        exit = shrinkVertically(motion.spatialFast()) +
                            fadeOut(motion.effectsFast()),
                    ) {
                        SlashCommandRow(
                            spec = spec,
                            selected = spec.id == selected.id,
                            onClick = { onAction(ChatAction.SlashPick(spec.id)) },
                            modifier = if (spec.id == selected.id) {
                                Modifier.bringIntoViewRequester(bringIntoView)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (fadeIn(motion.effectsFast()) + slideInVertically(motion.spatialFast()) { it / 2 }) togetherWith
                        (fadeOut(motion.effectsFast()) + slideOutVertically(motion.spatialFast()) { -it / 2 })
                },
                label = "slashHint",
            ) { spec ->
                Text(
                    text = stringResource(spec.hintRes),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SlashCommandRow(
    spec: SlashCommandSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalFocusMotion.current
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else Color.Transparent,
        animationSpec = motion.effectsFast(),
        label = "slashRowContainer",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = spec.id.icon(),
                contentDescription = null,
                tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spec.command,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = stringResource(spec.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        scheme.onSecondaryContainer
                    } else {
                        scheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(
                    imageVector = FocusIcons.Check,
                    contentDescription = null,
                    tint = scheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun SlashCommandId.icon(): ImageVector = when (this) {
    SlashCommandId.Undo -> FocusIcons.History
    SlashCommandId.Redo -> FocusIcons.Refresh
    SlashCommandId.Btw -> FocusIcons.Chat
    SlashCommandId.New -> FocusIcons.Add
    SlashCommandId.Resume -> FocusIcons.History
    SlashCommandId.Sessions -> FocusIcons.Chat
    SlashCommandId.Models -> FocusIcons.Assistant
    SlashCommandId.Effort -> FocusIcons.Idea
    SlashCommandId.Settings -> FocusIcons.Settings
    SlashCommandId.Help -> FocusIcons.Info
}
