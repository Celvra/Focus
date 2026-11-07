package fan.x0.focus.hijack

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class FocusHijackModule : XposedModule() {
    private var systemServerInstalled = false
    private var systemUiInstalled = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        HijackLog.attach(this)
        HijackLog.info(
            "module loaded process=${param.processName} systemServer=${param.isSystemServer} api=$apiVersion",
        )
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        if (systemServerInstalled) return
        systemServerInstalled = true
        HijackLog.info("system server starting")
        RomHijacker.installSystemServer(this, param.classLoader)
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != "com.android.systemui" || systemUiInstalled) return
        systemUiInstalled = true
        HijackLog.info("system ui ready")
        RomHijacker.installSystemUi(this, param.classLoader)
        SystemUiMarker.mark(HijackReflect.appContext())
    }
}
