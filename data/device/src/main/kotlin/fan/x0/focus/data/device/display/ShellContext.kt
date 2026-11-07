package fan.x0.focus.data.device.display

import android.content.Context
import android.content.ContextWrapper
import android.os.Build

internal fun shellContext(base: Context): Context {
    val packageContext = runCatching {
        base.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY)
    }.getOrDefault(base)
    val wrapper = object : ContextWrapper(packageContext) {
        override fun getApplicationContext(): Context = this

        override fun getOpPackageName(): String = "com.android.shell"
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ShellContextV31(wrapper)
    } else {
        wrapper
    }
}
