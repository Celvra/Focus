package fan.x0.focus

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fan.x0.focus.app.AndroidLocaleController
import fan.x0.focus.core.designsystem.theme.FocusTheme
import org.koin.mp.KoinPlatform

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AndroidLocaleController.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        KoinPlatform.getKoin().get<AndroidLocaleController>().bind(this)
        setContent {
            FocusTheme {
                FocusApp()
            }
        }
    }

}
