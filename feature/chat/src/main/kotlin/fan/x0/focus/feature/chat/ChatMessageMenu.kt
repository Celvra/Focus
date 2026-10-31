package fan.x0.focus.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.stateLayer
import fan.x0.focus.core.designsystem.theme.FocusRadii
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

@Composable
internal fun MessageActionPopup(
    menu: ChatMenuUi,
    onAction: (ChatAction) -> Unit,
    onCopy: (String) -> Unit,
) {
    val density = LocalDensity.current
    val provider = remember(density) {
        MessageMenuPositionProvider(
            margin = with(density) { 12.dp.roundToPx() },
            gap = with(density) { 8.dp.roundToPx() },
        )
    }
    val motion = LocalFocusMotion.current
    val visible = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = { onAction(ChatAction.CloseMessageMenu) },
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(motion.effectsDefault()) +
                scaleIn(
                    animationSpec = motion.spatialFast(),
                    initialScale = 0.9f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActionMenu(menu = menu, onAction = onAction, onCopy = onCopy)
            }
        }
    }
}

@Composable
private fun ActionMenu(
    menu: ChatMenuUi,
    onAction: (ChatAction) -> Unit,
    onCopy: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(min = 252.dp, max = 300.dp),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            MenuAction(
                icon = FocusIcons.Reply,
                label = stringResource(R.string.chat_menu_reply),
                onClick = { onAction(ChatAction.ReplyTo(menu.key)) },
            )
            MenuAction(
                icon = FocusIcons.Copy,
                label = stringResource(R.string.chat_menu_copy),
                enabled = menu.text.isNotBlank(),
                onClick = { onCopy(menu.text) },
            )
            MenuAction(
                icon = FocusIcons.SelectAll,
                label = stringResource(R.string.chat_menu_select),
                enabled = menu.text.isNotBlank(),
                onClick = { onAction(ChatAction.SelectText(menu.key)) },
            )
            MenuAction(
                icon = FocusIcons.Edit,
                label = stringResource(R.string.chat_menu_edit),
                enabled = menu.canEdit,
                onClick = { onAction(ChatAction.StartEdit(menu.key)) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            MenuAction(
                icon = FocusIcons.Delete,
                label = stringResource(R.string.chat_menu_delete),
                enabled = menu.canDelete,
                destructive = true,
                onClick = { onAction(ChatAction.AskDelete(menu.key)) },
            )
        }
    }
}

@Composable
private fun MenuAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.large
    val content = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.38f)
        destructive -> scheme.error
        else -> scheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(shape)
            .stateLayer(interaction, shape, content)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = content,
        )
    }
}

@Composable
internal fun MessageReactionChip(
    reaction: String,
    count: Int,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        FilterChip(
            selected = true,
            onClick = {},
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = reaction,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            },
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$reaction, $count" },
            shape = RoundedCornerShape(FocusRadii.extraLarge),
        )
    }
}

private class MessageMenuPositionProvider(
    private val margin: Int,
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(margin, maxX)
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val above = anchorBounds.top - popupContentSize.height - gap
        val below = anchorBounds.bottom + gap
        val y = if (above >= margin) {
            above
        } else {
            below.coerceIn(margin, maxY)
        }
        return IntOffset(x, y)
    }
}
