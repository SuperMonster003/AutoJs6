package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class R8CompilerSelectionPolicyTest {
    @Test
    fun defaultOffAndNoSelectionNeverDiscover() {
        val discovered = AtomicBoolean(false)
        assertTrue(
            R8CompilerSelectionPolicy.decide {
                discovered.set(true)
                emptyList()
            } is R8CompilerSelectionDecision.Disabled,
        )
        assertFalse(discovered.get())

        val noSelection = R8CompilerSelectionPolicy.decide(R8CompilerSelectionOptions(enabled = true)) {
            discovered.set(true)
            emptyList()
        }
        assertTrue(noSelection is R8CompilerSelectionDecision.NoSelection)
        assertFalse(discovered.get())
    }

    @Test
    fun onlyTheExactPersistedSameSignerComponentCanBeSelected() {
        val selected = R8CompilerHostFixtures.component()
        val other = R8CompilerComponent("example.other", "example.other.Service")
        val decision = R8CompilerSelectionPolicy.decide(
            R8CompilerSelectionOptions(true, selected),
        ) {
            listOf(
                R8CompilerHostFixtures.candidate(component = other),
                R8CompilerHostFixtures.candidate(component = selected),
            )
        }
        assertEquals(
            R8CompilerHostFixtures.candidate().pin(),
            (decision as R8CompilerSelectionDecision.Selected).identity,
        )
    }

    @Test
    fun identityAndServicePolicyDriftFailClosed() {
        val mutations = listOf(
            R8CompilerHostFixtures.candidate(action = "wrong"),
            R8CompilerHostFixtures.candidate(exported = false),
            R8CompilerHostFixtures.candidate(applicationEnabled = false),
            R8CompilerHostFixtures.candidate(serviceEnabled = false),
            R8CompilerHostFixtures.candidate(permission = "wrong.permission"),
            R8CompilerHostFixtures.candidate(signers = emptySet()),
            R8CompilerHostFixtures.candidate(sameSigner = false),
        )
        mutations.forEach { candidate ->
            val decision = R8CompilerSelectionPolicy.decide(
                R8CompilerSelectionOptions(true, candidate.component),
            ) { listOf(candidate) }
            assertTrue("Unexpected decision for $candidate", decision is R8CompilerSelectionDecision.Rejected)
        }
    }

    @Test
    fun duplicateExactMatchesAndDiscoveryFailureAreNotSelected() {
        val selected = R8CompilerHostFixtures.component()
        assertTrue(
            R8CompilerSelectionPolicy.decide(R8CompilerSelectionOptions(true, selected)) {
                listOf(R8CompilerHostFixtures.candidate(), R8CompilerHostFixtures.candidate())
            } is R8CompilerSelectionDecision.Rejected,
        )
        assertTrue(
            R8CompilerSelectionPolicy.decide(R8CompilerSelectionOptions(true, selected)) {
                throw IllegalStateException("discovery failed")
            } is R8CompilerSelectionDecision.Unavailable,
        )
    }

    @Test
    fun componentCodecIsCanonicalAndIndependentFromDexEncoding() {
        val component = R8CompilerHostFixtures.component()
        val encoded = R8CompilerComponentCodec.encode(component)
        assertTrue(encoded.startsWith("r8-v1:"))
        assertEquals(component, R8CompilerComponentCodec.decodeOrNull(encoded))
        assertNull(R8CompilerComponentCodec.decodeOrNull("v1:7:7:exampleService"))
        assertNull(R8CompilerComponentCodec.decodeOrNull("r8-v1:01:1:ab"))
    }

    @Test
    fun candidatePolicyPinsTheIndependentActionAndPermission() {
        val candidate = R8CompilerHostFixtures.candidate()
        assertEquals(R8CompilerContract.SERVICE_ACTION, candidate.declaredAction)
        assertEquals(R8CompilerContract.PLUGIN_PERMISSION, candidate.requiredPermission)
        assertNull(R8CompilerSelectionPolicy.rejection(candidate))
        assertTrue(R8CompilerSelectionPolicy.revalidate(candidate.pin(), candidate))
        assertFalse(R8CompilerSelectionPolicy.revalidate(candidate.pin(), candidate.copy(lastUpdateTime = 3L)))
    }
}
