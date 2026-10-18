package org.autojs.autojs.core.plugin.agent

/** Discovery is separate from attachment; neither inspection nor a failed attach starts a run. */
enum class ThreeStoveAgentUiState {
    NOT_INSTALLED, APPLICATION_DISABLED, ACTIVATION_REQUIRED, PLUGIN_DISABLED,
    AUTHORIZATION_REQUIRED, TRUST_CONFIRMATION_REQUIRED, INCOMPATIBLE, AVAILABLE,
    ATTACHING, ATTACHED, HOST_UNAVAILABLE, DETACHED, FAILED,
}

data class ThreeStoveAgentPluginFacts(
    val installed: Boolean,
    val applicationEnabled: Boolean = true,
    val stopped: Boolean = false,
    val pluginEnabled: Boolean = false,
    val authorized: Boolean = false,
    val trustedSigner: Boolean = false,
    val signerConfirmed: Boolean = false,
    val compatible: Boolean = true,
)

object ThreeStoveAgentUiPolicy {

    fun linkState(state: String): ThreeStoveAgentUiState = when (state) {
        "attaching" -> ThreeStoveAgentUiState.ATTACHING
        "attached" -> ThreeStoveAgentUiState.ATTACHED
        "host_unavailable" -> ThreeStoveAgentUiState.HOST_UNAVAILABLE
        "failed" -> ThreeStoveAgentUiState.FAILED
        else -> ThreeStoveAgentUiState.DETACHED
    }

    fun needsGuide(state: ThreeStoveAgentUiState) = state in setOf(
        ThreeStoveAgentUiState.NOT_INSTALLED, ThreeStoveAgentUiState.APPLICATION_DISABLED,
        ThreeStoveAgentUiState.ACTIVATION_REQUIRED, ThreeStoveAgentUiState.PLUGIN_DISABLED,
        ThreeStoveAgentUiState.AUTHORIZATION_REQUIRED, ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED,
        ThreeStoveAgentUiState.INCOMPATIBLE, ThreeStoveAgentUiState.FAILED, ThreeStoveAgentUiState.HOST_UNAVAILABLE,
    )

    fun availability(facts: ThreeStoveAgentPluginFacts): ThreeStoveAgentUiState = when {
        !facts.installed -> ThreeStoveAgentUiState.NOT_INSTALLED
        !facts.applicationEnabled -> ThreeStoveAgentUiState.APPLICATION_DISABLED
        !facts.compatible -> ThreeStoveAgentUiState.INCOMPATIBLE
        !facts.authorized -> ThreeStoveAgentUiState.AUTHORIZATION_REQUIRED
        !facts.trustedSigner && !facts.signerConfirmed -> ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED
        !facts.pluginEnabled -> ThreeStoveAgentUiState.PLUGIN_DISABLED
        facts.stopped -> ThreeStoveAgentUiState.ACTIVATION_REQUIRED
        else -> ThreeStoveAgentUiState.AVAILABLE
    }

    /** Scripts learn why the plugin is unavailable from the error, never from a later host dialog. */
    fun unavailableHint(state: ThreeStoveAgentUiState): String = when (state) {
        ThreeStoveAgentUiState.NOT_INSTALLED -> "3-Stove Agent is not installed; install it from AutoJs6 Plugin Center"
        ThreeStoveAgentUiState.APPLICATION_DISABLED -> "Android has disabled 3-Stove Agent; enable the app in system settings"
        ThreeStoveAgentUiState.ACTIVATION_REQUIRED -> "Android has not activated 3-Stove Agent; open the 3-Stove Agent app once"
        ThreeStoveAgentUiState.PLUGIN_DISABLED -> "3-Stove Agent is disabled in AutoJs6 Plugin Center; enable it there"
        ThreeStoveAgentUiState.AUTHORIZATION_REQUIRED -> "3-Stove Agent is not authorized; authorize it in AutoJs6 Plugin Center"
        ThreeStoveAgentUiState.TRUST_CONFIRMATION_REQUIRED -> "3-Stove Agent has an unrecognized signature; tap \"Open AutoJs6\" in 3-Stove Agent to confirm it"
        ThreeStoveAgentUiState.INCOMPATIBLE -> "3-Stove Agent and this AutoJs6 build are incompatible; install compatible versions"
        else -> "Check 3-Stove Agent in AutoJs6 Plugin Center"
    }
}

/** Pure lifecycle policy. A rejected attach is terminal; only process death schedules a rebind. */
class ThreeStoveAgentLinkPolicy {
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
