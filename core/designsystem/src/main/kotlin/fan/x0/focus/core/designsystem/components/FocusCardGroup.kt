package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val GroupCorner = 20.dp
private val GroupSpacing = 2.dp
private val InnerCorner = 4.dp

private data class GroupItem(
    val selected: Boolean = false,
    val onClick: (() -> Unit)?,
    val leadingContent: (@Composable () -> Unit)?,
    val headlineContent: @Composable () -> Unit,
    val supportingContent: (@Composable () -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
    val formContent: (@Composable ColumnScope.() -> Unit)? = null,
)

@DslMarker
private annotation class CardGroupDsl

@CardGroupDsl
interface FocusCardGroupScope {
    fun item(
        headlineContent: @Composable () -> Unit,
        onClick: (() -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        selected: Boolean = false,
    )

    fun formItem(
        headlineContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit,
    )
}

private class CardGroupScopeImpl : FocusCardGroupScope {
    val items = mutableListOf<GroupItem>()

    override fun item(
        headlineContent: @Composable () -> Unit,
        onClick: (() -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        selected: Boolean,
    ) {
        items += GroupItem(
            selected = selected,
            onClick = onClick,
            leadingContent = leadingContent,
            headlineContent = headlineContent,
            supportingContent = supportingContent,
            trailingContent = trailingContent,
        )
    }

    override fun formItem(
        headlineContent: (@Composable () -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        items += GroupItem(
            selected = false,
            onClick = null,
            leadingContent = leadingContent,
            headlineContent = headlineContent ?: {},
            supportingContent = supportingContent,
            trailingContent = null,
            formContent = content,
        )
    }
}

fun FocusCardGroupScope.switchItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    headlineContent: @Composable () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
) = item(
    headlineContent = headlineContent,
    onClick = { onCheckedChange(!checked) },
    supportingContent = supportingContent,
    leadingContent = leadingContent,
    trailingContent = {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    },
)

@Composable
fun FocusCardGroupIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val container by animateColorAsState(
        targetValue = containerColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "groupIconContainer",
    )
    val content by animateColorAsState(
        targetValue = contentColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "groupIconContent",
    )
    Box(
        modifier = modifier
            .size(40.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun FocusCardGroupValue(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
fun FocusCardGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: FocusCardGroupScope.() -> Unit,
) {
    val scope = CardGroupScopeImpl().apply(content)
    Column(modifier = modifier) {
        if (title != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                ProvideTextStyle(MaterialTheme.typography.titleSmallEmphasized) {
                    Box(modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(title)
                    }
                }
            }
        }
        val count = scope.items.size
        scope.items.forEachIndexed { index, item ->
            val form = item.formContent
            if (form != null) {
                GroupFormItem(item, form, index, count)
            } else {
                GroupListItem(item, index, count)
            }
            if (index != count - 1) Spacer(Modifier.height(GroupSpacing))
        }
    }
}

@Composable
private fun GroupFormItem(
    item: GroupItem,
    formContent: @Composable ColumnScope.() -> Unit,
    index: Int,
    count: Int,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = itemShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupHeader(item)
            formContent()
        }
    }
}

@Composable
private fun GroupHeader(item: GroupItem) {
    Row(
        verticalAlignment = if (item.supportingContent != null) Alignment.Top else Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item.leadingContent?.invoke()
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) {
                item.headlineContent()
            }
            item.supportingContent?.let {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        it()
                    }
                }
            }
        }
        item.trailingContent?.invoke()
    }
}

@Composable
private fun GroupListItem(item: GroupItem, index: Int, count: Int) {
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<androidx.compose.ui.graphics.Color>()
    val container by animateColorAsState(
        targetValue = if (item.selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = effects,
        label = "groupContainer",
    )
    val content by animateColorAsState(
        targetValue = if (item.selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = effects,
        label = "groupContent",
    )
    val supporting by animateColorAsState(
        targetValue = if (item.selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = effects,
        label = "groupSupporting",
    )
    val icons by animateColorAsState(
        targetValue = if (item.selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = effects,
        label = "groupIcons",
    )
    val isFirst = index == 0
    val isLast = index == count - 1
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val top by animateDpAsState(
        targetValue = if (pressed || isFirst || count == 1) GroupCorner else InnerCorner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "groupTop",
    )
    val bottom by animateDpAsState(
        targetValue = if (pressed || isLast || count == 1) GroupCorner else InnerCorner,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "groupBottom",
    )
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(top, top, bottom, bottom))
            .then(
                if (item.onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = item.onClick,
                    )
                } else {
                    Modifier
                },
            ),
        supportingContent = item.supportingContent,
        leadingContent = item.leadingContent,
        trailingContent = item.trailingContent,
        colors = ListItemDefaults.colors(
            containerColor = container,
            contentColor = content,
            leadingContentColor = icons,
            trailingContentColor = icons,
            overlineContentColor = supporting,
            supportingContentColor = supporting,
        ),
        content = { ProvideTextStyle(MaterialTheme.typography.bodyLarge) { item.headlineContent() } },
    )
}

private fun itemShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) GroupCorner else InnerCorner
    val bottom = if (index == count - 1) GroupCorner else InnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
