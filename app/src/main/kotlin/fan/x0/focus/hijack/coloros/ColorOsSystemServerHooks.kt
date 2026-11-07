package fan.x0.focus.hijack.coloros

import android.os.Message
import fan.x0.focus.hijack.HijackLog
import fan.x0.focus.hijack.HijackMarker
import fan.x0.focus.hijack.HijackReflect
import fan.x0.focus.hijack.HijackTrigger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

internal object ColorOsSystemServerHooks {
    private const val EXT = "com.android.server.policy.PhoneWindowManagerExtImpl"
    private const val SPEECH_HANDLER =
        "com.android.server.policy.PhoneWindowManagerExtImpl\$OplusSpeechHandler"
    private const val PHONE_WINDOW_MANAGER = "com.android.server.policy.PhoneWindowManager"
    private const val MSG_POWER_LONG_PRESS_FOR_SPEECH = 0x3F3

    private var systemMarked = false

    fun install(module: XposedModule, loader: ClassLoader): Boolean {
        val speech = hookStartSpeech(module, loader)
        val handler = hookSpeechHandler(module, loader)
        if (speech || handler) return true
        return hookPowerLongPress(module, loader)
    }

    private fun hookStartSpeech(module: XposedModule, loader: ClassLoader): Boolean {
        val owner = HijackReflect.findClass(loader, EXT) ?: return false
        val methods = HijackReflect.methods(owner, "startSpeech").filter {
            it.parameterTypes.size == 3 &&
                it.parameterTypes[0] == Integer.TYPE &&
                it.parameterTypes[1] == Integer.TYPE &&
                it.parameterTypes[2] == java.lang.Long.TYPE
        }
        if (methods.isEmpty()) return false
        var installed = 0
        for (method in methods) {
            runCatching {
                module.hook(method)
                    .setId("coloros_start_speech")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val context = HijackReflect.contextOf(chain.thisObject)
                        if (context == null) {
                            HijackLog.warn("startSpeech fallback: no context")
                            return@intercept chain.proceed()
                        }
                        markSystem(context)
                        val source = chain.getArg(1)
                        if (HijackTrigger.fire(context, "coloros.startSpeech source=$source")) {
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                installed++
            }.onFailure { HijackLog.error("hook ${EXT}.startSpeech failed", it) }
        }
        if (installed > 0) HijackLog.info("hooked PhoneWindowManagerExtImpl.startSpeech")
        return installed > 0
    }

    private fun hookSpeechHandler(module: XposedModule, loader: ClassLoader): Boolean {
        val owner = HijackReflect.findClass(loader, SPEECH_HANDLER) ?: return false
        val methods = HijackReflect.methods(owner, "handleMessage").filter {
            it.parameterTypes.size == 1 && it.parameterTypes[0] == Message::class.java
        }
        if (methods.isEmpty()) return false
        var installed = 0
        for (method in methods) {
            runCatching {
                module.hook(method)
                    .setId("coloros_speech_handler")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val message = chain.getArg(0) as? Message
                        if (message?.what != MSG_POWER_LONG_PRESS_FOR_SPEECH) {
                            return@intercept chain.proceed()
                        }
                        val context = HijackReflect.contextOf(chain.thisObject)
                        if (context == null) {
                            HijackLog.warn("speech handler fallback: no context")
                            return@intercept chain.proceed()
                        }
                        markSystem(context)
                        if (HijackTrigger.fire(context, "coloros.speechHandler")) {
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                installed++
            }.onFailure { HijackLog.error("hook OplusSpeechHandler failed", it) }
        }
        if (installed > 0) HijackLog.info("hooked OplusSpeechHandler.handleMessage")
        return installed > 0
    }

    private fun hookPowerLongPress(module: XposedModule, loader: ClassLoader): Boolean {
        val owner = HijackReflect.findClass(loader, PHONE_WINDOW_MANAGER) ?: return false
        val methods = HijackReflect.methods(owner, "powerLongPress").filter {
            it.parameterTypes.isEmpty() ||
                (it.parameterTypes.size == 1 && it.parameterTypes[0] == Integer.TYPE)
        }
        if (methods.isEmpty()) return false
        var installed = 0
        for (method in methods) {
            runCatching {
                module.hook(method)
                    .setId("coloros_power_long_press")
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept { chain ->
                        val context = HijackReflect.contextOf(chain.thisObject)
                        if (context == null) {
                            HijackLog.warn("powerLongPress fallback: no context")
                            return@intercept chain.proceed()
                        }
                        markSystem(context)
                        if (HijackTrigger.fire(context, "coloros.powerLongPress")) {
                            null
                        } else {
                            chain.proceed()
                        }
                    }
                installed++
            }.onFailure { HijackLog.error("hook PhoneWindowManager.powerLongPress failed", it) }
        }
        if (installed > 0) HijackLog.info("hooked PhoneWindowManager.powerLongPress")
        return installed > 0
    }

    private fun markSystem(context: android.content.Context) {
        if (systemMarked) return
        systemMarked = true
        HijackMarker.markSystem(context)
    }
}
