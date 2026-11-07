package fan.x0.focus.data.im.weixin

import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.im.ImAccount
import fan.x0.focus.data.im.ImAccountStore
import fan.x0.focus.data.im.ImAuthSpec
import fan.x0.focus.data.im.ImCapabilities
import fan.x0.focus.data.im.ImChannel
import fan.x0.focus.data.im.ImChannelDescriptor
import fan.x0.focus.data.im.ImChannelFactory
import fan.x0.focus.data.im.ImChatKind
import fan.x0.focus.data.im.ImHttpClient
import fan.x0.focus.data.im.ImTextFormat
import org.koin.core.annotation.Single

@Single
class WeixinChannelFactory(
    private val accounts: ImAccountStore,
    private val secrets: SecretStore,
    private val http: ImHttpClient,
) : ImChannelFactory {
    override val descriptor: ImChannelDescriptor = ImChannelDescriptor(
        id = "weixin",
        name = "微信",
        description = "用手机微信扫码登录，通过微信官方接口收发私聊消息。",
        auth = ImAuthSpec.Qr,
        capabilities = ImCapabilities(
            maxTextLength = 3000,
            textFormat = ImTextFormat.Markdown,
            chatKinds = setOf(ImChatKind.Direct),
            supportsReplyRef = true,
            supportsMedia = true,
            minSendIntervalMs = 400,
        ),
    )

    override fun create(account: ImAccount): ImChannel = WeixinChannel(
        account = account,
        accounts = accounts,
        secrets = secrets,
        http = http.client,
    )
}
