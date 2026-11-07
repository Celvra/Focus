package fan.x0.focus.hijack

import android.util.Log
import io.github.libxposed.api.XposedModule

internal object HijackLog {
    private const val TAG = "FocusHijack"
    private var module: XposedModule? = null

    fun attach(value: XposedModule) {
        module = value
    }

    fun info(message: String) {
        val value = module
        if (value == null) {
            Log.i(TAG, message)
        } else {
            value.log(Log.INFO, TAG, message)
        }
    }

    fun warn(message: String) {
        val value = module
        if (value == null) {
            Log.w(TAG, message)
        } else {
            value.log(Log.WARN, TAG, message)
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        val value = module
        if (value == null) {
            if (throwable == null) {
                Log.e(TAG, message)
            } else {
                Log.e(TAG, message, throwable)
            }
        } else {
            if (throwable == null) {
                value.log(Log.ERROR, TAG, message)
            } else {
                value.log(Log.ERROR, TAG, message, throwable)
            }
        }
    }
}
