package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.FocusRadii
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

data class FocusMentionGroup(
    val id: String,
    val title: String? = null,
    val items: List<FocusMentionItem>,
)

data class FocusMentionItem(
    val id: String,
    val label: String,
    val description: String = "",
    val kind: String = "",
    val action: Boolean = false,
)

@Composable
fun FocusMentionPanel(
    groups: List<FocusMentionGroup>,
    selectedId: String?,
    onPick: (FocusMentionItem) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String? = null,
    leading: (@Composable (FocusMentionItem) -> Boolean)? = null,
) {
    val total = groups.sumOf { it.items.size }
    if (total == 0 && emptyText == null) return
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(selectedId) {
        if (selectedId != null) bringIntoView.bringIntoView()
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 300.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (total == 0) {
                Text(
                    text = emptyText.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            groups.forEach { group ->
                if (group.items.isEmpty()) return@forEach
                if (!group.title.isNullOrBlank()) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                group.items.forEach { item ->
                    val selected = item.id == selectedId
                    FocusMentionRow(
                        item = item,
                        selected = selected,
                        leading = leading,
                        onClick = { onPick(item) },
                        modifier = if (selected) {
                            Modifier.bringIntoViewRequester(bringIntoView)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusMentionRow(
    item: FocusMentionItem,
    selected: Boolean,
    leading: (@Composable (FocusMentionItem) -> Boolean)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalFocusMotion.current
    val container by animateColorAsState(
        targetValue = if (selected) scheme.secondaryContainer else Color.Transparent,
        animationSpec = motion.effectsFast(),
        label = "mentionRowContainer",
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = FocusShapeTokens.inner(FocusRadii.extraLarge, 12.dp),
        color = container,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
        onClick = hapticTap(onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (leading?.invoke(item) == true) {
                Unit
            } else if (item.action) {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = if (selected) scheme.secondary else scheme.surfaceContainerHigh,
                    contentColor = if (selected) scheme.onSecondary else scheme.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = FocusIcons.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.size(30.dp),
                    shape = CircleShape,
                    color = if (selected) scheme.secondary else scheme.surfaceContainerHigh,
                    contentColor = if (selected) scheme.onSecondary else scheme.onSurfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "@",
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (item.action) item.label else "@${item.label}",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) {
                            scheme.onSecondaryContainer
                        } else {
                            scheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
