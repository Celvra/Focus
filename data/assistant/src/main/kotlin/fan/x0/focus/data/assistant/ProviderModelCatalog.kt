package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ModelMeta
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single

data class ProviderModelItem(
    val id: String,
    val name: String = "",
    val meta: ModelMeta = ModelMeta(),
)

data class ProviderModelGroup(
    val providerId: String,
    val providerName: String,
    val icon: String = "",
    val models: List<ProviderModelItem> = emptyList(),
)

@Single
class ProviderModelCatalog(
    private val catalog: ModelCatalogRepository,
    private val secrets: SecretStore,
) {
    suspend fun load(config: AssistantConfig): List<ProviderModelGroup> = coroutineScope {
        config.providers.map { provider ->
            async { loadProvider(provider) }
        }.awaitAll()
    }

    private suspend fun loadProvider(provider: ProviderConfig): ProviderModelGroup {
        val saved = provider.modelOptions
        val key = secrets.apiKey(provider.id).orEmpty()
        val fetched = if (key.isBlank() && provider.requiresKey) {
            emptyList()
        } else {
            try {
                catalog.models(
                    provider.id,
                    provider.baseUrl,
                    provider.model,
                    provider.requestConfig(key),
                    provider.protocol,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
        }
        val fetchedById = fetched.associateBy { it.id }
        val models = if (provider.modelsConfigured) {
            saved.map { model ->
                ProviderModelItem(
                    id = model.id,
                    name = model.name,
                    meta = fetchedById[model.id]?.meta ?: ModelMeta(),
                )
            }
        } else {
            buildList {
                saved.forEach { model ->
                    add(
                        ProviderModelItem(
                            id = model.id,
                            name = model.name,
                            meta = fetchedById[model.id]?.meta ?: ModelMeta(),
                        ),
                    )
                }
                fetched.forEach { entry ->
                    if (saved.none { it.id == entry.id }) {
                        add(ProviderModelItem(id = entry.id, name = entry.id, meta = entry.meta))
                    }
                }
            }
        }
        return ProviderModelGroup(
            providerId = provider.id,
            providerName = provider.name,
            icon = provider.icon,
            models = models,
        )
    }
}
