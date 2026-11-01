package fan.x0.focus.data.assistant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

object CrewToolCatalog {
    const val LIST_GROUPS = "list_agent_groups"
    const val GET_GROUP = "get_agent_group"
    const val CREATE_GROUP = "create_agent_group"
    const val UPDATE_GROUP = "update_agent_group"
    const val DELETE_GROUP = "delete_agent_group"
    const val ADD_MEMBERS = "add_agent_group_members"
    const val UPDATE_MEMBER = "update_agent_group_member"
    const val REMOVE_MEMBER = "remove_agent_group_member"
    const val START_TASK = "start_agent_group_task"

    const val REACT = "react_to_group_message"
    const val MENTION = "mention_group_agent"
    const val FINISH = "finish_group_discussion"

    val assistantNames: Set<String> = setOf(
        LIST_GROUPS,
        GET_GROUP,
        CREATE_GROUP,
        UPDATE_GROUP,
        DELETE_GROUP,
        ADD_MEMBERS,
        UPDATE_MEMBER,
        REMOVE_MEMBER,
        START_TASK,
    )

    val agentNames: Set<String> = setOf(REACT, MENTION, FINISH)

    val assistantSpecs: List<ToolSpec> = listOf(
        ToolSpec(
            name = LIST_GROUPS,
            description = "List every agent group with its members and duties. Use it before changing or starting a group.",
            kind = ToolKind.Read,
            parameters = schema {},
        ),
        ToolSpec(
            name = GET_GROUP,
            description = "Read one agent group with its members, duties, and recent messages.",
            kind = ToolKind.Read,
            parameters = schema(required = listOf("group")) {
                put("group", string("Group id or exact name"))
                put("messages", number("Recent messages to include, default 20"))
            },
        ),
        ToolSpec(
            name = CREATE_GROUP,
            description = "Create an agent group. Give every member a name and a duty. Optionally start a task immediately. The user can open the group from the Groups page.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("name")) {
                put("name", string("Group name"))
                put("topic", string("What the group works on"))
                put("avatar", string("Optional avatar as preset:palette, for example 2:3"))
                put("members", memberArray("Members to create. Two or three focused roles work best."))
                put("task", string("Optional first task. The group starts working when set."))
                put("mentions", stringArray("Optional member names to call first for the task"))
            },
        ),
        ToolSpec(
            name = UPDATE_GROUP,
            description = "Rename an agent group, change its topic, or change its avatar.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("group")) {
                put("group", string("Group id or exact name"))
                put("name", string("New group name"))
                put("topic", string("New topic"))
                put("avatar", string("Avatar as preset:palette"))
            },
        ),
        ToolSpec(
            name = DELETE_GROUP,
            description = "Delete an agent group and its messages. Ask the user before deleting.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("group")) {
                put("group", string("Group id or exact name"))
            },
        ),
        ToolSpec(
            name = ADD_MEMBERS,
            description = "Add members to an existing agent group. Each member needs a name and a duty.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("group", "members")) {
                put("group", string("Group id or exact name"))
                put("members", memberArray("Members to add"))
            },
        ),
        ToolSpec(
            name = UPDATE_MEMBER,
            description = "Change one member's name, duty, or avatar.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("group", "member")) {
                put("group", string("Group id or exact name"))
                put("member", string("Member id or exact name"))
                put("name", string("New name"))
                put("duty", string("New duty"))
                put("avatar", string("Avatar as preset:palette"))
            },
        ),
        ToolSpec(
            name = REMOVE_MEMBER,
            description = "Remove one member from a group. Earlier messages stay. The member stops speaking.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("group", "member")) {
                put("group", string("Group id or exact name"))
                put("member", string("Member id or exact name"))
            },
        ),
        ToolSpec(
            name = START_TASK,
            description = "Send a task to an agent group and let its members discuss and work. Optionally mention members to call first.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("group", "task")) {
                put("group", string("Group id or exact name"))
                put("task", string("Task or message for the group"))
                put("mentions", stringArray("Optional member names to call first"))
            },
        ),
    )

    val agentSpecs: List<ToolSpec> = listOf(
        ToolSpec(
            name = REACT,
            description = "Add one emoji reaction to a group message. Omit message_id and match to react to the latest user message.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("reaction")) {
                put("reaction", string("One emoji"))
                put("match", string("A short snippet from the target message"))
                put("message_id", number("Exact group message id"))
            },
        ),
        ToolSpec(
            name = MENTION,
            description = "Ask another member to speak next. Prefer writing @Name in your reply; use this only as a fallback.",
            kind = ToolKind.Write,
            parameters = schema(required = listOf("agent")) {
                put("agent", string("Member name or id"))
                put("reason", string("Why this member should speak"))
            },
        ),
        ToolSpec(
            name = FINISH,
            description = "End the current group discussion when the group has a clear result.",
            kind = ToolKind.Write,
            parameters = schema {
                put("summary", string("One line result for the group"))
            },
        ),
    )

    private fun memberArray(description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        put("maxItems", 8)
        putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
                put("name", string("Short member name"))
                put("duty", string("What this member owns"))
                put("avatar", string("Optional avatar as preset:palette"))
            }
            putJsonArray("required") {
                add(JsonPrimitive("name"))
                add(JsonPrimitive("duty"))
            }
            put("additionalProperties", false)
        }
    }

    private fun stringArray(description: String): JsonObject = buildJsonObject {
        put("type", "array")
        put("description", description)
        putJsonObject("items") { put("type", "string") }
    }

    private fun schema(required: List<String> = emptyList(), props: JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties", props)
            if (required.isNotEmpty()) {
                putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
            }
            put("additionalProperties", false)
        }

    private fun string(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun number(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }
}
