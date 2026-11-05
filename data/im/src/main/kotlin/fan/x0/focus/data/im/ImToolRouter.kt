package fan.x0.focus.data.im

import fan.x0.focus.data.assistant.ChatStore
import fan.x0.focus.data.assistant.ImToolBridge
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.koin.core.annotation.Single

@Single
class ImToolRouter(
    private val accounts: ImAccountStore,
    private val chats: ImChatStore,
    private val conversations: ChatStore,
    private val gateway: ImGateway,
) : ImToolBridge {
    override suspend fun listAccounts(): String {
        val statuses = gateway.statuses.value
        val list = accounts.list()
        return buildJsonObject {
            putJsonArray("accounts") {
                list.forEach { account ->
                    val status = statuses[account.id]
                    add(
                        buildJsonObject {
                            put("id", account.id)
                            put("channel", account.channel)
                            put("name", account.name)
                            put("enabled", account.enabled)
                            put("status", (status?.state ?: account.status).name)
                            put("detail", status?.detail ?: account.statusDetail)
                        },
                    )
                }
            }
        }.toString()
    }

    override suspend fun listChats(accountId: String?): String {
        val list = chats.list().filter {
            accountId.isNullOrBlank() || it.accountId == accountId
        }
        return buildJsonObject {
            putJsonArray("chats") {
                list.forEach { chat ->
                    add(
                        buildJsonObject {
                            put("id", chat.id)
                            put("account_id", chat.accountId)
                            put("account", chat.accountName)
                            put("title", chat.title)
                            put("kind", chat.kind.name)
                            put("enabled", chat.enabled)
                            put("send_thinking", chat.sendThinking)
                            put("send_tools", chat.sendTools)
                            put("conversation_id", chat.conversationId.orEmpty())
                            put("conversation", chat.conversationTitle.orEmpty())
                            put("last_message_at", chat.lastMessageAt)
                        },
                    )
                }
            }
        }.toString()
    }

    override suspend fun listConversations(): String {
        val list = conversations.conversations()
        return buildJsonObject {
            putJsonArray("conversations") {
                list.forEach { conversation ->
                    add(
                        buildJsonObject {
                            put("id", conversation.id)
                            put("title", conversation.title)
                            put("updated_at", conversation.updatedAt)
                        },
                    )
                }
            }
        }.toString()
    }

    override suspend fun bindChat(chatId: String, conversationId: String): String {
        val chat = chats.chatById(chatId) ?: return "Chat not found"
        val conversation = conversations.conversation(conversationId)
            ?: return "Conversation not found"
        chats.setConversation(chat.id, conversation.id)
        return "Bound ${chat.title} to ${conversation.title}"
    }

    override suspend fun setChatEnabled(chatId: String, enabled: Boolean): String {
        val chat = chats.chatById(chatId) ?: return "Chat not found"
        chats.setEnabled(chat.id, enabled)
        return if (enabled) "Resumed ${chat.title}" else "Paused ${chat.title}"
    }

    override suspend fun setChatProgress(chatId: String, thinking: Boolean, tools: Boolean): String {
        val chat = chats.chatById(chatId) ?: return "Chat not found"
        chats.setSendThinking(chat.id, thinking)
        chats.setSendTools(chat.id, tools)
        return "Updated ${chat.title}"
    }

    override suspend fun sendText(chatId: String, text: String): String {
        if (text.isBlank()) return "Message is empty"
        val result = gateway.sendToChat(chatId, text)
        return if (result.ok) "Message sent" else "Send failed: ${result.error}"
    }
}
