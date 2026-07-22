package org.autojs.autojs.core.plugin.editor

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AceEditorPluginPackageEventPolicyTest {

    @Test
    fun replacingRemovalIsIgnoredToAvoidUpdateFallbackFlicker() {
        assertTrue(
            AceEditorPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_REMOVED,
                replacing = true,
            ),
        )
        assertTrue(
            AceEditorPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_ADDED,
                replacing = true,
            ),
        )
    }

    @Test
    fun realRemovalAndReplacementCompletionAreObserved() {
        assertFalse(
            AceEditorPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_REMOVED,
                replacing = false,
            ),
        )
        assertFalse(
            AceEditorPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_REPLACED,
                replacing = true,
            ),
        )
    }

    @Test
    fun sameIdentityReplacementStillRequiresSessionReload() {
        val state = AceEditorPluginStateMonitor.State(
            status = AceEditorPluginStateMonitor.Status.AVAILABLE,
            packageVersionCode = 1L,
            packageLastUpdateTime = 2L,
        )

        assertTrue(
            AceEditorPluginStateMonitor.Change(
                id = 1L,
                previous = state,
                current = state,
                cause = AceEditorPluginStateMonitor.Cause.PACKAGE_REPLACED,
            ).requiresSessionReload,
        )
        assertFalse(
            AceEditorPluginStateMonitor.Change(
                id = 2L,
                previous = state,
                current = state,
                cause = AceEditorPluginStateMonitor.Cause.REFRESHED,
            ).requiresSessionReload,
        )
    }
}
