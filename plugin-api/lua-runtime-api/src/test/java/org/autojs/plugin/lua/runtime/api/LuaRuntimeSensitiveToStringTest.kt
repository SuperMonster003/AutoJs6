package org.autojs.plugin.lua.runtime.api

import org.junit.Assert.assertFalse
import org.junit.Test

class LuaRuntimeSensitiveToStringTest {
    @Test
    fun requestResultErrorsAndHostCallsAreSummaryOnly() {
        val request = LuaRuntimeFixtures.request()
        val secretValue = "token-secret-value"
        val result = LuaExecutionResult(
            LuaRuntimeFixtures.REQUEST_ID,
            LuaValue.StringValue(secretValue),
            1L,
        )
        val error = LuaExecutionError(
            LuaRuntimeFixtures.REQUEST_ID,
            LuaExecutionErrorCode.RUNTIME_ERROR,
            LuaExecutionFailurePhase.EXECUTION,
            secretValue,
            LuaRetryDisposition.DO_NOT_RETRY,
        )
        val host = LuaHostCallRequest(
            LuaRuntimeFixtures.REQUEST_ID,
            "call-secret-id",
            "device.info",
            LuaValue.StringValue(secretValue),
        )

        assertFalse(request.toString().contains("main.lua"))
        assertFalse(request.toString().contains("count"))
        assertFalse(result.toString().contains(secretValue))
        assertFalse(error.toString().contains(secretValue))
        assertFalse(host.toString().contains(secretValue))
        assertFalse(host.toString().contains("call-secret-id"))
    }
}
