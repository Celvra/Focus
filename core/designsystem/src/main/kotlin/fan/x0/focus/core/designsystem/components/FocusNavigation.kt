package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.FocusMotionDefaults
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

data class FocusNavItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

// Expressive bar: pill-shaped floating container, animated indicator, emphasized selected label.
@Composable
fun FocusNavigationBar(
    items: List<FocusNavItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = FocusShapeTokens.pill,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NavigationBarCell(
                    item = item,
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item.key) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun FocusNavigationRail(
    items: List<FocusNavItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(12.dp),
        shape = FocusShapeTokens.sheet,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEach { item ->
                NavigationRailCell(
                    item = item,
                    selected = item.key == selectedKey,
                    onClick = { onSelect(item.key) },
                )
            }
        }
    }
}

@Composable
private fun NavigationBarCell(
    item: FocusNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFocusMotion.current
    val indicatorWidth = animateDpAsState(if (selected) 64.dp else 40.dp, motion.spatialDefault<androidx.compose.ui.unit.Dp>(), label = "navWidth")
    val indicatorColor = animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        motion.effectsDefault<androidx.compose.ui.graphics.Color>(),
        label = "navIndicator",
    )
    val iconTint = animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        motion.effectsDefault<androidx.compose.ui.graphics.Color>(),
        label = "navIcon",
    )
    val labelAlpha = animateFloatAsState(if (selected) 1f else 0.7f, motion.effectsDefault<Float>(), label = "navLabel")

    Column(
        modifier = modifier
            .clip(FocusShapeTokens.pill)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = hapticSelect(onClick),
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth.value)
                .height(32.dp)
                .clip(FocusShapeTokens.pill)
                .background(indicatorColor.value),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, contentDescription = item.label, tint = iconTint.value, modifier = Modifier.size(20.dp))
        }
        Text(
            text = item.label,
            style = if (selected) LocalFocusTypography.current.labelMedium else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = labelAlpha.value),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NavigationRailCell(
    item: FocusNavItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val motion = LocalFocusMotion.current
    val indicatorColor = animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        motion.effectsDefault<androidx.compose.ui.graphics.Color>(),
        label = "railIndicator",
    )
    val iconTint = animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        motion.effectsDefault<androidx.compose.ui.graphics.Color>(),
        label = "railIcon",
    )
    Row(
        modifier = Modifier
            .clip(FocusShapeTokens.pill)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = hapticSelect(onClick),
            )
            .background(indicatorColor.value)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .widthIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(item.icon, contentDescription = item.label, tint = iconTint.value, modifier = Modifier.size(22.dp))
        if (selected) {
            Text(
                text = item.label,
                style = LocalFocusTypography.current.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}

@Suppress("unused")
private val defaultMotion = FocusMotionDefaults
