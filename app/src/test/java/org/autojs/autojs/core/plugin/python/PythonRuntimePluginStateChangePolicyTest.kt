package org.autojs.autojs.core.plugin.python

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PythonRuntimePluginStateChangePolicyTest {

    @Test
    fun unrelatedPluginChangesDoNotAffectPythonExecution() {
        assertNull(
            PythonRuntimePluginStateChangePolicy.failureFor(
                changedPackageName = "example.unrelated.plugin",
                officialPackageName = OFFICIAL_PACKAGE,
                pluginEnabled = false,
            ),
        )
    }

    @Test
    fun logicalDisableFailsWithStableDisabledError() {
        val failure = requireNotNull(
            PythonRuntimePluginStateChangePolicy.failureFor(
                changedPackageName = OFFICIAL_PACKAGE,
                officialPackageName = OFFICIAL_PACKAGE,
                pluginEnabled = false,
            ),
        )

        assertEquals(PythonRuntimeHostError.PLUGIN_DISABLED, failure.error)
    }

    @Test
    fun authorizationChangeInvalidatesThePinnedExecution() {
        val failure = requireNotNull(
            PythonRuntimePluginStateChangePolicy.failureFor(
                changedPackageName = OFFICIAL_PACKAGE,
                officialPackageName = OFFICIAL_PACKAGE,
                pluginEnabled = true,
            ),
        )

        assertEquals(PythonRuntimeHostError.PROVIDER_IDENTITY_CHANGED, failure.error)
    }

    private companion object {
        const val OFFICIAL_PACKAGE = "io.github.supermonster003.autojs6.plugin.python.runtime"
    }
}
