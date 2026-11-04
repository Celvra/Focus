package fan.x0.focus.data.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.util.Locale

@Single
class SkillStore(@Provided private val context: Context) {
    private fun root(): File = File(context.filesDir, "assistant/skills")

    suspend fun list(): List<SkillEntry> = withContext(Dispatchers.IO) {
        val root = root()
        if (!root.isDirectory) return@withContext emptyList()
        root.listFiles().orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { readEntry(it) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    suspend fun read(slug: String): SkillEntry? = withContext(Dispatchers.IO) {
        val dir = directory(slug) ?: return@withContext null
        readEntry(dir)
    }

    suspend fun readContent(slug: String): SkillContent? = withContext(Dispatchers.IO) {
        val dir = directory(slug) ?: return@withContext null
        val entry = readEntry(dir) ?: return@withContext null
        val raw = readLimited(File(dir, ENTRY), MAX_ENTRY_CHARS)
        val parsed = SkillParser.parse(raw) ?: return@withContext null
        SkillContent(entry = entry, body = parsed.body, files = listFiles(dir))
    }

    suspend fun save(draft: SkillDraft): SkillEntry = withContext(Dispatchers.IO) {
        val name = SkillParser.oneLine(draft.name, SkillParser.MAX_NAME)
        val description = SkillParser.oneLine(draft.description, SkillParser.MAX_DESCRIPTION)
        require(name.isNotBlank()) { "name is required" }
        require(description.isNotBlank()) { "description is required" }
        val slug = resolveSlug(draft.slug, name)
        val duplicate = root().listFiles().orEmpty().firstOrNull { dir ->
            dir.isDirectory && dir.name != slug &&
                readEntry(dir)?.name.equals(name, ignoreCase = true)
        }
        require(duplicate == null) { "A skill with this name already exists" }
        val dir = File(root(), slug)
        if (dir.exists() && !dir.isDirectory) error("skill path is not a directory")
        if (!dir.isDirectory && !dir.mkdirs()) error("cannot create skill directory")
        File(dir, ENTRY).writeText(SkillParser.render(name, description, draft.body))
        SkillEntry(
            id = id(slug),
            name = name,
            description = description,
            source = SkillSource.App,
            enabled = draft.enabled,
            location = "App",
            slug = slug,
        )
    }

    suspend fun delete(slug: String): Boolean = withContext(Dispatchers.IO) {
        val dir = directory(slug) ?: return@withContext false
        dir.deleteRecursively()
    }

    suspend fun writeFile(slug: String, relative: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val dir = directory(slug) ?: error("skill not found")
        val path = safeRelative(relative) ?: error("invalid file path")
        val file = File(dir, path).canonicalFile
        if (!file.path.startsWith(dir.canonicalPath + File.separator)) error("invalid file path")
        val parent = file.parentFile ?: error("invalid file path")
        if (!parent.isDirectory && !parent.mkdirs()) error("cannot create directory")
        file.writeBytes(bytes)
    }

    suspend fun readFile(slug: String, relative: String): String? = withContext(Dispatchers.IO) {
        val dir = directory(slug) ?: return@withContext null
        val path = safeRelative(relative) ?: return@withContext null
        val file = File(dir, path).canonicalFile
        if (!file.path.startsWith(dir.canonicalPath + File.separator)) return@withContext null
        if (!file.isFile) return@withContext null
        readLimited(file, MAX_FILE_CHARS)
    }

    internal suspend fun collect(): List<AppSkillFiles> = withContext(Dispatchers.IO) {
        val root = root()
        if (!root.isDirectory) return@withContext emptyList()
        root.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, ENTRY).isFile }
            .mapNotNull { dir ->
                val files = linkedMapOf<String, ByteArray>()
                collectFiles(dir, "", files)
                if (files.isEmpty()) null else AppSkillFiles(slug = dir.name, files = files)
            }
    }

    private fun readEntry(dir: File): SkillEntry? {
        val file = File(dir, ENTRY)
        if (!file.isFile) return null
        val parsed = SkillParser.parse(readLimited(file, MAX_ENTRY_CHARS)) ?: return null
        return SkillEntry(
            id = id(dir.name),
            name = parsed.name,
            description = parsed.description,
            source = SkillSource.App,
            location = "App",
            slug = dir.name,
        )
    }

    private fun resolveSlug(draftSlug: String, name: String): String {
        safeSlug(draftSlug)?.let { return it }
        val root = root()
        val existing = root.listFiles().orEmpty().firstOrNull { dir ->
            dir.isDirectory && readEntry(dir)?.name.equals(name, ignoreCase = true)
        }
        if (existing != null) return existing.name
        val base = SkillParser.slug(name)
        var candidate = base
        var suffix = 2
        while (File(root, candidate).exists()) {
            candidate = "$base-$suffix"
            suffix++
        }
        return candidate
    }

    private fun directory(slug: String): File? {
        val safe = safeSlug(slug) ?: return null
        val root = root()
        val dir = File(root, safe)
        if (!dir.isDirectory) return null
        return dir
    }

    private fun safeSlug(value: String): String? {
        val slug = value.trim()
        if (slug.isBlank() || slug.length > 80) return null
        if (slug.any { it !in 'a'..'z' && it !in 'A'..'Z' && it !in '0'..'9' && it != '-' && it != '_' }) return null
        return slug
    }

    private fun safeRelative(value: String): String? {
        val normalized = value.replace('\\', '/').trim().trimStart('/')
        if (normalized.isBlank()) return null
        val parts = normalized.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun id(slug: String): String = "app:$slug"

    private fun listFiles(dir: File): List<String> {
        val files = mutableListOf<String>()
        collectNames(dir, "", files, 0)
        return files.filterNot { it == ENTRY }
    }

    private fun collectNames(
        dir: File,
        prefix: String,
        out: MutableList<String>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        dir.listFiles().orEmpty().sortedBy { it.name }.forEach { file ->
            if (out.size >= MAX_FILES) return
            val relative = if (prefix.isBlank()) file.name else "$prefix/${file.name}"
            if (file.isDirectory) {
                collectNames(file, relative, out, depth + 1)
            } else if (file.isFile) {
                out += relative
            }
        }
    }

    private fun collectFiles(
        dir: File,
        prefix: String,
        out: MutableMap<String, ByteArray>,
        depth: Int = 0,
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        dir.listFiles().orEmpty().sortedBy { it.name }.forEach { file ->
            if (out.size >= MAX_FILES) return
            val relative = if (prefix.isBlank()) file.name else "$prefix/${file.name}"
            if (file.isDirectory) {
                collectFiles(file, relative, out, depth + 1)
            } else if (file.isFile && file.length() <= MAX_FILE_BYTES) {
                out[relative] = file.readBytes()
            }
        }
    }

    private fun readLimited(file: File, maxChars: Int): String {
        if (!file.isFile) return ""
        return file.bufferedReader().use { reader ->
            val buffer = CharArray(maxChars)
            var total = 0
            while (total < maxChars) {
                val read = reader.read(buffer, total, maxChars - total)
                if (read < 0) break
                total += read
            }
            String(buffer, 0, total)
        }
    }

    companion object {
        private const val ENTRY = "SKILL.md"
        private const val MAX_ENTRY_CHARS = 120_000
        private const val MAX_FILE_CHARS = 60_000
        private const val MAX_FILE_BYTES = 1_048_576L
        private const val MAX_FILES = 100
        private const val MAX_DEPTH = 4
    }
}
