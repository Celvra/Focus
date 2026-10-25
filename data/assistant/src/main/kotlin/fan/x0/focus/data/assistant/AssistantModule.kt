package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.openai.AnthropicChatClient
import fan.x0.focus.core.network.openai.ChatWireClient
import fan.x0.focus.core.network.openai.ModelConnectionTester
import fan.x0.focus.core.network.openai.OpenAiChatClient
import fan.x0.focus.core.network.openai.OpenAiResponsesClient
import io.ktor.client.HttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Single
fun chatWireClient(http: HttpClient): ChatWireClient = ChatWireClient(
    chat = OpenAiChatClient(http),
    responses = OpenAiResponsesClient(http),
    anthropic = AnthropicChatClient(http),
)

@Single
fun modelConnectionTester(client: ChatWireClient): ModelConnectionTester = ModelConnectionTester(client)

@Module
@ComponentScan("fan.x0.focus.data.assistant")
class AssistantModule
