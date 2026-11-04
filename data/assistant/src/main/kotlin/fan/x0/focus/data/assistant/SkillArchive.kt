package fan.x0.focus.data.assistant

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Single
class SkillArchive(
    private val store: SkillStore,
    private val repository: SkillRepository,
) {

    suspend fun previewMarkdown(text: String): List<SkillCandidate> {
        val parsed = SkillParser.parse(text) ?: return emptyList()
        return listOf(
            SkillCandidate(
                key = "markdown",
                name = parsed.name,
                description = parsed.description,
                body = parsed.body,
            ),
        )
    }

    suspend fun previewZip(bytes: ByteArray): List<SkillCandidate> = withContext(Dispatchers.IO) {
        val files = readZip(bytes) ?: return@withContext emptyList()
        val entryPaths = files.keys.filter { it.substringAfterLast('/').equals(ENTRY, ignoreCase = true) }
        if (entryPaths.isEmpty()) return@withContext emptyList()
        val roots = entryPaths.map { it.substringBeforeLast('/', "") }
            .sortedByDescending { it.length }
        entryPaths.mapNotNull { entryPath ->
            val root = entryPath.substringBeforeLast('/', "")
            val parsed = files[entryPath]?.toString(Charsets.UTF_8)?.let(SkillParser::parse)
                ?: return@mapNotNull null
            val refs = linkedMapOf<String, ByteArray>()
            files.forEach { (path, data) ->
                if (path == entryPath) return@forEach
                if (closestRoot(path, roots) != root) return@forEach
                val relative = if (root.isBlank()) path else path.removePrefix("$root/")
                if (relative.isBlank() || relative == path && root.isNotBlank()) return@forEach
                refs[relative] = data
            }
            SkillCandidate(
                key = entryPath,
                name = parsed.name,
                description = parsed.description,
                body = parsed.body,
                files = refs,
            )
        }.distinctBy { it.name.lowercase(Locale.ROOT) }
    }

    suspend fun install(candidates: List<SkillCandidate>): SkillImportReport = withContext(Dispatchers.IO) {
        var skipped = 0
        val imported = mutableListOf<String>()
        candidates.forEach { candidate ->
            val entry = save(candidate)
            if (entry == null) {
                skipped++
                return@forEach
            }
            candidate.files.forEach { (path, data) ->
                runCatching { store.writeFile(entry.slug, path, data) }
            }
            imported += entry.name
        }
        SkillImportReport(imported = imported, skipped = skipped)
    }

    suspend fun exportZip(): ByteArray = withContext(Dispatchers.IO) {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            store.collect().forEach { skill ->
                skill.files.forEach { (path, bytes) ->
                    zip.putNextEntry(ZipEntry("${skill.slug}/$path"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
        out.toByteArray()
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray>? {
        val files = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val path = normalize(entry.name) ?: continue
                if (files.size >= MAX_ENTRIES) return null
                val data = readEntry(zip, MAX_FILE_BYTES.toInt()) ?: return null
                total += data.size
                if (total > MAX_TOTAL_BYTES) return null
                files[path] = data
            }
        }
        return files
    }

    private fun readEntry(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > max) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun normalize(raw: String): String? {
        val value = raw.replace('\\', '/').trim().trimStart('/')
        if (value.isBlank()) return null
        if (value.length > 240) return null
        val parts = value.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return null
        if (value.contains(':')) return null
        return parts.joinToString("/")
    }

    private fun closestRoot(path: String, roots: List<String>): String? =
        roots.firstOrNull { it.isBlank() || path.startsWith("$it/") }

    private suspend fun save(candidate: SkillCandidate): SkillEntry? = try {
        repository.save(
            SkillDraft(
                name = candidate.name,
                description = candidate.description,
                body = candidate.body,
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    companion object {
        private const val ENTRY = "SKILL.md"
        private const val MAX_ENTRIES = 2000
        private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024
        private const val MAX_FILE_BYTES = 1L * 1024 * 1024
    }
}
