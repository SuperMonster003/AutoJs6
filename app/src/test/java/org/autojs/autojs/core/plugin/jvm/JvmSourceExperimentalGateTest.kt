package org.autojs.autojs.core.plugin.jvm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmSourceExperimentalGateTest {

    @Test
    fun explicitEntryRejectsDisabledBuildSynchronously() {
        var failure: JvmSourceUserFailure? = null
        try {
            JvmSourceExplicitAdmission.requireEnabled(false)
        } catch (expected: JvmSourceExperimentDisabledException) {
            failure = expected.failure
        }
        val rejected = requireNotNull(failure)
        assertEquals(JvmSourceUserFailureCode.EXPERIMENT_DISABLED, rejected.code)
        assertEquals(JvmSourceRecoveryAction.ENABLE_EXPERIMENT, rejected.recoveryAction)
        JvmSourceExplicitAdmission.requireEnabled(true)
    }

    @Test
    fun discoveryRequiresBuildFlagAndExplicitComponent() {
        assertFalse(JvmSourceExperimentalGate.shouldDiscover(false, "plugin/Service"))
        assertFalse(JvmSourceExperimentalGate.shouldDiscover(true, null))
        assertFalse(JvmSourceExperimentalGate.shouldDiscover(true, "  "))
        assertTrue(JvmSourceExperimentalGate.shouldDiscover(true, "plugin/Service"))
    }

    @Test
    fun admissionFailsClosedAtEveryBoundary() {
        val ready = readyInput()
        val rejected = listOf(
            ready.copy(featureEnabled = false) to JvmSourceExperimentalGate.Rejection.EXPERIMENT_DISABLED,
            ready.copy(preferredComponent = null) to JvmSourceExperimentalGate.Rejection.PROVIDER_NOT_SELECTED,
            ready.copy(pluginEnabled = false) to JvmSourceExperimentalGate.Rejection.PROVIDER_DISABLED,
            ready.copy(providerAuthorized = false) to JvmSourceExperimentalGate.Rejection.PROVIDER_UNAUTHORIZED,
            ready.copy(identityStable = false) to JvmSourceExperimentalGate.Rejection.IDENTITY_DRIFT,
            ready.copy(protocolCompatible = false) to JvmSourceExperimentalGate.Rejection.PROTOCOL_INCOMPATIBLE,
            ready.copy(languageSupported = false) to JvmSourceExperimentalGate.Rejection.LANGUAGE_UNSUPPORTED,
            ready.copy(dedicatedWorker = false) to JvmSourceExperimentalGate.Rejection.WORKER_ISOLATION_REQUIRED,
            ready.copy(hardTerminationSupported = false) to JvmSourceExperimentalGate.Rejection.HARD_TERMINATION_REQUIRED,
        )

        rejected.forEach { (input, expected) ->
            assertEquals(
                expected,
                (JvmSourceExperimentalGate.decide(input) as JvmSourceExperimentalGate.Decision.Rejected).reason,
            )
        }
    }

    @Test
    fun readyProviderKeepsTheExactSelectedComponent() {
        assertEquals(
            JvmSourceExperimentalGate.Decision.Admitted("plugin/Service"),
            JvmSourceExperimentalGate.decide(readyInput()),
        )
    }

    private fun readyInput() = JvmSourceExperimentalGate.Input(
        featureEnabled = true,
        preferredComponent = "plugin/Service",
        pluginEnabled = true,
        providerAuthorized = true,
        identityStable = true,
        protocolCompatible = true,
        languageSupported = true,
        dedicatedWorker = true,
        hardTerminationSupported = true,
    )
}
