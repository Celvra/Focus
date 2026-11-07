package fan.x0.focus.data.plugin

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.util.concurrent.TimeUnit

@Single
class PluginManager(
    @Provided private val context: Context,
    private val registry: PluginRegistry,
    private val installer: PluginInstaller,
    private val store: PluginStore,
    private val settings: PluginSettings,
    private val marketplace: PluginMarketplace,
) {
    val statuses: StateFlow<Map<String, PluginStatus>> get() = registry.statuses

    suspend fun loadAll() = withContext(Dispatchers.IO) {
        registry.loadAll()
        registry.statuses.value.values
            .filter { it.enabled && it.state == PluginRuntimeState.Loaded }
            .forEach { schedule(it) }
    }

    suspend fun restore() = withContext(Dispatchers.IO) {
        registry.loadAll()
        registry.statuses.value.values
            .filter { it.enabled && it.state == PluginRuntimeState.Loaded }
            .forEach { schedule(it) }
    }

    suspend fun installUri(uri: Uri): InstalledPlugin = withContext(Dispatchers.IO) {
        val installed = installer.installFromUri(uri, "file:$uri")
        afterInstall(installed)
        installed
    }

    suspend fun installUrl(url: String): InstalledPlugin = withContext(Dispatchers.IO) {
        val installed = installer.installFromUrl(url)
        afterInstall(installed)
        installed
    }

    suspend fun installMarket(entry: MarketplacePlugin): InstalledPlugin = withContext(Dispatchers.IO) {
        val installed = marketplace.install(entry)
        afterInstall(installed)
        installed
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        registry.setEnabled(id, enabled)
        val status = registry.status(id)
        if (enabled && status != null) schedule(status) else cancel(id)
    }

    suspend fun setCapability(id: String, capability: String, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            registry.setCapability(id, capability, enabled)
        }

    suspend fun uninstall(id: String) = withContext(Dispatchers.IO) {
        cancel(id)
        registry.uninstall(id)
    }

    suspend fun reload(id: String) = withContext(Dispatchers.IO) {
        cancel(id)
        registry.reload(id)
        registry.status(id)?.takeIf { it.state == PluginRuntimeState.Loaded }?.let { schedule(it) }
    }

    suspend fun listMarketplace(query: String = ""): List<MarketplacePlugin> =
        marketplace.search(query)

    suspend fun refreshMarketplace(): List<MarketplacePlugin> = marketplace.list(force = true)

    fun logs(id: String): String = registry.log().read(id)

    fun clearLogs(id: String) {
        registry.log().clear(id)
    }

    fun status(id: String): PluginStatus? = registry.status(id)

    suspend fun statusOnce(id: String): PluginStatus? {
        registry.statuses.first()
        return registry.status(id)
    }

    suspend fun registryUrl(): String = settings.registryUrlOnce()

    suspend fun setRegistryUrl(url: String) {
        settings.setRegistryUrl(url)
        marketplace.invalidate()
    }

    suspend fun developerMode(): Boolean = settings.developerModeOnce()

    suspend fun setDeveloperMode(enabled: Boolean) {
        settings.setDeveloperMode(enabled)
    }

    suspend fun pluginCount(): Int = store.list().size

    fun appContext(): Context = context

    private suspend fun afterInstall(installed: InstalledPlugin) {
        registry.reload(installed.manifest.id)
        registry.status(installed.manifest.id)
            ?.takeIf { it.state == PluginRuntimeState.Loaded }
            ?.let { schedule(it) }
    }

    private fun schedule(status: PluginStatus) {
        val jobs = status.manifest.jobs
        val manager = WorkManager.getInstance(context)
        jobs.forEach { job ->
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (job.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<PluginScheduleWorker>(
                job.intervalMinutes.coerceAtLeast(15).toLong(),
                TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setInputData(
                    Data.Builder()
                        .putString(PluginScheduleWorker.KEY_PLUGIN_ID, status.manifest.id)
                        .putString(PluginScheduleWorker.KEY_JOB_ID, job.id)
                        .build(),
                )
                .build()
            manager.enqueueUniquePeriodicWork(
                workName(status.manifest.id, job.id),
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    private fun cancel(id: String) {
        val manager = WorkManager.getInstance(context)
        registry.status(id)?.manifest?.jobs?.forEach { job ->
            manager.cancelUniqueWork(workName(id, job.id))
        }
        runCatching { manager.cancelUniqueWork(workName(id, "all")) }
    }

    private fun workName(pluginId: String, jobId: String): String = "plugin-$pluginId-$jobId"
}
