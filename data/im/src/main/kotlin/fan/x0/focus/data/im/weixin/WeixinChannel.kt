package fan.x0.focus.data.im.weixin

import fan.x0.focus.data.assistant.SecretStore
import fan.x0.focus.data.im.ImAccount
import fan.x0.focus.data.im.ImAccountStore
import fan.x0.focus.data.im.ImChannel
import fan.x0.focus.data.im.ImChatKind
import fan.x0.focus.data.im.ImChatRef
import fan.x0.focus.data.im.ImConnectionState
import fan.x0.focus.data.im.ImEvent
import fan.x0.focus.data.im.ImInbound
import fan.x0.focus.data.im.ImLoginChallenge
import fan.x0.focus.data.im.ImLoginChannel
import fan.x0.focus.data.im.ImLog
import fan.x0.focus.data.im.ImOutbound
import fan.x0.focus.data.im.ImSecretKeys
import fan.x0.focus.data.im.ImSendResult
import fan.x0.focus.data.im.ImSendTarget
import fan.x0.focus.data.im.ImUserRef
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal class WeixinChannel(
    private val account: ImAccount,
    private val accounts: ImAccountStore,
    private val secrets: SecretStore,
    private val http: HttpClient,
) : ImChannel, ImLoginChannel {
    private val api = WeixinHttp(http)
    private val _state = MutableStateFlow(ImConnectionState.Disabled)
    override val state: StateFlow<ImConnectionState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<ImEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<ImEvent> = _events.asSharedFlow()

    @Volatile private var closed = false
    @Volatile private var baseUrl = account.config.baseUrl.ifBlank { WeixinHttp.DEFAULT_BASE_URL }
    @Volatile private var botId = account.platformUserId
    private var codeDeferred: CompletableDeferred<String>? = null
    private val codeLock = Mutex()

    override suspend fun connect() {
        closed = false
        emitState(ImConnectionState.Connecting)
        if (token().isBlank()) {
            if (!login()) return
        }
        monitor()
    }

    override suspend fun send(target: ImSendTarget, message: ImOutbound): ImSendResult {
        val token = token()
        if (token.isBlank()) return ImSendResult(false, error = "微信未登录")
        val clientId = "focus-weixin:${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"
        ImLog.d(
            TAG,
            "send to=${target.chat.externalId} chars=${message.text.length}",
        )
        return try {
            val response = api.sendText(
                baseUrl = baseUrl,
                token = token,
                toUserId = target.chat.externalId,
                text = message.text,
                contextToken = target.context,
                clientId = clientId,
            )
            if (!response.ok) ImLog.w(TAG, "send failed: ${response.error}")
            ImSendResult(response.ok, response.messageId, response.error)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            ImLog.e(TAG, "send failed", e)
            ImSendResult(false, error = e.message.orEmpty().ifBlank { "发送失败" })
        }
    }

    override suspend fun disconnect() {
        closed = true
        codeLock.withLock { codeDeferred }?.complete("")
        val token = token()
        if (token.isNotBlank()) {
            withTimeoutOrNull(1_500) {
                runCatching { api.notify(baseUrl, token, start = false) }
            }
        }
    }

    override suspend fun submitLoginCode(code: String): Boolean {
        val deferred = codeLock.withLock { codeDeferred } ?: return false
        if (code.isBlank()) return false
        return deferred.complete(code.trim())
    }

    private suspend fun login(): Boolean {
        ImLog.i(TAG, "login start")
        var refreshCount = 0
        while (!closed) {
            val qr = try {
                api.fetchQrCode(listOfNotNull(token().takeIf { it.isNotBlank() }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ImLog.e(TAG, "fetch qr failed", e)
                emitState(ImConnectionState.Error, e.message.orEmpty().ifBlank { "获取二维码失败" })
                delay(5_000)
                continue
            }
            ImLog.i(TAG, "qr ready url=${qr.content.take(80)}")
            emitLogin(ImLoginChallenge.Qr(qr.content, System.currentTimeMillis() + QR_TTL_MS))
            emitState(ImConnectionState.Connecting, "等待微信扫码")

            var verifyCode: String? = null
            var codeAttempts = 0
            var refresh = false
            var pollBaseUrl = baseUrl
            val deadline = System.currentTimeMillis() + LOGIN_TIMEOUT_MS
            while (!closed && System.currentTimeMillis() < deadline) {
                val status = api.pollStatus(pollBaseUrl, qr.qrcode, verifyCode)
                if (status == null) {
                    delay(500)
                    continue
                }
                when (status.status) {
                    "wait" -> Unit
                    "scaned" -> {
                        verifyCode = null
                        emitState(ImConnectionState.Connecting, "已扫码，请在手机上确认")
                    }
                    "need_verifycode" -> {
                        codeAttempts++
                        val prompt = if (codeAttempts > 1) {
                            "验证码不正确，请重新输入"
                        } else {
                            "输入手机微信显示的数字"
                        }
                        val code = askCode(prompt)
                        if (code.isNullOrBlank()) {
                            emitState(ImConnectionState.AuthRequired, "未输入验证码")
                            return false
                        }
                        verifyCode = code
                        continue
                    }
                    "scaned_but_redirect" -> {
                        if (status.redirectHost.isNotBlank()) {
                            pollBaseUrl = "https://${status.redirectHost}"
                            ImLog.i(TAG, "redirect host=${status.redirectHost}")
                        }
                    }
                    "expired", "verify_code_blocked" -> {
                        refresh = true
                        break
                    }
                    "binded_redirect" -> {
                        emitState(
                            ImConnectionState.AuthRequired,
                            "该微信已绑定过，本地没有凭据，请重新登录",
                        )
                        return false
                    }
                    "confirmed" -> {
                        if (status.botToken.isBlank() || status.botId.isBlank()) {
                            emitState(ImConnectionState.AuthRequired, "登录响应缺少凭据")
                            return false
                        }
                        saveLogin(status)
                        emitLogin(ImLoginChallenge.Done)
                        emitState(ImConnectionState.Connected, "微信登录成功")
                        return true
                    }
                }
                delay(500)
            }
            if (closed) return false
            if (!refresh) {
                emitState(ImConnectionState.AuthRequired, "登录超时，请重新扫码")
                return false
            }
            refreshCount++
            if (refreshCount > MAX_QR_REFRESH) {
                emitState(ImConnectionState.AuthRequired, "二维码多次失效，请重新登录")
                return false
            }
            ImLog.i(TAG, "refresh qr count=$refreshCount")
        }
        return false
    }

    private suspend fun monitor() {
        var cursor = account.cursor
        var failures = 0
        var timeoutMs = DEFAULT_LONG_POLL_MS
        emitState(ImConnectionState.Connected)
        val initialToken = token()
        if (initialToken.isBlank()) {
            emitState(ImConnectionState.AuthRequired, "微信未登录")
            return
        }
        withTimeoutOrNull(3_000) {
            runCatching { api.notify(baseUrl, initialToken, start = true) }
        }
        ImLog.i(TAG, "monitor start base=$baseUrl")
        while (!closed) {
            val token = token()
            if (token.isBlank()) {
                emitState(ImConnectionState.AuthRequired, "微信未登录")
                return
            }
            val response = try {
                api.updates(baseUrl, token, cursor, timeoutMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failures++
                ImLog.w(TAG, "getUpdates failed", e)
                emitState(ImConnectionState.Connecting, "微信连接异常")
                delay(backoff(failures))
                continue
            }
            if (response.timedOut) {
                failures = 0
                continue
            }
            if (response.ret != 0 || response.errcode != 0) {
                if (response.ret == STALE_TOKEN || response.errcode == STALE_TOKEN) {
                    emitState(ImConnectionState.AuthRequired, "微信登录已失效，请重新扫码")
                    return
                }
                failures++
                ImLog.w(
                    TAG,
                    "getUpdates ret=${response.ret} errcode=${response.errcode} " +
                        "errmsg=${response.errmsg}",
                )
                delay(if (failures >= 3) 30_000 else 2_000)
                if (failures >= 3) failures = 0
                continue
            }
            failures = 0
            if (response.timeoutMs in 5_000L..60_000L) timeoutMs = response.timeoutMs
            if (response.cursor.isNotBlank() && response.cursor != cursor) {
                cursor = response.cursor
                accounts.setCursor(account.id, cursor)
            }
            response.messages.forEach(::emitInbound)
        }
    }

    private suspend fun askCode(prompt: String): String? {
        val deferred = CompletableDeferred<String>()
        codeLock.withLock { codeDeferred = deferred }
        emitLogin(ImLoginChallenge.Code(prompt))
        return try {
            withTimeoutOrNull(CODE_TIMEOUT_MS) { deferred.await() }
        } finally {
            codeLock.withLock {
                if (codeDeferred === deferred) codeDeferred = null
            }
        }
    }

    private suspend fun saveLogin(status: WeixinQrStatus) {
        val newBaseUrl = status.baseUrl.trim().ifBlank { baseUrl }
        secrets.setSecret(ImSecretKeys.field(account.id, ImSecretKeys.TOKEN), status.botToken.trim())
        accounts.setLogin(
            id = account.id,
            platformUserId = status.botId.trim(),
            ownerId = status.userId.trim(),
            baseUrl = newBaseUrl,
        )
        baseUrl = newBaseUrl
        botId = status.botId.trim()
        ImLog.i(TAG, "login saved bot=$botId base=$baseUrl")
    }

    private fun emitInbound(message: WeixinInbound) {
        if (message.messageType == MESSAGE_TYPE_BOT) return
        if (message.fromUserId == botId) return
        val title = message.fromUserId.removeSuffix("@im.wechat").ifBlank { "微信用户" }
        val chat = ImChatRef(
            externalId = message.fromUserId,
            kind = ImChatKind.Direct,
            title = title,
        )
        ImLog.i(
            TAG,
            "inbound from=${message.fromUserId} chars=${message.text.length} " +
                "context=${message.contextToken.isNotBlank()}",
        )
        _events.tryEmit(
            ImEvent.Inbound(
                ImInbound(
                    messageId = message.messageId,
                    chat = chat,
                    sender = ImUserRef(message.fromUserId, title),
                    text = message.text,
                    isBotMentioned = true,
                    sentAt = if (message.sentAt > 0) message.sentAt else System.currentTimeMillis(),
                    platformContext = message.contextToken.ifBlank { null },
                ),
            ),
        )
    }

    private suspend fun token(): String =
        secrets.secret(ImSecretKeys.field(account.id, ImSecretKeys.TOKEN)).orEmpty()

    private fun emitState(state: ImConnectionState, detail: String = "") {
        _state.value = state
        _events.tryEmit(ImEvent.State(state, detail))
    }

    private fun emitLogin(challenge: ImLoginChallenge) {
        _events.tryEmit(ImEvent.Login(challenge))
    }

    private fun backoff(attempt: Int): Long =
        (2_000L shl attempt.coerceIn(0, 4)).coerceAtMost(30_000L)

    companion object {
        private const val TAG = "FocusIM/Weixin"
        private const val MESSAGE_TYPE_BOT = 2
        private const val STALE_TOKEN = -14
        private const val QR_TTL_MS = 5 * 60_000L
        private const val LOGIN_TIMEOUT_MS = 8 * 60_000L
        private const val CODE_TIMEOUT_MS = 5 * 60_000L
        private const val MAX_QR_REFRESH = 3
        private const val DEFAULT_LONG_POLL_MS = 35_000L
    }
}
