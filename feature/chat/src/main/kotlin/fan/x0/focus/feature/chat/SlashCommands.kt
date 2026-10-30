package fan.x0.focus.feature.chat

enum class SlashCommandId {
    Undo,
    Redo,
    Btw,
    New,
    Resume,
    Sessions,
    Models,
    Effort,
    Variant,
    Settings,
    Help,
}

data class SlashCommandSpec(
    val id: SlashCommandId,
    val command: String,
    val descriptionRes: Int,
    val hintRes: Int,
    val acceptsArgument: Boolean = false,
)

object SlashCommands {
    val all: List<SlashCommandSpec> = listOf(
        SlashCommandSpec(SlashCommandId.Undo, "/undo", R.string.slash_undo_desc, R.string.slash_no_arg),
        SlashCommandSpec(SlashCommandId.Redo, "/redo", R.string.slash_redo_desc, R.string.slash_no_arg),
        SlashCommandSpec(SlashCommandId.Btw, "/btw", R.string.slash_btw_desc, R.string.slash_btw_hint, true),
        SlashCommandSpec(SlashCommandId.New, "/new", R.string.slash_new_desc, R.string.slash_no_arg),
        SlashCommandSpec(SlashCommandId.Resume, "/resume", R.string.slash_resume_desc, R.string.slash_resume_hint, true),
        SlashCommandSpec(SlashCommandId.Sessions, "/sessions", R.string.slash_sessions_desc, R.string.slash_no_arg),
        SlashCommandSpec(SlashCommandId.Models, "/models", R.string.slash_models_desc, R.string.slash_models_hint, true),
        SlashCommandSpec(SlashCommandId.Effort, "/effort", R.string.slash_effort_desc, R.string.slash_effort_hint, true),
        SlashCommandSpec(SlashCommandId.Variant, "/variant", R.string.slash_variant_desc, R.string.slash_variant_hint, true),
        SlashCommandSpec(SlashCommandId.Settings, "/settings", R.string.slash_settings_desc, R.string.slash_settings_hint, true),
        SlashCommandSpec(SlashCommandId.Help, "/help", R.string.slash_help_desc, R.string.slash_no_arg),
    )

    fun filter(query: String): List<SlashCommandSpec> {
        val text = query.trim().removePrefix("/").lowercase()
        if (text.isBlank()) return all
        return all.filter { it.command.removePrefix("/").startsWith(text) }
    }

    fun find(name: String): SlashCommandSpec? =
        all.firstOrNull { it.command.equals(name, ignoreCase = true) }
}
