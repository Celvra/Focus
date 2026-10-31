package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion

data class FocusDiff(
    val path: String,
    val additions: Int,
    val deletions: Int,
    val hunks: List<FocusDiffHunk> = emptyList(),
    val truncated: Boolean = false,
)

data class FocusDiffHunk(
    val lines: List<FocusDiffLine> = emptyList(),
)

data class FocusDiffLine(
    val kind: FocusDiffLineKind,
    val oldLine: Int? = null,
    val newLine: Int? = null,
    val text: String = "",
)

enum class FocusDiffLineKind { Context, Added, Removed }

@Composable
fun FocusDiffList(diffs: List<FocusDiff>, modifier: Modifier = Modifier) {
    if (diffs.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        diffs.forEachIndexed { index, diff ->
            key(index, diff.path) {
                FocusDiffCard(diff)
            }
        }
    }
}

@Composable
private fun FocusDiffCard(diff: FocusDiff) {
    val motion = LocalFocusMotion.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val addedBackground = if (dark) Color(0xFF173A26) else Color(0xFFE8F5E9)
    val removedBackground = if (dark) Color(0xFF3A1D1D) else Color(0xFFFFEBEE)
    val addedText = if (dark) Color(0xFF8FD9A0) else Color(0xFF1B5E20)
    val removedText = if (dark) Color(0xFFF0A0A0) else Color(0xFFB71C1C)
    var expanded by remember(diff.path) { mutableStateOf(true) }
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.spatialFast(),
        label = "diffChevron",
    )
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.small
    val codeStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    val maxLine = diff.hunks
        .flatMap { it.lines }
        .mapNotNull { it.oldLine ?: it.newLine }
        .maxOrNull() ?: 0
    val gutterWidth = (maxLine.toString().length.coerceAtLeast(2) * 8 + 10).dp

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                    ) { expanded = !expanded }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = FocusIcons.File,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = diff.path,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "+${diff.additions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = addedText,
                )
                Text(
                    text = "-${diff.deletions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = removedText,
                )
                Icon(
                    imageVector = FocusIcons.ArrowDown,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevron },
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.spatialDefault()) + fadeIn(motion.effectsDefault()),
                exit = shrinkVertically(motion.spatialDefault()) + fadeOut(motion.effectsFast()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainer)
                        .padding(vertical = 2.dp),
                ) {
                    diff.hunks.forEachIndexed { hunkIndex, hunk ->
                        if (hunkIndex > 0) {
                            DiffGap()
                        }
                        hunk.lines.forEach { line ->
                            DiffRow(
                                line = line,
                                gutterWidth = gutterWidth,
                                codeStyle = codeStyle,
                                addedBackground = addedBackground,
                                removedBackground = removedBackground,
                                addedText = addedText,
                                removedText = removedText,
                            )
                        }
                    }
                    if (diff.truncated) {
                        Text(
                            text = stringResource(R.string.common_diff_truncated),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffGap() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
    )
}

@Composable
private fun DiffRow(
    line: FocusDiffLine,
    gutterWidth: Dp,
    codeStyle: TextStyle,
    addedBackground: Color,
    removedBackground: Color,
    addedText: Color,
    removedText: Color,
) {
    val scheme = MaterialTheme.colorScheme
    val background = when (line.kind) {
        FocusDiffLineKind.Added -> addedBackground
        FocusDiffLineKind.Removed -> removedBackground
        FocusDiffLineKind.Context -> Color.Transparent
    }
    val foreground = when (line.kind) {
        FocusDiffLineKind.Added -> addedText
        FocusDiffLineKind.Removed -> removedText
        FocusDiffLineKind.Context -> scheme.onSurface.copy(alpha = 0.82f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = (line.oldLine ?: line.newLine)?.toString().orEmpty(),
            style = codeStyle,
            color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(gutterWidth)
                .padding(end = 8.dp),
        )
        Text(
            text = line.text.ifEmpty { " " },
            style = codeStyle,
            color = foreground,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
    }
}
