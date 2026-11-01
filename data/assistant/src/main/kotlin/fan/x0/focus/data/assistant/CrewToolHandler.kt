package fan.x0.focus.data.assistant

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.koin.core.annotation.Single
import kotlin.math.abs

@Single
class CrewToolHandler(
    private val store: CrewStore,
    private val launchBus: CrewLaunchBus,
) {
    suspend fun handle(name: String, args: JsonObject): ToolResult? = when (name) {
        CrewToolCatalog.LIST_GROUPS -> listGroups()
        CrewToolCatalog.GET_GROUP -> getGroup(args)
        CrewToolCatalog.CREATE_GROUP -> createGroup(args)
        CrewToolCatalog.UPDATE_GROUP -> updateGroup(args)
        CrewToolCatalog.DELETE_GROUP -> deleteGroup(args)
        CrewToolCatalog.ADD_MEMBERS -> addMembers(args)
        CrewToolCatalog.UPDATE_MEMBER -> updateMember(args)
        CrewToolCatalog.REMOVE_MEMBER -> removeMember(args)
        CrewToolCatalog.START_TASK -> startTask(args)
        else -> null
    }

    private suspend fun listGroups(): ToolResult {
        val groups = store.groups()
        if (groups.isEmpty()) {
            return ok("No agent groups", "No agent groups yet. Create one with create_agent_group.")
        }
        val details = groups.map { group -> group to store.members(group.id) }
        val body = buildJsonObject {
            putJsonArray("groups") {
                details.forEach { (group, members) ->
                    add(groupJson(group, members))
                }
            }
        }.toString()
        return ok("${groups.size} groups", body)
    }

    private suspend fun getGroup(args: JsonObject): ToolResult {
        val group = resolveGroup(args.text("group")) ?: return fail("Group not found")
        val members = store.members(group.id)
        val recent = store.messages(group.id, (args.number("messages") ?: 20).coerceIn(0, 60))
        val body = buildJsonObject {
            putJsonObject("group") {
                put("id", group.id)
                put("name", group.name)
                put("topic", group.topic)
            }
            putJsonArray("members") {
                members.forEach { member ->
                    add(buildJsonObject {
                        put("id", member.id)
                        put("name", member.name)
                        put("duty", member.duty)
                    })
                }
            }
            putJsonArray("messages") {
                recent.forEach { message ->
                    add(buildJsonObject {
                        put("id", message.id)
                        put("author", message.authorName)
                        put("type", message.authorType.name.lowercase())
                        put("content", message.content.take(500))
                    })
                }
            }
        }.toString()
        return ok(group.name, body)
    }

    private suspend fun createGroup(args: JsonObject): ToolResult {
        val name = args.text("name")?.trim().orEmpty()
        if (name.isBlank()) return fail("name is required")
        val topic = args.text("topic").orEmpty()
        val avatar = CrewAvatars.parse(args.text("avatar"), abs((name + topic).hashCode()))
        val group = store.createGroup(name, topic, avatar.preset, avatar.palette)
        val task = args.text("task")?.trim().orEmpty()
        val members = try {
            val inputs = args.memberInputs()
            val added = if (inputs.isEmpty()) {
                listOf(store.addMember(group.id, CrewDefaults.LEAD_NAME, CrewDefaults.LEAD_DUTY, 0, 0))
            } else {
                inputs.mapIndexedNotNull { index, input ->
                    if (input.name.isBlank()) return@mapIndexedNotNull null
                    val choice = CrewAvatars.parse(input.avatar, abs(input.name.hashCode()) + index)
                    store.addMember(group.id, input.name, input.duty, choice.preset, choice.palette)
                }
            }
            if (added.isEmpty()) error("every member needs a name")
            if (task.isNotBlank()) {
                val content = withMentions(task, args.stringList("mentions"))
                store.appendMessage(group.id, CrewAuthorType.User, "user", "You", content)
                launchBus.request(group.id, CrewMentions.ids(content, added).toSet())
            }
            added
        } catch (e: Exception) {
            store.deleteGroup(group.id)
            throw e
        }
        val body = buildJsonObject {
            put("id", group.id)
            put("name", group.name)
            put("started", task.isNotBlank())
            putJsonArray("members") {
                members.forEach { member ->
                    add(buildJsonObject {
                        put("id", member.id)
                        put("name", member.name)
                        put("duty", member.duty)
                    })
                }
            }
        }.toString()
        return ok("Created ${group.name}", body)
    }

    private suspend fun updateGroup(args: JsonObject): ToolResult {
        val group = resolveGroup(args.text("group")) ?: return fail("Group not found")
        val name = args.text("name")?.trim().takeUnless { it.isNullOrBlank() } ?: group.name
        val topic = args.text("topic")?.let { it.trim().take(400) } ?: group.topic
        val avatar = args.text("avatar")?.let {
            CrewAvatars.parse(it, abs(group.name.hashCode()))
        } ?: CrewAvatarChoice(group.avatarPreset, group.avatarPalette)
        store.updateGroup(group.id, name, topic, avatar.preset, avatar.palette)
        return ok("Updated $name", "Group updated: $name (${group.id}).")
    }

    private suspend fun deleteGroup(args: JsonObject): ToolResult {
        val group = resolveGroup(args.text("group")) ?: return fail("Group not found")
        store.deleteGroup(group.id)
        return ok("Deleted ${group.name}", "Group deleted: ${group.name}.")
    }

    private suspend fun addMembers(args: JsonObject): ToolResult {
        val group = resolveGroup(args.text("group")) ?: return fail("Group not found")
        val inputs = args.memberInputs().filter { it.name.isNotBlank() }
        if (inputs.isEmpty()) return fail("Each member needs a name")
        val added = inputs.mapIndexed { index, input ->
            val choice = CrewAvatars.parse(input.avatar, abs(input.name.hashCode()) + index)
            store.addMember(group.id, input.name, input.duty, choice.preset, choice.palette)
        }
        val body = buildJsonObject {
            put("group_id", group.id)
            putJsonArray("members") {
                added.forEach { member ->
                    add(buildJsonObject {
                        put("id", member.id)
                        put("name", member.name)
                    })
                }
            }
        }.toString()
        return ok("Added ${added.size} members", body)
    }

    private suspend fun updateMember(args: JsonObject): ToolResult {
        val group = resolveGroup(args.text("group")) ?: return fail("Group not found")
        val member = store.resolveMember(group.id, args.text("member").orEmpty())
            ?: return fail("Member not found")
        val name = args.text("name")?.trim().takeUnless { it.isNullOrBlank() } ?: member.name
        val duty = args.text("duty")?.trim() ?: member.duty
        val avatar = args.text("avatar")?.let {
            CrewAvatars.parse(it, abs(name.hashCode()))
        } ?: CrewAvatarChoice(member.avatarPreset, member.avatarPalette)
        store.updateMember(
            member.copy(
                name = name,
                duty = duty,
                avatarPreset = avatar.preset,
                avatarPalette = avatar.palette,
            ),
        )
        return ok("Updated $name", "Member updated: $name (${member.id}).")
    }

    private suspend fun removeMember(args: JsonObject): ToolResult {
        val group = resolveGroup(args.text("group")) ?: return fail("Group not found")
        val member = store.resolveMember(group.id, args.text("member").orEmpty())
            ?: return fail("Member not found")
        store.removeMember(member.id)
        return ok("Removed ${member.name}", "Member removed: ${member.name}.")
    }

    private suspend fun startTask(args: JsonObject): ToolResult {
        val group = resolveGroup(args.text("group")) ?: return fail("Group not found")
        val task = args.text("task")?.trim().orEmpty()
        if (task.isBlank()) return fail("task is required")
        val members = store.members(group.id)
        if (members.isEmpty()) return fail("The group has no active members")
        val content = withMentions(task, args.stringList("mentions"))
        val message = store.appendMessage(group.id, CrewAuthorType.User, "user", "You", content)
        launchBus.request(group.id, CrewMentions.ids(content, members).toSet())
        return ok(
            "Started ${group.name}",
            "Task sent to ${group.name}. Message id ${message.id}. The group is working now.",
        )
    }

    private suspend fun resolveGroup(reference: String?): AgentGroup? = store.resolveGroup(reference.orEmpty())

    private fun groupJson(group: AgentGroup, members: List<CrewMember>): JsonObject = buildJsonObject {
        put("id", group.id)
        put("name", group.name)
        put("topic", group.topic)
        put("member_count", members.size)
        putJsonArray("members") {
            members.forEach { member ->
                add(buildJsonObject {
                    put("id", member.id)
                    put("name", member.name)
                    put("duty", member.duty)
                })
            }
        }
    }

    private fun withMentions(task: String, mentions: List<String>): String {
        if (mentions.isEmpty()) return task
        return mentions.joinToString(" ") { "@${it.removePrefix("@").trim()}" } + " " + task
    }

    private fun ok(summary: String, content: String): ToolResult =
        ToolResult(ok = true, content = content, summary = summary)

    private fun fail(message: String): ToolResult =
        ToolResult(ok = false, content = "Error: $message", summary = message)

    private data class MemberInput(
        val name: String,
        val duty: String,
        val avatar: String?,
    )

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf { value -> value.isNotBlank() } }
            .orEmpty()

    private fun JsonObject.memberInputs(): List<MemberInput> =
        (this["members"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            MemberInput(
                name = item.text("name").orEmpty().trim(),
                duty = item.text("duty").orEmpty().trim(),
                avatar = item.text("avatar"),
            )
        }
}
