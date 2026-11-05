package fan.x0.focus.data.im

import fan.x0.focus.data.assistant.ImToolBridge
import fan.x0.focus.data.im.weixin.WeixinChannelFactory
import fan.x0.focus.data.im.yuanbao.YuanbaoChannelFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

class ImHttpClient(val client: HttpClient)

@Single
fun imHttpClient(): ImHttpClient = ImHttpClient(
    HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 120_000
        }
        install(WebSockets)
    },
)

@Single
fun imChannelRegistry(
    yuanbao: YuanbaoChannelFactory,
    weixin: WeixinChannelFactory,
): ImChannelRegistry = ImChannelRegistry(listOf(yuanbao, weixin))

@Single
fun imToolBridge(router: ImToolRouter): ImToolBridge = router

@Module
@ComponentScan("fan.x0.focus.data.im", "fan.x0.focus.data.im.yuanbao", "fan.x0.focus.data.im.weixin")
class ImModule
