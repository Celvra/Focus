package fan.x0.focus.hijack.coloros

import android.content.Context
import android.provider.Settings
import fan.x0.focus.hijack.HijackLog
import fan.x0.focus.hijack.HijackReflect
import fan.x0.focus.hijack.HijackTrigger
import fan.x0.focus.hijack.SystemUiMarker
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

internal object ColorOsSystemUiHooks {
    private const val ASSIST_TOUCH_GESTURE_ENABLED = "assist_touch_gesture_enabled"
    private const val QUICK_STEP_CONTRACT = "com.android.systemui.shared.system.QuickStepContract"

    private val NAV_BAR_UTILS = listOf(
        "com.oplus.systemui.navigationbar.utils.NavBarUtils",
        "com.oplus.systemui.navigationbar.NavBarUtils",
        "com.oplusos.systemui.navigationbar.utils.NavBarUtils",
        "com.oplusos.systemui.navigationbar.NavBarUtils",
    )

    private val GESTURE_CLASSES = listOf(
        "com.oplus.systemui.navigationbar.ocrscreen.OplusOcrScreenBusiness",
        "com.oplus.systemui.navigationbar.ocrscreen.OplusOcrScreenServiceHandler",
        "com.oplus.systemui.navigationbar.gesture.otherbusiness.SpeedChassistMainBusiness",
        "com.oplusos.systemui.navigationbar.ocrscreen.OplusOcrScreenBusiness",
        "com.oplusos.systemui.navigationbar.ocrscreen.OplusOcrScreenServiceHandler",
        "com.oplusos.systemui.navigationbar.gesture.otherbusiness.SpeedChassistMainBusiness",
    )

    fun install(module: XposedModule, loader: ClassLoader): Boolean {
        val availability = hookAssistantAvailability(module, loader)
        val gestures = hookGestureLongPress(module, loader)
        return availability || gestures
    }

    private fun hookAssistantAvailability(module: XposedModule, loader: ClassLoader): Boolean {
        var installed = 0
        for (className in NAV_BAR_UTILS) {
            val owner = HijackReflect.findClass(loader, className) ?: continue
            val methods = HijackReflect.methods(owner, "isAssistantAvailable").filter {
                it.parameterTypes.isNotEmpty() &&
                    it.parameterTypes[0] == Context::class.java &&
                    it.returnType == java.lang.Boolean.TYPE
            }
            for (method in methods) {
                runCatching {
                    module.hook(method)
                        .setId("coloros_assistant_available")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept { chain ->
                            val context = chain.getArg(0) as? Context
                                ?: return@intercept chain.proceed()
                            val mode = chain.args.getOrNull(1) as? Int ?: -1
                            if (!assistTouchEnabled(context) || !isGestural(loader, mode)) {
                                return@intercept false
                            }
                            true
                        }
                    installed++
                }.onFailure { HijackLog.error("hook $className.isAssistantAvailable failed", it) }
            }
            if (installed > 0) {
                HijackLog.info("hooked $className.isAssistantAvailable")
                break
            }
        }
        return installed > 0
    }

    private fun hookGestureLongPress(module: XposedModule, loader: ClassLoader): Boolean {
        var installed = 0
        for (className in GESTURE_CLASSES) {
            val owner = HijackReflect.findClass(loader, className) ?: continue
            val methods = HijackReflect.methods(owner, "onLongPressed") +
                HijackReflect.methods(owner, "onLongPress")
            for (method in methods.filter { it.returnType == java.lang.Void.TYPE }) {
                runCatching {
                    module.hook(method)
                        .setId("coloros_gesture_${owner.simpleName}_${method.name}")
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept { chain ->
                            val context = HijackReflect.contextOf(chain.thisObject)
                                ?: HijackReflect.appContext()
                                ?: return@intercept chain.proceed()
                            SystemUiMarker.mark(context)
                            if (HijackTrigger.fire(context, "coloros.gesture.${owner.simpleName}")) {
                                null
                            } else {
                                chain.proceed()
                            }
                        }
                    installed++
                }.onFailure { HijackLog.error("hook $className.$method failed", it) }
            }
        }
        if (installed > 0) HijackLog.info("hooked ColorOS gesture long press hooks=$installed")
        return installed > 0
    }

    private fun assistTouchEnabled(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(context.contentResolver, ASSIST_TOUCH_GESTURE_ENABLED, 1) != 0
    }.getOrDefault(true)

    private fun isGestural(loader: ClassLoader, mode: Int): Boolean {
        val owner = HijackReflect.findClass(loader, QUICK_STEP_CONTRACT) ?: return true
        val method = HijackReflect.methods(owner, "isGesturalMode").firstOrNull {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == Integer.TYPE
        } ?: return true
        return runCatching { method.invoke(null, mode) as? Boolean ?: true }.getOrDefault(true)
    }
}
