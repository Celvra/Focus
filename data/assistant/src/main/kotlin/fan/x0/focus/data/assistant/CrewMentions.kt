package fan.x0.focus.data.assistant

object CrewMentions {
    fun ids(text: String, members: List<CrewMember>): List<String> {
        val all = Regex("(?i)(?<![\\p{L}\\p{N}_])@all(?![\\p{L}\\p{N}_])").containsMatchIn(text)
        if (all) {
            return members.map { it.id }
        }
        val result = mutableListOf<String>()
        members
            .filter { it.name.isNotBlank() }
            .sortedByDescending { it.name.length }
            .forEach { member ->
                val needle = "@${member.name}"
                var from = 0
                while (from <= text.length) {
                    val index = text.indexOf(needle, from, ignoreCase = true)
                    if (index < 0) break
                    val before = text.getOrNull(index - 1)
                    val end = index + needle.length
                    val next = text.getOrNull(end)
                    if ((before == null || !before.isLetterOrDigit()) &&
                        (next == null || (!next.isLetterOrDigit() && next != '_'))
                    ) {
                        result += member.id
                        break
                    }
                    from = index + 1
                }
            }
        return result
    }

    fun names(text: String, members: List<CrewMember>): List<String> {
        val ids = ids(text, members).toSet()
        return members.filter { it.id in ids }.map { it.name }
    }

    fun query(input: String): String? {
        if (input.contains('\n')) return null
        val at = input.lastIndexOf('@')
        if (at < 0) return null
        if (at > 0 && !input[at - 1].isWhitespace()) return null
        return input.substring(at + 1).takeIf { it.length <= 32 }
    }
}
