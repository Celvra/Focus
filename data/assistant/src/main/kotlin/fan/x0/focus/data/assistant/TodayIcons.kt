package fan.x0.focus.data.assistant

object TodayIcons {
    val all: List<Pair<String, String>> = listOf(
        "spark" to "general idea or thought",
        "news" to "news and world updates",
        "code" to "software, tools, and development",
        "book" to "reading and learning",
        "money" to "money, pricing, and finance",
        "health" to "health, body, and habits",
        "people" to "people, relationships, and social",
        "work" to "work, projects, and tasks",
        "home" to "home and daily life",
        "travel" to "travel and places",
        "time" to "schedule and time",
        "goal" to "goals and progress",
    )

    val keys: List<String> get() = all.map { it.first }

    fun sanitize(key: String?): String {
        val candidate = key?.trim()?.lowercase().orEmpty()
        return if (candidate in keys) candidate else "spark"
    }
}
