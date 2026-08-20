package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeWorkerThreadsDesignGateInstrumentationTest {

    @Test
    fun workerThreadsAreStableAndEnabledWithoutBuildGate() {
        val capability = NodeRuntimeCapabilities.featureFlagsById.getValue("worker_threads")

        assertTrue(BuildConfig.NODEJS_WORKER_THREADS_ENABLED)
        assertTrue(capability.defaultEnabled)
        assertEquals("stable", capability.status)
        assertFalse(capability.buildTimeOverrideAllowed)
        assertFalse(capability.releaseForbidden)
    }
}
