package org.autojs.autojs.core.plugin.screencolorpicker

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenColorPickerPluginPackageEventPolicyTest {

    @Test
    fun replacementIgnoresIntermediateRemoveAndAddBroadcasts() {
        assertTrue(
            ScreenColorPickerPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_REMOVED,
                replacing = true,
            ),
        )
        assertTrue(
            ScreenColorPickerPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_ADDED,
                replacing = true,
            ),
        )
    }

    @Test
    fun ordinaryInstallUninstallAndReplacementRemainObservable() {
        assertFalse(
            ScreenColorPickerPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_ADDED,
                replacing = false,
            ),
        )
        assertFalse(
            ScreenColorPickerPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_REMOVED,
                replacing = false,
            ),
        )
        assertFalse(
            ScreenColorPickerPluginPackageEventPolicy.shouldIgnore(
                Intent.ACTION_PACKAGE_REPLACED,
                replacing = true,
            ),
        )
    }
}
