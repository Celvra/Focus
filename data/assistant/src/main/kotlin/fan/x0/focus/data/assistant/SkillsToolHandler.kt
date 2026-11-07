package fan.x0.focus.data.assistant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.koin.core.annotation.Single

@Single
class SkillsToolHandler(private val repository: SkillRepository) {

    suspend fun handle(name: String, args: JsonObject): ToolResult? = when (name) {
        SkillToolCatalog.SKILL -> load(args)
        SkillToolCatalog.LIST -> list()
        SkillToolCatalog.SAVE -> save(args)
        SkillToolCatalog.SET_ENABLED -> setEnabled(args)
        SkillToolCatalog.DELETE -> delete(args)
        else -> null
    }

    private suspend fun load(args: JsonObject): ToolResult {
        val name = args.text("name") ?: return fail("name is required")
        val entry = repository.find(name) ?: return fail("Skill not found: $name")
        if (!entry.enabled) return fail("Skill is disabled: ${entry.name}")
        val file = args.text("file")
        if (file != null) {
            val body = repository.readFile(entry, file)
                ?: return fail("Reference file not found: $file")
            return ok(
                "Read ${entry.name}: $file",
                "# ${entry.name}: $file\n\n${body.take(MAX_CONTENT)}",
            )
        }
        val content = repository.content(entry) ?: return fail("Skill content is unavailable: ${entry.name}")
        val builder = StringBuilder()
        builder.appendLine("# Skill: ${entry.name}")
        builder.appendLine(entry.description)
        if (entry.location.isNotBlank() && entry.source != SkillSource.App) {
            builder.appendLine("Source: ${entry.location}")
        }
        builder.appendLine()
        builder.appendLine(content.body.ifBlank { "(empty)" })
        if (content.files.isNotEmpty()) {
            builder.appendLine()
            builder.appendLine("Reference files:")
            content.files.take(MAX_FILES).forEach { builder.appendLine("- $it") }
        }
        return ok(entry.name, builder.toString().take(MAX_CONTENT))
    }

    private suspend fun list(): ToolResult {
        val skills = repository.entries()
        val body = buildJsonObject {
            put("count", skills.size)
            putJsonArray("skills") {
                skills.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("name", entry.name)
                            put("description", entry.description)
                            put("enabled", entry.enabled)
                            put(
                                "source",
                                when (entry.source) {
                                    SkillSource.App -> "app"
                                    SkillSource.Plugin -> "plugin"
                                    SkillSource.Workspace -> "workspace"
                                },
                            )
                            put("location", entry.location)
                        },
                    )
                }
            }
        }
        return ok("${skills.size} skills", body.toString())
    }

    private suspend fun save(args: JsonObject): ToolResult {
        val name = args.text("name") ?: return fail("name is required")
        val description = args.text("description") ?: return fail("description is required")
        val body = args.text("body").orEmpty()
        val enabled = args.flag("enabled") ?: true
        val entry = repository.save(
            SkillDraft(
                name = name,
                description = description,
                body = body,
                enabled = enabled,
            ),
        )
        return ok(
            entry.name,
            buildJsonObject {
                put("name", entry.name)
                put("slug", entry.slug)
                put("enabled", entry.enabled)
                put("source", "app")
            }.toString(),
        )
    }

    private suspend fun setEnabled(args: JsonObject): ToolResult {
        val name = args.text("name") ?: return fail("name is required")
        val enabled = args.flag("enabled") ?: return fail("enabled is required")
        val entry = repository.find(name) ?: return fail("Skill not found: $name")
        repository.setEnabled(entry, enabled)
        return ok(
            entry.name,
            "Skill ${entry.name} is now ${if (enabled) "enabled" else "disabled"}.",
        )
    }

    private suspend fun delete(args: JsonObject): ToolResult {
        val name = args.text("name") ?: return fail("name is required")
        val entry = repository.find(name) ?: return fail("Skill not found: $name")
        if (entry.source != SkillSource.App) return fail("Only app skills can be deleted")
        if (!repository.delete(entry)) return fail("Cannot delete ${entry.name}")
        return ok("Deleted ${entry.name}", "Skill ${entry.name} deleted.")
    }

    private fun ok(summary: String, content: String): ToolResult =
        ToolResult(ok = true, content = content, summary = summary)

    private fun fail(message: String): ToolResult =
        ToolResult(ok = false, content = "Error: $message", summary = message)

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.flag(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    companion object {
        private const val MAX_CONTENT = 45_000
        private const val MAX_FILES = 30
    }
}
