package org.autojs.autojs.core.plugin.jvm

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AndroidJvmClipboardActionsTest {
    @Test
    fun preAndroid10DoesNotRequireResumedActivity() {
        var checkedForeground = false

        requireJvmClipboardAccess(28) {
            checkedForeground = true
            false
        }

        assertEquals(false, checkedForeground)
    }

    @Test
    fun android10AndLaterRejectBackgroundClipboardAccess() {
        val error = try {
            requireJvmClipboardAccess(29) { false }
            fail("Expected background clipboard access to be rejected")
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertEquals(
            "AutoJs6 must be in the foreground to access the system clipboard",
            error?.message,
        )
    }

    @Test
    fun android10AndLaterAcceptResumedActivity() {
        requireJvmClipboardAccess(36) { true }
    }
}
