package org.autojs.autojs.core.plugin.lua

import org.autojs.plugin.lua.runtime.api.LuaRuntimeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class LuaRuntimePluginHostTest {

    @Test
    fun preparePreflightsBeforeProbeAndBindsOnlyExactEligibleComponent() {
        val rejected = descriptor(
            component = component("bad.lua.provider", "Runtime"),
            requiredPermission = null,
            packageUid = 10_002,
        )
        val accepted = descriptor(component = component("good.lua.provider", "Runtime"))
        val transport = fakeTransport(rejected, accepted).apply {
            providers[accepted.component] = FakeProvider()
        }

        val prepared = host(transport).prepare()

        assertEquals(listOf(LuaRuntimeContract.SERVICE_ACTION), transport.discoveryActions)
        assertEquals(listOf(accepted.component), transport.boundComponents)
        assertEquals(1, transport.bindings.single().closeCalls)
        assertEquals(accepted.component, prepared.pinnedIdentity.component)
        assertEquals(accepted.packageUid, prepared.expectedCallbackUid)
        assertEquals("arm64-v8a", prepared.runtimeInfo.processAbi)
        assertEquals(LuaRuntimeContract.RUNTIME_SLOT_LUA54, prepared.runtimeInfo.runtimeSlot)
    }

    @Test
    fun zeroAndMultipleProvidersReturnStableExplicitFailureCodes() {
        val unavailable = assertThrows(LuaRuntimeHostException::class.java) {
            host(fakeTransport()).prepare()
        }
        assertFailure(unavailable, LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE)

        val alpha = descriptor(component = component("alpha.lua.provider", "Runtime"), packageUid = 10_001)
        val zeta = descriptor(component = component("zeta.lua.provider", "Runtime"), packageUid = 10_002)
        val transport = fakeTransport(zeta, alpha).apply {
            providers[alpha.component] = FakeProvider(runtimeInfo(providerId = "alpha.provider"))
            providers[zeta.component] = FakeProvider(runtimeInfo(providerId = "zeta.provider"))
        }
        val ambiguous = assertThrows(LuaRuntimeHostException::class.java) {
            host(transport).prepare()
        }
        assertFailure(ambiguous, LuaRuntimeHostFailureCode.LUA_RUNTIME_AMBIGUOUS)
        assertEquals(
            listOf("alpha.lua.provider", "zeta.lua.provider"),
            ambiguous.ambiguousCandidates.map { it.packageName },
        )

        val preferred = host(transport).prepare(zeta.component)
        assertEquals(zeta.component, preferred.pinnedIdentity.component)
    }

    @Test
    fun discoveryAndMalformedRuntimeFailuresNeverFallBackOrLeakProbeBindings() {
        val discoveryTransport = fakeTransport().apply {
            discoveryFailure = IllegalStateException("package manager unavailable")
        }
        val discoveryFailure = assertThrows(LuaRuntimeHostException::class.java) {
            host(discoveryTransport).prepare()
        }
        assertFailure(discoveryFailure, LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE)
        assertTrue(discoveryTransport.boundComponents.isEmpty())

        val descriptor = descriptor()
        val malformedProvider = FakeProvider().apply { runtimeInfoBytes = byteArrayOf(1, 2, 3) }
        val malformedTransport = fakeTransport(descriptor).apply {
            providers[descriptor.component] = malformedProvider
        }
        val malformed = assertThrows(LuaRuntimeHostException::class.java) {
            host(malformedTransport).prepare()
        }
        assertFailure(malformed, LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE)
        assertEquals(1, malformedTransport.bindings.single().closeCalls)
        assertTrue(
            malformed.evaluations.any {
                it.rejection == LuaRuntimeProviderRejection.INVALID_RUNTIME_INFO ||
                    it.rejection == LuaRuntimeProviderRejection.BIND_FAILED
            },
        )
    }

    @Test
    fun acquireRediscoversRevalidatesAndRetainsOneLiveBindingForTheEngine() {
        val descriptor = descriptor()
        val provider = FakeProvider()
        val transport = fakeTransport(descriptor).apply { providers[descriptor.component] = provider }
        val host = host(transport)
        val prepared = host.prepare()

        val acquired = host.acquire(prepared)

        assertEquals(3, transport.discoveryActions.size)
        assertEquals(listOf(descriptor.component, descriptor.component, descriptor.component), transport.boundComponents)
        assertEquals(3, provider.getRuntimeInfoCalls)
        assertEquals(listOf(1, 1, 0), transport.bindings.map { it.closeCalls })
        assertSame(provider, acquired.provider)
        assertEquals(prepared.pinnedIdentity, acquired.pinnedIdentity)
        assertEquals(descriptor.component, acquired.component)
        assertEquals(descriptor.packageUid, acquired.expectedCallbackUid)

        val session = FakeSession()
        assertSame(session, acquired.attachSession(session))
        assertTrue(acquired.cancelActiveSession())
        assertEquals(1, session.cancelCalls)
        acquired.close()
        acquired.close()
        assertEquals(1, session.closeCalls)
        assertEquals(1, transport.bindings.last().closeCalls)
        assertFalse(acquired.cancelActiveSession())
        assertThrows(IllegalStateException::class.java) { acquired.provider }
    }

    @Test
    fun releasedSessionIsNotClosedAgainByProviderHandle() {
        val descriptor = descriptor()
        val transport = fakeTransport(descriptor).apply { providers[descriptor.component] = FakeProvider() }
        val host = host(transport)
        val acquired = host.acquire(host.prepare())
        val session = FakeSession()

        acquired.attachSession(session)
        assertTrue(acquired.releaseSession(session))
        assertFalse(acquired.releaseSession(session))
        acquired.close()

        assertEquals(0, session.closeCalls)
        assertEquals(1, transport.bindings.last().closeCalls)
    }

    @Test
    fun packageAndRuntimeIdentityDriftAfterPrepareAreRejected() {
        val descriptor = descriptor()
        val provider = FakeProvider()
        val transport = fakeTransport(descriptor).apply { providers[descriptor.component] = provider }
        val host = host(transport)
        val prepared = host.prepare()

        transport.descriptors = listOf(descriptor.copy(packageUid = descriptor.packageUid + 1))
        val packageDrift = assertThrows(LuaRuntimeHostException::class.java) {
            host.acquire(prepared)
        }
        assertFailure(packageDrift, LuaRuntimeHostFailureCode.LUA_RUNTIME_IDENTITY_CHANGED)

        transport.descriptors = listOf(descriptor)
        val preparedAgain = host.prepare()
        provider.replaceRuntimeInfo(runtimeInfo(providerVersionName = "1.0.1"))
        assertEquals("1.0.0", preparedAgain.runtimeInfo.providerVersionName)
        val runtimeDrift = assertThrows(LuaRuntimeHostException::class.java) {
            host.acquire(preparedAgain)
        }
        assertFailure(runtimeDrift, LuaRuntimeHostFailureCode.LUA_RUNTIME_IDENTITY_CHANGED)
        assertTrue(transport.bindings.all { it.closeCalls == 1 })
    }

    @Test
    fun aNewEligibleProviderBeforeAcquireMakesImplicitSelectionAmbiguous() {
        val first = descriptor(component = component("first.lua.provider", "Runtime"), packageUid = 10_001)
        val second = descriptor(component = component("second.lua.provider", "Runtime"), packageUid = 10_002)
        val transport = fakeTransport(first).apply {
            providers[first.component] = FakeProvider(runtimeInfo(providerId = "first.provider"))
            providers[second.component] = FakeProvider(runtimeInfo(providerId = "second.provider"))
        }
        val host = host(transport)
        val prepared = host.prepare()
        transport.descriptors = listOf(first, second)

        val failure = assertThrows(LuaRuntimeHostException::class.java) {
            host.acquire(prepared)
        }

        assertFailure(failure, LuaRuntimeHostFailureCode.LUA_RUNTIME_IDENTITY_CHANGED)
        assertTrue(transport.bindings.all { it.closeCalls == 1 })
    }

    @Test
    fun finalBindRejectsWrongComponentAndDoesNotRetryFailures() {
        val descriptor = descriptor()
        val wrongTransport = fakeTransport(descriptor).apply {
            providers[descriptor.component] = FakeProvider()
            boundComponent = { requested, attempt ->
                if (attempt == 3) component("attacker.lua.provider", "Runtime") else requested
            }
        }
        val wrongHost = host(wrongTransport)
        val wrongPrepared = wrongHost.prepare()
        val wrong = assertThrows(LuaRuntimeHostException::class.java) {
            wrongHost.acquire(wrongPrepared)
        }
        assertFailure(wrong, LuaRuntimeHostFailureCode.LUA_RUNTIME_IDENTITY_CHANGED)
        assertEquals(3, wrongTransport.boundComponents.size)
        assertEquals(1, wrongTransport.bindings.last().closeCalls)

        val failingTransport = fakeTransport(descriptor).apply {
            providers[descriptor.component] = FakeProvider()
            bindingFailure = { _, attempt ->
                if (attempt == 3) IllegalStateException("final exact bind failed") else null
            }
        }
        val failingHost = host(failingTransport)
        val failingPrepared = failingHost.prepare()
        val failed = assertThrows(LuaRuntimeHostException::class.java) {
            failingHost.acquire(failingPrepared)
        }
        assertFailure(failed, LuaRuntimeHostFailureCode.LUA_RUNTIME_BIND_FAILED)
        assertEquals(3, failingTransport.boundComponents.size)
        assertEquals(2, failingTransport.bindings.size)
        assertTrue(failingTransport.bindings.all { it.closeCalls == 1 })
    }

    @Test
    fun transferOrCloseClosesEveryFailureAndPreservesInterrupts() {
        val success = CloseProbe()
        assertEquals("transferred", transferOrClose(success) { "transferred" })
        assertEquals(0, success.closeCalls)

        val original = IllegalStateException("acquisition failed")
        val failed = CloseProbe(throwOnClose = true)
        val thrown = assertThrows(IllegalStateException::class.java) {
            transferOrClose(failed) { throw original }
        }
        assertSame(original, thrown)
        assertEquals(1, failed.closeCalls)

        val interrupted = CloseProbe()
        try {
            assertThrows(InterruptedException::class.java) {
                transferOrClose(interrupted) { throw InterruptedException("stop") }
            }
            assertEquals(1, interrupted.closeCalls)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun transportInterruptDuringPrepareIsNotTranslatedToUnavailable() {
        val descriptor = descriptor()
        val transport = fakeTransport(descriptor).apply {
            providers[descriptor.component] = FakeProvider()
            bindingFailure = { _, _ -> InterruptedException("engine stopped") }
        }

        try {
            assertThrows(InterruptedException::class.java) { host(transport).prepare() }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun oneAbsoluteDeadlineIsSharedByDiscoveryBindAndRuntimeInfoProbes() {
        val first = descriptor(component = component("first.lua.provider", "Runtime"), packageUid = 10_001)
        val second = descriptor(component = component("second.lua.provider", "Runtime"), packageUid = 10_002)
        val now = AtomicLong(0L)
        val transport = fakeTransport(first, second).apply {
            providers[first.component] = FakeProvider(runtimeInfo(providerId = "first.provider"))
            providers[second.component] = FakeProvider(runtimeInfo(providerId = "second.provider"))
            afterDiscover = { now.addAndGet(TimeUnit.MILLISECONDS.toNanos(2L)) }
            afterBind = { _, attempt ->
                now.addAndGet(TimeUnit.MILLISECONDS.toNanos(if (attempt == 1) 2L else 10L))
            }
        }

        val failure = assertThrows(LuaRuntimeHostException::class.java) {
            host(
                transport = transport,
                handshakeTimeoutMillis = 10L,
                nanoTime = now::get,
            ).prepare()
        }

        assertFailure(failure, LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE)
        assertEquals(listOf(10L), transport.discoveryTimeouts)
        assertEquals(listOf(8L, 6L), transport.bindTimeouts)
        assertEquals(listOf(6L), transport.bindings[0].runtimeInfoTimeouts)
        assertTrue(transport.bindings[1].runtimeInfoTimeouts.isEmpty())
        assertEquals(0, transport.providers.getValue(second.component).getRuntimeInfoCalls)
        assertTrue(transport.bindings.all { it.closeCalls == 1 })
    }

    @Test
    fun saturatedBinderLaneStopsProviderEnumerationWithoutTryingAnotherComponent() {
        val first = descriptor(component = component("first.lua.provider", "Runtime"), packageUid = 10_001)
        val second = descriptor(component = component("second.lua.provider", "Runtime"), packageUid = 10_002)
        val transport = fakeTransport(first, second).apply {
            bindingFailure = { _, _ -> LuaBinderCallException(LuaBinderCallFailure.STALLED, "runtime-info") }
        }

        val failure = assertThrows(LuaRuntimeHostException::class.java) {
            host(transport).prepare()
        }

        assertFailure(failure, LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE)
        assertEquals(listOf(first.component), transport.boundComponents)
    }

    @Test
    fun excessiveProviderInventoryFailsClosedBeforeAnyRuntimeBind() {
        val descriptors = (0..16).map { index ->
            descriptor(
                component = component("provider$index.lua", "Runtime"),
                packageUid = 20_000 + index,
            )
        }
        val transport = fakeTransport(*descriptors.toTypedArray())

        val failure = assertThrows(LuaRuntimeHostException::class.java) {
            host(transport).prepare()
        }

        assertFailure(failure, LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE)
        assertTrue(transport.boundComponents.isEmpty())
    }

    @Test
    fun acquireUsesOneDeadlineAcrossRefreshRediscoveryAndFinalRuntimeProbe() {
        val descriptor = descriptor()
        val now = AtomicLong(0L)
        val transport = fakeTransport(descriptor).apply {
            providers[descriptor.component] = FakeProvider()
        }
        val host = host(
            transport = transport,
            handshakeTimeoutMillis = 10L,
            nanoTime = now::get,
        )
        val prepared = host.prepare()
        transport.discoveryActions.clear()
        transport.discoveryTimeouts.clear()
        transport.boundComponents.clear()
        transport.bindTimeouts.clear()
        transport.bindings.clear()
        transport.afterDiscover = { now.addAndGet(TimeUnit.MILLISECONDS.toNanos(2L)) }
        transport.afterBind = { _, attempt ->
            now.addAndGet(TimeUnit.MILLISECONDS.toNanos(if (attempt == 1) 2L else 5L))
        }

        val failure = assertThrows(LuaRuntimeHostException::class.java) {
            host.acquire(prepared)
        }

        assertFailure(failure, LuaRuntimeHostFailureCode.LUA_RUNTIME_IDENTITY_CHANGED)
        assertEquals(listOf(10L, 6L), transport.discoveryTimeouts)
        assertEquals(listOf(8L, 4L), transport.bindTimeouts)
        assertEquals(listOf(6L), transport.bindings[0].runtimeInfoTimeouts)
        assertTrue(transport.bindings[1].runtimeInfoTimeouts.isEmpty())
        assertTrue(transport.bindings.all { it.closeCalls == 1 })
    }

    private fun host(
        transport: FakeTransport,
        handshakeTimeoutMillis: Long = 15_000L,
        nanoTime: () -> Long = System::nanoTime,
    ) = LuaRuntimePluginHost(
        transport = transport,
        environment = LuaRuntimeHostTestFixtures.environment(),
        requirement = LuaRuntimeHostTestFixtures.requirement(),
        handshakeTimeoutMillis = handshakeTimeoutMillis,
        nanoTime = nanoTime,
    )

    private fun assertFailure(error: LuaRuntimeHostException, expected: LuaRuntimeHostFailureCode) {
        assertEquals(expected, error.code)
        assertTrue(error.message.orEmpty().startsWith("$expected: "))
    }

    private class CloseProbe(private val throwOnClose: Boolean = false) : AutoCloseable {
        var closeCalls = 0

        override fun close() {
            closeCalls += 1
            if (throwOnClose) throw IllegalStateException("close failed")
        }
    }

}

private typealias FakeTransport = LuaRuntimeHostTestFixtures.FakeTransport
private typealias FakeProvider = LuaRuntimeHostTestFixtures.FakeProvider
private typealias FakeSession = LuaRuntimeHostTestFixtures.FakeSession

private fun fakeTransport(vararg descriptors: LuaRuntimeProviderDescriptor) =
    LuaRuntimeHostTestFixtures.FakeTransport(descriptors.asList())

private fun component(packageName: String, className: String) =
    LuaRuntimeHostTestFixtures.component(packageName, className)

private fun descriptor(
    component: LuaRuntimeProviderComponent = LuaRuntimeHostTestFixtures.component(),
    requiredPermission: String? = LuaRuntimeContract.PLUGIN_PERMISSION,
    packageUid: Int = 10_001,
) = LuaRuntimeHostTestFixtures.descriptor(
    component = component,
    requiredPermission = requiredPermission,
    packageUid = packageUid,
)

private fun runtimeInfo(
    providerId: String = "fixture.lua.provider",
    providerVersionName: String = "1.0.0",
) = LuaRuntimeHostTestFixtures.runtimeInfo(
    providerId = providerId,
    providerVersionName = providerVersionName,
)
