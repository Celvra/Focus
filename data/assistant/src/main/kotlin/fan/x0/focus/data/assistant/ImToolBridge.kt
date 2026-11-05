package fan.x0.focus.data.assistant

interface ImToolBridge {
    suspend fun listAccounts(): String

    suspend fun listChats(accountId: String?): String

    suspend fun listConversations(): String

    suspend fun bindChat(chatId: String, conversationId: String): String

    suspend fun setChatEnabled(chatId: String, enabled: Boolean): String

    suspend fun setChatProgress(chatId: String, thinking: Boolean, tools: Boolean): String

    suspend fun sendText(chatId: String, text: String): String
}
