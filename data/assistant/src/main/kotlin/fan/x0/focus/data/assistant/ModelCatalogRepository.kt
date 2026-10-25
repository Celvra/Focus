package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ModelEntry
import fan.x0.focus.core.network.openai.ModelEndpoint
import fan.x0.focus.core.network.openai.ModelMeta
import fan.x0.focus.core.network.openai.OpenAiModelCatalog
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Single

@Single
class ModelCatalogRepository(
    private val upstream: OpenAiModelCatalog,
    private val modelsDev: ModelsDevCatalog,
) {
    suspend fun models(
        providerId: String,
        baseUrl: String,
        model: String,
        apiKey: String,
        protocol: ChatProtocol = ChatProtocol.ChatCompletions,
    ): List<ModelEntry> {
        val entries = upstream.details(ModelEndpoint(baseUrl, model), apiKey, protocol)
        if (entries.isEmpty()) return entries
        val fallback = try {
            modelsDev.metadata(providerId, baseUrl, entries.map { it.id })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emptyMap()
        }
        if (fallback.isEmpty()) return entries
        return entries.map { entry ->
            entry.copy(meta = entry.meta.fillMissingFrom(fallback[entry.id] ?: ModelMeta()))
        }
    }
}