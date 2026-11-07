package fan.x0.focus.hijack.common

import android.os.Bundle
import fan.x0.focus.hijack.HijackLog
import fan.x0.focus.hijack.HijackReflect
import fan.x0.focus.hijack.HijackTrigger
import fan.x0.focus.hijack.SystemUiMarker
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

internal object CommonSystemUiHooks {
    private const val ASSIST_MANAGER = "com.android.systemui.assist.AssistManager"

    fun install(module: XposedModule, loader: ClassLoader): Boolean {
        val owner = HijackReflect.findClass(loader, ASSIST_MANAGER) ?: return false
        val methods = (HijackReflect.methods(owner, "startAssist") +
            HijackReflect.methods(owner, "startAssist\$1"))
            .filter { it.parameterTypes.size == 1 && it.parameterTypes[0] == Bundle::class.java }
        if (methods.isEmpty()) {
            HijackLog.warn("AssistManager.startAssist not found")
            return false
        }
        var installed = 0
        for (method in methods) {
            runCatching {
                module.hook(method)
                    .setId("assist_manager_${method.name}")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val context = HijackReflect.contextOf(chain.thisObject)
                        SystemUiMarker.mark(context)
                        val bundle = chain.getArg(0) as? Bundle
                        val type = bundle?.getInt("invocation_type", -1) ?: -1
                        if (HijackTrigger.fire(context, "assist.startAssist type=$type")) {
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                installed++
            }.onFailure { HijackLog.error("hook AssistManager.${method.name} failed", it) }
        }
        if (installed > 0) HijackLog.info("hooked AssistManager methods=$installed")
        return installed > 0
    }
}
