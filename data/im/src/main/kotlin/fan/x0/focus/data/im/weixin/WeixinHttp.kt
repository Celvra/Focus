package fan.x0.focus.data.im.weixin

import fan.x0.focus.data.im.ImReply
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

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
        reply: ImReply? = null,
    ): WeixinSendResponse {
        val item = buildJsonObject {
            put("type", TYPE_TEXT)
            put("text_item", buildJsonObject { put("text", text) })
            reply?.let { put("ref_msg", refMessage(it)) }
        }
        return sendItems(baseUrl, token, toUserId, contextToken, clientId, listOf(item))
    }

    suspend fun sendMedia(
        baseUrl: String,
        token: String,
        toUserId: String,
        contextToken: String?,
        clientId: String,
        upload: WeixinUpload,
        fileName: String,
        image: Boolean,
        reply: ImReply? = null,
    ): WeixinSendResponse {
        val media = buildJsonObject {
            put("encrypt_query_param", upload.encryptQueryParam)
            put("aes_key", upload.aesKey)
            put("encrypt_type", 1)
        }
        val item = buildJsonObject {
            if (image) {
                put("type", TYPE_IMAGE)
                put(
                    "image_item",
                    buildJsonObject {
                        put("media", media)
                        put("mid_size", upload.encryptedSize)
                    },
                )
            } else {
                put("type", TYPE_FILE)
                put(
                    "file_item",
                    buildJsonObject {
                        put("media", media)
                        put("file_name", fileName)
                        put("len", upload.rawSize.toString())
                        put("md5", upload.md5)
                    },
                )
            }
            reply?.let { put("ref_msg", refMessage(it)) }
        }
        return sendItems(baseUrl, token, toUserId, contextToken, clientId, listOf(item))
    }

    suspend fun uploadMedia(
        baseUrl: String,
        token: String,
        toUserId: String,
        bytes: ByteArray,
        image: Boolean,
    ): WeixinUpload {
        val key = ByteArray(16).also(random::nextBytes)
        val cipher = encrypt(bytes, key)
        val filekey = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val md5 = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
        val body = buildJsonObject {
            put("filekey", filekey)
            put("media_type", if (image) MEDIA_IMAGE else MEDIA_FILE)
            put("to_user_id", toUserId)
            put("rawsize", bytes.size)
            put("rawfilemd5", md5)
            put("filesize", cipher.size)
            put("no_need_thumb", true)
            put("aeskey", key.joinToString("") { "%02x".format(it) })
            put("base_info", baseInfo())
        }
        val raw = post("ilink/bot/getuploadurl", body.toString(), token, 20_000, baseUrl)
        val root = parseObject(raw) ?: error("上传配置响应无效")
        val fullUrl = root.string("upload_full_url")
        val uploadParam = root.string("upload_param")
        if (fullUrl.isBlank() && uploadParam.isBlank()) error("上传配置缺少地址")
        val url = fullUrl.ifBlank {
            "$CDN_BASE_URL/upload?encrypted_query_param=${encode(uploadParam)}&filekey=${encode(filekey)}"
        }
        val response = http.post(url) {
            contentType(ContentType.Application.OctetStream)
            setBody(cipher)
            timeout {
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }
        if (!response.status.isSuccess()) {
            val message = response.headers["x-error-message"].orEmpty()
            error("CDN 上传失败 ${response.status.value}: $message")
        }
        val encryptQueryParam = response.headers["x-encrypted-param"].orEmpty()
        if (encryptQueryParam.isBlank()) error("CDN 上传响应缺少参数")
        val aesKey = Base64.getEncoder().encodeToString(
            key.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.UTF_8),
        )
        return WeixinUpload(
            encryptQueryParam = encryptQueryParam,
            aesKey = aesKey,
            rawSize = bytes.size,
            encryptedSize = cipher.size,
            md5 = md5,
        )
    }

    suspend fun downloadMedia(media: WeixinMedia): ByteArray? = try {
        val url = when {
            media.encryptQueryParam.isNotBlank() ->
                "$CDN_BASE_URL/download?encrypted_query_param=${encode(media.encryptQueryParam)}"
            media.url.isNotBlank() -> media.url
            else -> return null
        }
        val response = http.get(url) {
            timeout {
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
        }
        if (!response.status.isSuccess()) {
            null
        } else {
            val bytes = response.bodyAsBytes()
            if (media.aesKey.isBlank()) {
                bytes
            } else {
                runCatching { decrypt(bytes, decodeKey(media.aesKey)) }.getOrNull()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    private suspend fun sendItems(
        baseUrl: String,
        token: String,
        toUserId: String,
        contextToken: String?,
        clientId: String,
        items: List<JsonObject>,
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
                    items.forEach(::add)
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

    private fun refMessage(reply: ImReply): JsonObject = buildJsonObject {
        put("title", reply.senderName)
        put(
            "message_item",
            buildJsonObject {
                put("type", TYPE_TEXT)
                put("text_item", buildJsonObject { put("text", reply.text) })
            },
        )
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
        val media = mutableListOf<WeixinMedia>()
        val quotedMedia = mutableListOf<WeixinMedia>()
        var reply: ImReply? = null
        var itemId = ""
        items.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            if (itemId.isBlank()) itemId = item.string("msg_id")
            when (item.int("type")) {
                TYPE_TEXT -> item.objectOrNull("text_item")?.string("text")
                    ?.takeIf { it.isNotBlank() }
                    ?.let(texts::add)
                TYPE_IMAGE -> item.objectOrNull("image_item")?.let { image ->
                    media.add(imageMedia(image))
                }
                TYPE_VOICE -> {
                    val voice = item.objectOrNull("voice_item")?.string("text").orEmpty()
                    texts.add(voice.ifBlank { "[语音]" })
                }
                TYPE_FILE -> item.objectOrNull("file_item")?.let { file ->
                    media.add(fileMedia(file))
                }
                TYPE_VIDEO -> texts.add("[视频]")
            }
            item.objectOrNull("ref_msg")?.let { ref ->
                val refItem = ref.objectOrNull("message_item")
                val refText = refItem?.objectOrNull("text_item")?.string("text").orEmpty()
                val title = ref.string("title")
                val quoted = listOf(title, refText).filter { it.isNotBlank() }.joinToString("\n")
                if (quoted.isNotBlank()) {
                    reply = ImReply(
                        messageId = "",
                        senderName = "",
                        text = quoted,
                    )
                }
                when (refItem?.int("type")) {
                    TYPE_IMAGE -> refItem.objectOrNull("image_item")?.let {
                        quotedMedia.add(imageMedia(it))
                    }
                    TYPE_FILE -> refItem.objectOrNull("file_item")?.let {
                        quotedMedia.add(fileMedia(it))
                    }
                }
            }
        }
        val files = media.ifEmpty { quotedMedia }
        val text = texts.joinToString("\n").trim()
        if (text.isBlank() && files.isEmpty()) return null
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
            media = files,
            reply = reply,
        )
    }

    private fun imageMedia(image: JsonObject): WeixinMedia {
        val media = image.objectOrNull("media")
        val name = listOf("image", image.string("uuid"), "jpg")
            .filter { it.isNotBlank() }
            .joinToString(".")
        return WeixinMedia(
            kind = fan.x0.focus.data.im.ImAttachmentKind.Image,
            name = name,
            mime = "image/jpeg",
            encryptQueryParam = media?.string("encrypt_query_param").orEmpty(),
            aesKey = media?.string("aes_key").orEmpty().ifBlank { image.string("aeskey") },
            url = image.string("url"),
            size = image.long("mid_size"),
        )
    }

    private fun fileMedia(file: JsonObject): WeixinMedia {
        val media = file.objectOrNull("media")
        val name = file.string("file_name").ifBlank { "file" }
        return WeixinMedia(
            kind = fan.x0.focus.data.im.ImAttachmentKind.File,
            name = name,
            mime = mimeOf(name),
            encryptQueryParam = media?.string("encrypt_query_param").orEmpty(),
            aesKey = media?.string("aes_key").orEmpty(),
            size = file.long("len"),
        )
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "pdf" -> "application/pdf"
        "txt", "md", "json", "csv", "xml", "log" -> "text/plain"
        else -> "application/octet-stream"
    }

    private fun encrypt(bytes: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(bytes)
    }

    private fun decrypt(bytes: ByteArray, key: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(bytes)
    }

    private fun decodeKey(value: String): ByteArray {
        if (Regex("^[0-9a-fA-F]{32}$").matches(value)) {
            return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        val decoded = Base64.getDecoder().decode(value)
        if (decoded.size == 16) return decoded
        val hex = String(decoded, Charsets.UTF_8)
        if (Regex("^[0-9a-fA-F]{32}$").matches(hex)) {
            return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        error("无效的 AES 密钥")
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

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
        private const val CDN_BASE_URL = "https://novac2c.cdn.weixin.qq.com/c2c"
        private const val QR_TIMEOUT_MS = 35_000L
        private const val TYPE_TEXT = 1
        private const val TYPE_IMAGE = 2
        private const val TYPE_VOICE = 3
        private const val TYPE_FILE = 4
        private const val TYPE_VIDEO = 5
        private const val MEDIA_IMAGE = 1
        private const val MEDIA_FILE = 3
    }
}
