package fan.x0.focus.hijack

import fan.x0.focus.hijack.coloros.ColorOsSystemServerHooks
import fan.x0.focus.hijack.coloros.ColorOsSystemUiHooks
import fan.x0.focus.hijack.common.CommonSystemUiHooks
import io.github.libxposed.api.XposedModule

internal object RomHijacker {

    fun installSystemServer(module: XposedModule, loader: ClassLoader) {
        SystemContext.install(module, loader)
        if (ColorOsSystemServerHooks.install(module, loader)) {
            HijackLog.info("ColorOS system server hooks installed")
            return
        }
        HijackLog.warn("no system server hijacker for this rom")
    }

    fun installSystemUi(module: XposedModule, loader: ClassLoader) {
        val common = CommonSystemUiHooks.install(module, loader)
        val colorOs = ColorOsSystemUiHooks.install(module, loader)
        HijackLog.info("system ui hooks common=$common colorOs=$colorOs")
    }
}
