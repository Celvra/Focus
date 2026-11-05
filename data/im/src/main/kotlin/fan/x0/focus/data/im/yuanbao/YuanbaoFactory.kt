package fan.x0.focus.data.im.yuanbao

import android.content.Context
import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.im.ImAccount
import fan.x0.focus.data.im.ImAccountStore
import fan.x0.focus.data.im.ImAuthSpec
import fan.x0.focus.data.im.ImCapabilities
import fan.x0.focus.data.im.ImChannel
import fan.x0.focus.data.im.ImChannelDescriptor
import fan.x0.focus.data.im.ImChannelFactory
import fan.x0.focus.data.im.ImChatKind
import fan.x0.focus.data.im.ImField
import fan.x0.focus.data.im.ImHttpClient
import fan.x0.focus.data.im.ImTextFormat
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class YuanbaoChannelFactory(
    private val accounts: ImAccountStore,
    private val secrets: SecretStore,
    private val http: ImHttpClient,
    @Provided context: Context,
) : ImChannelFactory {
    private val appVersion = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    override val descriptor: ImChannelDescriptor = ImChannelDescriptor(
        id = "yuanbao",
        name = "腾讯元宝",
        description = "在元宝 App 里创建机器人，获取 APP ID 和 APP Secret。",
        auth = ImAuthSpec.Fields(
            listOf(
                ImField(
                    key = YuanbaoChannel.FIELD_APP_KEY,
                    label = "APP ID",
                    secret = false,
                    required = true,
                ),
                ImField(
                    key = YuanbaoChannel.FIELD_APP_SECRET,
                    label = "APP Secret",
                    secret = true,
                    required = true,
                ),
            ),
        ),
        capabilities = ImCapabilities(
            maxTextLength = 3000,
            textFormat = ImTextFormat.Markdown,
            chatKinds = setOf(ImChatKind.Direct, ImChatKind.Group),
            supportsReplyRef = false,
            supportsMedia = false,
            minSendIntervalMs = 400,
        ),
    )

    override fun create(account: ImAccount): ImChannel = YuanbaoChannel(
        account = account,
        accounts = accounts,
        secrets = secrets,
        http = http.client,
        appVersion = appVersion,
    )
}
