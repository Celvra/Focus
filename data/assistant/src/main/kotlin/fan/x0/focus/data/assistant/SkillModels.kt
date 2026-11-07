package fan.x0.focus.data.assistant

enum class SkillSource { App, Workspace, Plugin }

data class SkillEntry(
    val id: String,
    val name: String,
    val description: String,
    val source: SkillSource,
    val enabled: Boolean = true,
    val location: String = "",
    val slug: String = "",
    val targetKey: String = "",
    val workspaceKind: WorkspaceKind? = null,
    val endpointId: String = "",
    val treeUri: String = "",
    val dirPath: String = "",
    val dirUri: String = "",
    val pluginId: String = "",
)

data class SkillContent(
    val entry: SkillEntry,
    val body: String,
    val files: List<String> = emptyList(),
)

data class SkillImportReport(
    val imported: List<String> = emptyList(),
    val skipped: Int = 0,
)

data class SkillCandidate(
    val key: String,
    val name: String,
    val description: String,
    val body: String,
    val files: Map<String, ByteArray> = emptyMap(),
    val installed: Boolean = false,
)

data class SkillDraft(
    val slug: String = "",
    val name: String = "",
    val description: String = "",
    val body: String = "",
    val enabled: Boolean = true,
)

internal data class ScannedSkill(
    val entry: SkillEntry,
    val body: String,
    val files: List<String> = emptyList(),
    val filePaths: Map<String, String> = emptyMap(),
)

internal data class AppSkillFiles(
    val slug: String,
    val files: Map<String, ByteArray>,
)
