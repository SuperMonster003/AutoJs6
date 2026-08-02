package org.autojs.autojs.core.plugin.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DexCompilerFallbackControllerTest {

    @Test
    fun preDispatchFailureFallsBackOnceWithoutRollback() {
        val controller = DexCompilerFallbackController()

        val directive = controller.tryClaimLocalFallback()
        assertNotNull(directive)
        assertFalse(directive!!.requiresPluginOutputRollback)
        assertNull(controller.tryClaimLocalFallback())
        assertFalse(controller.tryBeginPluginDispatch())
    }

    @Test
    fun postDispatchFailureFallsBackOnceAfterRollbackAndNeverRetriesBinder() {
        val controller = DexCompilerFallbackController()

        assertTrue(controller.tryBeginPluginDispatch())
        assertFalse(controller.tryBeginPluginDispatch())
        val directive = controller.tryClaimLocalFallback()
        assertNotNull(directive)
        assertTrue(directive!!.requiresPluginOutputRollback)
        assertNull(controller.tryClaimLocalFallback())
        assertFalse(controller.tryBeginPluginDispatch())
    }

    @Test
    fun cancellationIsIdempotentAndNeverFallsBack() {
        val beforeDispatch = DexCompilerFallbackController()
        assertTrue(beforeDispatch.cancel())
        assertFalse(beforeDispatch.cancel())
        assertNull(beforeDispatch.tryClaimLocalFallback())
        assertFalse(beforeDispatch.tryBeginPluginDispatch())

        val afterDispatch = DexCompilerFallbackController()
        assertTrue(afterDispatch.tryBeginPluginDispatch())
        assertTrue(afterDispatch.cancel())
        assertNull(afterDispatch.tryClaimLocalFallback())
        assertFalse(afterDispatch.tryBeginPluginDispatch())
    }

    @Test
    fun successfulCompletionSealsBothDispatchPaths() {
        val controller = DexCompilerFallbackController()
        assertTrue(controller.tryBeginPluginDispatch())
        assertTrue(controller.finish())
        assertFalse(controller.finish())
        assertFalse(controller.cancel())
        assertNull(controller.tryClaimLocalFallback())
        assertFalse(controller.tryBeginPluginDispatch())
    }

    @Test
    fun concurrentFallbackClaimsHaveExactlyOneWinner() {
        val controller = DexCompilerFallbackController()
        assertTrue(controller.tryBeginPluginDispatch())
        val ready = CountDownLatch(16)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(16)
        val results = (0 until 16).map {
            pool.submit<DexCompilerFallbackController.LocalFallbackDirective?> {
                ready.countDown()
                release.await()
                controller.tryClaimLocalFallback()
            }
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS))
        release.countDown()
        val directives = results.map { it.get(5, TimeUnit.SECONDS) }.filterNotNull()
        pool.shutdownNow()

        assertEquals(1, directives.size)
        assertTrue(directives.single().requiresPluginOutputRollback)
    }
}
