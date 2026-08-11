package org.autojs.plugin.python.runtime.consumer

import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PythonRuntimeApiDistributionSmokeTest {

    @Test
    fun distributedAarsExposeWireAndPythonRuntimeRoundTrip() {
        val result = PythonRuntimeApiDistributionSmoke.verify()

        assertEquals(PythonRuntimeContract.SCHEMA_CANCELLATION, result.schemaId)
        assertTrue(result.encodedBytes > 0)
        assertEquals(17L, result.cancellation.elapsedMillis)
    }
}
