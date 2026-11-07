package fan.x0.focus.data.plugin

import fan.x0.focus.plugin.PluginManifest
import fan.x0.focus.plugin.PluginRuntimeKind
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Single
class PluginCreator(
    private val installer: PluginInstaller,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    suspend fun create(manifest: PluginManifest, mainJs: String): InstalledPlugin {
        if (manifest.runtime != PluginRuntimeKind.SCRIPT) {
            throw PluginInstallException("Only script plugins can be created in the app")
        }
        val bytes = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                zip.putNextEntry(ZipEntry("assets/focus-plugin.json"))
                zip.write(json.encodeToString(PluginManifest.serializer(), manifest).toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("assets/${manifest.entry}"))
                zip.write(mainJs.toByteArray())
                zip.closeEntry()
            }
            buffer.toByteArray()
        }
        return installer.installBytes(bytes, "created")
    }
}
