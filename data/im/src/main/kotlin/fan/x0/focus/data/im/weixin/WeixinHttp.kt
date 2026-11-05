package fan.x0.focus.data.im.weixin

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.util.Base64

internal class WeixinHttp(private val http: HttpClient) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val random = SecureRandom()

    suspend fun fetchQrCode(tokens: List<String>): WeixinQrCode {
        val body = buildJsonObject {
            put(
                "local_token_list",
                buildJsonArray {
                    tokens.filter { it.isNotBlank() }.take(10).forEach { add(JsonPrimitive(it)) }
                },
            )
        }
        val raw = post("ilink/bot/get_bot_qrcode?bot_type=3", body.toString(), token = null, timeoutMs = 20_000)
        val root = parseObject(raw) ?: error("二维码响应无效")
        val qrcode = root.string("qrcode")
        val content = root.string("qrcode_img_content")
        if (qrcode.isBlank() || content.isBlank()) error("二维码响应缺少内容")
        return WeixinQrCode(qrcode, content)
    }

    suspend fun pollStatus(
        baseUrl: String,
        qrcode: String,
        verifyCode: String?,
    ): WeixinQrStatus? {
        val raw = try {
            http.get("${cleanBase(baseUrl)}/ilink/bot/get_qrcode_status") {
                commonHeaders()
                parameter("qrcode", qrcode)
                verifyCode?.takeIf { it.isNotBlank() }?.let { parameter("verify_code", it) }
                timeout {
                    requestTimeoutMillis = QR_TIMEOUT_MS + 5_000
                    socketTimeoutMillis = QR_TIMEOUT_MS + 10_000
                }
            }.let { response ->
                if (!response.status.isSuccess()) return null
                response.bodyAsText()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return null
        }
        val root = parseObject(raw) ?: return null
        return WeixinQrStatus(
            status = root.string("status"),
            botToken = root.string("bot_token"),
            botId = root.string("ilink_bot_id"),
            baseUrl = root.string("baseurl"),
            userId = root.string("ilink_user_id"),
            redirectHost = root.string("redirect_host"),
        )
    }

    suspend fun updates(
        baseUrl: String,
        token: String,
        cursor: String,
        timeoutMs: Long,
    ): WeixinUpdates {
        val body = buildJsonObject {
            put("get_updates_buf", cursor)
            put("base_info", baseInfo())
        }
        val raw = try {
            post(
                endpoint = "ilink/bot/getupdates",
                body = body.toString(),
                token = token,
                timeoutMs = timeoutMs + 5_000,
                baseUrl = baseUrl,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (isTimeout(e)) return WeixinUpdates(timedOut = true)
            throw e
        }
        val root = parseObject(raw) ?: return WeixinUpdates(timedOut = true)
        val messages = root["msgs"]
            ?.let { it as? JsonArray }
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull(::parseInbound)
            .orEmpty()
        return WeixinUpdates(
            ret = root.int("ret"),
            errcode = root.int("errcode"),
            errmsg = root.string("errmsg"),
            cursor = root.string("get_updates_buf"),
            timeoutMs = root.long("longpolling_timeout_ms"),
            messages = messages,
        )
    }

    suspend fun sendText(
        baseUrl: String,
        token: String,
        toUserId: String,
        text: String,
        contextToken: String?,
        clientId: String,
    ): WeixinSendResponse {
        val message = buildJsonObject {
            put("from_user_id", "")
            put("to_user_id", toUserId)
            put("client_id", clientId)
            put("message_type", 2)
            put("message_state", 2)
            contextToken?.takeIf { it.isNotBlank() }?.let { put("context_token", it) }
            put(
                "item_list",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", 1)
                            put(
                                "text_item",
                                buildJsonObject { put("text", text) },
                            )
                        },
                    )
                },
            )
        }
        val body = buildJsonObject {
            put("msg", message)
            put("base_info", baseInfo())
        }
        val raw = post("ilink/bot/sendmessage", body.toString(), token, 20_000, baseUrl)
        val root = parseObject(raw)
            ?: return WeixinSendResponse(false, clientId, "发送响应无效")
        val ret = root.int("ret")
        if (ret != 0) {
            return WeixinSendResponse(false, clientId, root.string("errmsg").ifBlank { "发送失败 $ret" })
        }
        return WeixinSendResponse(true, root.string("message_id").ifBlank { clientId }, "")
    }

    suspend fun notify(baseUrl: String, token: String, start: Boolean) {
        val endpoint = if (start) "ilink/bot/msg/notifystart" else "ilink/bot/msg/notifystop"
        val body = buildJsonObject { put("base_info", baseInfo()) }
        runCatching {
            post(endpoint, body.toString(), token, 8_000, baseUrl)
        }
    }

    private suspend fun post(
        endpoint: String,
        body: String,
        token: String?,
        timeoutMs: Long,
        baseUrl: String = DEFAULT_BASE_URL,
    ): String {
        val response = http.post("${cleanBase(baseUrl)}/$endpoint") {
            contentType(ContentType.Application.Json)
            commonHeaders()
            header("X-WECHAT-UIN", wechatUin())
            header("AuthorizationType", "ilink_bot_token")
            token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            setBody(body)
            timeout {
                requestTimeoutMillis = timeoutMs
                socketTimeoutMillis = timeoutMs + 5_000
            }
        }
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("微信接口 ${response.status.value}: ${raw.take(300)}")
        }
        return raw
    }

    private fun io.ktor.client.request.HttpRequestBuilder.commonHeaders() {
        header("iLink-App-Id", APP_ID)
        header("iLink-App-ClientVersion", CLIENT_VERSION.toString())
    }

    private fun baseInfo(): JsonObject = buildJsonObject {
        put("channel_version", CHANNEL_VERSION)
        put("bot_agent", BOT_AGENT)
    }

    private fun wechatUin(): String {
        val value = random.nextInt().toUInt().toString()
        return Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    }

    private fun parseObject(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()

    private fun parseInbound(message: JsonObject): WeixinInbound? {
        val from = message.string("from_user_id")
        if (from.isBlank()) return null
        val items = message["item_list"] as? JsonArray ?: return null
        val texts = mutableListOf<String>()
        var itemId = ""
        items.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            if (itemId.isBlank()) itemId = item.string("msg_id")
            when (item.int("type")) {
                TYPE_TEXT -> item.objectOrNull("text_item")?.string("text")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(texts::add)
                TYPE_IMAGE -> texts.add("[图片]")
                TYPE_VOICE -> {
                    val voice = item.objectOrNull("voice_item")?.string("text").orEmpty()
                    texts.add(voice.ifBlank { "[语音]" })
                }
                TYPE_FILE -> texts.add("[文件]")
                TYPE_VIDEO -> texts.add("[视频]")
            }
        }
        val text = texts.joinToString("\n").trim()
        if (text.isBlank()) return null
        val sentAt = message.long("create_time_ms")
        val messageId = message.string("message_id")
            .ifBlank { itemId }
            .ifBlank { message.string("seq") }
            .ifBlank { "$from:$sentAt:${text.take(16)}" }
        return WeixinInbound(
            messageId = messageId,
            fromUserId = from,
            text = text,
            contextToken = message.string("context_token"),
            sentAt = sentAt,
            messageType = message.int("message_type"),
        )
    }

    private fun JsonObject.string(key: String): String {
        val value = this[key] as? JsonPrimitive ?: return ""
        return value.contentOrNull.orEmpty()
    }

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.intOrNull ?: 0

    private fun JsonObject.long(key: String): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: 0

    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

    private fun isTimeout(error: Throwable): Boolean =
        error is java.net.SocketTimeoutException ||
            error.javaClass.simpleName.contains("Timeout", ignoreCase = true) ||
            error.message.orEmpty().contains("timeout", ignoreCase = true)

    private fun cleanBase(baseUrl: String): String =
        baseUrl.trim().ifBlank { DEFAULT_BASE_URL }.trimEnd('/')

    companion object {
        const val DEFAULT_BASE_URL = "https://ilinkai.weixin.qq.com"
        const val CHANNEL_VERSION = "2.4.10"
        const val BOT_AGENT = "Focus/1.0"
        const val APP_ID = "bot"
        const val CLIENT_VERSION = 132106
        private const val QR_TIMEOUT_MS = 35_000L
        private const val TYPE_TEXT = 1
        private const val TYPE_IMAGE = 2
        private const val TYPE_VOICE = 3
        private const val TYPE_FILE = 4
        private const val TYPE_VIDEO = 5
    }
}
