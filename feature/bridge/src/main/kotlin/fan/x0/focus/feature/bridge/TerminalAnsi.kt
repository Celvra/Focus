package fan.x0.focus.feature.bridge

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

internal val TerminalBackground = Color(0xFF16161A)

internal fun parseTerminal(raw: String, maxChars: Int): List<AnnotatedString> {
    val source = if (raw.length > maxChars) {
        raw.substring(raw.length - maxChars).substringAfter('\n', "")
    } else {
        raw
    }
    val lines = ArrayList<AnnotatedString>()
    val line = TerminalLine()
    var index = 0
    while (index < source.length) {
        val char = source[index]
        when {
            char == Esc -> index = line.escape(source, index)
            char == '\n' -> {
                lines += line.finish()
                index++
            }
            char == '\r' -> {
                val next = source.getOrNull(index + 1) ?: ' '
                if (next != '\n') line.carriageReturn()
                index++
            }
            char == '\b' -> {
                line.backspace()
                index++
            }
            char == '\t' -> {
                line.tab()
                index++
            }
            char < ' ' -> index++
            else -> {
                line.write(char)
                index++
            }
        }
    }
    lines += line.finish()
    return lines
}

private const val Esc = '\u001b'
private const val Bell = '\u0007'
private val DefaultForeground = Color(0xFFE4E4E7)
private val BoldForeground = Color(0xFFF8F8F8)
private val CubeLevels = intArrayOf(0, 95, 135, 175, 215, 255)
private val Palette = listOf(
    Color(0xFF1C1C1C),
    Color(0xFFD64F4F),
    Color(0xFF4FBF6A),
    Color(0xFFD6B84F),
    Color(0xFF5A8FD6),
    Color(0xFFC069D6),
    Color(0xFF4FBFC0),
    Color(0xFFD0D0D0),
    Color(0xFF7A7A7A),
    Color(0xFFFF6B6B),
    Color(0xFF6BE08A),
    Color(0xFFF0D060),
    Color(0xFF7EA8FF),
    Color(0xFFE47DEE),
    Color(0xFF63E0E0),
    Color(0xFFF8F8F8),
)

private fun paletteColor(index: Int): Color = when {
    index in Palette.indices -> Palette[index]
    index in 16..231 -> {
        val offset = index - 16
        rgbColor(
            CubeLevels[offset / 36],
            CubeLevels[(offset / 6) % 6],
            CubeLevels[offset % 6],
        )
    }
    index in 232..255 -> {
        val level = 8 + (index - 232) * 10
        rgbColor(level, level, level)
    }
    else -> DefaultForeground
}

private fun rgbColor(red: Int, green: Int, blue: Int): Color =
    Color(red = red.coerceIn(0, 255), green = green.coerceIn(0, 255), blue = blue.coerceIn(0, 255))

private data class TerminalStyle(
    val foreground: Color? = null,
    val background: Color? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
) {
    fun toSpanStyle(): SpanStyle {
        val text = if (inverse) {
            background ?: DefaultForeground
        } else {
            foreground ?: if (bold) BoldForeground else DefaultForeground
        }
        val fill = if (inverse) {
            foreground ?: TerminalBackground
        } else {
            background ?: Color.Unspecified
        }
        return SpanStyle(
            color = text,
            background = fill,
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (underline) TextDecoration.Underline else null,
        )
    }
}

private class TerminalLine {
    private val runs = ArrayList<Run>()
    private val buffer = StringBuilder()
    private var style = TerminalStyle()
    private var column = 0

    fun write(char: Char) {
        buffer.append(char)
        column++
    }

    fun tab() {
        val spaces = 8 - column % 8
        repeat(spaces) { buffer.append(' ') }
        column += spaces
    }

    fun backspace() {
        if (column == 0) return
        column--
        flush()
        val last = runs.lastOrNull() ?: return
        runs.removeAt(runs.lastIndex)
        val shortened = last.text.dropLast(1)
        if (shortened.isNotEmpty()) runs += Run(shortened, last.style)
    }

    fun carriageReturn() {
        runs.clear()
        buffer.clear()
        column = 0
    }

    fun finish(): AnnotatedString {
        flush()
        val line = buildAnnotatedString {
            runs.forEach { run ->
                withStyle(run.style.toSpanStyle()) { append(run.text) }
            }
        }
        runs.clear()
        column = 0
        return line
    }

    fun escape(source: String, start: Int): Int {
        val marker = source.getOrNull(start + 1) ?: return source.length
        return when (marker) {
            '[' -> {
                var cursor = start + 2
                val params = StringBuilder()
                while (cursor < source.length && source[cursor].isParameter()) {
                    params.append(source[cursor])
                    cursor++
                }
                if (cursor >= source.length) {
                    source.length
                } else {
                    if (source[cursor] == 'm') style = applySgr(style, params.toString())
                    cursor + 1
                }
            }
            ']' -> {
                var cursor = start + 2
                while (cursor < source.length) {
                    if (source[cursor] == Bell) return cursor + 1
                    if (source[cursor] == Esc && source.getOrNull(cursor + 1) == '\\') return cursor + 2
                    cursor++
                }
                source.length
            }
            else -> start + 2
        }
    }

    private fun flush() {
        if (buffer.isEmpty()) return
        runs += Run(buffer.toString(), style)
        buffer.clear()
    }

    private data class Run(val text: String, val style: TerminalStyle)
}

private fun Char.isParameter(): Boolean =
    this in '0'..'9' || this == ';' || this == ':' || this == '?' || this == '<' || this == '>' || this == '='

private fun applySgr(style: TerminalStyle, params: String): TerminalStyle {
    if (params.isEmpty()) return TerminalStyle()
    var current = style
    val codes = params.split(';')
    var index = 0
    while (index < codes.size) {
        when (val value = codes[index].substringBefore(':').toIntOrNull() ?: 0) {
            0 -> current = TerminalStyle()
            1 -> current = current.copy(bold = true)
            3 -> current = current.copy(italic = true)
            4 -> current = current.copy(underline = true)
            7 -> current = current.copy(inverse = true)
            22 -> current = current.copy(bold = false)
            23 -> current = current.copy(italic = false)
            24 -> current = current.copy(underline = false)
            27 -> current = current.copy(inverse = false)
            in 30..37 -> current = current.copy(foreground = paletteColor(value - 30))
            39 -> current = current.copy(foreground = null)
            in 40..47 -> current = current.copy(background = paletteColor(value - 40))
            49 -> current = current.copy(background = null)
            in 90..97 -> current = current.copy(foreground = paletteColor(value - 90 + 8))
            in 100..107 -> current = current.copy(background = paletteColor(value - 100 + 8))
            38, 48 -> {
                val (color, consumed) = extendedColor(codes, index + 1)
                if (color != null) {
                    current = if (value == 38) {
                        current.copy(foreground = color)
                    } else {
                        current.copy(background = color)
                    }
                }
                index += consumed
            }
        }
        index++
    }
    return current
}

private fun extendedColor(codes: List<String>, start: Int): Pair<Color?, Int> = when (
    codes.getOrNull(start)?.substringBefore(':')?.toIntOrNull()
) {
    5 -> {
        val index = codes.getOrNull(start + 1)?.toIntOrNull()
        val color = if (index == null) null else paletteColor(index)
        color to 2
    }
    2 -> {
        val red = codes.getOrNull(start + 1)?.toIntOrNull() ?: 0
        val green = codes.getOrNull(start + 2)?.toIntOrNull() ?: 0
        val blue = codes.getOrNull(start + 3)?.toIntOrNull() ?: 0
        rgbColor(red, green, blue) to 4
    }
    else -> null to 0
}
