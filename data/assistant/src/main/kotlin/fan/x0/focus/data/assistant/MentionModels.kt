package fan.x0.focus.data.assistant

enum class MentionKind { Skill, Member, File, Workspace }

data class MentionItemUi(
    val id: String,
    val kind: MentionKind,
    val label: String,
    val description: String = "",
    val insert: String = label,
    val action: Boolean = false,
    val directory: Boolean = false,
)

data class MentionUi(
    val open: Boolean = false,
    val query: String = "",
    val start: Int = 0,
    val items: List<MentionItemUi> = emptyList(),
    val selected: Int = 0,
) {
    val visible: Boolean get() = open && items.isNotEmpty()
}

const val WORKSPACE_SWITCH_ID = "workspace:switch"
const val WORKSPACE_BIND_PREFIX = "workspace:bind:"
