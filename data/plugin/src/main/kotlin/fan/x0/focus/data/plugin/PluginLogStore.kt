package fan.x0.focus.data.plugin

import android.content.Context
import fan.x0.focus.plugin.PluginLogger
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Single
class PluginLogStore(@Provided private val context: Context) {
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun logger(pluginId: String): PluginLogger = object : PluginLogger {
        override fun info(message: String) = write(pluginId, "I", message, null)
        override fun warn(message: String, error: Throwable?) = write(pluginId, "W", message, error)
        override fun error(message: String, error: Throwable?) = write(pluginId, "E", message, error)
    }

    fun write(pluginId: String, level: String, message: String, error: Throwable?) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(' ')
            append(level)
            append(' ')
            append(message)
            if (error != null) {
                append(' ')
                append(error.javaClass.simpleName)
                append(": ")
                append(error.message.orEmpty())
                error.stackTrace.take(12).forEach {
                    append("\n  at ")
                    append(it)
                }
            }
            append('\n')
        }
        synchronized(lock) {
            val file = logFile(pluginId)
            file.parentFile?.mkdirs()
            runCatching {
                if (file.length() > MAX_BYTES) {
                    val tail = file.readText().takeLast((MAX_BYTES / 2).toInt())
                    file.writeText(tail)
                }
                file.appendText(line)
            }
        }
    }

    fun read(pluginId: String, maxLines: Int = 400): String = synchronized(lock) {
        val file = logFile(pluginId)
        if (!file.isFile) return ""
        file.readLines().takeLast(maxLines.coerceIn(20, 2000)).joinToString("\n")
    }

    fun clear(pluginId: String) = synchronized(lock) {
        logFile(pluginId).delete()
        Unit
    }

    fun delete(pluginId: String) = synchronized(lock) {
        val dir = File(context.filesDir, "plugins/$pluginId")
        dir.deleteRecursively()
        Unit
    }

    private fun logFile(pluginId: String): File =
        File(context.filesDir, "plugins/$pluginId/plugin.log")

    companion object {
        private const val MAX_BYTES = 128 * 1024L
    }
}
