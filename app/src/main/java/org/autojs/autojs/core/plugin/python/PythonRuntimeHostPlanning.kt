package org.autojs.autojs.core.plugin.python

import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonRuntimeAdmissionPlan
import org.autojs.plugin.python.runtime.api.PythonRuntimeAdmissionPlanner
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeContractException
import org.autojs.plugin.python.runtime.api.PythonRuntimeContractViolation

internal data class PythonRuntimeDiscoveryPlan(
    val action: String = PythonRuntimeContract.RUNTIME_ACTION,
)

/** Platform-neutral plan for `Intent().setComponent(component)`: action is deliberately null. */
internal data class PythonRuntimeExplicitBindingPlan(
    val component: PythonRuntimeProviderComponent,
    val action: String? = null,
)

internal object PythonRuntimeBindingPlanner {
    fun discovery(): PythonRuntimeDiscoveryPlan = PythonRuntimeDiscoveryPlan()

    fun explicit(pinned: PythonRuntimeProviderPinnedIdentity): PythonRuntimeExplicitBindingPlan =
        PythonRuntimeExplicitBindingPlan(component = pinned.component)
}

internal class PythonRuntimeHostRequestPlan internal constructor(
    val pinnedProvider: PythonRuntimeProviderPinnedIdentity,
    val request: PythonExecutionRequest,
    val descriptorCount: Int,
    val admissionPlan: PythonRuntimeAdmissionPlan,
) {
    val bindingPlan: PythonRuntimeExplicitBindingPlan = PythonRuntimeBindingPlanner.explicit(pinnedProvider)
}

internal sealed class PythonRuntimeHostRequestPlanningResult {
    data object ProviderIdentityChanged : PythonRuntimeHostRequestPlanningResult()
    data class RequestRejected(val violation: PythonRuntimeContractViolation) :
        PythonRuntimeHostRequestPlanningResult()
    data class Planned(val plan: PythonRuntimeHostRequestPlan) : PythonRuntimeHostRequestPlanningResult()
}

internal object PythonRuntimeHostRequestPlanner {
    fun plan(
        pinned: PythonRuntimeProviderPinnedIdentity,
        current: PythonRuntimeProviderCandidate?,
        environment: PythonRuntimeProviderEnvironment,
        requirement: PythonRuntimeCapabilityRequirement,
        request: PythonExecutionRequest,
        descriptorCount: Int,
    ): PythonRuntimeHostRequestPlanningResult {
        if (!PythonRuntimeProviderSelectionPolicy.revalidate(pinned, current, environment, requirement)) {
            return PythonRuntimeHostRequestPlanningResult.ProviderIdentityChanged
        }
        return try {
            val admission = PythonRuntimeAdmissionPlanner.create(
                runtimeInfo = pinned.runtimeInfo,
                capabilities = pinned.capabilities,
                hostVersionCode = environment.hostVersionCode,
                deviceAbi = pinned.selectedAbi,
                negotiatedProtocolVersion = pinned.negotiatedProtocolVersion,
                request = request,
                descriptorCount = descriptorCount,
            )
            PythonRuntimeHostRequestPlanningResult.Planned(
                PythonRuntimeHostRequestPlan(
                    pinnedProvider = pinned,
                    request = request,
                    descriptorCount = descriptorCount,
                    admissionPlan = admission,
                ),
            )
        } catch (error: PythonRuntimeContractException) {
            PythonRuntimeHostRequestPlanningResult.RequestRejected(error.violation)
        }
    }
}
