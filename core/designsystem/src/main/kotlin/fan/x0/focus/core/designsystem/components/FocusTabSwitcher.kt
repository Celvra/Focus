package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

// M3 expressive connected button group used as a view switcher.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FocusTabSwitcher(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                ToggleButton(
                    checked = selected,
                    onCheckedChange = hapticToggle { if (!selected) onSelect(index) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.Tab },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        text = label,
                        style = if (selected) {
                            LocalFocusTypography.current.labelLarge
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun FocusTabContent(
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    val motion = LocalFocusMotion.current
    AnimatedContent(
        targetState = selectedIndex,
        modifier = modifier,
        transitionSpec = {
            val forward = targetState > initialState
            val enter = fadeIn(animationSpec = motion.effectsDefault()) +
                slideInHorizontally(
                    animationSpec = motion.spatialDefault(),
                    initialOffsetX = { width -> if (forward) width / 10 else -width / 10 },
                )
            val exit = fadeOut(animationSpec = motion.effectsFast()) +
                slideOutHorizontally(
                    animationSpec = motion.spatialDefault(),
                    targetOffsetX = { width -> if (forward) -width / 10 else width / 10 },
                )
            enter togetherWith exit
        },
        label = "focusTabContent",
    ) { index ->
        content(index)
    }
}
