package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.ModelConnectionTester
import fan.x0.focus.core.network.openai.OpenAiChatClient
import io.ktor.client.HttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Single
fun openAiChatClient(http: HttpClient): OpenAiChatClient = OpenAiChatClient(http)

@Single
fun modelConnectionTester(client: OpenAiChatClient): ModelConnectionTester = ModelConnectionTester(client)

@Module
@ComponentScan("fan.x0.focus.data.assistant")
class AssistantModule
