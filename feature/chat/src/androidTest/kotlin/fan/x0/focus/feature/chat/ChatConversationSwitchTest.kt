package fan.x0.focus.feature.chat

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import fan.x0.focus.core.designsystem.theme.FocusTheme
import org.junit.Rule
import org.junit.Test

class ChatConversationSwitchTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sameSizeConversationSwitchShowsLatestMessageWithoutTouch() {
        assertConversationSwitch(reuseMessageKeys = false)
    }

    @Test
    fun reusedMessageKeysDoNotKeepPreviousConversationState() {
        assertConversationSwitch(reuseMessageKeys = true)
    }

    @Test
    fun interruptedSwitchRemovesBothPreviousConversations() {
        val current = mutableStateOf(conversation("first", reuseMessageKeys = true))
        val snackbar = SnackbarHostState()
        compose.setContent {
            FocusTheme {
                ChatScreen(state = current.value, onAction = {}, snackbar = snackbar)
            }
        }
        compose.onNodeWithText("first message 39").assertIsDisplayed()
        compose.mainClock.autoAdvance = false

        compose.runOnUiThread {
            current.value = conversation("second", reuseMessageKeys = true)
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        compose.runOnUiThread {
            current.value = conversation("third", reuseMessageKeys = true)
        }
        compose.mainClock.autoAdvance = true

        compose.onNodeWithText("third message 39").assertIsDisplayed()
        compose.onNodeWithText("first message 39").assertDoesNotExist()
        compose.onNodeWithText("second message 39").assertDoesNotExist()
    }

    private fun assertConversationSwitch(reuseMessageKeys: Boolean) {
        val current = mutableStateOf(conversation("first", reuseMessageKeys))
        val snackbar = SnackbarHostState()
        compose.setContent {
            FocusTheme {
                ChatScreen(state = current.value, onAction = {}, snackbar = snackbar)
            }
        }
        compose.onNodeWithText("first message 39").assertIsDisplayed()
        compose.onNodeWithTag(ChatThreadTag).performScrollToIndex(0)
        compose.onNodeWithText("first message 0").assertIsDisplayed()

        compose.runOnIdle {
            current.value = conversation("second", reuseMessageKeys)
        }

        compose.onNodeWithText("second message 39").assertIsDisplayed()
        compose.onNodeWithText("first message 0").assertDoesNotExist()
        compose.onNodeWithText("first message 39").assertDoesNotExist()

        compose.runOnIdle {
            current.value = conversation("first", reuseMessageKeys)
        }

        compose.onNodeWithText("first message 39").assertIsDisplayed()
        compose.onNodeWithText("second message 39").assertDoesNotExist()
    }

    private fun conversation(id: String, reuseMessageKeys: Boolean) = ChatUiState(
        loading = false,
        conversationId = id,
        history = List(40) { index ->
            val key = if (reuseMessageKeys) "message-$index" else "$id-$index"
            val text = "$id message $index"
            if (index % 2 == 0) {
                ChatEntry.User(key = key, text = text)
            } else {
                ChatEntry.Assistant(key = key, text = text, live = false)
            }
        },
    )
}
