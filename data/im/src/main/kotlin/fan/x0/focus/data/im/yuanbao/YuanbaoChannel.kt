package fan.x0.focus.data.im.yuanbao

import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.im.ImAccount
import fan.x0.focus.data.im.ImAccountStore
import fan.x0.focus.data.im.ImAttachment
import fan.x0.focus.data.im.ImAttachmentKind
import fan.x0.focus.data.im.ImChannel
import fan.x0.focus.data.im.ImChatKind
import fan.x0.focus.data.im.ImChatRef
import fan.x0.focus.data.im.ImConnectionState
import fan.x0.focus.data.im.ImEvent
import fan.x0.focus.data.im.ImInbound
import fan.x0.focus.data.im.ImLog
import fan.x0.focus.data.im.ImOutbound
import fan.x0.focus.data.im.ImReply
import fan.x0.focus.data.im.ImSecretKeys
import fan.x0.focus.data.im.ImSendResult
import fan.x0.focus.data.im.ImSendTarget
import fan.x0.focus.data.im.ImUserRef
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

internal class YuanbaoFatalException(message: String) : Exception(message)

internal class YuanbaoRetryException(message: String) : Exception(message)

internal class YuanbaoTokenRefreshException(message: String) : Exception(message)

private fun <T> safeDecode(label: String, block: () -> T): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ImLog.d("FocusIM/Yuanbao", "$label decode failed: ${e.message}")
    null
}

private val jsonMessageJson = Json { ignoreUnknownKeys = true }

internal fun parseJsonMessage(content: String): InboundMessagePush? {
    val root = jsonMessageJson.parseToJsonElement(content).jsonObject
    val callbackCommand = root["callback_command"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val fromAccount = root["from_account"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val toAccount = root["to_account"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val senderNickname = root["sender_nickname"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val groupCode = root["group_code"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val groupName = root["group_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val msgKey = root["msg_key"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val msgId = root["msg_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val msgSeq = root["msg_seq"]?.jsonPrimitive?.longOrNull ?: 0L
    val msgTime = root["msg_time"]?.jsonPrimitive?.longOrNull ?: 0L
    val botOwnerId = root["bot_owner_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val cloudCustomData = root["cloud_custom_data"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val traceId = root["log_ext"]
        ?.jsonObject
        ?.get("trace_id")
        ?.jsonPrimitive
        ?.contentOrNull
        .orEmpty()
    val text = root["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val body = mutableListOf<MsgBodyElement>()
    root["msg_body"]?.jsonArray?.forEach { element ->
        val obj = element.jsonObject
        val type = obj["msg_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val contentObject = obj["msg_content"] as? JsonObject
        val value = when (val messageContent = obj["msg_content"]) {
            is JsonPrimitive -> messageContent.contentOrNull.orEmpty()
            is JsonObject -> messageContent["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
            else -> ""
        }
        val data = contentObject?.get("data")?.jsonPrimitive?.contentOrNull.orEmpty()
        val uuid = contentObject?.get("uuid")?.jsonPrimitive?.contentOrNull.orEmpty()
        val fileName = contentObject?.get("file_name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val fileSize = contentObject?.get("file_size")?.jsonPrimitive?.intOrNull ?: 0
        val imageInfos = contentObject?.get("image_info_array")?.jsonArray?.mapNotNull { info ->
            val obj = info as? JsonObject ?: return@mapNotNull null
            ImImageInfo(
                type = obj["type"]?.jsonPrimitive?.intOrNull ?: 0,
                size = obj["size"]?.jsonPrimitive?.intOrNull ?: 0,
                width = obj["width"]?.jsonPrimitive?.intOrNull ?: 0,
                height = obj["height"]?.jsonPrimitive?.intOrNull ?: 0,
                url = obj["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            )
        }.orEmpty()
        if (type.isNotBlank()) {
            body.add(
                MsgBodyElement(
                    type,
                    MsgContent(
                        text = value,
                        uuid = uuid,
                        data = data,
                        imageInfoArray = imageInfos,
                        url = contentObject?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty(),
                        fileSize = fileSize,
                        fileName = fileName,
                    ),
                ),
            )
        }
    }
    if (body.isEmpty() && text.isNotBlank()) {
        body.add(MsgBodyElement("TIMTextElem", MsgContent(text = text)))
    }
    if (fromAccount.isBlank() || body.isEmpty()) return null
    return InboundMessagePush(
        callbackCommand = callbackCommand.ifBlank {
            if (groupCode.isNotBlank()) {
                "Group.CallbackAfterSendMsg"
            } else {
                "C2C.CallbackAfterSendMsg"
            }
        },
        fromAccount = fromAccount,
        toAccount = toAccount,
        senderNickname = senderNickname,
        groupCode = groupCode,
        groupName = groupName,
        msgKey = msgKey,
        msgSeq = msgSeq,
        msgTime = msgTime,
        msgId = msgId.ifBlank { msgKey },
        msgBody = body,
        cloudCustomData = cloudCustomData,
        botOwnerId = botOwnerId,
        traceId = traceId,
    )
}


internal class YuanbaoChannel(
    private val account: ImAccount,
    private val accounts: ImAccountStore,
    private val secrets: SecretStore,
    private val http: HttpClient,
    private val appVersion: String,
) : ImChannel {
    private val json = Json { ignoreUnknownKeys = true }
    private val api = YuanbaoHttp(http, appVersion)
    private val _state = MutableStateFlow(ImConnectionState.Disabled)
    override val state: StateFlow<ImConnectionState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<ImEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ImEvent> = _events.asSharedFlow()
    @Volatile private var current: Session? = null
    @Volatile private var closed = false
    private var tokenCache: YuanbaoToken? = null
    private var tokenExpiresAt = 0L

    override suspend fun connect() {
        closed = false
        var attempt = 0
        ImLog.i(TAG, "connect start account=${account.id}")
        while (!closed) {
            try {
                val appKey = account.config.values[FIELD_APP_KEY].orEmpty()
                val appSecret = secrets.secret(ImSecretKeys.field(account.id, FIELD_APP_SECRET)).orEmpty()
                if (appKey.isBlank() || appSecret.isBlank()) {
                    emitState(ImConnectionState.AuthRequired, "缺少 APP ID 或 APP Secret")
                    return
                }
                emitState(ImConnectionState.Connecting)
                val token = token(appKey, appSecret)
                ImLog.i(
                    TAG,
                    "token bot=${token.botId} source=${token.source} " +
                        "duration=${token.duration} length=${token.token.length}",
                )
                val botId = token.botId.ifBlank { account.platformUserId }
                if (botId.isBlank()) {
                    throw YuanbaoAuthException("没有拿到机器人 ID")
                }
                if (token.botId.isNotBlank()) {
                    accounts.setPlatformUserId(account.id, token.botId)
                }
                ImLog.i(TAG, "ws connecting bot=$botId url=$WS_URL")
                http.webSocket(urlString = WS_URL) {
                    val session = Session(this, token, botId) { attempt = 0 }
                    current = session
                    try {
                        session.run()
                    } finally {
                        current = null
                    }
                }
                if (closed) return
                ImLog.i(TAG, "ws disconnected, retry")
                emitState(ImConnectionState.Connecting, "连接已断开")
                delay(backoff(attempt++))
            } catch (e: CancellationException) {
                throw e
            } catch (e: YuanbaoAuthException) {
                ImLog.e(TAG, "auth required: ${e.message}")
                emitState(ImConnectionState.AuthRequired, e.message.orEmpty())
                return
            } catch (e: YuanbaoTokenRefreshException) {
                ImLog.w(TAG, "token refresh: ${e.message}")
                tokenCache = null
                emitState(ImConnectionState.Connecting, e.message.orEmpty())
                delay(backoff(attempt++))
            } catch (e: YuanbaoFatalException) {
                ImLog.e(TAG, "fatal: ${e.message}", e)
                emitState(ImConnectionState.Error, e.message.orEmpty())
                return
            } catch (e: Throwable) {
                ImLog.e(TAG, "connect failed: ${e.message}", e)
                emitState(ImConnectionState.Error, e.message.orEmpty().ifBlank { "连接失败" })
                delay(backoff(attempt++))
            }
        }
    }

    override suspend fun send(target: ImSendTarget, message: ImOutbound): ImSendResult {
        ImLog.d(
            TAG,
            "send chat=${target.chat.kind.name}:${target.chat.externalId} chars=${message.text.length}",
        )
        val session = current ?: run {
            ImLog.w(TAG, "send skipped: not connected")
            return ImSendResult(false, error = "未连接")
        }
        var last = ImSendResult(true)
        if (message.text.isNotBlank()) {
            last = session.sendText(target.chat, message.text, message.reply)
        }
        message.attachments.forEachIndexed { index, attachment ->
            if (!last.ok) return@forEachIndexed
            val reply = if (message.text.isBlank() && index == 0) message.reply else null
            last = session.sendAttachment(target.chat, attachment, reply)
        }
        if (!last.ok) ImLog.w(TAG, "send failed: ${last.error}")
        return last
    }

    override suspend fun disconnect() {
        closed = true
        current?.close()
        current = null
    }

    private fun emitState(state: ImConnectionState, detail: String = "") {
        _state.value = state
        _events.tryEmit(ImEvent.State(state, detail))
    }

    private fun logInbound(label: String, message: InboundMessagePush) {
        ImLog.d(
            TAG,
            "$label from=${message.fromAccount} to=${message.toAccount} " +
                "group=${message.groupCode} sender=${message.senderNickname} " +
                "msgId=${message.msgId} seq=${message.msgSeq} time=${message.msgTime} " +
                "callback=${message.callbackCommand} body=${message.msgBody.size}",
        )
    }

    private suspend fun emitInbound(push: InboundMessagePush, botId: String) {
        if (push.fromAccount == botId) {
            ImLog.d(TAG, "skip self push ${push.fromAccount}")
            return
        }
        val group = push.groupCode.isNotBlank()
        val chat = ImChatRef(
            externalId = if (group) push.groupCode else push.fromAccount,
            kind = if (group) ImChatKind.Group else ImChatKind.Direct,
            title = when {
                group -> push.groupName.ifBlank { "群聊 ${push.groupCode.take(8)}" }
                push.senderNickname.isNotBlank() -> push.senderNickname
                else -> push.fromAccount
            },
        )
        val texts = mutableListOf<String>()
        val attachments = mutableListOf<ImAttachment>()
        var mentioned = false
        var unsupported = false
        push.msgBody.forEach { element ->
            when (element.msgType) {
                "TIMTextElem" -> if (element.msgContent.text.isNotBlank()) {
                    texts.add(element.msgContent.text)
                }
                "TIMCustomElem" -> {
                    val custom = parseCustom(element.msgContent.data)
                    if (custom != null && custom.elemType == 1002) {
                        if (custom.userId == botId) mentioned = true
                        if (custom.text.isNotBlank()) texts.add(custom.text)
                    } else {
                        unsupported = true
                    }
                }
                "TIMImageElem" -> {
                    val info = element.msgContent.imageInfoArray.getOrNull(1)
                        ?: element.msgContent.imageInfoArray.firstOrNull()
                    val url = info?.url.orEmpty()
                    val name = imageName(element.msgContent.uuid)
                    if (url.isNotBlank()) {
                        val bytes = downloadMedia(url, MAX_MEDIA_BYTES)
                        if (bytes != null) {
                            attachments.add(
                                ImAttachment(
                                    kind = ImAttachmentKind.Image,
                                    name = name,
                                    mime = mimeOf(name),
                                    bytes = bytes,
                                ),
                            )
                        } else {
                            unsupported = true
                        }
                    } else {
                        unsupported = true
                    }
                }
                "TIMFileElem" -> {
                    val url = element.msgContent.url
                    val name = element.msgContent.fileName.ifBlank { "file" }
                    if (url.isNotBlank()) {
                        val bytes = downloadMedia(url, MAX_FILE_BYTES)
                        if (bytes != null) {
                            attachments.add(
                                ImAttachment(
                                    kind = ImAttachmentKind.File,
                                    name = name,
                                    mime = mimeOf(name),
                                    bytes = bytes,
                                ),
                            )
                        } else {
                            unsupported = true
                        }
                    } else {
                        unsupported = true
                    }
                }
                else -> unsupported = true
            }
        }
        if (texts.isEmpty() && attachments.isEmpty() && !unsupported) return
        val reply = parseReply(push.cloudCustomData, botId)
        val messageId = push.msgId.ifBlank {
            "${push.fromAccount}:${push.msgSeq}:${push.msgTime}"
        }
        val sentAt = if (push.msgTime > 0) push.msgTime * 1000L else System.currentTimeMillis()
        ImLog.i(
            TAG,
            "inbound chat=${chat.kind.name}:${chat.externalId} title=${chat.title} " +
                "sender=${push.senderNickname}(${push.fromAccount}) " +
                "mentioned=${!group || mentioned || reply?.fromBot == true} " +
                "media=${attachments.size} text=${texts.joinToString("\n").take(80)}",
        )
        _events.tryEmit(
            ImEvent.Inbound(
                ImInbound(
                    messageId = messageId,
                    chat = chat,
                    sender = ImUserRef(
                        id = push.fromAccount,
                        name = push.senderNickname.ifBlank { push.fromAccount },
                    ),
                    text = texts.joinToString("\n").trim(),
                    isBotMentioned = !group || mentioned || reply?.fromBot == true,
                    sentAt = sentAt,
                    platformContext = null,
                    ownerId = push.botOwnerId.ifBlank { null },
                    reply = reply,
                    attachments = attachments,
                ),
            ),
        )
    }

    private suspend fun downloadMedia(url: String, maxBytes: Int): ByteArray? = try {
        val response = http.get(url)
        if (!response.status.isSuccess()) {
            ImLog.w(TAG, "download media failed status=${response.status.value}")
            null
        } else {
            val length = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
            if (length > maxBytes) {
                ImLog.w(TAG, "download media too large length=$length")
                null
            } else {
                response.bodyAsBytes().takeIf { it.isNotEmpty() && it.size <= maxBytes }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ImLog.w(TAG, "download media failed", e)
        null
    }

    private fun parseReply(data: String?, botId: String): ImReply? {
        if (data.isNullOrBlank()) return null
        val quote = runCatching {
            json.parseToJsonElement(data).jsonObject["quote"]?.jsonObject
        }.getOrNull() ?: return null
        val type = quote["type"]?.jsonPrimitive?.intOrNull ?: 0
        val desc = quote["desc"]?.jsonPrimitive?.contentOrNull.orEmpty().trim().ifBlank {
            when (type) {
                2 -> "[图片]"
                3 -> "[文件]"
                4 -> "[视频]"
                5 -> "[语音]"
                else -> ""
            }
        }
        if (desc.isBlank()) return null
        val senderId = quote["sender_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val senderName = quote["sender_nickname"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val id = quote["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return ImReply(
            messageId = id,
            senderName = senderName.ifBlank { senderId },
            text = desc,
            fromBot = senderId.isNotBlank() && senderId == botId,
        )
    }

    private fun imageName(uuid: String): String {
        val name = uuid.ifBlank { "image" }
        return if (name.contains('.')) name else "$name.jpg"
    }

    private fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "txt" -> "text/plain"
        "json" -> "application/json"
        "pdf" -> "application/pdf"
        else -> "application/octet-stream"
    }

    private fun parseCustom(data: String): CustomElement? = runCatching {
        json.decodeFromString(CustomElement.serializer(), data)
    }.getOrNull()

    private suspend fun token(appKey: String, appSecret: String): YuanbaoToken {
        val now = System.currentTimeMillis()
        val cached = tokenCache
        if (cached != null && tokenExpiresAt > now) return cached
        val fresh = api.signToken(appKey, appSecret, account.config.baseUrl)
        tokenCache = fresh
        tokenExpiresAt = if (fresh.duration > 0) {
            now + fresh.duration * 1000L - TOKEN_REFRESH_MARGIN_MS
        } else {
            now + TOKEN_FALLBACK_MS
        }
        return fresh
    }

    private fun backoff(attempt: Int): Long =
        BACKOFF[attempt.coerceAtMost(BACKOFF.lastIndex)]

    private inner class Session(
        private val ws: DefaultClientWebSocketSession,
        private val token: YuanbaoToken,
        private val botId: String,
        private val onConnected: () -> Unit,
    ) {
        private val pending = ConcurrentHashMap<String, CompletableDeferred<ConnMsg>>()
        private val sendMutex = Mutex()
        private var auth: CompletableDeferred<Pair<Int, AuthBindRsp>>? = null
        private var ping: CompletableDeferred<PingRsp>? = null
        @Volatile private var heartInterval = 30
        private val seq = AtomicInteger()

        suspend fun run() = coroutineScope {
            val reader = launch { readLoop() }
            try {
                val authDeferred = CompletableDeferred<Pair<Int, AuthBindRsp>>()
                auth = authDeferred
                ImLog.i(TAG, "auth send bot=$botId")
                val request = AuthBindReq(
                    authInfo = AuthInfo(uid = botId, source = token.source, token = token.token),
                    deviceInfo = DeviceInfo(appVersion = appVersion, botVersion = appVersion),
                )
                sendFrame(
                    ConnMsg(
                        head = Head(
                            cmdType = 0,
                            cmd = CMD_AUTH,
                            seqNo = nextSeq(),
                            msgId = newId(),
                            module = MODULE_CONN,
                        ),
                        data = request.encode(),
                    ).encode(),
                )
                val bound = withTimeoutOrNull(15_000) { authDeferred.await() }
                    ?: throw YuanbaoRetryException("鉴权超时")
                val response = bound.second
                val headStatus = bound.first
                ImLog.i(
                    TAG,
                    "auth response code=${response.code} headStatus=$headStatus " +
                        "message=${response.message}",
                )
                if (headStatus != 0 && response.code != AUTH_ALREADY) {
                    val message = response.message.ifBlank { "鉴权失败 $headStatus" }
                    if (response.code in AUTH_REFRESH) throw YuanbaoTokenRefreshException(message)
                    if (response.code in AUTH_FATAL || response.code == 0) throw YuanbaoAuthException(message)
                    throw YuanbaoRetryException(message)
                }
                if (response.code != 0 && response.code != AUTH_ALREADY) {
                    val message = response.message.ifBlank { "鉴权失败 ${response.code}" }
                    if (response.code in AUTH_REFRESH) throw YuanbaoTokenRefreshException(message)
                    if (response.code in AUTH_FATAL) throw YuanbaoAuthException(message)
                    throw YuanbaoRetryException(message)
                }
                onConnected()
                ImLog.i(TAG, "connected connectId=${response.connectId}")
                emitState(ImConnectionState.Connected)
                val heartbeat = launch { heartbeatLoop() }
                try {
                    reader.join()
                    val reason = withTimeoutOrNull(2_000) { ws.closeReason.await() }
                    if (reason != null && reason.code.toInt() in FATAL_CLOSE_CODES) {
                        throw YuanbaoFatalException(
                            reason.message.ifBlank { "连接已关闭 ${reason.code}" },
                        )
                    }
                } finally {
                    heartbeat.cancel()
                }
            } finally {
                val closedError = YuanbaoRetryException("连接已断开")
                auth?.completeExceptionally(closedError)
                ping?.completeExceptionally(closedError)
                auth = null
                ping = null
                pending.values.forEach { it.completeExceptionally(closedError) }
                pending.clear()
            }
        }

        suspend fun sendText(chat: ImChatRef, text: String, reply: ImReply? = null): ImSendResult {
            val body = listOf(
                MsgBodyElement(
                    msgType = "TIMTextElem",
                    msgContent = MsgContent(text = text),
                ),
            )
            return sendBody(chat, body, reply, "text=${text.length}")
        }

        suspend fun sendAttachment(
            chat: ImChatRef,
            attachment: ImAttachment,
            reply: ImReply? = null,
        ): ImSendResult {
            val upload = try {
                api.uploadMedia(
                    token = token,
                    botId = botId,
                    fileName = attachment.name,
                    mime = attachment.mime,
                    bytes = attachment.bytes,
                    baseUrl = account.config.baseUrl,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ImLog.w(TAG, "upload media failed", e)
                return ImSendResult(false, error = e.message.orEmpty().ifBlank { "上传失败" })
            }
            val body = if (attachment.kind == ImAttachmentKind.Image) {
                listOf(
                    MsgBodyElement(
                        msgType = "TIMImageElem",
                        msgContent = MsgContent(
                            uuid = upload.uuid,
                            imageFormat = 255,
                            imageInfoArray = listOf(
                                ImImageInfo(
                                    type = 1,
                                    size = upload.size,
                                    width = upload.width,
                                    height = upload.height,
                                    url = upload.url,
                                ),
                            ),
                        ),
                    ),
                )
            } else {
                listOf(
                    MsgBodyElement(
                        msgType = "TIMFileElem",
                        msgContent = MsgContent(
                            uuid = upload.uuid,
                            url = upload.url,
                            fileSize = upload.size,
                            fileName = attachment.name,
                        ),
                    ),
                )
            }
            return sendBody(chat, body, reply, "media=${attachment.kind.name} size=${upload.size}")
        }

        private suspend fun sendBody(
            chat: ImChatRef,
            body: List<MsgBodyElement>,
            reply: ImReply?,
            label: String,
        ): ImSendResult {
            val msgId = newId()
            val command: String
            val payload: ByteArray
            if (chat.kind == ImChatKind.Group) {
                command = CMD_SEND_GROUP
                payload = SendGroupMessageReq(
                    msgId = msgId,
                    groupCode = chat.externalId,
                    fromAccount = botId,
                    random = Random.nextInt(0, Int.MAX_VALUE).toString(),
                    msgBody = body,
                    refMsgId = reply?.messageId.orEmpty(),
                ).encode()
            } else {
                command = CMD_SEND_C2C
                payload = SendC2CMessageReq(
                    msgId = msgId,
                    toAccount = chat.externalId,
                    fromAccount = botId,
                    msgRandom = Random.nextInt(0, Int.MAX_VALUE),
                    msgBody = body,
                ).encode()
            }
            ImLog.d(TAG, "send cmd=$command chat=${chat.kind.name}:${chat.externalId} $label")
            val response = request(command, MODULE_BIZ, payload)
                ?: return ImSendResult(false, msgId, "发送超时")
            if (response.data.isEmpty()) {
                ImLog.w(TAG, "send empty response cmd=$command")
                return ImSendResult(false, msgId, "发送响应为空")
            }
            val result = SendMessageRsp.decode(response.data)
            ImLog.d(TAG, "send response code=${result.code} message=${result.message}")
            return if (result.code == 0) {
                ImSendResult(true, msgId)
            } else {
                ImSendResult(false, msgId, result.message.ifBlank { "发送失败 ${result.code}" })
            }
        }

        suspend fun close() {
            ImLog.i(TAG, "session close")
            try {
                ws.close(CloseReason(CloseReason.Codes.NORMAL, "bye"))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }

        private suspend fun readLoop() {
            for (frame in ws.incoming) {
                val bytes = when (frame) {
                    is Frame.Binary -> frame.readBytes()
                    is Frame.Close -> break
                    else -> continue
                }
                val message = try {
                    ConnMsg.decode(bytes)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    continue
                }
                when (message.head.cmdType) {
                    CMD_TYPE_RESPONSE -> try {
                        onResponse(message)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                    CMD_TYPE_PUSH -> onPush(message)
                    else -> Unit
                }
            }
        }

        private suspend fun onResponse(message: ConnMsg) {
            when (message.head.cmd) {
                CMD_AUTH -> {
                    if (message.data.isEmpty()) {
                        throw YuanbaoRetryException("鉴权响应为空")
                    }
                    auth?.complete(message.head.status to AuthBindRsp.decode(message.data))
                }
                CMD_PING -> {
                    val response = PingRsp.decode(message.data)
                    if (response.heartInterval > 1) heartInterval = response.heartInterval
                    ping?.complete(response)
                }
                else -> pending.remove(message.head.msgId)?.complete(message)
            }
        }

        private suspend fun onPush(message: ConnMsg) {
            if (message.head.needAck) {
                sendFrame(
                    ConnMsg(
                        head = message.head.copy(
                            cmdType = CMD_TYPE_ACK,
                            seqNo = nextSeq(),
                        ),
                    ).encode(),
                )
            }
            if (message.head.cmd == CMD_KICKOUT) {
                val kickout = KickoutMsg.decode(message.data)
                throw YuanbaoFatalException(
                    kickout.reason.ifBlank { "已被踢下线 ${kickout.status}" },
                )
            }
            ImLog.d(
                TAG,
                "push head cmd=${message.head.cmd} module=${message.head.module} " +
                    "msgId=${message.head.msgId} bytes=${message.data.size}",
            )
            ImLog.d(
                TAG,
                "push hex=${message.data.take(60).joinToString("") { "%02x".format(it) }}",
            )
            val direct = safeDecode("direct") { InboundMessagePush.decode(message.data) }
            direct?.let { logInbound("direct", it) }
            if (direct != null && direct.fromAccount.isNotBlank() && direct.msgBody.isNotEmpty()) {
                emitInbound(direct, botId)
                return
            }
            val jsonMessage = safeDecode("json") {
                parseJsonMessage(String(message.data, Charsets.UTF_8))
            }
            if (jsonMessage != null) {
                ImLog.d(TAG, "push json message")
                emitInbound(jsonMessage, botId)
                return
            }
            val push = safeDecode("push") { PushMsg.decode(message.data) }
            push?.let {
                ImLog.d(
                    TAG,
                    "push wrapper cmd=${it.cmd} module=${it.module} msgId=${it.msgId} bytes=${it.data.size}",
                )
            }
            val wrapped = push?.let {
                safeDecode("wrapped") { InboundMessagePush.decode(it.data) }
            }
            wrapped?.let { logInbound("wrapped", it) }
            if (wrapped != null && wrapped.hasContent()) {
                emitInbound(wrapped, botId)
                return
            }
            val wrappedJson = push?.data
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    safeDecode("wrapped-json") {
                        parseJsonMessage(String(it, Charsets.UTF_8))
                    }
                }
            if (wrappedJson != null) {
                ImLog.d(TAG, "push wrapped json")
                emitInbound(wrappedJson, botId)
                return
            }
            if (direct != null && direct.callbackCommand.isNotBlank()) {
                emitInbound(direct, botId)
                return
            }
            val directed = safeDecode("directed") { DirectedPush.decode(message.data) }
            directed?.let {
                ImLog.d(
                    TAG,
                    "directed type=${it.type} contentChars=${it.content.length} " +
                        "content=${it.content.take(120)}",
                )
            }
            val parsed = directed?.content
                ?.takeIf { it.isNotBlank() }
                ?.let { safeDecode("directed-content") { parseJsonMessage(it) } }
            if (parsed != null) {
                emitInbound(parsed, botId)
            } else {
                val base64 = java.util.Base64.getEncoder().encodeToString(message.data)
                base64.chunked(400).forEachIndexed { index, part ->
                    ImLog.d(TAG, "raw[$index]=$part")
                }
                ImLog.d(TAG, "push ignored cmd=${message.head.cmd} bytes=${message.data.size}")
            }
        }

        private suspend fun request(
            command: String,
            module: String,
            payload: ByteArray,
        ): ConnMsg? {
            val msgId = newId()
            val deferred = CompletableDeferred<ConnMsg>()
            pending[msgId] = deferred
            return try {
                sendFrame(
                    ConnMsg(
                        head = Head(
                            cmdType = 0,
                            cmd = command,
                            seqNo = nextSeq(),
                            msgId = msgId,
                            module = module,
                        ),
                        data = payload,
                    ).encode(),
                )
                withTimeoutOrNull(30_000) { deferred.await() }.also {
                    if (it == null) ImLog.w(TAG, "request timeout cmd=$command")
                }
            } finally {
                pending.remove(msgId)
            }
        }

        private suspend fun heartbeatLoop() {
            delay(5_000)
            var failures = 0
            while (true) {
                val deferred = CompletableDeferred<PingRsp>()
                ping = deferred
                sendFrame(
                    ConnMsg(
                        head = Head(
                            cmdType = 0,
                            cmd = CMD_PING,
                            seqNo = nextSeq(),
                            msgId = newId(),
                            module = MODULE_CONN,
                        ),
                    ).encode(),
                )
                val response = withTimeoutOrNull(heartInterval * 1000L) { deferred.await() }
                ping = null
                if (response == null) {
                    failures++
                    ImLog.w(TAG, "heartbeat timeout failures=$failures interval=$heartInterval")
                    if (failures >= 3) throw YuanbaoRetryException("心跳超时")
                } else {
                    failures = 0
                }
                delay((heartInterval - 1).coerceAtLeast(5) * 1000L)
            }
        }

        private suspend fun sendFrame(bytes: ByteArray) {
            sendMutex.withLock { ws.send(Frame.Binary(true, bytes)) }
        }

        private fun nextSeq(): Int = seq.updateAndGet { (it + 1) and Int.MAX_VALUE }

        private fun newId(): String = UUID.randomUUID().toString().replace("-", "")
    }

    @Serializable
    private data class CustomElement(
        @SerialName("elem_type") val elemType: Int = 0,
        val text: String = "",
        @SerialName("user_id") val userId: String = "",
    )

    companion object {
        private const val TAG = "FocusIM/Yuanbao"
        const val FIELD_APP_KEY = "appKey"
        const val FIELD_APP_SECRET = "appSecret"
        private const val WS_URL = "wss://bot-wss.yuanbao.tencent.com/wss/connection"
        private const val CMD_AUTH = "auth-bind"
        private const val CMD_PING = "ping"
        private const val CMD_KICKOUT = "kickout"
        private const val CMD_SEND_C2C = "send_c2c_message"
        private const val CMD_SEND_GROUP = "send_group_message"
        private const val MODULE_CONN = "conn_access"
        private const val MODULE_BIZ = "yuanbao_openclaw_proxy"
        private const val CMD_TYPE_RESPONSE = 1
        private const val CMD_TYPE_PUSH = 2
        private const val CMD_TYPE_ACK = 3
        private const val AUTH_ALREADY = 41101
        private val AUTH_REFRESH = setOf(41103, 41104, 41108)
        private val FATAL_CLOSE_CODES = setOf(4012, 4013, 4014, 4018, 4019, 4021)
        private const val TOKEN_REFRESH_MARGIN_MS = 5 * 60 * 1000L
        private const val TOKEN_FALLBACK_MS = 10 * 60 * 1000L
        private const val MAX_MEDIA_BYTES = 10 * 1024 * 1024
        private const val MAX_FILE_BYTES = 8 * 1024 * 1024
        private val AUTH_FATAL = setOf(
            40100,
            41102,
            41105,
            41106,
            41107,
            41109,
            41110,
            51002,
        )
        private val BACKOFF = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000, 60_000)
    }
}
