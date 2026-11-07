package fan.x0.focus.data.plugin

import android.content.Context
import android.net.Uri
import fan.x0.focus.plugin.PluginApi
import fan.x0.focus.plugin.PluginManifest
import fan.x0.focus.plugin.PluginRuntimeKind
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

class PluginInstallException(message: String) : Exception(message)

@Single
class PluginInstaller(
    @Provided private val context: Context,
    private val store: PluginStore,
    private val logs: PluginLogStore,
    private val http: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun installFromUri(uri: Uri, source: String): InstalledPlugin = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw PluginInstallException("Cannot open plugin file")
        input.use { installStream(it, source) }
    }

    suspend fun installFromUrl(url: String, sha256: String? = null): InstalledPlugin =
        withContext(Dispatchers.IO) {
            val response = http.get(url.trim())
            if (response.status.value !in 200..299) {
                throw PluginInstallException("Download failed with HTTP ${response.status.value}")
            }
            val bytes = response.bodyAsBytes()
            if (bytes.size > MAX_BYTES) throw PluginInstallException("Plugin is larger than 64 MB")
            if (sha256 != null && !sha256.isBlank() && !sha256.equals(sha256(bytes), ignoreCase = true)) {
                throw PluginInstallException("Plugin checksum mismatch")
            }
            installBytes(bytes, "url:$url")
        }

    suspend fun installBytes(bytes: ByteArray, source: String): InstalledPlugin = withContext(Dispatchers.IO) {
        if (bytes.size > MAX_BYTES) throw PluginInstallException("Plugin is larger than 64 MB")
        val temp = File(context.cacheDir, "plugin-${System.currentTimeMillis()}.apk")
        try {
            temp.writeBytes(bytes)
            installFile(temp, source)
        } finally {
            temp.delete()
        }
    }

    private suspend fun installStream(input: InputStream, source: String): InstalledPlugin {
        val temp = File(context.cacheDir, "plugin-${System.currentTimeMillis()}.apk")
        try {
            temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BYTES) throw PluginInstallException("Plugin is larger than 64 MB")
                    output.write(buffer, 0, read)
                }
            }
            return installFile(temp, source)
        } finally {
            temp.delete()
        }
    }

    private suspend fun installFile(file: File, source: String): InstalledPlugin {
        val manifest = readManifest(file)
        validate(manifest)
        val existing = store.get(manifest.id)
        val targetDir = File(context.filesDir, "plugins/${manifest.id}/${manifest.versionCode}")
        targetDir.mkdirs()
        val target = File(targetDir, "plugin.apk")
        if (target.exists()) target.setWritable(true)
        file.copyTo(target, overwrite = true)
        target.setReadOnly()
        store.save(
            manifest = manifest,
            source = source,
            enabled = existing?.enabled ?: true,
            capabilities = existing?.capabilities ?: emptySet(),
        )
        logs.write(
            manifest.id,
            "I",
            "Installed ${manifest.name} ${manifest.version} from $source",
            null,
        )
        return store.get(manifest.id) ?: throw PluginInstallException("Plugin was not stored")
    }

    private fun readManifest(file: File): PluginManifest {
        if (!file.isFile) throw PluginInstallException("Plugin file is missing")
        return ZipFile(file).use { zip ->
            val entry = zip.getEntry(PluginApi.MANIFEST_ENTRY)
                ?: throw PluginInstallException("assets/focus-plugin.json is missing")
            val raw = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            val manifest = runCatching { json.decodeFromString(PluginManifest.serializer(), raw) }
                .getOrElse { throw PluginInstallException("Invalid plugin manifest: ${it.message}") }
            when (manifest.runtime) {
                PluginRuntimeKind.SCRIPT -> {
                    if (zip.getEntry("assets/${manifest.entry}") == null && zip.getEntry(manifest.entry) == null) {
                        throw PluginInstallException("${manifest.entry} is missing")
                    }
                }
                else -> {
                    if (zip.getEntry("classes.dex") == null && zip.getEntry("classes2.dex") == null) {
                        throw PluginInstallException("classes.dex is missing")
                    }
                }
            }
            val total = zip.entries().asSequence().sumOf { it.size.coerceAtLeast(0) }
            if (total > MAX_UNCOMPRESSED) throw PluginInstallException("Plugin content is larger than 256 MB")
            manifest
        }
    }

    private suspend fun validate(manifest: PluginManifest) {
        if (!manifest.id.matches(ID_PATTERN)) {
            throw PluginInstallException("Plugin id must be lowercase letters, digits and underscores")
        }
        if (manifest.id.contains(PluginApi.TOOL_SEPARATOR)) {
            throw PluginInstallException("Plugin id cannot contain double underscores")
        }
        if (manifest.name.isBlank() || manifest.version.isBlank()) {
            throw PluginInstallException("Plugin name and version are required")
        }
        if (manifest.versionCode <= 0) throw PluginInstallException("versionCode must be positive")
        if (manifest.runtime == PluginRuntimeKind.DEX && manifest.mainClass.isBlank()) {
            throw PluginInstallException("mainClass is required for a DEX plugin")
        }
        if (manifest.runtime !in setOf(PluginRuntimeKind.DEX, PluginRuntimeKind.SCRIPT)) {
            throw PluginInstallException("Unknown plugin runtime: ${manifest.runtime}")
        }
        if (manifest.apiVersion != PluginApi.VERSION) {
            throw PluginInstallException("Plugin API ${manifest.apiVersion} is not supported")
        }
        manifest.tools.forEach { tool ->
            if (!tool.name.matches(TOOL_PATTERN)) {
                throw PluginInstallException("Invalid tool name: ${tool.name}")
            }
            if (manifest.id.length + PluginApi.TOOL_SEPARATOR.length + tool.name.length > 64) {
                throw PluginInstallException("Tool name is too long: ${tool.name}")
            }
        }
        val host = hostVersion()
        if (compareVersion(host, manifest.minHostVersion) < 0) {
            throw PluginInstallException("Plugin requires Focus $host or newer")
        }
    }

    private fun hostVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("0.0.0")

    private fun compareVersion(left: String, right: String): Int {
        val a = left.split('.').map { it.takeWhile { char -> char.isDigit() }.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.takeWhile { char -> char.isDigit() }.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(a.size, b.size)) {
            val diff = (a.getOrNull(index) ?: 0) - (b.getOrNull(index) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        private const val MAX_BYTES = 64L * 1024 * 1024
        private const val MAX_UNCOMPRESSED = 256L * 1024 * 1024
        private val ID_PATTERN = Regex("^[a-z][a-z0-9_]{1,40}$")
        private val TOOL_PATTERN = Regex("^[a-zA-Z0-9_-]{1,40}$")
    }
}
