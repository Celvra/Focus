package fan.x0.focus.core.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal data class MarkdownInline(
    val text: AnnotatedString,
    val codeSpans: List<IntRange> = emptyList(),
)

@Composable
internal fun MarkdownInlineText(
    content: MarkdownInline,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    var codeRects by remember { mutableStateOf<List<Rect>>(emptyList()) }
    val background = color.copy(alpha = InlineCodeBackgroundAlpha)
    Text(
        text = content.text,
        style = style,
        color = color,
        textAlign = textAlign,
        modifier = modifier.drawBehind {
            if (codeRects.isEmpty()) return@drawBehind
            val corner = CornerRadius(InlineCodeCornerRadius.toPx())
            val horizontalPadding = InlineCodeHorizontalPadding.toPx()
            codeRects.forEach { rect ->
                drawRoundRect(
                    color = background,
                    topLeft = Offset(rect.left - horizontalPadding, rect.top),
                    size = Size(rect.width + horizontalPadding * 2, rect.height),
                    cornerRadius = corner,
                )
            }
        },
        onTextLayout = { layout ->
            codeRects = layout.codeRects(content.codeSpans)
        },
    )
}

private fun TextLayoutResult.codeRects(spans: List<IntRange>): List<Rect> {
    if (spans.isEmpty()) return emptyList()
    val textLength = layoutInput.text.length
    val rects = mutableListOf<Rect>()
    spans.forEach { span ->
        val start = span.first.coerceIn(0, textLength)
        val end = (span.last + 1).coerceIn(start, textLength)
        if (start >= end) return@forEach
        val lastLine = getLineForOffset(end - 1)
        for (line in getLineForOffset(start)..lastLine) {
            val lineStart = getLineStart(line)
            val lineEnd = getLineEnd(line, visibleEnd = true)
            val from = maxOf(start, lineStart)
            val to = minOf(end, lineEnd)
            if (from >= to) continue
            val left = if (from == lineStart) {
                getLineLeft(line)
            } else {
                getHorizontalPosition(from, usePrimaryDirection = true)
            }
            val right = getHorizontalPosition(to, usePrimaryDirection = true)
            rects += Rect(
                left = minOf(left, right),
                top = getLineTop(line),
                right = maxOf(left, right),
                bottom = getLineBottom(line),
            )
        }
    }
    return rects
}

private val InlineCodeCornerRadius = 6.dp
private val InlineCodeHorizontalPadding = 4.dp
private const val InlineCodeBackgroundAlpha = 0.08f
