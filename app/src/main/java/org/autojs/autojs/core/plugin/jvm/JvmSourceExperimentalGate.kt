package org.autojs.autojs.core.plugin.jvm

/**
 * Host-side R1 admission policy for the JVM source provider.
 *
 * Discovery and dispatch remain gated by both the build flag and an exact persisted component.
 * Every provider boundary below is revalidated before dispatch.
 */
internal object JvmSourceExperimentalGate {

    fun shouldDiscover(featureEnabled: Boolean, preferredComponent: String?): Boolean =
        featureEnabled && !preferredComponent.isNullOrBlank()

    fun decide(input: Input): Decision = when {
        !input.featureEnabled -> Decision.Rejected(Rejection.EXPERIMENT_DISABLED)
        input.preferredComponent.isNullOrBlank() -> Decision.Rejected(Rejection.PROVIDER_NOT_SELECTED)
        !input.pluginEnabled -> Decision.Rejected(Rejection.PROVIDER_DISABLED)
        !input.providerAuthorized -> Decision.Rejected(Rejection.PROVIDER_UNAUTHORIZED)
        !input.identityStable -> Decision.Rejected(Rejection.IDENTITY_DRIFT)
        !input.protocolCompatible -> Decision.Rejected(Rejection.PROTOCOL_INCOMPATIBLE)
        !input.languageSupported -> Decision.Rejected(Rejection.LANGUAGE_UNSUPPORTED)
        !input.dedicatedWorker -> Decision.Rejected(Rejection.WORKER_ISOLATION_REQUIRED)
        !input.hardTerminationSupported -> Decision.Rejected(Rejection.HARD_TERMINATION_REQUIRED)
        else -> Decision.Admitted(input.preferredComponent)
    }

    data class Input(
        val featureEnabled: Boolean,
        val preferredComponent: String?,
        val pluginEnabled: Boolean,
        val providerAuthorized: Boolean,
        val identityStable: Boolean,
        val protocolCompatible: Boolean,
        val languageSupported: Boolean,
        val dedicatedWorker: Boolean,
        val hardTerminationSupported: Boolean,
    )

    sealed interface Decision {
        data class Admitted(val preferredComponent: String) : Decision
        data class Rejected(val reason: Rejection) : Decision
    }

    enum class Rejection {
        EXPERIMENT_DISABLED,
        PROVIDER_NOT_SELECTED,
        PROVIDER_DISABLED,
        PROVIDER_UNAUTHORIZED,
        IDENTITY_DRIFT,
        PROTOCOL_INCOMPATIBLE,
        LANGUAGE_UNSUPPORTED,
        WORKER_ISOLATION_REQUIRED,
        HARD_TERMINATION_REQUIRED,
    }
}
