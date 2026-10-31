package fan.x0.focus.data.assistant

object ToolLabels {
    fun of(name: String?): String = when (name) {
        "list_calendar_events", "search_calendar",
        "create_calendar_event", "delete_calendar_event",
        -> "Calendar"
        "list_feeds", "list_articles", "search_articles", "read_article",
        "mark_article_read", "add_feed", "delete_article", "remove_feed",
        -> "Feeds"
        "list_files", "read_text_file", "read_media_file", "write_text_file", "create_text_file", "delete_file",
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
        "react_to_message" -> "Reaction"
        "bridge_list_devices", "bridge_list_sessions", "bridge_read_session" -> "Computer"
        "bridge_run_command", "bridge_session_open", "bridge_session_send", "bridge_close_session" -> "Computer shell"
        "bridge_fs_read", "bridge_fs_media", "bridge_fs_list", "bridge_fs_glob", "bridge_fs_grep" -> "Computer files"
        "bridge_fs_write", "bridge_fs_edit", "bridge_fs_delete", "bridge_set_workspace" -> "Computer files"
        ToolCatalog.SET_WORKING_TEXT -> "Status"
        "ask" -> "Question"
        "create_plan" -> "Plan"
        "propose_plan" -> "Plan"
        "todowrite", "todoread" -> "Tasks"
        "web_search" -> "Web search"
        "spawn_agent" -> "Subagent"
        "mcp_list_tools", "mcp_call_tool", "mcp_list_resources", "mcp_read_resource" -> "MCP"
        "index_workspace", "search_files", "workspace_files" -> "Workspace"
        "bridge_bg_start", "bridge_bg_logs", "bridge_bg_kill", "bridge_bg_list", "bridge_bg_clear" -> "Background command"
        else -> name?.replace('_', ' ') ?: "Tool"
    }
}