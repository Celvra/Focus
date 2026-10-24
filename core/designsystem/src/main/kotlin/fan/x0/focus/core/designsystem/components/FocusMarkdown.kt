package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.LocalFocusMotion
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val index: Int?) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
}

@Composable
fun FocusMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    streaming: Boolean = false,
    bodyStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    preserveLineBreaks: Boolean = false,
) {
    val blocks = parseBlocks(text, preserveLineBreaks)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            StreamBlock(animate = streaming) {
                BlockContent(block = block, color = color, bodyStyle = bodyStyle)
            }
        }
    }
}

// New blocks fade in as they arrive; existing blocks keep their identity so text growth stays calm.
@Composable
private fun StreamBlock(animate: Boolean, content: @Composable () -> Unit) {
    if (!animate) {
        content()
        return
    }
    val motion = LocalFocusMotion.current
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(motion.effectsDefault()) +
            slideInVertically(motion.spatialFast()) { height -> height / 4 },
        content = { content() },
    )
}

@Composable
private fun BlockContent(
    block: MdBlock,
    color: androidx.compose.ui.graphics.Color,
    bodyStyle: TextStyle,
) {
    when (block) {
        is MdBlock.Heading -> Text(
            text = inline(block.text, color),
            style = when (block.level) {
                1 -> LocalFocusTypography.current.headlineSmall
                2 -> LocalFocusTypography.current.titleLarge
                else -> LocalFocusTypography.current.titleMedium
            },
            color = color,
        )
        is MdBlock.Paragraph -> Text(
            text = inline(block.text, color),
            style = bodyStyle,
            color = color,
        )
        is MdBlock.Bullet -> Row {
            Text(
                text = block.index?.let { "$it." } ?: "•",
                style = bodyStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(22.dp),
            )
            Text(
                text = inline(block.text, color),
                style = bodyStyle,
                color = color,
            )
        }
        is MdBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(
                modifier = Modifier
                    .width(3.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
            Text(
                text = inline(block.text, color),
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        is MdBlock.Code -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
                .padding(12.dp),
        )
    }
}

private fun parseBlocks(text: String, preserveLineBreaks: Boolean): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    var inCode = false
    val code = StringBuilder()
    text.lines().forEach { raw ->
        val line = raw.trimEnd()
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks += MdBlock.Code(code.toString().trimEnd())
                code.clear()
            }
            inCode = !inCode
            return@forEach
        }
        if (inCode) {
            code.appendLine(line)
            return@forEach
        }
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> Unit
            trimmed.startsWith("### ") -> blocks += MdBlock.Heading(3, trimmed.removePrefix("### "))
            trimmed.startsWith("## ") -> blocks += MdBlock.Heading(2, trimmed.removePrefix("## "))
            trimmed.startsWith("# ") -> blocks += MdBlock.Heading(1, trimmed.removePrefix("# "))
            trimmed.startsWith("> ") -> blocks += MdBlock.Quote(trimmed.removePrefix("> "))
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> blocks += MdBlock.Bullet(trimmed.drop(2), null)
            numberedPattern.matches(trimmed) -> {
                val dot = trimmed.indexOf('.')
                blocks += MdBlock.Bullet(trimmed.substring(dot + 1).trim(), trimmed.substring(0, dot).toIntOrNull())
            }
            else -> {
                val last = blocks.lastOrNull()
                if (last is MdBlock.Paragraph) {
                    val separator = if (preserveLineBreaks) "\n" else " "
                    blocks[blocks.lastIndex] = MdBlock.Paragraph(last.text + separator + trimmed)
                } else {
                    blocks += MdBlock.Paragraph(trimmed)
                }
            }
        }
    }
    if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(code.toString().trimEnd())
    return blocks
}

private val numberedPattern = Regex("""^\d{1,2}\.\s+.*""")

private fun inline(text: String, color: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = color.copy(alpha = 0.08f),
                        ),
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}
