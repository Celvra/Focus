package fan.x0.focus.data.plugin.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fan.x0.focus.data.plugin.PluginManager
import fan.x0.focus.data.plugin.PluginRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

class PluginBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> Unit
            else -> return
        }
        val koin = runCatching { KoinPlatform.getKoin() }.getOrNull() ?: return
        val registry = runCatching { koin.get<PluginRegistry>() }.getOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching { koin.get<PluginManager>().restore() }
                registry.bootHandlers().forEach { (plugin, handler) ->
                    runCatching { handler.onBoot() }
                        .onFailure { registry.log().write(plugin.manifest.id, "W", "Boot handler failed", it) }
                    if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                        runCatching { handler.onPackageReplaced() }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
