package fan.x0.focus.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusChatBubble
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens

@Composable
internal fun ChatMessageBubble(
    entry: ChatEntry,
    fromUser: Boolean,
    state: ChatUiState,
    onAction: (ChatAction) -> Unit,
    onCopy: (String) -> Unit,
    streaming: Boolean = false,
) {
    val text = when (entry) {
        is ChatEntry.User -> entry.text
        is ChatEntry.Assistant -> entry.text
        else -> return
    }
    val quote = when (entry) {
        is ChatEntry.User -> entry.replyTo
        is ChatEntry.Assistant -> entry.replyTo
    }
    val reactions = when (entry) {
        is ChatEntry.User -> entry.reactions
        is ChatEntry.Assistant -> entry.reactions
    }
    val key = entry.key
    val selecting = state.selectionKey == key
    val canMenu = entry.messageId != null
    val header: (@Composable () -> Unit)? = quote?.let {
        {
            MessageQuote(
                quote = it,
                onClick = { onAction(ChatAction.JumpToMessage("m${it.id}")) },
            )
        }
    }
    FocusChatBubble(
        text = text,
        fromUser = fromUser,
        streaming = streaming,
        highlighted = state.highlightKey == key,
        selectable = selecting,
        reactions = reactions,
        onLongPress = if (canMenu) {
            { onAction(ChatAction.OpenMessageMenu(key)) }
        } else {
            null
        },
        header = header,
        menu = {
            val menu = state.menu
            if (menu?.key == key) {
                MessageActionPopup(menu = menu, onAction = onAction, onCopy = onCopy)
            }
        },
    )
}

@Composable
private fun MessageQuote(
    quote: QuotedMessageUi,
    onClick: () -> Unit,
) {
    val content = LocalContentColor.current
    val author = quote.author.ifBlank {
        if (quote.fromUser) {
            stringResource(R.string.chat_quote_you)
        } else {
            stringResource(R.string.chat_title)
        }
    }
    Surface(
        onClick = hapticTap(onClick),
        enabled = !quote.deleted,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = content.copy(alpha = 0.12f),
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(content.copy(alpha = 0.7f)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelMedium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (quote.deleted) {
                        stringResource(R.string.chat_quote_deleted)
                    } else {
                        quote.text
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun SelectionModeBar(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FocusShapeTokens.pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = FocusIcons.SelectAll,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.chat_selection_mode),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = hapticTap(onDone),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.chat_selection_done),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
