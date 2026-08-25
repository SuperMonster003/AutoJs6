package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class R8CompilerHostControlPlaneTest {
    @Test
    fun defaultOffSelectionDoesNotQueryPackageManagerBoundary() {
        val transport = RecordingTransport()
        val controlPlane = controlPlane(transport) { R8CompilerSelectionOptions() }

        assertTrue(controlPlane.select() is R8CompilerSelectionDecision.Disabled)
        assertFalse(transport.discovered)
    }

    @Test
    fun preferenceReadFailureFailsClosedWithoutDiscovery() {
        val transport = RecordingTransport()
        val controlPlane = controlPlane(transport) {
            throw IllegalStateException("corrupt preference backend")
        }

        assertTrue(controlPlane.select() is R8CompilerSelectionDecision.Disabled)
        assertFalse(transport.discovered)
    }

    @Test
    fun exactSelectionBindAndPostBindNegotiationUseOnePinnedIdentity() {
        val candidate = R8CompilerHostFixtures.candidate()
        val transport = RecordingTransport(candidate)
        val controlPlane = controlPlane(transport) {
            R8CompilerSelectionOptions(true, candidate.component)
        }
        val selected = controlPlane.select() as R8CompilerSelectionDecision.Selected

        controlPlane.bindExact(selected.identity, NoOpBindingCallback).close()
        val negotiation = controlPlane.negotiateConnected(
            selected.identity,
            FixtureConnection(transport.events),
            minApi = 24,
        )

        assertEquals(selected.identity, transport.boundIdentity)
        assertEquals(listOf("discover", "bind", "inspect", "info", "capabilities"), transport.events)
        assertEquals("8.13.17", negotiation.capabilities.compilerVersion)
    }

    @Test
    fun postBindIdentityDriftIsRejectedBeforeAnyRemoteHandshakeRead() {
        val transport = RecordingTransport(R8CompilerHostFixtures.candidate(updateTime = 3L))
        val controlPlane = controlPlane(transport) {
            R8CompilerSelectionOptions(true, R8CompilerHostFixtures.component())
        }

        expectRejected {
            controlPlane.negotiateConnected(
                R8CompilerHostFixtures.identity(),
                FixtureConnection(transport.events),
                minApi = 24,
            )
        }

        assertEquals(listOf("inspect"), transport.events)
    }

    @Test
    fun semanticKeyIncludesAnExplicitSupportedTargetApi() {
        val candidate = R8CompilerHostFixtures.candidate()
        val transport = RecordingTransport(candidate)
        val controlPlane = controlPlane(transport) {
            R8CompilerSelectionOptions(true, candidate.component)
        }
        val negotiation = controlPlane.negotiateConnected(
            candidate.pin(),
            FixtureConnection(transport.events),
            24,
        )
        val request = R8CompilerHostFixtures.request(negotiation.capabilities, minApi = 24)

        val api24 = controlPlane.semanticCacheKey(request, negotiation)
        val api25 = controlPlane.semanticCacheKey(
            R8CompilerHostFixtures.request(negotiation.capabilities, minApi = 25),
            negotiation,
        )
        assertEquals(64, api24.length)
        assertFalse(api24 == api25)
    }

    private fun controlPlane(
        transport: RecordingTransport,
        options: () -> R8CompilerSelectionOptions,
    ) = R8CompilerHostControlPlane(
        transport = transport,
        selectionOptions = options,
        hostVersionCode = 5_276L,
        hostRuntimeApi = 24,
    )

    private fun expectRejected(action: () -> Unit) {
        try {
            action()
            throw AssertionError("Expected R8 control-plane rejection")
        } catch (_: IllegalArgumentException) {
            Unit
        }
    }

    private class RecordingTransport(
        private val candidate: R8CompilerServiceCandidate? = null,
    ) : R8CompilerTransport {
        val events = mutableListOf<String>()
        var discovered = false
        var boundIdentity: R8CompilerPinnedIdentity? = null

        override fun discoverExactAction(action: String): List<R8CompilerServiceCandidate> {
            events += "discover"
            discovered = true
            return listOfNotNull(candidate)
        }

        override fun inspectExact(component: R8CompilerComponent): R8CompilerServiceCandidate? {
            events += "inspect"
            return candidate?.takeIf { it.component == component }
        }

        override fun bindExact(
            identity: R8CompilerPinnedIdentity,
            callback: R8CompilerBindingCallback,
        ): R8CompilerBinding {
            events += "bind"
            boundIdentity = identity
            return object : R8CompilerBinding {
                override fun close() = Unit
            }
        }
    }

    private class FixtureConnection(
        private val events: MutableList<String>,
    ) : R8CompilerRemoteConnection {
        override fun getCompilerInfo(): ByteArray {
            events += "info"
            return R8CompilerCodec.encodeInfo(R8CompilerHostFixtures.info())
        }

        override fun getCapabilities(): ByteArray {
            events += "capabilities"
            return R8CompilerCodec.encodeCapabilities(R8CompilerHostFixtures.capabilities())
        }

        override fun openSession(
            encodedRequest: ByteArray,
            inputBundleDescriptor: R8CompilerOwnedDescriptor,
            outputBundleDescriptor: R8CompilerOwnedDescriptor,
            callback: R8CompilerRemoteCallback,
        ): R8CompilerRemoteSession = throw UnsupportedOperationException("No dispatch in G3 control-plane test")

    }

    private object NoOpBindingCallback : R8CompilerBindingCallback {
        override fun onConnected(connection: R8CompilerRemoteConnection) = Unit
        override fun onBindingFailure(cause: Throwable?) = Unit
        override fun onBinderDeath() = Unit
    }
}
