package fan.x0.focus.data.assistant

import java.util.Locale
import kotlin.math.abs

data class ParsedSkill(
    val name: String,
    val description: String,
    val body: String,
)

object SkillParser {
    const val MAX_NAME = 80
    const val MAX_DESCRIPTION = 300
    const val MAX_BODY = 40_000

    fun parse(raw: String): ParsedSkill? {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "---") return null
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return null
        val fields = mutableMapOf<String, String>()
        lines.subList(1, end + 1).forEach { line ->
            val index = line.indexOf(':')
            if (index <= 0) return@forEach
            val key = line.substring(0, index).trim().lowercase(Locale.ROOT)
            val value = line.substring(index + 1).trim().trim('"', '\'')
            if (key.isNotBlank() && value.isNotBlank()) fields[key] = value
        }
        val name = fields["name"]?.take(MAX_NAME)?.trim().orEmpty()
        val description = fields["description"]?.take(MAX_DESCRIPTION)?.trim().orEmpty()
        if (name.isBlank() || description.isBlank()) return null
        val body = lines.drop(end + 2).joinToString("\n").trim().take(MAX_BODY)
        return ParsedSkill(name = name, description = description, body = body)
    }

    fun render(name: String, description: String, body: String): String = buildString {
        appendLine("---")
        appendLine("name: ${oneLine(name, MAX_NAME)}")
        appendLine("description: ${oneLine(description, MAX_DESCRIPTION)}")
        appendLine("---")
        appendLine()
        append(body.trim().take(MAX_BODY))
        appendLine()
    }

    fun slug(name: String): String {
        val ascii = name.lowercase(Locale.ROOT)
            .map { char ->
                when {
                    char in 'a'..'z' || char in '0'..'9' -> char
                    char == '-' || char == '_' -> char
                    else -> '-'
                }
            }
            .joinToString("")
            .trim('-')
            .replace(Regex("-+"), "-")
            .take(40)
        if (ascii.isNotBlank()) return ascii
        return "skill-${abs(name.hashCode())}"
    }

    fun oneLine(value: String, max: Int): String =
        value.replace('\n', ' ').replace('\r', ' ').trim().take(max)
}
