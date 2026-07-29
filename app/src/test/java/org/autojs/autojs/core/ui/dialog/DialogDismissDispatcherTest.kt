package org.autojs.autojs.core.ui.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class DialogDismissDispatcherTest {

    @Test
    fun releasesOnlyAfterInternalAndUserCallbacksComplete() {
        val order = mutableListOf<String>()

        DialogDismissDispatcher.dispatch(
            { order += "internal" },
            {
                assertFalse(order.contains("release"))
                order += "user"
            },
            { order += "release" },
        )

        assertEquals(listOf("internal", "user", "release"), order)
    }

    @Test
    fun userCallbackAndReleaseRunWhenInternalCallbackThrows() {
        val order = mutableListOf<String>()

        try {
            DialogDismissDispatcher.dispatch(
                {
                    order += "internal"
                    throw IllegalStateException("internal")
                },
                { order += "user" },
                { order += "release" },
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("internal", e.message)
        }

        assertEquals(listOf("internal", "user", "release"), order)
    }

    @Test
    fun releaseRunsWhenUserCallbackThrows() {
        val order = mutableListOf<String>()

        try {
            DialogDismissDispatcher.dispatch(
                { order += "internal" },
                {
                    order += "user"
                    throw IllegalStateException("user")
                },
                { order += "release" },
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("user", e.message)
        }

        assertEquals(listOf("internal", "user", "release"), order)
    }

}
