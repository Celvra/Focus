package fan.x0.focus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import fan.x0.focus.app.AndroidMessageNotifier
import fan.x0.focus.app.AppModule
import fan.x0.focus.app.AppPresenceCallbacks
import fan.x0.focus.app.ChatTurnService
import fan.x0.focus.app.ImGatewayService
import fan.x0.focus.core.network.NetworkModule
import fan.x0.focus.core.notify.AppPresence
import fan.x0.focus.core.notify.NotifyModule
import fan.x0.focus.data.assistant.ApprovalStatus
import fan.x0.focus.data.assistant.ApprovalStore
import fan.x0.focus.data.assistant.AssistantEventSink
import fan.x0.focus.data.assistant.AssistantModule
import fan.x0.focus.data.assistant.CrewRunner
import fan.x0.focus.data.assistant.ReminderWorker
import fan.x0.focus.data.device.DeviceAutomationState
import fan.x0.focus.data.device.DeviceModule
import fan.x0.focus.data.device.DeviceRuntime
import fan.x0.focus.data.device.DeviceTriggerManager
import fan.x0.focus.data.im.ImModule
import fan.x0.focus.data.plugin.PluginManager
import fan.x0.focus.data.plugin.PluginModule
import fan.x0.focus.data.sources.FeedRefreshScheduler
import fan.x0.focus.data.sources.SourcePreferences
import fan.x0.focus.data.sources.SourcesModule
import fan.x0.focus.data.bridge.BridgeModule as BridgeDataModule
import fan.x0.focus.feature.assistant.AssistantFeatureModule
import fan.x0.focus.feature.bridge.BridgeModule as BridgeFeatureModule
import fan.x0.focus.feature.chat.ChatModule
import fan.x0.focus.feature.crew.CrewModule
import fan.x0.focus.feature.device.DeviceFeatureModule
import fan.x0.focus.feature.goals.GoalsModule
import fan.x0.focus.feature.library.LibraryModule
import fan.x0.focus.feature.onboarding.OnboardingModule
import fan.x0.focus.feature.overlay.AutomationOverlayService
import fan.x0.focus.feature.overlay.OverlayModule
import fan.x0.focus.feature.plugin.PluginFeatureModule
import fan.x0.focus.feature.today.TodayModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.annotation.KoinApplication
import org.koin.mp.KoinPlatform
import org.koin.plugin.module.dsl.startKoin

@KoinApplication(
    modules = [
        NetworkModule::class,
        NotifyModule::class,
        SourcesModule::class,
        AssistantModule::class,
        DeviceModule::class,
        ImModule::class,
        PluginModule::class,
        AppModule::class,
        OnboardingModule::class,
        ChatModule::class,
        CrewModule::class,
        TodayModule::class,
        GoalsModule::class,
        LibraryModule::class,
        BridgeDataModule::class,
        BridgeFeatureModule::class,
        DeviceFeatureModule::class,
        AssistantFeatureModule::class,
        OverlayModule::class,
        PluginFeatureModule::class,
    ],
)
class FocusApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin<FocusApplication> {
            androidLogger()
            androidContext(this@FocusApplication)
        }
        registerActivityLifecycleCallbacks(
            AppPresenceCallbacks(KoinPlatform.getKoin().get<AppPresence>()),
        )
        createChannels()
        scope.launch { bootstrap() }
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderWorker.CHANNEL_ID,
                getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ChatTurnService.CHANNEL_ID,
                getString(R.string.notification_channel_chat_turns),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                AndroidMessageNotifier.CHANNEL_ID,
                getString(R.string.notification_channel_messages),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ImGatewayService.CHANNEL_ID,
                getString(R.string.notification_channel_im),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private suspend fun bootstrap() {
        val koin = KoinPlatform.getKoin()
        koin.get<DeviceTriggerManager>()
        koin.get<CrewRunner>()
        val approvals = koin.get<ApprovalStore>()
        approvals.pending().forEach { approvals.decide(it.id, ApprovalStatus.Expired, null) }
        val hours = koin.get<SourcePreferences>().settings.first().refreshIntervalHours
        koin.get<FeedRefreshScheduler>().schedule(hours)
        koin.get<PluginManager>().loadAll()
        koin.get<AssistantEventSink>().emit("app.start")
        scope.launch {
            koin.get<DeviceRuntime>().automation
                .collect { state ->
                    if (state is DeviceAutomationState.Running) {
                        AutomationOverlayService.start(this@FocusApplication)
                    }
                }
        }
    }
}
