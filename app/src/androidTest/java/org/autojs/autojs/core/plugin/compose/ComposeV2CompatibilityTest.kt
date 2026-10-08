package org.autojs.autojs.core.plugin.compose

import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.core.plugin.center.PluginEnableStore
import org.autojs.plugin.compose.api.ComposeUiContract
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.ComposeUiIds
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run this test APK against the retained actual 5321 host, using V1 symbols only. */
@RunWith(AndroidJUnit4::class)
class ComposeV2CompatibilityTest {
    @Suppress("DEPRECATION")
    @Test fun oldHostRejectsV2BeforeLoading() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("composeV2Compatibility") == "oldHost")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".compose.spike"))
        assertEquals(5321L, PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0)))
        // Read the loaded parent class, not the new test compiler's inlined const value.
        assertEquals(1, ComposeUiContract::class.java.getField("CONTRACT_VERSION").getInt(null))
        val metadata = context.packageManager.getApplicationInfo(ComposeUiIds.PLUGIN_PACKAGE_NAME, PackageManager.GET_META_DATA).metaData
        assertEquals(5322, metadata.getInt("requiresHostVersion"))
        val enabled = PluginEnableStore.isEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME)
        PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, true)
        try {
            ComposeUiPluginHost(context).use { host ->
                assertFalse(host.isAvailable())
                try {
                    host.load()
                    fail("A V1 host must reject the V2 provider before loading its classes")
                } catch (failure: ComposeUiError) {
                    assertEquals(ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE, failure.code)
                    assertFalse(failure.message.isNullOrBlank())
                    assertNull("Selection rejection does not wrap a LinkageError", failure.cause)
                }
            }
        } finally {
            PluginEnableStore.setEnabled(context, ComposeUiIds.PLUGIN_PACKAGE_NAME, enabled)
        }
    }
}
