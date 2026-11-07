package fan.x0.focus.hijack

import android.content.Context
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

internal object SystemContext {
    @Volatile
    private var value: Context? = null

    fun get(): Context? = value

    fun install(module: XposedModule, loader: ClassLoader) {
        val owner = HijackReflect.findClass(loader, "com.android.server.SystemServer") ?: return
        val method = HijackReflect.methods(owner, "createSystemContext").firstOrNull() ?: return
        runCatching {
            module.hook(method)
                .setId("system_context")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept { chain ->
                    val result = chain.proceed()
                    val context = HijackReflect.field(chain.thisObject, "mSystemContext") as? Context
                    if (context != null) {
                        value = context
                        HijackMarker.markSystem(context)
                        HijackLog.info("system context captured")
                    }
                    result
                }
            HijackLog.info("hooked SystemServer.createSystemContext")
        }.onFailure { HijackLog.error("hook createSystemContext failed", it) }
    }
}
