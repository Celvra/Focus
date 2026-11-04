package fan.x0.focus.data.assistant

import fan.x0.focus.data.bridge.BridgeEndpoint
import fan.x0.focus.data.bridge.BridgeRepository
import fan.x0.focus.data.sources.FileRepository
import fan.x0.focus.data.sources.SourceFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single

@Single
class SkillWorkspaceScanner(
    private val trust: WorkspaceTrustService,
    private val bridge: BridgeRepository,
    private val files: FileRepository,
) {
    internal suspend fun scan(): List<ScannedSkill> = try {
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            val targets = trust.trustedTargets()
            val found = mutableListOf<ScannedSkill>()
            targets.forEach { target ->
                when (target.kind) {
                    WorkspaceKind.Bridge -> found += scanBridge(target)
                    WorkspaceKind.Local -> found += scanLocal(target)
                }
            }
            found.distinctBy { it.entry.id }
        } ?: emptyList()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    internal suspend fun readFile(skill: ScannedSkill, relative: String): String? {
        val path = skill.filePaths[relative] ?: return null
        return when (skill.entry.workspaceKind) {
            WorkspaceKind.Local -> quietly {
                files.readText(path, MAX_FILE_CHARS)
            }
            WorkspaceKind.Bridge -> {
                val endpoint = endpointFor(skill.entry.endpointId) ?: return null
                quietly {
                    val result = bridge.read(endpoint, path, limit = MAX_LINES)
                    result.content.ifBlank { result.lines }.take(MAX_FILE_CHARS)
                }
            }
            null -> null
        }
    }

    private suspend fun scanLocal(target: WorkspaceTarget): List<ScannedSkill> {
        if (target.treeUri.isBlank()) return emptyList()
        if (files.selectedTree() != target.treeUri) return emptyList()
        val rootEntries = quietly { files.list() }.orEmpty()
        val bases = mutableListOf<Pair<String, SourceFile>>()
        rootEntries.filter { it.isDirectory && it.name in WORKSPACE_DIRS }.forEach { dir ->
            val children = quietly { files.list(dir.uri) }.orEmpty()
            children.firstOrNull { it.isDirectory && it.name == SKILLS_DIR }?.let { skills ->
                bases += "${dir.name}/$SKILLS_DIR" to skills
            }
        }
        val found = mutableListOf<ScannedSkill>()
        bases.forEach { (relative, dir) ->
            val skills = quietly { files.list(dir.uri) }.orEmpty()
            skills.filter { it.isDirectory }.forEach { skillDir ->
                val entryFile = quietly { files.list(skillDir.uri) }.orEmpty()
                    .firstOrNull { !it.isDirectory && it.name == ENTRY }
                if (entryFile == null) return@forEach
                val raw = quietly { files.readText(entryFile.uri, MAX_ENTRY_CHARS) }
                    ?: return@forEach
                val parsed = SkillParser.parse(raw) ?: return@forEach
                val tree = localTree(skillDir)
                val filePaths = tree
                    .filterNot { it.first == ENTRY }
                    .associate { it.first to it.second.uri }
                found += ScannedSkill(
                    entry = SkillEntry(
                        id = "workspace:${target.key}:${skillDir.uri}",
                        name = parsed.name,
                        description = parsed.description,
                        source = SkillSource.Workspace,
                        location = "${target.label} / $relative/${skillDir.name}",
                        slug = skillDir.name,
                        targetKey = target.key,
                        workspaceKind = WorkspaceKind.Local,
                        treeUri = target.treeUri,
                        dirPath = skillDir.uri,
                        dirUri = skillDir.uri,
                    ),
                    body = parsed.body,
                    files = filePaths.keys.toList(),
                    filePaths = filePaths,
                )
            }
        }
        return found
    }

    private suspend fun scanBridge(target: WorkspaceTarget): List<ScannedSkill> {
        val endpoint = endpointFor(target.endpointId) ?: return emptyList()
        val roots = target.roots.filter { it.isNotBlank() }.ifEmpty {
            quietly { bridge.workspace(endpoint) }.orEmpty()
        }
        val found = mutableListOf<ScannedSkill>()
        val seen = mutableSetOf<String>()
        roots.forEach { root ->
            WORKSPACE_DIRS.forEach { workspaceDir ->
                val relative = "$workspaceDir/$SKILLS_DIR"
                val dir = joinPath(root, relative)
                if (!seen.add(dir)) return@forEach
                val entries = quietly { bridge.list(endpoint, dir, depth = 2) }
                    ?: return@forEach
                entries.filter { !it.isDir && it.name == ENTRY }.forEach { entryFile ->
                    val skillDir = entryFile.path.substringBeforeLast('/', "")
                    if (skillDir.isBlank() || skillDir == dir.trimEnd('/')) return@forEach
                    if (!seen.add(skillDir)) return@forEach
                    val content = quietly {
                        bridge.read(endpoint, entryFile.path, limit = MAX_LINES)
                    } ?: return@forEach
                    val raw = content.content.ifBlank { content.lines }.take(MAX_ENTRY_CHARS)
                    val parsed = SkillParser.parse(raw) ?: return@forEach
                    val nested = quietly {
                        bridge.list(endpoint, skillDir, depth = 3)
                    }.orEmpty()
                    val filePaths = nested.filterNot { it.isDir }.mapNotNull { file ->
                        val child = file.path.removePrefix("$skillDir/")
                        if (child.isBlank() || child == file.path || child == ENTRY) null else child to file.path
                    }.toMap()
                    found += ScannedSkill(
                        entry = SkillEntry(
                            id = "workspace:${target.key}:$skillDir",
                            name = parsed.name,
                            description = parsed.description,
                            source = SkillSource.Workspace,
                            location = "${target.label} / $relative/${skillDir.substringAfterLast('/')}",
                            slug = skillDir.substringAfterLast('/'),
                            targetKey = target.key,
                            workspaceKind = WorkspaceKind.Bridge,
                            endpointId = target.endpointId,
                            dirPath = skillDir,
                        ),
                        body = parsed.body,
                        files = filePaths.keys.toList(),
                        filePaths = filePaths,
                    )
                }
            }
        }
        return found
    }

    private suspend fun localTree(dir: SourceFile, prefix: String = "", depth: Int = 0): List<Pair<String, SourceFile>> {
        if (depth > MAX_DEPTH) return emptyList()
        val entries = quietly { files.list(dir.uri) }.orEmpty()
        val result = mutableListOf<Pair<String, SourceFile>>()
        entries.sortedBy { it.name }.forEach { entry ->
            if (result.size >= MAX_FILES) return@forEach
            val relative = if (prefix.isBlank()) entry.name else "$prefix/${entry.name}"
            if (entry.isDirectory) {
                result += localTree(entry, relative, depth + 1)
            } else {
                result += relative to entry
            }
        }
        return result.take(MAX_FILES)
    }

    private suspend fun endpointFor(id: String): BridgeEndpoint? {
        if (id.isBlank()) return null
        return bridge.endpointsOnce().firstOrNull { it.id == id }
    }

    private fun joinPath(root: String, relative: String): String {
        val base = root.trimEnd('/')
        return if (base.isEmpty()) "/$relative" else "$base/$relative"
    }

    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val ENTRY = "SKILL.md"
        private const val SKILLS_DIR = "skills"
        private val WORKSPACE_DIRS = listOf(".opencode", ".claude")
        private const val MAX_ENTRY_CHARS = 120_000
        private const val MAX_FILE_CHARS = 60_000
        private const val MAX_LINES = 2000
        private const val MAX_FILES = 100
        private const val MAX_DEPTH = 4
        private const val SCAN_TIMEOUT_MS = 20_000L
    }
}
