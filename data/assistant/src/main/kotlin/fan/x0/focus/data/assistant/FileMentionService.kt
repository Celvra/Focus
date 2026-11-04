package fan.x0.focus.data.assistant

import fan.x0.focus.data.bridge.BridgeEndpoint
import fan.x0.focus.data.bridge.BridgeRepository
import fan.x0.focus.data.sources.FileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

data class FileMentionEntry(
    val id: String,
    val name: String,
    val relative: String,
    val path: String,
    val insert: String,
    val kind: WorkspaceKind,
    val directory: Boolean = false,
)

@Single
class FileMentionService(
    private val bindings: WorkspaceBindingStore,
    private val bridge: BridgeRepository,
    private val files: FileRepository,
) {
    private val cache = ConcurrentHashMap<String, CachedScan>()

    suspend fun current(scope: String): FileMentionBinding? {
        val saved = bindings.current(scope)
        if (saved != null && valid(saved)) return saved
        val fallback = fallback() ?: return null
        bindings.set(scope, fallback)
        return fallback
    }

    suspend fun candidates(scope: String): List<FileMentionBinding> {
        val result = mutableListOf<FileMentionBinding>()
        bindings.current(scope)?.let { result += it }
        bridge.endpointsOnce().forEach { endpoint ->
            roots(endpoint).forEach { root -> result += bridgeBinding(endpoint, root) }
        }
        files.selectedTree()?.let { result += localBinding(it) }
        return result.distinctBy { it.key }
    }

    suspend fun bind(scope: String, binding: FileMentionBinding) {
        bindings.set(scope, binding.copy(manual = true))
        cache.clear()
    }

    suspend fun search(scope: String, query: String, limit: Int = MAX_RESULTS): List<FileMentionEntry> {
        val raw = query.trim()
        if (raw.startsWith("/")) return searchAbsolute(raw, limit)
        val binding = current(scope) ?: return emptyList()
        val relative = stripWorkspacePrefix(raw, binding)
        if (relative.isBlank() || relative.endsWith('/')) return browse(binding, relative, limit)
        val entries = searchIn(binding, relative, limit)
        val root = rootEntry(binding)
            ?.takeIf { it.name.contains(relative, ignoreCase = true) }
            ?: return entries
        return (listOf(root) + entries).take(limit)
    }

    private fun rootEntry(binding: FileMentionBinding): FileMentionEntry? {
        val name = when (binding.kind) {
            WorkspaceKind.Bridge -> binding.root.trimEnd('/').substringAfterLast('/')
            WorkspaceKind.Local -> binding.label
        }.ifBlank { binding.label }
        if (name.isBlank()) return null
        return FileMentionEntry(
            id = "root:${binding.key}",
            name = name,
            relative = name,
            path = binding.path,
            insert = name,
            kind = binding.kind,
            directory = true,
        )
    }

    private fun stripWorkspacePrefix(raw: String, binding: FileMentionBinding): String {
        val slash = raw.indexOf('/')
        if (slash <= 0) return raw
        val head = raw.substring(0, slash)
        val names = listOf(binding.label, binding.root.trimEnd('/').substringAfterLast('/'))
        val match = names.any { it.isNotBlank() && it.equals(head, ignoreCase = true) }
        return if (match) raw.substring(slash + 1) else raw
    }

    private suspend fun browse(
        binding: FileMentionBinding,
        raw: String,
        limit: Int,
    ): List<FileMentionEntry> = when (binding.kind) {
        WorkspaceKind.Bridge -> browseBridge(binding, raw, limit)
        WorkspaceKind.Local -> browseLocal(binding, raw, limit)
    }

    private suspend fun browseBridge(
        binding: FileMentionBinding,
        raw: String,
        limit: Int,
    ): List<FileMentionEntry> {
        val endpoint = endpoint(binding) ?: return emptyList()
        val base = normalize(binding.root)
        val dir = resolveBridgePath(endpoint, base, raw) ?: return emptyList()
        val key = "browse:${binding.key}:$dir"
        return cached(key) { listBridge(endpoint, dir, base) }
            .sortedWith(CHILD_ORDER)
            .take(limit)
    }

    private suspend fun resolveBridgePath(
        endpoint: BridgeEndpoint,
        base: String,
        raw: String,
    ): String? {
        var current = base
        val segments = raw.trim('/').split('/').filter { it.isNotBlank() }
        for (segment in segments) {
            val entries = cached("browse:${endpoint.id}:$current") {
                listBridge(endpoint, current, current)
            }
            val match = entries.firstOrNull {
                it.directory && it.name.equals(segment, ignoreCase = true)
            } ?: return null
            current = match.path
        }
        return current
    }

    private suspend fun browseLocal(
        binding: FileMentionBinding,
        raw: String,
        limit: Int,
    ): List<FileMentionEntry> {
        val parent = raw.trim('/')
        return scanLocal(binding).asSequence()
            .filter { parentOf(it.relative).equals(parent, ignoreCase = true) }
            .sortedWith(CHILD_ORDER)
            .take(limit)
            .toList()
    }

    private suspend fun searchIn(
        binding: FileMentionBinding,
        raw: String,
        limit: Int,
    ): List<FileMentionEntry> = when (binding.kind) {
        WorkspaceKind.Bridge -> searchBridge(binding, raw, limit)
        WorkspaceKind.Local -> match(scanLocal(binding), raw, limit)
    }

    private suspend fun searchBridge(
        binding: FileMentionBinding,
        raw: String,
        limit: Int,
    ): List<FileMentionEntry> {
        val endpoint = endpoint(binding) ?: return emptyList()
        val key = "search:${binding.key}:${raw.lowercase()}"
        val defaultRoot = normalize(endpoint.workspaceRoots.firstOrNull() ?: binding.root)
        val entries = cached(key) {
            val term = globTerm(raw)
            val pattern = if (term.isBlank()) "**/*" else "**/*$term*"
            val matches = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                runCatching { bridge.glob(endpoint, pattern, root = binding.root) }.getOrNull()
            }?.matches.orEmpty()
            globEntries(binding, defaultRoot, matches)
        }
        return verifyDirectories(endpoint, match(entries, raw, limit))
    }

    private suspend fun verifyDirectories(
        endpoint: BridgeEndpoint,
        entries: List<FileMentionEntry>,
    ): List<FileMentionEntry> {
        val pending = entries.filter { !it.directory && '.' !in it.name }
        if (pending.isEmpty()) return entries
        val directories = mutableSetOf<String>()
        pending.map { it.path.substringBeforeLast('/', "") }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_VERIFY_PARENTS)
            .forEach { parent ->
                cached("browse:${endpoint.id}:$parent") {
                    listBridge(endpoint, parent, parent)
                }.forEach { child ->
                    if (child.directory) directories += child.path
                }
            }
        if (directories.isEmpty()) return entries
        return entries.map { entry ->
            if (!entry.directory && entry.path in directories) {
                entry.copy(insert = entry.relative, directory = true)
            } else {
                entry
            }
        }
    }

    private fun globEntries(
        binding: FileMentionBinding,
        defaultRoot: String,
        matches: List<String>,
    ): List<FileMentionEntry> {
        if (matches.isEmpty()) return emptyList()
        val base = normalize(binding.root)
        val paths = matches.map { match ->
            when {
                match.startsWith("/") -> normalize(match)
                defaultRoot == "/" -> "/$match"
                else -> "$defaultRoot/$match"
            }
        }.distinct()
        val directories = mutableSetOf<String>()
        paths.forEach { path ->
            var index = path.indexOf('/', 1)
            while (index > 0) {
                directories += path.substring(0, index)
                index = path.indexOf('/', index + 1)
            }
        }
        return paths.mapNotNull { full ->
            val relative = when {
                full == base -> return@mapNotNull null
                base == "/" -> full.trimStart('/')
                full.startsWith("$base/") -> full.removePrefix("$base/")
                else -> full
            }
            if (ignored(relative)) return@mapNotNull null
            val directory = full in directories
            FileMentionEntry(
                id = if (directory) "dir:$full" else "file:$full",
                name = full.substringAfterLast('/'),
                relative = relative,
                path = full,
                insert = if (directory) relative else full,
                kind = WorkspaceKind.Bridge,
                directory = directory,
            )
        }
    }

    private suspend fun searchAbsolute(raw: String, limit: Int): List<FileMentionEntry> {
        val endpoints = bridge.endpointsOnce()
        if (endpoints.isEmpty()) return emptyList()
        val dir = when {
            raw.endsWith('/') -> raw
            raw.lastIndexOf('/') <= 0 -> "/"
            else -> raw.substringBeforeLast('/')
        }
        val endpoint = endpoints.firstOrNull { candidate ->
            roots(candidate).any { contains(it, dir) }
        } ?: endpoints.first()
        if (raw.endsWith('/')) {
            val target = normalize(dir)
            val key = "abs:${endpoint.id}:$target"
            return cached(key) { listBridge(endpoint, target, target) }
                .sortedWith(CHILD_ORDER)
                .take(limit)
        }
        val parent = normalize(dir)
        val key = "abs:${endpoint.id}:$parent"
        val entries = cached(key) { listBridge(endpoint, parent, parent) }
        val target = normalize(raw)
        val exact = entries.firstOrNull { it.path == target && it.directory }
        if (exact != null) {
            val innerKey = "abs:${endpoint.id}:$target"
            return cached(innerKey) { listBridge(endpoint, target, target) }
                .sortedWith(CHILD_ORDER)
                .take(limit)
        }
        return match(entries, raw.substringAfterLast('/'), limit)
    }

    private suspend fun listBridge(
        endpoint: BridgeEndpoint,
        dir: String,
        base: String,
    ): List<FileMentionEntry> {
        val entries = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            runCatching { bridge.list(endpoint, dir, depth = 1) }.getOrNull()
        }.orEmpty()
        val prefix = when (base) {
            "/" -> "/"
            else -> base.trimEnd('/') + "/"
        }
        return entries.mapNotNull { entry ->
            val full = normalize(entry.path)
            val relative = when {
                full.startsWith(prefix) -> full.removePrefix(prefix)
                else -> entry.name
            }
            if (relative.isBlank() || ignored(relative)) return@mapNotNull null
            FileMentionEntry(
                id = if (entry.isDir) "dir:$full" else "file:$full",
                name = entry.name,
                relative = relative,
                path = full,
                insert = if (entry.isDir) relative else full,
                kind = WorkspaceKind.Bridge,
                directory = entry.isDir,
            )
        }
    }

    private suspend fun scanLocal(binding: FileMentionBinding): List<FileMentionEntry> {
        if (files.selectedTree() != binding.treeUri) return emptyList()
        val key = "local:${binding.key}"
        return cached(key) {
            val out = mutableListOf<FileMentionEntry>()
            val label = binding.label.ifBlank { FALLBACK_LABEL }
            collectLocal(binding.treeUri, "", label, out, 0)
            out
        }
    }

    private suspend fun collectLocal(
        uri: String,
        prefix: String,
        label: String,
        out: MutableList<FileMentionEntry>,
        depth: Int,
    ) {
        if (depth > MAX_LOCAL_DEPTH || out.size >= MAX_SCAN_FILES) return
        val entries = quietly { files.list(uri) }.orEmpty()
        entries.sortedBy { it.name }.forEach { entry ->
            if (out.size >= MAX_SCAN_FILES) return
            val relative = if (prefix.isBlank()) entry.name else "$prefix/${entry.name}"
            if (entry.isDirectory) {
                if (!ignoredName(entry.name)) {
                    out += FileMentionEntry(
                        id = "dir:${entry.uri}",
                        name = entry.name,
                        relative = relative,
                        path = "$label/$relative",
                        insert = relative,
                        kind = WorkspaceKind.Local,
                        directory = true,
                    )
                    collectLocal(entry.uri, relative, label, out, depth + 1)
                }
            } else {
                out += FileMentionEntry(
                    id = "file:${entry.uri}",
                    name = entry.name,
                    relative = relative,
                    path = "$label/$relative",
                    insert = entry.uri,
                    kind = WorkspaceKind.Local,
                )
            }
        }
    }

    private fun globTerm(raw: String): String = buildString {
        raw.forEach { char ->
            when {
                char in 'a'..'z' || char in 'A'..'Z' -> {
                    append('[')
                    append(char.lowercaseChar())
                    append(char.uppercaseChar())
                    append(']')
                }
                char in GLOB_SPECIAL -> Unit
                else -> append(char)
            }
        }
    }

    private fun match(entries: List<FileMentionEntry>, query: String, limit: Int): List<FileMentionEntry> {
        val term = query.trim().trim('/')
        if (term.isBlank()) return entries.take(limit)
        return entries.asSequence()
            .filter { it.name.contains(term, ignoreCase = true) || it.relative.contains(term, ignoreCase = true) }
            .sortedWith(
                compareByDescending<FileMentionEntry> { it.name.startsWith(term, ignoreCase = true) }
                    .thenByDescending { it.relative.startsWith(term, ignoreCase = true) }
                    .thenByDescending { it.directory }
                    .thenBy { it.relative.length }
                    .thenBy { it.relative.lowercase() },
            )
            .take(limit)
            .toList()
    }

    private suspend fun cached(key: String, block: suspend () -> List<FileMentionEntry>): List<FileMentionEntry> {
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.at < CACHE_TTL_MS }?.let { return it.entries }
        if (cache.size >= MAX_CACHE_ENTRIES) {
            cache.entries.removeIf { now - it.value.at >= CACHE_TTL_MS }
            if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
        }
        val entries = block()
        cache[key] = CachedScan(entries, System.currentTimeMillis())
        return entries
    }

    private suspend fun endpoint(binding: FileMentionBinding): BridgeEndpoint? =
        bridge.endpointsOnce().firstOrNull { it.id == binding.endpointId }

    private suspend fun fallback(): FileMentionBinding? {
        val active = bridge.activeEndpoint()
        val endpoints = bridge.endpointsOnce()
        val ordered = buildList {
            active?.let { add(it) }
            addAll(endpoints.filter { it.id != active?.id })
        }
        ordered.forEach { endpoint ->
            val root = roots(endpoint).firstOrNull() ?: return@forEach
            return bridgeBinding(endpoint, root)
        }
        val tree = files.selectedTree() ?: return null
        return localBinding(tree)
    }

    private suspend fun valid(binding: FileMentionBinding): Boolean = when (binding.kind) {
        WorkspaceKind.Bridge -> {
            binding.endpointId.isNotBlank() &&
                binding.root.isNotBlank() &&
                bridge.endpointsOnce().any { it.id == binding.endpointId }
        }
        WorkspaceKind.Local -> {
            binding.treeUri.isNotBlank() && binding.treeUri == files.selectedTree()
        }
    }

    private suspend fun roots(endpoint: BridgeEndpoint): List<String> {
        val saved = endpoint.workspaceRoots.filter { it.isNotBlank() }
        if (saved.isNotEmpty()) return saved
        return withTimeoutOrNull(ROOTS_TIMEOUT_MS) {
            quietly { bridge.workspace(endpoint) }?.filter { it.isNotBlank() }
        }.orEmpty()
    }

    private suspend fun bridgeBinding(endpoint: BridgeEndpoint, root: String): FileMentionBinding {
        val path = normalize(root)
        return FileMentionBinding(
            key = "bridge:${endpoint.id}:$path",
            label = endpoint.name,
            kind = WorkspaceKind.Bridge,
            endpointId = endpoint.id,
            root = path,
        )
    }

    private suspend fun localBinding(tree: String): FileMentionBinding {
        val name = quietly { files.treeName() }?.takeIf { it.isNotBlank() } ?: FALLBACK_LABEL
        return FileMentionBinding(
            key = "local:$tree",
            label = name,
            kind = WorkspaceKind.Local,
            treeUri = tree,
        )
    }

    private fun contains(root: String, path: String): Boolean {
        val base = normalize(root)
        return path == base || path.startsWith(if (base == "/") "/" else "$base/")
    }

    private fun parentOf(relative: String): String {
        val path = relative.trim('/')
        return if ('/' in path) path.substringBeforeLast('/') else ""
    }

    private fun normalize(path: String): String {
        val trimmed = path.trim().trimEnd('/')
        return trimmed.ifBlank { "/" }
    }

    private fun ignored(path: String): Boolean =
        path.split('/').any { segment -> ignoredName(segment) }

    private fun ignoredName(name: String): Boolean =
        SKIP_DIRS.any { it.equals(name, ignoreCase = true) }

    private suspend fun <T> quietly(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private data class CachedScan(val entries: List<FileMentionEntry>, val at: Long)

    companion object {
        private const val FALLBACK_LABEL = "Connected folder"
        private const val MAX_RESULTS = 30
        private const val MAX_SCAN_FILES = 2_000
        private const val MAX_LOCAL_DEPTH = 12
        private const val MAX_CACHE_ENTRIES = 32
        private const val MAX_VERIFY_PARENTS = 8
        private const val CACHE_TTL_MS = 30_000L
        private const val SCAN_TIMEOUT_MS = 8_000L
        private const val ROOTS_TIMEOUT_MS = 4_000L
        private const val GLOB_SPECIAL = "*?[]"

        private val CHILD_ORDER = compareByDescending<FileMentionEntry> { it.directory }
            .thenBy { it.name.lowercase() }

        private val SKIP_DIRS = setOf(
            ".git",
            ".gradle",
            ".idea",
            ".cache",
            "build",
            "node_modules",
            "target",
            "dist",
            "out",
        )
    }
}
