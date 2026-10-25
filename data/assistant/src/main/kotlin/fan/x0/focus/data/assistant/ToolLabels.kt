package fan.x0.focus.data.assistant

object ToolLabels {
    fun of(name: String?): String = when (name) {
        "list_calendar_events", "search_calendar",
        "create_calendar_event", "delete_calendar_event",
        -> "Calendar"
        "list_feeds", "list_articles", "search_articles", "read_article",
        "mark_article_read", "add_feed", "delete_article", "remove_feed",
        -> "Feeds"
        "list_files", "read_text_file", "write_text_file", "create_text_file", "delete_file",
        -> "Files"
        "read_web_article" -> "Web page"
        "list_goals", "create_goal", "update_goal", "delete_goal" -> "Goals"
        "list_updates", "post_update", "delete_update" -> "Feed"
        "list_ideas", "save_idea", "delete_idea" -> "Ideas"
        "list_icons" -> "Icons"
        "save_memory" -> "Memory"
        "list_workspace_files", "read_workspace_file", "write_workspace_file", "append_workspace_file" -> "Context"
        "create_reminder", "list_reminders" -> "Reminder"
        "save_profile" -> "Profile"
        "set_assistant_name" -> "Name"
        "list_providers", "save_provider", "test_provider", "list_models", "switch_model" -> "Model"
        "complete_onboarding" -> "Setup"
        "rename_conversation" -> "Title"
        ToolCatalog.SET_WORKING_TEXT -> "Status"
        "create_plan" -> "Plan"
        else -> name?.replace('_', ' ') ?: "Tool"
    }
}