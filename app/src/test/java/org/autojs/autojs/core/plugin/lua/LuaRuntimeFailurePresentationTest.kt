package org.autojs.autojs.core.plugin.lua

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LuaRuntimeFailurePresentationTest {

    @Test
    fun `nested unavailable failure maps to installation guidance`() {
        val failure = IllegalStateException(
            "outer",
            hostFailure(LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE),
        )

        assertEquals(LuaRuntimeFailurePresentation.UNAVAILABLE, LuaRuntimeFailurePresentation.from(failure))
        assertEquals(
            "rendered-unavailable",
            LuaRuntimeFailurePresentation.messageOrOriginal(failure) { "rendered-${it.name.lowercase()}" },
        )
    }

    @Test
    fun `compatibility evaluation overrides generic unavailable presentation`() {
        val failure = hostFailure(
            code = LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE,
            rejection = LuaRuntimeProviderRejection.PROTOCOL_UNSUPPORTED,
        )

        assertEquals(LuaRuntimeFailurePresentation.COMPATIBILITY, LuaRuntimeFailurePresentation.from(failure))
    }

    @Test
    fun `ambiguous host failure has dedicated presentation`() {
        val failure = hostFailure(LuaRuntimeHostFailureCode.LUA_RUNTIME_AMBIGUOUS)

        assertEquals(LuaRuntimeFailurePresentation.AMBIGUOUS, LuaRuntimeFailurePresentation.from(failure))
    }

    @Test
    fun `identity bind and bind evaluation failures share recovery presentation`() {
        assertEquals(
            LuaRuntimeFailurePresentation.IDENTITY_OR_BIND,
            LuaRuntimeFailurePresentation.from(hostFailure(LuaRuntimeHostFailureCode.LUA_RUNTIME_IDENTITY_CHANGED)),
        )
        assertEquals(
            LuaRuntimeFailurePresentation.IDENTITY_OR_BIND,
            LuaRuntimeFailurePresentation.from(hostFailure(LuaRuntimeHostFailureCode.LUA_RUNTIME_BIND_FAILED)),
        )
        assertEquals(
            LuaRuntimeFailurePresentation.IDENTITY_OR_BIND,
            LuaRuntimeFailurePresentation.from(
                hostFailure(
                    code = LuaRuntimeHostFailureCode.LUA_RUNTIME_UNAVAILABLE,
                    rejection = LuaRuntimeProviderRejection.BIND_FAILED,
                ),
            ),
        )
    }

    @Test
    fun `non Lua failure passes its original message through`() {
        val failure = IllegalArgumentException("original diagnostic")

        assertNull(LuaRuntimeFailurePresentation.from(failure))
        assertEquals(
            "original diagnostic",
            LuaRuntimeFailurePresentation.messageOrOriginal(failure) { "must-not-render" },
        )
    }

    private fun hostFailure(
        code: LuaRuntimeHostFailureCode,
        rejection: LuaRuntimeProviderRejection? = null,
    ) = LuaRuntimeHostException(
        code = code,
        message = "host diagnostic",
        evaluations = rejection?.let {
            listOf(
                LuaRuntimeProviderEvaluation(
                    component = LuaRuntimeProviderComponent("test.lua.provider", "RuntimeService"),
                    providerId = "test-provider",
                    rejection = it,
                ),
            )
        }.orEmpty(),
    )
}
