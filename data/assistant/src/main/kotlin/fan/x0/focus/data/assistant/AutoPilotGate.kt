package fan.x0.focus.data.assistant

import fan.x0.focus.core.network.ProviderRequestConfig
import fan.x0.focus.core.network.openai.ChatProtocol
import fan.x0.focus.core.network.openai.ModelEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single

private data class JudgeTarget(
    val baseUrl: String,
    val model: String,
    val protocol: ChatProtocol,
    val requestConfig: ProviderRequestConfig,
)

@Single
class AutoPilotGate(
    private val policy: PolicySettings,
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val jev: JevJudge,
    private val llm: LlmJudge,
    private val chats: ChatStore,
) {
    suspend fun evaluate(action: JudgeAction, conversationId: String): GateDecision {
        val config = policy.configOnce()
        return when (config.policy) {
            CommandPolicy.Yolo -> GateDecision.Allowed("yolo")
            CommandPolicy.Manual -> GateDecision.NeedsApproval("manual")
            CommandPolicy.AutoPilot -> autoPilot(config, action, conversationId)
        }
    }

    private suspend fun autoPilot(
        config: PolicyConfig,
        action: JudgeAction,
        conversationId: String,
    ): GateDecision {
        val target = resolveTarget(config) ?: return GateDecision.NeedsApproval("judge provider has no key")
        val history = history(config, conversationId)
        val verdict = try {
            withTimeout(config.timeoutSeconds * 1000L) {
                when (config.judgeKind) {
                    JudgeKind.Jev -> jev.judge(
                        baseUrl = target.baseUrl,
                        config = target.requestConfig,
                        model = target.model,
                        action = action,
                        history = history,
                        timeoutSeconds = config.timeoutSeconds,
                        allowThreshold = config.allowThreshold,
                    )
                    JudgeKind.Llm -> llm.judge(
                        endpoint = ModelEndpoint(target.baseUrl, target.model),
                        protocol = target.protocol,
                        config = target.requestConfig,
                        action = action,
                        history = history,
                        timeoutSeconds = config.timeoutSeconds,
                        allowThreshold = config.allowThreshold,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return GateDecision.NeedsApproval("judge failed: ${e.message ?: e.javaClass.simpleName}")
        }
        return when {
            !verdict.allowed -> GateDecision.Refused(verdict.reason)
            verdict.risk >= config.maxRisk -> GateDecision.NeedsApproval(verdict.reason)
            else -> GateDecision.Allowed(verdict.reason)
        }
    }

    private suspend fun resolveTarget(config: PolicyConfig): JudgeTarget? {
        val assistant = settings.configOnce()
        val provider = assistant.providers.firstOrNull { it.id == config.judgeProviderId }
            ?: assistant.activeProvider
            ?: return null
        val key = secrets.apiKey(provider.id)?.takeIf { it.isNotBlank() } ?: return null
        return JudgeTarget(
            baseUrl = provider.baseUrl,
            model = config.judgeModel.ifBlank { provider.model },
            protocol = provider.protocol,
            requestConfig = provider.requestConfig(key),
        )
    }

    private suspend fun history(config: PolicyConfig, conversationId: String): List<String> {
        if (config.contextWindow <= 0 || conversationId.isBlank()) return emptyList()
        return chats.messages(conversationId, limit = config.contextWindow)
            .mapNotNull { message ->
                val body = message.content.lineSequence()
                    .joinToString(" ") { it.trim() }
                    .trim()
                    .take(300)
                if (body.isEmpty()) return@mapNotNull null
                "${message.role.name.lowercase()}: $body"
            }
    }
}
