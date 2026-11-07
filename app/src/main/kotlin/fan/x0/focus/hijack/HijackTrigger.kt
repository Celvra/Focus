package fan.x0.focus.hijack

import android.content.Context
import android.os.SystemClock

internal object HijackTrigger {
    private const val DEBOUNCE_MS = 900L
    private var lastFireAt = 0L

    @Synchronized
    fun fire(context: Context?, source: String): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastFireAt < DEBOUNCE_MS) {
            HijackLog.info("trigger debounced source=$source")
            return true
        }
        lastFireAt = now
        HijackLog.info("trigger source=$source")
        HijackTarget.haptic(context)
        val launched = HijackTarget.launch(context)
        HijackLog.info("trigger source=$source launched=$launched")
        return launched
    }
}
