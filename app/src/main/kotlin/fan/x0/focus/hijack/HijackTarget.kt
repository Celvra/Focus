package fan.x0.focus.hijack

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

internal object HijackTarget {
    const val PACKAGE = "fan.x0.focus"
    private const val ACTIVITY = "fan.x0.focus.AssistantEntryActivity"

    fun launch(context: Context?): Boolean {
        val value = context ?: run {
            HijackLog.warn("launch skipped: no context")
            return false
        }
        val intent = Intent(Intent.ACTION_ASSIST).apply {
            component = ComponentName(PACKAGE, ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return runCatching {
            value.startActivity(intent)
            HijackLog.info("assistant entry started")
            true
        }.getOrElse {
            HijackLog.error("assistant entry start failed", it)
            false
        }
    }

    fun haptic(context: Context?) {
        val value = context ?: return
        runCatching {
            val vibrator = value.getSystemService(Vibrator::class.java) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20L)
            }
        }.onFailure { HijackLog.warn("haptic failed: ${it.message}") }
    }
}
