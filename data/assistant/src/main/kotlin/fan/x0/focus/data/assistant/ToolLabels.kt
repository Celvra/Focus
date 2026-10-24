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
        "list_updates", "post_update" -> "Feed"
        "list_ideas", "save_idea" -> "Ideas"
        "list_icons" -> "Icons"
        "list_memories", "save_memory", "delete_memory" -> "Memory"
        "create_reminder", "list_reminders" -> "Reminder"
        "save_profile", "complete_onboarding" -> "Setup"
        "rename_conversation" -> "Title"
        "create_plan" -> "Plan"
        else -> name?.replace('_', ' ') ?: "Tool"
    }
}