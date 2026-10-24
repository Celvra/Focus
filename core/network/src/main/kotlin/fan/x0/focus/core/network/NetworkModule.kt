package fan.x0.focus.core.network

import fan.x0.focus.core.network.openai.OpenAiModelCatalog
import fan.x0.focus.core.network.openai.ProviderBalanceClient
import io.ktor.client.HttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

// Shared transport for model calls, feeds, and article extraction.
@Single
fun httpClient(): HttpClient = createHttpClient()

@Single
fun openAiModelCatalog(http: HttpClient): OpenAiModelCatalog = OpenAiModelCatalog(http)

@Single
fun providerBalanceClient(http: HttpClient): ProviderBalanceClient = ProviderBalanceClient(http)

@Module
@ComponentScan("fan.x0.focus.core.network")
class NetworkModule
