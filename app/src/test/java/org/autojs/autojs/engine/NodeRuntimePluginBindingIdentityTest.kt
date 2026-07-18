package org.autojs.autojs.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeRuntimePluginBindingIdentityTest {

    @Test
    fun staleGenerationCannotInvalidateReplacementUsingSameBinder() {
        val sharedBinder = Any()

        assertFalse(
            NodeRuntimePluginBindingIdentity.matches(
                currentGeneration = 2L,
                currentBinder = sharedBinder,
                expectedGeneration = 1L,
                expectedBinder = sharedBinder,
            ),
        )
    }

    @Test
    fun staleBinderCannotInvalidateReplacementInSameGeneration() {
        assertFalse(
            NodeRuntimePluginBindingIdentity.matches(
                currentGeneration = 3L,
                currentBinder = Any(),
                expectedGeneration = 3L,
                expectedBinder = Any(),
            ),
        )
    }

    @Test
    fun exactGenerationAndBinderMatchCanInvalidate() {
        val binder = Any()

        assertTrue(
            NodeRuntimePluginBindingIdentity.matches(
                currentGeneration = 4L,
                currentBinder = binder,
                expectedGeneration = 4L,
                expectedBinder = binder,
            ),
        )
    }
}
