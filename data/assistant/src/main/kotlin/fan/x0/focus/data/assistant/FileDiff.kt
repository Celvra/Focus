package fan.x0.focus.data.assistant

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min

@Serializable
data class FileDiff(
    val path: String,
    val additions: Int = 0,
    val deletions: Int = 0,
    val hunks: List<DiffHunk> = emptyList(),
    val truncated: Boolean = false,
)

@Serializable
data class DiffHunk(
    val lines: List<DiffLine> = emptyList(),
)

@Serializable
data class DiffLine(
    val kind: DiffLineKind,
    val oldLine: Int? = null,
    val newLine: Int? = null,
    val text: String = "",
)

@Serializable
enum class DiffLineKind { Context, Added, Removed }

object LineDiff {
    private const val ContextLines = 3
    private const val MaxFileLines = 4000
    private const val MaxCells = 2_000_000L
    private const val MaxOutputLines = 400
    private const val MaxOutputChars = 60_000
    private const val MaxLineChars = 400

    fun compute(path: String, old: String, new: String): FileDiff? {
        if (old == new) return null
        val oldLines = split(old)
        val newLines = split(new)
        if (oldLines.size > MaxFileLines || newLines.size > MaxFileLines) return null
        if (oldLines.isEmpty() && newLines.isEmpty()) return null
        val prefix = commonPrefix(oldLines, newLines)
        val suffix = commonSuffix(oldLines, newLines, prefix)
        val oldMiddle = oldLines.subList(prefix, oldLines.size - suffix)
        val newMiddle = newLines.subList(prefix, newLines.size - suffix)
        val fallback = oldMiddle.size.toLong() * newMiddle.size.toLong() > MaxCells
        val lines = buildList {
            for (index in 0 until prefix) {
                add(DiffLine(DiffLineKind.Context, index + 1, index + 1, clean(oldLines[index])))
            }
            if (fallback) {
                oldMiddle.forEachIndexed { index, line ->
                    add(DiffLine(DiffLineKind.Removed, prefix + index + 1, null, clean(line)))
                }
                newMiddle.forEachIndexed { index, line ->
                    add(DiffLine(DiffLineKind.Added, null, prefix + index + 1, clean(line)))
                }
            } else {
                addAll(diffMiddle(oldMiddle, newMiddle, prefix))
            }
            val oldStart = oldLines.size - suffix
            val newStart = newLines.size - suffix
            for (index in 0 until suffix) {
                add(
                    DiffLine(
                        DiffLineKind.Context,
                        oldStart + index + 1,
                        newStart + index + 1,
                        clean(oldLines[oldStart + index]),
                    ),
                )
            }
        }
        val additions = lines.count { it.kind == DiffLineKind.Added }
        val deletions = lines.count { it.kind == DiffLineKind.Removed }
        if (additions == 0 && deletions == 0) return null
        val (hunks, truncated) = limit(buildHunks(lines))
        return FileDiff(
            path = path,
            additions = additions,
            deletions = deletions,
            hunks = hunks,
            truncated = truncated,
        )
    }

    private fun diffMiddle(old: List<String>, new: List<String>, offset: Int): List<DiffLine> {
        val oldSize = old.size
        val newSize = new.size
        if (oldSize == 0) {
            return new.mapIndexed { index, line ->
                DiffLine(DiffLineKind.Added, null, offset + index + 1, clean(line))
            }
        }
        if (newSize == 0) {
            return old.mapIndexed { index, line ->
                DiffLine(DiffLineKind.Removed, offset + index + 1, null, clean(line))
            }
        }
        val width = newSize + 1
        val table = IntArray((oldSize + 1) * width)
        for (i in oldSize - 1 downTo 0) {
            val row = i * width
            val next = (i + 1) * width
            for (j in newSize - 1 downTo 0) {
                table[row + j] = if (old[i] == new[j]) {
                    table[next + j + 1] + 1
                } else {
                    max(table[next + j], table[row + j + 1])
                }
            }
        }
        val result = ArrayList<DiffLine>(oldSize + newSize)
        var i = 0
        var j = 0
        while (i < oldSize && j < newSize) {
            when {
                old[i] == new[j] -> {
                    result += DiffLine(DiffLineKind.Context, offset + i + 1, offset + j + 1, clean(old[i]))
                    i++
                    j++
                }
                table[(i + 1) * width + j] >= table[i * width + j + 1] -> {
                    result += DiffLine(DiffLineKind.Removed, offset + i + 1, null, clean(old[i]))
                    i++
                }
                else -> {
                    result += DiffLine(DiffLineKind.Added, null, offset + j + 1, clean(new[j]))
                    j++
                }
            }
        }
        while (i < oldSize) {
            result += DiffLine(DiffLineKind.Removed, offset + i + 1, null, clean(old[i]))
            i++
        }
        while (j < newSize) {
            result += DiffLine(DiffLineKind.Added, null, offset + j + 1, clean(new[j]))
            j++
        }
        return result
    }

    private fun buildHunks(lines: List<DiffLine>): List<DiffHunk> {
        val changed = lines.indices.filter { lines[it].kind != DiffLineKind.Context }
        if (changed.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var start = max(0, changed.first() - ContextLines)
        var end = min(lines.lastIndex, changed.first() + ContextLines)
        for (index in changed.drop(1)) {
            val nextStart = max(0, index - ContextLines)
            val nextEnd = min(lines.lastIndex, index + ContextLines)
            if (nextStart <= end + 1) {
                end = max(end, nextEnd)
            } else {
                ranges += start..end
                start = nextStart
                end = nextEnd
            }
        }
        ranges += start..end
        return ranges.map { range -> DiffHunk(lines.subList(range.first, range.last + 1)) }
    }

    private fun limit(hunks: List<DiffHunk>): Pair<List<DiffHunk>, Boolean> {
        val result = mutableListOf<DiffHunk>()
        var count = 0
        var chars = 0
        for (hunk in hunks) {
            val kept = mutableListOf<DiffLine>()
            for (line in hunk.lines) {
                if (count >= MaxOutputLines || chars + line.text.length > MaxOutputChars) {
                    if (kept.isNotEmpty()) result += DiffHunk(kept)
                    return result to true
                }
                kept += line
                count++
                chars += line.text.length
            }
            if (kept.isNotEmpty()) result += DiffHunk(kept)
        }
        return result to false
    }

    private fun commonPrefix(old: List<String>, new: List<String>): Int {
        val limit = min(old.size, new.size)
        var index = 0
        while (index < limit && old[index] == new[index]) index++
        return index
    }

    private fun commonSuffix(old: List<String>, new: List<String>, prefix: Int): Int {
        val limit = min(old.size, new.size) - prefix
        var count = 0
        while (count < limit && old[old.size - count - 1] == new[new.size - count - 1]) count++
        return count
    }

    private fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = text.replace("\r\n", "\n").split('\n')
        return if (lines.size > 1 && lines.last().isEmpty()) lines.dropLast(1) else lines
    }

    private fun clean(line: String): String {
        val text = line.removeSuffix("\r")
        return if (text.length > MaxLineChars) text.take(MaxLineChars) + "..." else text
    }
}
