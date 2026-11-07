package fan.x0.focus.hijack

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

internal object HijackMarker {
    const val KEY_SYSTEM = "focus_hijack_system"
    const val KEY_SYSTEM_UI = "focus_hijack_systemui"

    fun markSystem(context: Context?) {
        write(context, KEY_SYSTEM)
    }

    fun markSystemUi(context: Context?) {
        write(context, KEY_SYSTEM_UI)
    }

    private fun write(context: Context?, key: String) {
        val value = context ?: return
        runCatching {
            val bootCount = Settings.Global.getInt(
                value.contentResolver,
                Settings.Global.BOOT_COUNT,
                -1,
            )
            Settings.Secure.putString(
                value.contentResolver,
                key,
                "$bootCount:${SystemClock.elapsedRealtime()}",
            )
            HijackLog.info("marker written key=$key")
        }.onFailure { HijackLog.error("marker failed key=$key", it) }
    }
}
