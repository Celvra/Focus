package fan.x0.focus.data.assistant

import org.koin.core.annotation.Single

data class ResolvedModel(
    val role: AgentRole,
    val provider: ProviderConfig,
    val model: String,
    val reasoning: ReasoningLevel = ReasoningLevel.Auto,
    val via: AgentRole? = null,
) {
    val providerId: String get() = provider.id
}

@Single
class ModelResolver(
    private val settings: AssistantSettings,
    private val secrets: SecretStore,
    private val roles: RoleModels,
) {
    suspend fun resolve(role: AgentRole, parent: ResolvedModel? = null): ResolvedModel? {
        resolveAssigned(role)?.let { return it }
        if (parent != null && usable(parent.provider)) {
            return parent.copy(role = role, via = parent.role)
        }
        val config = settings.configOnce()
        val fallback = config.activeProvider ?: config.providers.firstOrNull()
        return fallback?.let { provider ->
            candidate(role, provider, provider.model, ReasoningLevel.Auto, via = null)
        }
    }

    suspend fun resolveAssigned(role: AgentRole): ResolvedModel? {
        val config = settings.configOnce()
        val assigned = roles.configsOnce()
        val chain = buildList {
            add(role)
            addAll(role.fallbacks)
        }.distinct()
        for (candidate in chain) {
            val entry = assigned[candidate] ?: continue
            resolveConfig(config, candidate, entry, via = if (candidate == role) null else candidate)
                ?.let { return it }
        }
        return null
    }

    suspend fun resolveManual(
        providerId: String,
        model: String,
        reasoning: ReasoningLevel,
    ): ResolvedModel? {
        val config = settings.configOnce()
        val provider = config.providers.firstOrNull { it.id == providerId }
        if (provider != null) {
            candidate(
                role = AgentRole.WorkerStandard,
                provider = provider,
                model = model.ifBlank { provider.model },
                reasoning = reasoning,
                via = null,
            )?.let { return it }
        }
        return resolve(AgentRole.WorkerStandard)
    }

    private suspend fun resolveConfig(
        config: AssistantConfig,
        role: AgentRole,
        entry: RoleModelConfig,
        via: AgentRole?,
    ): ResolvedModel? {
        if (entry.providerId.isBlank()) return null
        val provider = config.providers.firstOrNull { it.id == entry.providerId } ?: return null
        return candidate(
            role = role,
            provider = provider,
            model = entry.model.ifBlank { provider.model },
            reasoning = ReasoningLevel.fromName(entry.reasoning.takeIf { it.isNotBlank() }),
            via = via,
        )
    }

    private suspend fun candidate(
        role: AgentRole,
        provider: ProviderConfig,
        model: String,
        reasoning: ReasoningLevel,
        via: AgentRole?,
    ): ResolvedModel? {
        if (model.isBlank() || provider.baseUrl.isBlank()) return null
        if (!usable(provider)) return null
        return ResolvedModel(role, provider, model, reasoning, via)
    }

    private suspend fun usable(provider: ProviderConfig): Boolean {
        if (provider.baseUrl.isBlank()) return false
        if (!provider.requiresKey) return true
        return !secrets.apiKey(provider.id).isNullOrBlank()
    }
}
