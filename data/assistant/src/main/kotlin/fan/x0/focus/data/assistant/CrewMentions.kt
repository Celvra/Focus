package fan.x0.focus.data.assistant

object CrewMentions {
    fun ids(text: String, members: List<CrewMember>): List<String> {
        val all = Regex("(?i)(?<![A-Za-z0-9_])@all(?![A-Za-z0-9_])").containsMatchIn(text)
        if (all) {
            return members.map { it.id }
        }
        return MentionText.mentionedNames(text, members.map { it.name })
            .mapNotNull { name -> members.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id }
    }

    fun names(text: String, members: List<CrewMember>): List<String> {
        val ids = ids(text, members).toSet()
        return members.filter { it.id in ids }.map { it.name }
    }

    fun query(input: String): String? = MentionText.active(input)?.query
}
