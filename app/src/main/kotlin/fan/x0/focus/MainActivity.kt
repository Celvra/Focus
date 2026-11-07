package fan.x0.focus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fan.x0.focus.app.AndroidMessageNotifier
import fan.x0.focus.app.AndroidLocaleController
import fan.x0.focus.app.ImGatewayService
import fan.x0.focus.core.designsystem.theme.FocusTheme
import fan.x0.focus.data.assistant.ChatSwitcher
import fan.x0.focus.data.assistant.CrewSwitcher
import fan.x0.focus.data.plugin.PluginIntents
import fan.x0.focus.data.plugin.PluginNavigation
import org.koin.mp.KoinPlatform

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AndroidLocaleController.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ImGatewayService.start(this)
        KoinPlatform.getKoin().get<AndroidLocaleController>().bind(this)
        openAssistantFrom(intent)
        openConversationFrom(intent)
        openPluginFrom(intent)
        setContent {
            FocusTheme {
                FocusApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openAssistantFrom(intent)
        openConversationFrom(intent)
        openPluginFrom(intent)
    }

    private fun openAssistantFrom(intent: Intent?) {
        if (intent?.action != Intent.ACTION_ASSIST) return
        intent.action = null
        KoinPlatform.getKoin().get<ChatSwitcher>().requestAssist()
    }

    private fun openPluginFrom(intent: Intent?) {
        val pluginId = intent?.getStringExtra(PluginIntents.EXTRA_PLUGIN_ID) ?: return
        val pageId = intent.getStringExtra(PluginIntents.EXTRA_PLUGIN_PAGE) ?: return
        intent.removeExtra(PluginIntents.EXTRA_PLUGIN_ID)
        intent.removeExtra(PluginIntents.EXTRA_PLUGIN_PAGE)
        val args = intent.getStringExtra(PluginIntents.EXTRA_PLUGIN_ARGS)
        intent.removeExtra(PluginIntents.EXTRA_PLUGIN_ARGS)
        PluginNavigation.open(pluginId, pageId, args)
    }

    private fun openConversationFrom(intent: Intent?) {
        val id = intent?.getStringExtra(AndroidMessageNotifier.EXTRA_CONVERSATION_ID) ?: return
        intent.removeExtra(AndroidMessageNotifier.EXTRA_CONVERSATION_ID)
        val koin = KoinPlatform.getKoin()
        if (id.startsWith("crew:")) {
            koin.get<CrewSwitcher>().open(id.removePrefix("crew:"))
        } else {
            koin.get<ChatSwitcher>().open(id)
        }
    }
}
