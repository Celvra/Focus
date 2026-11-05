package fan.x0.focus.data.im.weixin

internal data class WeixinQrCode(
    val qrcode: String,
    val content: String,
)

internal data class WeixinQrStatus(
    val status: String,
    val botToken: String = "",
    val botId: String = "",
    val baseUrl: String = "",
    val userId: String = "",
    val redirectHost: String = "",
)

internal data class WeixinUpdates(
    val ret: Int = 0,
    val errcode: Int = 0,
    val errmsg: String = "",
    val cursor: String = "",
    val timeoutMs: Long = 0,
    val messages: List<WeixinInbound> = emptyList(),
    val timedOut: Boolean = false,
)

internal data class WeixinInbound(
    val messageId: String,
    val fromUserId: String,
    val text: String,
    val contextToken: String,
    val sentAt: Long,
    val messageType: Int,
)

internal data class WeixinSendResponse(
    val ok: Boolean,
    val messageId: String,
    val error: String,
)
