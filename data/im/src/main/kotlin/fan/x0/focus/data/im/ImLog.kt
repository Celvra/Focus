package fan.x0.focus.data.im

import android.util.Log
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ImLog {
    private const val MAX_LINES = 300
    private const val MAX_LINE_LENGTH = 600
    private val buffer = ArrayDeque<String>()
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun d(tag: String, message: String) {
        write(Log.DEBUG, "D", tag, message, null)
    }

    fun i(tag: String, message: String) {
        write(Log.INFO, "I", tag, message, null)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        write(Log.WARN, "W", tag, message, error)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        write(Log.ERROR, "E", tag, message, error)
    }

    fun snapshot(): List<String> = synchronized(buffer) { buffer.toList() }

    fun clear() {
        synchronized(buffer) { buffer.clear() }
    }

    private fun write(priority: Int, level: String, tag: String, message: String, error: Throwable?) {
        Log.println(priority, tag, message)
        error?.let { Log.println(priority, tag, Log.getStackTraceString(it)) }
        val suffix = error?.let { " | ${it.javaClass.simpleName}: ${it.message.orEmpty()}" }.orEmpty()
        val line = "${LocalTime.now().format(clock)} $level/$tag: $message$suffix"
        synchronized(buffer) {
            buffer.addLast(line.take(MAX_LINE_LENGTH))
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
    }
}
