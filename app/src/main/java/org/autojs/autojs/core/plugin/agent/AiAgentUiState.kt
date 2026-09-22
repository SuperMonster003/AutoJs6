package org.autojs.autojs.core.plugin.agent

/** Discovery is separate from attachment; neither inspection nor a failed attach starts a run. */
enum class AiAgentUiState {
    NOT_INSTALLED, APPLICATION_DISABLED, ACTIVATION_REQUIRED, PLUGIN_DISABLED,
    AUTHORIZATION_REQUIRED, TRUST_CONFIRMATION_REQUIRED, INCOMPATIBLE, AVAILABLE,
    ATTACHING, ATTACHED, HOST_UNAVAILABLE, DETACHED, FAILED,
}

data class AiAgentPluginFacts(
    val installed: Boolean,
    val applicationEnabled: Boolean = true,
    val stopped: Boolean = false,
    val pluginEnabled: Boolean = false,
    val authorized: Boolean = false,
    val trustedSigner: Boolean = false,
    val signerConfirmed: Boolean = false,
    val compatible: Boolean = true,
)

object AiAgentUiPolicy {
    fun availability(facts: AiAgentPluginFacts): AiAgentUiState = when {
        !facts.installed -> AiAgentUiState.NOT_INSTALLED
        !facts.applicationEnabled -> AiAgentUiState.APPLICATION_DISABLED
        !facts.compatible -> AiAgentUiState.INCOMPATIBLE
        !facts.authorized -> AiAgentUiState.AUTHORIZATION_REQUIRED
        !facts.trustedSigner && !facts.signerConfirmed -> AiAgentUiState.TRUST_CONFIRMATION_REQUIRED
        facts.stopped -> AiAgentUiState.ACTIVATION_REQUIRED
        !facts.pluginEnabled -> AiAgentUiState.PLUGIN_DISABLED
        else -> AiAgentUiState.AVAILABLE
    }
}

/** Pure lifecycle policy. A rejected attach is terminal; only process death schedules a rebind. */
class AiAgentLinkPolicy {
    var generation = 0L
        private set
    var wanted = false
        private set
    private var deaths = 0

    fun attach(): Long {
        wanted = true
        deaths = 0
        return ++generation
    }

    fun detach() {
        wanted = false
        ++generation
    }

    fun accepts(candidate: Long) = wanted && candidate == generation

    fun failed(candidate: Long) {
        if (accepts(candidate)) detach()
    }

    fun died(candidate: Long, available: Boolean): Long? {
        if (!accepts(candidate)) return null
        ++generation
        val delay = REBIND_DELAYS_MS.getOrNull(deaths++)
        if (!available || delay == null) {
            wanted = false
            return null
        }
        return delay
    }

    companion object {
        private val REBIND_DELAYS_MS = longArrayOf(1_000, 3_000, 9_000)
    }
}
