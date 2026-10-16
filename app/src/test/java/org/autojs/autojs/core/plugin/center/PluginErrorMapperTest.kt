package org.autojs.autojs.core.plugin.center

import android.os.DeadObjectException
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginErrorMapperTest {

    @Test
    fun processDisconnectSignalsAreClassifiedAsPluginCrashes() {
        listOf(
            "service binding died before getInfo: example.PluginInfoService",
            "service disconnected before getInfo: example.PluginInfoService",
            "Plugin process has died",
            "Plugin keeps stopping",
            "onBindingDied: example.PluginService",
            "onServiceDisconnected: example.PluginService",
            "binderDied: example.PluginService",
        ).forEach { message ->
            assertEquals(
                message,
                PluginErrorCode.PLUGIN_PROCESS_CRASHED,
                PluginErrorMapper.fromThrowable(IllegalStateException(message)).code,
            )
        }
    }

    @Test
    fun deadBinderIsClassifiedAsPluginProcessCrash() {
        assertEquals(
            PluginErrorCode.PLUGIN_PROCESS_CRASHED,
            PluginErrorMapper.fromThrowable(DeadObjectException()).code,
        )
    }

    @Test
    fun nestedBindingFailureRetainsItsSpecificClassification() {
        val error = IllegalStateException(
            "Unable to inspect plugin",
            IllegalStateException("service returned null binding: example.PluginInfoService"),
        )

        assertEquals(PluginErrorCode.BIND_FAILED, PluginErrorMapper.fromThrowable(error).code)
    }

    @Test
    fun bindingTimeoutRetainsItsSpecificClassification() {
        val error = IllegalStateException("bindService timed out: example.PluginInfoService")

        assertEquals(PluginErrorCode.HANDSHAKE_TIMEOUT, PluginErrorMapper.fromThrowable(error).code)
    }

    @Test
    fun missingServiceClassIsReportedWithoutRecommendingActivation() {
        val error = PluginErrorMapper.fromThrowable(ClassNotFoundException("example.PluginService"))
        assertEquals(PluginErrorCode.PLUGIN_PROCESS_CRASHED, error.code)
        org.junit.Assert.assertFalse(PluginErrorMapper.shouldRecommendActivation(error))
    }

    @Test
    fun aRefusedBindingIsMarkedSoTheAutomaticEnableCanWakeThePlugin() {
        val refused = org.autojs.autojs.core.plugin.PluginBindRefusedException("example", "bindService failed: example.PluginService")
        val error = PluginErrorMapper.fromThrowable(IllegalStateException("discovery failed", refused))
        assertEquals(PluginErrorCode.BIND_FAILED, PluginErrorMapper.fromThrowable(refused).code)
        org.junit.Assert.assertTrue(error.bindRefused)
        org.junit.Assert.assertFalse(PluginErrorMapper.fromThrowable(IllegalStateException("bindService failed: other")).bindRefused)
    }
}
