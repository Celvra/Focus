package fan.x0.focus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fan.x0.focus.app.AndroidMessageNotifier
import fan.x0.focus.app.AndroidLocaleController
import fan.x0.focus.core.designsystem.theme.FocusTheme
import fan.x0.focus.data.assistant.ChatSwitcher
import org.koin.mp.KoinPlatform

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AndroidLocaleController.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        KoinPlatform.getKoin().get<AndroidLocaleController>().bind(this)
        openConversationFrom(intent)
        setContent {
            FocusTheme {
                FocusApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openConversationFrom(intent)
    }

    private fun openConversationFrom(intent: Intent?) {
        val id = intent?.getStringExtra(AndroidMessageNotifier.EXTRA_CONVERSATION_ID) ?: return
        intent.removeExtra(AndroidMessageNotifier.EXTRA_CONVERSATION_ID)
        KoinPlatform.getKoin().get<ChatSwitcher>().open(id)
    }
}
