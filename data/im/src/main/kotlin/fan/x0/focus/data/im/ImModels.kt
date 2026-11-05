package fan.x0.focus.data.im

import kotlinx.serialization.Serializable

enum class ImChatKind {
    Direct,
    Group,
}

enum class ImTextFormat {
    Plain,
    Markdown,
}

enum class ImConnectionState {
    Disabled,
    Connecting,
    Connected,
    AuthRequired,
    Error,
}

data class ImChatRef(
    val externalId: String,
    val kind: ImChatKind,
    val title: String,
)

data class ImUserRef(
    val id: String,
    val name: String,
)

data class ImInbound(
    val messageId: String,
    val chat: ImChatRef,
    val sender: ImUserRef,
    val text: String,
    val isBotMentioned: Boolean,
    val sentAt: Long,
    val platformContext: String? = null,
    val ownerId: String? = null,
)

data class ImOutbound(
    val text: String,
    val replyToMessageId: String? = null,
)

data class ImSendTarget(
    val chat: ImChatRef,
    val context: String? = null,
)

data class ImSendResult(
    val ok: Boolean,
    val messageId: String = "",
    val error: String = "",
)

data class ImCapabilities(
    val maxTextLength: Int,
    val textFormat: ImTextFormat,
    val chatKinds: Set<ImChatKind>,
    val supportsReplyRef: Boolean,
    val supportsMedia: Boolean,
    val minSendIntervalMs: Long,
)

data class ImField(
    val key: String,
    val label: String,
    val secret: Boolean,
    val required: Boolean,
)

sealed interface ImAuthSpec {
    data class Fields(val fields: List<ImField>) : ImAuthSpec
    data object Qr : ImAuthSpec
}

data class ImChannelDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val auth: ImAuthSpec,
    val capabilities: ImCapabilities,
)

sealed interface ImEvent {
    data class Inbound(val message: ImInbound) : ImEvent
    data class State(val state: ImConnectionState, val detail: String = "") : ImEvent
    data class Login(val challenge: ImLoginChallenge) : ImEvent
    data class Failure(val message: String) : ImEvent
}

sealed interface ImLoginChallenge {
    data class Qr(val content: String, val expiresAt: Long) : ImLoginChallenge
    data class Code(val prompt: String) : ImLoginChallenge
    data object Done : ImLoginChallenge
}

@Serializable
data class ImAccountConfig(
    val values: Map<String, String> = emptyMap(),
    val requireMention: Boolean = true,
    val onlyOwnerInGroups: Boolean = false,
    val allowedSenders: Set<String> = emptySet(),
    val baseUrl: String = "",
)

data class ImAccount(
    val id: String,
    val channel: String,
    val name: String,
    val enabled: Boolean,
    val status: ImConnectionState,
    val statusDetail: String,
    val platformUserId: String,
    val ownerId: String,
    val cursor: String,
    val config: ImAccountConfig,
)

data class ImAccountDraft(
    val id: String,
    val channel: String,
    val name: String,
    val values: Map<String, String>,
    val enabled: Boolean,
    val requireMention: Boolean,
    val onlyOwnerInGroups: Boolean,
    val baseUrl: String = "",
)

data class ImChat(
    val id: String,
    val accountId: String,
    val accountName: String,
    val channel: String,
    val externalId: String,
    val kind: ImChatKind,
    val title: String,
    val conversationId: String?,
    val conversationTitle: String?,
    val enabled: Boolean,
    val sendThinking: Boolean = true,
    val sendTools: Boolean = true,
    val platformContext: String,
    val lastMessageAt: Long,
)

data class ImAccountStatus(
    val state: ImConnectionState,
    val detail: String = "",
)

object ImSecretKeys {
    const val TOKEN = "token"

    fun field(accountId: String, key: String): String = "im:$accountId:$key"
}
