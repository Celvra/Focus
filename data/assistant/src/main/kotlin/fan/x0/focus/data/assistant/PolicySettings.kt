package fan.x0.focus.data.assistant

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

enum class CommandPolicy { Yolo, AutoPilot, Manual }

enum class JudgeKind { Jev, Llm }

data class PolicyConfig(
    val policy: CommandPolicy = CommandPolicy.Manual,
    val judgeKind: JudgeKind = JudgeKind.Jev,
    val judgeProviderId: String = PolicyDefaults.JUDGE_PROVIDER_ID,
    val judgeModel: String = PolicyDefaults.JEV_MODEL,
    val contextWindow: Int = PolicyDefaults.CONTEXT_WINDOW,
    val allowThreshold: Float = PolicyDefaults.ALLOW_THRESHOLD,
    val maxRisk: Int = PolicyDefaults.MAX_RISK,
    val timeoutSeconds: Int = PolicyDefaults.TIMEOUT_SECONDS,
)

object PolicyDefaults {
    const val JUDGE_PROVIDER_ID = "typesafe"
    const val JEV_MODEL = "jev-latest"
    const val CONTEXT_WINDOW = 2
    const val ALLOW_THRESHOLD = 0.75f
    const val MAX_RISK = 3
    const val TIMEOUT_SECONDS = 8
}

private val Context.policyDataStore by preferencesDataStore(name = "policy_settings")

@Single
class PolicySettings(@Provided private val context: Context) {
    private object Keys {
        val policy = stringPreferencesKey("policy")
        val judgeKind = stringPreferencesKey("judge_kind")
        val judgeProviderId = stringPreferencesKey("judge_provider_id")
        val judgeModel = stringPreferencesKey("judge_model")
        val contextWindow = intPreferencesKey("judge_context_window")
        val allowThreshold = floatPreferencesKey("judge_allow_threshold")
        val maxRisk = intPreferencesKey("judge_max_risk")
        val timeoutSeconds = intPreferencesKey("judge_timeout_seconds")
    }

    val config: Flow<PolicyConfig> = context.policyDataStore.data.map { prefs ->
        PolicyConfig(
            policy = prefs[Keys.policy].toPolicy(),
            judgeKind = prefs[Keys.judgeKind].toJudgeKind(),
            judgeProviderId = prefs[Keys.judgeProviderId]
                ?.takeIf { it.isNotBlank() }
                ?: PolicyDefaults.JUDGE_PROVIDER_ID,
            judgeModel = prefs[Keys.judgeModel]
                ?.takeIf { it.isNotBlank() }
                ?: PolicyDefaults.JEV_MODEL,
            contextWindow = (prefs[Keys.contextWindow] ?: PolicyDefaults.CONTEXT_WINDOW).coerceIn(0, 12),
            allowThreshold = (prefs[Keys.allowThreshold] ?: PolicyDefaults.ALLOW_THRESHOLD)
                .coerceIn(0f, 1f),
            maxRisk = (prefs[Keys.maxRisk] ?: PolicyDefaults.MAX_RISK).coerceIn(1, 4),
            timeoutSeconds = (prefs[Keys.timeoutSeconds] ?: PolicyDefaults.TIMEOUT_SECONDS)
                .coerceIn(2, 60),
        )
    }

    suspend fun configOnce(): PolicyConfig = config.first()

    suspend fun setPolicy(value: CommandPolicy) {
        context.policyDataStore.edit { prefs -> prefs[Keys.policy] = value.name.lowercase() }
    }

    suspend fun setJudgeKind(value: JudgeKind) {
        context.policyDataStore.edit { prefs -> prefs[Keys.judgeKind] = value.name.lowercase() }
    }

    suspend fun setJudgeProvider(providerId: String, model: String) {
        context.policyDataStore.edit { prefs ->
            prefs[Keys.judgeProviderId] = providerId
            prefs[Keys.judgeModel] = model
        }
    }

    suspend fun setJudgeModel(value: String) {
        context.policyDataStore.edit { prefs -> prefs[Keys.judgeModel] = value }
    }

    suspend fun setContextWindow(value: Int) {
        context.policyDataStore.edit { prefs ->
            prefs[Keys.contextWindow] = value.coerceIn(0, 12)
        }
    }

    suspend fun setAllowThreshold(value: Float) {
        context.policyDataStore.edit { prefs ->
            prefs[Keys.allowThreshold] = value.coerceIn(0f, 1f)
        }
    }

    suspend fun setMaxRisk(value: Int) {
        context.policyDataStore.edit { prefs -> prefs[Keys.maxRisk] = value.coerceIn(1, 4) }
    }

    suspend fun setTimeoutSeconds(value: Int) {
        context.policyDataStore.edit { prefs ->
            prefs[Keys.timeoutSeconds] = value.coerceIn(2, 60)
        }
    }

    private fun String?.toPolicy(): CommandPolicy = when (this) {
        "yolo" -> CommandPolicy.Yolo
        "autopilot" -> CommandPolicy.AutoPilot
        else -> CommandPolicy.Manual
    }

    private fun String?.toJudgeKind(): JudgeKind = when (this) {
        "llm" -> JudgeKind.Llm
        else -> JudgeKind.Jev
    }
}
