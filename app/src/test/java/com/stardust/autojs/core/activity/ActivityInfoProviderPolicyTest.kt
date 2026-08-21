package com.stardust.autojs.core.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityInfoProviderPolicyTest {

    @Test
    fun `accepts a focused event window`() {
        assertTrue(
            shouldAcceptWindowStateChange(
                eventWindowId = 7,
                eventPackage = "com.example.app",
                eventWindowFocused = true,
                activeRootWindowId = 8,
                activeRootPackage = "com.example.other",
            ),
        )
    }

    @Test
    fun `rejects an unfocused event window even when the active root matches`() {
        assertFalse(
            shouldAcceptWindowStateChange(
                eventWindowId = 7,
                eventPackage = "com.example.app",
                eventWindowFocused = false,
                activeRootWindowId = 7,
                activeRootPackage = "com.example.app",
            ),
        )
    }

    @Test
    fun `rejects a missing event window when the active root window differs`() {
        assertFalse(
            shouldAcceptWindowStateChange(
                eventWindowId = 7,
                eventPackage = "com.example.app",
                eventWindowFocused = null,
                activeRootWindowId = 8,
                activeRootPackage = "com.example.app",
            ),
        )
    }

    @Test
    fun `rejects a missing event window when the active root package differs`() {
        assertFalse(
            shouldAcceptWindowStateChange(
                eventWindowId = 7,
                eventPackage = "com.example.app",
                eventWindowFocused = null,
                activeRootWindowId = 7,
                activeRootPackage = "com.example.other",
            ),
        )
    }

    @Test
    fun `accepts a missing event window verified by the active root`() {
        assertTrue(
            shouldAcceptWindowStateChange(
                eventWindowId = 7,
                eventPackage = "com.example.app",
                eventWindowFocused = null,
                activeRootWindowId = 7,
                activeRootPackage = "com.example.app",
            ),
        )
    }

    @Test
    fun `rejects a missing event window with no package evidence`() {
        assertFalse(
            shouldAcceptWindowStateChange(
                eventWindowId = 7,
                eventPackage = null,
                eventWindowFocused = null,
                activeRootWindowId = 7,
                activeRootPackage = null,
            ),
        )
    }

}
