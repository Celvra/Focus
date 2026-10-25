package fan.x0.focus.data.assistant

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File

enum class WorkspaceFile(val fileName: String) {
    Soul("SOUL.md"),
    Persona("PERSONA.md"),
    User("USER.md"),
    Agent("AGENT.md"),
    Memory("MEMORY.md"),
    ;

    companion object {
        fun fromKey(key: String): WorkspaceFile? {
            val value = key.trim()
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) ||
                    it.fileName.equals(value, ignoreCase = true) ||
                    it.fileName.removeSuffix(".md").equals(value, ignoreCase = true)
            }
        }

        fun keys(): String = entries.joinToString(", ") { it.name.lowercase() }
    }
}

data class WorkspaceDocument(
    val file: WorkspaceFile,
    val content: String,
)

@Single
class WorkspaceStore(@Provided private val context: Context) {
    private val revisionCount = MutableStateFlow(0)
    private val seedLock = Mutex()

    val revision: StateFlow<Int> = revisionCount.asStateFlow()

    suspend fun readAll(): List<WorkspaceDocument> = withContext(Dispatchers.IO) {
        ensureSeeded()
        WorkspaceFile.entries.map { file ->
            WorkspaceDocument(file, fileFor(file).readText().take(MAX_FILE_CHARS))
        }
    }

    suspend fun read(file: WorkspaceFile): String = withContext(Dispatchers.IO) {
        ensureSeeded()
        fileFor(file).readText().take(MAX_FILE_CHARS)
    }

    suspend fun write(file: WorkspaceFile, content: String) {
        withContext(Dispatchers.IO) {
            ensureSeeded()
            writeFile(file, content.take(MAX_FILE_CHARS))
        }
        revisionCount.update { it + 1 }
    }

    suspend fun append(file: WorkspaceFile, block: String) {
        val text = block.trim()
        if (text.isBlank()) return
        withContext(Dispatchers.IO) {
            ensureSeeded()
            val current = fileFor(file).readText()
            val next = buildString {
                append(current.trimEnd())
                if (isNotEmpty()) append("\n\n")
                append(text)
                append('\n')
            }
            writeFile(file, next.take(MAX_FILE_CHARS))
        }
        revisionCount.update { it + 1 }
    }

    suspend fun appendMemory(fact: String) {
        val text = fact.trim()
        if (text.isBlank()) return
        append(WorkspaceFile.Memory, "- $text")
    }

    suspend fun updateUserProfile(
        name: String? = null,
        tone: String? = null,
        focus: String? = null,
        language: String? = null,
    ) {
        updateFields(
            file = WorkspaceFile.User,
            values = listOf(
                "Name" to name,
                "Focus" to focus,
                "Tone" to tone,
                "Language" to language,
            ),
        )
    }

    suspend fun updateAssistantName(name: String) {
        val value = name.trim().take(MAX_NAME_CHARS)
        if (value.isBlank()) return
        updateFields(WorkspaceFile.Persona, listOf("Name" to value))
    }

    suspend fun userName(): String? = field(WorkspaceFile.User, "Name")

    suspend fun assistantName(): String =
        field(WorkspaceFile.Persona, "Name") ?: DEFAULT_ASSISTANT_NAME

    suspend fun userLanguage(): String? = field(WorkspaceFile.User, "Language")

    suspend fun field(file: WorkspaceFile, label: String): String? = withContext(Dispatchers.IO) {
        ensureSeeded()
        fieldOf(fileFor(file).readText(), label)
    }

    private suspend fun updateFields(
        file: WorkspaceFile,
        values: List<Pair<String, String?>>,
    ) {
        val present = values.filter { !it.second.isNullOrBlank() }
        if (present.isEmpty()) return
        withContext(Dispatchers.IO) {
            ensureSeeded()
            var content = fileFor(file).readText()
            if (content.isBlank()) content = "# ${file.fileName.removeSuffix(".md")}\n"
            present.forEach { (label, value) ->
                val line = "- $label: ${value!!.trim()}"
                val pattern = Regex("(?im)^\\s*[-*]?\\s*${Regex.escape(label)}\\s*:.*$")
                content = if (pattern.containsMatchIn(content)) {
                    pattern.replace(content, line)
                } else {
                    content.trimEnd() + "\n" + line + "\n"
                }
            }
            writeFile(file, content.take(MAX_FILE_CHARS))
        }
        revisionCount.update { it + 1 }
    }

    private suspend fun ensureSeeded() {
        seedLock.withLock {
            val dir = directory()
            if (!dir.exists()) dir.mkdirs()
            WorkspaceFile.entries.forEach { file ->
                val target = dir.resolve(file.fileName)
                if (!target.exists()) target.writeText(defaultContent(file))
            }
        }
    }

    private fun writeFile(file: WorkspaceFile, content: String) {
        val dir = directory()
        if (!dir.exists()) dir.mkdirs()
        val target = fileFor(file)
        val temp = dir.resolve("${file.fileName}.tmp")
        temp.writeText(content)
        if (!temp.renameTo(target)) {
            target.writeText(content)
            temp.delete()
        }
    }

    private fun directory(): File = File(File(context.filesDir, "assistant"), "workspace")

    private fun fileFor(file: WorkspaceFile): File = directory().resolve(file.fileName)

    private fun defaultContent(file: WorkspaceFile): String = when (file) {
        WorkspaceFile.Soul ->
            "# Soul\n\n- Direct, calm, and specific.\n- Short answers unless the user needs detail.\n"
        WorkspaceFile.Persona ->
            "# Persona\n\n- Name: $DEFAULT_ASSISTANT_NAME\n- $DEFAULT_ASSISTANT_NAME is a personal assistant on the user's phone.\n- Helpful first, warm second.\n"
        WorkspaceFile.User ->
            "# User\n\n"
        WorkspaceFile.Agent ->
            "# Agent\n\n- Nothing learned yet.\n"
        WorkspaceFile.Memory ->
            "# Memory\n\n"
    }

    private fun fieldOf(content: String, label: String): String? =
        Regex("(?im)^\\s*[-*]?\\s*${Regex.escape(label)}\\s*:\\s*(.+)$")
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    companion object {
        const val MAX_FILE_CHARS = 48_000
        const val PROMPT_LIMIT = 8_000
        const val DEFAULT_ASSISTANT_NAME = "Focus"
        private const val MAX_NAME_CHARS = 40
    }
}
