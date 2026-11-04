package fan.x0.focus.data.assistant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object SkillToolCatalog {
    const val SKILL = "skill"
    const val LIST = "list_skills"
    const val SAVE = "save_skill"
    const val SET_ENABLED = "set_skill_enabled"
    const val DELETE = "delete_skill"

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            name = SKILL,
            description = "Load a skill by name. Returns the skill instructions and its reference files. Call it when the current task matches a skill in the system prompt.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("name")) {
                put("name", string("Skill name from the Skills list"))
                put("file", string("Reference file path from the loaded skill, optional"))
            },
        ),
        ToolSpec(
            name = LIST,
            description = "List every skill with its description, source, location, and enabled state.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = SAVE,
            description = "Create or update one app skill. Pass the full instructions in body. Skills are folders with a SKILL.md file.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("name", "description", "body")) {
                put("name", string("Short skill name, under 80 characters"))
                put("description", string("One line that says when to use it, under 300 characters"))
                put("body", string("Full instructions in markdown"))
                put("enabled", boolean("Whether the skill starts enabled, default true"))
            },
        ),
        ToolSpec(
            name = SET_ENABLED,
            description = "Enable or disable one skill by name. Disabled skills are hidden from the prompt and cannot be loaded.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("name", "enabled")) {
                put("name", string("Skill name"))
                put("enabled", boolean("true to enable, false to disable"))
            },
        ),
        ToolSpec(
            name = DELETE,
            description = "Delete one app skill. Workspace skills are read-only. Requires the user's approval.",
            kind = ToolKind.Gated,
            parameters = schema(required = listOf("name")) {
                put("name", string("App skill name"))
            },
        ),
    )

    private fun schema(required: List<String> = emptyList(), build: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { build() }
            if (required.isNotEmpty()) {
                putJsonArray("required") {
                    required.forEach { add(JsonPrimitive(it)) }
                }
            }
            put("additionalProperties", false)
        }

    private fun string(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun boolean(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }
}
