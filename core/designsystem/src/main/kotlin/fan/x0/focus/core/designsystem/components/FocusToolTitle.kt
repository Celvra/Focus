package fan.x0.focus.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fan.x0.focus.core.designsystem.R

@Composable
internal fun friendlyToolName(name: String): String {
    val tool = name.trim()
    if (tool.isEmpty()) return ""
    val id = ToolTitles[tool]
    if (id != null) return stringResource(id)
    return humanize(tool)
}

private val ToolTitles: Map<String, Int> = mapOf(
    "add_feed" to R.string.tool_add_feed,
    "append_workspace_file" to R.string.tool_append_workspace_file,
    "ask" to R.string.tool_ask,
    "bridge_bg_clear" to R.string.tool_bridge_bg_clear,
    "bridge_bg_kill" to R.string.tool_bridge_bg_kill,
    "bridge_bg_list" to R.string.tool_bridge_bg_list,
    "bridge_bg_logs" to R.string.tool_bridge_bg_logs,
    "bridge_bg_start" to R.string.tool_bridge_bg_start,
    "bridge_close_session" to R.string.tool_bridge_close_session,
    "bridge_fs_delete" to R.string.tool_bridge_fs_delete,
    "bridge_fs_edit" to R.string.tool_bridge_fs_edit,
    "bridge_fs_glob" to R.string.tool_bridge_fs_glob,
    "bridge_fs_grep" to R.string.tool_bridge_fs_grep,
    "bridge_fs_list" to R.string.tool_bridge_fs_list,
    "bridge_fs_media" to R.string.tool_bridge_fs_media,
    "bridge_fs_read" to R.string.tool_bridge_fs_read,
    "bridge_fs_write" to R.string.tool_bridge_fs_write,
    "bridge_list_devices" to R.string.tool_bridge_list_devices,
    "bridge_list_sessions" to R.string.tool_bridge_list_sessions,
    "bridge_read_session" to R.string.tool_bridge_read_session,
    "bridge_run_command" to R.string.tool_bridge_run_command,
    "bridge_session_open" to R.string.tool_bridge_session_open,
    "bridge_session_send" to R.string.tool_bridge_session_send,
    "bridge_set_workspace" to R.string.tool_bridge_set_workspace,
    "complete_onboarding" to R.string.tool_complete_onboarding,
    "create_calendar_event" to R.string.tool_create_calendar_event,
    "create_goal" to R.string.tool_create_goal,
    "create_plan" to R.string.tool_create_plan,
    "create_reminder" to R.string.tool_create_reminder,
    "create_text_file" to R.string.tool_create_text_file,
    "delete_article" to R.string.tool_delete_article,
    "delete_calendar_event" to R.string.tool_delete_calendar_event,
    "delete_file" to R.string.tool_delete_file,
    "delete_goal" to R.string.tool_delete_goal,
    "delete_idea" to R.string.tool_delete_idea,
    "delete_update" to R.string.tool_delete_update,
    "finish_group_discussion" to R.string.tool_finish_group_discussion,
    "index_workspace" to R.string.tool_index_workspace,
    "list_agent_groups" to R.string.tool_list_agent_groups,
    "list_articles" to R.string.tool_list_articles,
    "list_calendar_events" to R.string.tool_list_calendar_events,
    "list_feeds" to R.string.tool_list_feeds,
    "list_files" to R.string.tool_list_files,
    "list_goals" to R.string.tool_list_goals,
    "list_icons" to R.string.tool_list_icons,
    "list_ideas" to R.string.tool_list_ideas,
    "list_models" to R.string.tool_list_models,
    "list_providers" to R.string.tool_list_providers,
    "list_reminders" to R.string.tool_list_reminders,
    "list_updates" to R.string.tool_list_updates,
    "list_workspace_files" to R.string.tool_list_workspace_files,
    "mark_article_read" to R.string.tool_mark_article_read,
    "mcp_call_tool" to R.string.tool_mcp_call_tool,
    "mcp_list_resources" to R.string.tool_mcp_list_resources,
    "mcp_list_tools" to R.string.tool_mcp_list_tools,
    "mcp_read_resource" to R.string.tool_mcp_read_resource,
    "mention_group_agent" to R.string.tool_mention_group_agent,
    "post_update" to R.string.tool_post_update,
    "propose_plan" to R.string.tool_propose_plan,
    "react_to_group_message" to R.string.tool_react_to_group_message,
    "react_to_message" to R.string.tool_react_to_message,
    "read_article" to R.string.tool_read_article,
    "read_media_file" to R.string.tool_read_media_file,
    "read_text_file" to R.string.tool_read_text_file,
    "read_web_article" to R.string.tool_read_web_article,
    "read_workspace_file" to R.string.tool_read_workspace_file,
    "remove_feed" to R.string.tool_remove_feed,
    "rename_conversation" to R.string.tool_rename_conversation,
    "save_idea" to R.string.tool_save_idea,
    "save_memory" to R.string.tool_save_memory,
    "save_profile" to R.string.tool_save_profile,
    "save_provider" to R.string.tool_save_provider,
    "search_articles" to R.string.tool_search_articles,
    "search_calendar" to R.string.tool_search_calendar,
    "search_files" to R.string.tool_search_files,
    "set_assistant_name" to R.string.tool_set_assistant_name,
    "spawn_agent" to R.string.tool_spawn_agent,
    "start_agent_group_task" to R.string.tool_start_agent_group_task,
    "switch_model" to R.string.tool_switch_model,
    "test_provider" to R.string.tool_test_provider,
    "todoread" to R.string.tool_todoread,
    "todowrite" to R.string.tool_todowrite,
    "update_goal" to R.string.tool_update_goal,
    "web_search" to R.string.tool_web_search,
    "workspace_files" to R.string.tool_workspace_files,
    "write_text_file" to R.string.tool_write_text_file,
    "write_workspace_file" to R.string.tool_write_workspace_file,
    "create_agent_group" to R.string.tool_create_agent_group,
    "get_agent_group" to R.string.tool_get_agent_group,
    "update_agent_group" to R.string.tool_update_agent_group,
    "delete_agent_group" to R.string.tool_delete_agent_group,
    "add_agent_group_members" to R.string.tool_add_agent_group_members,
    "update_agent_group_member" to R.string.tool_update_agent_group_member,
    "remove_agent_group_member" to R.string.tool_remove_agent_group_member,
)

private fun humanize(name: String): String =
    name.replace('_', ' ')
        .replace('.', ' ')
        .replace('-', ' ')
        .trim()
        .replaceFirstChar { it.uppercase() }
