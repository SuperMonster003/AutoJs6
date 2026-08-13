package org.autojs.autojs.core.plugin.python

import org.autojs.plugin.python.runtime.api.PythonExecutionSessionPolicy
import org.autojs.plugin.python.runtime.api.PythonProtocolVersion
import org.autojs.plugin.python.runtime.api.PythonRuntimeContractViolation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeHostPlanningTest {

    @Test
    fun requestPlanningRevalidatesIdentityAndReusesR0AdmissionPolicy() {
        val original = PythonRuntimeHostTestFixtures.candidate()
        val pinned = selected(original)
        val planned = plan(pinned, original) as PythonRuntimeHostRequestPlanningResult.Planned

        assertEquals(pinned, planned.plan.pinnedProvider)
        assertEquals(null, planned.plan.bindingPlan.action)
        assertEquals(1, planned.plan.descriptorCount)
        assertEquals(null, planned.plan.admissionPlan.sessionPolicy)

        val drifted = plan(pinned, original.copy(versionCode = original.versionCode + 1))
        assertTrue(drifted === PythonRuntimeHostRequestPlanningResult.ProviderIdentityChanged)

        val invalidRequest = plan(
            pinned,
            original,
            requestProtocol = PythonProtocolVersion(1, 1),
        ) as PythonRuntimeHostRequestPlanningResult.RequestRejected
        assertEquals(PythonRuntimeContractViolation.CAPABILITY_INCOMPATIBLE, invalidRequest.violation)
    }

    @Test
    fun workspaceRequestUsesTwoExhaustivelyReferencedDescriptors() {
        val original = PythonRuntimeHostTestFixtures.candidate()
        val pinned = selected(original)
        val planned = PythonRuntimeHostRequestPlanner.plan(
            pinned = pinned,
            current = original,
            environment = PythonRuntimeHostTestFixtures.environment(),
            requirement = PythonRuntimeCapabilityRequirement(workspaceArchive = true),
            request = PythonRuntimeHostTestFixtures.request(workspaceArchive = true),
            descriptorCount = 2,
        ) as PythonRuntimeHostRequestPlanningResult.Planned

        assertEquals(2, planned.plan.descriptorCount)
        assertEquals(0, planned.plan.request.source.descriptorIndex)
        assertEquals(1, requireNotNull(planned.plan.request.workspaceArchive).descriptorIndex)
    }

    @Test
    fun r5RequestUsesOneFinalCapabilitySnapshotDescriptor() {
        val runtimeInfo = PythonRuntimeHostTestFixtures.runtimeInfo(
            protocolMax = PythonProtocolVersion(1, 1),
        )
        val capabilities = PythonRuntimeHostTestFixtures.capabilities(
            supportsHostCapabilitySnapshot = true,
        )
        val original = PythonRuntimeHostTestFixtures.candidate(
            runtimeInfo = runtimeInfo,
            capabilities = capabilities,
        )
        val pinned = selected(original)
        val requirement = PythonRuntimeCapabilityRequirement(
            workspaceArchive = true,
            hostCapabilitySnapshot = true,
            minimumHostCapabilitySnapshotBytes = 32L,
        )
        val planned = PythonRuntimeHostRequestPlanner.plan(
            pinned = pinned,
            current = original,
            environment = PythonRuntimeHostTestFixtures.environment(),
            requirement = requirement,
            request = PythonRuntimeHostTestFixtures.request(
                protocolVersion = PythonProtocolVersion(1, 1),
                workspaceArchive = true,
                hostCapabilitySnapshot = true,
            ),
            descriptorCount = 3,
        ) as PythonRuntimeHostRequestPlanningResult.Planned

        assertEquals(3, planned.plan.descriptorCount)
        assertEquals(2, requireNotNull(planned.plan.request.hostCapabilitySnapshot).descriptorIndex)
    }

    @Test
    fun stdinRequestUsesTheDescriptorBetweenWorkspaceAndHostCapabilities() {
        val runtimeInfo = PythonRuntimeHostTestFixtures.runtimeInfo(
            protocolMax = PythonProtocolVersion(1, 1),
        )
        val capabilities = PythonRuntimeHostTestFixtures.capabilities(
            supportsStdinSnapshot = true,
            supportsHostCapabilitySnapshot = true,
        )
        val original = PythonRuntimeHostTestFixtures.candidate(
            runtimeInfo = runtimeInfo,
            capabilities = capabilities,
        )
        val pinned = selected(original)
        val planned = PythonRuntimeHostRequestPlanner.plan(
            pinned = pinned,
            current = original,
            environment = PythonRuntimeHostTestFixtures.environment(),
            requirement = PythonRuntimeCapabilityRequirement(
                workspaceArchive = true,
                stdinSnapshot = true,
                hostCapabilitySnapshot = true,
                minimumStdinBytes = 6L,
                minimumHostCapabilitySnapshotBytes = 32L,
            ),
            request = PythonRuntimeHostTestFixtures.request(
                protocolVersion = PythonProtocolVersion(1, 1),
                workspaceArchive = true,
                stdinSnapshot = true,
                hostCapabilitySnapshot = true,
            ),
            descriptorCount = 4,
        ) as PythonRuntimeHostRequestPlanningResult.Planned

        assertEquals(4, planned.plan.descriptorCount)
        assertEquals(0, planned.plan.request.source.descriptorIndex)
        assertEquals(1, requireNotNull(planned.plan.request.workspaceArchive).descriptorIndex)
        assertEquals(2, requireNotNull(planned.plan.request.stdin).descriptorIndex)
        assertEquals(3, requireNotNull(planned.plan.request.hostCapabilitySnapshot).descriptorIndex)
    }

    @Test
    fun dispatchClaimAtomicallyCrossesTheNoReplayBoundary() {
        val original = PythonRuntimeHostTestFixtures.candidate()
        val hostPlan = (plan(selected(original), original) as PythonRuntimeHostRequestPlanningResult.Planned).plan
        val registry = PythonRuntimeDispatchRegistry<Any>()
        val reservation = registry.reserve(hostPlan)!!
        val session = Any()

        assertEquals(
            PythonRuntimeDispatchRegistry.ReplayDisposition.SAFE_BEFORE_START,
            registry.replayDisposition(reservation),
        )
        val opened = registry.acceptOpened(reservation, session)
            as PythonRuntimeDispatchRegistry.Opened.Owned<*>
        assertSame(session, opened.session)
        val dispatch = registry.beginStart(reservation)
            as PythonRuntimeDispatchRegistry.Start.Dispatch<*>
        assertSame(session, dispatch.session)
        assertEquals(PythonExecutionSessionPolicy.State.START_REQUESTED, dispatch.policy.state)
        assertEquals(
            PythonRuntimeDispatchRegistry.ReplayDisposition.FORBIDDEN,
            registry.replayDisposition(reservation),
        )
        assertTrue(registry.beginStart(reservation) === PythonRuntimeDispatchRegistry.Start.Rejected)

        val cancellation = registry.cancel(hostPlan.request.requestId)
            as PythonRuntimeDispatchRegistry.Cancellation.AfterStart<*>
        assertSame(session, cancellation.session)
    }

    @Test
    fun cancellationBeforeOpenForbidsReplayAndLateSessionIsCallerOwned() {
        val original = PythonRuntimeHostTestFixtures.candidate()
        val hostPlan = (plan(selected(original), original) as PythonRuntimeHostRequestPlanningResult.Planned).plan
        val registry = PythonRuntimeDispatchRegistry<Any>()
        val reservation = registry.reserve(hostPlan)!!

        assertTrue(
            registry.cancel(hostPlan.request.requestId) ===
                PythonRuntimeDispatchRegistry.Cancellation.BeforeOpened,
        )
        assertEquals(
            PythonRuntimeDispatchRegistry.ReplayDisposition.FORBIDDEN,
            registry.replayDisposition(reservation),
        )
        val late = Any()
        val rejected = registry.acceptOpened(reservation, late)
            as PythonRuntimeDispatchRegistry.Opened.CloseLate<*>
        assertEquals(listOf(late), rejected.sessions)
    }

    @Test
    fun duplicateOpenedFailsClosedAndReturnsEveryHandleToClose() {
        val original = PythonRuntimeHostTestFixtures.candidate()
        val hostPlan = (plan(selected(original), original) as PythonRuntimeHostRequestPlanningResult.Planned).plan
        val registry = PythonRuntimeDispatchRegistry<Any>()
        val reservation = registry.reserve(hostPlan)!!
        val winner = Any()
        val duplicate = Any()

        assertTrue(registry.acceptOpened(reservation, winner) is PythonRuntimeDispatchRegistry.Opened.Owned)
        val rejected = registry.acceptOpened(reservation, duplicate)
            as PythonRuntimeDispatchRegistry.Opened.CloseLate<*>
        assertEquals(listOf(winner, duplicate), rejected.sessions)
        assertTrue(registry.beginStart(reservation) === PythonRuntimeDispatchRegistry.Start.Rejected)
        assertFalse(hostPlan.admissionPlan.sessionPolicy != null)
    }

    @Test
    fun staleReservationCannotTakeOwnershipFromReplacement() {
        val original = PythonRuntimeHostTestFixtures.candidate()
        val hostPlan = (plan(selected(original), original) as PythonRuntimeHostRequestPlanningResult.Planned).plan
        val registry = PythonRuntimeDispatchRegistry<Any>()
        val stale = registry.reserve(hostPlan)!!
        registry.release(stale)
        val replacementPlan = (plan(selected(original), original) as PythonRuntimeHostRequestPlanningResult.Planned).plan
        val replacement = registry.reserve(replacementPlan)!!

        val staleSession = Any()
        val staleResult = registry.acceptOpened(stale, staleSession)
            as PythonRuntimeDispatchRegistry.Opened.CloseLate<*>
        assertEquals(listOf(staleSession), staleResult.sessions)
        val replacementSession = Any()
        assertTrue(
            registry.acceptOpened(replacement, replacementSession) is PythonRuntimeDispatchRegistry.Opened.Owned,
        )
        assertSame(replacementSession, registry.release(replacement))
    }

    private fun selected(candidate: PythonRuntimeProviderCandidate): PythonRuntimeProviderPinnedIdentity =
        (PythonRuntimeProviderSelectionPolicy.select(
            candidates = listOf(candidate),
            environment = PythonRuntimeHostTestFixtures.environment(),
            options = PythonRuntimeProviderSelectionOptions(enabled = true),
        ) as PythonRuntimeProviderSelectionResult.Selected).pinned

    private fun plan(
        pinned: PythonRuntimeProviderPinnedIdentity,
        current: PythonRuntimeProviderCandidate,
        requestProtocol: PythonProtocolVersion = PythonProtocolVersion(1, 0),
    ): PythonRuntimeHostRequestPlanningResult = PythonRuntimeHostRequestPlanner.plan(
        pinned = pinned,
        current = current,
        environment = PythonRuntimeHostTestFixtures.environment(),
        requirement = PythonRuntimeCapabilityRequirement(),
        request = PythonRuntimeHostTestFixtures.request(requestProtocol),
        descriptorCount = 1,
    )
}
