package fan.x0.focus.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusIcons
import fan.x0.focus.core.designsystem.components.FocusLoadingBlob
import fan.x0.focus.core.designsystem.components.FocusStatusPill
import fan.x0.focus.core.designsystem.components.StatusTone
import fan.x0.focus.core.designsystem.components.stateLayer
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.data.assistant.ConversationTodo
import fan.x0.focus.data.assistant.TodoStatus

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatTodoCard(
    conversationId: String,
    todos: List<ConversationTodo>,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val done = todos.count { it.status == TodoStatus.Completed }
    val active = todos.any {
        it.status == TodoStatus.Pending || it.status == TodoStatus.InProgress
    }
    val progress by animateFloatAsState(
        targetValue = if (todos.isEmpty()) 0f else done.toFloat() / todos.size,
        animationSpec = motion.spatialDefault(),
        label = "todoProgress",
    )
    var expanded by rememberSaveable(conversationId) { mutableStateOf(active) }
    var wasActive by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (wasActive != active) {
            expanded = active
            wasActive = active
        }
    }
    val container by animateColorAsState(
        targetValue = if (active) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        animationSpec = motion.effectsDefault(),
        label = "todoContainer",
    )
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialFast(),
        label = "todoChevron",
    )
    val interaction = remember { MutableInteractionSource() }
    val rowShape = MaterialTheme.shapes.small
    val stateLabel = stringResource(
        if (expanded) R.string.chat_todo_expanded else R.string.chat_todo_collapsed,
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FocusShapeTokens.card,
        color = container,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .stateLayer(interaction, rowShape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                    ) { expanded = !expanded }
                    .semantics(mergeDescendants = true) { stateDescription = stateLabel }
                    .heightIn(min = 44.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = FocusIcons.Checklist,
                    contentDescription = null,
                    tint = if (active) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.chat_todo_title),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.chat_todo_progress, done, todos.size),
                    style = MaterialTheme.typography.labelMedium,
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
                        TodoRow(todo = todo)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TodoRow(todo: ConversationTodo) {
    val scheme = MaterialTheme.colorScheme
    val done = todo.status == TodoStatus.Completed
    val cancelled = todo.status == TodoStatus.Cancelled
    val contentColor = when {
        cancelled -> scheme.onSurfaceVariant.copy(alpha = 0.6f)
        done -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
    val statusLabel = stringResource(
        when (todo.status) {
            TodoStatus.Pending -> R.string.chat_todo_pending
            TodoStatus.InProgress -> R.string.chat_todo_in_progress
            TodoStatus.Completed -> R.string.chat_todo_completed
            TodoStatus.Cancelled -> R.string.chat_todo_cancelled
        },
    )
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
                .size(18.dp)
                .clearAndSetSemantics { contentDescription = statusLabel },
            contentAlignment = Alignment.Center,
        ) {
            when (todo.status) {
                TodoStatus.Completed -> Icon(
                    imageVector = FocusIcons.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                TodoStatus.InProgress -> CompositionLocalProvider(
                    LocalContentColor provides scheme.primary,
                ) {
                    FocusLoadingBlob(size = 16.dp)
                }
                TodoStatus.Cancelled -> Icon(
                    imageVector = FocusIcons.Close,
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
            color = contentColor,
            textDecoration = if (done || cancelled) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        if (todo.status == TodoStatus.InProgress) {
            FocusStatusPill(text = statusLabel, tone = StatusTone.Active)
        }
    }
}
