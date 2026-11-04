package fan.x0.focus.data.assistant

object MentionText {
    data class Active(val start: Int, val query: String)

    fun active(input: String): Active? {
        val at = input.lastIndexOf('@')
        if (at < 0) return null
        if (at > 0 && blocksBefore(input[at - 1])) return null
        val tail = input.substring(at + 1)
        if (tail.length > MAX_QUERY) return null
        if (tail.any { it.isWhitespace() }) return null
        return Active(at, tail)
    }

    fun insert(input: String, start: Int, mention: String): String {
        if (start !in 0..input.length) return input
        return input.substring(0, start) + "@" + mention + " "
    }

    fun mentionedNames(text: String, names: List<String>): List<String> {
        if (text.isBlank() || names.isEmpty()) return emptyList()
        val found = mutableListOf<Pair<Int, String>>()
        val claimed = mutableListOf<IntRange>()
        names.filter { it.isNotBlank() }
            .sortedByDescending { it.length }
            .forEach { name ->
                val needle = "@$name"
                var from = 0
                while (from <= text.length) {
                    val index = text.indexOf(needle, from, ignoreCase = true)
                    if (index < 0) break
                    val end = index + needle.length
                    val before = text.getOrNull(index - 1)
                    val next = text.getOrNull(end)
                    val range = index until end
                    val free = claimed.none { it.first <= range.last && range.first <= it.last }
                    if (!blocksBefore(before) && !blocksAfter(next) && free) {
                        found += index to name
                        claimed += range
                        break
                    }
                    from = index + 1
                }
            }
        return found.sortedBy { it.first }.map { it.second }
    }

    private fun blocksBefore(char: Char?): Boolean =
        char != null && (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9')

    private fun blocksAfter(char: Char?): Boolean =
        char != null && (char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9' || char == '_')

    private const val MAX_QUERY = 512
}
