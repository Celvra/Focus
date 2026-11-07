package fan.x0.focus.data.im

import fan.x0.focus.core.notify.NotifyActionKind
import fan.x0.focus.data.assistant.ChatAction
import fan.x0.focus.data.assistant.ChatAttachment
import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ChatTurnProgress
import fan.x0.focus.data.assistant.ChatTurnResult
import fan.x0.focus.data.assistant.ChatTurnRunner
import fan.x0.focus.data.assistant.ReasoningLevel
import fan.x0.focus.data.assistant.TurnOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap

@Single
class ImGateway(
    private val accounts: ImAccountStore,
    private val chats: ImChatStore,
    private val seen: ImSeenStore,
    private val conversations: ChatStore,
    private val turns: ChatTurnRunner,
    private val registry: ImChannelRegistry,
    private val media: ImMediaMapper,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reloadMutex = Mutex()
    private val active = ConcurrentHashMap<String, Active>()
    private val activeTurns = ConcurrentHashMap<String, String>()
    private val _statuses = MutableStateFlow<Map<String, ImAccountStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ImAccountStatus>> = _statuses.asStateFlow()
    private val _logins = MutableStateFlow<Map<String, ImLoginChallenge>>(emptyMap())
    val logins: StateFlow<Map<String, ImLoginChallenge>> = _logins.asStateFlow()

    init {
        scope.launch {
            seen.prune(System.currentTimeMillis() - SEEN_RETENTION_MS)
        }
        scope.launch {
            turns.actions.collect { action -> onAction(action) }
        }
    }

    suspend fun reload() = reloadMutex.withLock {
        ImLog.i(TAG, "reload")
        stopAllLocked()
        startAllLocked()
    }

    suspend fun stopAll() = reloadMutex.withLock {
        stopAllLocked()
    }

    suspend fun stop(accountId: String) = reloadMutex.withLock {
        active.remove(accountId)?.let { stop(it) }
        _logins.update { it - accountId }
    }

    suspend fun restart(accountId: String) = reloadMutex.withLock {
        val account = accounts.account(accountId) ?: return@withLock
        active.remove(accountId)?.let { stop(it) }
        _logins.update { it - accountId }
        if (account.enabled) start(account)
    }

    suspend fun submitLoginCode(accountId: String, code: String): Boolean {
        val channel = active[accountId]?.channel as? ImLoginChannel ?: return false
        return channel.submitLoginCode(code)
    }

    private suspend fun startAllLocked() {
        accounts.enabled().forEach { start(it) }
    }

    private suspend fun stopAllLocked() {
        active.values.toList().forEach { stop(it) }
        active.clear()
        _statuses.value = emptyMap()
        _logins.value = emptyMap()
    }

    private suspend fun stop(item: Active) {
        ImLog.i(TAG, "stop account=${item.account.id}")
        _logins.update { it - item.account.id }
        item.queues.values.forEach { it.close() }
        item.queues.clear()
        item.job.cancel()
        try {
            item.channel.disconnect()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
        accounts.setStatus(item.account.id, ImConnectionState.Disabled, "")
    }

    private fun start(account: ImAccount) {
        if (active.containsKey(account.id)) return
        ImLog.i(TAG, "start account=${account.id} channel=${account.channel} name=${account.name}")
        _logins.update { it - account.id }
        val channel = registry.create(account) ?: run {
            updateStatus(account.id, ImConnectionState.Error, "没有找到渠道适配器")
            return
        }
        val parent = scope.coroutineContext[Job]
        val job = SupervisorJob(parent)
        val activeScope = CoroutineScope(scope.coroutineContext + job + Dispatchers.IO)
        val item = Active(
            account = account,
            channel = channel,
            scope = activeScope,
            queues = ConcurrentHashMap(),
            pending = ConcurrentHashMap(),
            job = job,
        )
        active[account.id] = item
        updateStatus(account.id, ImConnectionState.Connecting)
        activeScope.launch(start = CoroutineStart.UNDISPATCHED) {
            channel.events.collect { event -> handleEvent(item, event) }
        }
        activeScope.launch {
            try {
                channel.connect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                updateStatus(account.id, ImConnectionState.Error, e.message.orEmpty())
            }
        }
    }

    private suspend fun handleEvent(item: Active, event: ImEvent) {
        when (event) {
            is ImEvent.Inbound -> enqueue(item, event.message)
            is ImEvent.State -> {
                updateStatus(item.account.id, event.state, event.detail)
                when (event.state) {
                    ImConnectionState.Connected,
                    ImConnectionState.AuthRequired,
                    ImConnectionState.Disabled,
                    -> _logins.update { it - item.account.id }
                    else -> Unit
                }
            }
            is ImEvent.Failure -> updateStatus(item.account.id, ImConnectionState.Error, event.message)
            is ImEvent.Login -> {
                when (val challenge = event.challenge) {
                    ImLoginChallenge.Done -> _logins.update { it - item.account.id }
                    is ImLoginChallenge.Qr -> {
                        _logins.update { it + (item.account.id to challenge) }
                        updateStatus(item.account.id, ImConnectionState.Connecting, "等待微信扫码")
                    }
                    is ImLoginChallenge.Code -> {
                        _logins.update { it + (item.account.id to challenge) }
                        updateStatus(item.account.id, ImConnectionState.Connecting, "等待输入验证码")
                    }
                }
            }
        }
    }

    private suspend fun enqueue(item: Active, message: ImInbound) {
        val account = item.account
        ImLog.d(
            TAG,
            "recv account=${account.id} chat=${message.chat.kind.name}:${message.chat.externalId} " +
                "sender=${message.sender.name}(${message.sender.id}) text=${message.text.take(80)}",
        )
        if (message.sender.id == account.platformUserId) {
            ImLog.d(TAG, "skip self ${message.sender.id}")
            return
        }
        val ownerId = account.ownerId.ifBlank { message.ownerId.orEmpty() }
        if (account.ownerId.isBlank() && ownerId.isNotBlank()) {
            accounts.setOwnerId(account.id, ownerId)
            item.account = account.copy(ownerId = ownerId)
        }
        val fresh = try {
            seen.mark(account.id, message.messageId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
        if (!fresh) {
            ImLog.d(TAG, "skip duplicate ${message.messageId}")
            return
        }
        val allowed = account.config.allowedSenders
        if (allowed.isNotEmpty() && message.sender.id !in allowed) {
            ImLog.d(TAG, "skip sender ${message.sender.id}")
            return
        }
        if (message.chat.kind == ImChatKind.Group && account.config.onlyOwnerInGroups) {
            if (ownerId.isBlank() || message.sender.id != ownerId) {
                ImLog.d(TAG, "skip group non-owner ${message.sender.id}")
                return
            }
        }
        val key = "${message.chat.kind.name}:${message.chat.externalId}"
        if (message.chat.kind == ImChatKind.Group && account.config.requireMention) {
            if (!message.isBotMentioned) {
                val line = media.messageLine(message)
                if (line.isNotBlank()) {
                    val files = message.attachments.mapNotNull { media.toChatAttachment(it) }
                    recordPending(item, key, "${message.sender.name}: $line", files)
                }
                ImLog.d(TAG, "record group without mention chat=${message.chat.externalId}")
                return
            }
        }
        val queue = item.queues.getOrPut(key) {
            Channel<ImInbound>(Channel.UNLIMITED).also { channel ->
                item.scope.launch { consume(item, channel) }
            }
        }
        queue.trySend(message)
    }

    private fun recordPending(
        item: Active,
        key: String,
        line: String,
        attachments: List<ChatAttachment>,
    ) {
        val list = item.pending.getOrPut(key) { mutableListOf() }
        synchronized(list) {
            list.add(PendingLine(line, attachments))
            while (list.size > PENDING_LIMIT) list.removeAt(0)
            var count = list.sumOf { it.attachments.size }
            while (count > PENDING_ATTACHMENT_LIMIT) {
                val index = list.indexOfFirst { it.attachments.isNotEmpty() }
                if (index < 0) break
                val entry = list[index]
                list[index] = entry.copy(attachments = emptyList())
                count -= entry.attachments.size
            }
        }
    }

    private fun takePending(item: Active, key: String): List<PendingLine> {
        val list = item.pending.remove(key) ?: return emptyList()
        synchronized(list) {
            return list.toList()
        }
    }

    private suspend fun consume(item: Active, queue: Channel<ImInbound>) {
        for (message in queue) {
            try {
                handle(item, message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ImLog.w(TAG, "handle ${item.account.id} failed", e)
            }
        }
    }

    private suspend fun handle(item: Active, message: ImInbound) {
        val account = item.account
        val chat = chats.ensure(account, message.chat, message.sentAt)
        if (!chat.enabled) return
        chats.setContext(chat.id, message.platformContext.orEmpty())
        if (message.text.isBlank() && message.attachments.isEmpty()) {
            if (chat.kind == ImChatKind.Direct) {
                sendText(item, chat, "暂不支持这种消息类型", message.platformContext)
            }
            return
        }
        val pending = takePending(item, "${message.chat.kind.name}:${message.chat.externalId}")
        val conversationId = ensureConversation(account, chat)
        activeTurns[conversationId] = chat.id
        val prompt = buildPrompt(chat, message, pending)
        val files = pending.flatMap { it.attachments } +
            message.attachments.mapNotNull { media.toChatAttachment(it) }
        ImLog.i(
            TAG,
            "turn start conversation=$conversationId chat=${chat.title} chars=${prompt.length}",
        )
        val conversation = conversations.conversation(conversationId)
        val reasoning = ReasoningLevel.fromName(conversation?.reasoning)
        val sendThinking = chat.sendThinking && reasoning.isEnabled
        val notifier = if (sendThinking || chat.sendTools) {
            ImTurnProgress(
                sendThinking = sendThinking,
                sendTools = chat.sendTools,
                send = { text -> sendText(item, chat, text, message.platformContext) },
            )
        } else {
            null
        }
        val progressChannel = if (notifier != null) {
            Channel<ChatTurnProgress>(Channel.UNLIMITED)
        } else {
            null
        }
        val progressJob = if (progressChannel != null && notifier != null) {
            item.scope.launch {
                for (progress in progressChannel) notifier.onProgress(progress)
                notifier.finish()
            }
        } else {
            null
        }
        val result = try {
            turns.startAndAwait(
                conversationId = conversationId,
                text = prompt,
                options = TurnOptions(
                    providerId = conversation?.providerId.orEmpty(),
                    model = conversation?.model.orEmpty(),
                    reasoning = reasoning,
                    attachments = files,
                    silentReply = true,
                    autoTitle = false,
                ),
                progress = progressChannel,
            )
        } finally {
            progressChannel?.close()
            if (progressJob != null) {
                withContext(NonCancellable) { progressJob.join() }
            }
            activeTurns.remove(conversationId)
        }
        ImLog.i(
            TAG,
            "turn done conversation=$conversationId error=${result?.errorText().orEmpty()} " +
                "answerChars=${result?.answer?.length ?: 0}",
        )
        val context = message.platformContext
        val reply = ImReply(
            messageId = message.messageId,
            senderName = message.sender.name,
            text = media.messageLine(message).take(MAX_REPLY_CHARS),
        )
        when {
            result == null -> sendText(item, chat, "处理超时，请稍后重试。", context, reply)
            result.error != null -> sendText(item, chat, "处理失败：${result.errorText()}", context, reply)
            result.answer.isBlank() -> sendText(item, chat, "已完成。", context, reply)
            else -> sendText(item, chat, result.answer, context, reply)
        }
        if (result != null && result.error == null && result.media.isNotEmpty()) {
            sendAttachments(item, chat, result.media, context)
        }
    }

    private fun buildPrompt(chat: ImChat, message: ImInbound, pending: List<PendingLine>): String = buildString {
        if (chat.kind == ImChatKind.Group && pending.isNotEmpty()) {
            appendLine("[群聊中最近的未提及消息]")
            pending.forEach { appendLine(it.line) }
            appendLine()
            appendLine("[当前消息]")
        }
        message.reply?.let { quote ->
            val sender = quote.senderName.ifBlank { "未知" }
            appendLine("[引用 $sender: ${quote.text.take(MAX_REPLY_CHARS)}]")
        }
        val line = media.messageLine(message)
        if (chat.kind == ImChatKind.Group) {
            append("${message.sender.name}: $line")
        } else {
            append(line)
        }
    }

    private suspend fun sendAttachments(
        item: Active,
        chat: ImChat,
        attachments: List<ChatAttachment>,
        context: String?,
    ) {
        if (attachments.isEmpty()) return
        val descriptor = registry.descriptor(item.account.channel) ?: return
        if (!descriptor.capabilities.supportsMedia) {
            ImLog.d(TAG, "skip media unsupported channel=${item.account.channel}")
            return
        }
        attachments.forEach { attachment ->
            val file = media.toImAttachment(attachment) ?: return@forEach
            val result = try {
                item.channel.send(
                    target = ImSendTarget(
                        chat = ImChatRef(chat.externalId, chat.kind, chat.title),
                        context = context ?: chat.platformContext.ifBlank { null },
                    ),
                    message = ImOutbound(text = "", attachments = listOf(file)),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ImLog.w(TAG, "send media ${item.account.id} failed", e)
                ImSendResult(false, error = e.message.orEmpty())
            }
            if (!result.ok) {
                ImLog.w(TAG, "send media ${item.account.id} rejected: ${result.error}")
            }
        }
    }

    private suspend fun ensureConversation(account: ImAccount, chat: ImChat): String {
        val existing = chat.conversationId?.let { conversations.conversation(it) }
        if (existing != null && !existing.archived) return existing.id
        val title = "${account.name} / ${chat.title}".take(80)
        val conversation = conversations.createConversation(title)
        ImLog.i(TAG, "create conversation ${conversation.id} title=$title")
        chats.setConversation(chat.id, conversation.id)
        return conversation.id
    }

    private suspend fun sendText(
        item: Active,
        chat: ImChat,
        text: String,
        context: String? = null,
        reply: ImReply? = null,
    ): ImSendResult {
        if (text.isBlank()) return ImSendResult(false, error = "Empty message")
        val descriptor = registry.descriptor(item.account.channel)
            ?: return ImSendResult(false, error = "Channel not found")
        val chunks = ImChunker.split(text, descriptor.capabilities.maxTextLength)
        ImLog.d(TAG, "send ${item.account.id} chat=${chat.title} chars=${text.length} chunks=${chunks.size}")
        chunks.forEachIndexed { index, chunk ->
            val quoted = reply?.takeIf { index == 0 && descriptor.capabilities.supportsReplyRef }
            val result = try {
                item.channel.send(
                    target = ImSendTarget(
                        chat = ImChatRef(chat.externalId, chat.kind, chat.title),
                        context = context ?: chat.platformContext.ifBlank { null },
                    ),
                    message = ImOutbound(chunk, reply = quoted),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ImLog.w(TAG, "send ${item.account.id} failed", e)
                ImSendResult(false, error = e.message.orEmpty())
            }
            if (!result.ok) {
                ImLog.w(TAG, "send ${item.account.id} rejected: ${result.error}")
                val state = _statuses.value[item.account.id]?.state ?: ImConnectionState.Error
                updateStatus(item.account.id, state, "发送失败: ${result.error}")
                return result
            }
            ImLog.d(TAG, "send ok ${item.account.id} chunk=${index + 1}/${chunks.size}")
            if (index < chunks.lastIndex) {
                delay(descriptor.capabilities.minSendIntervalMs)
            }
        }
        return ImSendResult(true)
    }

    suspend fun sendToChat(chatId: String, text: String): ImSendResult {
        val chat = chats.chatById(chatId) ?: return ImSendResult(false, error = "Chat not found")
        if (!chat.enabled) return ImSendResult(false, error = "Chat is paused")
        val item = active[chat.accountId] ?: return ImSendResult(false, error = "Account is not running")
        return sendText(item, chat, text)
    }

    private suspend fun onAction(action: ChatAction) {
        val chatId = activeTurns[action.conversationId] ?: return
        val chat = chats.chatById(chatId) ?: return
        val item = active[chat.accountId] ?: return
        ImLog.i(TAG, "action notice conversation=${action.conversationId} kind=${action.kind}")
        val text = when (action.kind) {
            NotifyActionKind.Approval -> "需要你在 Focus 里确认一次操作。"
            NotifyActionKind.Plan -> "需要你在 Focus 里确认计划。"
            NotifyActionKind.Question -> "需要你在 Focus 里回答一个问题。"
            NotifyActionKind.Workspace -> "需要你在 Focus 里确认工作区权限。"
        }
        if (!chat.enabled) return
        sendText(item, chat, text)
    }

    private fun ChatTurnResult.errorText(): String =
        error?.let { it.serverMessage ?: it.serverTitle ?: it.type.name }.orEmpty().ifBlank { "未知错误" }

    private fun updateStatus(accountId: String, state: ImConnectionState, detail: String = "") {
        _statuses.update { it + (accountId to ImAccountStatus(state, detail)) }
        scope.launch { accounts.setStatus(accountId, state, detail) }
    }

    private class Active(
        @Volatile var account: ImAccount,
        val channel: ImChannel,
        val scope: CoroutineScope,
        val queues: ConcurrentHashMap<String, Channel<ImInbound>>,
        val pending: ConcurrentHashMap<String, MutableList<PendingLine>>,
        val job: Job,
    )

    private data class PendingLine(
        val line: String,
        val attachments: List<ChatAttachment> = emptyList(),
    )

    companion object {
        private const val TAG = "ImGateway"
        private const val SEEN_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        private const val PENDING_LIMIT = 30
        private const val PENDING_ATTACHMENT_LIMIT = 8
        private const val MAX_REPLY_CHARS = 500
    }
}
