package org.autojs.autojs.app

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationContextBootstrapTest {
    @Test fun contextIsReadyWhenProvidersStartBeforeApplicationOnCreate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val value = context.contentResolver.call(Uri.parse("content://${context.packageName}.context-probe"), "probe", null, null)
        assertTrue("Both context references must exist before provider publication", value?.getBoolean("earlyContextReady") == true)
    }
}
