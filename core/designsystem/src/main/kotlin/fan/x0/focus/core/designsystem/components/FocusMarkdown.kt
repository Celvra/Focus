package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.R
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(
        val text: String,
        val depth: Int,
        val marker: String,
        val checked: Boolean?,
    ) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String?, val text: String) : MdBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<TextAlign>,
        val rows: List<List<String>>,
    ) : MdBlock
    data object Divider : MdBlock
}

@Composable
fun FocusMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    streaming: Boolean = false,
    bodyStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    preserveLineBreaks: Boolean = false,
) {
    val blocks = remember(text, preserveLineBreaks) { parseBlocks(text, preserveLineBreaks) }
    val linkColor = MaterialTheme.colorScheme.primary
    val fade = rememberStreamFade(text, streaming)
    val haptics = LocalFocusHaptics.current
    LaunchedEffect(text, streaming) {
        if (streaming) haptics.streamTick()
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            BlockContent(
                block = block,
                color = color,
                linkColor = linkColor,
                bodyStyle = bodyStyle,
                fade = if (index == blocks.lastIndex) fade else null,
            )
        }
    }
}

private const val FADE_CHARS = 512
private const val FADE_NANOS = 260_000_000L

private class StreamFadeState {
    private var length = 0
    private var lastArrival = 0L
    private val arrivals = ArrayDeque<Long>()
    val now = mutableLongStateOf(System.nanoTime())

    fun track(text: String) {
        if (text.length == length) return
        val at = System.nanoTime()
        val added = text.length - length
        if (added < 0) {
            arrivals.clear()
            repeat(minOf(text.length, FADE_CHARS)) { arrivals.addLast(at) }
        } else {
            repeat(added) { arrivals.addLast(at) }
            while (arrivals.size > FADE_CHARS) arrivals.removeFirst()
        }
        length = text.length
        lastArrival = if (arrivals.isEmpty()) 0L else at
    }

    fun alpha(distanceFromEnd: Int, at: Long): Float {
        val arrived = arrivals.elementAtOrNull(arrivals.lastIndex - distanceFromEnd) ?: return 1f
        return ((at - arrived).toFloat() / FADE_NANOS).coerceIn(0f, 1f)
    }

    fun fading(at: Long): Boolean = lastArrival > 0 && at - lastArrival < FADE_NANOS

    fun apply(text: AnnotatedString, color: Color, at: Long): AnnotatedString {
        if (text.isEmpty() || alpha(0, at) >= 1f) return text
        val limit = minOf(text.length, FADE_CHARS)
        var count = 0
        while (count < limit && alpha(count, at) < 1f) count++
        if (count == 0) return text
        return buildAnnotatedString {
            append(text)
            for (distance in 0 until count) {
                val value = alpha(distance, at)
                val start = text.length - 1 - distance
                val base = text.colorAt(start, color)
                addStyle(
                    SpanStyle(color = base.copy(alpha = base.alpha * value)),
                    start,
                    start + 1,
                )
            }
        }
    }
}

private fun AnnotatedString.colorAt(index: Int, fallback: Color): Color {
    for (span in spanStyles.asReversed()) {
        if (index >= span.start && index < span.end && span.item.color != Color.Unspecified) {
            return span.item.color
        }
    }
    return fallback
}

@Composable
private fun rememberStreamFade(text: String, streaming: Boolean): StreamFadeState? {
    val fade = remember { StreamFadeState() }
    if (!streaming && !fade.fading(System.nanoTime())) return null
    remember(text) { fade.track(text) }
    LaunchedEffect(streaming, text) {
        var frame = 0
        while (true) {
            withFrameNanos { }
            val at = System.nanoTime()
            if (frame == 0 || frame % 2 == 0) fade.now.longValue = at
            frame++
            if (!fade.fading(at)) break
        }
    }
    return fade
}

@Composable
private fun fadedInline(
    text: String,
    color: Color,
    linkColor: Color,
    fade: StreamFadeState?,
): MarkdownInline {
    val base = remember(text, color, linkColor) { inline(text, color, linkColor) }
    if (fade == null) return base
    return base.copy(text = fade.apply(base.text, color, fade.now.longValue))
}

@Composable
private fun BlockContent(
    block: MdBlock,
    color: Color,
    linkColor: Color,
    bodyStyle: TextStyle,
    fade: StreamFadeState?,
) {
    when (block) {
        is MdBlock.Heading -> MarkdownInlineText(
            content = fadedInline(block.text, color, linkColor, fade),
            style = when (block.level) {
                1 -> LocalFocusTypography.current.headlineSmall
                2 -> LocalFocusTypography.current.titleLarge
                3 -> LocalFocusTypography.current.titleMedium
                4 -> LocalFocusTypography.current.titleSmall
                5 -> LocalFocusTypography.current.labelLarge
                else -> LocalFocusTypography.current.labelMedium
            },
            color = color,
        )
        is MdBlock.Paragraph -> MarkdownInlineText(
            content = fadedInline(block.text, color, linkColor, fade),
            style = bodyStyle,
            color = color,
        )
        is MdBlock.ListItem -> ListRow(
            item = block,
            color = color,
            linkColor = linkColor,
            bodyStyle = bodyStyle,
            fade = fade,
        )
        is MdBlock.Quote -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
            )
            val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant
            MarkdownInlineText(
                content = fadedInline(block.text, quoteColor, linkColor, fade),
                style = bodyStyle,
                color = quoteColor,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        is MdBlock.Code -> CodeBlock(block = block, fade = fade)
        is MdBlock.Table -> TableBlock(block = block, color = color, linkColor = linkColor, fade = fade)
        MdBlock.Divider -> HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ListRow(
    item: MdBlock.ListItem,
    color: Color,
    linkColor: Color,
    bodyStyle: TextStyle,
    fade: StreamFadeState?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (item.depth * 16).dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.width(22.dp).padding(top = 3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (item.checked != null) {
                val checked = item.checked
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (checked) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (checked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) {
                        Icon(
                            imageVector = FocusIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            } else {
                Text(
                    text = item.marker,
                    style = bodyStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        MarkdownInlineText(
            content = fadedInline(item.text, color, linkColor, fade),
            style = bodyStyle,
            color = color,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code, fade: StreamFadeState?) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var copied by remember(block.text) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = block.language ?: "code",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = hapticTap {
                        clipboard.setText(AnnotatedString(block.text))
                        copied = true
                        scope.launch {
                            delay(1400)
                            copied = false
                        }
                    },
                    shape = CircleShape,
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (copied) FocusIcons.Check else FocusIcons.Copy,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = stringResource(
                                if (copied) R.string.common_copied else R.string.common_copy,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            val codeColor = MaterialTheme.colorScheme.onSurface
            val code = remember(block.text) { AnnotatedString(block.text) }
            Text(
                text = if (fade == null) code else fade.apply(code, codeColor, fade.now.longValue),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = codeColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun TableBlock(
    block: MdBlock.Table,
    color: Color,
    linkColor: Color,
    fade: StreamFadeState?,
) {
    val widths = remember(block.headers, block.rows) {
        block.headers.indices.map { index ->
            val longest = (listOf(block.headers[index]) + block.rows.map { it.getOrElse(index) { "" } })
                .maxOfOrNull { it.length } ?: 8
            (longest.coerceIn(8, 28) * 7).dp
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                block.headers.forEachIndexed { index, header ->
                    TableCell(
                        text = header,
                        width = widths.getOrElse(index) { 72.dp },
                        align = block.alignments.getOrElse(index) { TextAlign.Start },
                        header = true,
                        color = color,
                        linkColor = linkColor,
                        fade = fade,
                    )
                }
            }
            block.rows.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
                Row(
                    modifier = Modifier.background(
                        if (rowIndex % 2 == 1) {
                            MaterialTheme.colorScheme.surfaceContainer
                        } else {
                            Color.Transparent
                        },
                    ),
                ) {
                    block.headers.indices.forEach { index ->
                        TableCell(
                            text = row.getOrElse(index) { "" },
                            width = widths.getOrElse(index) { 72.dp },
                            align = block.alignments.getOrElse(index) { TextAlign.Start },
                            header = false,
                            color = color,
                            linkColor = linkColor,
                            fade = fade,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    align: TextAlign,
    header: Boolean,
    color: Color,
    linkColor: Color,
    fade: StreamFadeState?,
) {
    MarkdownInlineText(
        content = fadedInline(text, color, linkColor, fade),
        style = if (header) {
            MaterialTheme.typography.labelMedium
        } else {
            MaterialTheme.typography.bodySmall
        },
        color = color,
        textAlign = align,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

private fun parseBlocks(text: String, preserveLineBreaks: Boolean): List<MdBlock> {
    if (text.isBlank()) return emptyList()
    val lines = text.lines()
    val blocks = mutableListOf<MdBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> index++
            trimmed.startsWith("```") -> {
                val language = trimmed.removePrefix("```").trim().ifBlank { null }
                val code = StringBuilder()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code.appendLine(lines[index].trimEnd())
                    index++
                }
                if (index < lines.size) index++
                blocks += MdBlock.Code(language, code.toString().trimEnd())
            }
            dividerRegex.matches(trimmed) -> {
                blocks += MdBlock.Divider
                index++
            }
            headingRegex.matches(trimmed) -> {
                val match = headingRegex.matchEntire(trimmed)!!
                val level = match.groupValues[1].length.coerceIn(1, 6)
                val content = match.groupValues[2].trim()
                if (content.isNotEmpty()) blocks += MdBlock.Heading(level, content)
                index++
            }
            isTableStart(lines, index) -> {
                val table = parseTable(lines, index)
                blocks += table.first
                index = table.second
            }
            trimmed.startsWith(">") -> {
                val quote = StringBuilder(trimmed.removePrefix(">").trimStart())
                index++
                while (index < lines.size && lines[index].trim().startsWith(">")) {
                    quote.append(if (preserveLineBreaks) "\n" else " ")
                    quote.append(lines[index].trim().removePrefix(">").trimStart())
                    index++
                }
                blocks += MdBlock.Quote(quote.toString())
            }
            listItemRegex.matches(line) -> {
                val match = listItemRegex.matchEntire(line)!!
                val indent = match.groupValues[1].replace("\t", "  ").length
                val rawMarker = match.groupValues[2]
                var content = match.groupValues[3]
                val checked = when {
                    content.startsWith("[ ] ") -> false
                    content.startsWith("[x] ") || content.startsWith("[X] ") -> true
                    else -> null
                }
                if (checked != null) content = content.drop(4)
                val ordered = rawMarker.firstOrNull()?.isDigit() == true
                blocks += MdBlock.ListItem(
                    text = content,
                    depth = (indent / 2).coerceIn(0, 4),
                    marker = if (ordered) rawMarker.trimEnd('.', ')') + "." else "•",
                    checked = checked,
                )
                index++
            }
            else -> {
                val paragraph = StringBuilder(trimmed)
                index++
                while (index < lines.size && !isBlockStart(lines, index)) {
                    val next = lines[index].trim()
                    if (next.isNotEmpty()) {
                        if (paragraph.isNotEmpty()) {
                            paragraph.append(if (preserveLineBreaks) "\n" else " ")
                        }
                        paragraph.append(next)
                    }
                    index++
                }
                blocks += MdBlock.Paragraph(paragraph.toString())
            }
        }
    }
    return blocks
}

private fun isBlockStart(lines: List<String>, index: Int): Boolean {
    val trimmed = lines[index].trim()
    if (trimmed.isEmpty()) return true
    return trimmed.startsWith("```") ||
        dividerRegex.matches(trimmed) ||
        headingRegex.matches(trimmed) ||
        trimmed.startsWith(">") ||
        listItemRegex.matches(lines[index].trimEnd()) ||
        isTableStart(lines, index)
}

private fun isTableStart(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val header = lines[index].trim()
    if (!header.contains('|')) return false
    val separator = splitTableRow(lines[index + 1].trim())
    return separator.isNotEmpty() && separator.all { tableSeparatorRegex.matches(it) }
}

private fun parseTable(lines: List<String>, start: Int): Pair<MdBlock.Table, Int> {
    val headers = splitTableRow(lines[start].trim())
    val alignments = splitTableRow(lines[start + 1].trim()).map { cell ->
        when {
            cell.startsWith(":") && cell.endsWith(":") -> TextAlign.Center
            cell.endsWith(":") -> TextAlign.End
            else -> TextAlign.Start
        }
    }
    val rows = mutableListOf<List<String>>()
    var index = start + 2
    while (index < lines.size) {
        val line = lines[index].trim()
        if (line.isEmpty() || !line.contains('|')) break
        val cells = splitTableRow(line)
        rows += headers.indices.map { cells.getOrElse(it) { "" } }
        index++
    }
    return MdBlock.Table(headers, alignments, rows) to index
}

private fun splitTableRow(line: String): List<String> {
    val body = line.removePrefix("|").removeSuffix("|")
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    body.forEach { char ->
        when {
            escaped -> {
                current.append(char)
                escaped = false
            }
            char == '\\' -> escaped = true
            char == '|' -> {
                cells += current.toString().trim()
                current.clear()
            }
            else -> current.append(char)
        }
    }
    cells += current.toString().trim()
    return cells
}

private fun inline(text: String, color: Color, linkColor: Color): MarkdownInline {
    val codeSpans = mutableListOf<IntRange>()
    val annotated = buildAnnotatedString { appendInline(text, color, linkColor, codeSpans) }
    return MarkdownInline(annotated, codeSpans)
}

private fun AnnotatedString.Builder.appendInline(
    text: String,
    color: Color,
    linkColor: Color,
    codeSpans: MutableList<IntRange>,
) {
    var index = 0
    while (index < text.length) {
        when {
            text[index] == '\\' && index + 1 < text.length -> {
                append(text[index + 1])
                index += 2
            }
            text.startsWith("```", index) -> {
                val end = text.indexOf("```", index + 3)
                if (end > index) {
                    appendCode(text.substring(index + 3, end), codeSpans)
                    index = end + 3
                } else {
                    append(text[index])
                    index++
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    appendCode(text.substring(index + 1, end), codeSpans)
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }
            text.startsWith("***", index) || text.startsWith("___", index) -> {
                val marker = text.substring(index, index + 3)
                val end = text.indexOf(marker, index + 3)
                if (end > index) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        appendInline(text.substring(index + 3, end), color, linkColor, codeSpans)
                    }
                    index = end + 3
                } else {
                    append(text[index])
                    index++
                }
            }
            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val marker = text.substring(index, index + 2)
                val end = text.indexOf(marker, index + 2)
                if (end > index) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInline(text.substring(index + 2, end), color, linkColor, codeSpans)
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index++
                }
            }
            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", index + 2)
                if (end > index) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInline(text.substring(index + 2, end), color, linkColor, codeSpans)
                    }
                    index = end + 2
                } else {
                    append(text[index])
                    index++
                }
            }
            text[index] == '*' || text[index] == '_' -> {
                val previous = text.getOrNull(index - 1)
                val next = text.getOrNull(index + 1)
                if (text[index] == '_' && previous?.isLetterOrDigit() == true && next?.isLetterOrDigit() == true) {
                    append(text[index])
                    index++
                    continue
                }
                val end = text.indexOf(text[index], index + 1)
                if (end > index) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInline(text.substring(index + 1, end), color, linkColor, codeSpans)
                    }
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }
            text[index] == '[' -> {
                val labelEnd = text.indexOf("](", index + 1)
                val urlEnd = if (labelEnd > index) text.indexOf(')', labelEnd + 2) else -1
                if (labelEnd > index && urlEnd > labelEnd) {
                    val label = text.substring(index + 1, labelEnd)
                    val url = text.substring(labelEnd + 2, urlEnd)
                    appendLink(
                        label = label,
                        url = url,
                        color = color,
                        linkColor = linkColor,
                        codeSpans = codeSpans,
                    )
                    index = urlEnd + 1
                } else {
                    append(text[index])
                    index++
                }
            }
            text[index] == '<' -> {
                val end = text.indexOf('>', index + 1)
                val url = if (end > index) text.substring(index + 1, end) else ""
                if (end > index && (url.startsWith("http://") || url.startsWith("https://"))) {
                    appendLink(
                        label = url,
                        url = url,
                        color = color,
                        linkColor = linkColor,
                        codeSpans = codeSpans,
                    )
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }
            else -> {
                append(text[index])
                index++
            }
        }
    }
}

private fun AnnotatedString.Builder.appendLink(
    label: String,
    url: String,
    color: Color,
    linkColor: Color,
    codeSpans: MutableList<IntRange>,
) {
    withLink(
        LinkAnnotation.Url(
            url = url,
            styles = TextLinkStyles(
                style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
            ),
        ),
    ) {
        appendInline(label, color, linkColor, codeSpans)
    }
}

private fun AnnotatedString.Builder.appendCode(code: String, codeSpans: MutableList<IntRange>) {
    val start = length
    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
        append(code)
    }
    codeSpans += start until length
}

private val headingRegex = Regex("""^(#{1,6})\s+(.+?)\s*#*$""")
private val dividerRegex = Regex("""^(?:(?:-\s*){3,}|(?:\*\s*){3,}|(?:_\s*){3,})$""")
private val listItemRegex = Regex("""^(\s*)([-*+]|\d{1,3}[.)])\s+(.*)$""")
private val tableSeparatorRegex = Regex("""^:?-{3,}:?$""")
