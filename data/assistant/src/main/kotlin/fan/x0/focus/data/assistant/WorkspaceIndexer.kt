package fan.x0.focus.data.assistant

import fan.x0.focus.data.bridge.BridgeEndpoint
import fan.x0.focus.data.bridge.BridgeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koin.core.annotation.Single
import java.security.MessageDigest

data class WorkspaceIndexStatus(
    val root: String,
    val files: Int,
    val bytes: Long,
    val updatedAt: Long,
    val endpoint: String,
)

data class WorkspaceIndexResult(
    val root: String,
    val indexed: Int,
    val skipped: Int,
    val truncated: Boolean,
)

data class WorkspaceSearchHit(
    val path: String,
    val line: Int,
    val text: String,
)

@Single
class WorkspaceIndexer(
    private val bridge: BridgeRepository,
    private val store: FileIndexStore,
) {
    suspend fun index(force: Boolean = false): WorkspaceIndexResult {
        val endpoint = bridge.activeEndpoint() ?: error("Pair a computer before indexing a workspace")
        val roots = bridge.workspace(endpoint).filter { it.isNotBlank() }
        if (roots.isEmpty()) error("Set at least one workspace folder on ${endpoint.name} first")
        val root = roots.first()
        val existing = if (force) emptyMap() else store.existing(root)
        val paths = bridge.glob(endpoint, pattern = "**/*", root = root).matches
            .filterNot { it.endsWith("/") }
            .distinct()
            .take(MAX_FILES)
        val truncated = paths.size >= MAX_FILES
        var indexed = 0
        var skipped = 0
        val indexedCount = java.util.concurrent.atomic.AtomicInteger()
        val skippedCount = java.util.concurrent.atomic.AtomicInteger()
        val files = coroutineScope {
            val semaphore = Semaphore(4)
            paths.map { path ->
                async {
                    semaphore.withPermit {
                        val relative = relative(root, path)
                        val previous = existing[relative]
                        val result = runCatching {
                            bridge.read(endpoint, path, offset = 1, limit = MAX_LINES)
                        }.getOrNull() ?: return@withPermit null
                        if (result.size > MAX_FILE_BYTES) return@withPermit null
                        val content = stripLineNumbers(result.content.ifBlank { result.lines })
                        if (content.contains('\u0000')) return@withPermit null
                        val hash = sha256(content)
                        if (!force && previous?.contentHash == hash) {
                            skippedCount.incrementAndGet()
                            return@withPermit previous
                        }
                        indexedCount.incrementAndGet()
                        IndexedFile(
                            root = root,
                            path = relative,
                            size = result.size,
                            modifiedAt = System.currentTimeMillis(),
                            contentHash = hash,
                            content = content.take(MAX_FILE_CHARS),
                            language = languageOf(relative),
                            summary = summarize(content),
                            indexedAt = System.currentTimeMillis(),
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }
        indexed = indexedCount.get()
        skipped = skippedCount.get()
        store.replace(root, files)
        return WorkspaceIndexResult(root, indexed, skipped, truncated)
    }

    suspend fun status(): WorkspaceIndexStatus {
        val endpoint = bridge.activeEndpoint() ?: error("Pair a computer first")
        val roots = bridge.workspace(endpoint).filter { it.isNotBlank() }
        val root = roots.firstOrNull().orEmpty()
        val files = store.all(root.ifBlank { null })
        return WorkspaceIndexStatus(
            root = root,
            files = files.size,
            bytes = files.sumOf { it.size },
            updatedAt = files.maxOfOrNull { it.indexedAt } ?: 0,
            endpoint = endpoint.name,
        )
    }

    suspend fun search(
        query: String,
        regex: Boolean = false,
        glob: String = "",
        caseSensitive: Boolean = false,
        limit: Int = 80,
    ): List<WorkspaceSearchHit> {
        val text = query.trim()
        if (text.isBlank()) return emptyList()
        val flags = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val pattern = if (regex) text else Regex.escape(text)
        val compiled = runCatching { Regex(pattern, flags) }.getOrElse { error("Bad search pattern: ${it.message}") }
        val globRegex = glob.takeIf { it.isNotBlank() }?.let { Regex(globToRegex(it), flags) }
        val hits = mutableListOf<WorkspaceSearchHit>()
        store.all().forEach { file ->
            if (globRegex != null && !globRegex.matches(file.path)) return@forEach
            if (compiled.containsMatchIn(file.path)) {
                hits += WorkspaceSearchHit(file.path, 0, file.path)
            }
            file.content.lineSequence().forEachIndexed { index, line ->
                if (compiled.containsMatchIn(line)) {
                    hits += WorkspaceSearchHit(file.path, index + 1, line.trim().take(300))
                }
            }
            if (hits.size >= limit) return hits.take(limit)
        }
        return hits.take(limit)
    }

    suspend fun files(glob: String = "", limit: Int = 200): List<String> {
        val regex = glob.takeIf { it.isNotBlank() }?.let { Regex(globToRegex(it), setOf(RegexOption.IGNORE_CASE)) }
        return store.all()
            .map { it.path }
            .filter { regex == null || regex.matches(it) }
            .sorted()
            .take(limit)
    }

    suspend fun clear() = store.clear()

    suspend fun count(): Int = store.count()

    private fun relative(root: String, path: String): String =
        path.removePrefix(root).trimStart('/')

    private fun stripLineNumbers(text: String): String =
        text.lineSequence().joinToString("\n") { line ->
            line.replaceFirst(Regex("^\\s*\\d+\\s"), "")
        }

    private fun summarize(content: String): String =
        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(4)
            .joinToString(" ")
            .take(300)

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun languageOf(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "rs" -> "rust"
        "py" -> "python"
        "js", "mjs", "cjs" -> "javascript"
        "ts", "tsx" -> "typescript"
        "c", "h" -> "c"
        "cpp", "cc", "hpp" -> "cpp"
        "go" -> "go"
        "rb" -> "ruby"
        "sh", "bash" -> "shell"
        "md" -> "markdown"
        "json" -> "json"
        "xml" -> "xml"
        "gradle" -> "gradle"
        "toml" -> "toml"
        "yaml", "yml" -> "yaml"
        else -> null
    }

    private fun globToRegex(pattern: String): String {
        val builder = StringBuilder("^")
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when (c) {
                '*' -> {
                    if (i + 1 < pattern.length && pattern[i + 1] == '*') {
                        builder.append(".*")
                        i++
                    } else {
                        builder.append("[^/]*")
                    }
                }
                '?' -> builder.append("[^/]")
                '.', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' -> builder.append('\\').append(c)
                else -> builder.append(c)
            }
            i++
        }
        builder.append('$')
        return builder.toString()
    }

    companion object {
        private const val MAX_FILES = 1500
        private const val MAX_LINES = 2000
        private const val MAX_FILE_BYTES = 256 * 1024L
        private const val MAX_FILE_CHARS = 250_000
    }
}
